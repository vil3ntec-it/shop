package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** عددهای داشبورد — همه از خودِ دفتر */
class DashboardEngineTest {

  private val today = "2026-10-12"

  private fun data() = ShopData(
    products = listOf(
      Product("a", "شکر", category = "خوراکی", purchasePrice = 60.0, salePrice = 100.0, minStock = 5.0),
      Product("b", "چای", category = "خوراکی", purchasePrice = 40.0, salePrice = 70.0, minStock = 2.0),
      Product("c", "صابون", category = "بهداشتی", purchasePrice = 10.0, salePrice = 20.0),
      Product("z", "کهنه", active = false),
    ),
    warehouseEntries = listOf(
      WarehouseEntry("w1", "a", units = 10.0, price = 60.0, date = "2026-09-01"),
      WarehouseEntry("w2", "b", units = 3.0, price = 40.0, date = "2026-09-01"),
      WarehouseEntry("w3", "c", units = 50.0, price = 10.0, date = "2026-09-01"),
    ),
    sales = listOf(
      Sale("s1", finalTotal = 300.0, date = "2026-10-12", createdAt = 3),
      Sale("s2", finalTotal = 70.0, date = "2026-10-10", createdAt = 2),
      Sale("s3", finalTotal = 500.0, date = "2026-10-11", status = "cancelled", createdAt = 4),
      Sale("s0", finalTotal = 100.0, date = "2026-10-03", createdAt = 1),
    ),
    saleItems = listOf(
      SaleItem("i1", "s1", "a", quantity = 3.0, unitPrice = 100.0, purchasePrice = 60.0, totalPrice = 300.0),
      SaleItem("i2", "s2", "b", quantity = 1.0, unitPrice = 70.0, purchasePrice = 40.0, totalPrice = 70.0),
      SaleItem("i3", "s3", "c", quantity = 25.0, unitPrice = 20.0, purchasePrice = 10.0, totalPrice = 500.0),
      SaleItem("i0", "s0", "a", quantity = 1.0, unitPrice = 100.0, purchasePrice = 60.0, totalPrice = 100.0),
    ),
    debtors = listOf(Debtor("d1", "کریم"), Debtor("d2", "رحیم")),
    transactions = listOf(
      DebtTransaction("t1", "d1", "give", 1000.0, "2026-07-01", createdAt = isoMillis("2026-07-01")),
      DebtTransaction("t2", "d2", "give", 200.0, "2026-10-11", createdAt = isoMillis("2026-10-11")),
      DebtTransaction("t3", "d2", "receive", 50.0, "2026-10-12", createdAt = isoMillis("2026-10-12")),
    ),
  )

  @Test
  fun `فروش و سود و مقایسه با بازهٔ قبل، بی فاکتورِ لغوشده`() {
    val b = DashboardEngine.board(data(), today, 7, now = isoMillis("2026-10-12"))
    assertEquals(370.0, b.sales.value, 0.001)
    assertEquals(100.0, b.sales.previous, 0.001)
    assertEquals(270.0, b.sales.changePct!!, 0.001)
    assertEquals(120.0 + 30.0, b.profit.value, 0.001)
    assertEquals(2.0, b.invoices.value, 0.0)
    assertEquals(300.0, b.todaySales, 0.001)
    assertEquals(7, b.series.size)
    assertEquals("2026-10-12", b.series.last().iso)
    assertEquals(300.0, b.series.last().sales, 0.001)
  }

  @Test
  fun `کم‌موجودی و ناموجود و بی‌فروش`() {
    val b = DashboardEngine.board(data(), today, 7)
    //  شکر: ۱۰ − ۳ − ۱ = ۶ (> ۵) ⇐ کم نیست؛ چای: ۳ − ۱ = ۲ (≤ ۲) ⇐ کم
    assertEquals(listOf("b"), b.lowStock.map { it.product.id })
    assertTrue(b.outOfStock.isEmpty())
    //  صابون فقط در فاکتورِ لغوشده بوده ⇐ «بی‌فروش»؛ کالای غیرفعال نمی‌آید
    assertEquals(listOf("c"), b.unsold.map { it.product.id })
    assertEquals(2, b.top.size)
    assertEquals("a", b.top.first().product.id)
  }

  @Test
  fun `قرض‌دارِ رو به افزایش و دیرکرده`() {
    val b = DashboardEngine.board(data(), today, 7, now = isoMillis("2026-10-12"))
    assertEquals(listOf("d2"), b.debtRising.map { it.debtor.id })
    assertEquals(150.0, b.debtRising.first().change, 0.001)
    assertEquals(listOf("d1"), b.debtOverdue.map { it.debtor.id })
    assertEquals(1150.0, b.receivables, 0.001)
  }

  @Test
  fun `تاریخ رفت و برگشت`() {
    for (iso in listOf("2026-10-12", "2024-02-29", "2000-01-01", "2026-03-01")) {
      assertEquals(iso, DashboardEngine.isoOf(ExpiryEngine.epochDay(iso)))
    }
    assertNull(DashboardEngine.Kpi(5.0, 0.0).changePct)
  }

  private fun isoMillis(iso: String) = ExpiryEngine.epochDay(iso) * 86_400_000L + 43_200_000L
}
