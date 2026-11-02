package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  POS-23 — فروشنده برای کلِ فاکتور: نامِ حسابِ واردشده (`Checkout.seller`) روی فاکتور می‌نشیند و
 *  «فروشِ هر کاربر» (`ReportEngine.bySeller` و برگهٔ CSV ِ `sellers`) از همان فیلد جمع می‌زند.
 */
class SaleSellerTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", salePrice = 80.0)
  private val d = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "w", productId = "rice", units = 10.0)))

  private fun sale(id: String, seller: String, total: Double, date: String = "2026-11-01", status: String = "completed", discount: Double = 0.0) =
    Sale(id = id, seller = seller, finalTotal = total, discount = discount, date = date, status = status)

  @Test
  fun `فاکتور نامِ فروشنده را بی فاصلهٔ کناری می‌گیرد`() {
    val ok = SalesEngine.record(d, listOf(SalesEngine.CartLine("rice", 2.0)), SalesEngine.Checkout(seller = "  احمد "), "2026-11-01", 1_700_000_000_000, ::newId) as SalesEngine.Result.Ok
    assertEquals("احمد", ok.data.sales.single().seller)
    val none = SalesEngine.record(d, listOf(SalesEngine.CartLine("rice", 1.0)), SalesEngine.Checkout(), "2026-11-01", 1_700_000_000_001, ::newId) as SalesEngine.Result.Ok
    assertEquals("", none.data.sales.single().seller)
  }

  @Test
  fun `فروشِ هر کاربر — لغوشده و بیرونِ بازه نه، بی‌نام «نامعلوم»، بیشترین اول`() {
    val data = d.copy(
      sales = listOf(
        sale("a", "احمد", 100.0, discount = 5.0),
        sale("b", " احمد", 50.0),
        sale("c", "زهرا", 400.0),
        sale("d", "", 30.0),
        sale("e", "زهرا", 999.0, status = "cancelled"),
        sale("f", "احمد", 777.0, date = "2026-10-31"),
      ),
    )
    val rows = ReportEngine.bySeller(data, "2026-11-01", "2026-11-30")
    assertEquals(listOf("زهرا", "احمد", "نامعلوم"), rows.map { it.seller })
    val ahmad = rows.first { it.seller == "احمد" }
    assertEquals(2, ahmad.count)
    assertEquals(150.0, ahmad.net, 1e-9)
    assertEquals(5.0, ahmad.discount, 1e-9)
    assertEquals(400.0, rows.first().net, 1e-9)

    val csv = ReportCsv.of("sellers", data, "2026-11-01", "2026-11-30", "2026-11-30").rows
    assertEquals(listOf("فروشنده", "تعدادِ فاکتور", "فروشِ خالص", "تخفیف"), csv.first())
    assertEquals(listOf("احمد", "2", "150", "5"), csv[2])
  }
}
