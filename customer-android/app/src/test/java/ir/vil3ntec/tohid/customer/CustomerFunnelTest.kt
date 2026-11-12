package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  RPT-14 — تپشِ بالای قیف از برنامهٔ خرید (`ModelFunnel.kt` روی [ir.vil3ntec.tohid.core.shopper.ShopperFunnel]): با
 *  `features.funnel` یک «بازدید» با رسیدنِ دکان، یک «دیدنِ کالا» و یک «سبد» در هر بازکردن (همان `POST` ِ بی بدنهٔ وب)؛
 *  کم کردنِ سبد و کالای دوم چیزی نمی‌فرستند؛ پیوندِ مستقیمِ کالا «دیدن» را پسِ «بازدید» می‌فرستد؛ پرچمِ خاموش ⇒ هیچ.
 *  فقط در CI (`testDebugUnitTest`)؛ قاعده‌ها روی کامپیوتر با `core` ‹`ShopperFunnelTest`› سنجیده می‌شوند.
 */
class CustomerFunnelTest {

  @After fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  private class MapKv : ShopperKeyValue {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  private var funnelOn = true
  private val beats = mutableListOf<String>()
  private val rice = """{"id":"rice","name":"برنج","unit":"کیلو","priceMinor":12050,"maxQtyMilli":3000,"photo":false}"""
  private fun store() = """{"store":{"slug":"golestan","title":"گلستان","contactPhone":"0700000001",
    "status":{"badge":"open_online","connection":"confirmed","orderAcceptance":"accepting"},
    "fulfillment":{"delivery":true,"pickup":true},"deliveryFeeMinor":5000,"minOrderMinor":0,"categories":["خوراکی"],
    "delivery":{"zoned":false,"zones":[],"allowAsap":true,"scheduling":{"delivery":false,"pickup":false}}},
    "verification":{"available":true},"market":{"available":false},"features":{"funnel":$funnelOn}}"""

  private val http = ShopperHttp { r ->
    val p = r.path
    when {
      p.contains("/funnel/") -> { beats += "${r.method} $p"; ShopperResponse(204, "") }
      p == "/api/market" -> ShopperResponse(404, """{"error":{"code":"market_not_found","message":"بسته"}}""")
      p == "/api/store/golestan" -> ShopperResponse(200, store())
      p == "/api/store/golestan/products/rice" -> ShopperResponse(200, """{"product":$rice}""")
      p.startsWith("/api/store/golestan/products") -> ShopperResponse(200, """{"items":[$rice],"nextCursor":null}""")
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, MapKv(), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { 1_000L }, debounceMs = 0,
  )

  private fun beat(step: String) = "POST /api/store/golestan/funnel/$step"

  @Test fun visitViewAndCartOncePerOpening() {
    val m = model()
    m.go(Route.Shop("golestan"))
    assertEquals(listOf(beat("visit")), beats)
    m.go(Route.Product("golestan", "rice"))
    m.back()
    m.go(Route.Product("golestan", "rice"))
    assertEquals("کالای دومِ همان بازکردن «دیدن» ِ دوم نمی‌فرستد", listOf(beat("visit"), beat("view")), beats)
    val rice = m.items.single()
    m.add("golestan", rice)
    m.add("golestan", rice)
    m.step("golestan", "rice", up = true)
    m.step("golestan", "rice", up = false)
    assertEquals(listOf(beat("visit"), beat("view"), beat("cart")), beats)
  }

  @Test fun directProductLinkSendsViewAfterVisit() {
    val m = model()
    m.go(Route.Product("golestan", "rice"))
    assertEquals(listOf(beat("visit"), beat("view")), beats)
  }

  @Test fun flagOffSendsNothing() {
    funnelOn = false
    val m = model()
    m.go(Route.Shop("golestan"))
    m.go(Route.Product("golestan", "rice"))
    m.add("golestan", m.items.single())
    assertEquals(emptyList<String>(), beats)
  }
}
