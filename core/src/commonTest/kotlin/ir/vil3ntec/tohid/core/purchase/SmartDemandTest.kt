package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.purchase.DemandForecast.Day
import ir.vil3ntec.tohid.core.purchase.DemandForecast.Input
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Confidence
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Reason
import ir.vil3ntec.tohid.core.purchase.SmartDemand.Method
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AI-07 — میانگینِ متحرک با روزِ هفته و Croston/SBA، با اطمینان و دلیل؛ همهٔ عددها دستی */
class SmartDemandTest {

  /** دوشنبه؛ فردا سه‌شنبه */
  private val today = CivilDays.jdnOfIso("2026-10-26")!!

  private fun days(from: Int, to: Int, qty: (Int) -> Long) = (from..to).map { Day(it, qty(it)) }
  private fun fri(j: Int) = DemandForecast.isoWeekday(j) == 5

  /** S23: ۸۴ روز، جمعه ۲۴ و بقیه ۱۰ */
  private val s23 = days(today - 83, today) { if (fri(it)) 24_000 else 10_000 }

  /** ۳۰ روز، هر ۶ روز یک فروشِ ۲ تایی (روزهای ۲، ۸، ۱۴، ۲۰، ۲۶ ِ پنجره) */
  private val sparse = listOf(2, 8, 14, 20, 26).map { Day(today - 29 + it, 2_000) }

  @Test fun methodRuleIsTheShareOfZeroSaleDays() {
    //  نیمی از روزها بی فروش ⇒ Croston (مرز همان نیمه)
    assertEquals(Method.CROSTON, SmartDemand.methodOf(90, 45))
    assertEquals(Method.MOVING_AVERAGE, SmartDemand.methodOf(90, 46))
    assertEquals(Method.CROSTON, SmartDemand.methodOf(2, 1))
    //  بی فروش و تک‌روزِ پرفروش ⇒ میانگینِ متحرک
    assertEquals(Method.MOVING_AVERAGE, SmartDemand.methodOf(90, 0))
    assertEquals(Method.MOVING_AVERAGE, SmartDemand.methodOf(1, 1))
  }

  @Test fun s23MovingAverageKeepsTheFridayPatternAndExplainsIt() {
    val f = DemandForecast.forecast(Input(today, s23, onHandMilli = 60_000, leadDays = 5, windowDays = 84, smart = true))
    val s = assertNotNull(f.smart)
    assertEquals(Method.MOVING_AVERAGE, s.method)
    //  ۲۸ روزِ اخیر: ۴ × (۲۴ + ۶ × ۱۰) = ۳۳۶ ⇒ ۱۲ در روز، همان میانگینِ پنجره ⇒ ضریبِ سطح ۱
    assertEquals(12_000, f.dailyMilli)
    assertEquals(28, s.levelDays)
    assertEquals(0, s.zeroPermille)
    assertEquals(listOf(10_000L, 10_000, 10_000, 24_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 24_000, 10_000, 10_000, 10_000), s.perDayMilli)
    assertEquals(4, f.daysLeft)
    assertEquals(0, s.cvPermille)
    assertEquals(Confidence.HIGH, f.confidence)
    assertEquals(
      "میانگینِ روزانه ۱۲ (۲۸ روزِ اخیر)، روزِ جمعه ۲ برابر، ۴ روز موجودی مانده، زمانِ تأمین ۵ روز، نوسان ۰٪",
      DemandForecast.why(f),
    )
    //  همان مقدار و روزِ حسابِ پیشین (سطحِ اخیر همان میانگین است)
    val old = DemandForecast.forecast(Input(today, s23, onHandMilli = 60_000, leadDays = 5, windowDays = 84))
    assertEquals(old.qtyMilli, f.qtyMilli)
    assertEquals(old.reorderPointMilli, f.reorderPointMilli)
  }

