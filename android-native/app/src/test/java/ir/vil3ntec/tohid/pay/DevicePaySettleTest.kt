package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.pay.PaymentRow
import ir.vil3ntec.tohid.core.terminal.DeviceAttempt
import ir.vil3ntec.tohid.core.terminal.DevicePay
import ir.vil3ntec.tohid.core.terminal.DeviceReceipt
import ir.vil3ntec.tohid.data.PayFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-08 — «تأییدِ دستگاه» ⇐ تسویهٔ فروش (همان راهِ گوشی و کامپیوتر): فقط تأییدِ کامل و برابر فروش را می‌بندد؛ ردیف
 *  `device` و `confirmed` است و ردیفِ دستیِ پنل دست نمی‌خورد؛ «نیازمندِ بررسی»، فاکتورِ دیگر و مبلغِ ناجور هرگز.
 */
class DevicePaySettleTest {

  private val a = DeviceAttempt("sale1", 145_000, "dev-sale1-abcdefghijkm", until = 10_000)

  private fun approved(x: DeviceAttempt = a, amount: Long = x.amountMinor) =
    DevicePay.verdict(x, DeviceReceipt(x.key, x.saleKey, amount, "AFN", DeviceReceipt.Status.APPROVED, "778899", "•••• 1234"))

  @Test fun `تأییدِ دستگاه کلِ فاکتور را با ردیفِ device می‌بندد`() {
    val s = (DevicePaySettle.settle(1450.0, null, "sale1", a, approved()) as PayFlow.Outcome.Ok).value
    assertEquals("paid", s.status)
    val row = s.payments.single()
    assertEquals(listOf("afpay_card", "card", "778899 (•••• 1234)", "confirmed", "device"), listOf(row.method, row.kind, row.ref, row.status, row.verification))
    assertEquals(145_000L, row.amount)
    assertEquals(0.0, PayFlow.saleFields(s).remaining, 0.0)
  }

  @Test fun `با پنلِ ترکیبی فقط مانده؛ ردیفِ دستیِ همان روش دست نمی‌خورد`() {
    val draft = PayFlow.Draft(total = 1450.0, cash = "200", rows = listOf(PayFlow.DraftRow("afpay_card", "MANUAL-1", "250")))
    val rest = a.copy(amountMinor = 100_000)
    val s = (DevicePaySettle.settle(1450.0, draft, "sale1", rest, approved(rest)) as PayFlow.Outcome.Ok).value
    assertEquals(listOf("cash" to 20_000L, "afpay_card" to 25_000L, "afpay_card" to 100_000L), s.payments.map { it.method to it.amount })
    assertEquals(listOf("pending" to "manual", "confirmed" to "device"), s.payments.drop(1).map { it.status to it.verification })
  }

  @Test fun `بررسی، فاکتورِ دیگر یا مبلغِ ناجور فروش را نمی‌بندد`() {
    fun code(o: PayFlow.Outcome<*>) = (o as PayFlow.Outcome.Bad).problem.code
    //  دستگاه مبلغِ دیگری گرفت ⇒ «نیازمندِ بررسی» ⇒ بسته نمی‌شود
    assertEquals("NOT_CONFIRMED", code(DevicePaySettle.settle(1450.0, null, "sale1", a, approved(amount = 14_500))))
    assertEquals("NOT_CONFIRMED", code(DevicePaySettle.settle(1450.0, null, "sale1", a, DevicePay.Verdict.Waiting)))
    assertEquals("OTHER_SALE", code(DevicePaySettle.settle(1450.0, null, "sale2", a, approved())))
    //  سبد پس از تلاش عوض شد (مانده دیگر همان نیست)
    assertEquals("TOTAL_MISMATCH", code(DevicePaySettle.settle(1500.0, null, "sale1", a, approved())))
    assertEquals("TOTAL_MISMATCH", code(DevicePaySettle.settle(1450.0, PayFlow.Draft(total = 1450.0, cash = "2000"), "sale1", a, approved())))
    assertTrue(DevicePaySettle.settle(1450.0, null, "sale1", a, approved()) is PayFlow.Outcome.Ok)
  }

  /** حکمِ تلاشِ دیگری (همان فاکتور و مبلغ) یا ارزِ دیگر فروش را نمی‌بندد — پیش از این فقط کلیدِ فاکتورِ تلاش سنجیده می‌شد */
  @Test fun `حکمِ کلیدِ دیگر یا ارزِ غیرِافغانی فروش را نمی‌بندد`() {
    fun code(o: PayFlow.Outcome<*>) = (o as PayFlow.Outcome.Bad).problem.code
    val other = a.copy(key = "dev-sale1-zzzzzzzzzzzz")
    assertEquals("OTHER_KEY", code(DevicePaySettle.settle(1450.0, null, "sale1", a, approved(other))))
    val usd = a.copy(currency = "USD")
    val vUsd = DevicePay.verdict(usd, DeviceReceipt(usd.key, usd.saleKey, usd.amountMinor, "USD", DeviceReceipt.Status.APPROVED, "778899"))
    assertTrue(vUsd is DevicePay.Verdict.Confirmed)
    assertEquals("CURRENCY_MISMATCH", code(DevicePaySettle.settle(1450.0, null, "sale1", usd, vUsd)))
  }

  /** `withRow` (کدِ مشترکِ حساب‌پی و دستگاه) فقط ردیفِ `confirmed` با سطحِ غیرِدستی را می‌نشاند */
  @Test fun `withRow ردیفِ در انتظار یا دستی را تأییدشده نمی‌کند`() {
    fun row(status: String, verification: String?) = PaymentRow("afpay_card", "card", 145_000, "778899", status, verification)
    fun code(o: PayFlow.Outcome<*>) = (o as? PayFlow.Outcome.Bad)?.problem?.code
    assertEquals("NOT_CONFIRMED", code(HesabPaySettle.withRow(1450.0, null, row("pending", "device"))))
    assertEquals("NOT_CONFIRMED", code(HesabPaySettle.withRow(1450.0, null, row("confirmed", "manual"))))
    assertEquals("NOT_CONFIRMED", code(HesabPaySettle.withRow(1450.0, null, row("confirmed", null))))
    assertEquals("NOT_CONFIRMED", code(HesabPaySettle.withRow(1450.0, null, row("needs_review", "device"))))
    for (by in listOf("device", "gateway", "cashier")) assertEquals(by, null, code(HesabPaySettle.withRow(1450.0, null, row("confirmed", by))))
  }
}
