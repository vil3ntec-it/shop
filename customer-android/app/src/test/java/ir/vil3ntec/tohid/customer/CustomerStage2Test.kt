package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.ChatOutbox
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.RepeatPlans
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
import ir.vil3ntec.tohid.customer.ui.Locator
import ir.vil3ntec.tohid.customer.ui.Route
import ir.vil3ntec.tohid.customer.ui.duePlans
import ir.vil3ntec.tohid.customer.ui.fillFromPlan
import ir.vil3ntec.tohid.customer.ui.openLink
import ir.vil3ntec.tohid.customer.ui.setRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  مرحلهٔ ۲ ِ برنامهٔ خرید روی فرستندهٔ جعلی (بی اندروید): پین و ناحیه، نشانیِ ذخیره‌شده، کدِ تخفیفِ سرور در جمعِ
 *  دیده‌شده، نظرِ سفارش، گفت‌وگو با صفِ بی‌اینترنت (همان clientId پس از وصل شدن)، سفارشِ تکرارشونده و پیوندِ عمیق.
 */
class CustomerStage2Test {

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
    "status":{"badge":"open_online","connection":"confirmed","orderAcceptance":"accepting"},"fulfillment":{"delivery":true,"pickup":true},"deliveryFeeMinor":5000,"minOrderMinor":0,
    "categories":[],"delivery":{"zoned":true,"allowAsap":true,"scheduling":{"delivery":false,"pickup":false},
      "zones":[{"id":"zone_a","name":"کارتهٔ سه","feeMinor":3000,"minOrderMinor":0}]}},
    "verification":{"available":true},"market":{"available":false},"features":{"chat":true,"reviews":true}}"""
  private val rice = """{"id":"rice","name":"برنج","unit":"کیلو","priceMinor":12000,"photo":false}"""
  private val order = """{"order":{"token":"v2.tok12345","orderNo":5,"status":"delivered","fulfillment":"delivery","totalMinor":38000,
    "createdAt":10,"updatedAt":20,"canCancel":false,"timeline":[],"review":{"canReview":true,"commentMax":500,"review":null},"chat":true}}"""

  private val seen = mutableListOf<ShopperRequest>()
  private var chatOffline = 1
  private var now = 1_000_000L

  private val http = ShopperHttp { r ->
    seen += r
    val p = r.path
    when {
      p == "/api/store/golestan" -> ShopperResponse(200, store)
      p.startsWith("/api/store/golestan/products") -> ShopperResponse(200, """{"items":[$rice],"nextCursor":null}""")
      p.startsWith("/api/store/golestan/delivery?lat=34.53") ->
        ShopperResponse(200, """{"zoned":true,"inZone":true,"zone":{"id":"zone_a","name":"کارتهٔ سه","feeMinor":3000}}""")
      p.startsWith("/api/store/golestan/delivery") -> ShopperResponse(200, """{"zoned":true,"inZone":false,"zone":null,"pickup":true}""")
      p.endsWith("/promos") -> ShopperResponse(200, """{"promos":[],"codes":true,"serverTime":1}""")
      p.endsWith("/promos/quote") -> {
        val code = Json.parseToJsonElement(r.body!!).jsonObject["promoCode"]?.jsonPrimitive?.content
        if (code == "VIP") ShopperResponse(200, """{"subtotalMinor":36000,"discountMinor":1000,"totalMinor":35000,"code":{"code":"VIP","status":"applied"}}""")
        else if (code != null) ShopperResponse(200, """{"subtotalMinor":36000,"discountMinor":0,"totalMinor":36000,"code":{"code":"$code","status":"unknown"}}""")
        else ShopperResponse(200, """{"subtotalMinor":36000,"discountMinor":0,"totalMinor":36000}""")
      }
      p.endsWith("/verify/start") -> ShopperResponse(200, """{"sent":true,"expiresAt":999}""")
      p.endsWith("/verify/check") -> ShopperResponse(200, """{"phone":"+93700000002","ticket":"v2.ticket","expiresAt":99999999999}""")
      p.endsWith("/checkout") -> ShopperResponse(201, order)
      p.endsWith("/review") -> ShopperResponse(201, """{"review":{"stars":5,"comment":"خوب","status":"pending"},"replayed":false}""")
      p.endsWith("/rating") -> ShopperResponse(200, """{"canRate":true,"rating":null,"tags":["on_time","late"]}""")
      p.endsWith("/reorder") -> ShopperResponse(200, """{"orderNo":5,"lines":[{"productId":"rice","name":"برنج","qtyMilli":3000,"previousPriceMinor":11000,"priceMinor":12000,"available":true}]}""")
      p.endsWith("/reviews") -> ShopperResponse(200, """{"count":2,"avgTenths":45,"reviews":[]}""")
      p == "/store/go/QR7" -> ShopperResponse(200, """{"store":{"slug":"golestan","path":"/store/golestan"},"campaign":"QR7"}""")
      p.endsWith("/chat/conversations") && r.method == "POST" ->
        ShopperResponse(201, """{"conversation":{"id":"sch_1","orderNo":5,"canSend":true,"life":{"kind":"order"},"shop":{"name":"گلستان"}}}""")
      p.contains("/chat/conversations/sch_1/messages?") ->
        ShopperResponse(200, """{"conversation":{"id":"sch_1","canSend":true},"messages":[{"id":"m1","seq":1,"rev":1,"sender":"shop","body":"سلام"}],"cursor":1}""")
      p.endsWith("/chat/conversations/sch_1/messages") -> {
        if (chatOffline-- > 0) throw java.io.IOException("offline")
        val cid = Json.parseToJsonElement(r.body!!).jsonObject["clientId"]!!.jsonPrimitive.content
        ShopperResponse(201, """{"message":{"id":"m2","seq":2,"rev":2,"sender":"customer","mine":true,"clientId":"$cid","body":"برنج دارید؟","status":"sent"}}""")
      }
      p.endsWith("/receipts") -> ShopperResponse(200, """{"conversation":{"id":"sch_1"}}""")
      p.startsWith("/api/store/golestan/orders/") -> ShopperResponse(200, order)
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private val kv = MapKv()

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, kv, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { now }, debounceMs = 0,
    locator = Locator { it(GeoPin(34.53, 69.17, 12)) }, secureRandom = { ByteArray(it) { 7 } }, chatWaitMs = 0,
  )

  private fun last(path: String) = seen.last { it.path.endsWith(path) }

  @Test fun pinPromoReviewChatRepeatAndDeepLinks() {
    val m = model()
    assertEquals("07".repeat(16), ShopperMemory.load(kv).deviceId)
    m.go(Route.Shop("golestan"))
    repeat(3) { m.add("golestan", m.items.single()) }
    m.go(Route.Checkout("golestan"))
    m.updateForm(m.form.copy(name = "احمد", phone = "0700000002"))
    m.sendCode()
    m.code = "123456"
    m.checkCode()

    //  پین ⇒ ناحیه از سرور؛ پینِ تنها نشانی بس است
    m.takePin()
    assertEquals(GeoPin(34.53, 69.17, 12), m.form.pin)
    assertEquals("zone_a", m.form.zoneId)
    assertFalse(m.pinOutside)
    assertTrue(m.problems().toString(), m.problems().isEmpty())
    assertFalse(m.pastePin("خانه"))
    assertEquals(ShopperText.pinBad, m.pinNote)
    assertTrue(m.pastePin("https://www.openstreetmap.org/#map=17/34.60/69.30"))
    assertTrue("پینِ بیرون ⇒ پیامِ روشن، ناحیه پاک", m.pinOutside)
    assertNull(m.form.zoneId)
    m.takePin()
    assertEquals("zone_a", m.form.zoneId)

    //  ذخیرهٔ همین نشانی از برگهٔ ثبت ⇒ «نشانی‌های من»
    m.go(Route.AddressEdit(null, fromCheckout = true))
    assertEquals(GeoPin(34.53, 69.17, 12), m.book.pin)
    m.saveAddress()
    assertEquals("بی نام ذخیره نمی‌شود", ShopperText.addressProblem(ir.vil3ntec.tohid.core.shopper.AddressProblem.LABEL), m.book.note)
    m.book.label = "خانه"
    m.saveAddress()
    assertEquals(Route.Checkout("golestan"), m.route)
    val saved = ShopperMemory.load(kv).addresses.single()
    assertEquals("خانه", saved.label)
    assertEquals(saved.id, m.form.addressId)

    //  کدِ تخفیف: نادرست ⇒ پیامِ سرور و کد برداشته؛ درست ⇒ تخفیفِ سرور در جمعِ دیده‌شده
    m.promo.input = "bad1"
    m.applyPromo()
    assertEquals(ShopperText.promoStatus("unknown"), m.promo.note)
    assertEquals("", m.promo.code)
    m.promo.input = " vip "
    m.applyPromo()
    assertEquals("VIP", m.promo.code)
    m.placeOrder()
    val body = Json.parseToJsonElement(last("/checkout").body!!).jsonObject
    assertEquals("VIP", body["promoCode"]!!.jsonPrimitive.content)
    assertEquals((36000 + 3000 - 1000).toString(), body["expectedTotalMinor"]!!.jsonPrimitive.content)
    val addr = body["address"]!!.jsonObject
    assertEquals("34.53", addr["lat"]!!.jsonPrimitive.content)
    assertNull("با پین `zoneId` نمی‌رود", addr["zoneId"])
    //  RSK-04: شناسهٔ تصادفیِ نصب در هر ثبت می‌رود (فهرستِ سیاه/سفیدِ دستگاه)، نه فقط با سدِ «یک بار» — همان مقدارِ ShopperMemory ِ بالا
    assertEquals("deviceId در هر ثبت (RSK-04)", "07".repeat(16), body["deviceId"]!!.jsonPrimitive.content)
    assertEquals(Route.Placed("golestan", "v2.tok12345"), m.route)

    //  پیگیری ⇒ نظر و امتیاز
    m.go(Route.Track("golestan", "v2.tok12345"))
    assertTrue(m.orderExtras.chat)
    assertEquals(true, m.feedback.review?.canReview)
    assertEquals(listOf("on_time", "late"), m.feedback.rating?.tags)
    m.feedback.sendReview("golestan")
    assertEquals(ShopperText.reviewPickStars, m.feedback.note)
    m.feedback.stars = 5
    m.feedback.comment = "خوب"
    m.feedback.sendReview("golestan")
    assertEquals(5, m.feedback.review?.review?.stars)
    assertEquals(false, m.feedback.review?.canReview)

    //  گفت‌وگو: بی‌اینترنت ⇒ در صف با همان clientId؛ وصل شد ⇒ یک بار
    m.go(Route.Chat("golestan", 5))
    val t = (m.chat.thread as Load.Ready).value
    assertEquals(listOf("m1"), t.messages.map { it.id })
    assertEquals("v2.ticket", last("/chat/conversations").headers["X-Store-Ticket"])
    assertTrue("پیامِ دکان دیده شد ⇒ رسید", seen.any { it.path.endsWith("/receipts") })
    m.chat.draft = "برنج دارید؟"
    m.chat.send()
    assertEquals(ChatOutbox.WAITING, m.chat.outbox.single().state)
    val cid = m.chat.outbox.single().clientId
    assertTrue("صف روی دیسک می‌ماند", kv.map[ChatOutbox.KEY]!!.contains(cid))
    m.chat.online()
    assertTrue(m.chat.outbox.isEmpty())
    val sends = seen.filter { it.path.endsWith("/chat/conversations/sch_1/messages") }
    assertEquals(2, sends.size)
    assertEquals("همان شناسه در هر دو تلاش", 2, sends.count { it.body!!.contains(cid) })
    assertEquals(listOf("m1", "m2"), (m.chat.thread as Load.Ready).value.messages.map { it.id })
    m.back()

    //  سفارشِ تکرارشونده ⇒ موعد ⇒ سبد با قیمتِ امروزِ سرور
    m.setRepeat("v2.tok12345", 7)
    assertTrue(m.duePlans().isEmpty())
    now += 7 * RepeatPlans.DAY_MS
    val due = m.duePlans().single()
    m.fillFromPlan(due)
    assertEquals(Route.CartPage("golestan"), m.route)
    val refilled = m.cartOf("golestan").lines.single()
    assertEquals("قیمتِ امروزِ سرور، نه قیمتِ سفارشِ پیشین", 12000L, refilled.priceMinor)
    assertEquals(3000L, refilled.qtyMilli)
    assertTrue(m.duePlans().isEmpty())

    //  پیوندِ عمیق: سفارش، QR ِ کمپین و پیوندِ نادرست
    assertTrue(m.openLink("https://vill3n.top/store/golestan#/o/v2.tok12345"))
    assertEquals(Route.Track("golestan", "v2.tok12345"), m.route)
    assertTrue(m.openLink("https://vill3n.top/store/go/QR7"))
    assertEquals(Route.Shop("golestan"), m.route)
    assertEquals("QR7", m.campaigns["golestan"])
    assertFalse(m.openLink("https://evil.example/store/golestan"))
    assertEquals(ShopperText.linkBad, m.linkNote)
  }
}
