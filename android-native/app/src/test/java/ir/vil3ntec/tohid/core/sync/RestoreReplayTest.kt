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
 *  §۹.۵ — سرور از پشتیبان برگشت ⇒ opهای پذیرفته‌شدهٔ گم‌شده دوباره، opِ آشنا نه دو بار.
 *  همین بردارهای `plan` در قرینهٔ JS (`server/test/e2e/lib/native.mjs → restorePlan`)
 *  و سناریوی واقعیِ بازیابی در `server/test/e2e/ops.e2e.mjs` (E2E-OPS-14.replay).
 */
class RestoreReplayTest {

  private val e = RestoreReplay.Epoch("ep_new", base = 100, head = 130)

  /* ------------------------------------------------------------ plan */

  @Test fun `سرورِ کهنه یا خاموش (بی دوران) ⇒ هیچ تغییری`() {
    val p = RestoreReplay.plan("ep_old", 120, null)
    assertEquals(RestoreReplay.Plan("ep_old", 120, null), p)
    assertFalse(p.restored)
  }

  @Test fun `نخستین بار و cursor پشتِ سرِ دفتر ⇒ فقط دوران نگه داشته می‌شود`() {
    assertEquals(RestoreReplay.Plan("ep_new", 120, null), RestoreReplay.plan("", 120, e))
    assertEquals(RestoreReplay.Plan("ep_new", 130, null), RestoreReplay.plan("", 130, e))
  }

  @Test fun `همان دوران ⇒ هیچ کاری`() {
    assertEquals(RestoreReplay.Plan("ep_new", 125, null), RestoreReplay.plan("ep_new", 125, e))
  }

  @Test fun `دورانِ تازه پس از بازیابی ⇒ cursor تا پایه و opهای بالای پایه دوباره`() {
    //  cursor ِ دستگاه ۱۲۰ بود، سرور تا ۱۰۰ برگشت و دیگران تا ۱۳۰ جلو بردند —
    //  مقایسهٔ سادهٔ head با cursor این را نمی‌دید
    val p = RestoreReplay.plan("ep_old", 120, e)
    assertEquals(RestoreReplay.Plan("ep_new", 100, 100), p)
    assertTrue(p.restored)
  }

  @Test fun `بالا آمدنِ عادی (دورانِ تازه، پایه = سرِ دفتر) ⇒ cursor همان`() {
    val boot = RestoreReplay.Epoch("ep_boot", base = 130, head = 130)
    assertEquals(RestoreReplay.Plan("ep_boot", 125, 130), RestoreReplay.plan("ep_old", 125, boot))
  }

  @Test fun `cursor جلوتر از سرِ دفتر حتی بی دورانِ تازه ⇒ برگشتن`() {
    val same = RestoreReplay.Epoch("ep_same", base = 90, head = 95)
    assertEquals(RestoreReplay.Plan("ep_same", 90, 90), RestoreReplay.plan("ep_same", 120, same))
    //  نخستین بار (برنامه پس از بازیابی به‌روز شد) هم
    assertEquals(RestoreReplay.Plan("ep_same", 90, 90), RestoreReplay.plan("", 120, same))
  }

