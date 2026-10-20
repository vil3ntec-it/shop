package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShopperCheckoutTest {

  private val clock = Clock()
  private val storage = MemoryStorage()
  private val tickets = ShopperTickets(storage, clock.now)

  private val zone = DeliveryZone("z1", "مرکز", feeMinor = 3000, minOrderMinor = 15000, freeOverMinor = 50000)
  private val flat = StoreProfile(
    slug = "nan", title = "نانوایی", fulfillment = Fulfillment(delivery = true, pickup = true),
    deliveryFeeMinor = 5000, minOrderMinor = 10000,
  )
  private val zoned = flat.copy(delivery = DeliveryInfo(zoned = true, zones = listOf(zone)))

  private fun cart(subtotal: Long) = ShopCart("nan", lines = listOf(CartLine("p1", "نان", priceMinor = subtotal, qtyMilli = 1000)))

  private val form = CheckoutForm(
    fulfillment = ShopperCheckout.DELIVERY, name = "احمد", phone = "0700 000 001", addressText = "کارته سه",
  )

  private fun verify() = tickets.save(PhoneTicket("+93700000001", "v2.t", clock.at + 60_000))

  @Test
  fun `جمع — ارسالِ ثابت، دریافت از دکان، ناحیه و «رایگان از … به بالا»`() {
    assertEquals(CheckoutTotals(20000, 5000, 10000), ShopperCheckout.totals(flat, cart(20000), form, null))
    assertEquals(CheckoutTotals(20000, 0, 10000), ShopperCheckout.totals(flat, cart(20000), form.copy(fulfillment = "pickup"), null))
    val unknown = ShopperCheckout.totals(zoned, cart(20000), form, null)
    assertFalse(unknown.feeKnown)
    assertEquals(CheckoutTotals(20000, 3000, 15000), ShopperCheckout.totals(zoned, cart(20000), form, zone))
    val free = ShopperCheckout.totals(zoned, cart(50000), form, zone)
    assertEquals(0L, free.deliveryFeeMinor)
    assertTrue(free.freeDelivery)
    assertEquals(50000L, free.totalMinor)
  }

  @Test
  fun `ناحیه — پین برنده است، بی پین ناحیهٔ برگزیده`() {
    val pinned = form.copy(pin = GeoPoint(34.5, 69.2), zoneId = "z1")
    assertNull(ShopperCheckout.zoneOf(zoned, pinned, null))
    assertEquals(zone, ShopperCheckout.zoneOf(zoned, pinned, ZoneLookup(zoned = true, inZone = true, zone = zone)))
    assertNull(ShopperCheckout.zoneOf(zoned, pinned, ZoneLookup(zoned = true, inZone = false)))
    assertEquals(zone, ShopperCheckout.zoneOf(zoned, form.copy(zoneId = "z1"), null))
    assertNull(ShopperCheckout.zoneOf(flat, form.copy(zoneId = "z1"), null))
  }

  @Test
  fun `مسئله‌های فرم پیش از فرستادن`() {
    val empty = ShopperCheckout.problems(flat, null, CheckoutForm(), tickets, null, null)
    assertTrue(FormProblem.CART_EMPTY in empty)
    assertTrue(FormProblem.FULFILLMENT_REQUIRED in empty)
    assertTrue(FormProblem.NAME_REQUIRED in empty)
    assertTrue(FormProblem.PHONE_INVALID in empty)

    //  شماره درست ولی تأییدنشده
    assertEquals(listOf(FormProblem.PHONE_NOT_VERIFIED), ShopperCheckout.problems(flat, cart(20000), form, tickets, null, null))
    verify()
    assertEquals(emptyList<FormProblem>(), ShopperCheckout.problems(flat, cart(20000), form, tickets, null, null))
    //  هر شکلِ نوشتنِ همان شماره پذیرفته است
    assertTrue(ShopperCheckout.problems(flat, cart(20000), form.copy(phone = "+93 70 000 0001"), tickets, null, null).isEmpty())

    assertEquals(
      listOf(FormProblem.ADDRESS_REQUIRED),
      ShopperCheckout.problems(flat, cart(20000), form.copy(addressText = ""), tickets, null, null),
    )
    assertEquals(listOf(FormProblem.BELOW_MIN_ORDER), ShopperCheckout.problems(flat, cart(5000), form, tickets, null, null))
    val bad = cart(20000).let { it.copy(lines = it.lines.map { l -> l.copy(unavailable = true) }) }
    assertEquals(listOf(FormProblem.CART_UNAVAILABLE), ShopperCheckout.problems(flat, bad, form, tickets, null, null))
  }

  @Test
  fun `ارسالِ ناحیه‌دار — ناحیه لازم، پینِ بیرون از محدوده`() {
    verify()
    assertEquals(listOf(FormProblem.ZONE_REQUIRED), ShopperCheckout.problems(zoned, cart(20000), form, tickets, null, null))
    val out = form.copy(pin = GeoPoint(1.0, 1.0))
    assertEquals(
      listOf(FormProblem.OUT_OF_ZONE),
      ShopperCheckout.problems(zoned, cart(20000), out, tickets, ZoneLookup(zoned = true, inZone = false), null),
    )
    assertTrue(ShopperCheckout.problems(zoned, cart(20000), form.copy(zoneId = "z1"), tickets, null, null).isEmpty())
    //  حداقلِ ناحیه (۱۵۰) نه حداقلِ دکان (۱۰۰)
    assertEquals(
      listOf(FormProblem.BELOW_MIN_ORDER),
      ShopperCheckout.problems(zoned, cart(12000), form.copy(zoneId = "z1"), tickets, null, null),
    )
  }

  @Test
  fun `بازه — لازم وقتی «هرچه زودتر» بسته است؛ بازهٔ پر پذیرفته نیست`() {
    verify()
    val slots = SlotList(
      fulfillment = "delivery", scheduling = true, allowAsap = false,
      slots = listOf(DeliverySlot(startsAt = 100, endsAt = 200, available = true), DeliverySlot(startsAt = 300, endsAt = 400, available = false)),
    )
    assertEquals(listOf(FormProblem.SLOT_REQUIRED), ShopperCheckout.problems(flat, cart(20000), form, tickets, null, slots))
    assertTrue(ShopperCheckout.problems(flat, cart(20000), form.copy(slotStartsAt = 100), tickets, null, slots).isEmpty())
    assertEquals(
      listOf(FormProblem.SLOT_UNAVAILABLE),
      ShopperCheckout.problems(flat, cart(20000), form.copy(slotStartsAt = 300), tickets, null, slots),
    )
    assertTrue(ShopperCheckout.problems(flat, cart(20000), form, tickets, null, slots.copy(allowAsap = true)).isEmpty())
  }

  @Test
  fun `بدنهٔ ثبت همان شکلِ ویترینِ وب، با شمارهٔ E164 و جمعِ دیده‌شده`() {
    val f = form.copy(pin = GeoPoint(34.5, 69.2), landmark = "کنارِ مسجد", callBeforeArrival = true, slotStartsAt = 100, note = " زود ")
    val body = ShopperCheckout.body(cart(20000), f, CheckoutTotals(20000, 5000, 10000))
    assertEquals("delivery", body["fulfillment"]!!.jsonPrimitive.content)
    assertEquals("cod", body["paymentMethod"]!!.jsonPrimitive.content)
    assertEquals("+93700000001", body["customer"]!!.jsonObject["phone"]!!.jsonPrimitive.content)
    assertEquals(25000L, body["expectedTotalMinor"]!!.jsonPrimitive.long)
    val line = (body["lines"] as JsonArray).single().jsonObject
    assertEquals("p1", line["productId"]!!.jsonPrimitive.content)
    assertEquals(1000L, line["qtyMilli"]!!.jsonPrimitive.long)
    val address = body["address"]!!.jsonObject
    assertEquals("34.5", address["lat"]!!.jsonPrimitive.content)
    assertFalse(address.containsKey("zoneId"))
    assertEquals("true", body["delivery"]!!.jsonObject["callBeforeArrival"]!!.jsonPrimitive.content)
    assertEquals(100L, body["slotStartsAt"]!!.jsonPrimitive.long)
    assertEquals("زود", body["note"]!!.jsonPrimitive.content)

    val pickup = ShopperCheckout.body(cart(20000), form.copy(fulfillment = "pickup"), CheckoutTotals(20000, 0, 10000))
    assertFalse(pickup.containsKey("address"))
    assertFalse(pickup.containsKey("delivery"))
    assertFalse(pickup.containsKey("slotStartsAt"))
  }

  private fun failure(status: Int, code: String, extra: String = ""): ApiFailure =
    ApiFailure.fromHttp(status, err(status, code, "پیام", extra).second, authenticated = false)

  @Test
  fun `خطاهای ثبت با دادهٔ همراهشان خوانده می‌شوند`() {
    val quote = ShopperCheckout.interpret(
      failure(409, "price_changed", """"quote":{"lines":[{"productId":"p1","name":"نان","unitPriceMinor":1100}],"subtotalMinor":1100,"deliveryFeeMinor":0,"totalMinor":1100}"""),
    )
    assertEquals(1100L, (quote as CheckoutIssue.PriceChanged).quote.totalMinor)
    assertEquals(
      CheckoutIssue.Unavailable(listOf("p1", "p2")),
      ShopperCheckout.interpret(failure(409, "product_unavailable", """"unavailable":["p1","p2"]""")),
    )
    assertEquals(
      CheckoutIssue.OverLimit("p1", 2000),
      ShopperCheckout.interpret(failure(400, "qty_over_limit", """"productId":"p1","maxQtyMilli":2000""")),
    )
    assertEquals(CheckoutIssue.BelowMin(15000), ShopperCheckout.interpret(failure(400, "below_min_order", """"minOrderMinor":15000""")))
    assertEquals(CheckoutIssue.NeedsVerification, ShopperCheckout.interpret(failure(401, "verification_required")))
    assertEquals(CheckoutIssue.NeedsVerification, ShopperCheckout.interpret(failure(403, "phone_not_verified")))
    val zones = """"zones":[{"id":"z1","name":"مرکز","feeMinor":3000,"minOrderMinor":0,"freeOverMinor":null}]"""
    assertEquals(
      CheckoutIssue.OutOfZone(listOf(DeliveryZone("z1", "مرکز", 3000, 0, null)), pickup = true),
      ShopperCheckout.interpret(failure(400, "out_of_zone", """"pickup":true,$zones""")),
    )
    assertTrue(ShopperCheckout.interpret(failure(400, "zone_required", zones)) is CheckoutIssue.ZoneNeeded)
    val full = ShopperCheckout.interpret(failure(409, "slot_full", """"slots":[{"startsAt":5,"endsAt":6,"available":true}]"""))
    assertEquals(CheckoutIssue.SlotNeeded(listOf(DeliverySlot(startsAt = 5, endsAt = 6, available = true)), full = true), full)
    assertEquals(CheckoutIssue.FulfillmentGone, ShopperCheckout.interpret(failure(400, "fulfillment_unavailable")))
    //  دادهٔ ناقص یا ناشناخته ⇒ همان خطای سرور
    assertTrue(ShopperCheckout.interpret(failure(409, "price_changed")) is CheckoutIssue.Failed)
    assertTrue(ShopperCheckout.interpret(failure(500, "internal")) is CheckoutIssue.Failed)
  }

  @Test
  fun `کلیدِ یکتایی تا بدنه همان است همان می‌ماند`() {
    val keys = CheckoutKeys(storage, Random(1))
    val a = JsonObject(mapOf("x" to kotlinx.serialization.json.JsonPrimitive(1)))
    val b = JsonObject(mapOf("x" to kotlinx.serialization.json.JsonPrimitive(2)))
    val k1 = keys.keyFor("nan", a)
    assertTrue(Regex("^[A-Za-z0-9_.:-]{8,128}$").matches(k1))
    //  برنامه بسته و باز شد ⇒ همان کلید
    assertEquals(k1, CheckoutKeys(storage, Random(2)).keyFor("nan", a))
    val k2 = keys.keyFor("nan", b)
    assertNotEquals(k1, k2)
    keys.drop("nan")
    assertNotEquals(k2, keys.keyFor("nan", b))
  }

  @Test
  fun `روشِ پیش‌فرض — ارسال اگر هست`() {
    assertEquals("delivery", ShopperCheckout.defaultFulfillment(flat))
    assertEquals("pickup", ShopperCheckout.defaultFulfillment(flat.copy(fulfillment = Fulfillment(pickup = true))))
    assertEquals("", ShopperCheckout.defaultFulfillment(flat.copy(fulfillment = Fulfillment())))
  }
}
