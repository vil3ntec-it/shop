package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ORDER_JSON
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.STORE_JSON
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.fail
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ok
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.page
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.shop
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** مرحلهٔ ۲: درهای تازهٔ کارخواه (همان درهای امروزِ سرور)، تخفیف، نظر، پیوندِ عمیق و سفارشِ تکرارشونده. */
class ShopperStage2Test {

  private fun <T> ShopperResult<T>.ok(): T = assertIs<ShopperResult.Ok<T>>(this).value

  private fun body(r: ShopperRequest) = Json.parseToJsonElement(r.body!!).jsonObject

  @Test fun storeFeaturesTrackExtrasAndFeedbackEndpoints() {
    val http = FakeHttp { r ->
      when {
        r.path == "/api/store/golestan" -> ok(STORE_JSON)
        r.path.endsWith("/review") -> ok("""{"review":{"stars":4,"comment":"خوب","status":"pending","createdAt":5},"replayed":false}""", 201)
        r.path.endsWith("/rating") && r.method == "GET" -> ok("""{"canRate":true,"rating":null,"tags":["on_time","polite","late"]}""")
        r.path.endsWith("/rating") -> ok("""{"rating":{"rater":"customer","stars":5,"tags":["on_time"],"createdAt":9},"replayed":true}""")
        r.path.endsWith("/reviews") -> ok("""{"count":3,"avgTenths":43,"reviews":[{"stars":5,"comment":"عالی","at":1}]}""")
        else -> ok("""{"order":${ORDER_JSON.dropLast(1)},"review":{"canReview":true,"commentMax":500,"review":null},"chat":true}}""")
      }
    }
    val api = ShopperApi(http)
    val page = api.store("golestan").ok()
    assertTrue(page.features.chat)
    assertTrue(!page.features.reviews, "کلیدِ نیامده ⇒ خاموش")
    val t = api.trackPage("golestan", "v1.abc").ok()
    assertEquals(12, t.order.orderNo)
    assertTrue(t.extras.chat)
    assertEquals(true, t.extras.review?.canReview)
    val rv = api.review("golestan", "v1.abc", 4, " خوب‮ ").ok()
    assertEquals(4, rv.review.stars)
    val sent = body(http.seen.last())
    assertEquals("خوب", sent["comment"]!!.jsonPrimitive.content, "همان پاک‌سازیِ سرور (نویسهٔ جهت ⇒ فاصله، بی فاصلهٔ دو سر)")
    assertEquals(listOf("on_time", "polite", "late"), api.rating("golestan", "v1.abc").ok().tags)
    assertTrue(api.rate("golestan", "v1.abc", 5, listOf("on_time")).ok().replayed)
    assertEquals(listOf("on_time"), body(http.seen.last())["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
    val shopReviews = api.shopReviews("golestan").ok()
    assertEquals(4 to 3, ReviewRules.average(shopReviews.avgTenths!!))
  }

  @Test fun reviewAndRatingRules() {
    assertTrue(ReviewRules.validStars(1) && ReviewRules.validStars(5) && !ReviewRules.validStars(0) && !ReviewRules.validStars(6))
    assertEquals(500, ReviewRules.cleanComment("ن".repeat(800)).length)
    val emoji = "😀".repeat(600)
    assertEquals(1000, ReviewRules.cleanComment(emoji).length, "سقف به نویسهٔ یونیکد (همان `[...comment].length` ِ سرور)")
    assertEquals("خط\nدوم", ReviewRules.cleanComment("خط\nدوم"))
    val allowed = listOf("on_time", "polite", "careful", "late", "rude", "damaged")
    assertEquals(listOf("on_time", "late"), ReviewRules.cleanTags(setOf("late", "hacked", "on_time"), allowed), "ترتیبِ فهرستِ سرور، بی برچسبِ ناشناخته")
    var picked = emptyList<String>()
    for (tag in allowed) picked = ReviewRules.toggleTag(picked, tag, allowed)
    assertEquals(4, picked.size, "حداکثر چهار")
    assertEquals(3, ReviewRules.toggleTag(picked, "on_time", allowed).size)
  }

  @Test fun promoQuoteGoesIntoTheExpectedTotalAndRejectedCodeNeverRides() {
    val http = FakeHttp { r ->
      when {
        r.path.endsWith("/promos") -> ok("""{"promos":[{"id":"p1","name":"هفتهٔ برنج","kind":"percent","endsOn":"2026-11-10"}],"codes":true,"serverTime":7,"guard":true}""")
        else -> ok("""{"subtotalMinor":80000,"discountMinor":8000,"totalMinor":72000,"applied":[{"name":"VIP","discountMinor":8000}],"code":{"code":"VIP10","status":"applied"},"unavailable":[]}""")
      }
    }
    val api = ShopperApi(http)
    val list = api.promos("golestan").ok()
    assertTrue(list.codes && list.guard)
    val c = Cart("golestan", listOf(line("rice", 25000, 2000), line("oil", 30000, 1000), line("gone", 100, 1000).copy(unavailable = true)))
    val q = api.promoQuote("golestan", PromoRules.lines(c), PromoRules.cleanCode(" vip 10 "), "abcdef0123456789", "v2.ticket").ok()
    val req = http.seen.last()
    assertEquals("v2.ticket", req.headers["X-Store-Ticket"])
    val b = body(req)
    assertEquals("VIP10", b["promoCode"]!!.jsonPrimitive.content)
    assertEquals(2, b["lines"]!!.jsonArray.size, "قلمِ خط‌خورده پیش‌نمایش نمی‌شود")
    assertEquals(setOf("productId", "qtyMilli"), b["lines"]!!.jsonArray[0].jsonObject.keys, "قیمت از برنامه نمی‌رود")
    val applied = PromoRules.applied(q, "VIP10", "abcdef0123456789")!!
    val p = page(shop(min = 0))
    val form = CheckoutForm(fulfillment = "delivery", name = "احمد", phone = "0700000002", addressText = "کوچه")
    val r = CheckoutRules.request(p, c.copy(lines = c.lines.take(2)), form, "+93700000002", "fa", null, applied)
    assertEquals(80000 + 5000 - 8000, r.expectedTotalMinor, "جمعِ دیده‌شده پس از تخفیفِ سرور")
    assertEquals("VIP10", r.promoCode)
    assertEquals("abcdef0123456789", r.deviceId)
    val rejected = PromoRules.applied(q.copy(code = PromoCodeStatus("NOPE", "unknown")), "NOPE", null)!!
    assertEquals("", rejected.code, "کدِ اعمال‌نشده هرگز در بدنه نمی‌رود")
    assertNull(CheckoutRules.request(p, c, form, "+93700000002", "fa", null, rejected).promoCode)
    assertTrue(PromoRules.keyOf(c, "A", null) != PromoRules.keyOf(c, "B", null))
    assertTrue(PromoRules.keyOf(c, "A", null) != PromoRules.keyOf(c, "A", "+93700000002"), "با سد، کلید به شماره بسته است")
    //  ۴۰۹ ِ `promo_code_rejected` ⇒ کد برداشته، پیامِ همان وضعیت
    val e = ShopperError(409, ShopperError.PROMO_REJECTED, data = Json.parseToJsonElement("""{"promo":{"code":"X","status":"used_up"}}""").jsonObject)
    val after = CheckoutFlow.after(c, e, 1)
    assertEquals(CheckoutFlow.After.PromoRejected("used_up"), after)
    assertEquals(ShopperText.promoStatus("used_up"), CheckoutFlow.note(after, e))
    assertEquals(1794338999000L, PromoRules.endsAtMs("2026-11-10"), "پایانِ همان روز به وقتِ کابل")
    assertNull(PromoRules.endsAtMs("10/11/2026"))
  }

  @Test fun addressAndChatEndpointsCarryTheRightIdentity() {
    val png = byteArrayOf(0x89.toByte()) + "PNG\r\n\u001a\n".encodeToByteArray() + ByteArray(8)
    val http = FakeHttp { r ->
      when {
        r.path.endsWith("/account/addresses") && r.method == "GET" -> ok("""{"addresses":[{"id":"a1","label":"خانه","text":"","landmark":"","lat":34.5,"lng":69.1,"updatedAt":3}]}""")
        r.path.contains("/account/addresses") && r.method == "DELETE" -> ok("""{"deleted":true}""")
        r.path.contains("/account/addresses") -> ok("""{"address":{"id":"a2","label":"دفتر","text":"x","landmark":"","lat":null,"lng":null,"updatedAt":4}}""", 201)
        r.method == "POST" && r.path.endsWith("/chat/conversations") -> ok("""{"conversation":{"id":"sch_1","orderNo":12,"canSend":true,"life":{"kind":"order","state":"open"},"shop":{"name":"گلستان"}}}""", 201)
        r.path.contains("/messages?") -> ok("""{"conversation":{"id":"sch_1"},"messages":[{"id":"m1","seq":1,"rev":1,"sender":"shop","kind":"text","body":"سلام"}],"cursor":1,"hasMore":false}""")
        r.path.endsWith("/messages") -> ok("""{"message":{"id":"m2","seq":2,"rev":2,"sender":"customer","mine":true,"clientId":"c-1234567890","status":"sent"},"replayed":true}""")
        r.path.contains("/media?role=") -> ok("""{"mediaId":"scf_1","kind":"image","mime":"image/png","size":17,"role":"main","reused":false}""", 201)
        r.path.contains("/media/") -> ShopperResponse(200, "", mapOf("Content-Type" to "image/png"), png)
        else -> fail(404, "chat_disabled")
      }
    }
    val api = ShopperApi(http)
    assertEquals(GeoPin(34.5, 69.1), api.addresses("golestan", "sc1.s").ok().single().pin)
    assertEquals("sc1.s", http.seen.last().headers["X-Store-Session"], "نشانی فقط با نشستِ حساب")
    assertEquals("a2", api.addAddress("golestan", "sc1.s", AddressInput("دفتر", "x")).ok().id)
    assertEquals(setOf("label", "text", "landmark"), body(http.seen.last()).keys, "پینِ نیامده در بدنه نیست")
    api.updateAddress("golestan", "sc1.s", "a/2", AddressInput("دفتر", "x"))
    assertEquals("/api/store/golestan/account/addresses/a%2F2", http.seen.last().path)
    assertTrue(api.removeAddress("golestan", "sc1.s", "a2").ok())

    val conv = api.chatOpen("golestan", "v2.t", 12, " احمد ").ok()
    assertEquals("order", conv.life.kind)
    assertEquals("v2.t", http.seen.last().headers["X-Store-Ticket"], "هویت همان شمارهٔ تأییدشده")
    assertEquals(null, http.seen.last().headers["X-Store-Session"])
    assertEquals("12", body(http.seen.last())["orderNo"]!!.jsonPrimitive.content)
    assertEquals("احمد", body(http.seen.last())["name"]!!.jsonPrimitive.content)
    val ch = api.chatChanges("golestan", "v2.t", "sch_1", after = 0, waitMs = 20_000).ok()
    assertEquals("/api/store/golestan/chat/conversations/sch_1/messages?after=0&wait=20000", http.seen.last().path)
    assertEquals(1, ch.messages.size)
    val item = ChatOutItem("c-1234567890", "golestan", "sch_1", body = "سلام")
    assertTrue(api.chatSend("golestan", "v2.t", item).ok().replayed)
    val up = api.chatUpload("golestan", "v2.t", "sch_1", png, thumb = false).ok()
    assertEquals("scf_1", up.mediaId)
    val upReq = http.seen.last()
    assertEquals("image/png", upReq.headers["Content-Type"], "نوعِ بایت‌ها، نه JSON")
    assertContentEquals(png, upReq.bytes)
    assertNull(upReq.body)
    assertContentEquals(png, api.chatMedia("golestan", "v2.t", "sch_1", "scf_1").ok())
    assertTrue(http.seen.last().binary)
  }

  @Test fun deepLinksIntoAShopProductOrderOrCampaign() {
    val cases = mapOf(
      "https://vill3n.top/store/golestan" to ShopperLink.Shop("golestan"),
      "https://vill3n.top/store/Golestan/p/rice-1" to ShopperLink.Shop("golestan", "rice-1"),
      "https://vill3n.top/store/golestan?c=QR7#/p/oil" to ShopperLink.Shop("golestan", "oil", "QR7"),
      "https://vill3n.top/store/golestan#/o/v2.AbC_d-123" to ShopperLink.Order("golestan", "v2.AbC_d-123"),
      "https://golestan.vill3n.top/#/o/v2.AbC_d-123" to ShopperLink.Order("golestan", "v2.AbC_d-123"),
      "https://golestan.vill3n.top/" to ShopperLink.Shop("golestan"),
      "https://api.vill3n.top/store/golestan/p/ric%65" to ShopperLink.Shop("golestan", "rice"),
      "https://vill3n.top/store/golestan/p/%D8%A8%D8%B1%D9%86%D8%AC" to ShopperLink.Shop("golestan"),
      "https://vill3n.top/store/go/QR7" to ShopperLink.Campaign("QR7"),
      "tohidshop://store/golestan" to ShopperLink.Shop("golestan"),
      "tohidshop://store/golestan/o/v2.AbC_d-123" to ShopperLink.Order("golestan", "v2.AbC_d-123"),
    )
    for ((url, want) in cases) assertEquals(want, ShopperLinks.parse(url), url)
    for (bad in listOf("", "golestan", "https://evil.example/store/golestan", "https://vill3n.top/", "https://vill3n.top/market/",
      "javascript://store/golestan", "https://www.vill3n.top/", "https://a.b.vill3n.top/", "https://vill3n.top/store/-x-")) {
      assertNull(ShopperLinks.parse(bad), bad)
    }
    assertEquals("tohidshop://store/golestan/o/v2.x1234567", ShopperLinks.appLink(ShopperLink.Order("golestan", "v2.x1234567")))
    assertEquals(ShopperLink.Shop("golestan", "rice"), ShopperLinks.parse(ShopperLinks.appLink(ShopperLink.Shop("golestan", "rice"))))

    val http = FakeHttp { ok("""{"store":{"slug":"golestan","path":"/store/golestan"},"campaign":"QR7"}""") }
    assertEquals(CampaignTarget("golestan", "QR7"), ShopperApi(http).campaign("QR7").ok())
    assertEquals("/store/go/QR7", http.seen.last().path)
    assertEquals("application/json", http.seen.last().headers["Accept"], "با JSON، نه پوستهٔ HTML")
  }

  @Test fun repeatPlansAreDeviceRemindersAndRefillAtTodaysPrices() {
    val day = RepeatPlans.DAY_MS
    val o = SavedOrder("golestan", "v2.t1", 12, 27100, 0, "گلستان")
    var plans = RepeatPlans.set(emptyList(), o, 7, now = 0)
    assertEquals(7 * day, plans.single().nextAt)
    assertTrue(RepeatPlans.due(plans, 6 * day).isEmpty())
    assertEquals(listOf("v2.t1"), RepeatPlans.due(plans, 7 * day).map { it.token })
    plans = RepeatPlans.set(plans, o, 30, now = day)
    assertEquals(1, plans.size, "یکی برای هر سفارش")
    assertEquals(31 * day, plans.single().nextAt)
    plans = RepeatPlans.snooze(plans, "v2.t1", 40 * day)
    assertEquals(41 * day, plans.single().nextAt)
    plans = RepeatPlans.advance(plans, "v2.t1", 100 * day)
    assertEquals(130 * day, plans.single().nextAt, "موعدهای عقب‌افتاده پشتِ سرِ هم نمی‌آیند")
    assertEquals("new", RepeatPlans.moved(plans, "golestan", "new").single().slug)
    var many = emptyList<RepeatPlan>()
    repeat(12) { i -> many = RepeatPlans.set(many, o.copy(token = "v2.t$i"), 14, i.toLong()) }
    assertEquals(RepeatPlans.MAX, many.size)

    val reorder = ReorderPage(12, listOf(
      ReorderLine("rice", "برنج", "کیلو", 5000, previousPriceMinor = 12000, priceMinor = 12500, maxQtyMilli = 3000, available = true),
      ReorderLine("oil", "روغن", "بوتل", 1000, previousPriceMinor = 30000, priceMinor = null, available = false, reason = "out_of_stock"),
    ))
    val (cart, gone) = RepeatPlans.cartFrom(Cart("golestan", listOf(line("rice", 9, 1000), line("soap", 4000, 1000))), reorder, 77)
    assertEquals(1, gone)
    val rice = cart.lines.first { it.productId == "rice" }
    assertEquals(12500, rice.priceMinor, "قیمتِ امروزِ سرور، نه قیمتِ سفارشِ پیشین")
    assertEquals(3000, rice.qtyMilli, "سقفِ امروز")
    assertEquals(listOf("rice", "soap"), cart.lines.map { it.productId }, "قلمِ ناموجود نمی‌آید؛ سبدِ پیشین می‌ماند")
  }

  @Test fun memoryKeepsAddressesPlansAndAStableDeviceId() {
    val kv = object : ShopperKeyValue {
      val m = HashMap<String, String>()
      override fun get(key: String) = m[key]
      override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }
    var s = ShopperMemory.withDevice(ShopperState(), { ByteArray(16) { it.toByte() } })
    assertEquals("000102030405060708090a0b0c0d0e0f", s.deviceId)
    assertEquals(s, ShopperMemory.withDevice(s) { ByteArray(16) { 9 } }, "یک بار برای هر نصب")
    s = s.copy(addresses = listOf(SavedAddress("a1", "خانه", lat = 34.5, lng = 69.1)), plans = RepeatPlans.set(emptyList(), SavedOrder("old", "t", 1, 1, 0), 7, 0))
    ShopperMemory.save(kv, s)
    assertEquals(s, ShopperMemory.load(kv))
    assertEquals("new", ShopperMemory.moved(s, "old", "new").plans.single().slug)
    //  حالتِ مرحلهٔ ۱ (بی کلیدهای تازه) همان خوانده می‌شود
    kv.put(ShopperMemory.KEY, """{"lang":"ps"}""")
    assertEquals(ShopperState(lang = "ps"), ShopperMemory.load(kv))
  }
}
