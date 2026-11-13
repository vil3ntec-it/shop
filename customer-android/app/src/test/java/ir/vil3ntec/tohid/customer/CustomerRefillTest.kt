package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.Refills
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperMemory
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Route
import ir.vil3ntec.tohid.customer.ui.fillRefill
import ir.vil3ntec.tohid.customer.ui.muteRefill
import ir.vil3ntec.tohid.customer.ui.refillCards
import ir.vil3ntec.tohid.customer.ui.snoozeRefill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  MKT-06 روی فرستندهٔ جعلی (بی اندروید): سفارشِ ثبت‌شده ⇐ خانه چرخهٔ مصرف را از سرور (`?ids=`) می‌خواند ⇐ پس از
 *  همان چند روز کارتِ «وقتِ دوباره خریدن؟» ⇐ «افزودن» سبد را با قیمتِ **امروزِ سرور** پر می‌کند؛ «بعداً» و «دیگر نه».
 */
class CustomerRefillTest {

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

  private val store = """{"store":{"slug":"golestan","title":"گلستان","contactPhone":"0700000001",
    "status":{"badge":"open_online","connection":"confirmed","orderAcceptance":"accepting"},
    "fulfillment":{"delivery":true,"pickup":true},"deliveryFeeMinor":5000,"minOrderMinor":0,"categories":["خوراکی"],
    "delivery":{"zoned":false,"zones":[],"allowAsap":true,"scheduling":{"delivery":false,"pickup":false}}},
    "verification":{"available":true},"market":{"available":false}}"""
  private var teaPrice = 9000L
  private val tea get() = """{"id":"tea","name":"چای سبز","unit":"بسته","priceMinor":$teaPrice,"photo":false,"consumptionDays":30}"""
  private val order = """{"order":{"token":"v2.tok","orderNo":5,"status":"new","fulfillment":"delivery","totalMinor":23000,
    "createdAt":1000,"updatedAt":1000,"canCancel":true,"timeline":[{"status":"new","at":1000}],
    "lines":[{"productId":"tea","name":"چای سبز","unit":"بسته","qtyMilli":2000,"unitPriceMinor":9000,"lineTotalMinor":18000}]}}"""

  private val seen = mutableListOf<ShopperRequest>()
  private var now = 1_000L

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    when {
      p == "/api/market" -> ShopperResponse(404, """{"error":{"code":"market_not_found","message":"بسته"}}""")
      p == "/api/store/golestan" -> ShopperResponse(200, store)
      p.startsWith("/api/store/golestan/products?ids=") -> ShopperResponse(200, """{"items":[$tea]}""")
      p.startsWith("/api/store/golestan/products") -> ShopperResponse(200, """{"items":[$tea],"nextCursor":null}""")
      p.endsWith("/verify/start") -> ShopperResponse(200, """{"sent":true,"expiresAt":999}""")
      p.endsWith("/verify/check") -> ShopperResponse(200, """{"email":"c700000002@test.local","ticket":"v2.ticket","expiresAt":99999999999}""")
      p.endsWith("/checkout") -> ShopperResponse(201, order)
      p.startsWith("/api/store/golestan/orders/") -> ShopperResponse(200, order)
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private val kv = MapKv()

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, kv, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { now }, debounceMs = 0,
  )

  private fun idsCalls() = seen.count { it.path.startsWith("/api/store/golestan/products?ids=") }

  @Test fun placedOrderBecomesABuyAgainCardWithTodaysServerPrice() {
    val m = model()
    m.go(Route.Shop("golestan"))
    m.add("golestan", m.items.single())
    m.go(Route.Checkout("golestan"))
    m.updateForm(m.form.copy(name = "احمد", phone = "0700000002", email = "c700000002@test.local", addressText = "کارتهٔ سه"))
    m.sendCode()
    m.code = "123456"
    m.checkCode()
    m.placeOrder()
    assertEquals(Route.Placed("golestan", "v2.tok"), m.route)
    //  قلم‌های پذیرفتهٔ سرور (۲ بسته)، نه سبدِ دستگاه (۱ بسته)
    val bought = ShopperMemory.load(kv).bought.single()
    assertEquals(listOf("tea", "2000", "1000"), listOf(bought.productId, bought.qtyMilli.toString(), bought.at.toString()))

    //  خانه ⇒ چرخه از سرور، یک بار در هر شش ساعت
    val before = idsCalls()
    m.go(Route.Home)
    assertEquals(before + 1, idsCalls())
    assertEquals(30, ShopperMemory.load(kv).bought.single().days)
    m.go(Route.Shop("golestan"))
    m.go(Route.Home)
    assertEquals(before + 1, idsCalls())
    assertTrue("پیش از موعد کارتی نیست", m.refillCards().isEmpty())

    //  ۲۹ روز بعد (یک روز پیش از پایانِ چرخه) ⇒ کارت؛ قیمتِ امروز گران‌تر شده
    now += 29 * Refills.DAY_MS
    val card = m.refillCards().single()
    assertEquals("گلستان", card.storeTitle)
    teaPrice = 9500L
    m.fillRefill(card)
    assertEquals(Route.CartPage("golestan"), m.route)
    val line = m.cartOf("golestan").lines.single()
    assertEquals("قیمتِ امروزِ سرور، نه ۹۰ افغانیِ سفارشِ پیشین", 9500L, line.priceMinor)
    assertEquals(2000L, line.qtyMilli)
    assertTrue("پس از افزودن کارت تا فردا نمی‌آید", m.refillCards().isEmpty())

    //  «بعداً» ⇒ فردا؛ «دیگر نه» ⇒ تا خریدِ بعد
    now += Refills.DAY_MS
    m.snoozeRefill(m.refillCards().single())
    assertTrue(m.refillCards().isEmpty())
    now += Refills.DAY_MS
    m.muteRefill(m.refillCards().single())
    now += 90 * Refills.DAY_MS
    assertTrue(m.refillCards().isEmpty())
  }
}
