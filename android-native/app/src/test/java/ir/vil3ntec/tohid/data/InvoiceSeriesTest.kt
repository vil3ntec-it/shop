package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  F0-03 — شمارهٔ فاکتور میان گوشی و کامپیوترِ یک دکان تکراری نمی‌شود.
 *
 *  هر دستگاه «سری سند»ِ خودش را از سرور می‌گیرد (`DocSeries`) و شمارهٔ
 *  نمایشی `{سری}-{شماره}` می‌شود. شمارنده همان شمارندهٔ هر دستگاه است
 *  (`nextInvoiceNo`)؛ فاکتورِ بی سری (قدیمی، وب) همان عدد را نشان می‌دهد.
 *  قرینهٔ سرور: `server/test/doc-series.test.js`.
 */
class InvoiceSeriesTest {

  private val rice = Product(id = "p1", name = "برنج", salePrice = 300.0)
  private val ahmad = Debtor(id = "d1", name = "احمد")
  private fun ledger(next: Int = 1) = ShopData(
    products = listOf(rice),
    debtors = listOf(ahmad),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0)),
    nextInvoiceNo = next,
  )

  private var seq = 0
  private fun newId(): String = "t${seq++}"
  private val cart = listOf(SalesEngine.CartLine("p1", 1.0))
  private val credit = SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = "d1")

  private fun sell(d: ShopData, series: String, checkout: SalesEngine.Checkout = SalesEngine.Checkout()): SalesEngine.Result.Ok {
    val r = SalesEngine.record(d, cart, checkout, today = "2026-10-13", now = 1_700_000_000_000, newId = ::newId, series = series)
    assertTrue("$r", r is SalesEngine.Result.Ok)
    return r as SalesEngine.Result.Ok
  }

  @Test
  fun `شمارهٔ نمایشی سری را جلوی شماره می‌گذارد و فاکتورِ بی سری همان عدد است`() {
    assertEquals("154", Sale(id = "s", invoiceNumber = 154).invoiceLabel())
    assertEquals("B1-154", Sale(id = "s", invoiceNumber = 154, series = "B1").invoiceLabel())
    assertEquals("154", Sale(id = "s", invoiceNumber = 154, series = " ").invoiceLabel())
    assertNull(Sale(id = "s", series = "B1").invoiceLabel())
  }

  @Test
  fun `گوشی و کامپیوتر هر دو به شمارهٔ ۵ رسیده‌اند — دو فاکتورِ جدا، نه دو «#۵»`() {
    val phone = sell(ledger(next = 5), series = "A1")
    val desk = sell(ledger(next = 5), series = "B1")
    val a = phone.data.sales.single()
    val b = desk.data.sales.single()
    assertEquals(5, a.invoiceNumber)
    assertEquals(5, b.invoiceNumber)
    assertEquals("A1-5", a.invoiceLabel())
    assertEquals("B1-5", b.invoiceLabel())
    assertNotEquals(a.invoiceLabel(), b.invoiceLabel())
    //  شمارندهٔ هر دستگاه همان‌طور یکی‌یکی بالا می‌رود
    assertEquals(6, phone.data.nextInvoiceNo)
  }

  @Test
  fun `شرحِ قرض و انبار و سابقه هم شمارهٔ نمایشی را دارند`() {
    val r = sell(ledger(next = 12), series = "B1", checkout = credit)
    assertEquals("فروش نسیه — فاکتور #B1-12", r.data.transactions.single().notes)
    assertEquals("فاکتور #B1-12", r.data.stockMovements.single().notes)
    assertTrue(r.data.auditLog.single().notes.startsWith("ثبت فروش فاکتور #B1-12 "))
  }

  @Test
  fun `بی سری (هنوز نرسیده) همان رفتارِ پیشین — شماره‌های گذشته عوض نمی‌شوند`() {
    val r = sell(ledger(next = 7), series = "", checkout = credit)
    val sale = r.data.sales.single()
    assertNull(sale.series)
    assertEquals("7", sale.invoiceLabel())
    assertEquals("فروش نسیه — فاکتور #7", r.data.transactions.single().notes)
  }

  @Test
  fun `فاکتورِ بی سری در Sync v1 فیلدِ series نمی‌سازد — هیچ opِ اضافه‌ای برای فاکتورهای قدیمی`() {
    val old = ShopData(sales = listOf(Sale(id = "s-old", invoiceNumber = 3)))
    val tree = LedgerDiff.toJson(old)
    val row = (tree["sales"] as kotlinx.serialization.json.JsonArray)[0] as kotlinx.serialization.json.JsonObject
    assertTrue("series نباید نوشته شود: $row", "series" !in row)
    //  سایهٔ ساخته‌شده پیش از این نسخه (بی کلیدِ series) ⇒ تفاضل صفر
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(tree), tree).size)

    //  و فاکتورِ سری‌دار سری‌اش را می‌برد و از دستگاهِ دیگر برمی‌گردد
    val withSeries = ShopData(sales = listOf(Sale(id = "s-new", invoiceNumber = 3, series = "B1")))
    val back = LedgerDiff.fromJson(LedgerDiff.toJson(withSeries))
    assertEquals("B1", back.sales.single().series)
  }
}
