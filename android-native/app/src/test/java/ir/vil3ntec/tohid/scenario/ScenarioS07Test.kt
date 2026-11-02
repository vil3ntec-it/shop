package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S07 ⌨ — فروشِ قرضیِ بیش از سقف (CRM-03، STF-02، POS-21): مشتری با سقفِ ۵٬۰۰۰ و ماندهٔ ۴٬۰۰۰، فاکتورِ ۲٬۰۰۰ تماماً
 *  نسیه. سر تا ته: پیش‌سنجشِ صندوق (`SalesEngine.creditOver`) ⇐ «درخواستِ تأیید» فقط برای مدیر/صاحب
 *  (`CreditGate.canApprove`) ⇐ PIN ِ مدیر (`ApproverVault.verify`، همان کاری که `CreditApprovalSheet` می‌کند) ⇐ ثبت.
 *
 *  همهٔ سطرهای «نتیجه» این‌جا سبز است؛ در برنامه پشتِ تنظیمِ خاموشِ دکان `ShopPolicy.creditGate`. پنجرهٔ PIN
 *  (`CreditApprovalSheet`، `CreditApprovalDialog`) فقط در CI کامپایل می‌شود.
 */
class ScenarioS07Test {

  private var seq = 0
  private fun newId() = "c${seq++}"

  private val shoes = Product(id = "p1", name = "کفش", unit = "جفت", purchasePrice = 1500.0, salePrice = 2000.0)
  private val d0 = ShopData(
    products = listOf(shoes),
    debtors = listOf(Debtor(id = "c1", name = "کریمی", creditLimit = 5000.0)),
    transactions = listOf(DebtTransaction(id = "t1", debtorId = "c1", type = "give", amount = 4000.0, date = "2026-10-01")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 10.0)),
  )
  private val cart = listOf(SalesEngine.CartLine("p1", 1.0))
  private val manager = ApproverVault.create("usr_m", "مدیرِ دکان", "manager", "482917", iterations = 1000)

  /** F9 و «۰» در پنلِ پرداخت: همه نسیه */
  private fun credit(approval: CreditGate.Approval? = null) = SalesEngine.Checkout(
    seller = "صندوقدار", payment = SalesEngine.Payment.CREDIT, debtorId = "c1", paidAmount = 0.0,
    creditGate = true, creditApproval = approval,
  )

  private fun post(c: SalesEngine.Checkout) = SalesEngine.record(d0, cart, c, "2026-11-02", 1_000, ::newId, saleId = "s1")

  /** همان `CreditApprovalSheet`: PIN درست ⇒ تأییدِ همین مشتری تا همین برد با دلیل؛ غلط ⇒ هیچ */
  private fun pin(pin: String, reason: String): CreditGate.Approval? {
    val over = SalesEngine.creditOver(d0, cart, credit()) ?: return null
    val out = ApproverVault.verify(manager, pin)
    return if (out.ok) CreditGate.Approval(manager.name, manager.role, "c1", over.addMinor, reason) else null
  }

  @Test
  fun `S07 — پیامِ «۱٬۰۰۰ بیش از سقف» و «درخواستِ تأیید» فقط برای مدیر و صاحب`() {
    val over = SalesEngine.creditOver(d0, cart, credit())!!
    assertEquals(100_000L, over.excessMinor)
    val msg = (post(credit()) as SalesEngine.Result.Failed).message
    assertTrue(msg, msg.startsWith(ir.vil3ntec.tohid.money(1000.0)) && msg.contains("بیش از سقفِ اعتبار"))
    assertTrue(msg, msg.contains(SalesEngine.CREDIT_NEEDS_APPROVAL))
    assertTrue(CreditGate.canApprove("manager"))
    assertTrue(CreditGate.canApprove("owner"))
    assertFalse(CreditGate.canApprove("staff"))
  }

  @Test
  fun `S07 — با PIN درست فروش ثبت و سابقه نامِ مدیر و دلیل دارد`() {
    val ok = (post(credit(pin("482917", "مشتریِ قدیمی، تا آخرِ ماه"))) as SalesEngine.Result.Ok).data
    assertEquals(1, ok.sales.size)
    assertEquals(2000.0, ok.sales.single().debtGiven, 0.0)
    val a = ok.auditLog.single { it.type == "credit_over_limit" }
    assertEquals("مدیرِ دکان", a.approvedBy)
    assertEquals("مشتریِ قدیمی، تا آخرِ ماه", a.reason)
  }

  @Test
  fun `S07 — با PIN غلط یا Esc هیچ چیز ثبت نمی‌شود`() {
    assertNull(pin("000000", "دلیل"))
    //  PIN غلط ⇒ بی تأیید؛ Esc ⇒ پنجره بسته و بی تأیید. ثبت رد می‌شود و دفتری برنمی‌گردد که نوشته شود
    for (c in listOf(credit(pin("000000", "دلیل")), credit(null))) {
      val r = post(c)
      assertTrue(r.toString(), r is SalesEngine.Result.Failed)
    }
  }
}
