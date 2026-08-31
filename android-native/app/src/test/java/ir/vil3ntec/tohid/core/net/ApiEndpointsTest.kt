package ir.vil3ntec.tohid.core.net

import ir.vil3ntec.tohid.core.config.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiEndpointsTest {

  @Test
  fun `هیچ مسیری نسخه را داخلِ خودش ندارد`() {
    //  نسخه یک جاست؛ اگر مسیری خودش `/api/v1` داشته باشد، رفتن به `v2`
    //  همان یکی را جا می‌گذارد.
    val paths = listOf(
      ApiEndpoints.HEALTH, ApiEndpoints.CONFIG,
      ApiEndpoints.Auth.LOGIN, ApiEndpoints.Auth.REFRESH, ApiEndpoints.Auth.OTP_VERIFY,
      ApiEndpoints.Me.DEVICES, ApiEndpoints.Me.SUBSCRIPTION,
      ApiEndpoints.Shop.ME, ApiEndpoints.Shop.STAFF_CODES,
      ApiEndpoints.Sync.PUSH, ApiEndpoints.Sync.PULL,
      ApiEndpoints.License.SYNC,
    )
    paths.forEach { path ->
      assertFalse("مسیر نباید نسخه داشته باشد: $path", path.contains("/api/"))
      assertEquals("مسیر باید با / شروع شود: $path", '/', path.first())
    }
  }

  @Test
  fun `شناسه در مسیر encode می‌شود`() {
    assertEquals("/me/devices/dev_abc123", ApiEndpoints.Me.device("dev_abc123"))
    //  شناسه‌ای که کاراکترِ خاص دارد نباید مسیر را بشکند
    assertEquals("/shop/members/a%2Fb", ApiEndpoints.Shop.member("a/b"))
  }

  @Test
  fun `پرسمان encode می‌شود`() {
    assertEquals(
      "/shop/sync/pull?since=12&deviceId=abc",
      ApiEndpoints.withQuery(ApiEndpoints.Sync.PULL, mapOf("since" to 12L, "deviceId" to "abc")),
    )
    //  مقدارِ خالی یا نبود، اصلاً فرستاده نمی‌شود
    assertEquals(
      "/shop/sync/pull?since=0",
      ApiEndpoints.withQuery(ApiEndpoints.Sync.PULL, mapOf("since" to 0L, "deviceId" to "", "x" to null)),
    )
    //  `&` در مقدار، پرسمان را نمی‌شکند
    assertEquals(
      "/shop/members?q=a%26b%20c",
      ApiEndpoints.withQuery(ApiEndpoints.Shop.MEMBERS, mapOf("q" to "a&b c")),
    )
  }

  @Test
  fun `مسیر و نشانی با هم یک نشانیِ درست می‌سازند`() {
    assertEquals(
      "https://api.example.com/api/v1/shop/sync/pull?since=5&deviceId=d1",
      ApiConfig.urlOf(
        "https://api.example.com",
        ApiEndpoints.withQuery(ApiEndpoints.Sync.PULL, mapOf("since" to 5L, "deviceId" to "d1")),
      ),
    )
  }
}
