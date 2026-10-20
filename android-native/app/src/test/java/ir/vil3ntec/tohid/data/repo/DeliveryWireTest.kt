package ir.vil3ntec.tohid.data.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  شکلِ سیمِ میزِ تحویل همان است که `server/src/routes/delivery.js` و
 *  `lib/delivery.js` می‌دهند و می‌خواهند (همان کلیدهای آزمونِ
 *  `server/test/delivery-desk.test.js`).
 */
class DeliveryWireTest {

  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Test fun `config — دسترسی‌ها، روش‌ها و دلیل‌ها`() {
    val c = DeliveryWire.config(obj("""
      {"ordersFlag":true,"flag":true,"readable":true,"entitled":true,"enabled":true,
       "modes":{"shop_courier":true,"external":true,"pickup":true,"platform":false},
       "reasons":{"failed":["no_answer","other"],"cash":["partial_payment"]},
       "defaultCashCapMinor":1000000,
       "permissions":{"view":true,"assign":false,"handover":true,"couriers":false,"settle":false,"photo":false}}
    """))
    assertTrue(c.readable && c.entitled && c.enabled)
    assertTrue(c.shopCourier && c.external && c.pickup)
    assertEquals(listOf("no_answer", "other"), c.reasons["failed"])
    assertEquals(1_000_000, c.defaultCashCapMinor)
    assertTrue(c.perms.view && c.perms.handover)
    assertFalse(c.perms.assign || c.perms.settle)
    val off = DeliveryWire.config(obj("""{"readable":false}"""))
    assertFalse(off.readable)
    assertFalse(off.perms.view)
  }

  @Test fun `پیک و مأموریتِ تابلو — با نامِ مشتری و وضعیتِ سفارش`() {
    val cr = DeliveryWire.courier(obj("""
      {"id":"01ARZ3NDEKTSV4RRFFQ69G5FAV","userId":"u1","name":"احمد","phone":"","active":true,
       "cashCapMinor":1000000,"cashHeldMinor":250000,"liveCount":2,"liveExpectedMinor":300000,"createdAt":1,"updatedAt":2}
    """))!!
    assertEquals(250_000, cr.cashHeldMinor)
    assertEquals(2, cr.liveCount)
    assertNull(DeliveryWire.courier(obj("""{"name":"بی شناسه"}""")))

    val m = DeliveryWire.mission(obj("""
      {"id":"A","orderId":"O","attemptNo":2,"mode":"external","courierId":"","externalName":"پیک‌رسان","externalRef":"TRK-1",
       "status":"picked_up","statusReason":"","live":true,"expectedCollectMinor":1240000,"cashCollectedMinor":null,
       "fareMinor":15000,"cashReview":false,"hasCode":true,"codeUsed":false,"codeLocked":false,
       "recall":{"requestedAt":5,"reason":"customer_request","by":"u","outcome":"pending"},
       "deliveredAt":null,"settlementId":"","version":3,"createdAt":1,"updatedAt":2,
       "orderNo":17,"orderStatus":"out_for_delivery","customerName":"زهرا"}
    """))!!
    assertEquals(17L, m.orderNo)
    assertEquals(2, m.attemptNo)
    assertNull(m.cashCollectedMinor)
    assertEquals(15_000, m.fareMinor)
    assertTrue(m.recallRequested)
    assertEquals("pending", m.recallOutcome)
    assertEquals("زهرا", m.customerName)
    assertEquals("out_for_delivery", m.orderStatus)
    val noRecall = DeliveryWire.mission(obj("""{"id":"B","recall":null,"live":false}"""))!!
    assertFalse(noRecall.recallRequested)
  }

