package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.QueueState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  بدنه و پاسخِ درِ پیک درست همان است که سرور می‌خواهد و می‌دهد
 *  (`server/src/lib/delivery.js`: `parseEvent`، `courierView`).
 */
class CourierWireTest {

  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Test fun `رویداد همان کلیدهای سرور را دارد و کلیدِ خالی نمی‌فرستد`() {
    val (_, e) = CourierQueue.enqueue(QueueState(nextSeq = 7), "cev-cccccccc-1", "01ARZ3NDEKTSV4RRFFQ69G5FAV", "assigned", 1_700_000_000_000, from = "requested")
    val j = CourierWire.eventJson(e)
    assertEquals(setOf("eventId", "assignmentId", "type", "deviceTime", "deviceSeq", "from"), j.keys)
    assertEquals("7", j["deviceSeq"].toString())
    assertEquals("1700000000000", j["deviceTime"].toString())
    val (_, d) = CourierQueue.enqueue(QueueState(), "cev-cccccccc-2", "A", "delivered", 1, cashCollectedMinor = 1_200_000, cashReason = "partial_payment", code = "123456")
    val dj = CourierWire.eventJson(d)
    assertEquals("1200000", dj["cashCollectedMinor"].toString())
    assertEquals("\"123456\"", dj["code"].toString())
    assertEquals("\"partial_payment\"", dj["cashReason"].toString())
  }

  @Test fun `کارتِ زنده و کارتِ پایان‌یافتهٔ سرور`() {
    val live = CourierWire.card(obj("""
      {"id":"A","orderId":"O","orderNo":12,"orderStatus":"out_for_delivery","status":"moving","live":true,
       "address":{"landmark":"نزدیکِ مسجد","text":"کوچهٔ سوم","lat":34.5,"lng":69.1},
       "customer":{"name":"احمد","phoneMasked":"0791•••567"},
       "delivery":{"callBeforeArrival":true,"leaveWith":""},
       "itemsCount":1,"paymentMethod":"cod","expectedCollectMinor":1240000,"cashCollectedMinor":null,
       "fareMinor":5000,"requiresCode":true,"photoAllowed":true,"recall":null,"version":3,"updatedAt":99}
    """))!!
    assertEquals("نزدیکِ مسجد", live.landmark)
    assertEquals(34.5, live.lat!!, 0.0)
    assertEquals("0791•••567", live.phoneMasked)
    assertTrue(live.callBeforeArrival)
    assertNull(live.cashCollectedMinor)
    assertFalse(live.recallRequested)
    val done = CourierWire.card(obj("""
      {"id":"B","orderNo":13,"status":"delivered","live":false,"address":null,"customer":null,"delivery":null,
       "expectedCollectMinor":1240000,"cashCollectedMinor":1200000,"recall":{"requestedAt":5,"reason":"other"}}
    """))!!
    assertEquals("", done.landmark)
    assertEquals("", done.phoneMasked)
    assertEquals(1_200_000L, done.cashCollectedMinor)
    assertTrue(done.recallRequested)
    assertNull(CourierWire.card(obj("""{"status":"moving"}""")))
  }

  @Test fun `خطِ زمانیِ مأموریت و «نقدِ من» همان کلیدهای سرور را می‌خوانند`() {
    val c = CourierWire.card(obj("""
      {"id":"A","orderNo":12,"status":"moving","timeline":[
        {"kind":"created","to":"requested","at":100,"reason":"","by":"shop"},
        {"kind":"status","to":"failed","at":200,"reason":"no_answer","by":"courier"},
        {"kind":"status","to":"moving"}]}
    """))!!
    assertEquals(2, c.timeline.size)
    assertEquals("no_answer", c.timeline[1].reason)
    assertEquals("courier", c.timeline[1].by)
    assertTrue(CourierWire.card(obj("""{"id":"B","status":"moving"}"""))!!.timeline.isEmpty())

    val s = CourierWire.summary(obj("""
      {"courier":{"name":"پیک","active":true},"shop":{"name":"دکان","phone":"0700000000"},
       "cash":{"inHandMinor":98000,"liveExpectedMinor":552000,"capMinor":1000000,"headroomMinor":350000,"liveCount":3},
       "unsettled":[{"orderNo":1038,"deliveredAt":5,"expectedCollectMinor":98000,"cashCollectedMinor":98000,"fareMinor":5000}],
       "settlements":[{"settleNo":11,"at":7,"status":"confirmed","assignmentsCount":4,"collectedCashMinor":920000,
         "fareTotalMinor":28000,"handoverDueMinor":920000,"receivedMinor":900000,"shortageMinor":20000,"excessMinor":0,"reason":"کسری"}],
       "stats":{"today":{"from":1,"delivered":2,"failed":1,"collectedCashMinor":98000,"fareMinor":11000},
                "week":{"from":0,"delivered":14,"failed":2,"collectedCashMinor":3120000,"fareMinor":76000}},
       "serverTime":9}
    """))
    assertEquals("دکان", s.shopName)
    assertEquals("0700000000", s.shopPhone)
    assertEquals(98_000L, s.cashInHandMinor)
    assertEquals(350_000L, s.headroomMinor)
    assertEquals(1038L, s.unsettled.single().orderNo)
    assertEquals(20_000L, s.settlements.single().shortageMinor)
    assertEquals(2, s.today.delivered)
    assertEquals(3_120_000L, s.week.collectedCashMinor)
    //  کلیدِ ناپیدا ⇒ صفر/خالی، نه خطا
    val empty = CourierWire.summary(obj("{}"))
    assertEquals(0L, empty.cashCapMinor)
    assertTrue(empty.settlements.isEmpty())
  }

  @Test fun `پاسخِ رویداد`() {
    val r = CourierWire.result(obj("""{"eventId":"cev-x","status":"rejected","code":"bad_code","message":"کد درست نیست"}"""))
    assertEquals("rejected", r.status)
    assertEquals("bad_code", r.code)
  }
}
