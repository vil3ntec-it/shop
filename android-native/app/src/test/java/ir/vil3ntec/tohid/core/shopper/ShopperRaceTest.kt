package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.TestHttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** پاسخ‌های دیررس، شرایطِ عوض‌شدهٔ دکان و پیگیریِ زنده — جایی که ترتیبِ رسیدن مهم است */
class ShopperRaceTest {

  private lateinit var server: TestHttpServer
  private val clock = Clock()
  private val storage = MemoryStorage()
  private val tickets = ShopperTickets(storage, clock.now)
  private val carts = ShopperCarts(storage, clock.now)
  private val book = ShopperOrderBook(storage)

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun api() = ShopperApi(clientOf(server), { tickets.current() }, clock.now)

  /** درخواستی که تا `release` در سرور می‌ماند؛ `arrived` یعنی رسید */
  private class Gate {
    val arrived = CountDownLatch(1)
    private val open = CountDownLatch(1)
    fun hold() { arrived.countDown(); open.await(5, TimeUnit.SECONDS) }
    fun release() = open.countDown()
    fun awaitArrival() = assertTrue(arrived.await(5, TimeUnit.SECONDS))
  }

  @Test
  fun `هزینهٔ ارسال میانِ ثبت عوض شد — شرایط تازه، کلیدِ تازه، ثبت؛ نه ۴۰۹ ِ بی‌پایان`() = runBlocking {
    var fee = 5000L
    server.at("/store/nan") { ok(Fixtures.profile().replace("\"deliveryFeeMinor\":5000", "\"deliveryFeeMinor\":$fee")) }
    server.at("/store/nan/products") { ok("""{"items":[${Fixtures.product("p1", 20000)}],"nextCursor":null}""") }
    val keys = mutableListOf<String>()
    server.at("/store/nan/checkout") { r ->
      keys += r.headers["idempotency-key"].orEmpty()
      val expected = Regex("\"expectedTotalMinor\":(\\d+)").find(r.body)?.groupValues?.get(1)?.toLong()
      if (expected != 20000 + fee) {
        err(409, "price_changed", "قیمت‌ها عوض شده است",
          """"quote":{"lines":[{"productId":"p1","name":"کالا p1","qtyMilli":1000,"unitPriceMinor":20000,"lineTotalMinor":20000}],"subtotalMinor":20000,"deliveryFeeMinor":$fee,"totalMinor":${20000 + fee}}""")
      } else ok("""{"order":${Fixtures.order()}}""")
    }
    tickets.save(PhoneTicket("+93700000001", "v2.t", clock.at + 60_000))
    carts.add(CartShop("nan", "نانوایی"), StoreProduct(id = "p1", name = "کالا p1", priceMinor = 20000))
    val m = ShopperCheckoutModel(api(), carts, tickets, CheckoutKeys(storage), book, storage, "nan")
    m.load()
    m.edit { it.copy(name = "احمد", addressText = "کارته سه") }
    assertEquals(25000L, m.state.value.totals!!.totalMinor)

    fee = 7000   // دکان هزینه را بالا برد؛ قیمتِ کالا همان است
    assertNull(m.submit())
    assertTrue(m.state.value.issue is CheckoutIssue.PriceChanged)
    //  شرایط همان دم تازه شد و جمعِ تازه دیده می‌شود
    assertFalse(m.state.value.termsStale)
    assertEquals(27000L, m.state.value.totals!!.totalMinor)
    assertNotNull(m.submit())
    assertEquals(2, keys.size)
    assertEquals(2, keys.distinct().size)
  }

  @Test
  fun `شرایطِ کهنه و سرورِ در دسترس‌نبوده — ثبت بسته می‌ماند تا تازه شود`() = runBlocking {
    var up = true
    server.at("/store/nan") { if (up) ok(Fixtures.profile()) else err(429, "rate_limited", "بعداً") }
    server.at("/store/nan/products") { ok("""{"items":[${Fixtures.product("p1", 20000)}],"nextCursor":null}""") }
    var hits = 0
    server.at("/store/nan/checkout") {
      hits++
      err(400, "below_min_order", "کم است", """"minOrderMinor":30000,"quote":{"lines":[],"subtotalMinor":20000,"deliveryFeeMinor":5000,"totalMinor":25000}""")
    }
    tickets.save(PhoneTicket("+93700000001", "v2.t", clock.at + 60_000))
    carts.add(CartShop("nan", "نانوایی"), StoreProduct(id = "p1", name = "نان", priceMinor = 20000))
    val m = ShopperCheckoutModel(api(), carts, tickets, CheckoutKeys(storage), book, storage, "nan")
    m.load()
    m.edit { it.copy(name = "احمد", addressText = "کارته سه") }
    up = false
    assertNull(m.submit())
    assertTrue(m.state.value.termsStale)
    assertFalse(m.state.value.canSubmit)
    //  دوباره زدن بی تازه شدن چیزی نمی‌فرستد
    assertNull(m.submit())
    assertEquals(1, hits)
  }

