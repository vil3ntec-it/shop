package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.report.QuarterTax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RPT-10 / S49: جمع‌های سه‌ماهه از ردیف‌های واقعیِ دفتر، برابر با `ReportEngine`، و برگهٔ خروجی. */
class TaxQuarterTest {

  private fun s(y: Int, m: Int, d: Int): String = SolarHijri.fromSolar(y, m, d)!!
  private fun minor(v: Double): Long = Money.fromLegacy(v, Currency.AFN).minor

  /** سرطان تا سنبلهٔ ۱۴۰۵ با ردیف‌هایی پیش و پس از دوره، فاکتورِ لغوشده و برگشتِ آن */
  private val q2 = QuarterTax.Quarter(1405, 2)
  private val data = ShopData(
    sales = listOf(
      Sale(id = "s1", finalTotal = 1_200.50, total = 1_300.0, discount = 99.5, date = s(1405, 4, 1)),
      Sale(id = "s2", finalTotal = 800.0, total = 800.0, date = s(1405, 6, 31)),
      Sale(id = "s3", finalTotal = 500.0, total = 500.0, date = s(1405, 5, 10), status = "cancelled"),
      Sale(id = "s4", finalTotal = 999.0, total = 999.0, date = s(1405, 3, 31)),
      Sale(id = "s5", finalTotal = 999.0, total = 999.0, date = s(1405, 7, 1)),
    ),
    saleReturns = listOf(
      //  برگشت در روزِ خودش: فاکتورِ پیش از دوره، برگشت درونِ دوره
      SaleReturn(id = "r1", saleId = "s4", amount = 150.25, date = s(1405, 4, 5)),
      SaleReturn(id = "r2", saleId = "s3", amount = 500.0, date = s(1405, 5, 11)),
      SaleReturn(id = "r3", saleId = "s2", amount = 100.0, date = s(1405, 7, 2)),
    ),
    purchases = listOf(
      Purchase(id = "p1", totalAmount = 640.0, date = s(1405, 4, 20)),
      Purchase(id = "p2", totalAmount = 50.0, date = s(1405, 3, 1)),
    ),
  )

  @Test fun `جمعِ درآمدِ ناخالص با جمعِ اسنادِ گزارشِ فروش و خرید برابر است`() {
    val cfg = QuarterTax.Config(rateBp = 1_000)
    val r = TaxQuarter.report(data, cfg, q2)!!
    val (from, to) = QuarterTax.range(1405, 2)!!
    val sales = ReportEngine.sales(data, from, to)
    assertEquals(minor(sales.net), r.summary.salesMinor)
    assertEquals(2, r.summary.salesCount)
    assertEquals(minor(sales.returnAmount), r.summary.returnsMinor)
    assertEquals(1, r.summary.returnsCount)
    assertEquals(minor(ReportEngine.purchases(data, from, to).total), r.summary.purchasesMinor)
    //  ۲۰۰۰٫۵۰ − ۱۵۰٫۲۵ = ۱۸۵۰٫۲۵؛ ۱۰٪ ⇒ ۲۰۰٫۰۵ − ۱۵٫۰۳ = ۱۸۵٫۰۲
    assertEquals(200_050L, r.summary.salesMinor)
    assertEquals(185_025L, r.summary.netMinor)
    assertEquals(20_005L, r.summary.salesTaxMinor)
    assertEquals(1_503L, r.summary.returnsTaxMinor)
    assertEquals(18_502L, r.summary.netTaxMinor)
    //  S49: سررسید ۱۵ میزان
    assertEquals(s(1405, 7, 15), r.dueIso)
  }

  @Test fun `برگهٔ خروجی همان عددهای گزارش را دارد`() {
    val r = TaxQuarter.report(data, QuarterTax.Config(rateBp = 1_000), q2)!!
    val sheet = TaxQuarter.sheet(r, 1)
    assertEquals("مالیات-سه‌ماهه-1405-Q2", sheet.name)
    val byLabel = sheet.rows.drop(1).associate { it[0] to it[2] }
    assertEquals("2000.50", byLabel["فروشِ مشمول"])
    assertEquals("150.25", byLabel["برگشتِ فروش"])
    assertEquals("1850.25", byLabel["درآمدِ ناخالصِ خالص"])
    assertEquals("10", byLabel["نرخ (درصد)"])
    assertEquals("185.02", byLabel["مالیاتِ خالصِ دوره"])
    assertEquals(s(1405, 7, 15), byLabel["سررسید"])
    assertTrue(sheet.rows.all { it.size == 3 })
  }

  @Test fun `بی نرخ مالیات صفر است ولی پایه‌ها همان`() {
    val r = TaxQuarter.report(data, QuarterTax.Config(), q2)!!
    assertEquals(200_050L, r.summary.salesMinor)
    assertEquals(0L, r.summary.netTaxMinor)
  }

  @Test fun `صفحه نخست سه‌ماهه‌ای را نشان می‌دهد که یادآورش روشن است`() {
    val cfg = QuarterTax.Config()
    val inWindow = CivilDays.addDays(s(1405, 7, 15), -4)!!
    assertEquals(q2, TaxQuarter.initial(inWindow, cfg))
    //  بیرون از پنجره ⇒ سه‌ماههٔ پیشِ امروز
    assertEquals(QuarterTax.Quarter(1405, 2), TaxQuarter.initial(s(1405, 8, 20), cfg))
    assertEquals("سرطان–سنبله 1405", TaxQuarter.monthsLabel(q2, 1))
    assertEquals("جدی–حوت 1404", TaxQuarter.monthsLabel(QuarterTax.Quarter(1404, 1), 10))
    assertEquals("دلو 1404–حمل 1405", TaxQuarter.monthsLabel(QuarterTax.Quarter(1404, 1), 11))
  }
}
