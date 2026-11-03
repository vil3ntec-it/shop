package ir.vil3ntec.tohid.core.orders

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
}
