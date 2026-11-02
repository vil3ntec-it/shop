package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockActions
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.data.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S25 — برگشت به تأمین‌کننده (PUR-08): خریدِ ۱۰ روغن به ۱۰۰ با پیش‌پرداختِ ۲۰۰، سپس برگشتِ ۳ تا با «سندِ کسر»
 *  (`PurchaseBook.supplierReturn`)، برگهٔ چاپیِ همان سند (`PurchaseBook.invoiceSheet`، دکمهٔ «چاپ» ِ خریدِ کامپیوتر) و بها
 *  روی پلِ موجودی (`StockLedger`).
 *
 *  ماندهٔ سناریو (pending، نه سرخ): برگهٔ چاپی عنوانِ «سندِ کسر» ندارد — همان «فاکتورِ خرید» با ردیفِ منفی است — و دکمهٔ
 *  چاپش فقط در خریدِ کامپیوتر است (گوشی برگهٔ چاپی ندارد).
 */
class ScenarioS25Test {

  private var n = 0
  private fun newId() = "r${++n}"
  private val today = "2026-11-02"
  private val now = 1_790_000_000_000L

  private fun bought(): ShopData {
    val d0 = ShopData(
      products = listOf(Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 100.0)),
      suppliers = listOf(Supplier(id = "s1", name = "شرکت")),
    )
    return (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p1", 10.0, 100.0)), 200.0, "2026-10-20", "", today, now, ::newId)
      as PurchaseBook.Result.Ok).data
  }

  @Test
  fun `S25 — موجودی و بدهی کم، سندِ کسر چاپی، بهای تمام‌شده طبقِ قاعده`() {
    val d = bought()
    assertEquals(800.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    val after = (PurchaseBook.supplierReturn(d, "s1", "p1", 3.0, 100.0, "خراب رسید", today, today, now + 1, ::newId)
      as PurchaseBook.Result.Ok).data

    //  موجودی کم می‌شود؛ بدهی کم می‌شود و صندوق نه
    assertEquals(7.0, ShopStore.stock(after, "p1"), 0.0)
    assertEquals(500.0, ShopStore.supplierDebt(after, "s1"), 0.0)
    assertEquals(d.supplierPayments, after.supplierPayments)

    //  سندِ کسر: همان برگهٔ چاپیِ خرید، با ردیفِ −۳ و جمعِ −۳۰۰
    val credit = PurchaseBook.invoices(after, "s1").single { it.isReturn }
    val sheet = PurchaseBook.invoiceSheet(after, credit)
    assertTrue(sheet.rows.any { row -> row.contains("روغن") && row.any { it.contains("3") } })
    assertEquals(-300.0, credit.total, 0.0)

    //  بها: فیِ خریدِ کالا و تاریخچهٔ قیمت دست نمی‌خورند؛ برگشت به بهای میانگین بیرون می‌رود و تطبیقِ بها درست است
    assertEquals(100.0, after.products.single().purchasePrice, 0.0)
    assertEquals(d.priceHistory, after.priceHistory)
    val view = StockLedger.replay(StockActions.setMode(after, true, "", today, 5, ::newId))
    val row = view.rows.single { it.type == "supplier_return" }
    assertEquals(30_000L, row.costMinor)
    assertTrue(StockLedger.reconcileAll(view))
  }
}
