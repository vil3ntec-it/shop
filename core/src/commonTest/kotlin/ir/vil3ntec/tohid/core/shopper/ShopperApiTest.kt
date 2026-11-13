package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.fail
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ok
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** کارخواهِ برنامهٔ خرید روی فرستندهٔ جعلی: مسیر، سرآیند، بدنه و خواندنِ هر پاسخ و خطا. */
class ShopperApiTest {

  private fun api(reply: (ShopperRequest) -> ShopperResponse): Pair<ShopperApi, FakeHttp> {
    val http = FakeHttp(reply)
    return ShopperApi(http) { "ps" } to http
  }

  @Test fun storePageReadsShopZonesAndFlagsAndIgnoresUnknownKeys() {
    val (a, http) = api { ok(ShopperSamples.STORE_JSON) }
    val page = a.store("golestan").valueOrNull()
    assertNotNull(page)
    assertEquals("سوپرمارکت گلستان", page.shop.title)
    assertEquals(listOf("zone_a", "zone_b"), page.delivery.zones.map { it.id })
    assertNull(page.delivery.zones[1].freeOverMinor)
    assertTrue(page.delivery.zoned)
    assertTrue(page.verificationAvailable)
    assertFalse(page.marketAvailable)
    val r = http.seen.single()
    assertEquals("GET", r.method)
    assertEquals("/api/store/golestan", r.path)
    assertEquals("ps", r.headers["Accept-Language"])
    assertNull(r.body)
    assertNull(r.headers["Content-Type"], "GET بی بدنه ⇒ بی Content-Type")
  }

  @Test fun recentAndDeliveryPrefsAreReadOnlyWhenTheServerSendsThem() {
    //  پرچمِ خاموش ⇒ کلید نیست ⇒ هر دو false (WEB-13 `recent` ِ ریشه، CHK-14 `features.deliveryPrefs`)
    val off = api { ok(ShopperSamples.STORE_JSON) }.first.store("golestan").valueOrNull()
    assertNotNull(off)
    assertFalse(off.recent)
    assertFalse(off.features.deliveryPrefs)
    val json = ShopperSamples.STORE_JSON.replace(
      "\"features\": {\"chat\": true}", "\"recent\": true, \"features\": {\"chat\": true, \"deliveryPrefs\": true}",
    )
    assertTrue(json != ShopperSamples.STORE_JSON)
    val on = api { ok(json) }.first.store("golestan").valueOrNull()
    assertNotNull(on)
    assertTrue(on.recent)
    assertTrue(on.features.deliveryPrefs)
    assertTrue(on.features.chat)
  }

  @Test fun catalogQueryIsEncodedTrimmedAndCapped() {
    val (a, http) = api { ok("""{"items":[{"id":"oil","name":"روغن","priceMinor":30000}],"nextCursor":"c2"}""") }
    val long = "  روغنِ آفتاب‌گردان ${"x".repeat(80)} "
    val page = a.products("golestan", q = long, category = "خوراکی & شوینده", cursor = "c1").valueOrNull()
    assertEquals(listOf("oil"), page?.items?.map { it.id })
    assertEquals("c2", page?.nextCursor)
    val path = http.seen.single().path
    assertTrue(path.startsWith("/api/store/golestan/products?q="), path)
    assertTrue("category=%D8%AE%D9%88%D8%B1%D8%A7%DA%A9%DB%8C%20%26%20" in path, "& و فاصله کدگذاری شده‌اند")
    assertTrue("&cursor=c1&limit=30" in path, path)
    val q = path.substringAfter("q=").substringBefore("&")
    assertEquals(ShopperUrl.encode(long.trim().take(60)), q, "همان سقفِ ۶۰ نویسهٔ سرور")
  }

  @Test fun emptyIdsListNeverCallsTheServer() {
    val (a, http) = api { error("نباید زده شود") }
    assertEquals(0, a.productsByIds("golestan", emptyList()).valueOrNull()?.items?.size)
    assertTrue(http.seen.isEmpty())
  }

  @Test fun idsAreDedupedAndCappedAtOneHundred() {
    val (a, http) = api { ok("""{"items":[]}""") }
    a.productsByIds("golestan", List(150) { "p$it" } + "p1")
    val ids = http.seen.single().path.substringAfter("ids=").split("%2C")
    assertEquals(100, ids.size)
    assertEquals("p0", ids.first())
  }

