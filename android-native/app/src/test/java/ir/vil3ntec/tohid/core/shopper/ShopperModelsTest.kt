package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.TestHttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** منطقِ صفحه‌ها روی سرورِ آزمایشی — همان مسیرها و شکل‌های سرورِ واقعی */
class ShopperModelsTest {

  private lateinit var server: TestHttpServer
  private val clock = Clock()
  private val storage = MemoryStorage()
  private val tickets = ShopperTickets(storage, clock.now)
  private val carts = ShopperCarts(storage, clock.now)
  private val book = ShopperOrderBook(storage)

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun api() = ShopperApi(clientOf(server), { tickets.current() }, clock.now)

  /* ------------------------------ خانه ------------------------------ */

  @Test
  fun `خانه — دسته‌ها، دکان‌ها، قفسه و صفحهٔ بعد`() = runBlocking {
    server.at("/market") { ok("""{"shopCount":2,"categories":[{"key":"bakery","label":"نانوایی","count":2}],"verification":{"available":true}}""") }
    server.at("/market/shops") { r ->
      if (r.param("offset") == "24") ok("""{"shops":[${Fixtures.card("b")},${Fixtures.card("c")}],"total":3,"nextOffset":null}""")
      else ok("""{"shops":[${Fixtures.card("a")},${Fixtures.card("b")}],"total":3,"nextOffset":24}""")
    }
    val home = ShopperHomeModel(api(), ShopperFavourites(storage, clock.now))
    home.load()
    val s = home.state.value
    assertFalse(s.loading)
    assertEquals(listOf("a", "b"), s.shops.map { it.slug })
    assertEquals("bakery", s.categories.single().key)
    assertTrue(s.verificationAvailable)
    //  سرورِ بی «اول کالا» ⇒ فقط دکان‌ها
    assertNull(s.feed)
    home.loadMore()
    //  تکراری دوباره نمی‌آید
    assertEquals(listOf("a", "b", "c"), home.state.value.shops.map { it.slug })
    assertNull(home.state.value.nextOffset)
  }

  @Test
  fun `خانه — بازارچهٔ بسته یک وضعیت است، نه خطا`() = runBlocking {
    server.at("/market") { err(404, "market_not_found", "بازارچه هنوز باز نیست") }
    server.at("/market/shops") { err(404, "market_not_found") }
    val home = ShopperHomeModel(api(), ShopperFavourites(storage, clock.now))
    home.load()
    assertTrue(home.state.value.marketClosed)
    assertNull(home.state.value.failure)
    assertFalse(home.state.value.isEmpty)
  }

  @Test
  fun `خانه — دلخواه‌ها با first می‌روند و دسته دوباره ⇒ همه`() = runBlocking {
    val asked = mutableListOf<String>()
    server.at("/market") { ok("""{"shopCount":1,"categories":[]}""") }
    server.at("/market/shops") { r -> asked += "${r.param("category")}|${r.param("first")}"; ok("""{"shops":[],"total":0}""") }
    val favs = ShopperFavourites(storage, clock.now)
    val home = ShopperHomeModel(api(), favs)
    assertTrue(home.toggleFavourite(ShopCard(slug = "gul", title = "گل")))
    home.selectCategory("bakery")
    home.selectCategory("bakery")
    assertEquals(listOf("bakery|gul", "null|gul"), asked)
    assertEquals("", home.state.value.category)
    assertTrue(home.state.value.isEmpty)
    assertFalse(home.toggleFavourite(ShopCard(slug = "gul")))
    assertTrue(ShopperFavourites(storage, clock.now).state.value.isEmpty())
  }

  @Test
  fun `خانه — جست‌وجو از دو حرف`() = runBlocking {
    var hits = 0
    server.at("/market/search") { r -> hits++; ok("""{"q":"${r.param("q")}","category":"","shops":[],"products":[]}""") }
    val home = ShopperHomeModel(api(), ShopperFavourites(storage, clock.now))
    home.search("ن")
    assertEquals(0, hits)
    assertNull(home.state.value.search)
    home.search("نان")
    assertEquals(1, hits)
    assertEquals("نان", home.state.value.search!!.q)
    home.search("")
    assertNull(home.state.value.search)
    assertFalse(home.state.value.inSearch)
  }

  /* ------------------------------ دکان ------------------------------ */

