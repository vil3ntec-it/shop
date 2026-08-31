package ir.vil3ntec.tohid.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سنجشِ قاعده‌های نشانیِ سرور.
 *
 *  این‌ها روی JVM اجرا می‌شوند، بدونِ گوشی و شبیه‌ساز — چون `ApiConfig`
 *  عمداً هیچ‌چیزِ اندرویدی ندارد.
 */
class ApiConfigTest {

  @Test
  fun `نسخهٔ API یک جا نوشته می‌شود`() {
    assertEquals("/api/v1", ApiConfig.API_PREFIX)
    assertTrue(ApiConfig.API_PREFIX.endsWith(ApiConfig.API_VERSION))
  }

  @Test
  fun `اسلشِ آخر و فاصله برداشته می‌شود`() {
    assertEquals("https://api.example.com", ApiConfig.normalize("  https://api.example.com/  "))
    assertEquals("https://api.example.com", ApiConfig.normalize("https://api.example.com///"))
  }

  @Test
  fun `بدونِ طرح، https گذاشته می‌شود نه http`() {
    assertEquals("https://api.example.com", ApiConfig.normalize("api.example.com"))
  }

  @Test
  fun `اگر کاربر پیشوند API را هم چسبانده باشد، برداشته می‌شود`() {
    //  وگرنه مسیرها `/api/v1/api/v1/…` می‌شدند و هر درخواست ۴۰۴ می‌گرفت
    assertEquals("https://api.example.com", ApiConfig.normalize("https://api.example.com/api/v1"))
    assertEquals("https://api.example.com", ApiConfig.normalize("https://api.example.com/api/v1/"))
    assertEquals("https://api.example.com", ApiConfig.normalize("https://api.example.com/api"))
  }

  @Test
  fun `نشانیِ کامل، پیشوند را یک بار می‌گذارد`() {
    assertEquals(
      "https://api.example.com/api/v1/auth/login",
      ApiConfig.urlOf("https://api.example.com/", "/auth/login"),
    )
    //  مسیرِ بدونِ اسلشِ اول هم باید همان شود
    assertEquals(
      "https://api.example.com/api/v1/health",
      ApiConfig.urlOf("api.example.com", "health"),
    )
  }

  @Test
  fun `نشانیِ خالی پذیرفته نیست`() {
    assertEquals(ApiConfig.Rejection.MISSING, ApiConfig.reject("", allowInsecure = false))
    assertEquals(ApiConfig.Rejection.MISSING, ApiConfig.reject(null, allowInsecure = false))
  }

  @Test
  fun `در نسخهٔ منتشرشده فقط https`() {
    assertEquals(
      ApiConfig.Rejection.INSECURE,
      ApiConfig.reject("http://api.example.com", allowInsecure = false),
    )
    //  ساختِ آزمایشی روی شبکهٔ خانگی باید بتواند http بزند
    assertNull(ApiConfig.reject("http://192.168.1.5:3000", allowInsecure = true))
  }

  @Test
  fun `نشانیِ عددی در نسخهٔ منتشرشده پذیرفته نیست`() {
    //  قاعدهٔ اصلی: برنامه دامنه می‌شناسد نه جای فیزیکیِ سرور. اگر IP
    //  داخلِ نسخه بنشیند، جابه‌جا شدنِ سرور نسخهٔ تازه لازم دارد.
    assertEquals(
      ApiConfig.Rejection.IP_ADDRESS,
      ApiConfig.reject("https://203.0.113.10", allowInsecure = false),
    )
    assertEquals(
      ApiConfig.Rejection.IP_ADDRESS,
      ApiConfig.reject("https://203.0.113.10:8443", allowInsecure = false),
    )
    assertNull(ApiConfig.reject("https://api.example.com", allowInsecure = false))
  }

  @Test
  fun `تشخیصِ نشانیِ عددی`() {
    assertTrue(ApiConfig.isIpLiteral("10.0.0.1"))
    assertTrue(ApiConfig.isIpLiteral("255.255.255.255"))
    assertTrue(ApiConfig.isIpLiteral("2001:db8::1"))
    assertFalse(ApiConfig.isIpLiteral("api.example.com"))
    //  عددِ بیرون از بازه، IP نیست
    assertFalse(ApiConfig.isIpLiteral("999.1.1.1"))
    //  دامنه‌ای که با عدد شروع می‌شود هم IP نیست
    assertFalse(ApiConfig.isIpLiteral("1host.example.com"))
  }

  @Test
  fun `نامِ میزبان بدونِ پورت و مسیر خوانده می‌شود`() {
    assertEquals("api.example.com", ApiConfig.hostOf("https://api.example.com:8443/api/v1"))
    assertEquals("api.example.com", ApiConfig.hostOf("api.example.com"))
    assertEquals("2001:db8::1", ApiConfig.hostOf("https://[2001:db8::1]:8443"))
  }
}
