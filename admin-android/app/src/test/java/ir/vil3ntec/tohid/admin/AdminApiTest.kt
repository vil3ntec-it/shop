package ir.vil3ntec.tohid.admin

import ir.vil3ntec.tohid.admin.net.AdminApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  نشانیِ سرور، پیش از آنکه به شبکه برسد.
 *
 *  این تنها جایی است که کاربر چیزی تایپ می‌کند و اگر اشتباه باشد برنامه
 *  هیچ کاری نمی‌تواند بکند. پس همین‌جا آزموده می‌شود، نه با گوشی در دست.
 */
class AdminApiTest {

  private fun errorFor(raw: String): AdminApi.ApiError {
    try {
      AdminApi.normalizeBase(raw)
    } catch (e: AdminApi.ApiError) {
      return e
    }
    fail("انتظار خطا داشتیم برای: $raw")
    throw IllegalStateException()
  }

  @Test fun `اسلشِ آخر حذف می‌شود`() {
    assertEquals("https://a.trycloudflare.com", AdminApi.normalizeBase("https://a.trycloudflare.com/"))
  }

  @Test fun `فاصلهٔ دور و بر اهمیتی ندارد`() {
    assertEquals("https://a.trycloudflare.com", AdminApi.normalizeBase("  https://a.trycloudflare.com  "))
  }

  @Test fun `نشانیِ خالی رد می‌شود`() {
    assertEquals("no_server", errorFor("   ").code)
  }

  @Test fun `http رد می‌شود و دلیلش گفته می‌شود`() {
    val e = errorFor("http://192.168.1.5:8080")
    assertEquals("cleartext", e.code)
    assertTrue("پیام باید بگوید چه کار کند: ${e.message}", e.message!!.contains("https"))
  }

  @Test fun `نشانیِ بدونِ پروتکل رد می‌شود`() {
    assertEquals("bad_server", errorFor("192.168.1.5:8080").code)
  }

  @Test fun `پورت و مسیر روی https می‌ماند`() {
    assertEquals("https://box.example:8443", AdminApi.normalizeBase("https://box.example:8443/"))
  }
}
