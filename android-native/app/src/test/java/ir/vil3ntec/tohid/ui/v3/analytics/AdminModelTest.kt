package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** مرکزِ مدیریت: سدِ کارت‌های پولی، درِ هر کارت و عددها از همان موتورها */
class AdminModelTest {

  @Test
  fun `شاگرد سود و ارزشِ انبار و بدهی به تأمین‌کننده را نمی‌بیند`() {
    assertEquals(AdminKpi.entries.toList(), AdminModel.kpis(true))
    val staff = AdminModel.kpis(false)
    assertFalse(AdminKpi.MONTH_PROFIT in staff || AdminKpi.STOCK_VALUE in staff || AdminKpi.PAYABLE in staff)
    assertEquals(listOf(AdminKpi.TODAY_SALES, AdminKpi.MONTH_SALES, AdminKpi.RECEIVABLE, AdminKpi.CASH, AdminKpi.STOCK_ALERT), staff)
  }

  @Test
  fun `هر کارت صفحهٔ دقیقِ خودش را باز می‌کند`() {
    assertEquals("sales", AdminModel.route(AdminKpi.TODAY_SALES))
    assertEquals("reports?tab=profit&range=month", AdminModel.route(AdminKpi.MONTH_SALES))
    assertEquals(AdminModel.route(AdminKpi.MONTH_SALES), AdminModel.route(AdminKpi.MONTH_PROFIT))
    assertEquals("stock", AdminModel.route(AdminKpi.STOCK_VALUE))
    assertEquals("debtors?sort=most", AdminModel.route(AdminKpi.RECEIVABLE))
    assertEquals("purchasing", AdminModel.route(AdminKpi.PAYABLE))
    assertEquals("sales/drawer", AdminModel.route(AdminKpi.CASH))
    assertEquals("stock?filter=out", AdminModel.route(AdminKpi.STOCK_ALERT))
  }

  @Test
  fun `عددها از موتورِ گزارش و صندوقِ بسته`() {
    val today = "2026-10-24"
    val d = ShopData(
      sales = listOf(Sale("a", date = today, finalTotal = 300.0, total = 300.0), Sale("b", date = "2026-10-02", finalTotal = 200.0, total = 200.0)),
      expenses = listOf(Expense("e", amount = 600.0, date = "2026-10-05")),
    )
    val n = AdminModel.numbers(d, today, 0L)
    assertEquals(300.0, n.todayNet, 0.0)
    assertEquals(1, n.todayCount)
    assertEquals(500.0, n.monthNet, 0.0)
    assertEquals(2, n.monthCount)
    assertEquals(-100.0, n.monthProfit, 0.0)
    assertNull(n.cashExpected)
  }
}
