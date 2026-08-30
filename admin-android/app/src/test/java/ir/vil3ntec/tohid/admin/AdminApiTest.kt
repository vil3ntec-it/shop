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

  @Test fun `نشانیِ بدونِ پروتکل رد می‌شود`() {
    assertEquals("bad_server", errorFor("192.168.1.5:8080").code)
  }

  /*
   *  سرورِ خانگی https ندارد و قرار هم نیست داشته باشد — برای آی‌پیِ
   *  داخلی گواهی صادر نمی‌شود. پس http در خانه باز است و بیرون از خانه بسته.
   */
  @Test fun `http به سرورِ خانگی می‌رسد`() {
    assertEquals("http://192.168.0.101:4700", AdminApi.normalizeBase("http://192.168.0.101:4700"))
    assertEquals("http://10.0.0.8:4700", AdminApi.normalizeBase("http://10.0.0.8:4700/"))
    assertEquals("http://172.16.5.4:80", AdminApi.normalizeBase("http://172.16.5.4:80"))
    assertEquals("http://127.0.0.1:4700", AdminApi.normalizeBase("http://127.0.0.1:4700"))
    assertEquals("http://server.local", AdminApi.normalizeBase("http://server.local"))
  }

  @Test fun `http به نشانیِ اینترنتی رد می‌شود`() {
    val e = errorFor("http://tohid.example.com")
    assertEquals("cleartext", e.code)
    assertTrue("پیام باید راهِ درست را بگوید: ${e.message}", e.message!!.contains("https"))
    assertEquals("cleartext", errorFor("http://8.8.8.8").code)
    // ۱۷۲.۳۲ دیگر خصوصی نیست، هرچند شبیهش است
    assertEquals("cleartext", errorFor("http://172.32.0.1").code)
  }

  @Test fun `https همه جا باز است`() {
    assertEquals("https://x.trycloudflare.com", AdminApi.normalizeBase("https://x.trycloudflare.com/"))
    assertEquals("https://192.168.0.101", AdminApi.normalizeBase("https://192.168.0.101"))
  }

  @Test fun `مرزهای شبکهٔ خصوصی`() {
    assertTrue(AdminApi.isHomeAddress("192.168.255.255"))
    assertTrue(AdminApi.isHomeAddress("172.31.0.1"))
    assertTrue(AdminApi.isHomeAddress("169.254.1.1"))
    assertTrue(AdminApi.isHomeAddress("localhost"))
    assertTrue(AdminApi.isHomeAddress("box"))
    assertTrue(!AdminApi.isHomeAddress("172.15.0.1"))
    assertTrue(!AdminApi.isHomeAddress("11.0.0.1"))
    assertTrue(!AdminApi.isHomeAddress("example.com"))
  }

  @Test fun `پورت و مسیر روی https می‌ماند`() {
    assertEquals("https://box.example:8443", AdminApi.normalizeBase("https://box.example:8443/"))
  }
}