  @Test
  fun `دکان — پروفایل، قفسه، کاتالوگ و دسته`() = runBlocking {
    server.at("/store/nan") { ok(Fixtures.profile()) }
    server.at("/store/nan/highlights") { ok("""{"bestsellers":[${Fixtures.product("b1", 100)}],"newArrivals":[],"windowDays":30}""") }
    server.at("/store/nan/products") { r ->
      val cat = r.param("category").orEmpty()
      ok("""{"items":[${Fixtures.product(if (cat.isEmpty()) "p1" else "c1", 1500)}],"nextCursor":null}""")
    }
    val shop = ShopperShopModel(api(), "nan")
    shop.load()
    val s = shop.state.value
    assertEquals("نانوایی", s.store!!.title)
    assertEquals(listOf("نان", "شیرینی"), s.store!!.categories)
    assertEquals("b1", s.highlights!!.bestsellers.single().id)
    assertEquals(listOf("p1"), s.items.map { it.id })
    assertEquals(CartShop("nan", "نانوایی", "#108B81"), s.cartShop)
    shop.selectCategory("نان")
    assertEquals(listOf("c1"), shop.state.value.items.map { it.id })
  }

  @Test
  fun `دکان — ویترینِ بسته ⇒ «پیدا نشد»`() = runBlocking {
    server.at("/store/gone") { err(404, "store_not_found", "این ویترین پیدا نشد") }
    val shop = ShopperShopModel(api(), "gone")
    shop.load()
    assertTrue(shop.state.value.notFound)
    assertNull(shop.state.value.failure)
  }

  /* ------------------------------ ثبت ------------------------------ */

  private fun checkoutModel() = ShopperCheckoutModel(api(), carts, tickets, CheckoutKeys(storage), book, storage, "nan")

  @Test
  fun `ثبت — تأیید، قیمتِ عوض‌شده، پذیرش و ثبت با همان سبد`() = runBlocking {
    server.at("/store/nan") { ok(Fixtures.profile()) }
    var price = 15000L
    server.at("/store/nan/products") { ok("""{"items":[${Fixtures.product("p1", 15000)}],"nextCursor":null}""") }
    server.at("/store/nan/verify/start") { ok("""{"sent":true,"expiresAt":1,"resendSeconds":30}""") }
    server.at("/store/nan/verify/check") { r ->
      if ("\"123456\"" in r.body) ok("""{"phone":"+93700000001","ticket":"v2.ok","expiresAt":${clock.at + 3_600_000}}""")
      else err(403, "otp_wrong", "کد درست نیست")
    }
    val keysSeen = mutableListOf<String>()
    server.at("/store/nan/checkout") { r ->
      keysSeen += r.headers["idempotency-key"].orEmpty()
      if (r.headers["x-store-ticket"] != "v2.ok") return@at err(401, "verification_required")
      val subtotal = 2 * price
      val expected = Regex("\"expectedTotalMinor\":(\\d+)").find(r.body)?.groupValues?.get(1)?.toLong()
      if (expected != subtotal + 5000) {
        price = 16000
        return@at err(409, "price_changed", "قیمت‌ها عوض شده است",
          """"quote":{"lines":[{"productId":"p1","name":"کالا p1","qtyMilli":2000,"unitPriceMinor":16000,"lineTotalMinor":32000}],"subtotalMinor":32000,"deliveryFeeMinor":5000,"totalMinor":37000}""")
      }
      ok("""{"order":${Fixtures.order()}}""")
    }
    //  سرور قیمت را میانِ دیدن و ثبت عوض می‌کند
    price = 16000
    val shop = CartShop("nan", "نانوایی")
    carts.add(shop, StoreProduct(id = "p1", name = "کالا p1", priceMinor = 15000))
    carts.increment("nan", "p1")

    val m = checkoutModel()
    m.load()
    assertEquals(ShopperCheckout.DELIVERY, m.state.value.form.fulfillment)
    m.edit { it.copy(name = "احمد", phone = "0700000001", addressText = "کارته سه") }
    assertEquals(listOf(FormProblem.PHONE_NOT_VERIFIED), m.state.value.problems)
    assertNull(m.submit())

    val v = PhoneVerification(api(), tickets, "nan", clock.now)
    assertTrue(v.sendCode("0700000001"))
    assertEquals(VerifyState.Step.CODE, v.state.value.step)
    assertFalse(v.checkCode("111111"))
    assertEquals(VerifyProblem.WRONG_CODE, v.state.value.problem)
    assertTrue(v.checkCode("۱۲۳۴۵۶"))
    m.onVerified(tickets.current()!!)
    assertTrue(m.state.value.problems.isEmpty())

    //  ۱) سرور: قیمت عوض شد ⇒ سبد تازه و ثبت نشد
    assertNull(m.submit())
    assertTrue(m.state.value.issue is CheckoutIssue.PriceChanged)
    assertEquals(listOf(PriceChange("p1", "کالا p1", 15000, 16000)), m.state.value.priceChanges)
    assertEquals(32000L + 5000L, m.state.value.totals!!.totalMinor)
    //  ۲) پذیرفت و دوباره زد ⇒ ثبت
    val placed = m.submit()
    assertNotNull(placed)
    assertEquals(17L, placed!!.order.orderNo)
    assertNull(carts.cart("nan"))
    assertEquals(Fixtures.TOKEN, book.state.value.single().token)
    //  بدنه عوض شد ⇒ کلیدِ تازه
    assertEquals(2, keysSeen.distinct().size)
    //  نام و نشانی برای دفعهٔ بعد
    assertEquals("احمد", storage.load(ShopperStorage.CONTACT, SavedContact.serializer())!!.name)
  }

