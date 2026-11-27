package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.purchase.InvoiceOcr
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PUR-12/AI-10 — ردیف‌های تأییدشدهٔ «از عکسِ فاکتور» در فرمِ «ثبت خرید» ِ گوشی (`PurchaseFormModel.withAccepted`): فقط پیش‌نویسِ
 *  همان فرم عوض می‌شود (ردیفِ خالیِ نخست، سپس ردیف‌های خالی، سپس ردیفِ تازه تا سقف)؛ ثبت همان `PurchaseBook.record` ِ «ثبت» است.
 */
class PurchaseFormOcrTest {

  private val rice = Product(id = "rice", name = "برنج", unit = "بسته", purchasePrice = 950.0, salePrice = 1100.0)
  private val tea = Product(id = "tea", name = "چای", unit = "کیلو", purchasePrice = 420.0, salePrice = 500.0)
  private val d = ShopData(products = listOf(rice, tea), suppliers = listOf(Supplier(id = "s1", name = "البرز")))
  private val base = PurchaseFormModel.initial("s1", "2026-11-16")

  @Test fun confirmedRowsFillTheEmptyFormThenNewRows() {
    val (f, n) = PurchaseFormModel.withAccepted(
      base, listOf(InvoiceOcr.Accepted("rice", 10.0, 950.0), InvoiceOcr.Accepted("tea", 2.5, 420.0)), d,
    )
    assertEquals(2, n)
    assertEquals(Triple("rice", "10", "950"), Triple(f.productId, f.quantity, f.price))
    assertNull("واحدِ پایه", f.unitName)
    assertEquals(listOf(PurchaseRow("tea", "2.5", "420")), f.more)
    //  همان ورودیِ «ثبت»: دو ردیفِ دفتر، جمع ۱۰۶۰۰ (۲٫۵ × ۴۲۰ = ۱۰۵۰)
    assertEquals(9500.0 + 1050.0, PurchaseFormModel.total(f), 0.0)
    val lines = PurchaseFormModel.lines(f) { id -> d.products.first { it.id == id }.unit }
    assertEquals(listOf("rice" to 10.0, "tea" to 2.5), lines.map { it.productId to it.quantity })
    var k = 0
    assertTrue(PurchaseBook.record(d, "s1", lines, 0.0, f.date, "", f.date, 1L, { "o${k++}" }) is PurchaseBook.Result.Ok)
  }

  @Test fun filledRowsStayAndBlankAddedRowsAreReused() {
    val typed = PurchaseFormModel.addRow(base.copy(productId = "tea", quantity = "1", price = "400"))
    val (f, n) = PurchaseFormModel.withAccepted(typed, listOf(InvoiceOcr.Accepted("rice", 3.0, 900.0)), d)
    assertEquals(1, n)
    assertEquals(Triple("tea", "1", "400"), Triple(f.productId, f.quantity, f.price))
    assertEquals(listOf(PurchaseRow("rice", "3", "900")), f.more)
  }

  @Test fun recognizedAfnPricesCannotBeAppliedToAForeignCurrencyDraft() {
    val foreign = PurchaseFormModel.setCurrency(base, "USD")
    val (form, added) = PurchaseFormModel.withAccepted(
      foreign, listOf(InvoiceOcr.Accepted("rice", 1.0, 950.0)), d,
    )
    assertEquals(0, added)
    assertEquals(foreign, form)
  }

  @Test fun unknownProductsAreSkippedAndAcceptedRowsAreKept() {
    val (f, n) = PurchaseFormModel.withAccepted(base, listOf(InvoiceOcr.Accepted("gone", 1.0, 1.0)), d)
    assertEquals(0, n)
    assertEquals(base, f)
    val many = (1..60).map { InvoiceOcr.Accepted(if (it % 2 == 0) "rice" else "tea", it.toDouble(), 10.0) }
    val (g, m) = PurchaseFormModel.withAccepted(base, many, d)
    assertEquals(many.size, m)
    assertEquals(many.size - 1, g.more.size)
  }
}
