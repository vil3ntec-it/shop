package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.gateway.PaymentIntent
import ir.vil3ntec.tohid.data.PayFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  حساب‌پی ⇐ تسویهٔ فروش (PAY-21، PAY-08): ردیفِ سرور، کلِ مبلغ بی پنل و باقی‌مانده با پنلِ ترکیبی؛
 *  هر ناجوری ⇐ فروش بسته نمی‌شود.
 */
class HesabPaySettleTest {

  private fun intent(status: String = "paid", amount: Long = 145000, ref: String = "sale1", row: Boolean = true): PaymentIntent =
    PaymentIntent.fromMap(
      mapOf(
        "id" to "pi_1", "kind" to "pos_sale", "refId" to ref, "status" to status,
        "amount" to amount.toDouble(), "currency" to "AFN", "checkoutUrl" to "https://pay.example/c/pi_1",
        "providerRef" to "TX-9",
        "payment" to if (row) mapOf(
          "method" to "hesabpay", "kind" to "wallet", "amount" to amount.toDouble(), "ref" to "TX-9",
          "status" to "confirmed", "verification" to "gateway", "intentId" to "pi_1",
        ) else null,
      ),
    )!!

  @Test fun `بی پنل حساب‌پی کلِ فاکتور را با ردیفِ تأییدشدهٔ سرور می‌بندد`() {
    assertEquals(HesabPaySettle.Due.Amount(145000), HesabPaySettle.due(1450.0, null))
    val s = (HesabPaySettle.settle(1450.0, null, "sale1", intent()) as PayFlow.Outcome.Ok).value
    assertEquals("paid", s.status)
    val row = s.payments.single()
    assertEquals(listOf("hesabpay", "wallet", "TX-9", "confirmed", "gateway"), listOf(row.method, row.kind, row.ref, row.status, row.verification))
    assertEquals(145000L, row.amount)
    //  فروشِ کیف‌پولی «نقد» نیست و نقدی به دخل نمی‌آورد
    val f = PayFlow.saleFields(s)
    assertEquals("wallet", f.paymentMethod)
    assertEquals(0.0, f.remaining, 0.0)
    assertEquals(0L, f.changeMinor)
  }

  @Test fun `با پنلِ ترکیبی حساب‌پی فقط باقی‌مانده را می‌پردازد`() {
    val draft = PayFlow.Draft(total = 1450.0, cash = "450")
    assertEquals(HesabPaySettle.Due.Amount(100000), HesabPaySettle.due(1450.0, draft))
    val s = (HesabPaySettle.settle(1450.0, draft, "sale1", intent(amount = 100000)) as PayFlow.Outcome.Ok).value
    assertEquals("paid", s.status)
    assertEquals(listOf("cash" to 45000L, "hesabpay" to 100000L), s.payments.map { it.method to it.amount })
    assertEquals("confirmed", s.payments.last().status)
    assertEquals("mixed", PayFlow.legacyMethod(s))
  }

  @Test fun `ردیفِ دستیِ همان روش دست نمی‌خورد`() {
    val draft = PayFlow.Draft(total = 1450.0, rows = listOf(PayFlow.DraftRow("hesabpay", "MANUAL-1", "450")))
    val s = (HesabPaySettle.settle(1450.0, draft, "sale1", intent(amount = 100000)) as PayFlow.Outcome.Ok).value
    assertEquals(listOf("pending" to "manual", "confirmed" to "gateway"), s.payments.map { it.status to it.verification })
  }

  @Test fun `چیزی نمانده ⇐ حساب‌پی پیشنهاد نمی‌شود`() {
    assertEquals(HesabPaySettle.Due.None, HesabPaySettle.due(0.0, null))
    assertEquals(HesabPaySettle.Due.None, HesabPaySettle.due(1450.0, PayFlow.Draft(total = 1450.0, cash = "2000")))
    assertTrue(HesabPaySettle.due(1450.0, PayFlow.Draft(total = 1450.0, cash = "abc")) is HesabPaySettle.Due.Bad)
  }