  @Test fun `پایه از سرِ دفتر بالاتر نمی‌رود`() {
    val odd = RestoreReplay.Epoch("ep_x", base = 150, head = 110)
    assertEquals(RestoreReplay.Plan("ep_x", 110, 110), RestoreReplay.plan("ep_old", 140, odd))
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
  private fun book(cap: Int = ReplayBook.SENT_CAP, seen: Int = ReplayBook.SEEN_CAP) =
    ReplayBook(File(dir, "x-sent.jsonl"), File(dir, "x-seen.jsonl"), cap, seen)
  private fun op(id: String) = buildJsonObject {
    put("op_id", JsonPrimitive(id)); put("table", JsonPrimitive("sales")); put("row_id", JsonPrimitive("s1"))
    put("type", JsonPrimitive("update")); put("fields", buildJsonObject { put("paidAmount", buildJsonObject { put("\$inc", JsonPrimitive(50)) }) })
  }

  @Test fun `فرستاده‌ها بالای پایه، به ترتیبِ فرستادن، با همان بدنه`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("o1", 90, op("o1")), ReplayBook.Sent("o2", 101, op("o2"))))
    b.sent(listOf(ReplayBook.Sent("o3", 120, op("o3")), ReplayBook.Sent("o4", 0, op("o4"))))
    val again = b.replayAbove(100)
    assertEquals(listOf("o2", "o3", "o4"), again.map { it.id })
    assertEquals(op("o2"), again.first().body)
    //  از دیسک دوباره خوانده می‌شود (برنامه بسته و باز شد)
    assertEquals(listOf("o2", "o3", "o4"), book().replayAbove(100).map { it.id })
    assertEquals(4, book().sentCount())
  }

  @Test fun `همان op دو بار پذیرفته شد ⇒ یک بار، جای نخست، شمارهٔ آخر`() {
    val b = book()
    b.sent(listOf(ReplayBook.Sent("o1", 50, op("o1")), ReplayBook.Sent("o2", 60, op("o2"))))
    //  پس از بازیابیِ پیشین دوباره رفت و شمارهٔ تازه گرفت
    b.sent(listOf(ReplayBook.Sent("o1", 140, op("o1"))))
    assertEquals(listOf("o1"), b.replayAbove(100).map { it.id })
    assertEquals(listOf("o1", "o2"), b.replayAbove(10).map { it.id })
    assertEquals(140L, b.replayAbove(10).first().seq)
  }

  @Test fun `opِ آشنا (نشانده یا فرستاده) نادیده، بقیه به همان ترتیب`() {
    val b = book()
    assertEquals(listOf("a", "b"), b.unknown(listOf("a", "b")) { it })
    b.seen(listOf("r1", "r2"))
    b.sent(listOf(ReplayBook.Sent("mine", 5, op("mine"))))
    assertEquals(listOf("r3", "x"), b.unknown(listOf("r1", "r3", "mine", "r2", "x")) { it })
    assertTrue(b.knows("r1"))
    assertFalse(b.knows("r9"))
    assertFalse(b.knows(""))
    //  پس از باز شدنِ دوباره هم
    assertTrue(book().knows("r2"))
    assertTrue(book().knows("mine"))
  }

  @Test fun `شناسهٔ نادرست (فاصله، خطِ تازه) نوشته نمی‌شود`() {
    val b = book()
    b.seen(listOf("ok_1", "bad id", "bad\nid", ""))
    b.sent(listOf(ReplayBook.Sent("x y", 1, op("x"))))
    assertEquals(1, book().seenCount())
    assertEquals(0, book().sentCount())
  }

  @Test fun `خطِ نیمه‌کارهٔ قطعِ برق نادیده و رکوردِ بعدی سالم`() {
    val b = book()
    b.seen(listOf("r1"))
    File(dir, "x-seen.jsonl").appendText("r2-نیمه")          //  بی خطِ تازه، شناسهٔ نادرست
    File(dir, "x-sent.jsonl").appendText("{\"id\":\"o9\",\"q\":")
    val c = book()
    c.seen(listOf("r3"))
    c.sent(listOf(ReplayBook.Sent("o1", 200, op("o1"))))
    val d = book()
    assertTrue(d.knows("r1"))
    assertTrue(d.knows("r3"))
    assertFalse(d.knows("r2"))
    assertEquals(listOf("o1"), d.replayAbove(0).map { it.id })
  }

  @Test fun `سقف — کهنه‌ترین‌ها می‌روند، تازه‌ترین‌ها می‌مانند`() {
    val b = book(cap = 4, seen = 4)
    for (i in 1..7) b.sent(listOf(ReplayBook.Sent("o$i", i.toLong(), op("o$i"))))
    for (i in 1..7) b.seen(listOf("r$i"))
    val c = book(cap = 4, seen = 4)
    assertTrue(c.sentCount() <= 6)
    assertTrue(c.seenCount() <= 6)
    assertEquals("o7", c.replayAbove(0).last().id)
    assertTrue(c.knows("r7"))
    assertFalse("کهنه‌ترین رفته", c.knows("r1"))
    val lines = File(dir, "x-sent.jsonl").readLines().filter { it.isNotBlank() }
    lines.forEach { Json.parseToJsonElement(it).jsonObject }   //  همه JSONِ سالم
  }

  @Test fun `بی فایل و پوشه ⇒ خالی، بی خطا`() {
    val b = ReplayBook.inDir(File(dir, "nested/deeper"), "acc")
    assertEquals(emptyList<ReplayBook.Sent>(), b.replayAbove(0))
    assertEquals(listOf("a"), b.unknown(listOf("a")) { it })
    b.seen(listOf("a"))
    assertEquals(emptyList<String>(), b.unknown(listOf("a")) { it })
    assertTrue(File(dir, "nested/deeper/acc-seen.jsonl").isFile)
  }
}
