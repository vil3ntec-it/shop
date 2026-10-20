package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  §۹.۵ — تصمیمِ «سرور از پشتیبان برگشت» و دفترچهٔ opهای پذیرفته‌شده. همین بردارهای `plan`
 *  در قرینهٔ JS (`server/test/e2e/lib/native.mjs → restorePlan`)؛ سیم‌کشیِ v1 در
 *  `sync/v1/V1RestoreTest.kt`، v3 در `SyncV3EngineTest` و سناریوی واقعی در `ops.e2e.mjs`.
 */
class RestoreReplayTest {

  private val e = RestoreReplay.Epoch("ep_new", base = 100, head = 130)

  /* ------------------------------------------------------------ plan */

  @Test fun `سرورِ کهنه یا خاموش (بی دوران) ⇒ هیچ تغییری`() {
    assertEquals(RestoreReplay.Plan("ep_old", false, 0), RestoreReplay.plan("ep_old", 120, null))
  }

  @Test fun `نخستین بار و cursor پشتِ سرِ دفتر ⇒ فقط دوران نگه داشته می‌شود`() {
    assertEquals(RestoreReplay.Plan("ep_new", false, 100), RestoreReplay.plan("", 120, e))
    assertEquals(RestoreReplay.Plan("ep_new", false, 100), RestoreReplay.plan("", 130, e))
  }

  @Test fun `همان دوران (بالا آمدنِ عادی، هر چند بار) ⇒ هیچ کاری`() {
    assertEquals(RestoreReplay.Plan("ep_new", false, 100), RestoreReplay.plan("ep_new", 125, e) { true })
  }

  @Test fun `دورانِ تازه و cursor بالای پایه ⇒ بازیابی`() {
    //  cursor ِ دستگاه ۱۲۰ بود، سرور تا ۱۰۰ برگشت و دیگران تا ۱۳۰ جلو بردند —
    //  مقایسهٔ سادهٔ head با cursor این را نمی‌دید
    assertEquals(RestoreReplay.Plan("ep_new", true, 100), RestoreReplay.plan("ep_old", 120, e))
  }

  @Test fun `دورانِ تازه، cursor زیرِ پایه ولی opِ فرستاده بالای آن ⇒ بازیابی`() {
    assertEquals(RestoreReplay.Plan("ep_new", true, 100), RestoreReplay.plan("ep_old", 90, e) { it == 100L })
  }

  @Test fun `دورانِ تازه ولی دستگاه پس از پایه چیزی ندیده و نفرستاده ⇒ فقط دوران`() {
    assertEquals(RestoreReplay.Plan("ep_new", false, 100), RestoreReplay.plan("ep_old", 100, e) { false })
  }

  @Test fun `cursor جلوتر از سرِ دفتر حتی بی دورانِ تازه ⇒ بازیابی`() {
    val same = RestoreReplay.Epoch("ep_same", base = 90, head = 95)
    assertEquals(RestoreReplay.Plan("ep_same", true, 90), RestoreReplay.plan("ep_same", 120, same))
    //  نخستین بار (برنامه پس از بازیابی به‌روز شد) هم
    assertEquals(RestoreReplay.Plan("ep_same", true, 90), RestoreReplay.plan("", 120, same))
  }

  @Test fun `پایه از سرِ دفتر بالاتر نمی‌رود`() {
    val odd = RestoreReplay.Epoch("ep_x", base = 150, head = 110)
    assertEquals(RestoreReplay.Plan("ep_x", true, 110), RestoreReplay.plan("ep_old", 140, odd))
  }

