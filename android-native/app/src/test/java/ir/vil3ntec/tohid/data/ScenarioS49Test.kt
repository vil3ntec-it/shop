package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.report.QuarterTax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  سناریوی S49 (§۷.۵) — گزارشِ مالیاتِ سه‌ماهه (RPT-10، LOC-02).
 *
 *  «دورهٔ سرطان تا سنبله و نرخ‌ها از تنظیمات. جمعِ درآمدِ ناخالص با جمعِ اسناد برابر است. یادآور ۱۰ روز
 *  پیش از ۱۵ میزان.» — از اسنادِ دفترِ برنامه (`ShopData`) تا برگهٔ گزارش و یادآورِ صفحهٔ نخست، با همان
 *  موتورهای برنامه (`TaxQuarter`، `QuarterTax`). جمعِ مورد انتظار مستقیم از خودِ اسناد حساب می‌شود، نه از
 *  هستهٔ گزارش.
 *
 *  نسنجیده: «تنظیمات» در برنامه `TaxQuarterPref` است (SharedPreferences، پیش‌فرض خاموش)؛ این‌جا همان
 *  `QuarterTax.Config` که صفحهٔ تنظیم ذخیره می‌کند.
 */
class ScenarioS49Test {

  private fun s(y: Int, m: Int, d: Int): String = SolarHijri.fromSolar(y, m, d)!!
  private fun minor(v: Double): Long = Money.fromLegacy(v, Currency.AFN).minor

  /** تنظیماتِ دکان: سالِ مالی از حمل، نرخِ ۱۰٪، سررسید ۱۵ ِ ماهِ بعد، یادآور ۱۰ روز پیش */
  private val settings = QuarterTax.Config(startMonth = 1, rateBp = 1_000, dueDay = 15, dueMonths = 1, remindDays = 10)

  private val data = ShopData(
    sales = listOf(
      Sale(id = "in-1", finalTotal = 2_400.0, total = 2_500.0, discount = 100.0, date = s(1405, 4, 1)),
      Sale(id = "in-2", finalTotal = 1_350.75, total = 1_350.75, date = s(1405, 5, 17)),
      Sale(id = "in-3", finalTotal = 600.0, total = 600.0, date = s(1405, 6, 31)),
      Sale(id = "before", finalTotal = 9_999.0, total = 9_999.0, date = s(1405, 3, 31)),
      Sale(id = "after", finalTotal = 7_777.0, total = 7_777.0, date = s(1405, 7, 1)),
    ),
    saleReturns = listOf(
      SaleReturn(id = "r-in", saleId = "in-2", amount = 350.75, date = s(1405, 5, 20)),
      SaleReturn(id = "r-after", saleId = "in-3", amount = 100.0, date = s(1405, 7, 2)),
    ),
    purchases = listOf(Purchase(id = "p-in", totalAmount = 800.0, date = s(1405, 4, 10))),
  )

  @Test
  fun `S49 دورهٔ سرطان تا سنبله و نرخ از تنظیمات`() {
    val q = QuarterTax.quarterOf(s(1405, 5, 17), settings.startMonth)
    assertEquals(QuarterTax.Quarter(1405, 2), q)
    assertEquals(s(1405, 4, 1) to s(1405, 6, 31), QuarterTax.range(1405, 2, settings.startMonth))
    assertEquals("سرطان–سنبله 1405", TaxQuarter.monthsLabel(q!!, settings.startMonth))
    val r = TaxQuarter.report(data, settings, q)!!
    assertEquals(1_000, r.summary.rateBp)
    //  نرخِ دیگری در تنظیمات ⇒ همان پایه، مالیاتِ دیگر
    val r5 = TaxQuarter.report(data, settings.copy(rateBp = 500), q)!!
    assertEquals(r.summary.netMinor, r5.summary.netMinor)
    assertEquals(QuarterTax.taxOf(r.summary.netMinor, 500), r5.summary.netTaxMinor)
  }

  @Test
  fun `S49 جمعِ درآمدِ ناخالص با جمعِ اسناد برابر است`() {
    val q = QuarterTax.Quarter(1405, 2)
    val (from, to) = QuarterTax.range(1405, 2)!!
    val r = TaxQuarter.report(data, settings, q)!!
    //  تطبیق: مستقیم از خودِ اسناد
    val sales = data.sales.filter { it.status != "cancelled" && it.date in from..to }
    val returns = data.saleReturns.filter { it.date in from..to }
    val purchases = data.purchases.filter { it.date in from..to }
    assertEquals(sales.size, r.summary.salesCount)
    assertEquals(sales.sumOf { minor(it.finalTotal) }, r.summary.salesMinor)
    assertEquals(returns.size, r.summary.returnsCount)
    assertEquals(returns.sumOf { minor(it.amount) }, r.summary.returnsMinor)
    assertEquals(r.summary.salesMinor - r.summary.returnsMinor, r.summary.netMinor)
    assertEquals(purchases.sumOf { minor(it.totalAmount) }, r.summary.purchasesMinor)
    //  ۲۴۰۰ + ۱۳۵۰٫۷۵ + ۶۰۰ − ۳۵۰٫۷۵ = ۴۰۰۰ ⇒ ۱۰٪ = ۴۰۰
    assertEquals(400_000L, r.summary.netMinor)
    assertEquals(40_000L, r.summary.netTaxMinor)
    //  برگهٔ خروجی همان عدد
    val sheet = TaxQuarter.sheet(r, settings.startMonth)
    assertEquals("4000", sheet.rows.drop(1).associate { it[0] to it[2] }["درآمدِ ناخالصِ خالص"])
  }

  @Test
  fun `S49 یادآور ۱۰ روز پیش از ۱۵ میزان`() {
    val due = s(1405, 7, 15)
    assertEquals(due, TaxQuarter.report(data, settings, QuarterTax.Quarter(1405, 2))!!.dueIso)
    assertNull("۱۱ روز پیش هنوز نه", QuarterTax.reminder(s(1405, 7, 4), settings))
    val first = QuarterTax.reminder(s(1405, 7, 5), settings)
    assertNotNull("۱۰ روز پیش", first)
    assertEquals(QuarterTax.Due(QuarterTax.Quarter(1405, 2), due, 10), first)
    assertEquals(0, QuarterTax.reminder(due, settings)!!.daysLeft)
    assertNull("پس از سررسید نه", QuarterTax.reminder(s(1405, 7, 16), settings))
    //  صفحهٔ نخستِ گزارش در پنجرهٔ یادآور همان سه‌ماهه را باز می‌کند
    assertEquals(QuarterTax.Quarter(1405, 2), TaxQuarter.initial(s(1405, 7, 5), settings))
  }
}
