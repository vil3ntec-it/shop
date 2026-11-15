package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperMemory
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Load
import ir.vil3ntec.tohid.customer.ui.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  جریانِ کاملِ برنامهٔ خرید روی فرستندهٔ جعلی (بی اندروید): خانه ⇐ دکان ⇐ سبد ⇐ تأییدِ شماره ⇐ ثبت (با ۴۰۹ ِ قیمت و
 *  قطعِ شبکه) ⇐ پیگیری ⇐ لغو. همین آزمون در CI با `testDebugUnitTest` و روی کامپیوتر با کامپایلِ Compose Desktop می‌دود.
 */
class CustomerModelTest {

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
  private val rice = """{"id":"rice","name":"برنج","unit":"کیلو","priceMinor":12050,"maxQtyMilli":3000,"photo":false}"""
  private val rice13 = rice.replace("12050", "13000")
  private fun order(status: String, total: Long, canCancel: Boolean) = """{"order":{"token":"v2.tok","orderNo":5,"status":"$status",
    "fulfillment":"delivery","totalMinor":$total,"createdAt":10,"updatedAt":20,"canCancel":$canCancel,"timeline":[{"status":"new","at":10}]}}"""

  private val seen = mutableListOf<ShopperRequest>()
  private var checkoutReplies = ArrayDeque<() -> ShopperResponse>()
  private var price = rice

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    when {
      p == "/api/market" -> ShopperResponse(404, """{"error":{"code":"market_not_found","message":"بسته"}}""")
      p == "/api/store/golestan" -> ShopperResponse(200, store)
      p.startsWith("/api/store/golestan/products?ids=") -> ShopperResponse(200, """{"items":[$price]}""")
      p.startsWith("/api/store/golestan/products") -> ShopperResponse(200, """{"items":[$price],"nextCursor":null}""")
      p.endsWith("/verify/start") -> ShopperResponse(200, """{"sent":true,"expiresAt":999}""")
      p.endsWith("/verify/check") -> ShopperResponse(200, """{"email":"c700000002@test.local","ticket":"v2.ticket","expiresAt":9999999}""")
      p.endsWith("/checkout") -> checkoutReplies.removeFirst()()
      p.endsWith("/cancel") -> ShopperResponse(200, order("cancelled_by_customer", 29100, false))
      p.startsWith("/api/store/golestan/orders/") -> ShopperResponse(200, order("new", 29100, true))
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private val kv = MapKv()

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, kv, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { 1_000L }, debounceMs = 0,
  )

  @Test fun shopToPlacedOrderWithPriceChangeAndNetworkRetry() {
    val m = model()
    assertEquals("market_not_found", (m.market as Load.Failed).error.code)

    //  نشانیِ چسبانده ⇒ دکان، کاتالوگ و «دکان‌های اخیر»
    assertFalse(m.openAddress("دکان؟"))
    assertTrue(m.addressError)
    assertTrue(m.openAddress("https://api.vill3n.top/store/golestan#/p/rice"))
    assertEquals(Route.Shop("golestan"), m.route)
    assertEquals(listOf("rice"), m.items.map { it.id })
    assertEquals("golestan", ShopperMemory.load(kv).recent.single().slug)

    //  سبد روی دستگاه، با سقفِ هر سفارش
    val p = m.items.single()
    repeat(4) { m.add("golestan", p) }
    assertEquals(3000L, m.cartOf("golestan").lines.single().qtyMilli)
    assertTrue(m.notice!!.isNotBlank())
    m.step("golestan", "rice", up = false)
    assertEquals(2000L, ShopperMemory.load(kv).carts.single().lines.single().qtyMilli)

    //  ثبت: تأییدِ شماره
    m.go(Route.CartPage("golestan"))
    m.go(Route.Checkout("golestan"))
    assertEquals("delivery", m.form.fulfillment)
    m.updateForm(m.form.copy(name = "احمد", phone = "0700000002", email = "c700000002@test.local", addressText = "کارتهٔ سه"))
    m.sendCode()
    assertTrue(m.codeSent != null)
    m.code = "۱۲۳۴۵۶"
    m.checkCode()
    assertEquals("123456", seen.last().body!!.substringAfter("\"code\":\"").substringBefore("\""))
    assertEquals("v2.ticket", ShopperMemory.load(kv).ticket?.ticket)
    assertTrue(m.problems().isEmpty())

    //  ۱) قیمت در سرور عوض شده ⇒ ۴۰۹ با برگهٔ امروز؛ سبد تازه و دکمه «پذیرفتن و ثبت»
    checkoutReplies.add {
      ShopperResponse(409, """{"error":{"code":"price_changed","message":"x","quote":{"lines":[{"productId":"rice","unitPriceMinor":13000}],"totalMinor":31000}}}""")
    }
    m.placeOrder()
    val key1 = seen.last().headers["Idempotency-Key"]!!
    assertEquals("v2.ticket", seen.last().headers["X-Store-Ticket"])
    assertTrue(m.repriced)
    assertEquals(13000L, m.cartOf("golestan").lines.single().priceMinor)
    assertTrue(m.placeNote!!.contains("۳۱۰"))

    //  ۲) شبکه قطع ⇒ کلید می‌ماند؛ ۳) زدنِ دوباره همان کلید ⇒ ثبت
    price = rice13
    checkoutReplies.add { throw java.io.IOException("offline") }
    m.placeOrder()
    val key2 = seen.last().headers["Idempotency-Key"]!!
    assertNotEquals("بدنهٔ تازه (قیمتِ پذیرفته) ⇒ کلیدِ تازه", key1, key2)
    assertEquals(ShopperText.error(ir.vil3ntec.tohid.core.shopper.ShopperError(0, "network")), m.placeNote)
    assertEquals(key2, ShopperMemory.load(kv).keys["golestan"]?.key)
    checkoutReplies.add { ShopperResponse(201, order("new", 31000, true)) }
    m.placeOrder()
    assertEquals("همان بدنه ⇒ همان کلید (سفارشِ دوم ساخته نمی‌شود)", key2, seen.last().headers["Idempotency-Key"])
    assertEquals(Route.Placed("golestan", "v2.tok"), m.route)
    val after = ShopperMemory.load(kv)
    assertTrue(after.carts.isEmpty())
    assertNull(after.keys["golestan"])
    assertEquals(5L, after.orders.single().orderNo)
    assertEquals("احمد", after.name)

    //  پیگیری و لغو
    m.go(Route.Track("golestan", "v2.tok"))
    assertEquals("new", (m.order as Load.Ready).value.status)
    m.cancelOrder()
    assertEquals(ShopperText.cancelled, m.orderNote)
    assertTrue(seen.any { it.path.endsWith("/orders/v2.tok/cancel") && it.method == "POST" })

    //  برگشت و زبان
    assertTrue(m.back())
    m.home()
    assertFalse(m.canGoBack)
    m.setLanguage(ShopperLang.EN)
    assertEquals("en", ShopperMemory.load(kv).lang)
    assertEquals("Back", ShopperText.back)
  }

  @Test fun localPhoneShowsTheSavedNumberTheAfghanWay() {
    assertEquals("0700000002", CustomerModel.localPhone("+93700000002"))
    assertEquals("+4915112345678", CustomerModel.localPhone("+4915112345678"))
  }
}
