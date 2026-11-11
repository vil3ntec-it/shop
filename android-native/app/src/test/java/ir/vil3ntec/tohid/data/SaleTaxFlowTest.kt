package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSaleTax
import ir.vil3ntec.tohid.core.sale.SaleTax
import ir.vil3ntec.tohid.ui.components.pos.PosSummary
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-37 — مالیاتِ فاکتور از صندوق تا ثبت، رسید، برگشت و پیش‌سنجیِ سقف: بی تنظیم (یا پرچمِ سرورِ خاموش) فاکتور
 *  بایت‌به‌بایت همان امروز؛ با تنظیم، مالیات روی (جمع − تخفیف) و یک ردیفِ جدای `taxMinor`، نهایی و پرداختیِ نقد با آن.
 */
class SaleTaxFlowTest {

  private val a = Product(id = "p1", name = "الف", unit = "عدد", purchasePrice = 30.0, salePrice = 500.0)
  private val b = Product(id = "p2", name = "ب", unit = "عدد", purchasePrice = 60.0, salePrice = 250.0)

  private fun ledger() = ShopData(
    products = listOf(a, b),
    warehouseEntries = listOf("p1", "p2").mapIndexed { i, p -> WarehouseEntry(id = "w$i", productId = p, units = 100.0) },
  )

  private var seq = 0
  private fun newId(): String = "t${seq++}"

  private fun sell(cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout): Sale {
    seq = 0
    val r = SalesEngine.record(ledger(), cart, checkout, today = "2026-11-02", now = 1_700_000_000_000, newId = ::newId)
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is SalesEngine.Result.Ok)
    return (r as SalesEngine.Result.Ok).data.sales.single()
  }

  private val cart = listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("p2", 1.0)) // ۱۰۰۰ + ۲۵۰
  private val json = Json { encodeDefaults = true }
  private val tenPct = SaleTax.Config(SaleTax.Mode.INVOICE, 1000)

  @After fun reset() { SaleTaxWiring.serverFlag = false }

  @Test
  fun `بی تنظیم همان فاکتورِ امروز است — بی ردیفِ مالیات و بایت‌به‌بایت همان`() {
    val today = sell(cart, SalesEngine.Checkout())
    assertEquals(1250.0, today.finalTotal, 0.0)
    assertNull(today.taxMinor)
    val text = json.encodeToString(Sale.serializer(), today)
    assertFalse(text.contains("taxMinor"))
    val t = SalesEngine.totals(ledger(), cart, SalesEngine.Checkout())
    assertEquals(0.0, t.tax, 0.0)
    assertFalse(t.taxOutOfRange)
    assertEquals(PosSummary.rows(t).size, 1)
  }

  @Test
  fun `فاکتور ۱۰٪ پس از تخفیف — ردیفِ جدا، نهایی و پرداختیِ نقد با مالیات`() {
    val checkout = SalesEngine.Checkout(discountType = SalesEngine.DiscountType.AMOUNT, discountValue = 50.0, tax = tenPct)
    val t = SalesEngine.totals(ledger(), cart, checkout)
    assertEquals(120.0, t.tax, 0.0)                // (۱۲۵۰ − ۵۰) × ۱۰٪
    assertEquals(1320.0, t.finalTotal, 0.0)
    val s = sell(cart, checkout)
    assertEquals(1250.0, s.total, 0.0)
    assertEquals(50.0, s.discount, 0.0)
    assertEquals(12_000L, s.taxMinor)
    assertEquals(1320.0, s.finalTotal, 0.0)
    assertEquals(1320.0, s.paidAmount, 0.0)
    assertEquals(0.0, s.remaining, 0.0)
    //  رسید و خلاصهٔ صندوق: ردیفِ «مالیات» پیش از «مبلغ نهایی»
    assertTrue(ReceiptModel.totals(s).any { it.label == StrSaleTax.line && it.amount == 120.0 })
    assertTrue(PosSummary.rows(t).any { it.label == StrSaleTax.line })
  }

  @Test
  fun `ردیف — نرخِ هر کالا (۰ = معاف) و گردِ نقدی روی نهاییِ با مالیات`() {
    val perLine = SaleTax.Config(SaleTax.Mode.LINE, 1000, mapOf("p2" to 0))
    val s = sell(cart, SalesEngine.Checkout(tax = perLine, cashRounding = 1_000))
    assertEquals(10_000L, s.taxMinor)              // فقط ۱۰۰۰ ِ الف
    assertEquals(1350.0, s.finalTotal, 0.0)        // ۱۲۵۰ + ۱۰۰؛ گردِ ۱۰ افغانی بی اثر
    assertNull(s.roundingMinor)
  }

  @Test
  fun `پرچمِ سرور و تنظیمِ دکان هر دو لازم‌اند`() {
    assertNull(SaleTaxWiring.active(tenPct))
    SaleTaxWiring.serverFlag = true
    assertEquals(tenPct, SaleTaxWiring.active(tenPct))
    assertNull(SaleTaxWiring.active(null))
    assertNull(SaleTaxWiring.active(SaleTax.Config(SaleTax.Mode.INVOICE, 0)))
  }

  @Test
  fun `برگشتِ کامل سهمِ مالیاتِ همان قلم را هم پس می‌دهد — همان سقفِ سرور`() {
    val s = sell(cart, SalesEngine.Checkout(tax = tenPct))
    assertEquals(1.1, SalesEngine.paidRatio(s), 1e-12)
    val i = SaleItem(id = "i1", saleId = s.id, productId = "p1", quantity = 2.0, unitPrice = 500.0, totalPrice = 1000.0)
    assertEquals(1100.0, SalesEngine.returnAmount(s, i, 2.0), 0.0)
    //  فاکتورِ بی مالیات همان سهمِ پیشین
    assertEquals(1.0, SalesEngine.paidRatio(sell(cart, SalesEngine.Checkout())), 0.0)
  }

  @Test
  fun `پیش‌سنجیِ سقف همان saleOf ِ سرور — مالیات فقط با پرچم در پایهٔ تخفیف`() {
    val s = sell(cart, SalesEngine.Checkout(tax = tenPct))
    assertEquals(0L, SaleCapGate.sale(s).taxMinor)
    SaleTaxWiring.serverFlag = true
    assertEquals(12_500L, SaleCapGate.sale(s).taxMinor)
    assertEquals(0L, ir.vil3ntec.tohid.core.perm.SaleCaps.basis(0, 125_000, 137_500, 0, 0, 12_500).gapMinor)
  }
}
