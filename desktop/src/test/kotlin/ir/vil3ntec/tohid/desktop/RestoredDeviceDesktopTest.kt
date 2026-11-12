package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestScope
import ir.vil3ntec.tohid.core.sync.SyncReplica
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.DocSeries
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  DATA-06 — گوشیِ بازیابی‌شده از پشتیبانِ گوشیِ **دیگر** (`core/sync/RestoredDevice.kt`، `sync/v1/V1ReplicaAndroid.kt`) روی
 *  همان کدِ برنامه و دیسکِ واقعیِ کامپیوتر. «گوشیِ دیگر» = `device_uid` ِ دیگر در `tohid-sync` (پرونده‌ای که با پشتیبان
 *  نمی‌رود). دو گونهٔ بازیابی:
 *   • عادی (از این نسخه): `tohid-sync-v1` نیامد، دفتر و صف آمدند ⇒ شناسهٔ دستگاه روی ردِ کارِ پیشین ساخته می‌شود؛
 *   • رونوشتِ خام (ابزارِ انتقالِ سازنده، نسخهٔ پیشین): `tohid-sync-v1` با replica و cursor ِ گوشیِ پیشین آمد.
 *  آزمونِ «دو بار شمردن» با سرورِ واقعی: `server/test/e2e/journeys-b.e2e.mjs` (JB6). قاعده‌های پشتیبان هم این‌جا (روی
 *  لینوکس، بی SDK ِ اندروید) — همان `DeviceOnlyPrefsTest` ِ اندروید.
 */
class RestoredDeviceDesktopTest {

  private val ctx = DesktopContext
  private val n = System.nanoTime().toString(36)
  private val owner = "usr_dsown$n"
  private val staff = "usr_dsstf$n"
  private val shop = "shp_ds$n"
  private val old = "0123456789abcdef0123456789abcd${n.takeLast(2).padStart(2, '0')}"
  private val sync get() = SyncStore(ctx)
  private val engine get() = SyncV1Engine.of(ctx)
  private val syncPrefs get() = ctx.getSharedPreferences("tohid-sync", 0)
  private val v1Prefs get() = ctx.getSharedPreferences("tohid-sync-v1", 0)

  private var uidBefore: String? = null
  private var ownerBefore = ""
  private var restoredBefore = 0L
  private var accountBefore = ""
  private var deskBefore = "" to ""

  private val ledger = ShopData(products = listOf(Product(id = "rdd-p1$n", name = "برنج", salePrice = 120.0)))

  @Before fun before() {
    uidBefore = syncPrefs.getString("device_uid", null)
    ownerBefore = sync.replicaOwner
    restoredBefore = syncPrefs.getLong("restored_at", 0L)
    accountBefore = SyncStateV1.currentAccount(ctx)
    deskBefore = LedgerOwner.currentUser(ctx) to LedgerOwner.currentShop(ctx)
    sync.replicaOwner = ""
    syncPrefs.edit().remove("restored_at").commit()
  }

  @After fun after() {
    val e = syncPrefs.edit().putString("replica_owner", ownerBefore)
    if (uidBefore != null) e.putString("device_uid", uidBefore) else e.remove("device_uid")
    if (restoredBefore > 0) e.putLong("restored_at", restoredBefore) else e.remove("restored_at")
    e.commit()
    SyncStateV1.setCurrentAccount(ctx, accountBefore)
    LedgerOwner.remember(ctx, deskBefore.first, deskBefore.second)
    v1Prefs.edit().apply { v1Prefs.all.keys.filter { it.contains(n) }.forEach { remove(it) } }.commit()
    ctx.getSharedPreferences("tohid-doc-series", 0).edit().remove(owner).remove(staff).commit()
    ctx.filesDir.listFiles()?.filter { it.name.contains(n) }?.forEach { it.delete() }
    engine.rebind(force = true)
  }

  private fun use(account: String) { SyncStateV1.setCurrentAccount(ctx, account); engine.rebind(force = true) }
  private fun state() = SyncStateV1(ctx, engine.currentKey())

  /** «پشتیبانِ گوشیِ دیگر روی این دستگاه»: شناسهٔ دستگاه نیست ⇒ با نخستین خواندن روی ردِ کارِ پیشین ساخته می‌شود */
  private fun restoreOntoAnotherDevice(): String {
    syncPrefs.edit().remove("device_uid").commit()
    return sync.deviceUid
  }

