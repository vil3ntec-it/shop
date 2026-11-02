package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.report.QuarterTax.Doc
import ir.vil3ntec.tohid.core.report.QuarterTax.Kind
import ir.vil3ntec.tohid.core.report.QuarterTax.Quarter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RPT-10 / S49: مرزهای سه‌ماهه (با گذر از حوت)، سررسید و یادآور، و جمعِ مالیات از ردیف‌ها. */
class QuarterTaxTest {

  private fun s(y: Int, m: Int, d: Int): String = assertNotNull(SolarHijri.fromSolar(y, m, d))

  @Test fun defaultQuartersMatchTheFiscalQuartersOfLoc02() {
    for (q in 1..4) assertEquals(SolarHijri.fiscalQuarterRange(1405, q), QuarterTax.range(1405, q))
    assertEquals("2026-03-21", QuarterTax.range(1405, 1)!!.first)
    //  سرطان تا سنبله
    assertEquals(s(1405, 4, 1) to s(1405, 6, 31), QuarterTax.range(1405, 2))
  }

  @Test fun quarterStartingInJadiWrapsIntoTheNextSolarYear() {
    //  سالِ مالیِ ۱۴۰۴ از جدی: Q1 = جدی–حوتِ ۱۴۰۴، Q2 = حمل–جوزای ۱۴۰۵
    assertEquals(s(1404, 10, 1) to s(1404, 12, SolarHijri.monthLength(1404, 12)), QuarterTax.range(1404, 1, startMonth = 10))
    assertEquals(s(1405, 1, 1) to s(1405, 3, 31), QuarterTax.range(1404, 2, startMonth = 10))
    assertEquals(Quarter(1404, 2), QuarterTax.quarterOf(s(1405, 2, 10), startMonth = 10))
    assertEquals(Quarter(1404, 1), QuarterTax.quarterOf(s(1404, 12, 1), startMonth = 10))
    assertEquals(Quarter(1405, 1), QuarterTax.quarterOf(s(1405, 10, 1), startMonth = 10))
  }

  @Test fun quarterThatCrossesHootSpansTwoSolarYears() {
    //  از دلو: Q1 = دلو و حوتِ ۱۴۰۴ و حملِ ۱۴۰۵
    assertEquals(listOf(1404 to 11, 1404 to 12, 1405 to 1), QuarterTax.months(1404, 1, startMonth = 11))
    assertEquals(s(1404, 11, 1) to s(1405, 1, 31), QuarterTax.range(1404, 1, startMonth = 11))
    assertEquals(Quarter(1404, 1), QuarterTax.quarterOf(s(1405, 1, 31), startMonth = 11))
    assertEquals(Quarter(1404, 2), QuarterTax.quarterOf(s(1405, 2, 1), startMonth = 11))
  }

  @Test fun everyDayOfAYearBelongsToExactlyOneQuarterWhateverTheStartMonth() {
    for (start in 1..12) {
      val first = assertNotNull(SolarHijri.fromSolar(1405, 1, 1))
      for (i in 0 until 366) {
        val day = assertNotNull(CivilDays.addDays(first, i))
        val x = assertNotNull(QuarterTax.quarterOf(day, start))
        val r = assertNotNull(QuarterTax.range(x.fiscalYear, x.q, start))
        assertTrue(day in r.first..r.second, "$start $day $x $r")
      }
    }
  }

  @Test fun invalidQuarterOrStartMonthGivesNull() {
    assertNull(QuarterTax.range(1405, 0))
    assertNull(QuarterTax.range(1405, 5))
    assertNull(QuarterTax.range(1405, 1, startMonth = 13))
    assertNull(QuarterTax.quarterOf("2026-02-30"))
    assertEquals(Quarter(1404, 4), QuarterTax.previous(Quarter(1405, 1)))
    assertEquals(Quarter(1406, 1), QuarterTax.next(Quarter(1405, 4)))
  }

  @Test fun dueDateIsTheFifteenthOfTheMonthAfterTheQuarter() {
    val cfg = QuarterTax.Config()
    //  S49: سرطان–سنبله ⇒ ۱۵ میزان
    assertEquals(s(1405, 7, 15), QuarterTax.dueDate(Quarter(1405, 2), cfg))
    //  جدی–حوت ⇒ ۱۵ حملِ سالِ بعد
    assertEquals(s(1406, 1, 15), QuarterTax.dueDate(Quarter(1405, 4), cfg))
    //  روزِ ۳۱ در ماهِ ۳۰ روزه ⇒ روزِ آخر؛ سه ماه پس از پایان
    assertEquals(s(1405, 7, 30), QuarterTax.dueDate(Quarter(1405, 2), cfg.copy(dueDay = 31)))
    assertEquals(s(1405, 9, 15), QuarterTax.dueDate(Quarter(1405, 2), cfg.copy(dueMonths = 3)))
  }

