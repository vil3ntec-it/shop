package ir.vil3ntec.tohid.update

import ir.vil3ntec.tohid.update.ResumableDownload.Response
import ir.vil3ntec.tohid.update.ResumableDownload.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  تصمیمِ دانلودِ ادامه‌دار دربارهٔ هر پاسخ — بی شبکه.
 *  دانلودِ واقعی با سرورِ محلیِ قطع‌شونده: `desktop/src/test/…/ResumableDownloadServerTest.kt`
 *  (تست‌های اندروید روی `android.jar` کامپایل می‌شوند و `com.sun.net.httpserver` ندارند).
 */
class ResumableDownloadTest {

  private val etag = "\"abc123\""

  @Test
  fun `۲۰۶ از همان بایت ⇒ ادامه از همان‌جا`() {
    val s = ResumableDownload.decide(400, 1000, 1000, etag, Response(206, "bytes 400-999/1000", 600, etag))
    assertEquals(Step.Write(400, 1000, etag), s)
  }

  @Test
  fun `۲۰۶ از بایتِ دیگر ⇒ از صفر، نه قاطی`() {
    val s = ResumableDownload.decide(400, 1000, 1000, etag, Response(206, "bytes 0-999/1000", 1000, etag))
    assertTrue(s is Step.Restart)
  }

  @Test
  fun `۲۰۰ به‌جای ۲۰۶ ⇒ همان بدنه روی فایلِ خالی`() {
    val s = ResumableDownload.decide(400, 1000, 1000, etag, Response(200, null, 1000, "\"new\""))
    assertEquals(Step.Write(0, 1000, "\"new\""), s)
  }

  @Test
  fun `اندازهٔ کلِ دیگر یا ETagِ دیگر ⇒ از صفر`() {
    assertTrue(ResumableDownload.decide(400, 1000, 1000, etag, Response(206, "bytes 400-1199/1200", 800, etag)) is Step.Restart)
    assertTrue(ResumableDownload.decide(400, 0, 1200, etag, Response(206, "bytes 400-999/1000", 600, etag)) is Step.Restart)
    assertTrue(ResumableDownload.decide(400, 1000, 1000, etag, Response(206, "bytes 400-999/1000", 600, "\"other\"")) is Step.Restart)
  }

  @Test
  fun `اندازهٔ نامعلوم از Content-Range گرفته می‌شود، نه شروع از صفر`() {
    val s = ResumableDownload.decide(400, 0, -1, "", Response(206, "bytes 400-999/1000", 600))
    assertEquals(Step.Write(400, 1000, ""), s)
    //  و از Content-Lengthِ پاسخِ اول
    assertEquals(Step.Write(0, 1000, ""), ResumableDownload.decide(0, 0, -1, "", Response(200, null, 1000)))
  }

  @Test
  fun `۴۱۶ وقتی همه رسیده ⇒ تمام`() {
    assertEquals(Step.Complete(1000), ResumableDownload.decide(1000, 1000, 1000, etag, Response(416, "bytes */1000")))
    assertTrue(ResumableDownload.decide(900, 1000, 1000, etag, Response(416, "bytes */800")) is Step.Restart)
  }

  @Test
  fun `۵xx و ۴۲۹ دوباره؛ ۴۰۴ نه`() {
    assertEquals(Step.Fail(503, true), ResumableDownload.decide(0, 1000, -1, "", Response(503)))
    assertEquals(Step.Fail(429, true), ResumableDownload.decide(0, 1000, -1, "", Response(429)))
    assertEquals(Step.Fail(404, false), ResumableDownload.decide(0, 1000, -1, "", Response(404)))
  }

  @Test
  fun `If-Range فقط ETagِ قوی یا تاریخ`() {
    assertEquals(etag, ResumableDownload.validatorOf(etag, "Sat, 17 Oct 2026 10:00:00 GMT"))
    assertEquals("Sat, 17 Oct 2026 10:00:00 GMT", ResumableDownload.validatorOf("W/\"x\"", "Sat, 17 Oct 2026 10:00:00 GMT"))
    assertEquals("", ResumableDownload.validatorOf("W/\"x\"", null))
  }

  @Test
  fun `Content-Range ناجور پذیرفته نمی‌شود`() {
    assertNull(ResumableDownload.parseContentRange("bytes 10-5/100"))
    assertNull(ResumableDownload.parseContentRange("bytes 10-100/100"))
    assertNull(ResumableDownload.parseContentRange("items 0-1/2"))
    assertEquals(ResumableDownload.Span(10, 19, -1), ResumableDownload.parseContentRange("bytes 10-19/*"))
  }

  @Test
  fun `متنِ وضعیت — ادامه از X٪`() {
    assertEquals("ادامه از 42٪", ResumableDownload.statusText(ResumableDownload.Event.Progress(500, 1000, 420)))
    assertNull(ResumableDownload.statusText(ResumableDownload.Event.Progress(500, 1000, 0)))
    assertEquals("منتظرِ اینترنت… (ادامه از 50٪)", ResumableDownload.statusText(ResumableDownload.Event.Offline(500, 1000)))
  }
}