  @Test fun movingAverageFollowsTheRecentLevelWithWeekdayMultipliers() {
    //  ۵۶ روز: ۲۸ روزِ نخست جمعه ۲۰ و بقیه ۱۰، ۲۸ روزِ اخیر جمعه ۳۰ و بقیه ۱۵
    val h = days(today - 55, today) { j -> if (j <= today - 28) (if (fri(j)) 20_000 else 10_000) else (if (fri(j)) 30_000 else 15_000) }
    val f = DemandForecast.forecast(Input(today, h, onHandMilli = 100_000, leadDays = 5, windowDays = 56, smart = true))
    val s = assertNotNull(f.smart)
    //  پنجره ۸۰۰ ÷ ۵۶؛ جمعه ۲۵ ÷ ۱۴٫۲۸۶ = ۱٫۷۵ و بقیه ۰٫۸۷۵
    assertEquals(listOf(875, 875, 875, 875, 1750, 875, 875), f.weekdayPermille)
    //  سطحِ ۲۸ روزِ اخیر ۴۸۰ ÷ ۲۸ = ۱۷٫۱۴۳؛ نسبت به پنجره ۴۸۰ × ۵۶ ÷ (۲۸ × ۸۰۰) = ۱٫۲ ⇒ جمعه ۲۵ × ۱٫۲ = ۳۰، بقیه ۱۵
    assertEquals(17_143, f.dailyMilli)
    assertEquals(listOf(15_000L, 15_000, 15_000, 30_000, 15_000, 15_000, 15_000), s.perDayMilli.take(7))
    //  سه‌شنبه تا شنبه ۱۵ + ۱۵ + ۱۵ + ۳۰ + ۱۵ = ۹۰ ≤ ۱۰۰ و یکشنبه ۱۰۵ ⇒ پنج روز (حسابِ پیشین با ۱۲٫۵/۲۵: هفت روز)
    assertEquals(5, f.daysLeft)
    assertEquals(7, DemandForecast.forecast(Input(today, h, onHandMilli = 100_000, leadDays = 5, windowDays = 56)).daysLeft)
    assertEquals(
      "میانگینِ روزانه 17.143 (28 روزِ اخیر)، روزِ جمعه 1.8 برابر، 5 روز موجودی مانده، زمانِ تأمین 5 روز",
      DemandForecast.why(f, LocaleFormat.DIGITS_LATN).substringBeforeLast("، نوسان"),
    )
  }

  @Test fun crostonOnAnEvenlyIntermittentSeries() {
    val f = DemandForecast.forecast(Input(today, sparse, onHandMilli = 1_000, leadDays = 5, windowDays = 30, smart = true))
    val s = assertNotNull(f.smart)
    assertEquals(Method.CROSTON, s.method)
    //  ۲۵ روزِ بی فروش از ۳۰ = ۸۳۳ هزارم؛ z = ۱۰ ÷ ۵ = ۲، p = ۳۰ ÷ ۵ = ۶ و فاصله‌ها همه ۶ ⇒ بی جابه‌جایی
    assertEquals(833, s.zeroPermille)
    assertEquals(2_000L, s.sizeMilli)
    assertEquals(6_000L, s.intervalMilli)
    //  ۲ ÷ ۶ × ۰٫۹۵ = ۰٫۳۱۶۷
    assertEquals(317, f.dailyMilli)
    assertEquals(listOf(317L, 316, 317), s.perDayMilli.take(3))
    //  مصرفِ n روز = ۲ × n × ۰٫۹۵ ÷ ۶: سه روز ۰٫۹۵ ≤ ۱ و چهار روز ۱٫۲۶۷
    assertEquals(3, f.daysLeft)
    //  نقطهٔ سفارش ۵ روز ۱٫۵۸۳؛ هدف ۱۹ روز ۶٫۰۱۷ − ۱ = ۵٫۰۱۷ ⇒ ۶ واحد
    assertEquals(1_583, f.reorderPointMilli)
    assertEquals(6_000, f.qtyMilli)
    assertEquals(Reason.REORDER_POINT, f.reason)
    //  کم‌فروش روزِ اوج ندارد
    assertNull(f.peakWeekday)
    assertEquals(0, s.cvPermille)
    //  پنج فروش ⇒ «بالا» نه (هشت لازم است)؛ دست‌کم سه ⇒ «متوسط»
    assertEquals(Confidence.MEDIUM, f.confidence)
    assertEquals(
      "فروشِ گاه‌به‌گاه (۸۳٪ روزها بی فروش)، هر ۶ روز یک بار حدودِ ۲، روزانه ۰.۳۱۷، ۳ روز موجودی مانده، زمانِ تأمین ۵ روز، نوسان ۰٪",
      DemandForecast.why(f),
    )
  }