  @Test
  fun `۴۰۳ ِ «شماره فرق دارد» بلیت را نگه می‌دارد`() = runBlocking {
    server.at("/store/nan") { ok(Fixtures.profile()) }
    server.at("/store/nan/products") { ok("""{"items":[${Fixtures.product("p1", 20000)}],"nextCursor":null}""") }
    server.at("/store/nan/checkout") { err(403, "phone_not_verified", "این شماره تأیید نشده است") }
    tickets.save(PhoneTicket("+93700000001", "v2.t", clock.at + 60_000))
    carts.add(CartShop("nan", "نانوایی"), StoreProduct(id = "p1", name = "نان", priceMinor = 20000))
    val m = ShopperCheckoutModel(api(), carts, tickets, CheckoutKeys(storage), book, storage, "nan")
    m.load()
    m.edit { it.copy(name = "احمد", addressText = "کارته سه") }
    assertNull(m.submit())
    assertEquals(CheckoutIssue.PhoneMismatch, m.state.value.issue)
    assertNotNull(tickets.current())
  }

  @Test
  fun `پرسشِ پیش از لغو، «لغو شد» را برنمی‌گرداند`() = runBlocking {
    var status = "new"
    var updated = 2000L
    val gate = Gate()
    var holdNext = false
    server.at("/store/nan/orders/${Fixtures.TOKEN}") {
      val snapshot = Fixtures.order(status, updatedAt = updated)
      if (holdNext) { holdNext = false; gate.hold() }
      ok("""{"order":$snapshot}""")
    }
    server.at("/store/nan/orders/${Fixtures.TOKEN}/cancel") {
      status = "cancelled_by_customer"; updated = 3000
      ok("""{"order":${Fixtures.order(status, updatedAt = updated)}}""")
    }
    book.remember("nan", "نانوایی", PublicOrder(token = Fixtures.TOKEN, orderNo = 17, status = "new"))
    val t = ShopperTrackModel(api(), book, "nan", Fixtures.TOKEN)
    t.load()
    holdNext = true
    val poll = launch(Dispatchers.Default) { t.load() }
    gate.awaitArrival()
    assertTrue(t.cancel())
    gate.release()
    poll.join()
    assertEquals("cancelled_by_customer", t.state.value.order!!.status)
    assertFalse(t.state.value.order!!.canCancel)
    assertEquals("cancelled_by_customer", book.state.value.single().status)
  }

  @Test
  fun `«بیشتر» ِ نیمه‌کاره پس از عوض شدنِ دسته گیر نمی‌کند`() = runBlocking {
    val gate = Gate()
    var morePages = 0
    server.at("/market") { ok("""{"shopCount":3,"categories":[]}""") }
    server.at("/market/shops") { r ->
      if (r.param("offset") != null) {
        morePages++
        if (morePages == 1) gate.hold()
        ok("""{"shops":[${Fixtures.card("z")}],"total":3,"nextOffset":null}""")
      } else ok("""{"shops":[${Fixtures.card("a")}],"total":3,"nextOffset":24}""")
    }
    val home = ShopperHomeModel(api(), ShopperFavourites(storage, clock.now))
    home.load()
    val more = launch(Dispatchers.Default) { home.loadMore() }
    gate.awaitArrival()
    assertTrue(home.state.value.loadingMore)
    home.selectCategory("bakery")
    gate.release()
    more.join()
    //  صفحهٔ دیررس مالِ فهرستِ قبلی بود: نه آمد، نه پرچم را گیر انداخت
    assertFalse(home.state.value.loadingMore)
    assertEquals(listOf("a"), home.state.value.shops.map { it.slug })
    home.loadMore()
    assertEquals(2, morePages)
    assertEquals(listOf("a", "z"), home.state.value.shops.map { it.slug })
  }

  @Test
  fun `«بیشتر» ِ کاتالوگِ دکان پس از جست‌وجو گیر نمی‌کند`() = runBlocking {
    val gate = Gate()
    var morePages = 0
    server.at("/store/nan") { ok(Fixtures.profile()) }
    server.at("/store/nan/products") { r ->
      if (r.param("cursor") != null) {
        morePages++
        if (morePages == 1) gate.hold()
        ok("""{"items":[${Fixtures.product("p9", 100)}],"nextCursor":null}""")
      } else ok("""{"items":[${Fixtures.product("p1", 100)}],"nextCursor":"c1"}""")
    }
    val shop = ShopperShopModel(api(), "nan")
    shop.load()
    val more = launch(Dispatchers.Default) { shop.loadMore() }
    gate.awaitArrival()
    shop.search("نان")
    gate.release()
    more.join()
    assertFalse(shop.state.value.loadingMore)
    shop.loadMore()
    assertEquals(2, morePages)
  }

