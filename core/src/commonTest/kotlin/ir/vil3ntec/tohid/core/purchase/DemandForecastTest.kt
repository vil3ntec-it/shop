package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.purchase.DemandForecast.Day
import ir.vil3ntec.tohid.core.purchase.DemandForecast.Input
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Confidence
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Reason
import ir.vil3ntec.tohid.core.purchase.SupplierPricing.Buy
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-13 (پیشنهاد با فروش، زمانِ تأمین و فصل، با دلیل و اطمینان) و INV-14 (چند روز کافی است) — عددها دستی */
class DemandForecastTest {

  /** دوشنبه */
  private val today = CivilDays.jdnOfIso("2026-10-26")!!

  private fun days(from: Int, to: Int, qty: (Int) -> Long) = (from..to).map { Day(it, qty(it)) }

  /** S23: ۸۴ روز، جمعه ۲۴ و بقیه ۱۰ ⇒ میانگینِ ۱۲ و جمعهٔ دو برابر */
  private val s23 = days(today - 83, today) { if (DemandForecast.isoWeekday(it) == 5) 24_000 else 10_000 }

  @Test fun s23WeekdayReasonDaysLeftAndQty() {
    assertEquals(1, DemandForecast.isoWeekday(today))
    assertEquals(5, DemandForecast.isoWeekday(CivilDays.jdnOfIso("2026-10-30")!!))
    val f = DemandForecast.forecast(Input(today, s23, onHandMilli = 60_000, leadDays = 5, windowDays = 84))
    assertEquals(12_000, f.dailyMilli)
    assertEquals(listOf(833, 833, 833, 833, 2000, 833, 833), f.weekdayPermille)
    assertEquals(5, f.peakWeekday)
    //  سه‌شنبه تا شنبه: ۱۰+۱۰+۱۰+۲۴+۱۰ = ۶۴ ⇒ ۶۰ چهار روز بس است، نه پنج
    assertEquals(4, f.daysLeft)
    assertFalse(f.daysLeftBeyond)
    assertEquals(64_000, f.reorderPointMilli)
    assertEquals(Reason.REORDER_POINT, f.reason)
    //  ۵ + ۱۴ = ۱۹ روز: سه جمعه (۷۲) و ۱۶ روزِ دیگر (۱۶۰) = ۲۳۲ − ۶۰
    assertEquals(172_000, f.qtyMilli)
    assertEquals(Confidence.HIGH, f.confidence)
    assertEquals("میانگینِ روزانه ۱۲، روزِ جمعه ۲ برابر، ۴ روز موجودی مانده، زمانِ تأمین ۵ روز", DemandForecast.why(f))
    assertEquals("4 روز", DemandForecast.coverText(f, LocaleFormat.DIGITS_LATN))
    //  جمعه چهارمین روزِ آینده است، نه پنجمین: ۵۰ فقط سه روز (۳۰) بس است، چون جمعه ۲۴ می‌خواهد
    assertEquals(3, DemandForecast.forecast(Input(today, s23, onHandMilli = 50_000, leadDays = 5, windowDays = 84)).daysLeft)
    //  پنجشنبه: فردا جمعه است ⇒ ۲۰ حتی یک روز بس نیست
    val thu = today + 3
    val hist = days(thu - 83, thu) { if (DemandForecast.isoWeekday(it) == 5) 24_000 else 10_000 }
    assertEquals(0, DemandForecast.forecast(Input(thu, hist, onHandMilli = 20_000, leadDays = 5, windowDays = 84)).daysLeft)
    assertEquals(1, DemandForecast.forecast(Input(thu, hist, onHandMilli = 24_000, leadDays = 5, windowDays = 84)).daysLeft)
  }

  @Test fun shortWindowIsBitForBitReorderCore() {
    val cases = listOf(
      Triple(2_000L, 37_500L, 3), Triple(0L, 1_000L, 7), Triple(10_000L, 0L, 0), Triple(-5_000L, 12_345L, 4),
    )
    for ((onHand, perDay, lead) in cases) {
      for (min in listOf(0L, 4_000L)) for (whole in listOf(true, false)) for (measured in listOf(true, false)) {
        val hist = days(today - 19, today) { perDay }
        val f = DemandForecast.forecast(
          Input(today, hist, onHand, onOrderMilli = 1_000, minStockMilli = min, leadDays = lead.takeIf { measured },
            windowDays = 20, wholeUnits = whole),
        )
        val r = ReorderCore.suggest(
          ReorderCore.Input(onHand, 1_000, min, perDay * 20, windowDays = 20, leadDays = lead.takeIf { measured }, wholeUnits = whole),
        )
        val tag = "onHand=$onHand perDay=$perDay lead=$lead min=$min whole=$whole measured=$measured"
        assertNull(f.weekdayPermille, tag)
        assertEquals(r.qtyMilli, f.qtyMilli, tag)
        assertEquals(r.reason, f.reason, tag)
        assertEquals(r.dailyMilli, f.dailyMilli, tag)
        assertEquals(r.reorderPointMilli, f.reorderPointMilli, tag)
        assertEquals(r.positionMilli, f.positionMilli, tag)
        assertEquals(r.leadDaysUsed, f.leadDaysUsed, tag)
      }
    }
  }