  @Test fun `Epoch-from فقط شکلِ درست را می‌پذیرد`() {
    val ok = buildJsonObject { put("id", JsonPrimitive("ep_1")); put("base", JsonPrimitive(7)); put("head", JsonPrimitive(9)); put("at", JsonPrimitive(1)) }
    assertEquals(RestoreReplay.Epoch("ep_1", 7, 9), RestoreReplay.Epoch.from(ok))
    assertNull(RestoreReplay.Epoch.from(null))
    assertNull(RestoreReplay.Epoch.from(JsonPrimitive("ep_1")))
    assertNull(RestoreReplay.Epoch.from(buildJsonObject { put("id", JsonPrimitive("")); put("base", JsonPrimitive(1)); put("head", JsonPrimitive(1)) }))
    assertNull(RestoreReplay.Epoch.from(buildJsonObject { put("id", JsonPrimitive(5)); put("base", JsonPrimitive(1)); put("head", JsonPrimitive(1)) }))
    assertNull(RestoreReplay.Epoch.from(buildJsonObject { put("id", JsonPrimitive("ep")); put("head", JsonPrimitive(1)) }))
    assertNull(RestoreReplay.Epoch.from(buildJsonObject { put("id", JsonPrimitive("ep")); put("base", JsonPrimitive(-1)); put("head", JsonPrimitive(1)) }))
  }

  /* ------------------------------------------------------- ReplayBook */

  private val dir: File = Files.createTempDirectory("replay-book").toFile()
  private fun book(cap: Int = ReplayBook.CAP) = ReplayBook(File(dir, "x-sent.jsonl"), cap)
  private fun op(id: String) = buildJsonObject {
    put("op_id", JsonPrimitive(id)); put("table", JsonPrimitive("sales")); put("row_id", JsonPrimitive("s1"))
    put("type", JsonPrimitive("update")); put("fields", buildJsonObject { put("paidAmount", buildJsonObject { put("\$inc", JsonPrimitive(50)) }) })
  }

