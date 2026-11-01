package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.StockMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** حالتِ خالصِ «گزارش‌ها»: سدِ پول، پیوندِ زبانه و بازه، پیکانِ ماه و گردشِ موجودی */
class ReportsModelTest {

  @Test
  fun `شاگرد سود و خرید را نمی‌بیند و پیوندِ بسته به نخستین زبانهٔ باز می‌رود`() {
    assertEquals(ReportTab.entries.toList(), ReportsModel.tabs(true))
    val staff = ReportsModel.tabs(false)
    assertFalse(ReportTab.PROFIT in staff)
    assertFalse(ReportTab.PURCHASES in staff)
    assertEquals(ReportTab.PRODUCTS, ReportsModel.tabOf("profit", canSeeMoney = false))
    assertEquals(ReportTab.PROFIT, ReportsModel.tabOf(null, canSeeMoney = true))
    assertEquals(ReportTab.EXPIRY, ReportsModel.tabOf("expiry", canSeeMoney = false))
    assertEquals(ReportTab.PROFIT, ReportsModel.tabOf("nope", canSeeMoney = true))
  }

  @Test
  fun `خروجیِ هر زبانه همان بخشِ امروزِ ReportCsv است`() {
    assertEquals("sales", ReportTab.PROFIT.csv)
    assertEquals(listOf("products", "debtors", "purchases", "expiry", "stock"), ReportTab.entries.drop(1).map { it.csv })
    assertEquals(listOf(ReportTab.PROFIT, ReportTab.PURCHASES), ReportTab.entries.filter { it.ranged })
  }

  @Test
  fun `بازهٔ پیوند و پیش‌فرضِ این ماه`() {
    assertEquals(ReportEngine.Range.MONTH, ReportsModel.rangeOf(null))
    assertEquals(ReportEngine.Range.MONTH, ReportsModel.rangeOf("bogus"))
    assertEquals(ReportEngine.Range.JYEAR, ReportsModel.rangeOf("jyear"))
    ReportsModel.RANGES.forEach { assertEquals(it, ReportsModel.rangeOf(ReportsModel.rangeId(it))) }
  }

  @Test
  fun `پیکانِ ماه از سال می‌گذرد و پیکانِ سال فقط سال را عوض می‌کند`() {
    assertEquals(1404 to 12, ReportsModel.stepMonth(1405, 1, -1))
    assertEquals(1406 to 1, ReportsModel.stepMonth(1405, 12, 1))
    assertEquals(1405 to 7, ReportsModel.stepMonth(1405, 7, 0))
    val y = ReportPeriod(ReportEngine.Range.JYEAR, 1405, 7)
    assertEquals(ReportPeriod(ReportEngine.Range.JYEAR, 1404, 7), ReportsModel.step(y, -1))
    val today = ReportPeriod(ReportEngine.Range.TODAY, 1405, 7)
    assertEquals(today, ReportsModel.step(today, 1))
  }

  @Test
  fun `کسرِ میله منفی و صفر را صفر می‌گیرد`() {
    assertEquals(0.5f, ReportsModel.fraction(50.0, 100.0), 0.0001f)
    assertEquals(0f, ReportsModel.fraction(-5.0, 100.0), 0f)
    assertEquals(0f, ReportsModel.fraction(5.0, 0.0), 0f)
    assertEquals(1f, ReportsModel.fraction(150.0, 100.0), 0f)
  }

  @Test
  fun `گردشِ موجودی ورود و خروج و خالصِ هر کالا را جمع می‌زند`() {
    val products = listOf(Product("p1", name = "برنج"))
    val ledger = listOf(
      StockMovement("m1", productId = "p1", qty = 10.0, createdAt = 1),
      StockMovement("m2", productId = "p1", qty = -3.5, createdAt = 5),
      StockMovement("m3", productId = "gone", qty = -1.0, createdAt = 3),
    )
    val flows = ReportsModel.stockFlow(ledger, products)
    assertEquals(listOf("p1", "gone"), flows.map { it.productId })
    val rice = flows.first()
    assertEquals(10.0, rice.inQty, 0.0)
    assertEquals(3.5, rice.outQty, 0.0)
    assertEquals(6.5, rice.net, 0.0)
    assertEquals(2, rice.moves)
    assertNull(flows[1].product)
    assertTrue(ReportsModel.stockFlow(emptyList(), products).isEmpty())
  }
}