  @Test fun checkoutSendsIdempotencyKeyTicketAndWebShapedBody() {
    val (a, http) = api { ok("""{"order":${ShopperSamples.ORDER_JSON}}""", status = 201) }
    val req = CheckoutRequest(
      fulfillment = "pickup", customer = CheckoutCustomer("احمد", "+93700000002"),
      lines = listOf(CheckoutLine("rice", 2000, 12050)), expectedTotalMinor = 24100, lang = "fa",
    )
    val placed = a.checkout("golestan", req, "app-01ABC", "v2.tkt").valueOrNull()
    assertNotNull(placed)
    assertFalse(placed.replayed)
    assertEquals(12, placed.order.orderNo)
    assertEquals(27100, placed.order.totalMinor)
    val r = http.seen.single()
    assertEquals("POST", r.method)
    assertEquals("/api/store/golestan/checkout", r.path)
    assertEquals("app-01ABC", r.headers["Idempotency-Key"])
    assertEquals("v2.tkt", r.headers["X-Store-Ticket"])
    assertEquals("application/json", r.headers["Content-Type"])
    val body = Json.parseToJsonElement(r.body!!).jsonObject
    assertEquals("cod", body["paymentMethod"]!!.jsonPrimitive.content)
    assertEquals(12050, body["lines"]!!.jsonArray[0].jsonObject["expectedUnitPriceMinor"]!!.jsonPrimitive.long)
    assertFalse("address" in body, "دریافت از دکان ⇒ بی نشانی (null در بدنه نمی‌رود)")
    assertFalse("slotStartsAt" in body)
  }

  @Test fun replayedCheckoutIsReportedAsTheSameOrder() {
    val (a, _) = api { ok("""{"order":${ShopperSamples.ORDER_JSON},"replayed":true}""", headers = mapOf("Idempotent-Replayed" to "true")) }
    val req = CheckoutRequest("pickup", customer = CheckoutCustomer("a", "+93700000002"), lines = emptyList(), expectedTotalMinor = 0, lang = "fa")
    assertEquals(true, a.checkout("golestan", req, "app-01ABCDEFG", null).valueOrNull()?.replayed)
  }

  @Test fun priceChangedCarriesTodaysQuote() {
    val quote = ""","quote":{"lines":[{"productId":"rice","name":"برنج","unit":"کیلو","qtyMilli":2000,"unitPriceMinor":13000,"lineTotalMinor":26000}],"subtotalMinor":26000,"deliveryFeeMinor":3000,"totalMinor":29000}"""
    val (a, _) = api { fail(409, "price_changed", quote) }
    val req = CheckoutRequest("pickup", customer = CheckoutCustomer("a", "+93700000002"), lines = emptyList(), expectedTotalMinor = 0, lang = "fa")
    val e = a.checkout("golestan", req, "app-01ABCDEFG", "t").errorOrNull()
    assertNotNull(e)
    assertEquals(409, e.status)
    assertEquals("price_changed", e.code)
    assertEquals("پیامِ سرور", e.message)
    assertEquals(29000, e.quote()?.totalMinor)
    assertEquals(13000, e.quote()?.lines?.single()?.unitPriceMinor)
  }

  @Test fun errorHelpersReadUnavailableMaxQtySlotsAndMovedSlug() {
    val (a, _) = api {
      when {
        "checkout" in it.path -> fail(409, "product_unavailable", ""","unavailable":["oil","soap"]""")
        "verify" in it.path -> fail(400, "qty_over_limit", ""","productId":"rice","maxQtyMilli":5000""")
        "slots" in it.path -> fail(409, "slot_full", ""","slots":[{"startsAt":10,"endsAt":20,"available":true}]""")
        else -> ShopperResponse(308, """{"error":{"code":"store_moved","message":"نشانی عوض شده","slug":"golestan-2"}}""")
      }
    }
    val req = CheckoutRequest("pickup", customer = CheckoutCustomer("a", "+93700000002"), lines = emptyList(), expectedTotalMinor = 0, lang = "fa")
    assertEquals(listOf("oil", "soap"), a.checkout("s", req, "app-01ABCDEFG", "t").errorOrNull()?.unavailable())
    assertEquals("rice" to 5000L, a.verifyStart("s", "0700").errorOrNull()?.maxQty())
    assertEquals(10L, a.slots("s", "delivery").errorOrNull()?.slots()?.single()?.startsAt)
    val moved = a.store("old-slug").errorOrNull()
    assertEquals("golestan-2", moved?.movedTo())
    assertEquals(308, moved?.status)
  }

