package ir.vil3ntec.tohid.core.risk

import ir.vil3ntec.tohid.core.risk.Anomalies.Adjust
import ir.vil3ntec.tohid.core.risk.Anomalies.Kind
import ir.vil3ntec.tohid.core.risk.Anomalies.Line
import ir.vil3ntec.tohid.core.risk.Anomalies.SaleAt
import ir.vil3ntec.tohid.core.risk.Anomalies.Series
import ir.vil3ntec.tohid.core.risk.Anomalies.Severity
import ir.vil3ntec.tohid.core.risk.Anomalies.Shift
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AI-08 — هر قاعده در مرز (برابرِ حد نه، یکی بیشتر آری) و حصارِ Tukey با عددهای دستی */
class AnomaliesTest {

  private val latn = Anomalies.Text(LocaleFormat.DIGITS_LATN)
  private val day = 2_461_000

  @Test fun cashierFindingsReuseRsk10Alerts() {
    //  همان هشدارِ هستهٔ RSK-10: علی ۶ لغو در ۱۰ فاکتور، بقیه ۲ در ۲۰ ⇒ ۶۰۰٪
    val alerts = CashierAlerts.alerts(
      listOf(CashierAlerts.Counts("ali", 10, 6, 0, 0), CashierAlerts.Counts("sara", 20, 2, 0, 0)),
    )
    val f = Anomalies.cashier(alerts)
    assertEquals(1, f.size)
    assertEquals(Kind.CASHIER_VOID, f[0].kind)
    assertEquals(Severity.HIGH, f[0].severity)
    assertEquals(600L, f[0].evidence["ratioPct"])
    assertEquals("ali: ۶ لغوِ فاکتور در ۱۰ فاکتور، ۶۰۰٪ نرخِ بقیه (۲ در ۲۰) — نیازمندِ بررسی", f[0].reason)
    //  مرزِ «بالا» دو برابرِ حد (۲۰۰٪ ⇒ ۴۰۰٪)
    fun sev(ratio: Long) = Anomalies.cashier(listOf(CashierAlerts.Alert("x", CashierAlerts.Kind.DISCOUNT, 3, 10, 3, 20, ratio))).single().severity
    assertEquals(Severity.MEDIUM, sev(399))
    assertEquals(Severity.HIGH, sev(400))
    assertEquals(Kind.CASHIER_NO_SALE, Anomalies.cashier(listOf(CashierAlerts.Alert("x", CashierAlerts.Kind.NO_SALE, 3, 10, 0, 20, 600))).single().kind)
    assertTrue(Anomalies.cashier(emptyList()).isEmpty())
  }

  @Test fun shiftOverShortBeyondTolerance() {
    fun f(counted: Long) = Anomalies.shifts(listOf(Shift("s1", "ali", day, 500_000, counted)), t = latn)
    //  حدِ ۱۰۰ ؋: کسریِ دقیقاً ۱۰۰ نه، ۱۰۰٫۰۱ آری
    assertTrue(f(490_000).isEmpty())
    assertTrue(f(510_000).isEmpty())
    val short = f(489_999).single()
    assertEquals(Severity.MEDIUM, short.severity)
    assertEquals(-10_001L, short.evidence["diff"])
    assertEquals("صندوقِ ali: کسری 100.01 ؋ (مورد انتظار 5,000 ؋، شمرده 4,899.99 ؋)، بیش از حدِ 100 ؋ — نیازمندِ بررسی", short.reason)
    //  «بالا» بیش از سه برابرِ حد
    assertEquals(Severity.MEDIUM, f(530_000).single().severity)
    assertEquals(Severity.HIGH, f(530_001).single().severity)
    assertTrue(f(530_001).single().reason.contains("اضافه"))
  }

  @Test fun negativeStockAdjustmentBeyondThreshold() {
    fun f(qty: Long, cost: Long = 10_000) = Anomalies.stock(listOf(Adjust("m1", "p1", "چای", "ali", day, qty, cost)), t = latn)
    //  ۱۰ واحد × ۱۰۰ ؋ = ۱٬۰۰۰ ؋ = حد ⇒ نه؛ ۱۰٫۰۰۱ واحد ⇒ ۱٬۰۰۰٫۱۰ ؋ ⇒ آری
    assertTrue(f(-10_000).isEmpty())
    val one = f(-10_001).single()
    assertEquals(Severity.MEDIUM, one.severity)
    assertEquals(100_010L, one.evidence["value"])
    assertEquals("کمِ موجودیِ چای با «اصلاح» (ali): 10.001 واحد، حدودِ 1,000.10 ؋، بیش از حدِ 1,000 ؋ — نیازمندِ بررسی", one.reason)
    //  «بالا» بیش از پنج برابر
    assertEquals(Severity.MEDIUM, f(-50_000).single().severity)
    assertEquals(Severity.HIGH, f(-50_001).single().severity)
    //  افزایش هرگز؛ کالای بی بها با مقدار (۱۰ واحد)
    assertTrue(f(90_000).isEmpty())
    assertTrue(f(-10_000, cost = 0).isEmpty())
    assertEquals(10_000L, f(-10_001, cost = 0).single().evidence["limit"])
  }

