package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.platform.PhraseBackup
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.data.BackupRestore
import ir.vil3ntec.tohid.data.BackupSeal
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopSettingsBundle
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.SnapshotSeal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 *  OPS-02 — «رمزِ پشتیبانِ دستی و پوشه» درونِ برنامه (`BackupSeal`)، سرتاسری روی دیسکِ واقعیِ کامپیوتر و همان کدِ گوشی:
 *  پیش‌فرض خاموش و ZIP ِ ساده؛ روشن ⇒ همان پاکتِ عبارتِ بازیابی (`PhraseBackup`، قراردادِ `phrase-backup.js`) روی کلِ ZIP؛
 *  بازیابی با کلیدِ همین دستگاه یا فقط با عبارت روی دستگاهِ تازه؛ و ردِ امنِ «عبارتِ اشتباه» و «پشتیبانِ دست‌کاری‌شده»
 *  پیش از نوشتنِ هر چیزی. کلید باز نشد ⇒ پشتیبان ساخته نمی‌شود، هرگز ساده.
 */
class BackupSealDrillTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val ledger get() = File(dir, "shop-data.json")
  private val photos get() = File(dir, "product-photos")
  private val keys get() = File(File(dir, "atrest"), SnapshotSeal.KEYS_FILE)
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private val canon = Json { encodeDefaults = true }
  private val fixture = File("../android-native/app/src/test/resources/upgrade/stable-3.2.337/shop-data.json")
  private val prefs get() = ctx.getSharedPreferences("tohid", 0)
  private lateinit var mirrorDir: File
  private var folderBefore: String? = null
  private var mirrorBefore = true

  private fun dropKeys() =
    keys.parentFile?.listFiles().orEmpty().filter { it.name.startsWith(SnapshotSeal.KEYS_FILE) }.forEach { it.delete() }

  /** «برنامه برداشته شد»: دفتر، عکس‌ها، تنظیماتِ دکان و پوشهٔ موقت (پوشهٔ پشتیبان بیرون از برنامه است و می‌ماند) */
  private fun wipe() {
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") || it.name == "before-restore.json" }.forEach { it.deleteRecursively() }
    photos.deleteRecursively()
    ShopSettingsBundle.SHOP_PREFS.forEach { ctx.deleteSharedPreferences(it) }
    BackupBundle.dropStaging(ctx)
  }

  @Before fun before() {
    LedgerOwner.remember(ctx, "", "")
    SnapshotSeal.setOn(ctx, false)
    BackupSeal.setOn(ctx, false)
    dropKeys()
    folderBefore = prefs.getString("backup_folder", null)
    mirrorBefore = BackupMirror.enabled(ctx)
    mirrorDir = Files.createTempDirectory("backup-seal-").toFile()
    BackupMirror.setFolder(ctx, mirrorDir.path)
    wipe()
  }

  @After fun after() {
    SnapshotSeal.setOn(ctx, false)
    BackupSeal.setOn(ctx, false)
    dropKeys()
    wipe()
    BackupMirror.setFolder(ctx, folderBefore)
    BackupMirror.setEnabled(ctx, mirrorBefore)
    mirrorDir.deleteRecursively()
    LedgerOwner.remember(ctx, "", "")
  }

  /* ------------------------------ دکانِ آزمون ------------------------------ */

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private fun seed(): ShopStore {
    val store = open()
    val base = json.decodeFromString(ShopData.serializer(), fixture.readText())
    val d = base.copy(products = base.products + Product(id = "seal-p1", name = "زعفرانِ هرات", salePrice = 950.0, photo = true))
    assertTrue(runBlocking { store.save(d) })
    photos.mkdirs()
    File(photos, "seal-p1.jpg").writeBytes(ByteArray(2500) { i -> (i * 7).toByte() })
    ctx.getSharedPreferences("tohid-policy", 0).edit().putString("seal.note", "رسیدِ آزمون").commit()
    return store
  }

  private fun canonical(d: ShopData) = canon.encodeToString(ShopData.serializer(), d)

  /** عبارتِ تأییدشده ⇒ کلیدِ همین دستگاه (همان راهِ برگهٔ «عبارتِ بازیابی») و «پشتیبانِ دستی و پوشه هم رمزدار» */
  private fun sealOn(): String {
    val phrase = RecoveryPhrase.generate()
    SnapshotSeal.adopt(ctx, RecoveryPhrase.normalize(phrase)!!, iterations = RecoveryPhrase.ITERATIONS_MIN)
    assertTrue(SnapshotSeal.setOn(ctx, true))
    assertTrue(BackupSeal.setOn(ctx, true))
    return phrase
  }

  private fun fullBackup(store: ShopStore): ByteArray {
    val out = ByteArrayOutputStream()
    runBlocking { BackupRestore.writeFull(ctx, store, "دکانِ آزمون") { out } }
    return out.toByteArray()
  }

  private fun restore(store: ShopStore, bytes: ByteArray, typed: String? = null) =
    BackupRestore.open(ctx, store, ByteArrayInputStream(bytes), typed)

  private fun staged(): Boolean =
    File(ctx.cacheDir, "restore-photos").listFiles().orEmpty().isNotEmpty() || File(ctx.cacheDir, "restore-settings.json").exists()

  private fun latin(b: ByteArray) = String(b, Charsets.ISO_8859_1)

  /* ------------------------------ ۱) پیش‌فرض ------------------------------ */

  @Test fun `پیش‌فرض خاموش است، بی کلیدِ عبارت روشن نمی‌شود و پشتیبانِ دستی و پوشه همان ZIP ِ ساده است`() {
    assertFalse(BackupSeal.on(ctx))
    assertFalse("بی عبارتِ تأییدشده روشن نمی‌شود", BackupSeal.setOn(ctx, true))
    assertFalse(BackupSeal.on(ctx))
    val store = seed()
    val zip = fullBackup(store)
    assertArrayEquals("ZIP ِ ساده", byteArrayOf(0x50, 0x4B, 0x03, 0x04), zip.copyOf(4))
    val file = BackupMirror.write(ctx).getOrThrow()
    assertTrue(file.name.endsWith(".zip") && !file.name.endsWith(BackupSeal.SEALED_EXT))
    assertFalse(AtRestEnvelope.isSealed(file))
  }

  /* ------------------------------ ۲) رفت و برگشت ------------------------------ */

  @Test fun `پشتیبانِ دستیِ رمزدار بی متنِ باز است و با کلیدِ همین دستگاه یا فقط با عبارت روی دستگاهِ تازه کامل برمی‌گردد`() {
    val store = seed()
    val original = store.data.value
    val phrase = sealOn()
    val sealed = fullBackup(store)
    assertTrue(AtRestEnvelope.isSealed(sealed))
    assertTrue("KID ِ عبارتِ بازیابی، نه کلیدِ دستگاه", RecoveryPhrase.parseKid(AtRestEnvelope.keyIdOf(sealed)!!) != null)
    assertFalse("نامِ کالا و شناسه‌اش در فایل پیدا نیست", latin(sealed).contains("seal-p1") || latin(sealed).contains("shop-data.json"))

    //  همین دستگاه (کلید در کلیددان): بی پرسیدنِ عبارت
    assertEquals(original, restore(store, sealed).getOrThrow().data)
    BackupBundle.dropStaging(ctx)

    //  برنامه پاک شد و کلیدِ دستگاه هم (گوشیِ تازه): فقط عبارت
    wipe()
    dropKeys()
    val fresh = open()
    val locked = restore(fresh, sealed)
    assertTrue(BackupSeal.needsPhrase(locked.exceptionOrNull()))
    assertEquals(StrBackupSafety.fileNeedsPhrase, locked.exceptionOrNull()!!.message)
    val opened = restore(fresh, sealed, phrase.lowercase().replace("-", " ")).getOrThrow()
    assertEquals(1, opened.photos)
    assertTrue("تا تأیید هیچ چیزی ننشسته", fresh.data.value.products.isEmpty())
    val r = runBlocking { BackupRestore.confirm(ctx, fresh, opened.data) }
    assertTrue(r.message, r.ok)
    assertEquals("دفتر بایت‌به‌بایت", canonical(original), canonical(open().data.value))
    assertArrayEquals("عکس", ByteArray(2500) { i -> (i * 7).toByte() }, File(photos, "seal-p1.jpg").readBytes())
    assertEquals("رسیدِ آزمون", ctx.getSharedPreferences("tohid-policy", 0).getString("seal.note", null))
  }

  /* ------------------------------ ۳) ردِ امن ------------------------------ */

  @Test fun `عبارتِ اشتباه و پشتیبانِ دست‌کاری‌شده یا بریده رد می‌شوند و هیچ چیزی نوشته نمی‌شود`() {
    val store = seed()
    val phrase = sealOn()
    val sealed = fullBackup(store)
    wipe()
    val fresh = open()
    val ledgerBefore = ledger.takeIf { it.exists() }?.readBytes()

    fun refused(bytes: ByteArray, typed: String?, expect: String, why: String) {
      val e = restore(fresh, bytes, typed).exceptionOrNull()
      assertEquals(why, expect, e?.message)
      assertTrue("$why: دفتر خالی ماند", fresh.data.value.products.isEmpty())
      assertFalse("$why: هیچ عکس یا تنظیمی کنار گذاشته نشد", staged())
      assertArrayEquals("$why: دفترِ روی دیسک دست نخورد", ledgerBefore, ledger.takeIf { it.exists() }?.readBytes())
    }

    //  عبارتِ دیگر (رقمِ وارسیِ درست) و عبارتِ غلط‌نوشته
    refused(sealed, RecoveryPhrase.generate(), StrBackupSafety.wrongPhraseFile, "عبارتِ دیگر")
    refused(sealed, phrase.dropLast(1) + (if (phrase.last() == '0') '1' else '0'), StrBackupSafety.wrongPhraseFile, "غلطِ تایپی")
    //  یک بیت در دلِ رمزنوشته، در برچسب، در سرآیند (زیرِ AAD)؛ و بریده
    val body = sealed.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
    refused(body, phrase, StrBackupSafety.wrongPhraseFile, "بیتِ رمزنوشته")
    val tag = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x80).toByte() }
    refused(tag, phrase, StrBackupSafety.wrongPhraseFile, "برچسب")
    val nonceAt = AtRestEnvelope.MAGIC.size + 2 + AtRestEnvelope.keyIdOf(sealed)!!.length
    val header = sealed.copyOf().also { it[nonceAt] = (it[nonceAt].toInt() xor 1).toByte() }
    refused(header, phrase, StrBackupSafety.wrongPhraseFile, "سرآیند")
    refused(sealed.copyOf(sealed.size - 7), phrase, StrBackupSafety.wrongPhraseFile, "بریده")
    //  تکرارِ بیرون از بازه در KID (فایلِ دست‌ساز برنامه را با PBKDF2 ِ بی‌پایان قفل نمی‌کند): پیش از ساختنِ هر کلیدی رد
    val kid = AtRestEnvelope.keyIdOf(sealed)!!
    val greedy = kid.replace(Regex("""^rp1\.\d+\."""), "rp1.9999999.").toByteArray(Charsets.US_ASCII)
    val forged = AtRestEnvelope.MAGIC + byteArrayOf(1, greedy.size.toByte()) + greedy + sealed.copyOfRange(nonceAt, sealed.size)
    refused(forged, phrase, StrBackupSafety.damaged, "تکرارِ دست‌ساز")
    //  پاکتِ کلیدِ دستگاه (نه عبارت) و سرآیندِ تنها: هیچ عبارتی بازش نمی‌کند ⇒ «خراب»
    val deviceSealed = AtRestEnvelope.seal(ByteArray(64) { 1 }, "dev1", SecretKeySpec(ByteArray(32) { 3 }, "AES"), SecureRandom())
    refused(deviceSealed, null, StrBackupSafety.damaged, "کلیدِ دستگاه")
    refused(AtRestEnvelope.MAGIC + byteArrayOf(1), null, StrBackupSafety.damaged, "سرآیندِ تنها")

    //  کلیدِ همین دستگاه هست (wipe کلیددان را نمی‌برد) ولی فایل دست‌خورده ⇒ «خراب»، نه پرسیدنِ عبارت
    assertTrue(SnapshotSeal.keyReady(ctx))
    refused(body, null, StrBackupSafety.damaged, "دست‌خورده با کلیدِ همین دستگاه")
    refused(sealed.copyOf(sealed.size - 7), null, StrBackupSafety.damaged, "بریده با کلیدِ همین دستگاه")
    //  و همان فایلِ سالم پس از این همه رد هنوز باز می‌شود
    assertTrue(restore(fresh, sealed, phrase).isSuccess)
  }

  /* ------------------------------ ۴) پوشه ------------------------------ */

  @Test fun `پوشه رمزدار می‌نویسد، فهرست هر دو را می‌شمارد و بی کلید هیچ فایلی نمی‌سازد، نه ساده`() {
    val store = seed()
    val plain = BackupMirror.write(ctx).getOrThrow()
    val phrase = sealOn()
    val sealedFile = BackupMirror.write(ctx).getOrThrow()
    assertTrue(sealedFile.name.endsWith(BackupSeal.SEALED_EXT))
    assertTrue(AtRestEnvelope.isSealed(sealedFile))
    assertEquals("ساده و رمزدار هر دو در فهرست (هرسِ ۳۰ تایی)", setOf(plain.name, sealedFile.name), BackupMirror.list(ctx).map { it.name }.toSet())
    assertEquals(store.data.value, restore(store, sealedFile.readBytes(), phrase).getOrThrow().data)
    BackupBundle.dropStaging(ctx)

    //  کلیدِ دستگاه باز نشد ⇒ نه پشتیبانِ پوشه، نه دستی؛ مقصدِ دستی هرگز باز نمی‌شود؛ پشتیبان‌های پیشین سرِ جایشان
    keys.writeBytes(keys.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() })
    assertFalse(SnapshotSeal.keyReady(ctx))
    val before = mirrorDir.listFiles().orEmpty().map { it.name }.toSet()
    Thread.sleep(1100)
    val failed = BackupMirror.write(ctx)
    assertEquals(StrBackupSafety.backupSealKeyMissing, failed.exceptionOrNull()?.message)
    assertEquals("هیچ فایلِ تازه یا .part", before, mirrorDir.listFiles().orEmpty().map { it.name }.toSet())
    var opened = false
    val manual = runCatching { runBlocking { BackupRestore.writeFull(ctx, store, "دکان") { opened = true; ByteArrayOutputStream() } } }
    assertEquals(StrBackupSafety.backupSealKeyMissing, manual.exceptionOrNull()?.message)
    assertFalse("مقصدِ دستی باز نشد", opened)
    //  خاموش ⇒ همان ZIP ِ ساده
    assertTrue(BackupSeal.setOn(ctx, false))
    assertFalse(AtRestEnvelope.isSealed(BackupMirror.write(ctx).getOrThrow()))
  }

  /* ------------------------------ ۵) هم‌قرارداد ------------------------------ */

  @Test fun `پاکتِ جریانی بایت‌به‌بایت همان PhraseBackup_seal است و با عبارت باز می‌شود`() {
    val canonical = RecoveryPhrase.normalize(RecoveryPhrase.generate())!!
    val (kid, key) = PhraseBackup.newKey(canonical, SecureRandom(), RecoveryPhrase.ITERATIONS_MIN)
    val plain = ByteArray(200_003) { i -> (i * 13 + 5).toByte() }
    fun seeded() = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42L) }
    val whole = PhraseBackup.seal(plain, kid, key, seeded())
    val out = ByteArrayOutputStream()
    PhraseBackup.sealing(out, kid, key, seeded()).use { s ->
      //  تکه‌های ناهم‌اندازه، همان شکلِ نوشتنِ ZipOutputStream
      var at = 0
      var step = 1
      while (at < plain.size) { val n = minOf(step, plain.size - at); s.write(plain, at, n); at += n; step = step * 3 % 8191 + 1 }
    }
    assertArrayEquals(whole, out.toByteArray())
    assertArrayEquals(plain, PhraseBackup.open(out.toByteArray(), canonical))
  }
}
