package ir.vil3ntec.tohid.core.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  OPS-03 — گزارشِ از کار افتادن: پوشاندنِ دادهٔ شخصی پیش از دیسک، شناسهٔ `cer_<ULID>`،
 *  یکی کردنِ حلقهٔ خطا و سقفِ صف.
 */
class CrashReportsTest {

  private val now = 1_761_800_000_000L

  @Test
  fun scrubHidesEmailPhoneTokenAndLongNumbersInAllDigitScripts() {
    val cases = listOf(
      "خطا برای ahmad.k+1@gmail.com" to "خطا برای [ایمیل]",
      "شماره 0700123456 و +93 700123456" to "شماره [شماره] و [شماره]",
      "شمارهٔ فارسی ۰۷۹۱۱۱۲۲۳۳ تمام" to "شمارهٔ فارسی [شماره] تمام",
      "شمارهٔ عربی ٠٧٩١١١٢٢٣٣" to "شمارهٔ عربی [شماره]",
      "ایران 09121234567" to "ایران [شماره]",
      "توکن abcdefghijklmnopqrstuvwxyz0123456789 بود" to "توکن [توکن] بود",
      "کارت 6037991234567890" to "کارت [عدد]",
      "کارتِ فارسی ۶۰۳۷۹۹۱۲۳۴۵۶۷۸۹۰" to "کارتِ فارسی [عدد]",
      //  عددِ کوتاه (شمارهٔ خط، اندازه) دست نمی‌خورد
      "at Foo.bar(Foo.kt:1234) size=500" to "at Foo.bar(Foo.kt:1234) size=500",
      //  شناسهٔ خودِ گزارش (۳۰ نویسه) پوشانده نمی‌شود
      "cer_01JB2Z3K4M5N6P7Q8R9S0T1V2W" to "cer_01JB2Z3K4M5N6P7Q8R9S0T1V2W",
    )
    for ((input, want) in cases) assertEquals(want, CrashReports.scrub(input, 4000), input)
    assertEquals(5, CrashReports.scrub("abcdefghij", 5).length)
  }

  @Test
  fun reportFromThrowableIsScrubbedBeforeItIsStored() {
    val t = IllegalStateException("مشتری 0791112233 با ali@example.com", RuntimeException("کارت 6037991234567890"))
    val r = CrashReports.fromThrowable(t, "2.14.4", now)
    assertTrue(CrashReports.ID.matches(r.id), r.id)
    assertEquals(now, r.atMs)
    assertEquals("2.14.4", r.appVersion)
    assertTrue(r.message.startsWith("IllegalStateException: "), r.message)
    for (secret in listOf("0791112233", "ali@example.com", "6037991234567890")) {
      assertFalse(r.message.contains(secret), "پیام: $secret")
      assertFalse(r.stack.contains(secret), "Stack: $secret")
    }
    assertTrue(r.message.length <= CrashReports.MAX_MESSAGE)
    assertTrue(r.stack.length <= CrashReports.MAX_STACK)
    assertEquals(16, r.signature.length)
    assertEquals(1, r.count)
    assertFalse(r.shown)
  }

  @Test
  fun sameCrashWithDifferentMessageHasTheSameSignature() {
    fun boom(msg: String): Throwable = try { error(msg) } catch (e: Throwable) { e }
    //  همان جا (همان قاب‌ها)، پیامِ دیگر — مثلِ حلقهٔ خطا با شمارهٔ عوض‌شونده
    val (a, b) = listOf("شمارهٔ ۱", "شمارهٔ ۲").map { CrashReports.fromThrowable(boom(it), "1", now) }
    assertEquals(a.signature, b.signature)
    val c = CrashReports.fromThrowable(IllegalArgumentException("x"), "1", now)
    assertTrue(a.signature != c.signature)
  }

  private fun report(id: Int, at: Long, sig: String = "s$id") = CrashReport(
    id = "cer_01JB2Z3K4M5N6P7Q8R9S0T" + id.toString().padStart(4, '0'),
    atMs = at, appVersion = "1", message = "m", stack = "s", signature = sig,
  )

  @Test
  fun crashLoopIsOneRowWithACounter() {
    var q = emptyList<CrashReport>()
    q = CrashReports.enqueue(q, report(1, now, "loop"), now)
    q = CrashReports.enqueue(q, report(2, now + 1_000, "loop"), now + 1_000)
    q = CrashReports.enqueue(q, report(3, now + 2_000, "loop"), now + 2_000)
    assertEquals(1, q.size)
    assertEquals(3, q.single().count)
    assertEquals(report(1, now).id, q.single().id, "همان ردیف و همان کدِ نشان‌داده")
    //  پس از بازهٔ یکی‌سازی ⇒ ردیفِ تازه
    val later = now + CrashReports.DEDUPE_MS + 1
    q = CrashReports.enqueue(q, report(4, later, "loop"), later)
    assertEquals(2, q.size)
    assertEquals(listOf(3, 1), q.map { it.count })
  }