  @Test fun crostonSmoothsSizeAndIntervalAtEachSale() {
    //  ۲۰ روز، فروش روزهای ۱ (۱)، ۵ (۳) و ۱۳ (۲)
    val h = listOf(Day(today - 19 + 1, 1_000), Day(today - 19 + 5, 3_000), Day(today - 19 + 13, 2_000))
    val f = DemandForecast.forecast(Input(today, h, onHandMilli = 0, leadDays = null, windowDays = 20, smart = true))
    val s = assertNotNull(f.smart)
    assertEquals(Method.CROSTON, s.method)
    assertEquals(850, s.zeroPermille)
    //  z₀ = ۲، p₀ = ۶٫۶۶۷؛ فروشِ دوم (۳، فاصلهٔ ۴): z = ۲٫۱، p = ۶٫۴؛ سوم (۲، فاصلهٔ ۸): z = ۲٫۰۹، p = ۶٫۵۶
    assertEquals(2_090L, s.sizeMilli)
    assertEquals(6_560L, s.intervalMilli)
    //  ۲٫۰۹ ÷ ۶٫۵۶ × ۰٫۹۵ = ۰٫۳۰۲۷
    assertEquals(303, f.dailyMilli)
    assertEquals(2_119L, s.perDayMilli.take(7).sum())
    //  نوسانِ اندازه‌ها (۱، ۳، ۲) ۰٫۴۰۸ و فاصله‌ها (۴، ۸) ۰٫۳۳۳ ⇒ بیشینه
    assertEquals(408, s.cvPermille)
    assertEquals(Confidence.MEDIUM, f.confidence)
    assertEquals(0, f.daysLeft)
  }

  @Test fun confidenceComesFromDataLengthAndVariance() {
    fun conf(h: List<Day>, window: Int, lead: Int?, first: Int? = null) =
      DemandForecast.forecast(Input(today, h, onHandMilli = 0, leadDays = lead, windowDays = window, firstJdn = first, smart = true))
    //  ۵۶ روز، ۱۰ سپس ۳۰ ⇒ میانگینِ هر روزِ هفته ۲۰ و انحراف ±۱۰: نوسان دقیقاً ۰٫۵ (مرز) ⇒ با زمانِ تأمینِ سنجیده «بالا»
    val step = days(today - 55, today) { if (it <= today - 28) 10_000 else 30_000 }
    assertEquals(500, conf(step, 56, 5).smart!!.cvPermille)
    assertEquals(Confidence.HIGH, conf(step, 56, 5).confidence)
    assertEquals(Confidence.MEDIUM, conf(step, 56, null).confidence)
    //  ۲۸ روز، سه هفته ۱ و هفتهٔ آخر ۵۰ ⇒ نوسان ۱٫۶ > ۱ ⇒ «پایین» حتی با زمانِ تأمین
    val noisy = days(today - 27, today) { if (it > today - 7) 50_000 else 1_000 }
    assertEquals(1601, conf(noisy, 28, 5).smart!!.cvPermille)
    assertEquals(Confidence.LOW, conf(noisy, 28, 5).confidence)
    //  کالای تازه: ۱۴ روز ⇒ «متوسط»، ۱۳ روز ⇒ «پایین»
    assertEquals(Confidence.MEDIUM, conf(days(today - 13, today) { 5_000 }, 90, 5, first = today - 13).confidence)
    assertEquals(Confidence.LOW, conf(days(today - 12, today) { 5_000 }, 90, 5, first = today - 12).confidence)
  }

