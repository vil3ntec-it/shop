package ir.vil3ntec.tohid.ios

import android.content.Context
import android.content.IosContext
import android.content.SharedPreferences
import android.content.UserDefaultsPreferences
import ir.vil3ntec.tohid.core.atrest.AtRestKeyring
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.data.DiskWrite
import ir.vil3ntec.tohid.data.LedgerRecovery
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopSettingsBundle
import ir.vil3ntec.tohid.data.ShopSettingsSync
import ir.vil3ntec.tohid.ios.keychain.Keychain
import ir.vil3ntec.tohid.sync.v1.SignOutVault
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncV1Keys
import ir.vil3ntec.tohid.ui.Platform
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  ایمنیِ دادهٔ دسته‌های ۲۸ تا ۳۰ روی آیفون (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود): همان کدِ مشترکِ اندروید
 *  روی shimهای iOS — نوعِ درستِ `SharedPreferences.all` (همگام‌سازیِ تنظیماتِ دکان، تنظیماتِ پشتیبان، جابه‌جاییِ کلیدِ
 *  همگام‌سازی)، نسخهٔ پیشینِ دفتر (`.prev`) و بازیابیِ آن، گاوصندوقِ رمزدارِ خروج روی کلیددان، و پوشهٔ پشتیبانِ دیدنی.
 *  منطقِ نوع روی لینوکس: ‎jvmTest/…/ios/prefs/PrefTypesJvmTest‎.
 */
class DataSafetyIosTest {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  @Test
  fun prefsAllKeepsAndroidTypes() {
    val name = "ios-test-pref-types"
    val types = mapOf("small" to "l", "days" to "i", "legacy" to "b")
    val p = UserDefaultsPreferences(name) { key -> types[key] }
    try {
      p.edit()
        .putBoolean("flag", true)
        .putLong("at", 1_730_000_000_000L)
        .putLong("small", 500L)
        .putInt("days", 14)
        .putInt("count", 7)
        .putFloat("ratio", 0.5f)
        .putString("text", "x")
        .putStringSet("tags", setOf("a", "b"))
        //  رونوشتِ shim ِ پیشین: بولی به شکلِ متن
        .putString("legacy", "1")
        .commit()
      val all = p.all
      assertEquals(true, all["flag"])
      assertEquals(1_730_000_000_000L, all["at"])
      assertEquals(500L, all["small"])
      assertEquals(14, all["days"])
      //  NSUserDefaults عددِ صحیح را ۶۴ بیتی نگه می‌دارد ⇒ کلیدِ اعلام‌نشده Long
      assertEquals(7L, all["count"])
      assertEquals(0.5f, all["ratio"])
      assertEquals("x", all["text"])
      assertEquals(setOf("a", "b"), all["tags"])
      assertEquals(true, all["legacy"])
      //  خواننده‌های نوع‌دار همان مقدار را می‌دهند
      assertEquals(14, p.getInt("days", 0))
      assertEquals(500L, p.getLong("small", 0L))
      assertTrue(p.getBoolean("legacy", false))
    } finally {
      NSUserDefaults.standardUserDefaults.removePersistentDomainForName(UserDefaultsPreferences.suite(name))
    }
  }

