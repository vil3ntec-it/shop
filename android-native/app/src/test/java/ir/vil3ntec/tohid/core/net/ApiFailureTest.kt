package ir.vil3ntec.tohid.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سنجشِ خواندنِ خطای سرور.
 *
 *  این تست‌ها دقیقاً همان باگی را می‌بندند که پیامِ فارسیِ سرور را به
 *  کاربر نمی‌رساند: سرور خطا را در `{ "error": { … } }` می‌فرستد و لایهٔ
 *  قبلی در سطحِ اول دنبالش می‌گشت.
 */
class ApiFailureTest {

  @Test
  fun `پیام و کد از شکلِ تودرتوی سرور خوانده می‌شود`() {
    val body = """{"error":{"code":"bad_credentials","message":"ایمیل/شماره یا رمز درست نیست"}}"""
    val (message, code) = ApiFailure.parse(body)
    assertEquals("ایمیل/شماره یا رمز درست نیست", message)
    assertEquals("bad_credentials", code)
  }

  @Test
  fun `شکلِ تختِ قدیمی هم خوانده می‌شود`() {
    val (message, code) = ApiFailure.parse("""{"message":"چیزی نشد","code":"oops"}""")
    assertEquals("چیزی نشد", message)
    assertEquals("oops", code)
  }

  @Test
  fun `بدنهٔ خراب یا خالی استثنا پرتاب نمی‌کند`() {
    assertEquals(null to null, ApiFailure.parse(null))
    assertEquals(null to null, ApiFailure.parse(""))
    assertEquals(null to null, ApiFailure.parse("<html>خطای دروازه</html>"))
    //  `error` یک شیء است نه رشته — همان چیزی که لایهٔ قبلی رویش می‌شکست
    assertEquals(null to null, ApiFailure.parse("""{"error":{}}"""))
  }

  @Test
  fun `پیامِ سرور به کاربر می‌رسد، نه پیامِ عمومی`() {
    val failure = ApiFailure.fromHttp(
      429,
      """{"error":{"code":"otp_resend_wait","message":"۹۰ ثانیه دیگر می‌توانید کد تازه بخواهید"}}""",
      authenticated = false,
    )
    assertTrue(failure is ApiFailure.RateLimited)
    assertEquals("۹۰ ثانیه دیگر می‌توانید کد تازه بخواهید", failure.userMessage)
    assertEquals("otp_resend_wait", failure.code)
  }

  @Test
  fun `اگر سرور چیزی نگفت، پیامِ عمومیِ فارسی می‌آید`() {
    val failure = ApiFailure.fromHttp(500, "", authenticated = true)
    assertTrue(failure is ApiFailure.ServerFault)
    assertTrue(failure.userMessage.isNotBlank())
    //  خطای خودِ سرور ممکن است گذرا باشد
    assertTrue(failure.retryable)
  }

  @Test
  fun `۴۰۱ روی درخواستِ توکن‌دار یعنی نشست تمام شده`() {
    //  این تفاوت مهم است: کاربری که یک ساعت کار کرده رمزش را غلط نزده،
    //  توکنش پیر شده. پیامِ «رمز درست نیست» هم غلط بود و هم بی‌فایده.
    val expired = ApiFailure.fromHttp(401, """{"error":{"code":"invalid_token"}}""", authenticated = true)
    assertTrue(expired is ApiFailure.SessionExpired)

    val badLogin = ApiFailure.fromHttp(
      401,
      """{"error":{"code":"bad_credentials","message":"رمز درست نیست"}}""",
      authenticated = false,
    )
    assertTrue(badLogin is ApiFailure.Unauthorized)
    assertEquals("رمز درست نیست", badLogin.userMessage)
  }

  @Test
  fun `۴۰۱ با کدِ رمزِ غلط، حتی با توکن، نشستِ تمام‌شده نیست`() {
    //  عوض کردنِ رمز با رمزِ فعلیِ اشتباه: درخواست توکن دارد ولی مشکل
    //  از توکن نیست. اگر «نشست تمام شد» می‌گفتیم، کاربر بی‌دلیل بیرون
    //  می‌افتاد.
    val failure = ApiFailure.fromHttp(
      401,
      """{"error":{"code":"bad_credentials","message":"رمز فعلی درست نیست"}}""",
      authenticated = true,
    )
    assertTrue(failure is ApiFailure.Unauthorized)
    assertEquals("رمز فعلی درست نیست", failure.userMessage)
  }

  @Test
  fun `هر وضعیت به نوعِ خودش می‌رسد`() {
    assertTrue(ApiFailure.fromHttp(400, "", false) is ApiFailure.Invalid)
    assertTrue(ApiFailure.fromHttp(403, "", false) is ApiFailure.Forbidden)
    assertTrue(ApiFailure.fromHttp(404, "", false) is ApiFailure.NotFound)
    assertTrue(ApiFailure.fromHttp(409, "", false) is ApiFailure.Conflict)
  }

  @Test
  fun `فقط خطاهای گذرا دوباره تلاش می‌شوند`() {
    assertTrue(ApiFailure.Timeout().retryable)
    assertTrue(ApiFailure.Unreachable().retryable)
    assertTrue(ApiFailure.Offline().retryable)
    //  رمزِ غلط با تکرار درست نمی‌شود
    assertFalse(ApiFailure.fromHttp(401, "", false).retryable)
    assertFalse(ApiFailure.fromHttp(400, "", false).retryable)
    assertFalse(ApiFailure.SessionExpired().retryable)
  }

  @Test
  fun `استثناهای شبکه از هم جدا می‌شوند`() {
    //  همه‌شان `IOException` بودند و همه یک پیام می‌گرفتند
    assertTrue(ApiFailure.fromException(java.net.SocketTimeoutException()) is ApiFailure.Timeout)
    assertTrue(ApiFailure.fromException(java.net.UnknownHostException("x")) is ApiFailure.Unreachable)
    assertTrue(ApiFailure.fromException(java.net.ConnectException()) is ApiFailure.Unreachable)
    //  خطای این لایه دست‌نخورده بالا می‌رود
    val original = ApiFailure.SessionExpired()
    assertTrue(ApiFailure.fromException(original) === original)
  }

  @Test
  fun `متنِ نمایشی همیشه فارسی است`() {
    val local = IllegalStateException("Unexpected token in JSON at position 4")
    assertEquals("پرونده خوانده نشد", local.userText("پرونده خوانده نشد"))
    assertEquals("اینترنت وصل نیست", ApiFailure.Offline().userText("جایگزین"))
  }
}
