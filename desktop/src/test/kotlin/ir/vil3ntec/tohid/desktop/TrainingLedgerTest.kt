package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.data.HeldInvoices
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TrainingLedger
import ir.vil3ntec.tohid.data.TrainingMode
import ir.vil3ntec.tohid.data.TrainingPref
import ir.vil3ntec.tohid.data.TrainingSample
import ir.vil3ntec.tohid.sync.DocSeries
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  OPS-07 — حالتِ آموزشی روی دیسکِ واقعیِ کامپیوتر (پوشهٔ دادهٔ آزمون، `TOHID_DATA_DIR`):
 *  دفترِ جدا، دفترِ واقعیِ بایت‌به‌بایت دست‌نخورده (SHA)، شبکهٔ بسته، شمارهٔ واقعیِ ثابت، خروج و پاک کردن،
 *  اجازهٔ نقش و «تنظیمِ خاموش ⇒ همان امروز».
 */
class TrainingLedgerTest {

  private val ctx = DesktopContext
  private val files: File get() = ctx.filesDir
  private val real: File get() = File(files, "shop-data.json")
  private val trainingDir: File get() = File(files, TrainingMode.DIR)

  private fun clean() {
    TrainingLedger.exit(ctx, wipe = false)
    TrainingPref.setOn(ctx, false)
    TrainingPref.setStaff(ctx, false)
    real.delete()
    File(files, "shop-data.json.tmp").delete()
    trainingDir.deleteRecursively()
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun sha(f: File): String =
    MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

  private val realData = ShopData(
    products = listOf(Product(id = "r-p1", name = "برنجِ واقعی", salePrice = 100.0)),
    sales = listOf(Sale(id = "r-s1", invoiceNumber = 40, total = 100.0, finalTotal = 100.0, paidAmount = 100.0, date = "2026-11-01")),
    nextInvoiceNo = 41,
  )

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  /** دفترِ واقعی روی دیسک ⇒ SHA ِ همان بایت‌ها */
  private fun seedReal(): String {
    assertTrue(runBlocking { open().save(realData) })
    return sha(real)
  }

  private val today = "2026-11-02"
  private val now = 1_793_000_000_000L

  private fun enter(role: String = ShopRole.OWNER): ShopStore? = runBlocking { TrainingLedger.enter(ctx, today, now, role) }

  /** [n] فروشِ تازه با شمارهٔ بعدیِ همان دفتر — همان کاری که صندوق با `nextInvoiceNo` می‌کند */
  private fun sell(store: ShopStore, n: Int) = repeat(n) { i ->
    val ok = runBlocking {
      store.update { d ->
        d.copy(
          sales = d.sales + Sale(id = "t-x$i-${d.nextInvoiceNo}", invoiceNumber = d.nextInvoiceNo, total = 50.0, finalTotal = 50.0, paidAmount = 50.0, date = today),
          nextInvoiceNo = d.nextInvoiceNo + 1,
        )
      }
    }
    assertEquals(true, ok)
  }

  @Test fun `تنظیمِ خاموش یعنی هیچ ورود و هیچ پوشهٔ آموزشی و دفترِ واقعی همان رفتارِ امروز`() {
    seedReal()
    assertNull(enter())
    assertFalse(TrainingMode.on)
    assertNull(TrainingLedger.store.value)
    assertFalse("پوشهٔ آموزش ساخته نمی‌شود", trainingDir.exists())
    assertEquals(files, TrainingLedger.filesDir(ctx))
    //  دفترِ واقعی همان‌طور نوشتنی است
    val store = open()
    assertTrue(runBlocking { store.save(realData.copy(nextInvoiceNo = 42)) })
    assertEquals(42, open().data.value.nextInvoiceNo)
  }

  @Test fun `دفترِ آموزشی فایلِ جدای خودش را دارد و با دادهٔ نمونهٔ قطعی باز می‌شود`() {
    TrainingPref.setOn(ctx, true)
    val before = seedReal()
    val t = must(enter())
    assertTrue(t.training)
    assertTrue(TrainingMode.on)
    assertTrue(File(trainingDir, "shop-data.json").isFile)
    assertEquals(TrainingSample.build(today, now), t.data.value)
    assertEquals(before, sha(real))
    //  پرونده‌های کناری (پیش‌نویسِ سبد، فاکتورهای نگه‌داشته) هم در همان پوشه
    assertEquals(trainingDir.absoluteFile, TrainingLedger.filesDir(ctx).absoluteFile)
    val heldTraining = HeldInvoices.of(ctx)
    TrainingLedger.exit(ctx, wipe = false)
    assertTrue("فاکتورهای نگه‌داشتهٔ آموزشی جدا از واقعی‌اند", heldTraining !== HeldInvoices.of(ctx))
  }

  @Test fun `فروشِ آموزشی دفترِ واقعی را بایت‌به‌بایت دست نمی‌زند و شمارهٔ فاکتورِ واقعی جلو نمی‌رود`() {
    TrainingPref.setOn(ctx, true)
    val before = seedReal()
    val realLive = open()
    val t = must(enter())
    sell(t, 3)
    assertEquals("شمارهٔ آموزشی از دفترِ خودش", TrainingSample.build(today, now).nextInvoiceNo + 3, t.data.value.nextInvoiceNo)
    assertEquals("سریِ سندِ واقعیِ دستگاه به فروشِ آموزشی داده نمی‌شود", "", DocSeries.current(ctx))
    //  هر راهِ نوشتن روی دفترِ واقعی در آموزش بسته است — حتی نمونه‌ای که پیش از ورود باز بود
    assertFalse(runBlocking { realLive.save(realData.copy(nextInvoiceNo = 99)) })
    assertEquals(false, runBlocking { realLive.update { it.copy(nextInvoiceNo = 98) } })
    assertEquals(before, sha(real))
    TrainingLedger.exit(ctx, wipe = false)
    assertFalse(TrainingMode.on)
    assertEquals("پس از خروج، دفترِ واقعی بایت‌به‌بایت همان", before, sha(real))
    assertEquals(realData, open().data.value)
    assertEquals(41, open().data.value.nextInvoiceNo)
  }

  @Test fun `در حالتِ آموزشی هیچ درخواستِ شبکه یا همگام‌سازی ساخته نمی‌شود`() {
    var urls = 0
    var probes = 0
    val engine = HttpEngine(baseUrl = { urls++; "http://127.0.0.1:9" }, allowInsecure = true, online = { probes++; false })
    TrainingPref.setOn(ctx, true)
    val t = must(enter())
    for (path in listOf("/sync/v1/push", "/sync/v1/pull", "/shop/series")) {
      val failure = runCatching { runBlocking { engine.send("POST", path, buildJsonObject { }) } }.exceptionOrNull()
      assertTrue("$path: $failure", failure is ApiFailure && failure.code == TrainingMode.NETWORK_CODE)
    }
    val bytes = runCatching { runBlocking { engine.fetchBytes("/backups/latest", token = null) } }.exceptionOrNull()
    assertTrue("$bytes", bytes is ApiFailure && bytes.code == TrainingMode.NETWORK_CODE)
    assertEquals("هیچ نشانی‌ای ساخته نشد", 0, urls)
    assertEquals("حتی پرسیدنِ نت", 0, probes)
    //  دفترِ آموزشی به موتورِ همگام‌سازیِ v3 نمی‌رسد
    assertFalse(runBlocking { ir.vil3ntec.tohid.sync.v3.SyncV3Runner.of(ctx).work(t) })
    //  نگهبان فقط مالِ آموزش است: پس از خروج درخواست دوباره راه می‌افتد (این‌جا با «نت نیست»)
    TrainingLedger.exit(ctx, wipe = true)
    val after = runCatching { runBlocking { engine.send("GET", "/health") } }.exceptionOrNull()
    assertTrue("$after", after is ApiFailure.Offline)
    assertTrue("پس از خروج نشانی دوباره ساخته می‌شود (GET با تکرار)", urls >= 1)
  }

  @Test fun `خروج با پاک کردن پوشهٔ آموزش را پاک می‌کند و ورودِ بعدی دفترِ نمونهٔ تازه می‌گیرد`() {
    TrainingPref.setOn(ctx, true)
    sell(must(enter()), 2)
    TrainingLedger.exit(ctx, wipe = true)
    assertFalse(trainingDir.exists())
    assertFalse(TrainingLedger.hasSaved(ctx))
    assertEquals(TrainingSample.build(today, now), must(enter()).data.value)
  }

  @Test fun `خروج بی پاک کردن دفترِ آموزشی را نگه می‌دارد و ورودِ بعدی همان را باز می‌کند`() {
    TrainingPref.setOn(ctx, true)
    sell(must(enter()), 2)
    TrainingLedger.exit(ctx, wipe = false)
    assertTrue(TrainingLedger.hasSaved(ctx))
    assertEquals(TrainingSample.build(today, now).nextInvoiceNo + 2, must(enter()).data.value.nextInvoiceNo)
    //  پاک کردنِ دستی وقتی آموزش باز است انجام نمی‌شود
    assertFalse(TrainingLedger.wipe(ctx))
    assertTrue(TrainingLedger.hasSaved(ctx))
  }

  @Test fun `کارمند بی اجازه وارد نمی‌شود و با اجازهٔ صاحب وارد می‌شود`() {
    TrainingPref.setOn(ctx, true)
    assertNull(enter(ShopRole.CASHIER))
    assertFalse(TrainingMode.on)
    assertFalse(trainingDir.exists())
    must(enter(ShopRole.MANAGER))
    TrainingLedger.exit(ctx, wipe = true)
    TrainingPref.setStaff(ctx, true)
    must(enter(ShopRole.CASHIER))
  }

  private fun <T> must(v: T?): T { assertNotNull(v); return v!! }
}