  /** حالِ `tohid-sync-v1` ِ یک حساب، همان‌طور که رونوشتِ خامِ گوشیِ پیشین آن را آورد */
  private fun copiedState(account: String, replica: String) {
    use(account)
    val s = state()
    s.replica = replica
    s.cursor = 42L
    s.lastPushAt = 5L
    s.reconciledAt = 7L
    s.shadow = LedgerDiff.shadowOf(ledger).toString()
    v1Prefs.edit().putString("${engine.currentKey()}.device", old).commit()
  }

  @Test fun `قاعده‌های پشتیبان و عاملِ رمزِ دفتر tohid-sync-v1 را هم بیرون می‌گذارند`() {
    fun file(rel: String) = File("../android-native/app/src/main", rel).readText()
    fun excluded(xml: String) =
      Regex("""<exclude\s+domain="sharedpref"\s+path="([^"]+)"\s*/>""").findAll(xml).map { it.groupValues[1] }.toSet()
    val rules = file("res/xml/backup_rules.xml")
    val agentSrc = file("java/ir/vil3ntec/tohid/data/TohidBackupAgent.kt")
    val agent = Regex("\"([^\"]+)\"").findAll(Regex("""val EXCLUDED_PREFS = setOf\(([^)]*)\)""").find(agentSrc)!!.groupValues[1])
      .map { it.groupValues[1] }.toSet()
    val paths = mapOf(
      "cloud-backup" to excluded(rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")),
      "device-transfer" to excluded(rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")),
      "backup_rules_legacy" to excluded(file("res/xml/backup_rules_legacy.xml")),
      "TohidBackupAgent" to agent,
    )
    val deviceOnly = setOf(
      "tohid-sync.xml", "tohid-session.xml", "tohid-session-secure.xml", "tohid-staff-pins.xml", "tohid-doc-series.xml",
      "tohid-sync-v1.xml",
      //  OPS-02: پرچمِ رمزِ پشتیبانِ دستی (کلیدِ عبارت مالِ همین دستگاه است) و شکستِ پشتیبانِ پوشه
      "tohid-backup-device.xml",
    )
    for ((where, set) in paths) assertEquals(where, deviceOnly, set)
    //  رمزِ دفتر روشن: همان فهرست به‌اضافهٔ حالِ همگام‌سازی (`AtRestScope.SYNC_PREFS`) — هیچ‌کدام کمتر از XML نیست
    assertTrue(AtRestScope.SYNC_PREFS.all { it in agent })
    //  نامِ پروندهٔ سری در `V1Replica` همان `DocSeries` است (پاک کردنِ سریِ کهنه پس از حصار)
    val docSeries = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/sync/DocSeries.kt").readText()
    val replicaSrc = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/sync/v1/V1ReplicaAndroid.kt").readText()
    val name = Regex("""const val PREFS = "([^"]+)"""").find(docSeries)!!.groupValues[1]
    assertEquals(name, Regex("""const val DOC_SERIES = "([^"]+)"""").find(replicaSrc)!!.groupValues[1])
  }

  @Test fun `بازیابیِ عادی روی گوشیِ دیگر — کلیدِ صاحبِ میز، پایهٔ سایه پیش از نخستین ذخیره، شناسهٔ تازه، در انتظارِ بازسازی`() {
    LedgerOwner.remember(ctx, owner, shop)
    SyncStateV1.setCurrentAccount(ctx, "")
    val device = restoreOntoAnotherDevice()
    assertTrue("شناسه روی ردِ کارِ پیشین ساخته شد", sync.restoredAt > 0)
    engine.rebind(force = true)
    assertEquals("کلیدِ همگام‌سازی همان صاحبِ میز (صف و ژورنالِ پشتیبان)", owner, SyncStateV1.currentAccount(ctx))
    engine.recover(ledger)
    val s = state()
    assertTrue(s.restoredRebuild)
    assertEquals(device, s.device)
    assertTrue("پایهٔ سایه همین دفتر", s.shadow.isNotBlank())
    //  نخستین فروش/کالای پس از بازیابی op می‌شود (پیش از این اصلاح «پایه» می‌شد و هرگز نمی‌رفت)
    val next = ledger.copy(products = ledger.products + Product(id = "rdd-p2$n", name = "شکر", salePrice = 90.0))
    assertTrue(engine.record(next) >= 1)
    assertEquals("replica ِ تازه = شناسهٔ همین دستگاه", device, engine.deviceId())
    //  تا بازسازی: نه هم‌ترازی (بی شبکه هم چیزی نمی‌پرسد و دفتر دست نمی‌خورد)
    var wrote = false
    runBlocking { engine.prepare({ next }) { wrote = true } }
    assertFalse(wrote)
  }

  @Test fun `رونوشتِ خامِ حالِ گوشیِ دیگر — replica و سری از نو، در انتظارِ بازسازی`() {
    restoreOntoAnotherDevice()
    copiedState(owner, replica = old)
    ctx.getSharedPreferences("tohid-doc-series", 0).edit().putString(owner, "A1").commit()
    assertEquals("A1", DocSeries.current(ctx))
    val device = sync.deviceUid
    assertEquals(device, engine.deviceId())
    val s = state()
    assertTrue(s.restoredRebuild)
    assertEquals(device, s.device)
    assertEquals("سریِ گوشیِ پیشین کنار ⇒ از سرور با شناسهٔ تازه", "", DocSeries.current(ctx))
    assertEquals("cursor تا بازسازی دست نمی‌خورد (Pull بسته است)", 42L, s.cursor)
  }

  @Test fun `به‌روزرسانی از نسخهٔ پیشین هرگز نمی‌چرخاند`() {
    val device = sync.deviceUid
    //  به‌روزرسانی: شناسه از پیش بود (این‌جا شاید همین حالا ساخته شد — نشانِ بازیابی نه)
    syncPrefs.edit().remove("restored_at").commit()
    assertEquals(0L, sync.restoredAt)
    use(owner)
    val s = state()
    s.replica = device
    s.cursor = 9L
    s.lastPushAt = 5L
    s.shadow = LedgerDiff.shadowOf(ledger).toString()
    engine.recover(ledger)
    assertEquals(device, engine.deviceId())
    assertEquals("فقط بستن", device, state().device)
    assertFalse(state().restoredRebuild)
    assertEquals(9L, state().cursor)
    //  حسابِ دوم با شناسهٔ خودش ِ همین دستگاه
    use(staff)
    state().replica = SyncReplica.scoped(device, staff)
    engine.recover(ledger)
    assertEquals(SyncReplica.scoped(device, staff), engine.deviceId())
    assertFalse(state().restoredRebuild)
  }

  @Test fun `جداییِ حساب‌ها پس از حصار و اجرای دوباره همان نتیجه`() {
    restoreOntoAnotherDevice()
    val tag = SyncReplica.tag(staff)
    copiedState(owner, replica = old)
    val device = sync.deviceUid
    assertEquals(device, engine.deviceId())
    copiedState(staff, replica = "$old-$tag")
    val mine = engine.deviceId()
    assertEquals("$device-$tag", mine)
    assertNotEquals(device, mine)
    //  بستن و باز کردن (و دوباره هنگامِ باز شدنِ دفتر) ⇒ همان شناسه‌ها، همان «در انتظار»
    repeat(2) {
      engine.rebind(force = true)
      engine.recover(ledger)
      assertEquals(mine, engine.deviceId())
      assertTrue(state().restoredRebuild)
    }
    use(owner)
    engine.recover(ledger)
    assertEquals(device, engine.deviceId())
    assertEquals(owner, sync.replicaOwner)
  }

  @Test fun `بسته شدن میانِ حصار ⇒ اجرای بعد همان نتیجه`() {
    restoreOntoAnotherDevice()
    copiedState(owner, replica = old)
    //  گام‌های پیش از `commit` ِ یگانه نشستند (مالکِ خام، سری)، خودِ commit نه
    sync.replicaOwner = owner
    val s = state()
    assertEquals(old, s.replica)
    assertNotEquals(sync.deviceUid, s.device)
    //  باز شدنِ دوباره
    engine.rebind(force = true)
    engine.recover(ledger)
    assertEquals(sync.deviceUid, engine.deviceId())
    assertTrue(state().restoredRebuild)
    assertEquals(sync.deviceUid, state().device)
  }
}