  @Test
  fun `ثبت — بلیتِ باطل ⇒ پاک و دوباره تأیید`() = runBlocking {
    server.at("/store/nan") { ok(Fixtures.profile()) }
    server.at("/store/nan/products") { ok("""{"items":[${Fixtures.product("p1", 20000)}],"nextCursor":null}""") }
    server.at("/store/nan/checkout") { err(401, "verification_required", "شماره را تأیید کنید") }
    tickets.save(PhoneTicket("+93700000001", "v2.revoked", clock.at + 60_000))
    carts.add(CartShop("nan", "نانوایی"), StoreProduct(id = "p1", name = "نان", priceMinor = 20000))
    val m = checkoutModel()
    m.load()
    //  شماره از بلیت پر شد
    assertEquals("+93 70 000 0001", m.state.value.form.phone)
    m.edit { it.copy(name = "احمد", addressText = "کارته سه") }
    assertNull(m.submit())
    assertEquals(CheckoutIssue.NeedsVerification, m.state.value.issue)
    assertNull(tickets.current())
    assertTrue(FormProblem.PHONE_NOT_VERIFIED in m.state.value.problems)
  }

  @Test
  fun `ثبت — ناحیه از پین و بازه‌های «دریافت از دکان»`() = runBlocking {
    server.at("/store/nan") {
      ok(Fixtures.profile("""{"zoned":true,"zones":[{"id":"z1","name":"مرکز","feeMinor":3000,"minOrderMinor":0,"freeOverMinor":null}],
        "allowAsap":false,"scheduling":{"delivery":false,"pickup":true}}"""))
    }
    server.at("/store/nan/products") { ok("""{"items":[${Fixtures.product("p1", 20000)}],"nextCursor":null}""") }
    server.at("/store/nan/delivery") { r ->
      if (r.param("lat") == "34.5") ok("""{"zoned":true,"inZone":true,"zone":{"id":"z1","name":"مرکز","feeMinor":3000,"minOrderMinor":0,"freeOverMinor":null},"pickup":true}""")
      else ok("""{"zoned":true,"inZone":false,"zone":null,"pickup":true}""")
    }
    server.at("/store/nan/slots") { r ->
      assertEquals("pickup", r.param("fulfillment"))
      ok("""{"fulfillment":"pickup","scheduling":true,"allowAsap":false,"timezone":"Asia/Kabul","slots":[{"startsAt":100,"endsAt":200,"date":"2026-10-21","startMin":600,"endMin":660,"available":true,"few":true}]}""")
    }
    tickets.save(PhoneTicket("+93700000001", "v2.t", clock.at + 60_000))
    carts.add(CartShop("nan", "نانوایی"), StoreProduct(id = "p1", name = "نان", priceMinor = 20000))
    val m = checkoutModel()
    m.load()
    m.edit { it.copy(name = "احمد") }
    assertEquals(listOf(FormProblem.ADDRESS_REQUIRED, FormProblem.ZONE_REQUIRED), m.state.value.problems)
    m.setPin(GeoPoint(1.0, 1.0))
    assertEquals(listOf(FormProblem.OUT_OF_ZONE), m.state.value.problems)
    m.setPin(GeoPoint(34.5, 69.2))
    assertTrue(m.state.value.problems.isEmpty())
    assertEquals(3000L, m.state.value.totals!!.deliveryFeeMinor)

    m.setFulfillment(ShopperCheckout.PICKUP)
    assertEquals(listOf(FormProblem.SLOT_REQUIRED), m.state.value.problems)
    assertTrue(m.state.value.slots!!.slots.single().few)
    m.edit { it.copy(slotStartsAt = 100) }
    assertTrue(m.state.value.canSubmit)
    assertEquals(0L, m.state.value.totals!!.deliveryFeeMinor)
  }