  @Test fun reminderStartsTenDaysBeforeAndEndsOnTheDueDay() {
    val cfg = QuarterTax.Config()
    val due = s(1405, 7, 15)
    fun at(daysBefore: Int) = QuarterTax.reminder(assertNotNull(CivilDays.addDays(due, -daysBefore)), cfg)
    assertNull(at(11))
    assertEquals(QuarterTax.Due(Quarter(1405, 2), due, 10), at(10))
    assertEquals(3, at(3)?.daysLeft)
    assertEquals(0, at(0)?.daysLeft)
    assertNull(at(-1))
    //  N از تنظیمات
    assertEquals(20, QuarterTax.reminder(assertNotNull(CivilDays.addDays(due, -20)), cfg.copy(remindDays = 20))?.daysLeft)
    assertNull(QuarterTax.reminder(assertNotNull(CivilDays.addDays(due, -1)), cfg.copy(remindDays = 0)))
  }

  @Test fun reminderAcrossTheYearWrap() {
    //  جدی–حوتِ ۱۴۰۵ ⇒ ۱۵ حملِ ۱۴۰۶: پنج روز پیش (۱۰ حمل) سه‌ماههٔ پیشینِ سالِ قبل را یاد می‌دهد
    val due = s(1406, 1, 15)
    assertEquals(QuarterTax.Due(Quarter(1405, 4), due, 5), QuarterTax.reminder(s(1406, 1, 10), QuarterTax.Config()))
    //  با سررسیدِ همان ماهِ آخر (dueMonths = 0)
    assertEquals(Quarter(1405, 4), QuarterTax.reminder(s(1405, 12, 10), QuarterTax.Config(dueMonths = 0))?.quarter)
    assertNull(QuarterTax.reminder("not-a-date", QuarterTax.Config()))
  }

  @Test fun taxSumsComeOnlyFromRowsInsideTheQuarter() {
    val (from, to) = assertNotNull(QuarterTax.range(1405, 2))
    val before = assertNotNull(CivilDays.addDays(from, -1))
    val after = assertNotNull(CivilDays.addDays(to, 1))
    val docs = listOf(
      Doc(Kind.SALE, from, 100_000),
      Doc(Kind.SALE, to, 250_050),
      Doc(Kind.SALE, before, 999_999),
      Doc(Kind.SALE, after, 999_999),
      Doc(Kind.RETURN, s(1405, 5, 2), 20_000),
      Doc(Kind.RETURN, after, 5_000),
      Doc(Kind.PURCHASE, s(1405, 4, 9), 70_000),
    )
    val r = QuarterTax.summarize(docs, from, to, rateBp = 1_000)
    assertEquals(2, r.salesCount)
    assertEquals(350_050, r.salesMinor)
    assertEquals(1, r.returnsCount)
    assertEquals(20_000, r.returnsMinor)
    assertEquals(330_050, r.netMinor)
    //  ۱۰٪ ِ ۳۵۰۰٫۵۰ = ۳۵۰٫۰۵؛ ۱۰٪ ِ ۲۰۰ = ۲۰
    assertEquals(35_005, r.salesTaxMinor)
    assertEquals(2_000, r.returnsTaxMinor)
    assertEquals(33_005, r.netTaxMinor)
    assertEquals(1, r.purchasesCount)
    assertEquals(70_000, r.purchasesMinor)
  }

  @Test fun noRateMeansZeroTaxButTheSameBases() {
    val r = QuarterTax.summarize(listOf(Doc(Kind.SALE, "2026-07-01", 12_345)), "2026-06-22", "2026-09-22", rateBp = 0)
    assertEquals(12_345, r.salesMinor)
    assertEquals(0, r.salesTaxMinor)
    assertEquals(0, r.netTaxMinor)
  }

  @Test fun taxRoundsHalfAwayFromZeroWithoutOverflow() {
    assertEquals(1, QuarterTax.taxOf(5, 1_000))
    assertEquals(0, QuarterTax.taxOf(4, 1_000))
    assertEquals(-1, QuarterTax.taxOf(-5, 1_000))
    val big = 9_007_199_254_740_991L
    assertEquals(big, QuarterTax.taxOf(big, 10_000))
    assertEquals(900_719_925_474_099L, QuarterTax.taxOf(big, 1_000))
  }

  @Test fun rateTextRoundTrips() {
    assertEquals(250, QuarterTax.parseRate("2.5"))
    assertEquals(250, QuarterTax.parseRate("۲٫۵"))
    assertEquals(1_000, QuarterTax.parseRate("10%"))
    assertEquals(5, QuarterTax.parseRate("0.05"))
    assertNull(QuarterTax.parseRate("0.005"))
    assertNull(QuarterTax.parseRate("101"))
    assertNull(QuarterTax.parseRate("-1"))
    assertNull(QuarterTax.parseRate("abc"))
    assertEquals("2.5", QuarterTax.rateText(250))
    assertEquals("10", QuarterTax.rateText(1_000))
    assertEquals("0.05", QuarterTax.rateText(5))
  }

  @Test fun brokenConfigIsClampedNotCrashing() {
    val c = QuarterTax.Config(startMonth = 0, rateBp = 99_999, dueDay = 0, dueMonths = 9, remindDays = 999).clamped()
    assertEquals(QuarterTax.Config(1, 10_000, 1, 3, 60), c)
  }
}
