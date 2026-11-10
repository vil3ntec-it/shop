package ir.vil3ntec.tohid.core.orders

import ir.vil3ntec.tohid.core.shopper.GeoPin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RSK-03 — خواندنِ «سابقهٔ مشتری» ِ سرور (`GET /api/shop/orders/:id/risk`، `order-risk.js` ‹riskOf›) برای کارتِ جزئیاتِ
 *  سفارش: پرچمِ خاموش و پاسخِ بدشکل ⇒ هیچ؛ مشتریِ تازه ⇒ فقط نشانهٔ «تازه»؛ هشدارها اول؛ کدِ ناشناخته کنار نمی‌رود.
 *  «کارت در صف» (`GET …/risk/queue`، `queueOf`): خواندنِ نرم، تصمیمِ کارت (سرورِ کهنه ⇒ هیچ، خطا ⇒ «خوانده نشد») و نشانِ
 *  کارتِ هر سفارش؛ RSK-04: فرم و بدنهٔ «بستنِ این جا» (همان رشتهٔ `server/test/order-risk-queue.test.js`).
 */
class OrderRiskTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Test fun flagOffOrBadShapeShowsNothing() {
    assertFalse(OrderRisk.visible(OrderRisk.parse(json("""{"enabled":false}"""))))
    assertFalse(OrderRisk.visible(OrderRisk.parse(json("""{"enabled":"true","known":true}"""))))
    assertFalse(OrderRisk.visible(OrderRisk.parse(null)))
    //  بی شماره (سفارشِ دستی) ⇒ روشن ولی چیزی برای گفتن نیست
    assertFalse(OrderRisk.visible(OrderRisk.parse(json("""{"enabled":true,"known":false,"history":null,"flags":[]}"""))))
  }

  @Test fun serverShapeIsReadWarningsFirst() {
    val v = OrderRisk.parse(json(
      """{"enabled":true,"policy":{"newCustomerMaxTotalMinor":1000000},"known":true,
        "history":{"orders":6,"completed":3,"open":2,"rejectedByShop":0,"cancelledByCustomer":2,"cancelledByShop":1,"noShow":1,
          "deliveryFailed":1,"returnedToShop":0,"returnsOrRefunds":1,"firstOrderAt":100,"lastOrderAt":900},
        "flags":[{"code":"returns_before","severity":"info","count":1},{"code":"delivery_failed_before","severity":"warn","count":1},
          {"code":"no_show_before","severity":"warn","count":1},{"code":"possible_duplicate","severity":"warn","count":1,"orderNo":41},
          {"code":"blocklisted","severity":"warn","count":1,"until":1999}]}""",
    ))
    assertTrue(OrderRisk.visible(v))
    assertEquals(6L, v.history!!.orders)
    assertEquals(2L, v.history!!.cancelledByCustomer)
    assertEquals(900L, v.history!!.lastOrderAt)
    assertEquals(
      listOf("delivery_failed_before", "no_show_before", "possible_duplicate", "blocklisted", "returns_before"),
      OrderRisk.shown(v).map { it.code },
    )
    assertEquals(41L, OrderRisk.shown(v).first { it.code == "possible_duplicate" }.orderNo)
    assertEquals(1999L, OrderRisk.shown(v).first { it.code == "blocklisted" }.until)
    assertEquals(OrderRisk.Severity.WARN, OrderRisk.worst(v))
  }

  @Test fun newCustomerIsInfoOnly() {
    val v = OrderRisk.parse(json(
      """{"enabled":true,"known":false,"history":{"orders":0,"completed":0,"open":0,"firstOrderAt":null,"lastOrderAt":null},
        "flags":[{"code":"new_customer","severity":"info","count":0}]}""",
    ))
    assertTrue(OrderRisk.visible(v))
    assertEquals(OrderRisk.Severity.INFO, OrderRisk.worst(v))
    assertNull(v.history!!.firstOrderAt)
  }

  @Test fun unknownCodeFromNewerServerIsKept() {
    val v = OrderRisk.parse(json("""{"enabled":true,"known":true,"history":{"orders":1},"flags":[{"code":"chargeback_before","severity":"warn","count":2},{"severity":"warn"}]}"""))
    assertEquals(listOf("chargeback_before"), OrderRisk.shown(v).map { it.code })
    assertFalse("chargeback_before" in OrderRisk.CODES)
    assertEquals(2L, OrderRisk.shown(v).single().count)
  }

  @Test fun listEntriesAreReadAndBadRowsDropped() {
    val e = OrderRisk.entries(json(
      """{"items":[{"id":"r1","list":"block","kind":"phone","phone":"+93700111222","reason":"سه بار نبود","expiresAt":5000,"createdBy":"u","createdAt":1},
        {"id":"r2","list":"block","kind":"pin","pin":{"lat":34.5,"lng":69.2,"radiusM":150},"reason":"نشانیِ ساختگی","expiresAt":6000},
        {"id":"r3","list":"weird","kind":"phone"},{"list":"allow","kind":"phone"}],"maxActive":500,"maxDays":365}""",
    ))
    assertEquals(listOf("r1", "r2"), e.map { it.id })
    assertEquals("+93700111222", e[0].phone)
    assertEquals(150L, e[1].radiusM)
    assertEquals(34.5, e[1].lat)
    assertTrue(OrderRisk.entries(null).isEmpty())
  }

  @Test fun phoneEntryFormUsesServerBounds() {
    assertNull(OrderRisk.entryError(OrderRisk.BLOCK, "+93700111222", "سه بار نبود", 30))
    assertEquals("bad_list", OrderRisk.entryError("grey", "+93700111222", "سه بار نبود", 30))
    assertEquals("phone_required", OrderRisk.entryError(OrderRisk.ALLOW, " ", "مشتریِ همیشگی", 30))
    assertEquals("reason_required", OrderRisk.entryError(OrderRisk.BLOCK, "+93700111222", " ab ", 30))
    assertEquals("reason_required", OrderRisk.entryError(OrderRisk.BLOCK, "+93700111222", "x".repeat(201), 30))
    assertEquals("bad_expiry", OrderRisk.entryError(OrderRisk.BLOCK, "+93700111222", "سه بار نبود", 0))
    assertEquals("bad_expiry", OrderRisk.entryError(OrderRisk.BLOCK, "+93700111222", "سه بار نبود", 366))
    assertEquals("bad_expiry", OrderRisk.entryError(OrderRisk.BLOCK, "+93700111222", "سه بار نبود", null))
    val b = OrderRisk.phoneEntryBody(OrderRisk.BLOCK, "+93700111222", "  سه   بار نبود ", 30)
    assertEquals(
      """{"list":"block","kind":"phone","phone":"+93700111222","reason":"سه بار نبود","expiresInDays":30}""",
      b.toString(),
    )
  }

  @Test fun queueIsReadLenientlyWarningsFirstAndQuietRowsDropped() {
    val q = OrderRisk.queue(json(
      """{"enabled":true,"items":[
          {"orderId":"o2","orderNo":42,"status":"new","createdAt":9,"flags":[{"code":"new_customer","severity":"info","count":0},
            {"code":"delivery_failed_before","severity":"warn","count":1},{"code":"returned_to_shop_before","severity":"warn","count":1}]},
          {"orderId":"o3","orderNo":43,"status":"accepted","flags":[{"code":"new_customer","severity":"info","count":0}]},
          {"orderNo":44,"flags":[{"code":"blocklisted","severity":"warn","count":1}]},
          {"orderId":"o1","orderNo":41,"status":"packed","flags":[{"code":"chargeback_before","severity":"warn","count":2}]}],
        "scanned":3,"limit":20,"truncated":true}""",
    ))
    assertTrue(q.enabled)
    assertTrue(q.truncated)
    assertEquals(listOf("o2", "o1"), q.items.map { it.orderId })
    assertEquals(42L, q.items[0].orderNo)
    assertEquals("new", q.items[0].status)
    assertEquals(listOf("delivery_failed_before", "returned_to_shop_before", "new_customer"), q.items[0].flags.map { it.code })
    //  کدِ ناشناختهٔ سرورِ تازه‌تر کنار نمی‌رود
    assertEquals("chargeback_before", q.items[1].flags.single().code)
    assertFalse(OrderRisk.queue(json("""{"enabled":false}""")).enabled)
    assertFalse(OrderRisk.queue(json("""{"enabled":"true","items":[]}""")).enabled)
    assertFalse(OrderRisk.queue(null).enabled)
    assertTrue(OrderRisk.queue(json("""{"enabled":true,"items":"x"}""")).items.isEmpty())
  }

  @Test fun queueCardHidesForOldServerAndNeverClaimsSafe() {
    val some = OrderRisk.Queue(true, listOf(OrderRisk.QueueItem("o1", 1, "new", listOf(OrderRisk.Flag("no_show_before", OrderRisk.Severity.WARN, 1)))))
    val none = OrderRisk.Queue(true)
    assertEquals(OrderRisk.QueueCard.HIDDEN, OrderRisk.queueCard(false, some, null), "پرچمِ /config خاموش")
    assertEquals(OrderRisk.QueueCard.ITEMS, OrderRisk.queueCard(true, some, null))
    assertEquals(OrderRisk.QueueCard.HIDDEN, OrderRisk.queueCard(true, none, null))
    assertEquals(OrderRisk.QueueCard.HIDDEN, OrderRisk.queueCard(true, OrderRisk.Queue(false), null))
    assertEquals(OrderRisk.QueueCard.HIDDEN, OrderRisk.queueCard(true, null, null), "هنوز بار نشده")
    assertEquals(OrderRisk.QueueCard.HIDDEN, OrderRisk.queueCard(true, null, 404), "سرورِ کهنه بی این در")
    assertEquals(OrderRisk.QueueCard.HIDDEN, OrderRisk.queueCard(true, null, 403))
    assertEquals(OrderRisk.QueueCard.FAILED, OrderRisk.queueCard(true, null, 0), "بی شبکه")
    assertEquals(OrderRisk.QueueCard.FAILED, OrderRisk.queueCard(true, null, 500))
    assertEquals(OrderRisk.QueueCard.FAILED, OrderRisk.queueCard(true, null, 429))
  }

  @Test fun cardFlagsGiveFirstWarningPerOrder() {
    val q = OrderRisk.queue(json(
      """{"enabled":true,"items":[{"orderId":"o2","flags":[{"code":"new_customer","severity":"info"},
        {"code":"many_open","severity":"warn","count":3},{"code":"no_show_before","severity":"warn","count":1}]}]}""",
    ))
    val m = OrderRisk.cardFlags(q)
    assertEquals(setOf("o2"), m.keys)
    assertEquals("many_open", m.getValue("o2").code)
    assertEquals(3L, m.getValue("o2").count)
    assertTrue(OrderRisk.cardFlags(null).isEmpty())
    assertTrue(OrderRisk.cardFlags(OrderRisk.Queue(false)).isEmpty())
    //  ردیفی که (دستی) بی هشدار ساخته شده کرش نمی‌دهد
    assertTrue(OrderRisk.cardFlags(OrderRisk.Queue(true, listOf(OrderRisk.QueueItem("x", 1, "new", emptyList())))).isEmpty())
  }

  @Test fun pinEntryFormAndBodyUseServerBounds() {
    val pin = GeoPin(34.531234, 69.171234)
    assertNull(OrderRisk.pinEntryError(pin, 150, "نشانیِ ساختگی", 30))
    assertEquals("pin_required", OrderRisk.pinEntryError(null, 150, "نشانیِ ساختگی", 30))
    assertEquals("pin_required", OrderRisk.pinEntryError(GeoPin(0.0, 0.0), 150, "نشانیِ ساختگی", 30))
    assertEquals("pin_required", OrderRisk.pinEntryError(GeoPin(91.0, 69.0), 150, "نشانیِ ساختگی", 30))
    assertEquals("bad_radius", OrderRisk.pinEntryError(pin, 9, "نشانیِ ساختگی", 30))
    assertEquals("bad_radius", OrderRisk.pinEntryError(pin, 2001, "نشانیِ ساختگی", 30))
    assertEquals("bad_radius", OrderRisk.pinEntryError(pin, null, "نشانیِ ساختگی", 30))
    assertNull(OrderRisk.pinEntryError(pin, OrderRisk.RADIUS_MIN, "abc", 1))
    assertNull(OrderRisk.pinEntryError(pin, OrderRisk.RADIUS_MAX, "abc", OrderRisk.DAYS_MAX))
    assertEquals("reason_required", OrderRisk.pinEntryError(pin, 150, " ab ", 30))
    assertEquals("bad_expiry", OrderRisk.pinEntryError(pin, 150, "نشانیِ ساختگی", 366))
    //  همان رشته‌ای که `order-risk-queue.test.js` به سرور می‌فرستد و ۲۰۱ می‌گیرد
    assertEquals(
      """{"list":"block","kind":"pin","lat":34.531234,"lng":69.171234,"radiusM":150,"reason":"نشانیِ ساختگی","expiresInDays":30}""",
      OrderRisk.pinEntryBody(GeoPin(34.5312341, 69.1712339), 150, "  نشانیِ   ساختگی ", 30).toString(),
    )
  }

  @Test fun orderPinOnlyForDeliveryWithRealPin() {
    assertEquals(GeoPin(34.5, 69.2), OrderRisk.orderPin("delivery", 34.5, 69.2))
    assertNull(OrderRisk.orderPin("pickup", 34.5, 69.2))
    assertNull(OrderRisk.orderPin("delivery", null, 69.2))
    assertNull(OrderRisk.orderPin("delivery", 0.0, 0.0))
  }

  @Test fun typedPhoneKeepsOnlyDigitsAndPlus() {
    assertEquals("0791120005", OrderRisk.typedPhone(" ۰۷۹۱ ۱۲۰-۰۰۵ "))
    assertEquals("+93700111222", OrderRisk.typedPhone("+93 700 111 222"))
    assertEquals("", OrderRisk.typedPhone("  "))
    assertEquals("phone_required", OrderRisk.entryError(OrderRisk.BLOCK, OrderRisk.typedPhone(" - "), "سه بار نبود", 30))
  }
}
