package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.TestHttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** قراردادِ `/api/market/…` و `/api/store/:slug/…` از دیدِ برنامه */
class ShopperApiTest {

  private lateinit var server: TestHttpServer
  private val clock = Clock()
  private var ticket: PhoneTicket? = null

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun api() = ShopperApi(clientOf(server), { ticket }, clock.now)

  @Test
  fun `کارتِ دکان و صفحه‌بندی همان شکلِ سرور خوانده می‌شود`() = runBlocking {
    var query = ""
    server.at("/market/shops") { r ->
      query = r.query
      ok("""{"shops":[${Fixtures.card("nan", extra = ""","cover":"p9","isNew":true""")}],"total":30,"nextOffset":24}""")
    }
    val page = api().shops(category = "bakery", first = listOf("nan", "gul")).valueOrNull()!!
    assertEquals(30, page.total)
    assertEquals(24, page.nextOffset)
    val c = page.shops.single()
    assertEquals("nan", c.slug)
    assertEquals("p9", c.cover)
    assertTrue(c.isNew)
    assertTrue(c.status.isOpen)
    assertEquals(listOf(listOf(480, 1260)), c.status.today)
    assertEquals(5000L, c.deliveryFeeMinor)
    assertTrue(c.fulfillment.delivery && c.fulfillment.pickup)
    assertTrue("category=bakery" in query)
    assertTrue("first=nan%2Cgul" in query)
  }

  @Test
  fun `کارتِ سرورِ قدیمی بی جلد و بی «تازه» هم خوانده می‌شود`() = runBlocking {
    server.at("/market/shops") { ok("""{"shops":[${Fixtures.card("nan")}],"total":1,"nextOffset":null}""") }
    val c = api().shops().valueOrNull()!!.shops.single()
    assertNull(c.cover)
    assertFalse(c.isNew)
  }

  @Test
  fun `خانهٔ «اول کالا» روی سرورِ قدیمی null است، نه خطا`() = runBlocking {
    //  مسیر نیست ⇒ همان ۴۰۴ ِ `not_found` ِ سرور
    val r = api().feed()
    assertTrue(r is ApiResult.Success)
    assertNull(r.valueOrNull())
  }

  @Test
  fun `بازارچهٔ بسته خطای خودش را نگه می‌دارد`() = runBlocking {
    server.at("/market/products") { err(404, "market_not_found", "بازارچه هنوز باز نیست") }
    val r = api().feed() as ApiResult.Failure
    assertEquals("market_not_found", r.error.code)
  }

  @Test
  fun `خانهٔ «اول کالا» با دکانِ هر کالا`() = runBlocking {
    server.at("/market/products") {
      ok("""{"items":[{"id":"p1","name":"شیر","unit":"لیتر","category":"لبنیات","priceMinor":4000,"regularPriceMinor":4500,
        "maxQtyMilli":null,"photo":true,"stock":{"status":"in_stock"},"shop":{"slug":"nan","title":"نانوایی","accent":"",
        "badge":"open_online","orderAcceptance":"accepting","distanceKm":1.5}}],"popular":[],"deals":[],"windowDays":30,
        "shops":[${Fixtures.card("nan", extra = ""","cover":null""")}],"shopTotal":1}""")
    }
    val feed = api().feed()
    val f = feed.valueOrNull()!!
    assertEquals("nan", f.items.single().shop.slug)
    assertEquals(4500L, f.items.single().regularPriceMinor)
    assertEquals(1.5, f.items.single().shop.distanceKm!!, 0.0)
    assertEquals("p1", f.items.single().asStoreProduct().id)
  }

  @Test
  fun `بلیتِ معتبر فقط با «سفارش‌های من» و ثبت می‌رود`() = runBlocking {
    val seen = mutableMapOf<String, String?>()
    server.at("/market/orders") { r -> seen["orders"] = r.headers["x-store-ticket"]; ok("""{"orders":[]}""") }
    server.at("/market") { r -> seen["home"] = r.headers["x-store-ticket"]; ok("""{"shopCount":0,"categories":[]}""") }
    ticket = PhoneTicket("+93700000001", "v2.abc", clock.at + 60_000)
    api().myOrders()
    api().overview()
    assertEquals("v2.abc", seen["orders"])
    assertNull(seen["home"])
  }