  @Test fun `فرستاده‌ها بالای پایه، به ترتیبِ فرستادن، با همان بدنه`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("o1", 90, op("o1")), ReplayBook.Sent("o2", 101, op("o2"))))
    b.sent(listOf(ReplayBook.Sent("o3", 120, op("o3"))))
    val again = b.replayAbove(100)
    assertEquals(listOf("o2", "o3"), again.map { it.id })
    assertEquals(op("o2"), again.first().body)
    assertTrue(b.hasAbove(119))
    assertFalse(b.hasAbove(120))
    //  از دیسک دوباره خوانده می‌شود (برنامه بسته و باز شد)
    assertEquals(listOf("o2", "o3"), book().replayAbove(100).map { it.id })
    assertEquals(3, book().count())
    assertTrue(book().hasAbove(119))
  }

  @Test fun `بی شماره (seq صفر) در دفترچه نمی‌نشیند و هر بالا آمدن دوباره نمی‌رود`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("z0", 0, op("z0")), ReplayBook.Sent("zneg", -3, op("zneg"))))
    assertEquals(0, b.count())
    assertEquals(emptyList<ReplayBook.Sent>(), b.replayAbove(0))
    assertFalse(b.hasAbove(0))
  }

  @Test fun `همان op دو بار پذیرفته شد ⇒ یک بار، با شمارهٔ آخر و به ترتیبِ همان`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("o1", 50, op("o1")), ReplayBook.Sent("o2", 60, op("o2"))))
    //  پس از بازیابیِ پیشین دوباره رفت و شمارهٔ تازه گرفت
    b.sent(listOf(ReplayBook.Sent("o1", 140, op("o1"))))
    assertEquals(listOf("o1"), b.replayAbove(100).map { it.id })
    assertEquals(listOf("o2", "o1"), b.replayAbove(10).map { it.id })
    assertEquals(140L, b.replayAbove(10).last().seq)
  }

  @Test fun `hasAbove با آخرین شمارهٔ هر op — شمارهٔ کهنهٔ بالاتر حساب نیست`() {
    //  بازبینِ دوم: پیش‌تر بیشینهٔ همهٔ شماره‌ها بود
    val b = book()
    b.sent(listOf(ReplayBook.Sent("o1", 150, op("o1"))))
    //  بازیابیِ پیشین: همان op دوباره رفت و این بار ۱۲۰ گرفت
    b.sent(listOf(ReplayBook.Sent("o1", 120, op("o1"))))
    assertFalse(b.hasAbove(130))
    assertTrue(b.hasAbove(119))
    assertFalse(book().hasAbove(130))
    assertEquals(emptyList<ReplayBook.Sent>(), book().replayAbove(130))
  }

  @Test fun `گرفته‌ها و فرستاده‌ها با هم، به ترتیبِ سرور؛ opِ خودِ سرور و بی‌دستگاه نه`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("mine-2", 102, op("mine-2"))))
    b.seen(listOf(
      ReplayBook.Sent("theirs-1", 101, op("theirs-1"), device = "dev-a"),
      ReplayBook.Sent("srv-1", 103, op("srv-1"), device = ReplayBook.SERVER_DEVICE),
      ReplayBook.Sent("anon", 104, op("anon")),
      ReplayBook.Sent("theirs-5", 105, op("theirs-5"), device = "dev-c"),
    ))
    assertEquals(listOf("theirs-1", "mine-2", "theirs-5"), b.replayAbove(100).map { it.id })
    assertTrue("ردِ دوباره‌فرستادهٔ گرفته‌شده هم بی‌صدا", b.wasSent("theirs-1"))
    assertFalse(b.wasSent("srv-1"))
    assertFalse(b.wasSent("anon"))
    assertEquals(listOf("theirs-1", "mine-2", "theirs-5"), book().replayAbove(100).map { it.id })
  }

  @Test fun `پوشش — نخستین بار cursor ِ همان لحظه، فشرده کردن بالاترین شمارهٔ رفته`() {
    val b = book(cap = 4)
    b.begin(cursor = 70)
    assertEquals(70L, b.floor())
    b.begin(cursor = 500)
    assertEquals("فقط بارِ نخست", 70L, book(cap = 4).floor())
    for (i in 1..7) b.sent(listOf(ReplayBook.Sent("o$i", 100L + i, op("o$i"))))
    val c = book(cap = 4)
    assertTrue("کهنه‌ها رفتند ⇒ پایه زیرِ ۱۰۳ پوشیده نیست", c.floor() >= 103)
    assertTrue(c.replayAbove(0).all { it.seq > c.floor() })
    val fresh = ReplayBook(File(dir, "y-sent.jsonl"))
    fresh.begin(cursor = 0)
    assertEquals(0L, fresh.floor())
    assertEquals(0, fresh.count())
  }

  @Test fun `wasSent — فقط opی که پذیرفته شده`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("mine", 5, op("mine"))))
    assertTrue(b.wasSent("mine"))
    assertFalse(b.wasSent("other"))
    assertFalse(b.wasSent(""))
    assertTrue(book().wasSent("mine"))
  }

  @Test fun `شناسهٔ نادرست (فاصله، خطِ تازه) نوشته نمی‌شود`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("x y", 1, op("x")), ReplayBook.Sent("x\ny", 1, op("x")), ReplayBook.Sent("ok_1", 2, op("ok_1"))))
    assertEquals(1, book().count())
  }

  @Test fun `خطِ نیمه‌کارهٔ قطعِ برق نادیده و رکوردِ بعدی سالم`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("o1", 10, op("o1"))))
    File(dir, "x-sent.jsonl").appendText("{\"id\":\"o9\",\"q\":")
    val c = book()
    c.sent(listOf(ReplayBook.Sent("o2", 200, op("o2"))))
    val d = book()
    assertEquals(listOf("o1", "o2"), d.replayAbove(0).map { it.id })
    assertFalse(d.wasSent("o9"))
  }

  @Test fun `سقف — کهنه‌ترین‌ها می‌روند، تازه‌ترین‌ها می‌مانند`() {
    val b = book(cap = 4)
    for (i in 1..7) b.sent(listOf(ReplayBook.Sent("o$i", i.toLong(), op("o$i"))))
    val c = book(cap = 4)
    assertTrue(c.count() <= 6)
    assertEquals("o7", c.replayAbove(0).last().id)
    assertFalse("کهنه‌ترین رفته", c.wasSent("o1"))
    File(dir, "x-sent.jsonl").readLines().filter { it.isNotBlank() }.forEach { Json.parseToJsonElement(it).jsonObject }
  }

  @Test fun `بی فایل و پوشه ⇒ خالی، بی خطا`() {
    val b = ReplayBook.inDir(File(dir, "nested/deeper"), "acc")
    assertEquals(emptyList<ReplayBook.Sent>(), b.replayAbove(0))
    assertFalse(b.hasAbove(0))
    b.sent(listOf(ReplayBook.Sent("a", 1, op("a"))))
    assertTrue(File(dir, "nested/deeper/acc-sent.jsonl").isFile)
  }
}