  @Test fun `پیش‌نمایش و سندِ تسویه و جمعِ تسویه‌ها`() {
    val p = DeliveryWire.preview(obj("""
      {"courier":{"id":"C","name":"احمد","active":true,"cashCapMinor":1000000,"cashHeldMinor":1240000},
       "rows":[{"assignmentId":"A","orderId":"O","orderNo":5,"deliveredAt":10,"expectedCollectMinor":1240000,
                "cashCollectedMinor":1200000,"collectionDiffMinor":-40000,"fareMinor":30000,"cashReview":true}],
       "totals":{"count":1},"serverTime":99}
    """))!!
    assertEquals("C", p.courier.id)
    assertEquals(-40_000, p.rows.single().collectionDiffMinor)
    assertTrue(p.rows.single().cashReview)
    assertEquals(99, p.serverTime)

    val page = DeliveryWire.settlementPage(obj("""
      {"items":[{"id":"S","settleNo":4,"courierId":"C","status":"confirmed","assignmentsCount":1,"expectedCashMinor":1240000,
                 "collectedCashMinor":1240000,"collectionDiffMinor":0,"fareTotalMinor":30000,"farePaidFromCash":false,
                 "handoverDueMinor":1240000,"handedOverMinor":1200000,"receivedMinor":1200000,"shortageMinor":40000,
                 "excessMinor":0,"reason":"کسری","note":"","confirmedBy":"u","createdAt":7}],
       "totals":{"settlements":1,"assignments":1,"collectedCashMinor":1240000,"receivedMinor":1200000,"shortageMinor":40000}}
    """))
    assertEquals(4L, page.items.single().settleNo)
    assertEquals(40_000, page.items.single().shortageMinor)
    assertEquals(1_200_000, page.sums.receivedMinor)
    assertEquals(1, page.sums.settlements)
  }

  @Test fun `شرکت‌ها و صورت‌حساب`() {
    val co = DeliveryWire.rows(obj("""{"items":[{"name":"پیک‌رسان","assignments":3,"live":1,"delivered":1,"lastFareMinor":18000,"lastUsedAt":5},{"name":""}]}"""), "items", DeliveryWire::company)
    assertEquals(1, co.size)
    assertEquals(18_000, co.single().lastFareMinor)
    val st = DeliveryWire.statement(obj("""
      {"name":"پیک‌رسان","truncated":false,"rows":[{"assignmentId":"A","orderId":"O","orderNo":11,"externalRef":"TRK-1",
        "status":"delivered","fareMinor":15000,"expectedCollectMinor":1240000,"cashCollectedMinor":1240000,"deliveredAt":9,"createdAt":3}],
       "totals":{"count":1,"delivered":1,"deliveredFareMinor":15000,"deliveredCashMinor":1240000}}
    """))
    assertEquals("TRK-1", st.rows.single().externalRef)
    assertEquals("کرایه‌ای که دکان بدهکار است", 15_000, st.deliveredFareMinor)
    assertEquals("نقدی که شرکت از مشتری گرفت — گم نمی‌شود", 1_240_000, st.deliveredCashMinor)
    assertEquals(1_240_000L, st.rows.single().cashCollectedMinor)
    assertFalse(st.truncated)
  }

  @Test fun `بدنه‌ها — فقط کلیدهای همان روش`() {
    val courier = DeliveryWire.assignJson("shop_courier", courierId = "C", fareMinor = 20_000, overrideCashCap = true)
    assertEquals(setOf("mode", "fareMinor", "courierId", "overrideCashCap"), courier.keys)
    val ext = DeliveryWire.assignJson("external", externalName = " پیک‌رسان ", externalRef = "", fareMinor = 0)
    assertEquals(setOf("mode", "externalName"), ext.keys)
    assertEquals("\"پیک‌رسان\"", ext["externalName"].toString())
    val pickup = DeliveryWire.assignJson("pickup", fareMinor = 500)
    assertEquals("دریافتِ حضوری کرایه ندارد (bad_fee)", setOf("mode"), pickup.keys)

    val st = DeliveryWire.statusJson(to = "delivered", from = "assigned", code = "123456", cashCollectedMinor = 1_200_000, cashReason = "partial_payment")
    assertEquals(setOf("to", "from", "code", "cashCollectedMinor", "cashReason"), st.keys)
    assertEquals(setOf("to"), DeliveryWire.statusJson(to = "picked_up").keys)

    val s = DeliveryWire.settleJson("dsk-1", listOf("A", "B"), 1_200_000, 1_200_000, false, " کسری ", "")
    assertEquals(setOf("clientKey", "assignmentIds", "handedOverMinor", "receivedMinor", "farePaidFromCash", "reason"), s.keys)
    assertEquals("[\"A\",\"B\"]", s["assignmentIds"].toString())
    assertEquals("\"کسری\"", s["reason"].toString())

    assertEquals(setOf("reason"), DeliveryWire.recallJson("customer_request", " ").keys)
    assertEquals(setOf("cashCapMinor"), DeliveryWire.courierUpdateJson(cashCapMinor = 2_000_000).keys)
    assertEquals(setOf("active"), DeliveryWire.courierUpdateJson(active = false).keys)
  }
}