  @Test fun `ناجوری فروش را نمی‌بندد`() {
    fun code(o: PayFlow.Outcome<*>) = (o as PayFlow.Outcome.Bad).problem.code
    assertEquals("NOT_PAID", code(HesabPaySettle.settle(1450.0, null, "sale1", intent(status = "pending"))))
    assertEquals("NOT_PAID", code(HesabPaySettle.settle(1450.0, null, "sale1", intent(status = "expired"))))
    assertEquals("OTHER_SALE", code(HesabPaySettle.settle(1450.0, null, "sale2", intent())))
    assertEquals("TOTAL_MISMATCH", code(HesabPaySettle.settle(1500.0, null, "sale1", intent())))
    //  سبد پس از پرداخت عوض شد ⇐ باقی‌مانده دیگر همان نیست
    assertEquals("TOTAL_MISMATCH", code(HesabPaySettle.settle(1450.0, PayFlow.Draft(1450.0, cash = "100"), "sale1", intent())))
  }

  @Test fun `سرورِ بی ردیفِ آماده ⇐ همان شکل از خودِ قصد`() {
    val row = HesabPaySettle.rowOf(intent(row = false))!!
    assertEquals(listOf("hesabpay", "wallet", "TX-9", "confirmed", "gateway", "pi_1"), listOf(row.method, row.kind, row.ref, row.status, row.verification, row.intentId))
  }
  @Test fun `حالتِ ساده فقط با پول رسیدِ صندوقدار ردیفِ تأییدِ صندوقدار با کدِ فاکتور می‌سازد`() {
    val s = (HesabPaySettle.settleSimple(1450.0, null, "HP-AB12-CD34") as PayFlow.Outcome.Ok).value
    assertEquals("paid", s.status)
    val row = s.payments.single()
    assertEquals(listOf("hesabpay", "wallet", "HP-AB12-CD34", "confirmed", "cashier"), listOf(row.method, row.kind, row.ref, row.status, row.verification))
    assertEquals(145000L, row.amount)
    //  پنلِ ترکیبی: فقط باقی‌مانده، نقد سرِ جایش
    val mixed = (HesabPaySettle.settleSimple(1450.0, PayFlow.Draft(total = 1450.0, cash = "450"), "HP-1") as PayFlow.Outcome.Ok).value
    assertEquals(listOf(45000L, 100000L), mixed.payments.map { it.amount })
    assertEquals("cashier", mixed.payments.last().verification)
    //  بی کد یا بی مبلغ ⇐ فروش بسته نمی‌شود
    assertEquals("REF_REQUIRED", (HesabPaySettle.settleSimple(1450.0, null, "  ") as PayFlow.Outcome.Bad).problem.code)
    assertTrue(HesabPaySettle.settleSimple(0.0, null, "HP-1") is PayFlow.Outcome.Bad)
  }
  @Test fun `حالتِ ساده اثرِ انگشتِ حسابِ گیرندهٔ سرور را روی ردیفِ حساب‌پی و ردیفِ فروش می‌گذارد`() {
    val s = (HesabPaySettle.settleSimple(1450.0, PayFlow.Draft(total = 1450.0, cash = "450"), "HP-1", " hp1_abc ") as PayFlow.Outcome.Ok).value
    assertEquals(listOf(null, "hp1_abc"), s.payments.map { it.payee })
    //  ردیفِ ذخیره‌شدهٔ فروش همان را دارد (گزارشِ «پول به کدام حساب رفت» در سرور)
    val sale = ir.vil3ntec.tohid.data.SalePayment("hesabpay", "wallet", 100000L, "confirmed", "HP-1", "cashier", payee = "hp1_abc")
    val json = kotlinx.serialization.json.Json.encodeToString(ir.vil3ntec.tohid.data.SalePayment.serializer(), sale)
    assertTrue(json, json.contains("\"payee\":\"hp1_abc\""))
    //  ردیفِ بی گیرنده (نقد) فیلد را اصلاً نمی‌فرستد
    val cash = kotlinx.serialization.json.Json.encodeToString(ir.vil3ntec.tohid.data.SalePayment.serializer(),
      ir.vil3ntec.tohid.data.SalePayment("cash", "cash", 100L, "confirmed"))
    assertTrue(cash, !cash.contains("payee"))
    assertEquals(null, (HesabPaySettle.settleSimple(1450.0, null, "HP-1", "  ") as PayFlow.Outcome.Ok).value.payments.single().payee)
  }
}
