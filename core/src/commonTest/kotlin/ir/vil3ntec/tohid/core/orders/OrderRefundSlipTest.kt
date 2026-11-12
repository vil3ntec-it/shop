package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  E2E-RET-11 — کاغذِ رسیدِ اصلاحی فقط از عددهای سرور ([OrderRefundSlip]): همان ردیف‌های `receiptsOf` ِ
 *  `server/src/lib/order-refund-receipt.js` (سفارشِ ۱۲۵۰ افغانی، بازپرداختِ ۳۰۰ و سپس ۲۰۰ ِ پایانی)؛ ناهمخوان ⇒ هیچ کاغذ.
 */
class OrderRefundSlipTest {

  private object Tx : OrderRefundSlip.Texts {
    override fun title(no: String) = "T $no"
    override fun forOrder(orderNo: String) = "O $orderNo"
    override fun issuedAt(at: String) = "I $at"
    override fun orderTotal(amount: String) = "TOTAL $amount"
    override fun refundedBefore(amount: String) = "BEFORE $amount"
    override fun thisRefund(amount: String) = "THIS $amount"
    override fun refundedAfter(amount: String) = "AFTER $amount"
    override fun net(amount: String) = "NET $amount"
    override val cash = "M cash"
    override val hesabpay = "M hesabpay"
    override val closed = "CLOSED"
    override fun reason(text: String) = "WHY $text"
    override fun printedAt(at: String) = "P $at"
  }

  private val first = OrderRefundSlip.Receipt("71-R1", 71, 1_000, "cash", false, 125_000, 0, 30_000, 30_000, 95_000, "کمبود")
  private val second = OrderRefundSlip.Receipt("71-R2", 71, 2_000, "hesabpay", true, 125_000, 30_000, 20_000, 50_000, 75_000)

  private fun texts(r: OrderRefundSlip.Receipt) = OrderRefundSlip.rows(r, Tx, 9_000, { "t$it" })?.map { it.text }

  @Test
  fun firstRefundShowsTotalThisRefundAndCorrectAmountOnly() {
    assertEquals(
      listOf("T 71-R1", "O 71", "I t1000", OrderSlip.RULE, "TOTAL 1,250", "THIS 300", "M cash", OrderSlip.RULE, "NET 950", "WHY کمبود", "P t9000"),
      texts(first),
    )
  }

  @Test
  fun laterRefundAlsoShowsRefundedBeforeAndAfterAndClosedWhenFinal() {
    assertEquals(
      listOf("T 71-R2", "O 71", "I t2000", OrderSlip.RULE, "TOTAL 1,250", "BEFORE 300", "THIS 200", "M hesabpay", "AFTER 500",
        OrderSlip.RULE, "NET 750", "CLOSED", "P t9000"),
      texts(second),
    )
  }

  @Test
  fun trainingMarkSitsUnderTheTitle() {
    val rows = OrderRefundSlip.rows(first, Tx, 9_000, { "t$it" }, mark = " آموزشی ")!!
    assertEquals("آموزشی", rows[1].text)
    assertEquals(OrderSlip.Kind.HEAD, rows[1].kind)
  }

  @Test
  fun inconsistentReceiptPrintsNothing() {
    assertTrue(OrderRefundSlip.consistent(first))
    assertTrue(OrderRefundSlip.consistent(second))
    //  پیش + همین ≠ پس
    assertNull(texts(second.copy(refundedAfterMinor = 49_999)))
    //  جمع − پس ≠ درست
    assertNull(texts(first.copy(netMinor = 95_001)))
    //  شمارهٔ سفارشِ دیگر، شمارهٔ بی‌قالب، مبلغِ صفر و برگشتِ بیش از جمع
    assertFalse(OrderRefundSlip.consistent(first.copy(no = "72-R1")))
    assertFalse(OrderRefundSlip.consistent(first.copy(no = "71-1")))
    assertFalse(OrderRefundSlip.consistent(first.copy(amountMinor = 0, refundedAfterMinor = 0, netMinor = 125_000)))
    assertFalse(OrderRefundSlip.consistent(first.copy(amountMinor = 130_000, refundedAfterMinor = 130_000, netMinor = -5_000)))
  }
}