  @Test fun edgeCasesNoSalesSingleSaleNewProductNegativeStockAndReturns() {
    //  بی فروش: میانگینِ صفر، «پایین»، بی نوسان، همان دلیلِ پیشین
    val none = DemandForecast.forecast(Input(today, emptyList(), onHandMilli = 5_000, leadDays = 5, smart = true))
    assertEquals(Method.MOVING_AVERAGE, none.smart!!.method)
    assertEquals(List(SmartDemand.FORECAST_DAYS) { 0L }, none.smart!!.perDayMilli)
    assertNull(none.smart!!.cvPermille)
    assertNull(none.daysLeft)
    assertEquals(0, none.qtyMilli)
    assertEquals(Confidence.LOW, none.confidence)
    assertEquals("در ۹۰ روز فروشی نبود، زمانِ تأمین ۵ روز", DemandForecast.why(none))

    //  تک فروشِ ۳ تایی در ۹۰ روز ⇒ Croston با p = ۹۰: روزانه ۳ ÷ ۹۰ × ۰٫۹۵ = ۰٫۰۳۲؛ ۳۱ روز ۰٫۹۸۲ ≤ ۱
    val one = DemandForecast.forecast(Input(today, listOf(Day(today - 40, 3_000)), onHandMilli = 1_000, leadDays = 5, smart = true))
    assertEquals(Method.CROSTON, one.smart!!.method)
    assertEquals(32, one.dailyMilli)
    assertEquals(31, one.daysLeft)
    assertEquals(Confidence.LOW, one.confidence)

    //  کالای تازه با یک روز (امروز ۴) ⇒ میانگینِ همان یک روز
    val fresh = DemandForecast.forecast(Input(today, listOf(Day(today, 4_000)), onHandMilli = 10_000, leadDays = 5, firstJdn = today, smart = true))
    assertEquals(Method.MOVING_AVERAGE, fresh.smart!!.method)
    assertEquals(1, fresh.smart!!.levelDays)
    assertEquals(4_000, fresh.dailyMilli)
    assertEquals(2, fresh.daysLeft)
    assertEquals(Confidence.LOW, fresh.confidence)

    //  موجودیِ منفی: صفر روز و مقدارِ بیشتر (هدفِ ۱۹ روزِ S23 = ۲۳۲ + ۵)
    val neg = DemandForecast.forecast(Input(today, s23, onHandMilli = -5_000, leadDays = 5, windowDays = 84, smart = true))
    assertEquals(0, neg.daysLeft)
    assertEquals(237_000, neg.qtyMilli)

    //  برگشتیِ بیش از فروشِ یک روز: آن روز «بی فروش»، جمعِ پنجره خالص
    val ret = days(today - 13, today) { 3_000 } + Day(today - 5, -5_000)
    val r = DemandForecast.forecast(Input(today, ret, onHandMilli = 0, leadDays = 5, firstJdn = today - 13, smart = true))
    assertEquals(13, r.saleDays)
    assertEquals(Method.MOVING_AVERAGE, r.smart!!.method)
    //  (۱۳ × ۳ − ۲) ÷ ۱۴ = ۲٫۶۴۳، همان میانگینِ حسابِ پیشین
    assertEquals(2_643, r.dailyMilli)
    assertEquals(DemandForecast.forecast(Input(today, ret, onHandMilli = 0, leadDays = 5, firstJdn = today - 13)).dailyMilli, r.dailyMilli)
    assertTrue(r.smart!!.perDayMilli.all { it >= 0 })
  }

  @Test fun smartOffLeavesTheForecastUnchanged() {
    for (i in listOf(
      Input(today, s23, onHandMilli = 60_000, leadDays = 5, windowDays = 84),
      Input(today, sparse, onHandMilli = 1_000, leadDays = 5, windowDays = 30),
      Input(today, emptyList(), onHandMilli = 5_000, leadDays = null, minStockMilli = 9_000),
    )) {
      val off = DemandForecast.forecast(i)
      assertNull(off.smart)
      assertEquals(off, DemandForecast.forecast(i.copy(smart = false)))
      assertEquals(DemandForecast.why(off), DemandForecast.why(DemandForecast.forecast(i.copy(smart = false))))
      assertFalse(DemandForecast.why(off).contains("نوسان"))
    }
    //  همان دلیلِ S23 ِ پیشین
    assertEquals(
      "میانگینِ روزانه ۱۲، روزِ جمعه ۲ برابر، ۴ روز موجودی مانده، زمانِ تأمین ۵ روز",
      DemandForecast.why(DemandForecast.forecast(Input(today, s23, onHandMilli = 60_000, leadDays = 5, windowDays = 84))),
    )
  }

  @Test fun textHelpers() {
    assertEquals("۸۳٪", SmartDemand.percentText(833))
    assertEquals("25%".replace('%', '٪'), SmartDemand.percentText(250, LocaleFormat.DIGITS_LATN))
    val f = DemandForecast.forecast(Input(today, s23, onHandMilli = 60_000, leadDays = 5, windowDays = 84, smart = true))
    assertEquals("10، 10، 10، 24، 10", SmartDemand.nextDaysText(f.smart!!, 5, LocaleFormat.DIGITS_LATN))
    assertEquals("میانگینِ متحرک و روزِ هفته", SmartDemand.methodText(Method.MOVING_AVERAGE))
  }
}
