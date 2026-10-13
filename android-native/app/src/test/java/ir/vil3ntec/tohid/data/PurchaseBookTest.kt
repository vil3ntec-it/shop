package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** فاکتورِ خریدِ چندردیفه — انبار، بدهیِ تأمین‌کننده و صورت‌حساب */
class PurchaseBookTest {
  private var seq = 0
  private fun newId() = "x${seq++}"
  private val d0 = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 100.0),
      Product(id = "p2", name = "روغن", unit = "عدد", purchasePrice = 500.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "عمده‌فروشی")),
  )

  @Test fun `چند ردیف زیرِ یک فاکتور، پرداخت پخش و انبار پر`() {
    val r = PurchaseBook.record(
      d0, "s1",
      listOf(PurchaseBook.Line("p1", 10.0, 120.0, expiryDate = "2027-01-01"), PurchaseBook.Line("p2", 2.0, 550.0)),
      paid = 1500.0, date = "2026-10-10", notes = "", today = "2026-10-10", now = 1, newId = ::newId,
    ) as PurchaseBook.Result.Ok
    val d = r.data
    assertEquals(1, r.invoiceNo)
    assertEquals(2, d.purchases.size)
    assertTrue(d.purchases.all { it.invoiceNo == 1 })
    //  جمع ۱۲۰۰ + ۱۱۰۰ = ۲۳۰۰؛ پرداخت ۱۵۰۰ ⇒ ردیفِ اول کامل، دوم ۳۰۰
    assertEquals(listOf(1200.0, 300.0), d.purchases.map { it.paidAmount })
    assertEquals(800.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    assertEquals(10.0, ShopStore.stock(d, "p1"), 0.0)
    assertEquals("2027-01-01", d.warehouseEntries.first { it.productId == "p1" }.expiryDate)
    assertEquals(120.0, d.products.first { it.id == "p1" }.purchasePrice, 0.0)

    val inv = PurchaseBook.invoices(d).single()
    assertEquals(2300.0, inv.total, 0.0)
    assertEquals(800.0, inv.debt, 0.0)

    val led = PurchaseBook.ledger(d, "s1")
    assertEquals(listOf(2300.0, 800.0), led.map { it.balance })
    assertEquals(2, PurchaseBook.nextNo(d))
  }

  @Test fun `ردیفِ خراب ⇒ هیچ چیز ثبت نمی‌شود`() {
    val r = PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 0.0, 10.0)), 0.0, "", "", "2026-10-10", 1, ::newId)
    assertTrue(r is PurchaseBook.Result.Failed)
    val r2 = PurchaseBook.record(d0, "nope", listOf(PurchaseBook.Line("p1", 1.0, 10.0)), 0.0, "", "", "2026-10-10", 1, ::newId)
    assertTrue(r2 is PurchaseBook.Result.Failed)
  }

  @Test fun `پرداختِ بعدی در صورت‌حساب`() {
    var d = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 5.0, 100.0)), 0.0, "2026-10-01", "", "2026-10-01", 1, ::newId) as PurchaseBook.Result.Ok).data
    d = d.copy(supplierPayments = d.supplierPayments + SupplierPayment("sp", "s1", 200.0, "2026-10-05", "", 2))
    assertEquals(listOf(500.0, 300.0), PurchaseBook.ledger(d, "s1").map { it.balance })
    assertEquals(300.0, PurchaseBook.suppliers(d).single().balance, 0.0)
    assertEquals(5, PurchaseBook.invoiceSheet(d, PurchaseBook.invoices(d).single()).rows.size)
  }
}