  @Test
  fun `بلیتِ منقضی فرستاده نمی‌شود`() = runBlocking {
    var sent: String? = "x"
    server.at("/market/orders") { r -> sent = r.headers["x-store-ticket"]; err(401, "verification_required") }
    ticket = PhoneTicket("+93700000001", "v2.old", clock.at - 1)
    val r = api().myOrders() as ApiResult.Failure
    assertNull(sent)
    assertTrue(r.error is ApiFailure.Unauthorized)
    assertEquals("verification_required", r.error.code)
  }

  @Test
  fun `ثبت با کلیدِ یکتایی و بلیت می‌رود و «دوباره‌پخش» خوانده می‌شود`() = runBlocking {
    var key: String? = null
    var body = ""
    server.at("/store/nan/checkout") { r ->
      key = r.headers["idempotency-key"]; body = r.body
      ok("""{"order":${Fixtures.order()},"replayed":true}""")
    }
    ticket = PhoneTicket("+93700000001", "v2.abc", clock.at + 60_000)
    val placed = api().checkout("nan", Json.parseToJsonElement("""{"fulfillment":"pickup"}""").jsonObject, "app-abcdefgh").valueOrNull()!!
    assertEquals("app-abcdefgh", key)
    assertTrue(""""fulfillment":"pickup"""" in body)
    assertTrue(placed.replayed)
    assertEquals(17L, placed.order.orderNo)
    assertEquals(8000L, placed.order.dueMinor)
  }

  @Test
  fun `خطای ثبت دادهٔ همراهش را نگه می‌دارد`() = runBlocking {
    server.at("/store/nan/checkout") {
      err(409, "price_changed", "قیمت‌ها عوض شده است", """"quote":{"lines":[],"subtotalMinor":1,"deliveryFeeMinor":0,"totalMinor":1}""")
    }
    val r = api().checkout("nan", Json.parseToJsonElement("{}").jsonObject, "app-abcdefgh") as ApiResult.Failure
    assertEquals("price_changed", r.error.code)
    assertEquals(409, r.error.status)
    assertTrue(r.error.detail!!.containsKey("quote"))
  }

  @Test
  fun `بیش از صد شناسه چند درخواست می‌شود`() = runBlocking {
    val asked = mutableListOf<Int>()
    server.at("/store/nan/products") { r ->
      val ids = r.param("ids")!!.split(',')
      asked += ids.size
      ok("""{"items":[${ids.joinToString(",") { Fixtures.product(it, 100) }}],"nextCursor":null}""")
    }
    val ids = (1..150).map { "p$it" }
    val items = api().productsByIds("nan", ids).valueOrNull()!!
    assertEquals(listOf(100, 50), asked)
    assertEquals(150, items.size)
  }

  @Test
  fun `پروفایلِ ویترین با ناحیه و بازه`() = runBlocking {
    server.at("/store/nan") {
      ok(Fixtures.profile("""{"zoned":true,"zones":[{"id":"z1","name":"مرکز","feeMinor":3000,"minOrderMinor":0,"freeOverMinor":50000}],
        "allowAsap":false,"scheduling":{"delivery":true,"pickup":false}}"""))
    }
    val home = api().store("nan").valueOrNull()!!
    assertTrue(home.store.delivery.zoned)
    assertEquals(50000L, home.store.delivery.zones.single().freeOverMinor)
    assertFalse(home.store.delivery.allowAsap)
    assertTrue(home.store.delivery.scheduling.of(ShopperCheckout.DELIVERY))
    assertFalse(home.store.delivery.scheduling.of(ShopperCheckout.PICKUP))
    assertEquals(listOf("cod"), home.store.payment.methods)
  }

  @Test
  fun `تأییدِ شماره از راهِ ویترین یا بازارچه`() = runBlocking {
    val bodies = mutableListOf<String>()
    server.at("/store/nan/verify/start") { r -> bodies += r.body; ok("""{"sent":true,"expiresAt":9,"resendSeconds":60,"devCode":"123456"}""") }
    server.at("/market/verify/check") { r -> bodies += r.body; ok("""{"phone":"+93700000001","ticket":"v2.t","expiresAt":99}""") }
    val sent = api().startVerification("+93700000001", "nan").valueOrNull()!!
    assertEquals(60, sent.resendSeconds)
    val t = api().checkVerification("+93700000001", "123456").valueOrNull()!!
    assertEquals("v2.t", t.ticket)
    assertTrue(""""code":"123456"""" in bodies[1])
  }
}
