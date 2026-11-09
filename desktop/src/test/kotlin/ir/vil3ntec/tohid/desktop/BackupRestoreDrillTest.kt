package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.backup.BackupStamp
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.BackupRestore
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldInvoiceCodec
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldInvoices
import ir.vil3ntec.tohid.data.HeldTag
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.LedgerRecovery
import ir.vil3ntec.tohid.data.OwnerExport
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SaleDraftCodec
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopSettingsBundle
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.SnapshotSeal
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 *  OPS-02 — «بازیابیِ آزموده»، سرتاسری روی دیسکِ واقعیِ کامپیوتر و **همان کدِ** گوشی و آیفون (`BackupRestore`، همان
 *  که صفحهٔ «داده‌ها» صدا می‌زند): دفترِ واقعیِ دکان (کالا، فروش و قلم، برگشت، قرض و پرداخت، خرید و تأمین‌کننده، گدام،
 *  حرکتِ موجودی، تاریخچهٔ قیمت، سابقه — دفترِ ساختِ پایدارِ ۳.۲.۳۳۷)، تنظیماتِ دکان از هر نوع و عکسِ کالا ⇐ پشتیبانِ
 *  کامل و نسخهٔ خودکار ⇐ پاک شدنِ دادهٔ برنامه ⇐ برگرداندن ⇐ دفتر (JSON ِ استاندارد) و تنظیمات و عکس‌ها بایت‌به‌بایت همان.
 *  و راه‌های شکست: فایلِ بریده/خراب، نسخهٔ کهنه/تازه‌تر، دکانِ دیگر، صفِ نرفته، و رمزِ نسخه‌ها با عبارتِ بازیابی.
 *
 *  و فاکتورهای نگه‌داشته و سبدِ جاری (`held-invoices.json`، `sale-draft.json`؛ DATA-SAFETY §۸): با پشتیبانِ کامل برمی‌گردند،
 *  فقط پس از نشستنِ دفتر، فقط در همان دکان، فقط روی دستگاهی که خودش فاکتورِ بازی ندارد، و هرگز آن‌که فروش رفته.
 */
class BackupRestoreDrillTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val ledger get() = File(dir, "shop-data.json")
  private val photos get() = File(dir, "product-photos")
  private val keys get() = File(File(dir, "atrest"), SnapshotSeal.KEYS_FILE)
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private val canon = Json { encodeDefaults = true }
  private val fixture = File("../android-native/app/src/test/resources/upgrade/stable-3.2.337/shop-data.json")
  private val prefsBefore = HashMap<String, Map<String, Any?>>()

  /* ------------------------------ پاک کردن ------------------------------ */

  /** «برنامه برداشته شد»: دفتر و همراه‌هایش، عکس‌ها، نسخه‌های خودکار، تنظیماتِ دکان، صفِ همین کلید و پوشهٔ موقت */
  private fun wipe(snapshots: Boolean = true) {
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name == "before-restore.json" || it.name.startsWith("oplog-${OpQueue.fileKey(key())}") }
      .forEach { it.deleteRecursively() }
    photos.deleteRecursively()
    if (snapshots) AutoBackup.dir(ctx).listFiles().orEmpty().forEach { it.delete() }
    ShopSettingsBundle.SHOP_PREFS.forEach { ctx.deleteSharedPreferences(it) }
    BackupBundle.dropStaging(ctx)
    //  فاکتورهای نگه‌داشته و سبدِ جاری — پرونده و حافظهٔ برنامه
    dir.listFiles().orEmpty().filter { it.name.startsWith(HeldInvoiceStore.FILE) || it.name.startsWith(CartStore.FILE) }.forEach { it.delete() }
    HeldInvoices.reset()
    ir.vil3ntec.tohid.data.HeldSync.cart = null
  }

  private fun key(): String {
    val sync = ir.vil3ntec.tohid.sync.SyncStore(ctx)
    return SyncStateV1.currentAccount(ctx).ifBlank { sync.accountName }.ifBlank { sync.deviceUid }
  }

  private fun neverSynced() { SyncStateV1(ctx, key()).apply { cursor = 0L; reconciledAt = 0L; snapshotAt = 0L } }

  @Before fun before() {
    ShopSettingsBundle.SHOP_PREFS.forEach { prefsBefore[it] = HashMap(ctx.getSharedPreferences(it, 0).all) }
    LedgerOwner.remember(ctx, "", "")
    SnapshotSeal.setOn(ctx, false)
    keys.parentFile?.listFiles().orEmpty().filter { it.name.startsWith(SnapshotSeal.KEYS_FILE) }.forEach { it.delete() }
    wipe()
    neverSynced()
  }

  @After fun after() {
    SnapshotSeal.setOn(ctx, false)
    keys.parentFile?.listFiles().orEmpty().filter { it.name.startsWith(SnapshotSeal.KEYS_FILE) }.forEach { it.delete() }
    wipe()
    neverSynced()
    LedgerOwner.remember(ctx, "", "")
    //  تنظیم‌هایی که پیش از این آزمون بودند (آزمون‌های دیگرِ همین JVM)
    for ((name, all) in prefsBefore) {
      val e = ctx.getSharedPreferences(name, 0).edit()
      for ((k, v) in all) when (v) {
        is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v)
        is Float -> e.putFloat(k, v); is Boolean -> e.putBoolean(k, v)
        is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
      }
      e.commit()
    }
  }

  /* ------------------------------ دکانِ آزمون ------------------------------ */

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private fun shop(): ShopData = json.decodeFromString(ShopData.serializer(), fixture.readText())

  private fun photoBytes(id: String) = ByteArray(3000) { i -> ((i * 31) xor id.hashCode()).toByte() }

  /** دفترِ پایدار + کالای تازه با عکس + هر گونه تنظیمِ دکان */
  private fun seed(): ShopStore {
    val store = open()
    val base = shop()
    val d = base.copy(products = base.products + Product(id = "drill-p9", name = "روغنِ آفتاب‌گردان", salePrice = 310.0, photo = true))
    assertTrue(runBlocking { store.save(d) })
    photos.mkdirs()
    d.products.filter { it.photo }.forEach { File(photos, "${it.id}.jpg").writeBytes(photoBytes(it.id)) }
    ctx.getSharedPreferences("tohid-policy", 0).edit()
      .putLong("drill.rounding", 500L).putString("drill.note", "سپاس").putFloat("drill.fee", 1.5f).putInt("drill.days", 7).commit()
    ctx.getSharedPreferences("tohid-receipt", 0).edit().putStringSet("drill.lines", setOf("الف", "ب")).putBoolean("drill.qr", true).commit()
    ctx.getSharedPreferences("tohid-modules", 0).edit().putBoolean("drill.giftcards", true).commit()
    return store
  }

  private fun canonical(d: ShopData) = canon.encodeToString(ShopData.serializer(), d)

  private fun prefsNow(): Map<String, Map<String, String>> =
    ShopSettingsBundle.SHOP_PREFS.associateWith { n ->
      ctx.getSharedPreferences(n, 0).all.mapValues { (_, v) ->
        val text = if (v is Set<*>) v.map { it.toString() }.sorted().toString() else v.toString()
        "${v?.let { it::class.simpleName }}:$text"
      }.toSortedMap()
    }

  private fun photosNow(): Map<String, String> =
    photos.listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes().contentHashCode().toString() + ":" + it.length() }

  private fun fullBackup(store: ShopStore): ByteArray {
    val out = ByteArrayOutputStream()
    runBlocking { BackupRestore.writeFull(ctx, store, "دکانِ آزمون") { out } }
    return out.toByteArray()
  }

  private fun openZip(store: ShopStore, bytes: ByteArray) = BackupRestore.open(ctx, store, ByteArrayInputStream(bytes))

  private fun staged(): Boolean =
    File(ctx.cacheDir, "restore-photos").listFiles().orEmpty().isNotEmpty() || File(ctx.cacheDir, "restore-settings.json").exists() ||
      File(ctx.cacheDir, "restore-held.json").exists()

  /* ------------------------- ۱) رفت و برگشتِ کامل ------------------------- */

  @Test fun `پشتیبانِ کامل پس از پاک شدنِ برنامه دفتر و تنظیمات و عکس‌ها را بایت‌به‌بایت برمی‌گرداند`() {
    val store = seed()
    val original = store.data.value
    val ledgerJson = canonical(original)
    val prefs = prefsNow()
    val pics = photosNow()
    assertTrue("دفترِ واقعی", original.sales.isNotEmpty() && original.saleItems.isNotEmpty() && original.saleReturns.isNotEmpty() &&
      original.transactions.isNotEmpty() && original.stockMovements.isNotEmpty() && original.purchases.isNotEmpty())
    assertEquals(2, pics.size)
    val zip = fullBackup(store)

    wipe()
    val fresh = open()
    assertTrue("برنامهٔ تازه دفترِ خالی دارد", fresh.data.value.products.isEmpty() && photosNow().isEmpty())
    val opened = openZip(fresh, zip).getOrThrow()
    assertEquals(BackupStamp.FORMAT_VERSION, opened.stamp.formatVersion)
    assertEquals(2, opened.photos)
    assertTrue("تا تأیید هیچ چیزی ننشسته", fresh.data.value.products.isEmpty() && photosNow().isEmpty())
    val r = runBlocking { BackupRestore.confirm(ctx, fresh, opened.data) }
    assertTrue(r.message, r.ok)
    assertEquals(2, r.photos)

    assertEquals(original, fresh.data.value)
    assertEquals("دفتر بایت‌به‌بایت", ledgerJson, canonical(fresh.data.value))
    assertEquals("دفترِ روی دیسک هم", ledgerJson, canonical(open().data.value))
    assertEquals("تنظیماتِ دکان", prefs, prefsNow())
    assertEquals("عکس‌ها", pics, photosNow())
    assertFalse("پوشهٔ موقت خالی شد", staged())
  }

  @Test fun `نسخهٔ خودکار پس از گم شدنِ دفتر برمی‌گردد`() {
    val store = seed()
    val original = store.data.value
    val snap = AutoBackup.take(ctx, "2026-11-09").getOrThrow()
    assertNotNull(snap)
    wipe(snapshots = false)
    val fresh = open()
    val back = BackupRestore.openSnapshot(ctx, fresh, snap!!).getOrThrow()
    assertEquals(original, back)
    assertTrue(runBlocking { BackupRestore.confirm(ctx, fresh, back) }.ok)
    //  عکس‌ها با برنامه رفتند ⇒ پرچمِ عکس با دیسک یکی (همان رفتارِ همیشه)؛ بقیهٔ دفتر بایت‌به‌بایت
    assertEquals(canonical(original.copy(products = original.products.map { it.copy(photo = false) })), canonical(open().data.value))
  }

  /* ------------------------- ۲) بریده و خراب ------------------------- */

  @Test fun `پشتیبانِ بریده سرِ هر مرز یا خراب رد می‌شود با پیامِ روشن و هیچ چیزی عوض نمی‌شود`() {
    val store = seed()
    val zip = fullBackup(store)
    fun at(sig: Int) = (0 until zip.size - 4).filter {
      zip[it] == 0x50.toByte() && zip[it + 1] == 0x4B.toByte() && zip[it + 2] == (sig shr 8).toByte() && zip[it + 3] == (sig and 0xFF).toByte()
    }
    val cuts = (at(0x0304).filter { it > 0 } + at(0x0102) + at(0x0506) + listOf(0, 3, 30, zip.size / 3, zip.size / 2, zip.size - 1)).distinct()
    assertTrue("هر قلم و فهرستِ پایانی", cuts.size >= 8)
    val ledgerBytes = ledger.readBytes()
    val prefs = prefsNow()
    val pics = photosNow()
    val flipped = zip.copyOf().also { it[at(0x0304)[1] + 60] = (it[at(0x0304)[1] + 60].toInt() xor 0x5A).toByte() }

    val inputs = cuts.map { "cut@$it" to zip.copyOf(it) } + listOf(
      "flip" to flipped,
      "json-cut" to "{\"products\":[{\"id\":\"p".toByteArray(),
      "garbage" to "not a backup at all".toByteArray(),
    )
    for ((what, bytes) in inputs) {
      val res = openZip(store, bytes)
      assertTrue("پذیرفته شد: $what", res.isFailure)
      assertEquals(what, StrBackupSafety.damaged, res.exceptionOrNull()?.message)
      assertArrayEquals("دفتر دست نخورد: $what", ledgerBytes, ledger.readBytes())
      assertEquals(what, prefs, prefsNow())
      assertEquals(what, pics, photosNow())
      assertFalse("پوشهٔ موقت: $what", staged())
    }
    //  همان فایلِ کامل پس از این‌همه هنوز باز می‌شود
    assertTrue(openZip(store, zip).isSuccess)
  }

  /* ------------------------- ۳) نسخهٔ کهنه و تازه‌تر ------------------------- */

  @Test fun `پشتیبانِ نسخه‌های کهنه باز می‌شود و قالبِ تازه‌تر رد می‌شود`() {
    val store = seed()
    val old = shop()
    //  JSON ِ سادهٔ ساختِ پایدارِ ۳.۲.۳۳۷ (بی exportedAt، بی مُهر)
    val plain = openZip(store, fixture.readBytes()).getOrThrow()
    assertEquals(old, plain.data)
    assertEquals(BackupStamp.LEGACY, plain.stamp)
    //  ZIP ِ پیش از تنظیمات و مُهر: فقط دفتر و عکس
    val legacyZip = ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("shop-data.json")); z.write(fixture.readBytes()); z.closeEntry()
        z.putNextEntry(ZipEntry("photos/p1.jpg")); z.write(photoBytes("p1")); z.closeEntry()
      }
    }.toByteArray()
    val legacy = openZip(store, legacyZip).getOrThrow()
    assertEquals(old, legacy.data)
    assertEquals(1, legacy.photos)
    BackupBundle.dropStaging(ctx)
    //  خروجیِ کاملِ مالک (OPS-08): نسخهٔ ۱ باز می‌شود، نسخهٔ ۹۹ رد (پیش از این بی‌صدا نیمه‌خوانده می‌نشست)
    val meta = OwnerExport.Meta(1_790_000_000_000L, "desktop", "test", "دکانِ آزمون")
    val v1 = openZip(store, OwnerExport.json(old, meta).toByteArray()).getOrThrow()
    assertEquals(old.products, v1.data.products)
    assertEquals(old.sales, v1.data.sales)
    val v99 = """{"format":"tohid-owner-export","formatVersion":99,"products":[{"id":"x1","name":"n"}]}"""
    assertEquals(StrAppData.exportNewer(99), openZip(store, v99.toByteArray()).exceptionOrNull()?.message)
    //  بستهٔ مُهرخورده با قالبِ تازه‌تر
    val future = ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry(BackupStamp.ENTRY)); z.write("""{"formatVersion":99,"user":"","shop":""}""".toByteArray()); z.closeEntry()
        z.putNextEntry(ZipEntry("shop-data.json")); z.write(fixture.readBytes()); z.closeEntry()
      }
    }.toByteArray()
    assertEquals(StrBackupSafety.newerApp, openZip(store, future).exceptionOrNull()?.message)
    //  JSON ِ درست ولی نه دفترِ این نسخه
    assertEquals(StrBackupSafety.newerApp, openZip(store, """{"products":"x","exportedAt":"2026"}""".toByteArray()).exceptionOrNull()?.message)
  }

  /* ------------------------- ۴) دکانِ دیگر ------------------------- */

  @Test fun `پشتیبانِ دکانِ دیگر رد می‌شود و دفترِ این دکان دست نمی‌خورد`() {
    LedgerOwner.remember(ctx, "u-a", "s-a")
    val store = seed()
    val zip = fullBackup(store)
    val ledgerBytes = ledger.readBytes()
    assertEquals(BackupStamp.Stamp(BackupStamp.FORMAT_VERSION, "u-a", "s-a"), openZip(store, zip).getOrThrow().stamp)
    BackupBundle.dropStaging(ctx)

    LedgerOwner.remember(ctx, "u-b", "s-b")
    val other = openZip(store, zip)
    assertEquals(StrBackupSafety.otherShop, other.exceptionOrNull()?.message)
    assertFalse(staged())
    assertArrayEquals(ledgerBytes, ledger.readBytes())
    //  کارمندِ همان دکان، دفترِ بی‌صاحب، و پشتیبانِ کهنهٔ بی مُهر — همان رفتارِ پیشین
    LedgerOwner.remember(ctx, "u-staff", "s-a")
    assertTrue(openZip(store, zip).isSuccess)
    LedgerOwner.remember(ctx, "", "")
    assertTrue(openZip(store, zip).isSuccess)
    LedgerOwner.remember(ctx, "u-b", "s-b")
    assertTrue(openZip(store, fixture.readBytes()).isSuccess)
  }

  /* ------------------------- ۵) صفِ همگام‌سازیِ نرفته ------------------------- */

  @Test fun `بازیابی با صفِ نرفته هیچ opی را گم نمی‌کند و چیزی را دو بار نمی‌نشاند`() {
    val store = seed()
    val engine = SyncV1Engine.of(ctx)
    val s1 = store.data.value.sales.first().copy(id = "drill-s9", invoiceNumber = 9001)
    assertTrue(runBlocking { store.save(store.data.value.copy(sales = store.data.value.sales + s1)) })
    val backup = store.data.value
    val zip = fullBackup(store)
    val s2 = s1.copy(id = "drill-s10", invoiceNumber = 9002)
    assertTrue(runBlocking { store.save(backup.copy(sales = backup.sales + s2)) })
    val after = store.data.value
    val queued = engine.queuedOps().map { it.opId }
    assertTrue("صف چیزی دارد", queued.isNotEmpty())

    val r = runBlocking { BackupRestore.confirm(ctx, store, openZip(store, zip).getOrThrow().data) }
    assertTrue(r.message, r.ok)
    assertEquals(backup, store.data.value)
    assertEquals("هیچ opی گم یا افزوده نشد", queued, engine.queuedOps().map { it.opId })
    assertEquals("سایه همان دفترِ بازیابی‌شده: هیچ تفاضلی", 0, engine.record(store.data.value))
    //  فروشِ پس از پشتیبان در نسخهٔ ایمنی است و «برگرداندن» بی op بازش می‌آورد
    assertTrue(runBlocking { store.undoRestore() }.isSuccess)
    assertEquals(after, store.data.value)
    assertEquals(queued, engine.queuedOps().map { it.opId })
    assertEquals(0, engine.record(store.data.value))
  }

  @Test fun `بازیابیِ ردشده روی حسابِ همگامِ بی ورود عکس و تنظیم و دفتر و صف را دست نمی‌زند`() {
    val store = seed()
    val zip = fullBackup(store)
    val p = shop().products.first { it.photo }
    File(photos, "${p.id}.jpg").writeBytes(ByteArray(1500) { 9 })
    ctx.getSharedPreferences("tohid-policy", 0).edit().remove("drill.rounding").commit()
    val ledgerBytes = ledger.readBytes()
    val pics = photosNow()
    val prefs = prefsNow()
    val engine = SyncV1Engine.of(ctx)
    val queued = engine.queuedOps().map { it.opId }
    SyncStateV1(ctx, key()).cursor = 5L
    val r = runBlocking { BackupRestore.confirm(ctx, store, openZip(store, zip).getOrThrow().data) }
    SyncStateV1(ctx, key()).cursor = 0L
    assertFalse(r.ok)
    assertEquals(ShopStore.NEED_SIGN_IN_SYNCED, r.message)
    assertArrayEquals(ledgerBytes, ledger.readBytes())
    assertEquals("عکسِ تازه‌تر با عکسِ کهنهٔ پشتیبان رونویسی نشد", pics, photosNow())
    assertEquals("تنظیمی ننشست", prefs, prefsNow())
    assertEquals(queued, engine.queuedOps().map { it.opId })
    assertFalse(staged())
  }

  /* ------------------------- ۶) رمزِ نسخه‌های خودکار ------------------------- */

  private fun sealWith(): String {
    val phrase = RecoveryPhrase.generate()
    SnapshotSeal.adopt(ctx, RecoveryPhrase.normalize(phrase)!!, iterations = RecoveryPhrase.ITERATIONS_MIN)
    assertTrue(SnapshotSeal.setOn(ctx, true))
    return phrase
  }

  @Test fun `رمزِ نسخه‌ها پیش‌فرض خاموش است و بی کلید روشن نمی‌شود`() {
    assertFalse(SnapshotSeal.on(ctx))
    assertFalse("بی عبارتِ تأییدشده روشن نمی‌شود", SnapshotSeal.setOn(ctx, true))
    assertFalse(SnapshotSeal.on(ctx))
    seed()
    val snap = AutoBackup.take(ctx, "2026-11-09").getOrThrow()!!
    assertFalse(SnapshotSeal.isPhraseSealed(snap))
    assertArrayEquals("خاموش ⇒ همان رونوشتِ خامِ پیشین", ledger.readBytes(), snap.readBytes())
  }

  @Test fun `نسخهٔ رمزدار با عبارت ساخته می‌شود فقط با تغییر و پس از گم شدنِ دفتر برمی‌گردد`() {
    val store = seed()
    val original = store.data.value
    val oldRaw = AutoBackup.take(ctx, "2026-11-01").getOrThrow()!!
    sealWith()
    assertNull("دفتر عوض نشده ⇒ نسخهٔ تازه نیست (متنِ باز سنجیده می‌شود، نه بایتِ رمزدار)", AutoBackup.take(ctx, "2026-11-09").getOrThrow())
    val more = original.copy(products = original.products + Product(id = "drill-p10", name = "چای", salePrice = 80.0))
    assertTrue(runBlocking { store.save(more) })
    val snap = AutoBackup.take(ctx, "2026-11-09").getOrThrow()!!
    assertTrue(SnapshotSeal.isPhraseSealed(snap))
    assertFalse("متنِ دفتر در نسخه پیدا نیست", String(snap.readBytes(), Charsets.ISO_8859_1).contains("drill-p10"))
    assertNull("بی تغییر ⇒ هیچ", AutoBackup.take(ctx, "2026-11-09").getOrThrow())
    //  خودِ دفتر هرگز با این کلید رمز نمی‌شود
    assertFalse(SnapshotSeal.isPhraseSealed(ledger))
    assertTrue(ledger.readText().startsWith("{"))
    //  کوچِ «رمزِ دفتر» به نسخهٔ رمزدار با عبارت دست نمی‌زند؛ نسخهٔ سادهٔ پیشین در فهرستش هست
    assertTrue(AtRest.files(ctx).none { it.name == snap.name } && AtRest.files(ctx).any { it.name == oldRaw.name })

    wipe(snapshots = false)
    val fresh = open()
    assertEquals(more, BackupRestore.openSnapshot(ctx, fresh, snap).getOrThrow())
    assertEquals("نسخهٔ سادهٔ پیشین هنوز باز می‌شود", original, BackupRestore.openSnapshot(ctx, fresh, oldRaw).getOrThrow())
    assertTrue(runBlocking { BackupRestore.confirm(ctx, fresh, BackupRestore.openSnapshot(ctx, fresh, snap).getOrThrow()) }.ok)
    assertEquals(canonical(more.copy(products = more.products.map { it.copy(photo = false) })), canonical(open().data.value))
  }

  @Test fun `گم شدنِ کلیدِ دستگاه نسخهٔ رمزدار را نمی‌برد — با عبارت باز می‌شود و نسخه‌های بعد بی‌وقفه گرفته می‌شوند`() {
    val store = seed()
    val phrase = sealWith()
    val more = store.data.value.copy(products = store.data.value.products + Product(id = "drill-p11", name = "نمک"))
    assertTrue(runBlocking { store.save(more) })
    val snap = AutoBackup.take(ctx, "2026-11-09").getOrThrow()!!
    assertTrue(SnapshotSeal.isPhraseSealed(snap))

    //  Keystore پاک شد / گوشیِ تازه از پشتیبانِ اندروید: پروندهٔ کلید باز نمی‌شود
    assertTrue(keys.renameTo(File(keys.parentFile, "${SnapshotSeal.KEYS_FILE}.gone")))
    assertFalse(SnapshotSeal.keyReady(ctx))
    val locked = BackupRestore.openSnapshot(ctx, store, snap)
    assertTrue(BackupRestore.needsPhrase(locked.exceptionOrNull(), snap))
    assertTrue("عبارتِ دیگر باز نمی‌کند", BackupRestore.openSnapshot(ctx, store, snap, RecoveryPhrase.generate()).isFailure)
    assertEquals("فقط عبارت، به هر شکلِ نوشتن", more, BackupRestore.openSnapshot(ctx, store, snap, phrase.lowercase().replace("-", " ")).getOrThrow())

    //  نسخهٔ بعدی بی‌وقفه: همان رونوشتِ خامِ دفترِ کنارش، و صفحه می‌گوید کلید باز نشد
    val next = more.copy(products = more.products + Product(id = "drill-p12", name = "شکر"))
    assertTrue(runBlocking { store.save(next) })
    val raw = AutoBackup.take(ctx, "2026-11-10").getOrThrow()!!
    assertFalse(SnapshotSeal.isPhraseSealed(raw))
    assertArrayEquals(ledger.readBytes(), raw.readBytes())
    assertEquals(StrBackupSafety.sealKeyMissing, SnapshotSeal.lastError)

    //  عبارتِ تازه ⇒ کلیدِ تازه؛ پروندهٔ کلیدِ ناخوانا پاک نمی‌شود، کنار می‌رود
    keys.parentFile!!.listFiles().orEmpty().first { it.name.endsWith(".gone") }.renameTo(keys)
    keys.writeBytes(keys.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() })
    assertFalse(SnapshotSeal.keyReady(ctx))
    SnapshotSeal.adopt(ctx, RecoveryPhrase.normalize(RecoveryPhrase.generate())!!, iterations = RecoveryPhrase.ITERATIONS_MIN)
    assertTrue(SnapshotSeal.keyReady(ctx))
    assertTrue(keys.parentFile!!.listFiles().orEmpty().any { it.name.startsWith("${SnapshotSeal.KEYS_FILE}.lost-") })
  }

  @Test fun `دفترِ خراب خودکار از نسخهٔ رمزدار با کلیدِ همین دستگاه برمی‌گردد`() {
    val store = seed()
    sealWith()
    val more = store.data.value.copy(products = store.data.value.products + Product(id = "drill-p13", name = "آرد"))
    assertTrue(runBlocking { store.save(more) })
    assertTrue(SnapshotSeal.isPhraseSealed(AutoBackup.take(ctx, "2026-11-09").getOrThrow()!!))
    dir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json.") }.forEach { it.delete() }
    ledger.writeBytes(ledger.readBytes().let { it.copyOf(it.size / 2) })

    val back = open()
    assertEquals(LedgerRecovery.Source.AUTO_BACKUP, back.guard.recovered.value?.source)
    assertEquals(more, back.data.value)
  }

  /* ------------------------- ۷) فاکتورهای نگه‌داشته و سبدِ جاری ------------------------- */

  private val heldFile get() = File(dir, HeldInvoiceStore.FILE)
  private val cartFile get() = File(dir, CartStore.FILE)

  private fun line(p: Product, q: Double, price: Double? = null) =
    SaleDraft.Line(productId = p.id, quantity = q, unitPrice = price, note = "بی‌نمک", seller = "زهرا", specialPrice = price)

  /** دو نگه‌داشته (با همهٔ فیلدها) و سبدِ جاریِ نیمه‌کاره با برگه‌اش — همان پرونده‌هایی که صندوق می‌نویسد */
  private fun heldDesk(d: ShopData): Triple<HeldInvoice, HeldInvoice, SaleDraft> {
    val p = d.products
    val h1 = HeldInvoice(
      id = "drill-h1", number = 1, label = "آقای کریمی", lines = listOf(line(p[0], 2.0, 95.0), SaleDraft.Line("", 1.0, 5.0, label = "کیسه")),
      debtorId = d.debtors.firstOrNull()?.id, discount = "10", note = "پولش را می‌آورد", heldAt = 1_762_600_000_000, payment = "CREDIT",
      paid = "50", coupons = listOf("EID5"), discountAmount = true, saleNote = "فاکتورِ عمده", priceList = "wholesale",
    )
    val h2 = HeldInvoice(id = "drill-h2", number = 3, lines = listOf(line(p[1], 1.0)), heldAt = 1_762_600_100_000)
    val cart = SaleDraft(key = "drill-c1", lines = listOf(line(p[2], 4.0)), savedAt = 1_762_600_200_000, coupons = listOf("NEW"), note = "سبدِ نیمه‌کاره")
    heldFile.writeText(HeldInvoiceCodec.encode(HeldBook(items = listOf(h1, h2), current = HeldTag("drill-c1", 2, "خانمِ احمدی", "منتظرِ پول"))))
    cartFile.writeText(SaleDraftCodec.encode(cart))
    HeldInvoices.reset()
    return Triple(h1, h2, cart)
  }

  /** نگه‌داشته‌های برنامه همان‌طور که صندوق می‌بیند (حافظه، پس از نخستین خواندن) */
  private fun heldNow(): HeldBook = HeldInvoices.of(ctx).let { runBlocking { it.load() }; it.book.value }

  private fun confirmFrom(zip: ByteArray): BackupRestore.Confirmed {
    val fresh = open()
    return runBlocking { BackupRestore.confirm(ctx, fresh, openZip(fresh, zip).getOrThrow().data) }
  }

  @Test fun `فاکتورهای نگه‌داشته و سبدِ جاری با پشتیبانِ کامل از پاک شدنِ برنامه برمی‌گردند`() {
    val store = seed()
    val (h1, h2, cart) = heldDesk(store.data.value)
    val zip = fullBackup(store)

    wipe()
    val fresh = open()
    assertTrue("برنامهٔ تازه نگه‌داشته ندارد", heldNow().items.isEmpty())
    val opened = openZip(fresh, zip).getOrThrow()
    assertTrue("تا تأییدِ بازیابی هیچ نگه‌داشته‌ای ننشسته", heldNow().items.isEmpty() && !heldFile.exists())
    assertTrue(runBlocking { BackupRestore.confirm(ctx, fresh, opened.data) }.ok)

    val back = heldNow().items.associateBy { it.id }
    assertEquals(setOf("drill-h1", "drill-h2", "drill-c1"), back.keys)
    assertEquals("نگه‌داشته با همهٔ فیلدهایش", h1, back.getValue("drill-h1"))
    assertEquals(h2, back.getValue("drill-h2"))
    val c = back.getValue("drill-c1")
    assertEquals("سبدِ جاری با شماره، نام و یادداشتِ برگه‌اش نگه‌داشته می‌شود", Triple(2, "خانمِ احمدی", "منتظرِ پول"), Triple(c.number, c.label, c.note))
    assertEquals("سبدِ جاری با همهٔ ردیف‌ها، کدها و یادداشتِ فاکتور", cart, c.draft())
    assertFalse("سبدِ این دستگاه هرگز رونویسی نمی‌شود", cartFile.exists())
    //  روی دیسک هم: برنامه از نو باز شد
    HeldInvoices.reset()
    assertEquals(back.values.toSet(), heldNow().items.toSet())
    assertFalse("پوشهٔ موقت خالی شد", staged())
  }

  @Test fun `نگه‌داشته‌های پشتیبان فقط پس از دفتر، در همان دکان و روی دستگاهِ بی فاکتورِ باز می‌نشینند و فروخته هرگز`() {
    LedgerOwner.remember(ctx, "u-h", "s-h")
    val store = seed()
    val sold = store.data.value.sales.first()
    heldDesk(store.data.value)
    //  نگه‌داشته‌ای که همان فروشِ دفتر است (از دستگاهِ دیگر یا پیش از پشتیبان فروش رفت) دوباره پیشنهاد نمی‌شود
    val book = HeldInvoiceCodec.decode(heldFile.readText())!!
    heldFile.writeText(HeldInvoiceCodec.encode(book.copy(items = book.items + book.items[1].copy(id = sold.id, number = 4))))
    HeldInvoices.reset()
    val zip = fullBackup(store)

    //  ۱) دستگاهی که نگه‌داشتهٔ خودش را دارد: هیچ چیز رونویسی یا قاطی نمی‌شود
    wipe()
    val mine = HeldBook(items = listOf(book.items[1].copy(id = "drill-mine", number = 5, label = "مالِ همین دستگاه")))
    heldFile.writeText(HeldInvoiceCodec.encode(mine))
    HeldInvoices.reset()
    assertTrue(confirmFrom(zip).ok)
    assertEquals(mine, heldNow())
    assertFalse(staged())

    //  ۲) سبدِ پُرِ دیگری روی این دستگاه (فاکتورِ بازِ خودش): همان؛ سبد دست نمی‌خورد
    wipe()
    val own = SaleDraft(key = "drill-own", lines = listOf(line(store.data.value.products[3], 1.0)), savedAt = 5)
    cartFile.writeText(SaleDraftCodec.encode(own))
    assertTrue(confirmFrom(zip).ok)
    assertTrue(heldNow().items.isEmpty())
    assertEquals(own, SaleDraftCodec.decode(cartFile.readText()))

    //  ۳) بازیابیِ ردشده (حسابِ همگامِ بی ورود): مثلِ عکس و تنظیم، هیچ نگه‌داشته‌ای پیش یا بی دفتر نمی‌نشیند
    wipe()
    SyncStateV1(ctx, key()).cursor = 5L
    val refused = confirmFrom(zip)
    SyncStateV1(ctx, key()).cursor = 0L
    assertFalse(refused.ok)
    assertTrue(heldNow().items.isEmpty() && !heldFile.exists())
    assertFalse(staged())

    //  ۴) دستگاهِ تازهٔ همان دکان: همه، جز آن‌که فروش رفته
    wipe()
    assertTrue(confirmFrom(zip).ok)
    assertEquals(setOf("drill-h1", "drill-h2", "drill-c1"), heldNow().items.map { it.id }.toSet())

    //  ۵) دفترِ دکانِ دیگر نیست: پشتیبانِ «s-h» روی دفترِ بی‌صاحب (مُهر می‌پذیرد) — دفتر برمی‌گردد، نگه‌داشته‌ها نه
    wipe()
    LedgerOwner.remember(ctx, "", "")
    assertTrue(confirmFrom(zip).ok)
    assertTrue(heldNow().items.isEmpty())
    assertFalse(staged())
  }
}
