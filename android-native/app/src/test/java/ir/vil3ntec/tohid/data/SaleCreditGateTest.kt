package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.credit.CreditGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-03 / S07 — سقفِ اعتبار در صندوق (`SalesEngine.record` با `Checkout.creditGate`).
 *
 *  پیش‌شرطِ S07: مشتری با سقفِ ۵٬۰۰۰ و ماندهٔ ۴٬۰۰۰ افغانی؛ فاکتورِ ۲٬۰۰۰ تماماً نسیه ⇐ «۱٬۰۰۰ بیش از
 *  سقف». بی تأیید هیچ چیز ثبت نمی‌شود؛ با تأییدِ مدیر فروش ثبت و ردِ `credit_over_limit` با نامِ مدیر و دلیل.
 */
class SaleCreditGateTest {

  private val item = Product(id = "p1", name = "کفش", unit = "جفت", purchasePrice = 1500.0, salePrice = 2000.0)
  private var seq = 0
  private fun newId() = "g${seq++}"
  private val d0 = ShopData(
    products = listOf(item),
    debtors = listOf(Debtor(id = "c1", name = "کریمی", creditLimit = 5000.0), Debtor(id = "c2", name = "بی‌سقف")),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = "c1", type = "give", amount = 4000.0, date = "2026-10-01"),
      DebtTransaction(id = "t2", debtorId = "c2", type = "give", amount = 90000.0, date = "2026-10-01"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 10.0)),
  )
  private val cart = listOf(SalesEngine.CartLine("p1", 1.0))
  private fun credit(who: String = "c1", gate: Boolean = true, approval: CreditGate.Approval? = null, paid: Double = 0.0) =
    SalesEngine.Checkout(
      seller = "صندوقدار", payment = SalesEngine.Payment.CREDIT, debtorId = who, paidAmount = paid,
      creditGate = gate, creditApproval = approval,
    )
  private fun record(c: SalesEngine.Checkout) = SalesEngine.record(d0, cart, c, "2026-10-30", 1_000, ::newId, saleId = "s1")
  private val manager = CreditGate.Approval("مدیرِ دکان", "manager", "c1", 200_000, "مشتریِ قدیمی، تا آخرِ ماه")

  @Test
  fun `S07 — بی تأیید رد با «۱٬۰۰۰ بیش از سقف» و دفتر دست‌نخورده`() {
    val r = record(credit())
    assertTrue(r is SalesEngine.Result.Failed)
    val msg = (r as SalesEngine.Result.Failed).message
    assertTrue(msg, msg.contains("بیش از سقفِ اعتبار") && msg.contains(SalesEngine.CREDIT_NEEDS_APPROVAL))
    assertTrue(msg, msg.startsWith(ir.vil3ntec.tohid.money(1000.0)))
    //  پیش‌سنجشِ صندوق همان حساب را دارد
    assertEquals(CreditGate.Over(500_000, 400_000, 200_000, 600_000), SalesEngine.creditOver(d0, cart, credit()))
  }

  @Test
  fun `S07 — با تأییدِ مدیر ثبت و سابقه با نامِ مدیر و دلیل`() {
    val d = (record(credit(approval = manager)) as SalesEngine.Result.Ok).data
    assertEquals(1, d.sales.size)
    assertEquals(2000.0, d.sales.single().debtGiven, 0.0)
    val a = d.auditLog.single { it.type == "credit_over_limit" }
    assertEquals("s1", a.refId)
    assertEquals("مدیرِ دکان", a.approvedBy)
    assertEquals("مشتریِ قدیمی، تا آخرِ ماه", a.reason)
    assertEquals("صندوقدار", a.by)
    assertEquals(1000.0, a.amount, 0.0)
    assertTrue(a.notes, a.notes.contains("کریمی") && a.notes.contains("تأیید: مدیرِ دکان"))
  }

  @Test
  fun `تأییدِ نادرست — شاگرد، مشتریِ دیگر، مبلغِ کمتر یا بی دلیل ⇐ رد`() {
    listOf(
      manager.copy(role = "staff"),
      manager.copy(debtorId = "c2"),
      manager.copy(addMinor = 199_999),
      manager.copy(reason = " "),
    ).forEach { bad -> assertTrue(bad.toString(), record(credit(approval = bad)) is SalesEngine.Result.Failed) }
  }

  @Test
  fun `بی پرچم، زیرِ سقف، نقدی و بی سقف — همان رفتارِ همیشه`() {
    //  پرچم خاموش: همان ثبت، بی ردِ تازه
    val off = (record(credit(gate = false)) as SalesEngine.Result.Ok).data
    assertTrue(off.auditLog.none { it.type == "credit_over_limit" })
    assertNull(SalesEngine.creditOver(d0, cart, credit(gate = false)))
    //  پیش‌پرداختِ ۱٬۰۰۰ ⇐ بردِ ۱٬۰۰۰ ⇐ درست برابرِ سقف ⇐ آزاد
    assertTrue(record(credit(paid = 1000.0)) is SalesEngine.Result.Ok)
    assertNull(SalesEngine.creditOver(d0, cart, credit(paid = 1000.0)))
    //  نقدی با مشتری بدهی نمی‌سازد ⇐ سقف سنجیده نمی‌شود
    val cash = credit().copy(payment = SalesEngine.Payment.CASH)
    assertTrue(record(cash) is SalesEngine.Result.Ok)
    assertNull(SalesEngine.creditOver(d0, cart, cash))
    //  مشتریِ بی سقف (ماندهٔ ۹۰٬۰۰۰) ⇐ آزاد
    assertTrue(record(credit(who = "c2")) is SalesEngine.Result.Ok)
  }

  @Test
  fun `پرچمِ خاموش همان شیء را می‌دهد که پیش از CRM-03`() {
    val before = SalesEngine.record(d0, cart, credit(gate = false).copy(creditApproval = null), "2026-10-30", 1_000, { "h" }, saleId = "s1")
    val after = SalesEngine.record(d0, cart, credit(gate = false, approval = manager), "2026-10-30", 1_000, { "h" }, saleId = "s1")
    assertEquals((before as SalesEngine.Result.Ok).data, (after as SalesEngine.Result.Ok).data)
    assertSame(d0.debtors, after.data.debtors)
  }
}
