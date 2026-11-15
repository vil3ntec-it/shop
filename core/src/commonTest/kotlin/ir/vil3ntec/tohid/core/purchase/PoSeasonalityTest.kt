package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.purchase.DemandForecast.Day
import ir.vil3ntec.tohid.core.purchase.DemandForecast.Input
import ir.vil3ntec.tohid.core.purchase.PoSeasonality.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PUR-03 — فصل (فقط با ۱۲ ماه تاریخچه) و اطمینانِ کم/متوسط/زیاد در پیشنهادِ سفارش؛ تاریخچه‌های ساختگی، عددها دستی */
class PoSeasonalityTest {

  private val today = CivilDays.jdnOfIso("2026-10-26")!!
  private val ly = today - DemandForecast.YEAR_DAYS

  private fun days(from: Int, to: Int, qty: (Int) -> Long) = (from..to).map { Day(it, qty(it)) }

  /** موجودیِ ۵۰، زمانِ تأمینِ ۷ و پوششِ ۱۴ روز (پیش‌فرض) */
  private fun input(history: List<Day>, first: Int) =
    Input(today, history, onHandMilli = 50_000, leadDays = 7, firstJdn = first)

  @Test fun seasonalSpikeSameMonthLastYearRaisesTheOrder() {
    val first = today - 799
    //  روزی ۱۰، جز «همین ماهِ پارسال» روزی ۴۰
    val h = days(first, today) { if (it in ly + 1..ly + PoSeasonality.MONTH_DAYS) 40_000 else 10_000 }
    val r = PoSeasonality.forecast(input(h, first), first)
    //  ۴۰ ÷ ۱۰ = ۴ ⇒ سقفِ ۳
    assertEquals(DemandForecast.SEASON_MAX, r.season.permille)
    assertTrue(r.season.baseLastYear)
    assertEquals(DemandForecast.SEASON_MAX, r.forecast.seasonPermille)
    //  مصرفِ ۷ روز = ۲۱۰ ≥ ۵۰ ⇒ ۳۰ × ۲۱ − ۵۰ = ۵۸۰
    assertEquals(210_000, r.forecast.reorderPointMilli)
    assertEquals(580_000, r.forecast.qtyMilli)
    assertEquals(800, r.season.historyDays)
    assertEquals(0, r.season.cvPermille)
    assertEquals(Confidence.HIGH, r.season.confidence)
    assertTrue(DemandForecast.why(r.forecast).contains("پارسال"))
  }

  @Test fun flatHistoryKeepsTheOrderAndNamesNoSeason() {
    val first = today - 799
    val h = days(first, today) { 10_000 }
    val r = PoSeasonality.forecast(input(h, first), first)
    assertNull(r.season.permille)
    assertNull(r.forecast.seasonPermille)
    //  ۱۰ × ۲۱ − ۵۰ = ۱۶۰
    assertEquals(160_000, r.forecast.qtyMilli)
    assertEquals(DemandForecast.forecast(input(h, first)).qtyMilli, r.forecast.qtyMilli)
    assertEquals(Confidence.HIGH, r.season.confidence)
    assertFalse(DemandForecast.why(r.forecast).contains("پارسال"))
    //  همان تخت با ۱۳ ماه ⇒ فصل دو بار دیده نشده ⇒ متوسط
    val young = today - 399
    assertEquals(Confidence.MEDIUM, PoSeasonality.forecast(input(days(young, today) { 10_000 }, young), young).season.confidence)
  }

  @Test fun shortHistoryIsTodaysLogicLabelledLow() {
    val first = today - 99
    val h = days(first, today) { 10_000 }
    val r = PoSeasonality.forecast(input(h, first), first)
    //  بیت‌به‌بیت همان حسابِ امروز
    assertEquals(DemandForecast.forecast(input(h, first)), r.forecast)
    assertFalse(r.season.seasonal)
    assertNull(r.season.permille)
    assertEquals(Confidence.LOW, r.season.confidence)
    //  ۳۶۴ روز هنوز کم است
    val almost = today - 363
    val r2 = PoSeasonality.forecast(input(days(almost, today) { 10_000 }, almost), almost)
    assertEquals(364, r2.season.historyDays)
    assertEquals(Confidence.LOW, r2.season.confidence)
    //  بی فروش
    val none = PoSeasonality.forecast(input(emptyList(), today - 900), null)
    assertEquals(0, none.season.historyDays)
    assertEquals(Confidence.LOW, none.season.confidence)
  }

  @Test fun twelveMonthsIsEnoughWithThisYearsRecentAverageAsTheBase() {
    //  ۴۰۰ روز: «همین ماهِ پارسال» در تاریخچه هست، پنجرهٔ ۹۰ روزهٔ پارسال نه ⇒ پایه میانگینِ اخیرِ امسال
    val first = today - 399
    val h = days(first, today) { if (it in ly + 1..ly + PoSeasonality.MONTH_DAYS) 20_000 else 10_000 }
    val r = PoSeasonality.forecast(input(h, first), first)
    assertEquals(2000, r.season.permille)
    assertFalse(r.season.baseLastYear)
    //  ۲۰ × ۲۱ − ۵۰ = ۳۷۰؛ حسابِ INV-13 همین‌جا فصلی نمی‌دید (۱۶۰)
    assertEquals(370_000, r.forecast.qtyMilli)
    assertEquals(160_000, DemandForecast.forecast(input(h, first)).qtyMilli)
    assertEquals(Confidence.MEDIUM, r.season.confidence)
  }

  @Test fun varianceLowersConfidence() {
    val first = today - 799
    //  سه روز یک بار ۳۰ ⇒ میانگینِ ۱۰، نوسانِ ۱٫۴۱ ⇒ متوسط با وجودِ دو سال
    val lumpy = days(first, today) { if (it.mod(3) == 0) 30_000 else 0 }
    val m = PoSeasonality.forecast(input(lumpy, first), first).season
    assertEquals(1414, m.cvPermille)
    assertEquals(Confidence.MEDIUM, m.confidence)
    //  ده روز یک بار ۱۰۰ ⇒ نوسانِ ۳ ⇒ کم
    val rare = days(first, today) { if (it.mod(10) == 0) 100_000 else 0 }
    val l = PoSeasonality.forecast(input(rare, first), first).season
    assertEquals(3000, l.cvPermille)
    assertEquals(Confidence.LOW, l.confidence)
  }
}
