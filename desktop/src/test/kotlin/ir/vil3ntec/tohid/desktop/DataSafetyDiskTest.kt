package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.data.LedgerRecovery
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  DATA-SAFETY (`docs/DATA-SAFETY-fa.md`) — روی دیسکِ واقعیِ کامپیوتر، همان کدِ گوشی و آیفون.
 *
 *  هر آزمون همان رخدادِ عادی را می‌سازد که پیش از این رفع دادهٔ دکان را می‌برد، و نشان می‌دهد حالا
 *  چیزی پاک یا رونویسی نمی‌شود: دفترِ خراب، فایلِ قفل‌شده، صفِ نیمه‌جابه‌جاشده، تنظیماتِ خراب پس از
 *  قطعِ برق، بایگانیِ خراب، و نسخهٔ خودکار از دفترِ خراب.
 */
class DataSafetyDiskTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val ledger get() = File(dir, "shop-data.json")
  private val archive get() = File(dir, "ledger-archive.json")
  private val queueKey = "dsafety-queue"

  private fun clean() {
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty()
      .filter {
        it.name.startsWith("shop-data.json") || it.name.startsWith("ledger-archive.json") ||
          it.name.startsWith("oplog-${OpQueue.fileKey(queueKey)}")
      }
      .forEach { it.deleteRecursively() }
    AutoBackup.dir(ctx).listFiles().orEmpty().forEach { it.delete() }
    File(ctx.root, "shared_prefs").listFiles().orEmpty().filter { it.name.startsWith("dsafety-") }.forEach { it.delete() }
  }

  /**
   *  برگشتِ خودکار فقط روی دستگاهِ هرگز همگام‌نشده است (`LedgerRecovery.autoRecoverable`) — موتورِ مشترکِ
   *  آزمون‌ها را همان‌جا می‌گذاریم (کلیدِ حساب همان `SyncV1Engine.keyOf`).
   */
  private fun neverSynced() {
    val sync = ir.vil3ntec.tohid.sync.SyncStore(ctx)
    val key = SyncStateV1.currentAccount(ctx).ifBlank { sync.accountName }.ifBlank { sync.deviceUid }
    SyncStateV1(ctx, key).apply { cursor = 0L; reconciledAt = 0L; snapshotAt = 0L }
  }

  @Before fun before() { clean(); neverSynced() }
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private val rice = ShopData(products = listOf(Product(id = "dsf-p1", name = "برنج", salePrice = 120.0)))
  private val sugar = rice.copy(products = rice.products + Product(id = "dsf-p2", name = "شکر", salePrice = 90.0))
  private val oil = sugar.copy(products = sugar.products + Product(id = "dsf-p3", name = "روغن", salePrice = 300.0))

  private fun unreadableCopies() = dir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json.unreadable-") }

  /* ------------------------- ShopStore.load ------------------------- */

  @Test fun `دفترِ بریده دفترِ خالی نیست و از نسخهٔ پیشین برمی‌گردد و فایلِ خراب می‌ماند`() {
    val store = open()
    assertTrue(runBlocking { store.save(rice) })
    assertTrue(runBlocking { store.save(sugar) })
    //  قطعِ برق / خرابیِ دیسک: فایلِ دفتر نیمه
    val cut = ledger.readBytes().let { it.copyOf(it.size / 2) }
    ledger.writeBytes(cut)

    val back = open()
    //  پیش از رفع: دفترِ خالی، و نخستین ذخیره فایلِ واقعی را رونویسی می‌کرد
    assertEquals("دفتر از نسخهٔ پیشینِ ذخیره (`.prev`) برمی‌گردد", rice, back.data.value)
    assertNull("پس از برگشت نوشتن باز است", back.unreadable.value)
    assertNotNull("کاربر یک بار می‌شنود که دفتر برگشت", back.guard.recovered.value)
    assertEquals(LedgerRecovery.Source.PREVIOUS, back.guard.recovered.value?.source)
    //  فایلِ خراب پاک نشده، کنار رفته — همان بایت‌ها
    val kept = unreadableCopies()
    assertEquals(1, kept.size)
    assertArrayEquals(cut, kept.single().readBytes())
    //  و دفتر دوباره عادی ذخیره می‌شود
    assertTrue(runBlocking { back.save(oil) })
    assertEquals(oil, open().data.value)
  }

  @Test fun `دفترِ خرابِ بی نسخهٔ سالم — نوشتن بسته و فایل دست‌نخورده`() {
    ledger.writeText("{\"products\":[{\"id\":\"dsf-p1\",\"name\":\"برن")
    val bytes = ledger.readBytes()

    val store = open()
    assertEquals(LedgerRecovery.Damage.CORRUPT, store.guard.damage.value)
    assertNotNull("نوشتن بسته است", store.unreadable.value)
    //  پیش از رفع `true` بود و فایلِ واقعی با همین یک کالا رونویسی می‌شد
    assertFalse("هیچ ذخیره‌ای روی دفترِ خراب نمی‌نشیند", runBlocking { store.save(sugar) })
    assertArrayEquals("فایلِ دفتر بایت‌به‌بایت همان است", bytes, ledger.readBytes())
    //  پشتیبانِ «خالی» هم داده نمی‌شود
    assertTrue(runCatching { runBlocking { store.exportBackup() } }.isFailure)
  }

  @Test fun `دفترِ صفربایتی با نسخهٔ خودکار برمی‌گردد`() {
    val store = open()
    assertTrue(runBlocking { store.save(sugar) })
    assertNotNull(AutoBackup.take(ctx, "2026-11-08").getOrThrow())
    ledger.writeBytes(ByteArray(0))
    //  نسخهٔ پیشین هم نیست (مثلاً دیسکی که پیوندِ سخت نمی‌دهد)
    File(dir, "shop-data.json.prev").delete()

    val back = open()
    assertEquals(sugar, back.data.value)
    assertEquals(LedgerRecovery.Source.AUTO_BACKUP, back.guard.recovered.value?.source)
  }

  @Test fun `خطای خواندن دفترِ خالی نیست — نوشتن بسته و «دوباره امتحان» بازش می‌کند`() {
    val store = open()
    assertTrue(runBlocking { store.save(sugar) })
    val good = ledger.readBytes()
    //  فایلی که خوانده نمی‌شود (قفلِ ضدویروس، دیسک): جایش پوشه است
    ledger.renameTo(File(dir, "shop-data.json.hold"))
    ledger.mkdirs()

    val blocked = open()
    assertEquals(LedgerRecovery.Damage.READ_ERROR, blocked.guard.damage.value)
    assertFalse(runBlocking { blocked.save(oil) })
    assertNull("خطای خواندن هرگز نسخهٔ کهنه را جای دفتر نمی‌نشاند", blocked.guard.recovery.value)

    ledger.delete()
    File(dir, "shop-data.json.hold").renameTo(ledger)
    assertTrue(runBlocking { blocked.retryRead() })
    assertEquals(sugar, blocked.data.value)
    assertArrayEquals(good, ledger.readBytes())
  }

  @Test fun `دفترِ قالبِ تازه‌تر خودکار با نسخهٔ کهنه عوض نمی‌شود`() {
    val store = open()
    assertTrue(runBlocking { store.save(rice) })
    assertTrue(runBlocking { store.save(sugar) })
    //  JSON ِ درست، ولی نه دفترِ این نسخه (مثلاً نسخهٔ تازه‌ترِ برنامه نوعِ فیلدی را عوض کرده)
    ledger.writeText("{\"products\":\"v99\"}")
    val bytes = ledger.readBytes()

    val back = open()
    assertEquals(LedgerRecovery.Damage.NEWER_FORMAT, back.guard.damage.value)
    assertNull(back.guard.recovered.value)
    assertNotNull("نسخهٔ سالم فقط پیشنهاد می‌شود", back.guard.recovery.value)
    assertFalse(runBlocking { back.save(oil) })
    assertArrayEquals(bytes, ledger.readBytes())
  }

  @Test fun `هر ذخیره نسخهٔ پیشین را کنارش نگه می‌دارد`() {
    val store = open()
    assertTrue(runBlocking { store.save(rice) })
    assertTrue(runBlocking { store.save(sugar) })
    val prev = File(dir, "shop-data.json.prev")
    assertTrue(prev.isFile)
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    assertEquals(rice, (LedgerRecovery.decode(prev.readText(), json) as LedgerRecovery.Read.Ok).data)
    assertEquals(sugar, open().data.value)
  }

  /* ------------------------- AutoBackup ------------------------- */

  @Test fun `دفترِ خراب نسخهٔ خودکار نمی‌شود`() {
    ledger.writeText("{\"products\":[")
    assertNull(AutoBackup.take(ctx, "2026-11-08").getOrThrow())
    assertTrue(AutoBackup.list(ctx).isEmpty())
  }

  /* ------------------------- LedgerArchive ------------------------- */

  @Test fun `بایگانیِ خراب با فشرده‌سازی رونویسی نمی‌شود و دفتر کوتاه نمی‌شود`() {
    archive.writeText("{\"stockMovements\":[{\"id\":\"old-1\"")
    val bytes = archive.readBytes()
    val now = 1_800_000_000_000L
    val old = now - (LedgerArchive.KEEP_DAYS + 30) * 86_400_000L
    val d = ShopData(stockMovements = listOf(StockMovement(id = "dsf-m1", productId = "dsf-p1", type = "sale", qty = -1.0, createdAt = old)))

    val out = LedgerArchive.compact(ctx, d, now)
    //  پیش از رفع: «بایگانیِ خالی + همین یک ردیف» روی بایگانیِ واقعی می‌نشست
    assertTrue("فشرده‌سازی روی بایگانیِ خراب شکست می‌خورد", out.isFailure)
    assertArrayEquals("بایگانی بایت‌به‌بایت همان است", bytes, archive.readBytes())
    assertTrue("رونوشتِ بایگانیِ خراب کنار رفت", dir.listFiles().orEmpty().any { it.name.startsWith("ledger-archive.json.corrupt-") })
  }

  /* ------------------------- OpQueue ------------------------- */

  private fun op(i: Int) = SyncOp(
    opId = "dsf-op-$i", ts = 1_000L + i, table = "products", rowId = "dsf-p$i", type = "insert",
    fields = buildJsonObject { put("name", JsonPrimitive("کالا $i")) },
  )

  @Test fun `صفی که فقط در فایلِ کناری ماند گم نمی‌شود`() {
    val q = OpQueue(ctx, queueKey)
    q.push(listOf(op(1), op(2)))
    val main = File(dir, "oplog-${OpQueue.fileKey(queueKey)}.json")
    //  نوشتنِ ساختِ پیشین روی ویندوز: اصل پاک شد و برنامه پیش از جابه‌جاییِ کناری بسته شد
    main.copyTo(File(dir, main.name + ".tmp"), overwrite = true)
    main.delete()

    val again = OpQueue(ctx, queueKey)
    assertEquals(listOf("dsf-op-1", "dsf-op-2"), again.all().map { it.opId })
    assertTrue("کناری سرِ جای اصل نشست", main.isFile)
  }

  @Test fun `opهای ردشده پس از دویست تا بی‌صدا نمی‌افتند`() {
    val q = OpQueue(ctx, queueKey)
    q.drop((1..250).map { op(it) }, "rejected")
    assertEquals(250, q.dropped().size)
    assertEquals(250, OpQueue(ctx, queueKey).droppedCount())
  }

  /* ------------------------- DesktopPrefs ------------------------- */

  @Test fun `تنظیماتِ خراب پس از قطعِ برق «هیچ» نیست — نسخهٔ پیشین خوانده می‌شود`() {
    val f = File(File(ctx.root, "shared_prefs"), "dsafety-prefs.json")
    val p = DesktopPrefs(f)
    assertTrue(p.edit().putString("shadow", "سایهٔ دکان").commit())
    assertTrue(p.edit().putLong("cursor", 42L).commit())
    f.writeBytes(ByteArray(0))

    val back = DesktopPrefs(f)
    //  پیش از رفع: هر دو خالی، و نخستین `apply` رویِ همان فایلِ خراب
    assertEquals("سایهٔ دکان", back.getString("shadow", null))
    assertTrue("رونوشتِ فایلِ خراب کنار رفت", f.parentFile.listFiles().orEmpty().any { it.name.startsWith("dsafety-prefs.json.corrupt-") })
  }
}
