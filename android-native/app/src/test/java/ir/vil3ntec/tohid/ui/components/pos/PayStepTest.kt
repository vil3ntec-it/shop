package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.pay.TillMethods
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  e-paystep — «گامِ پرداخت» (`PayStep`): هر پرداخت جدا تأیید می‌شود و فقط تسویهٔ کامل (`Done`) ثبت می‌شود.
 *  نقد (دقیق، بخشی، با پولِ پس، با گرد)، کارت (شمارهٔ تأیید اختیاری — صاحب‌کار ۱۴۰۵/۰۸/۲۶)، کیف پول (مرجع لازم)، نسیه (مشتری لازم)،
 *  پرداختِ چندتکه، برداشتنِ پرداخت و دوباره‌زدن پس از تسویه (هیچ پرداختِ دوم).
 */
class PayStepTest {

  private val cash = TillMethods.Tile(TillMethods.Kind.CASH, "cash")
  private val card = TillMethods.Tile(TillMethods.Kind.CARD, "afpay_card")
  private val wallet = TillMethods.Tile(TillMethods.Kind.WALLET, "e_money")
  private val credit = TillMethods.Tile(TillMethods.Kind.CREDIT)

  private fun step() = PayPanelState() to PayEntry()

  private fun done(r: PayStep.Result) = (r as? PayStep.Result.Done)?.settlement ?: throw AssertionError("انتظارِ تسویهٔ کامل: $r")

  @Test fun `نقدِ خالی همان نقدِ دقیق است و فروش را می‌بندد؛ زدنِ دوباره پرداختِ دوم نمی‌سازد`() {
    val (p, e) = step()
    PayStep.open(e, cash, p, 1080.0)
    assertEquals(108_000L, PayStep.dueMinor(p, 1080.0))
    val s = done(PayStep.confirm(e, p, 1080.0, null))
    assertEquals(listOf("cash" to 108_000L), s.payments.map { it.method to it.amount })
    assertEquals("1080", p.cash)
    //  دو زدن (یا برگشت و تأییدِ دوباره): همان تسویه، نقد دو برابر نمی‌شود
    PayStep.open(e, cash, p, 1080.0)
    val again = done(PayStep.confirm(e, p, 1080.0, null))
    assertEquals(s.payments, again.payments)
    assertEquals("1080", p.cash)
  }

  @Test fun `نقدِ بیشتر ⇒ پولِ پس؛ دکمه‌های تند — دقیق و گردهای بالا`() {
    val (p, e) = step()
    PayStep.open(e, cash, p, 1080.0)
    e.amount = "2000"
    assertEquals(92_000L, PayStep.changeMinor(e, p, 1080.0))
    val s = done(PayStep.confirm(e, p, 1080.0, null))
    assertEquals(92_000L, s.change)
    assertEquals(listOf(108_000L, 110_000L, 150_000L, 200_000L), PayStep.cashChips(108_000L))
    assertEquals(emptyList<Long>(), PayStep.cashChips(0L))
  }

  @Test fun `پرداختِ چندتکه — نقدِ بخشی و کارت برای باقی، بی شمارهٔ تأیید`() {
    val (p, e) = step()
    PayStep.open(e, cash, p, 1080.0)
    e.amount = "500"
    assertEquals(PayStep.Result.More, PayStep.confirm(e, p, 1080.0, null))
    assertEquals(58_000L, PayStep.dueMinor(p, 1080.0))
    PayStep.open(e, card, p, 1080.0)
    assertEquals("580", e.amount)
    val s = done(PayStep.confirm(e, p, 1080.0, null))
    assertEquals(listOf("cash" to 50_000L, "afpay_card" to 58_000L), s.payments.map { it.method to it.amount })
    assertEquals(null, s.payments.first { it.method == "afpay_card" }.ref)
    assertEquals(listOf("نقد" to 50_000L, "کارت AfPay" to 58_000L), PayStep.paid(p).map { it.name to it.amountMinor })
  }

  @Test fun `کارت با شمارهٔ تأیید؛ بیش از باقی پذیرفته نیست و هیچ ردیفی نمی‌ماند`() {
    val (p, e) = step()
    PayStep.open(e, card, p, 1080.0)
    e.amount = "2000"
    val bad = PayStep.confirm(e, p, 1080.0, null)
    assertTrue("$bad", bad is PayStep.Result.Bad)
    assertTrue(p.rows.isEmpty())
    e.amount = ""
    e.ref = "778899"
    val s = done(PayStep.confirm(e, p, 1080.0, null))
    assertEquals(listOf("afpay_card", "778899", "pending", "manual"), s.payments.single().let { listOf(it.method, it.ref, it.status, it.verification) })
  }

  @Test fun `کیف پول شمارهٔ مرجع می‌خواهد`() {
    val (p, e) = step()
    PayStep.open(e, wallet, p, 600.0)
    val bad = PayStep.confirm(e, p, 600.0, null) as PayStep.Result.Bad
    assertTrue(bad.message.contains("e& money"))
    assertTrue(p.rows.isEmpty())
    e.ref = "EM-1"
    assertEquals("e_money", done(PayStep.confirm(e, p, 600.0, null)).payments.single().method)
  }

  @Test fun `نسیه فقط با مشتریِ موجود؛ باقیِ پس از نقدِ بخشی به حسابش`() {
    val (p, e) = step()
    val ledger = ShopData(debtors = listOf(Debtor(id = "c1", name = "کریم")))
    PayStep.open(e, cash, p, 900.0)
    e.amount = "300"
    assertEquals(PayStep.Result.More, PayStep.confirm(e, p, 900.0, null, ledger))
    PayStep.open(e, credit, p, 900.0)
    val none = PayStep.confirm(e, p, 900.0, null, ledger) as PayStep.Result.Bad
    assertTrue(none.needCustomer)
    assertEquals(StrTillPay.creditNeedsCustomer, none.message)
    //  مشتریِ ناشناخته در دفتر ⇒ همان سدِ `CreditDebtor`
    assertTrue((PayStep.confirm(e, p, 900.0, "gone", ledger) as PayStep.Result.Bad).needCustomer)
    val s = done(PayStep.confirm(e, p, 900.0, "c1", ledger))
    assertEquals(60_000L, s.toAccount)
    assertEquals("credit", PayFlow.legacyMethod(s))
  }

  @Test fun `برداشتنِ یک پرداخت باقی را برمی‌گرداند؛ گردِ نقد فقط سهمِ نقد`() {
    val (p, e) = step()
    PayStep.open(e, cash, p, 1000.0)
    e.amount = "400"
    PayStep.confirm(e, p, 1000.0, null)
    assertEquals(60_000L, PayStep.dueMinor(p, 1000.0))
    PayStep.remove(p, -1)
    assertEquals(100_000L, PayStep.dueMinor(p, 1000.0))
    //  گردِ ۵ افغانی (PAY-09): ۱۰۸۳ ⇒ نقدِ دقیق ۱۰۸۵
    val (q, f) = step()
    q.cashRounding = 500
    PayStep.open(f, cash, q, 1083.0)
    assertEquals(108_500L, PayStep.exactCashMinor(q, 1083.0))
    val s = done(PayStep.confirm(f, q, 1083.0, null))
    assertEquals(200L, s.rounding)
  }
}