  @Test
  fun `پینِ دیررس پرچمِ «یافتنِ ناحیه» را گیر نمی‌اندازد`() = runBlocking {
    val gate = Gate()
    server.at("/store/nan") {
      ok(Fixtures.profile("""{"zoned":true,"zones":[{"id":"z1","name":"مرکز","feeMinor":3000,"minOrderMinor":0,"freeOverMinor":null}],"allowAsap":true,"scheduling":{"delivery":false,"pickup":false}}"""))
    }
    server.at("/store/nan/products") { ok("""{"items":[],"nextCursor":null}""") }
    server.at("/store/nan/delivery") {
      gate.hold()
      ok("""{"zoned":true,"inZone":true,"zone":{"id":"z1","name":"مرکز","feeMinor":3000,"minOrderMinor":0,"freeOverMinor":null},"pickup":true}""")
    }
    val m = ShopperCheckoutModel(api(), carts, tickets, CheckoutKeys(storage), book, storage, "nan")
    m.load()
    val pin = launch(Dispatchers.Default) { m.setPin(GeoPoint(34.5, 69.2)) }
    gate.awaitArrival()
    assertTrue(m.state.value.locating)
    m.setPin(null)
    gate.release()
    pin.join()
    assertFalse(m.state.value.locating)
    assertNull(m.state.value.located)
  }

  @Test
  fun `پیگیریِ زنده هر ۲۰ ثانیه، تا تحویل؛ بعد می‌ایستد`() = runBlocking {
    var status = "new"
    var hits = 0
    server.at("/store/nan/orders/${Fixtures.TOKEN}") { hits++; ok("""{"order":${Fixtures.order(status, updatedAt = 2000L + hits)}}""") }
    val waits = mutableListOf<Long>()
    val t = ShopperTrackModel(api(), book, "nan", Fixtures.TOKEN)
    t.follow { ms ->
      waits += ms
      clock.at += ms
      if (waits.size == 2) status = "delivered"
    }
    assertEquals(listOf(ShopperOrders.POLL_MS, ShopperOrders.POLL_MS), waits)
    assertEquals(3, hits)
    assertFalse(t.state.value.live)
  }

  @Test
  fun `پیگیریِ زنده با رفتنِ صفحه می‌ایستد`() = runBlocking {
    var hits = 0
    server.at("/store/nan/orders/${Fixtures.TOKEN}") { hits++; ok("""{"order":${Fixtures.order("picking")}}""") }
    val paused = CompletableDeferred<Unit>()
    val t = ShopperTrackModel(api(), book, "nan", Fixtures.TOKEN)
    val screen = launch { t.follow { paused.complete(Unit); awaitCancellation() } }
    paused.await()
    screen.cancel()
    screen.join()
    assertEquals(1, hits)
    assertTrue(t.state.value.live)
  }

  @Test
  fun `پیگیریِ سفارشِ ناشناخته نمی‌چرخد`() = runBlocking {
    server.at("/store/nan/orders/x") { err(404, "order_not_found") }
    val t = ShopperTrackModel(api(), book, "nan", "x")
    t.follow { error("نباید منتظر بماند") }
    assertTrue(t.state.value.notFound)
  }

  @Test
  fun `بازارچهٔ خاموش — «سفارش‌های من» از ویترینِ هر دکانِ دفتر`() = runBlocking {
    server.at("/market/orders") { err(404, "market_not_found", "بازارچه هنوز باز نیست") }
    server.at("/store/nan/orders") {
      ok("""{"orders":[{"orderNo":4,"status":"accepted","totalMinor":900,"itemsCount":1,"createdAt":50,"token":"t4"}]}""")
    }
    server.at("/store/gone/orders") { err(404, "store_not_found") }
    book.remember("nan", "نانوایی", PublicOrder(token = "t1", orderNo = 1, createdAt = 10))
    book.remember("gone", "بسته", PublicOrder(token = "t2", orderNo = 2, createdAt = 20))
    tickets.save(PhoneTicket("+93700000001", "v2.t", clock.at + 60_000))
    val m = ShopperOrdersModel(api(), tickets, book)
    m.load()
    val s = m.state.value
    assertTrue(s.verified)
    assertNull(s.failure)
    assertEquals(listOf("t4", "t2", "t1"), s.orders.map { it.token })
    assertEquals("نانوایی", s.orders.first().storeTitle)
  }
}
