package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderRefundSummaryDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  FIN-01 — «ثبتِ بازپرداخت» با مبلغ در برگهٔ گذار، فقط با دفترِ بازپرداختِ روشن (`refundLedger`). سرور گذارِ سادهٔ
 *  «بازپرداخت‌شده» را آن‌وقت رد می‌کند (۴۰۹ `refund_via_ledger`)، پس برگه مبلغ و دلیل می‌گیرد؛ خاموش همان گذارِ پیشین.
 */
class OrderRefundModelTest {

  private val order = OrderDto(id = "o", orderNo = 12, status = "return_requested", totalMinor = 250_000)
  private val half = order.copy(refund = OrderRefundSummaryDto(totalMinor = 250_000, refundedMinor = 100_000, remainingMinor = 150_000))

  @Test fun `فقط با دفترِ روشن و فقط برای گذارهای پولی`() {
    val on = OrdersConfigDto(enabled = true, refundLedger = true)
    assertTrue(OrderRefundModel.ledger(on, "refunded_partial"))
    assertTrue(OrderRefundModel.ledger(on, "refunded_full"))
    assertFalse(OrderRefundModel.ledger(on, "cancelled"))
    //  خاموش یا سرورِ پیشین ⇒ همان گذارِ ساده
    assertFalse(OrderRefundModel.ledger(OrdersConfigDto(enabled = true), "refunded_full"))
    assertFalse(OrderRefundModel.ledger(null, "refunded_full"))
  }

  @Test fun `مبلغِ نوشته به واحدِ خرد — رقمِ فارسی، جداکننده و دو رقمِ اعشار؛ نادرست null`() {
    assertEquals(125_000L, OrderRefundModel.amountMinor("1250"))
    assertEquals(125_050L, OrderRefundModel.amountMinor("۱٬۲۵۰٫۵"))
    assertEquals(125_005L, OrderRefundModel.amountMinor("1,250.05"))
    assertNull(OrderRefundModel.amountMinor("0"))
    assertNull(OrderRefundModel.amountMinor("-5"))
    assertNull(OrderRefundModel.amountMinor("1.234"))
    assertNull(OrderRefundModel.amountMinor("ده"))
    assertNull(OrderRefundModel.amountMinor(""))
    assertEquals("1250", OrderRefundModel.afnText(125_000))
    assertEquals("1250.5", OrderRefundModel.afnText(125_050))
    assertEquals("1250.05", OrderRefundModel.afnText(125_005))
  }

  @Test fun `کامل همان مانده؛ بخشی کمتر از مانده؛ دلیل لازم`() {
    //  بی دفترِ سفارش (سرورِ پیشین) مانده همان جمعِ سفارش
    assertEquals(250_000L, OrderRefundModel.remainingMinor(order))
    assertEquals(150_000L, OrderRefundModel.remainingMinor(half))
    assertEquals("1500", OrderRefundModel.startAmount(half, "refunded_full"))
    assertEquals("", OrderRefundModel.startAmount(half, "refunded_partial"))

    assertNull(OrderRefundModel.check(half, "refunded_full", "1500", "کالا خراب رسید"))
    assertEquals(OrderRefundModel.Problem.NOT_REMAINING, OrderRefundModel.check(half, "refunded_full", "2500", "x"))
    assertNull(OrderRefundModel.check(half, "refunded_partial", "400", "یک قلم کم بود"))
    assertEquals(OrderRefundModel.Problem.NOT_BELOW_REMAINING, OrderRefundModel.check(half, "refunded_partial", "1500", "x"))
    assertEquals(OrderRefundModel.Problem.AMOUNT, OrderRefundModel.check(half, "refunded_partial", "", "x"))
    assertEquals(OrderRefundModel.Problem.REASON, OrderRefundModel.check(half, "refunded_partial", "400", "  "))
    //  «بخشی» سفارش را می‌بندد؛ «کامل» با رسیدن به مانده خودش بسته می‌شود
    assertTrue(OrderRefundModel.final("refunded_partial"))
    assertFalse(OrderRefundModel.final("refunded_full"))
  }

  @Test fun `پاسخِ سرور — refundLedger ِ پیکربندی و refund ِ جزئیات با همان نام‌ها؛ سرورِ پیشین بی آن‌ها`() {
    val cfg = ApiJson.decode<OrdersConfigDto>(Json.parseToJsonElement("""{"enabled":true,"refundLedger":true}""") as JsonObject)
    assertTrue(cfg.refundLedger)
    val got = ApiJson.decode<OrderDto>(
      Json.parseToJsonElement(
        """{"id":"o","orderNo":12,"status":"return_requested","totalMinor":250000,
          "refund":{"currency":"AFN","totalMinor":250000,"refundedMinor":100000,"remainingMinor":150000,"netMinor":150000,"issue":null,"entries":[]}}""",
      ) as JsonObject,
    )
    assertEquals(150_000L, got.refund?.remainingMinor)
    val old = ApiJson.decode<OrderDto>(Json.parseToJsonElement("""{"id":"o","totalMinor":9}""") as JsonObject)
    assertNull(old.refund)
    assertFalse(ApiJson.decode<OrdersConfigDto>(Json.parseToJsonElement("""{"enabled":true}""") as JsonObject).refundLedger)
  }

  @Test fun `ORD-15 — تراشهٔ «حساب‌پیِ دستی» فقط وقتی سرور refundMethods داد؛ وگرنه همان نقد`() {
    val on = ApiJson.decode<OrdersConfigDto>(
      Json.parseToJsonElement("""{"enabled":true,"refundLedger":true,"refundMethods":["cash","hesabpay","card"]}""") as JsonObject,
    )
    assertEquals(listOf("cash", "hesabpay"), OrderRefundModel.methods(on))
    assertEquals("hesabpay", OrderRefundModel.methodFor("hesabpay", OrderRefundModel.methods(on)))
    //  پرچمِ خاموش، سرورِ پیشین یا فقط نقد ⇒ هیچ تراشه و همیشه نقد (همان بدنهٔ پیشین)
    val off = OrdersConfigDto(enabled = true, refundLedger = true)
    assertEquals(emptyList<String>(), OrderRefundModel.methods(off))
    assertEquals(emptyList<String>(), OrderRefundModel.methods(off.copy(refundMethods = listOf("cash"))))
    assertEquals("cash", OrderRefundModel.methodFor("hesabpay", OrderRefundModel.methods(off)))
    assertEquals("cash", OrderActionModel.Form().method)
  }
}