  /* ------------------------------ سفارش‌ها ------------------------------ */

  @Test
  fun `سفارش‌های من — بی بلیت فقط همین دستگاه؛ بلیتِ باطل پاک می‌شود`() = runBlocking {
    book.remember("nan", "نانوایی", PublicOrder(token = "t1", orderNo = 1, createdAt = 10))
    server.at("/market/orders") { r ->
      if (r.headers["x-store-ticket"] == "v2.ok") ok("""{"orders":[{"orderNo":5,"status":"new","totalMinor":100,"itemsCount":1,
        "fulfillment":"pickup","createdAt":50,"token":"t5","store":{"slug":"gul","title":"گل","accent":""}}]}""")
      else err(401, "verification_required")
    }
    val m = ShopperOrdersModel(api(), tickets, book)
    m.load()
    assertFalse(m.state.value.verified)
    assertEquals(listOf("t1"), m.state.value.orders.map { it.token })

    tickets.save(PhoneTicket("+93700000001", "v2.ok", clock.at + 60_000))
    m.load()
    assertTrue(m.state.value.verified)
    assertEquals(listOf("t5", "t1"), m.state.value.orders.map { it.token })

    tickets.save(PhoneTicket("+93700000001", "v2.revoked", clock.at + 60_000))
    m.load()
    assertFalse(m.state.value.verified)
    assertNull(tickets.current())
    assertNull(m.state.value.failure)
  }

  @Test
  fun `پیگیری و لغو؛ پس از قبول «لغو نمی‌شود» و وضعیتِ تازه`() = runBlocking {
    var status = "new"
    server.at("/store/nan/orders/${Fixtures.TOKEN}") { ok("""{"order":${Fixtures.order(status)}}""") }
    server.at("/store/nan/orders/${Fixtures.TOKEN}/cancel") {
      if (status == "new") { status = "cancelled_by_customer"; ok("""{"order":${Fixtures.order(status)},"unchanged":false}""") }
      else err(409, "cannot_cancel", "سفارش قبول شده است")
    }
    val t = ShopperTrackModel(api(), book, "nan", Fixtures.TOKEN)
    t.load()
    assertTrue(t.state.value.live)
    assertTrue(t.state.value.order!!.canCancel)
    assertEquals(OrderStage.PLACED, t.state.value.steps.first().stage)

    status = "accepted"
    t.load()
    assertFalse(t.state.value.order!!.canCancel)
    assertFalse(t.cancel())

    status = "new"
    t.load()
    assertTrue(t.cancel())
    assertEquals("cancelled_by_customer", t.state.value.order!!.status)
    assertFalse(t.state.value.live)
    assertEquals(OrderStage.STOPPED, t.state.value.steps.last().stage)
  }

  @Test
  fun `پیگیری — توکنِ ناشناخته ⇒ «پیدا نشد»`() = runBlocking {
    server.at("/store/nan/orders/x") { err(404, "order_not_found", "سفارش پیدا نشد") }
    val t = ShopperTrackModel(api(), book, "nan", "x")
    t.load()
    assertTrue(t.state.value.notFound)
  }

  @Test
  fun `خریدِ دوباره سبد را با قیمتِ امروز پر می‌کند`() = runBlocking {
    server.at("/store/nan/orders/${Fixtures.TOKEN}/reorder") {
      ok("""{"orderNo":17,"lines":[{"productId":"p1","name":"نان","unit":"عدد","qtyMilli":2000,"previousPriceMinor":1500,
        "priceMinor":1700,"maxQtyMilli":null,"available":true,"reason":"","stock":{"status":"in_stock"}}]}""")
    }
    val t = ShopperTrackModel(api(), book, "nan", Fixtures.TOKEN)
    assertEquals(1, t.reorder(carts, CartShop("nan", "نانوایی")))
    assertEquals(1700L, carts.cart("nan")!!.line("p1")!!.priceMinor)
  }
}