  @Test fun flatSalesGiveSameDemandWithOrWithoutWeekdays() {
    val hist = days(today - 29, today) { 2_000 }
    val f = DemandForecast.forecast(Input(today, hist, onHandMilli = 5_000, leadDays = 3, windowDays = 30))
    assertEquals(List(7) { 1000 }, f.weekdayPermille)
    assertNull(f.peakWeekday)
    assertEquals(6_000, f.reorderPointMilli)
    assertEquals(2, f.daysLeft)
    //  ۳ + ۱۴ روز × ۲ = ۳۴ − ۵
    assertEquals(29_000, f.qtyMilli)
  }

  @Test fun seasonFromLastYearSameDays() {
    //  پارسال: پنجرهٔ ۹۰ روزه روزی ۱، ۲۱ روزِ پس از آن روزی ۲؛ امسال روزی ۳
    val ly = today - 365
    val hist = days(ly - 89, ly) { 1_000 } + days(ly + 1, ly + 21) { 2_000 } + days(today - 89, today) { 3_000 }
    val f = DemandForecast.forecast(Input(today, hist, onHandMilli = 10_000, leadDays = null, firstJdn = today - 500))
    assertEquals(2000, f.seasonPermille)
    assertEquals(7, f.leadDaysUsed)
    assertFalse(f.leadMeasured)
    assertEquals(42_000, f.reorderPointMilli)
    assertEquals(116_000, f.qtyMilli)
    assertEquals(1, f.daysLeft)
    assertEquals(Confidence.MEDIUM, f.confidence)
    assertEquals(
      "میانگینِ روزانه ۳، همین روزهای پارسال ۲ برابر، ۱ روز موجودی مانده، زمانِ تأمین ۷ روز (سنجیده نشده)",
      DemandForecast.why(f),
    )
    //  جهشِ ده برابر سقفِ ۳ دارد
    val spike = days(ly - 89, ly) { 1_000 } + days(ly + 1, ly + 21) { 10_000 } + days(today - 89, today) { 3_000 }
    assertEquals(DemandForecast.SEASON_MAX, DemandForecast.forecast(Input(today, spike, 0, leadDays = 7, firstJdn = today - 500)).seasonPermille)
  }

  @Test fun seasonUnknownWithoutFullYearOrLastYearSales() {
    val ly = today - 365
    val hist = days(ly - 89, ly) { 1_000 } + days(ly + 1, ly + 21) { 2_000 } + days(today - 89, today) { 3_000 }
    //  نخستین روز دیرتر از آغازِ پنجرهٔ پارسال
    assertNull(DemandForecast.forecast(Input(today, hist, 0, leadDays = 7, firstJdn = ly - 80)).seasonPermille)
    //  بی firstJdn هم نه
    assertNull(DemandForecast.forecast(Input(today, hist, 0, leadDays = 7)).seasonPermille)
    //  پنجرهٔ پارسال بی فروش
    val noBase = days(ly + 1, ly + 21) { 2_000 } + days(today - 89, today) { 3_000 }
    assertNull(DemandForecast.forecast(Input(today, noBase, 0, leadDays = 7, firstJdn = today - 500)).seasonPermille)
  }

  @Test fun newProductIsNotDilutedByDaysBeforeItExisted() {
    val hist = days(today - 9, today) { 5_000 }
    val f = DemandForecast.forecast(Input(today, hist, onHandMilli = 20_000, leadDays = 2, firstJdn = today - 9))
    assertEquals(10, f.windowDaysUsed)
    assertEquals(5_000, f.dailyMilli)
    assertNull(f.weekdayPermille)
    assertEquals(4, f.daysLeft)
    //  همان کالا بی firstJdn: ۵۰ در ۹۰ روز
    assertEquals(556, DemandForecast.forecast(Input(today, hist, onHandMilli = 20_000, leadDays = 2)).dailyMilli)
  }

