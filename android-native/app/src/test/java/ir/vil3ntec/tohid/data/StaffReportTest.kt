package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.staff.Commission
import ir.vil3ntec.tohid.core.staff.TimeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** STF-07 و STF-08 — دفتر ⇒ «۷ روز و این ماه» و پورسانت با برگشت به نامِ فروشندهٔ اصل */
class StaffReportTest {

  private fun af(a: Long) = Money(a * 100, Currency.AFN)

  private val d = ShopData(
    sales = listOf(
      Sale(id = "old", seller = "Ahmad", finalTotal = 4_000.0, date = "2026-09-28"),
      Sale(id = "s1", seller = "Ahmad", finalTotal = 1_000.0, date = "2026-10-05"),
      Sale(id = "s2", seller = " Karim ", finalTotal = 2_000.0, date = "2026-10-06"),
      Sale(id = "s3", seller = "Karim", finalTotal = 9_000.0, date = "2026-10-07", status = "cancelled"),
      Sale(id = "s4", seller = "", finalTotal = 500.0, date = "2026-10-08"),
    ),
    saleReturns = listOf(
      SaleReturn(id = "r1", saleId = "old", amount = 400.0, date = "2026-10-03"),
      SaleReturn(id = "r2", saleId = "s2", amount = 250.0, date = "2026-10-09"),
      SaleReturn(id = "r3", saleId = "s3", amount = 900.0, date = "2026-10-09"),
      SaleReturn(id = "r4", saleId = "s1", amount = 100.0, date = "2026-11-01"),
    ),
  )

  @Test
  fun returnsInTheMonthComeOffTheOriginalSellerEvenForLastMonthsSale() {
    val lines = StaffReport.commissionLines(d, "2026-10-01", "2026-10-31")
    val r = Commission.compute(lines, Commission.Plan(1_000), Currency.AFN)
    //  Ahmad: ۱۰۰۰ − ۴۰۰ (برگشتِ فروشِ شهریور) = ۶۰۰؛ Karim: ۲۰۰۰ − ۲۵۰ = ۱۷۵۰؛ اصلِ لغوشده و برگشتِ ماهِ بعد بیرون
    assertEquals(listOf("Ahmad", "Karim"), r.sellers.map { it.sellerId })
    assertEquals(listOf(af(600), af(1_750)), r.sellers.map { it.base })
    assertEquals(listOf(af(60), af(175)), r.sellers.map { it.commission })
    assertEquals(af(500), r.unassignedBase)
  }

  //  STF-08 — «نرخِ دسته»: هر قلم با دستهٔ کالایش (پس از سهمِ تخفیفِ فاکتور) و برگشت با دستهٔ کالای برگشتی
  @Test
  fun categoryRatesApplyPerLineAndToReturnsOfThatCategory() {
    val shop = ShopData(
      products = listOf(Product(id = "p-rice", category = "rice"), Product(id = "p-cig", category = "cigarettes")),
      sales = listOf(Sale(id = "s1", seller = "Ahmad", finalTotal = 900.0, date = "2026-10-05")),
      saleItems = listOf(
        SaleItem(id = "i1", saleId = "s1", productId = "p-rice", totalPrice = 600.0),
        SaleItem(id = "i2", saleId = "s1", productId = "p-cig", totalPrice = 400.0),
      ),
      saleReturns = listOf(
        SaleReturn(id = "r1", saleId = "s1", productId = "p-cig", amount = 100.0, date = "2026-10-06"),
        SaleReturn(id = "r2", saleId = "s1", saleItemId = "i1", amount = 40.0, date = "2026-10-07"),
      ),
    )
    val lines = StaffReport.commissionLines(shop, "2026-10-01", "2026-10-31")
    val r = Commission.compute(lines, Commission.Plan(1_000, categoryBp = mapOf("cigarettes" to 0)), Currency.AFN)
    //  برنج ۵۴۰ − ۴۰ (برگشتِ قلمِ i1) = ۵۰۰ × ۱۰٪ = ۵۰؛ سیگار ۳۶۰ − ۱۰۰ با نرخِ ۰
    assertEquals(af(760), r.sellers.single().base)
    assertEquals(af(50), r.sellers.single().commission)
    //  بی نرخِ دسته همان پورسانتِ یک‌ردیفهٔ پیشین: (۹۰۰ − ۱۴۰) × ۱۰٪
    assertEquals(af(76), Commission.compute(lines, Commission.Plan(1_000), Currency.AFN).sellers.single().commission)
  }

  @Test
  fun periodsSplitTheLastSevenDaysFromTheMonth() {
    fun span(staff: String, date: String, minutes: Long, issue: TimeClock.Issue? = null) =
      TimeClock.Span(staff, "i", "o", 1L, 2L, date, minutes, minutes, issue)
    val spans = listOf(
      span("a", "2026-10-02", 300), span("a", "2026-10-20", 240), span("a", "2026-10-25", 120),
      span("a", "2026-10-25", 0, TimeClock.Issue.MISSING_OUT),
      span("b", "2026-10-03", 60),
      span("c", "2026-09-30", 480),
    )
    val p = StaffReport.periods(spans, today = "2026-10-25", monthFrom = "2026-10-01")
    assertEquals(listOf("a", "b"), p.map { it.staffId })
    assertEquals("2026-10-19", StaffReport.weekFrom("2026-10-25"))
    assertEquals(TimeClock.PeriodTotal("a", 360, 2, 1), p[0].week)
    assertEquals(TimeClock.PeriodTotal("a", 660, 3, 1), p[0].month)
    assertNull(p[1].week)
    assertEquals(TimeClock.PeriodTotal("b", 60, 1, 0), p[1].month)
  }
}