  @Test
  fun shopSettingsSyncAndBackupSeeRealTypes() {
    val prefs = IosContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)
    val keys = listOf("checkout_block_zero_total", "cash_rounding", "return_window_days", "return_limit", "return_reasons")
    val before = keys.associateWith { prefs.all[it] }
    try {
      prefs.edit()
        .putBoolean("checkout_block_zero_total", true)
        .putLong("cash_rounding", 500L)
        .putInt("return_window_days", 14)
        .putFloat("return_limit", 0.5f)
        .putString("return_reasons", "خراب")
        .commit()

      //  همگام‌سازیِ تنظیماتِ دکان: پیش‌تر بولی و عددی «نیست» بودند ⇒ «برداشتن» به سرور
      val local = ShopSettingsSync.readLocal(IosContext)
      assertEquals("b", local["tohid-policy/checkout_block_zero_total"]?.t)
      assertEquals(JsonPrimitive(true), local["tohid-policy/checkout_block_zero_total"]?.v)
      assertEquals("l", local["tohid-policy/cash_rounding"]?.t)
      assertEquals(JsonPrimitive(500L), local["tohid-policy/cash_rounding"]?.v)
      assertEquals("i", local["tohid-policy/return_window_days"]?.t)
      assertEquals(JsonPrimitive(14), local["tohid-policy/return_window_days"]?.v)
      assertEquals("f", local["tohid-policy/return_limit"]?.t)
      assertEquals("s", local["tohid-policy/return_reasons"]?.t)

      //  تنظیماتِ پشتیبان: همان نوعی که اندروید با `getBoolean`/`getLong`/`getInt` می‌خواند (نه `"t":"s"`)
      val policy = json.parseToJsonElement(ShopSettingsBundle.export(IosContext)) as JsonObject
      val exported = policy["tohid-policy"] as JsonObject
      fun typeOf(k: String) = ((exported[k] as JsonObject)["t"] as JsonPrimitive).content
      assertEquals("b", typeOf("checkout_block_zero_total"))
      assertEquals("l", typeOf("cash_rounding"))
      assertEquals("i", typeOf("return_window_days"))
      assertEquals("f", typeOf("return_limit"))
      assertEquals("s", typeOf("return_reasons"))

      //  پشتیبانِ اندروید روی آیفون: فقط کلیدهای تنظیم‌نشده، با همان نوع
      val e = prefs.edit()
      keys.forEach { e.remove(it) }
      e.commit()
      val android = """{"tohid-policy":{"checkout_block_zero_total":{"t":"b","v":true},"cash_rounding":{"t":"l","v":500},""" +
        """"return_window_days":{"t":"i","v":14}}}"""
      assertEquals(3, ShopSettingsBundle.apply(IosContext, android))
      assertEquals(true, prefs.all["checkout_block_zero_total"])
      assertEquals(500L, prefs.all["cash_rounding"])
      assertEquals(14, prefs.all["return_window_days"])
      assertEquals(14, prefs.getInt("return_window_days", 0))
    } finally {
      restore(prefs, before)
    }
  }

  @Test
  fun syncKeyMoveKeepsTypes() {
    val prefs = IosContext.getSharedPreferences("tohid-sync-v1", Context.MODE_PRIVATE)
    val from = "ios-test-move-a"
    val to = "ios-test-move-b"
    val names = listOf("cursor", "holding", "shadow", "serverSchema")
    try {
      prefs.edit()
        .putLong("$from.cursor", 42L)
        .putBoolean("$from.holding", true)
        .putString("$from.shadow", "{}")
        .putInt("$from.serverSchema", 3)
        .commit()
      SyncV1Keys.move(IosContext, from, to)
      assertFalse(prefs.contains("$from.cursor"))
      assertEquals(42L, prefs.all["$to.cursor"])
      assertEquals(42L, prefs.getLong("$to.cursor", 0L))
      //  پیش‌تر "1" (متن) جابه‌جا می‌شد
      assertEquals(true, prefs.all["$to.holding"])
      assertTrue(prefs.getBoolean("$to.holding", false))
      assertEquals("{}", prefs.getString("$to.shadow", null))
      assertEquals(3, prefs.getInt("$to.serverSchema", 0))
    } finally {
      val e = prefs.edit()
      names.forEach { e.remove("$from.$it").remove("$to.$it") }
      e.commit()
    }
  }

  @Test
  fun diskWriteKeepsPreviousAndRecoveryFindsIt() {
    val dir = File(IosContext.cacheDir, "ios-test-data-safety")
    dir.deleteRecursively()
    try {
      val ledger = File(dir, "shop-data.json")
      val first = json.encodeToString(ShopData.serializer(), ShopData(expenseCategories = listOf("نخست")))
      val second = json.encodeToString(ShopData.serializer(), ShopData(expenseCategories = listOf("دوم")))
      DiskWrite.bytes(ledger, first.encodeToByteArray(), keepPrevious = true)
      DiskWrite.bytes(ledger, second.encodeToByteArray(), keepPrevious = true)
      assertEquals(second, ledger.readText())
      assertEquals(first, DiskWrite.previousOf(ledger).readText())
      assertFalse(File(dir, "shop-data.json.tmp").exists())

      //  دفترِ بریده: هرگز «خالی» نیست و نسخهٔ سالمِ پیشین پیدا می‌شود
      DiskWrite.text(ledger, "{\"debtors\":[")
      assertEquals(LedgerRecovery.Read.Bad(LedgerRecovery.Damage.CORRUPT), LedgerRecovery.read(ledger, json, { it.readText() }))
      val found = assertNotNull(LedgerRecovery.best(LedgerRecovery.candidates(ledger, emptyList()), json) { it.readText() })
      assertEquals(LedgerRecovery.Source.PREVIOUS, found.source)
      assertEquals(listOf("نخست"), found.data.expenseCategories)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun signOutVaultSealsOnKeychainForTheSameAccountOnly() = onMemoryKeychain {
    val user = "ios-test-vault-user"
    SignOutVault.drop(IosContext, user)
    try {
      val op = SyncOp(
        opId = "ios-test-op-1", ts = 1L, table = "products", rowId = "p1", type = SyncOp.INSERT,
        fields = buildJsonObject { put("name", JsonPrimitive("روغن")) },
      )
      assertTrue(SignOutVault.seal(IosContext, user, "s1", listOf(op)))
      assertTrue(SignOutVault.has(IosContext, user))
      //  پاکتِ رمزدار است: متنِ op در پرونده نیست
      assertFalse(SignOutVault.file(IosContext, user).readBytes().decodeToString().contains("ios-test-op-1"))
      assertEquals(listOf("ios-test-op-1"), SignOutVault.open(IosContext, user)?.map { it.opId })
      assertNull(SignOutVault.open(IosContext, "ios-test-other-user"))
    } finally {
      SignOutVault.drop(IosContext, user)
    }
    assertFalse(SignOutVault.has(IosContext, user))
  }

  @Test
  fun visibleBackupFolderIsDocumentsBackups() {
    val dir = assertNotNull(Platform.defaultBackupDir(IosContext))
    assertTrue(dir.path.endsWith("/Documents/Backups"), dir.path)
    //  `BackupMirror.write` (و «خروجِ اجباری» ِ `SafeSignOut`) دیگر به «پوشه نیست» نمی‌خورد
    val folder = assertNotNull(BackupMirror.folder(IosContext))
    folder.mkdirs()
    assertTrue(folder.isDirectory)
    assertTrue(folder.canWrite())
  }

  /**
   *  کلیددانِ درونِ حافظهٔ همین فرآیند (`Keychain.memory`؛ اگر آزمونِ سرتاسری گذاشته، همان): test.kexe کلیددانِ iOS ندارد
   *  (OSStatus -25291، ‎docs/IOS-PLAN-fa.md‎) و پیش‌تر KEK ِ `AtRestKeysIos` به `KEY_UNAVAILABLE` می‌خورد ⇒ `seal` همیشه false.
   *  KEK، کلیددانِ دفتر (`filesDir/atrest/keyring`) و پاکت همان کدِ برنامه‌اند؛ کلیددانی که همین آزمون ساخت پس از آن برمی‌دارد.
   */
  private fun onMemoryKeychain(body: () -> Unit) {
    val memory = Keychain.memory
    val ring = File(File(IosContext.filesDir, "atrest"), "keyring")
    val hadRing = ring.exists()
    Keychain.memory = memory ?: HashMap()
    try {
      body()
    } finally {
      Keychain.memory = memory
      if (!hadRing) {
        for (suffix in listOf("", AtRestKeyring.BACKUP_SUFFIX, AtRestKeyring.SUSPECT_SUFFIX)) File(ring.path + suffix).delete()
        AtRest.keyring(IosContext).forget()
      }
    }
  }

  private fun restore(prefs: SharedPreferences, before: Map<String, Any?>) {
    val e = prefs.edit()
    for ((k, v) in before) {
      when (v) {
        is Boolean -> e.putBoolean(k, v)
        is Int -> e.putInt(k, v)
        is Long -> e.putLong(k, v)
        is Float -> e.putFloat(k, v)
        is String -> e.putString(k, v)
        else -> e.remove(k)
      }
    }
    e.commit()
  }
}
