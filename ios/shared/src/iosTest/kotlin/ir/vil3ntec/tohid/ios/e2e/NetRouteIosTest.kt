package ir.vil3ntec.tohid.ios.e2e

import ir.vil3ntec.tohid.ios.IosHost
import ir.vil3ntec.tohid.ios.NetRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 *  قاعدهٔ لایهٔ حملِ آزمونِ سرتاسری (`IosNetRoute` ⇐ `NetRule`، گامِ ۸): فقط میزبانِ برنامه به سرورِ آزمونِ همین مک
 *  می‌رود، با همان مسیر و پرسش؛ میزبانِ دیگر، نشانیِ هم‌پیشوند و درگاهِ دیگر دست‌نخورده. سرورِ غیرِ loopback پذیرفته
 *  نیست. و فرآیندِ آزمون «درونِ بستهٔ برنامه» نیست (`IosHost`) ⇐ اعلان و لرزش آن‌جا بی‌صدا.
 *  (قاعدهٔ سراسریِ `IosNetRoute` را فقط `CoreFlowsE2EIosTest` می‌گذارد و تا پایانِ فرآیند نگه می‌دارد.)
 */
class NetRouteIosTest {
  @Test
  fun onlyTheAppHostGoesToTheLocalTestServer() {
    val r = NetRule.of("https://api.example.com", "http://127.0.0.1:4100")
    assertEquals("http://127.0.0.1:4100/api/auth/login", r.of("https://api.example.com/api/auth/login"))
    assertEquals("http://127.0.0.1:4100/api/x?a=1", r.of("https://API.example.com:443/api/x?a=1"))
    assertEquals("http://127.0.0.1:4100?q=1", r.of("https://api.example.com?q=1"))
    assertEquals("ws://127.0.0.1:4100/api/sync/v1/live", r.of("wss://api.example.com/api/sync/v1/live"))
    //  میزبانِ دیگر، هم‌پیشوندِ فریبنده و درگاهِ دیگر دست نمی‌خورند
    assertEquals("https://api.example.com.evil.io/x", r.of("https://api.example.com.evil.io/x"))
    assertEquals("https://cdn.example.com/a.png", r.of("https://cdn.example.com/a.png"))
    assertEquals("https://api.example.com:8443/x", r.of("https://api.example.com:8443/x"))
    assertEquals("not a url", r.of("not a url"))
    assertFailsWith<IllegalArgumentException> { NetRule.of("https://api.example.com", "http://10.0.0.5:4100") }
    assertFailsWith<IllegalArgumentException> { NetRule.of("https://api.example.com", "https://127.0.0.1:4100") }
  }

  @Test
  fun theTestProcessIsNotTheAppBundle() {
    assertFalse(IosHost.inApp)
  }
}
