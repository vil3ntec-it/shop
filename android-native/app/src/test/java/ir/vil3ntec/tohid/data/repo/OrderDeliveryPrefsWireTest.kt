package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionView
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.CourierCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  دستورِ تحویل روی سیم (CHK-14، CHK-24) — همان شکلِ `server/src/lib/delivery-prefs.js`:
 *  فرمِ دستی اسکناس را فقط وقتی نوشته شده می‌فرستد؛ جزئیات و کارتِ پیک آن را می‌خوانند و پیک
 *  آن را در صفِ آفلاینِ گوشی نگه می‌دارد؛ سرورِ کهنه ⇒ بی اسکناس.
 */
class OrderDeliveryPrefsWireTest {
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
  private val form = OrderDraft.Form(
    name = "احمد", phone = "0791234567", fulfillment = "delivery", landmark = "مسجد",
    lines = listOf(OrderDraft.LineInput("برنج", "1", "120")),
  )
  private fun body(f: OrderDraft.Form) = (OrderDraft.build(f) as OrderDraft.Result.Ok).body

  @Test fun manual_order_sends_cash_tender_only_when_written() {
    assertFalse(OrdersRepository.createJson("app-k1", body(form)).containsKey("delivery"))
    val sent = OrdersRepository.createJson("app-k2", body(form.copy(cashTender = "۱۰۰۰")))
    assertEquals("100000", sent["delivery"]!!.jsonObject["cashTenderMinor"].toString())
  }

  @Test fun order_detail_and_config_read_delivery_prefs_and_old_server_defaults() {
    val o = ApiJson.decode<OrderDto>(obj("""{"id":"o1","fulfillment":"delivery","totalMinor":1240000,
      "delivery":{"callBeforeArrival":true,"leaveWith":"دربان","photoConsent":true,"cashTenderMinor":1300000}}"""))
    assertEquals(1_300_000L, o.delivery.cashTenderMinor)
    assertEquals("دربان", o.delivery.leaveWith)
    val old = ApiJson.decode<OrderDto>(obj("""{"id":"o2","delivery":{"callBeforeArrival":true}}"""))
    assertNull(old.delivery.cashTenderMinor)
    assertFalse(ApiJson.decode<OrdersConfigDto>(obj("""{"enabled":true}""")).deliveryPrefs)
  }

  @Test fun courier_card_keeps_tender_offline_and_redacts_it_after_the_mission() {
    val card = CourierWire.card(obj("""{"id":"A","orderNo":12,"status":"moving","live":true,
      "delivery":{"callBeforeArrival":false,"leaveWith":"","cashTenderMinor":1300000,"changeMinor":60000},
      "expectedCollectMinor":1240000}"""))!!
    assertEquals(1_300_000L, card.cashTenderMinor)
    val back = CourierCodec.cards(CourierCodec.cardsJson(listOf(card, card.copy(id = "B", cashTenderMinor = null))))
    assertEquals(1_300_000L, back[0].cashTenderMinor)
    assertNull(back[1].cashTenderMinor)
    assertNull(MissionView.redact(card).cashTenderMinor)
    //  سرورِ کهنه بی کلید ⇒ null
    assertNull(CourierWire.card(obj("""{"id":"C","status":"moving","delivery":{"callBeforeArrival":true}}"""))!!.cashTenderMinor)
    assertEquals(MissionCard(id = "D", orderNo = 1, status = "moving"), CourierCodec.cards(CourierCodec.cardsJson(listOf(MissionCard(id = "D", orderNo = 1, status = "moving"))))[0])
  }
}