  @Test
  fun queueKeepsTheNewestAndDropsOldOrBadRows() {
    var q = emptyList<CrashReport>()
    for (i in 1..CrashReports.MAX_PENDING + 5) q = CrashReports.enqueue(q, report(i, now + i), now + i)
    assertEquals(CrashReports.MAX_PENDING, q.size)
    assertEquals(report(6, 0).id, q.first().id, "کهنه‌ترها رفتند")
    assertEquals(report(CrashReports.MAX_PENDING + 5, 0).id, q.last().id)
    //  کهنه‌تر از هفت روز، شناسهٔ ناجور، یا زمانِ آینده بیرون
    val old = report(90, now - CrashReports.TTL_MS - 1)
    val bad = report(91, now).copy(id = "cer_bad")
    val future = report(92, now + 60_000)
    assertEquals(listOf(report(93, now).id), CrashReports.prune(listOf(old, bad, future, report(93, now)), now).map { it.id })
  }

  @Test
  fun sentRowsLeaveAndTheNoticeIsShownOnce() {
    val a = report(1, now - 10_000)
    val b = report(2, now - 5_000)
    val stale = report(3, now - CrashReports.NOTICE_MS - 1)
    var q = listOf(stale, a, b)
    assertEquals(b.id, CrashReports.notice(q, now)?.id, "تازه‌ترین")
    q = CrashReports.markShown(q, b.id)
    assertEquals(a.id, CrashReports.notice(q, now)?.id)
    q = CrashReports.markShown(q, a.id)
    assertNull(CrashReports.notice(q, now), "کهنه‌تر از یک روز نشان داده نمی‌شود")
  }

  @Test
  fun sentRowStaysUntilItsCodeIsShownThenLeaves() {
    val a = report(1, now - 10_000)
    val b = report(2, now - 5_000)
    val stale = report(3, now - CrashReports.NOTICE_MS - 1)
    var q = listOf(stale, a, b)
    assertEquals(listOf(stale.id, a.id, b.id), CrashReports.toSend(q, now).map { it.id }, "کهنه‌ترین اول")
    q = CrashReports.settle(CrashReports.markSent(q, setOf(stale.id, a.id)), now)
    //  کهنه و فرستاده ⇒ بیرون؛ فرستاده ولی کدش هنوز نشان داده نشده ⇒ می‌ماند
    assertEquals(listOf(a.id, b.id), q.map { it.id })
    assertEquals(listOf(b.id), CrashReports.toSend(q, now).map { it.id }, "فرستاده دوباره نمی‌رود")
    assertEquals(b.id, CrashReports.notice(q, now)?.id)
    q = CrashReports.settle(CrashReports.markShown(q, a.id), now)
    assertEquals(listOf(b.id), q.map { it.id }, "فرستاده و نشان‌داده ⇒ کار تمام")
    q = CrashReports.settle(CrashReports.markShown(q, b.id), now)
    assertEquals(listOf(b.id), q.map { it.id }, "نشان‌داده ولی نفرستاده ⇒ هنوز می‌رود")
    //  همان خطا پس از فرستادن ⇒ ردیفِ تازه، نه شمارندهٔ ردیفِ فرستاده
    val sent = CrashReports.markSent(listOf(report(5, now, "loop")), setOf(report(5, now).id))
    val again = CrashReports.enqueue(sent, report(6, now + 1_000, "loop"), now + 1_000)
    assertEquals(listOf(1, 1), again.map { it.count })
    assertEquals(listOf(report(6, 0).id), CrashReports.toSend(again, now + 1_000).map { it.id })
  }

  @Test
  fun payloadUsesTheServerFieldNamesAndCarriesOnlyScrubbedText() {
    val r = report(1, now).copy(count = 4, message = "IllegalStateException: [شماره]", stack = "at A.b(A.kt:1)")
    val p = CrashReports.payload(r)
    assertEquals(setOf("report_id", "app", "app_version", "message", "stack", "log_tail"), p.keys)
    assertEquals(r.id, p["report_id"])
    assertEquals("shop", p["app"])
    assertEquals("count=4", p["log_tail"])
    assertEquals("IllegalStateException: [شماره]", p["message"])
  }
}