  @Test fun noSalesBelowMinEnoughAndEmptyStock() {
    val low = DemandForecast.forecast(Input(today, emptyList(), onHandMilli = 2_000, minStockMilli = 5_000, leadDays = null))
    assertEquals(Reason.BELOW_MIN, low.reason)
    assertEquals(3_000, low.qtyMilli)
    assertNull(low.daysLeft)
    assertEquals(Confidence.LOW, low.confidence)
    assertEquals("در ۹۰ روز فروشی نبود، زیرِ حدِ کم، زمانِ تأمین ۷ روز (سنجیده نشده)", DemandForecast.why(low))
    assertEquals("بی فروش", DemandForecast.coverText(low))

    val enough = DemandForecast.forecast(Input(today, emptyList(), onHandMilli = 2_000, leadDays = 3))
    assertEquals(Reason.ENOUGH, enough.reason)
    assertEquals(0, enough.qtyMilli)

    //  برگشتِ بیش از فروش ⇒ فروشی نیست
    val net = DemandForecast.forecast(Input(today, listOf(Day(today, 3_000), Day(today - 1, -4_000)), 1_000, leadDays = 3))
    assertNull(net.daysLeft)
    assertEquals(0, net.dailyMilli)

    val out = DemandForecast.forecast(Input(today, s23, onHandMilli = -3_000, leadDays = 5, windowDays = 84))
    assertEquals(0, out.daysLeft)
    assertTrue(DemandForecast.why(out).contains("موجودی تمام است"))
    assertEquals("تمام", DemandForecast.coverText(out))

    val lots = DemandForecast.forecast(Input(today, s23, onHandMilli = 1_000_000_000, leadDays = 5, windowDays = 84))
    assertEquals(DemandForecast.MAX_COVER_DAYS, lots.daysLeft)
    assertTrue(lots.daysLeftBeyond)
    assertEquals(Reason.ENOUGH, lots.reason)
    assertEquals("بیش از ۳۶۵ روز", DemandForecast.coverText(lots))
  }

  @Test fun sameDayRowsSumAndOutsideWindowIgnored() {
    val hist = listOf(Day(today, 1_000), Day(today, 2_000), Day(today - 30, 50_000), Day(today + 1, 9_000))
    val f = DemandForecast.forecast(Input(today, hist, onHandMilli = 0, leadDays = 1, windowDays = 10))
    assertEquals(1, f.saleDays)
    assertEquals(300, f.dailyMilli)
  }

  @Test fun onOrderCountsInPositionNotInDaysLeft() {
    val f = DemandForecast.forecast(Input(today, s23, onHandMilli = 60_000, onOrderMilli = 2_000, leadDays = 5, windowDays = 84))
    assertEquals(4, f.daysLeft)
    assertEquals(62_000, f.positionMilli)
    assertEquals(170_000, f.qtyMilli)
    assertTrue(DemandForecast.why(f).contains("۲ در راه"))
    //  «در راه» ِ بیش از نقطهٔ سفارش ⇒ کافی، ولی روزهای موجودی همان ۴
    val covered = DemandForecast.forecast(Input(today, s23, onHandMilli = 60_000, onOrderMilli = 100_000, leadDays = 5, windowDays = 84))
    assertEquals(Reason.ENOUGH, covered.reason)
    assertEquals(4, covered.daysLeft)
  }

  @Test fun weighedUnitsKeepMilli() {
    val hist = days(today - 29, today) { 333 }
    val f = DemandForecast.forecast(Input(today, hist, onHandMilli = 0, leadDays = 2, windowDays = 30, wholeUnits = false))
    //  ۱۶ روز × ۳۳۳ = ۵۳۲۸ هزارم، بی گرد تا واحد
    assertEquals(5_328, f.qtyMilli)
  }

  @Test fun inputBounds() {
    assertFailsWith<IllegalArgumentException> { DemandForecast.forecast(Input(today, emptyList(), 0, leadDays = 1, windowDays = 0)) }
    assertFailsWith<IllegalArgumentException> { DemandForecast.forecast(Input(today, emptyList(), 0, leadDays = -1)) }
    assertFailsWith<IllegalArgumentException> { DemandForecast.forecast(Input(today, emptyList(), 0, leadDays = 1, coverDays = 400)) }
  }

  @Test fun textHelpers() {
    assertEquals("۱۲.۵", DemandForecast.qtyText(12_500, LocaleFormat.DIGITS_FA))
    assertEquals("0.25", DemandForecast.qtyText(250, LocaleFormat.DIGITS_LATN))
    assertEquals("۱.۵", DemandForecast.factorText(1_500, LocaleFormat.DIGITS_FA))
    assertEquals("۲", DemandForecast.factorText(2_000, LocaleFormat.DIGITS_FA))
    assertEquals("۱.۳", DemandForecast.factorText(1_250, LocaleFormat.DIGITS_FA))
    assertEquals("شنبه", DemandForecast.weekdayName(6))
    assertEquals("اطمینانِ بالا", DemandForecast.confidenceText(Confidence.HIGH))
  }

  @Test fun latestSupplierSkipsCreditNotes() {
    val buys = listOf(
      Buy("s1", "p1", "2026-10-01", 10, 5_000, 100),
      Buy("s2", "p1", "2026-10-05", 20, 5_000, 100),
      Buy("s1", "p1", "2026-10-09", 30, -1_000, 100),
      Buy("s3", "p2", "2026-10-09", 40, -1_000, 100),
    )
    assertEquals("s2", SupplierPricing.latestSupplier(buys, "p1"))
    assertNull(SupplierPricing.latestSupplier(buys, "p2"))
    assertNull(SupplierPricing.latestSupplier(buys, "p3"))
  }
}