  @Test fun salesBelowCostGroupedPerInvoice() {
    fun line(net: Long, cost: Long = 5_000, sale: String = "a") = Line(sale, "#7", "ali", day, 2_000, net, cost)
    //  بهای ۲ × ۵۰ = ۱۰۰ ؋؛ زیانِ دقیقاً ۱ ؋ گردکردن است ⇒ نه؛ ۱٫۰۱ ⇒ آری
    assertTrue(Anomalies.belowCost(listOf(line(9_900))).isEmpty())
    val f = Anomalies.belowCost(listOf(line(9_899)), t = latn).single()
    assertEquals(Severity.MEDIUM, f.severity)
    assertEquals(101L, f.evidence["loss"])
    assertEquals("فاکتورِ #7 (ali): 1 قلم زیرِ بها، فروش 98.99 ؋ در برابرِ بهای 100 ؋ (کمتر 1.01 ؋) — نیازمندِ بررسی", f.reason)
    //  نیمِ بها یا کمتر ⇒ «بالا»؛ دو قلمِ یک فاکتور یک یافته؛ قلمِ بی بها و قلمِ سودده بیرون
    assertEquals(Severity.HIGH, Anomalies.belowCost(listOf(line(5_000))).single().severity)
    val two = Anomalies.belowCost(listOf(line(9_000), line(8_000), line(0, cost = 0), line(20_000))).single()
    assertEquals(2L, two.evidence["lines"])
    assertEquals(3_000L, two.evidence["loss"])
    assertEquals(2, Anomalies.belowCost(listOf(line(9_000), line(9_000, sale = "b"))).size)
  }

  @Test fun salesAtUnusualHours() {
    fun at(m: Int, who: String = "ali", d: Int = day) = SaleAt("s$m$who$d", who, d, m, 10_000)
    //  ۰۰:۰۰ تا ۰۶:۰۰: ۵:۵۹ آری، ۶:۰۰ نه
    assertTrue(Anomalies.oddHours(listOf(at(360), at(720))).isEmpty())
    val one = Anomalies.oddHours(listOf(at(359)), t = latn).single()
    assertEquals(Severity.LOW, one.severity)
    assertEquals("ali: 1 فاکتور میانِ 00:00 و 06:00 (نخستین 05:59)، جمعاً 100 ؋ — نیازمندِ بررسی", one.reason)
    //  هر کاربر و روز یک یافته؛ سه فاکتور ⇒ «متوسط»
    val g = Anomalies.oddHours(listOf(at(0), at(10), at(20), at(30, "sara"), at(40, d = day + 1)))
    assertEquals(3, g.size)
    assertEquals(Severity.MEDIUM, g.first { it.subject == "ali" && it.dayJdn == day }.severity)
    //  پنجرهٔ ۲۳:۰۰ تا ۰۵:۰۰ از نیمه‌شب می‌گذرد؛ «نخستین» از آغازِ پنجره
    val wrap = Anomalies.Policy(quietFrom = 23 * 60, quietTo = 5 * 60)
    assertTrue(Anomalies.quiet(1380, 1380, 300) && Anomalies.quiet(299, 1380, 300))
    assertFalse(Anomalies.quiet(1379, 1380, 300) || Anomalies.quiet(300, 1380, 300))
    assertFalse(Anomalies.quiet(100, 200, 200))
    val w = Anomalies.oddHours(listOf(at(60), at(1400), at(700)), wrap).single()
    assertEquals(1400L, w.evidence["firstMinute"])
    assertEquals(2L, w.evidence["count"])
  }

  /** ۱۰ روزِ معمول + روزِ آخر */
  private fun returns(last: Long) = Series(Kind.DAY_RETURNS, "", day, listOf(1_000L, 1_200, 1_100, 1_300, 1_200, 1_000, 1_100, 1_200, 1_300, 1_100, last))