  @Test fun networkFailureAndNonJsonBodiesBecomeClearErrors() {
    val (a, _) = api { throw IllegalStateException("socket closed") }
    val e = a.track("golestan", "tok").errorOrNull()
    assertEquals(0, e?.status)
    assertEquals(ShopperError.NETWORK, e?.code)
    assertTrue(e?.network == true)

    val (b, _) = api { ShopperResponse(502, "<html>bad gateway</html>") }
    assertEquals(ShopperError.BAD_RESPONSE, b.track("golestan", "tok").errorOrNull()?.code)

    val (c, _) = api { ShopperResponse(429, "", mapOf("retry-after" to " 42 ")) }
    val rate = c.market().errorOrNull()
    assertEquals("rate_limited", rate?.code)
    assertEquals(42, rate?.retryAfterSeconds)

    val (d, _) = api { ok("""{"unexpected":true}""") }
    assertEquals(ShopperError.BAD_RESPONSE, d.track("golestan", "tok").errorOrNull()?.code, "۲۰۰ بی `order` ⇒ پاسخِ نادرست، نه کرش")
  }

  @Test fun trackingTokenAndProductIdAreEncodedAsOnePathSegment() {
    val (a, http) = api { ok("""{"order":${ShopperSamples.ORDER_JSON}}""") }
    a.track("golestan", "v2.ab/c?d")
    a.cancel("golestan", "v2.x")
    a.payCash("golestan", "v2.x")
    assertEquals("/api/store/golestan/orders/v2.ab%2Fc%3Fd", http.seen[0].path)
    assertEquals("/api/store/golestan/orders/v2.x/cancel", http.seen[1].path)
    assertEquals("{}", http.seen[1].body)
    assertEquals("/api/store/golestan/orders/v2.x/pay-choice", http.seen[2].path)
    assertEquals("cash", Json.parseToJsonElement(http.seen[2].body!!).jsonObject["method"]!!.jsonPrimitive.content)
    assertEquals("/api/store/golestan/products/rice%3A1/thumb?v=abc123", a.photoPath("golestan", "rice:1", "abc123", thumb = true))
    assertEquals("/api/store/golestan/products/rice/photo", a.photoPath("golestan", "rice", null, thumb = false))
  }

  @Test fun verificationRoundTripKeepsServerEmail() {
    val (a, http) = api {
      if (it.path.endsWith("/start")) ok("""{"email":"a@b.co","sent":true,"expiresAt":99,"resendSeconds":60,"devCode":"123456"}""")
      else ok("""{"email":"a@b.co","ticket":"v3.x.y","expiresAt":555}""")
    }
    assertEquals("123456", a.verifyStart("golestan", "a@b.co").valueOrNull()?.devCode)
    val t = a.verifyCheck("golestan", "a@b.co", "123456").valueOrNull()
    assertEquals(CustomerTicket("a@b.co", "v3.x.y", 555), t)
    //  «فقط ایمیل»: بدنه فقط `email` دارد، هرگز `phone`
    for (seen in http.seen.take(2)) {
      val b = Json.parseToJsonElement(seen.body!!) as JsonObject
      assertEquals("a@b.co", b["email"]!!.jsonPrimitive.content)
      assertEquals(null, b["phone"])
    }
    val body = Json.parseToJsonElement(http.seen[1].body!!) as JsonObject
    assertEquals("123456", body["code"]!!.jsonPrimitive.content)
  }

  @Test fun marketShopsListAndOverview() {
    val (a, http) = api {
      if (it.path.startsWith("/api/market/shops")) ok(
        """{"shops":[{"slug":"golestan","title":"گلستان","category":"grocery","status":{"badge":"closed","connection":"stale","orderAcceptance":"later"},"fulfillment":{"delivery":true},"productCount":3,"isNew":true,"cover":null}],"total":1,"nextOffset":null}""",
      ) else ok("""{"shopCount":1,"categories":[{"key":"grocery","label":"سوپرمارکت","count":1}],"verification":{"available":true}}""")
    }
    assertEquals(1, a.market().valueOrNull()?.categories?.single()?.count)
    val list = a.shops(q = " نان ", category = "grocery", offset = 0).valueOrNull()
    assertEquals("closed", list?.shops?.single()?.status?.badge)
    assertNull(list?.nextOffset)
    assertEquals("/api/market/shops?q=%D9%86%D8%A7%D9%86&category=grocery&limit=20", http.seen[1].path)
  }

  @Test fun urlEncodingMatchesEncodeUriComponent() {
    assertEquals("a-b_c.d~e", ShopperUrl.encode("a-b_c.d~e"))
    assertEquals("%20%2B%26%3D%23", ShopperUrl.encode(" +&=#"))
    assertEquals("%DB%8C", ShopperUrl.encode("ی"))
    assertEquals("", ShopperUrl.query("a" to null, "b" to ""))
  }
}
