package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.CheckoutRules
import ir.vil3ntec.tohid.core.shopper.RecentProducts
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
import ir.vil3ntec.tohid.customer.ui.clearRecent
import ir.vil3ntec.tohid.customer.ui.recentItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  برنامهٔ خرید روی فرستندهٔ جعلی: «دیده‌شده‌های اخیر» (WEB-13، پرچمِ `recent` ِ دکان) و «با کدام اسکناس…» در ثبت
 *  (CHK-14، `features.deliveryPrefs`) — هر دو فقط با پرچمِ روشن؛ خاموش ⇒ همان رفتار و همان بدنهٔ پیشین.
 */
class CustomerRecentTenderTest {

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

  private fun store(extra: String) = """{"store":{"slug":"golestan","title":"گلستان","contactPhone":"0700000001",
    "status":{"badge":"open_online","connection":"confirmed","orderAcceptance":"accepting"},
    "fulfillment":{"delivery":true,"pickup":true},"deliveryFeeMinor":5000,"minOrderMinor":0,"categories":[],
    "delivery":{"zoned":false,"zones":[],"allowAsap":true,"scheduling":{"delivery":false,"pickup":false}}},
    "verification":{"available":true},"market":{"available":false}$extra}"""
  private val rice = """{"id":"rice","name":"برنج","unit":"کیلو","priceMinor":12050,"maxQtyMilli":3000,"photo":false}"""
  private val order = """{"order":{"token":"v2.tok","orderNo":5,"status":"new","fulfillment":"delivery","totalMinor":17100,
    "createdAt":10,"updatedAt":20,"canCancel":true,"timeline":[{"status":"new","at":10}]}}"""

  private val seen = mutableListOf<ShopperRequest>()
  private var storeJson = store("")
  /** پاسخِ `?ids=` (کالایی که دیگر در ویترین نیست ⇒ خالی) */
  private var byIds = """{"items":[$rice]}"""

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    when {
      p == "/api/store/golestan" -> ShopperResponse(200, storeJson)
      p.startsWith("/api/store/golestan/products?ids=") -> ShopperResponse(200, byIds)
      p == "/api/store/golestan/products/rice" -> ShopperResponse(200, """{"product":$rice}""")
      p.startsWith("/api/store/golestan/products") -> ShopperResponse(200, """{"items":[$rice],"nextCursor":null}""")
      p.endsWith("/verify/start") -> ShopperResponse(200, """{"sent":true,"expiresAt":999}""")
      p.endsWith("/verify/check") -> ShopperResponse(200, """{"phone":"+93700000002","ticket":"v2.ticket","expiresAt":9999999}""")
      p.endsWith("/checkout") -> ShopperResponse(201, order)
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private val kv = MapKv()

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, kv, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { 1_000L }, debounceMs = 0,
  )

  private fun idsCalls() = seen.count { it.path.startsWith("/api/store/golestan/products?ids=") }

  @Test fun recentlyViewedStaysOnThisDeviceWithTodaysProductsOnlyWhenTheShopHasIt() {
    storeJson = store(""","recent":true""")
    val m = model()
    assertTrue(m.openAddress("golestan"))
    assertEquals(0, idsCalls())
    assertTrue(m.recentItems("golestan").isEmpty())
    m.go(Route.Product("golestan", "rice"))
    assertEquals(listOf("rice"), RecentProducts.ids(ShopperMemory.load(kv), "golestan", 1_000L))
    assertTrue(m.back())
    assertEquals(listOf("rice"), m.recentItems("golestan").map { it.id })
    assertTrue("فهرست هرگز به سرور نمی‌رود", seen.none { it.body?.contains("recent") == true || it.path.contains("recent") })

    //  بازگشایی: یک `?ids=` برای قیمت و «هنوز در ویترین» ِ امروز؛ کالای نیامده از فهرست بیرون
    val again = model()
    assertTrue(again.openAddress("golestan"))
    assertEquals(1, idsCalls())
    assertEquals(listOf("rice"), again.recentItems("golestan").map { it.id })
    byIds = """{"items":[]}"""
    val gone = model()
    assertTrue(gone.openAddress("golestan"))
    assertEquals(2, idsCalls())
    assertTrue(gone.recentItems("golestan").isEmpty())
    assertTrue(RecentProducts.ids(ShopperMemory.load(kv), "golestan", 1_000L).isEmpty())

    //  «پاک کردن»
    byIds = """{"items":[$rice]}"""
    gone.go(Route.Product("golestan", "rice"))
    assertEquals(listOf("rice"), RecentProducts.ids(ShopperMemory.load(kv), "golestan", 1_000L))
    gone.clearRecent("golestan")
    assertTrue(ShopperMemory.load(kv).seen.isEmpty())
  }

  @Test fun recentFlagOffNeitherRecordsNorShows() {
    val m = model()
    assertTrue(m.openAddress("golestan"))
    m.go(Route.Product("golestan", "rice"))
    assertTrue(m.back())
    assertTrue(ShopperMemory.load(kv).seen.isEmpty())
    assertTrue(m.recentItems("golestan").isEmpty())
    assertEquals(0, idsCalls())
  }

  /** سبد ⇐ ثبت ⇐ تأییدِ شماره؛ پس از آن «ثبت» باز است */
  private fun readyToPlace(m: CustomerModel) {
    m.go(Route.CartPage("golestan"))
    m.go(Route.Checkout("golestan"))
    m.updateForm(m.form.copy(name = "احمد", phone = "0700000002", addressText = "کارتهٔ سه"))
    m.sendCode()
    m.code = "123456"
    m.checkCode()
    assertTrue(m.problems().isEmpty())
  }

  @Test fun cashTenderGoesWithTheOrderOnlyWithTheFlagAndIsResetAfterPlacing() {
    storeJson = store(""","features":{"deliveryPrefs":true}""")
    val m = model()
    assertTrue(m.openAddress("golestan"))
    m.add("golestan", m.items.single())
    readyToPlace(m)
    val page = m.store.valueOrNull()!!
    val cart = m.cartOf("golestan")
    //  ۱۲۱ + ۵۰ = ۱۷۱ افغانی ⇒ ۲۰۰، ۵۰۰ یا ۱۰۰۰ (پول خرد < ۱۰۰۰)
    assertEquals(listOf(20_000L, 50_000L, 100_000L), CheckoutRules.tenders(page, cart, m.form, null))
    m.updateForm(m.form.copy(cashTenderMinor = 50_000))
    m.placeOrder()
    val body = seen.last { it.path.endsWith("/checkout") }.body!!
    assertTrue(body, body.contains("\"cashTenderMinor\":50000"))
    assertEquals(Route.Placed("golestan", "v2.tok"), m.route)
    assertNull("سفارشِ بعدی با اسکناسِ این سفارش نمی‌رود", m.form.cashTenderMinor)
  }

  @Test fun cashTenderFlagOffKeepsTheOldBody() {
    val m = model()
    assertTrue(m.openAddress("golestan"))
    m.add("golestan", m.items.single())
    readyToPlace(m)
    val page = m.store.valueOrNull()!!
    assertFalse(CheckoutRules.tenderShown(page, m.cartOf("golestan"), m.form, null))
    m.updateForm(m.form.copy(cashTenderMinor = 50_000))
    m.placeOrder()
    val body = seen.last { it.path.endsWith("/checkout") }.body!!
    assertFalse(body, body.contains("cashTenderMinor"))
  }
}