  @Test fun tukeyFenceOnDailyTotals() {
    //  مرتب: ۱۰۰۰، ۱۰۰۰، ۱۱۰۰، ۱۱۰۰، ۱۱۰۰، ۱۲۰۰، ۱۲۰۰، ۱۲۰۰، ۱۳۰۰، ۱۳۰۰، x — Q1 = ۱۱۰۰، میانه ۱۲۰۰، Q3 = ۱۲۵۰، IQR = ۱۵۰
    val fence = Anomalies.fence(returns(6_000).values, 100)
    assertEquals(4_400L, fence.q1x4)
    assertEquals(4_800L, fence.median4)
    assertEquals(5_000L, fence.q3x4)
    //  حصار ۱۲۵۰ + ۱٫۵ × ۱۵۰ = ۱۴۷۵ و دوردست ۱۲۵۰ + ۳ × ۱۵۰ = ۱۷۰۰
    assertEquals(1_475L * 8, fence.inner8)
    assertEquals(1_700L * 8, fence.outer8)
    assertTrue(Anomalies.outliers(returns(1_475)).isEmpty())
    assertEquals(Severity.MEDIUM, Anomalies.outliers(returns(1_476)).single().severity)
    assertEquals(Severity.MEDIUM, Anomalies.outliers(returns(1_700)).single().severity)
    assertEquals(Severity.HIGH, Anomalies.outliers(returns(1_701)).single().severity)
    val f = Anomalies.outliers(returns(6_000), t = latn).single()
    assertEquals(day + 10, f.dayJdn)
    assertEquals(linkedMapOf("value" to 6_000L, "median" to 1_200L, "q3" to 1_250L, "fence" to 1_475L, "days" to 11L), f.evidence)
    assertEquals("برگشتیِ این روز 60 ؋، بیرون از دامنهٔ معمولِ 11 روز (میانه 12 ؋، سقفِ معمول 14.75 ؋) — نیازمندِ بررسی", f.reason)
  }

  @Test fun outlierOnlyInsideTheRangeWithEnoughDaysAndAMinimumSpread() {
    //  روزِ پیش از بازه پایه است
    assertTrue(Anomalies.outliers(returns(6_000), fromJdn = day + 11).isEmpty())
    assertEquals(1, Anomalies.outliers(returns(6_000), fromJdn = day + 10).size)
    //  کمتر از ۷ روز ⇒ بی آمار
    assertTrue(Anomalies.outliers(Series(Kind.DAY_RETURNS, "", day, listOf(0, 0, 0, 0, 0, 9_000))).isEmpty())
    //  سریِ صفر: IQR صفر ⇒ پهنای کمینه؛ ۱۰۰ ⇒ حصار ۱۰۰ (نه)، ۱۰۱ آری، بیش از ۲۰۰ «بالا»
    fun zeros(last: Long) = Series(Kind.DAY_DISCOUNTS, "sara", day, List(7) { 0L } + last)
    assertTrue(Anomalies.outliers(zeros(100)).isEmpty())
    assertEquals(Severity.MEDIUM, Anomalies.outliers(zeros(101)).single().severity)
    assertEquals(Severity.HIGH, Anomalies.outliers(zeros(201)).single().severity)
    assertTrue(Anomalies.outliers(zeros(201)).single().reason.startsWith("sara: تخفیفِ این روز"))
    //  پهنای بزرگ‌ترِ لایهٔ داده (۲٪ ِ فروشِ روزانه) برگشتیِ کوچک را معمولی می‌داند
    assertTrue(Anomalies.outliers(zeros(201), p = Anomalies.Policy(minSpreadMinor = 500)).isEmpty())
  }

  @Test fun detectSortsBySeverityThenDayAndNeverAccuses() {
    val all = Anomalies.detect(
      Anomalies.Input(
        cashierAlerts = listOf(CashierAlerts.Alert("ali", CashierAlerts.Kind.VOID, 6, 10, 2, 20, 600)),
        shifts = listOf(Shift("s1", "ali", day, 500_000, 489_999), Shift("s2", "sara", day + 2, 0, 90_000)),
        adjusts = listOf(Adjust("m1", "p1", "چای", "", day + 1, -60_000, 10_000)),
        lines = listOf(Line("a", "#7", "ali", day + 1, 2_000, 9_000, 5_000)),
        sales = listOf(SaleAt("z", "", day + 2, 120, 5_000)),
        series = listOf(returns(6_000)),
        fromJdn = day,
      ),
    )
    assertEquals(
      listOf(Kind.DAY_RETURNS, Kind.SHIFT_DIFF, Kind.STOCK_LOSS, Kind.CASHIER_VOID, Kind.BELOW_COST, Kind.SHIFT_DIFF, Kind.ODD_HOUR),
      all.map { it.kind },
    )
    assertEquals(listOf(Severity.HIGH, Severity.HIGH, Severity.HIGH, Severity.HIGH, Severity.MEDIUM, Severity.MEDIUM, Severity.LOW), all.map { it.severity })
    assertTrue(all.single { it.kind == Kind.ODD_HOUR }.reason.startsWith("کاربرِ نامعلوم: "))
    for (f in all) {
      assertTrue(f.reason.endsWith("— " + Anomalies.REVIEW), f.reason)
      assertFalse(Regex("تقلب|دزد|متهم|اختلاس").containsMatchIn(f.reason), f.reason)
    }
    assertTrue(Anomalies.detect(Anomalies.Input()).isEmpty())
  }
}
