package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.report.ReportPeriod.Range
import ir.vil3ntec.tohid.core.report.ReportPeriod.Unit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RPT-13 — «دورهٔ قبل / بعد» به واحدِ همان بازه، بی دورهٔ آینده؛ و تغییر نسبت به دورهٔ قبل */
class ReportPeriodTest {

  private val today = "2026-10-31"

  @Test
  fun daysKeepTheSameLength() {
    assertEquals(Range("2026-10-30", "2026-10-30"), ReportPeriod.shift(Unit.DAYS, today, today, -1, today))
    //  ۷ روز ⇒ ۷ روزِ پیش از آن
    assertEquals(Range("2026-10-18", "2026-10-24"), ReportPeriod.shift(Unit.DAYS, "2026-10-25", today, -1, today))
    //  از روی مرزِ ماه و سال
    assertEquals(Range("2025-12-31", "2025-12-31"), ReportPeriod.shift(Unit.DAYS, "2026-01-01", "2026-01-01", -1, today))
    assertEquals(Range("2026-10-25", today), ReportPeriod.shift(Unit.DAYS, "2026-10-25", today, 0, today))
  }

  @Test
  fun nextPeriodIsNeverWhollyInTheFuture() {
    assertNull(ReportPeriod.shift(Unit.DAYS, today, today, 1, today))
    assertNull(ReportPeriod.shift(Unit.GMONTH, "2026-10-01", today, 1, today))
    //  دو دوره پس، بعد یکی پیش ⇒ یک دوره پیش از جاری
    val back = ReportPeriod.shift(Unit.DAYS, "2026-10-25", today, -2, today)!!
    assertEquals(Range("2026-10-18", "2026-10-24"), ReportPeriod.shift(Unit.DAYS, back.from, back.to, 1, today))
  }

  @Test
  fun gregorianMonthIsWholeExceptTheCurrentOne() {
    assertEquals(Range("2026-09-01", "2026-09-30"), ReportPeriod.shift(Unit.GMONTH, "2026-10-01", today, -1, today))
    assertEquals(Range("2026-02-01", "2026-02-28"), ReportPeriod.shift(Unit.GMONTH, "2026-10-01", today, -8, today))
    assertEquals(Range("2025-12-01", "2025-12-31"), ReportPeriod.shift(Unit.GMONTH, "2026-01-01", "2026-01-31", -1, today))
    //  ماهِ جاری تا امروز می‌رود، نه تا آخرِ ماه
    assertEquals(Range("2026-10-01", "2026-10-15"), ReportPeriod.shift(Unit.GMONTH, "2026-09-01", "2026-09-30", 1, "2026-10-15"))
  }

  @Test
  fun solarMonthAndYearAreWhole() {
    //  عقرب ۱۴۰۵ = 2026-10-23..2026-11-21؛ میزان = 2026-09-23..2026-10-22
    assertEquals(Range("2026-09-23", "2026-10-22"), ReportPeriod.shift(Unit.JMONTH, "2026-10-23", "2026-11-21", -1, today))
    //  از حمل ۱۴۰۵ به حوتِ ۱۴۰۴
    val hoot = ReportPeriod.shift(Unit.JMONTH, "2026-03-21", "2026-04-20", -1, today)!!
    assertEquals("2026-02-20", hoot.from)
    assertEquals("2026-03-20", hoot.to)
    assertEquals(Range("2025-03-21", "2026-03-20"), ReportPeriod.shift(Unit.JYEAR, "2026-03-21", "2027-03-20", -1, today))
  }

  @Test
  fun allAndBadInputDoNotMove() {
    assertNull(ReportPeriod.shift(Unit.NONE, "0000-01-01", "9999-12-31", -1, today))
    assertNull(ReportPeriod.shift(Unit.DAYS, "nope", today, -1, today))
    assertNull(ReportPeriod.shift(Unit.DAYS, today, "2026-10-01", -1, today))
  }

  @Test
  fun changeAgainstPreviousPeriod() {
    assertEquals(ReportPeriod.Change(2_000, 2_000), ReportPeriod.change(12_000, 10_000))
    assertEquals(ReportPeriod.Change(-2_500, -2_500), ReportPeriod.change(7_500, 10_000))
    //  نیمه دور از صفر: 1/3 ⇒ ۳۳۳۳٫۳ ⇒ ۳۳۳۳؛ 2/3 ⇒ ۶۶۶۶٫۶ ⇒ ۶۶۶۷
    assertEquals(3_333L, ReportPeriod.change(4, 3).bp)
    assertEquals(6_667L, ReportPeriod.change(5, 3).bp)
    assertEquals(-6_667L, ReportPeriod.change(1, 3).bp)
    assertEquals(ReportPeriod.Change(500, null), ReportPeriod.change(500, 0))
    //  پیشینِ منفی (زیان) ⇒ نسبت به اندازه‌اش
    assertEquals(15_000L, ReportPeriod.change(500, -1_000).bp)
    assertNull(ReportPeriod.change(Long.MAX_VALUE / 2, 1).bp)
  }
}
