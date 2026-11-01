package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.calendar.BusinessDay
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData

/** دانه‌بندیِ نمودارِ آمار (`stats?period=`): هر ستون یک روز، هفته، ماه یا سالِ خورشیدی */
enum class StatsPeriod(val id: String, val count: Int) {
  DAY("day", 7), WEEK("week", 6), MONTH("month", 6), YEAR("year", 5);

  companion object {
    /** ناشناخته ⇒ روز (هفتهٔ اخیر، روز به روز) */
    fun of(id: String?): StatsPeriod = entries.firstOrNull { it.id == id } ?: DAY
  }
}

/** یک ستون: بازهٔ میلادیِ فایل و عددهای همان بازه از `ReportEngine.sales` */
data class StatsBar(val from: String, val to: String, val net: Double, val count: Int)

/** یک روزِ کارتِ «روزهای فروشِ این هفته» */
data class StreakDay(val date: String, val sold: Boolean, val future: Boolean)

/** یک خانهٔ پخشِ ساعتی: از ساعتِ `hour` به اندازهٔ `span` ساعت */
data class HourBin(val hour: Int, val span: Int, val count: Int, val net: Double)

/**
 *  عددهای صفحهٔ آمار — همه از موتورِ گزارش (`ReportEngine.sales`) با همان قاعدهٔ
 *  «فاکتورِ لغونشده»؛ پخشِ ساعتی همان فاکتورها را بر ساعتِ ثبت (`createdAt`) می‌شمارد.
 *  خالص و بی Compose، با آزمون (`StatsModelTest`).
 */
object StatsModel {

  /** بازه‌های ستون‌ها، قدیمی اول و بازهٔ جاری آخر */
  fun buckets(period: StatsPeriod, today: String): List<Pair<String, String>> = when (period) {
    StatsPeriod.DAY -> (period.count - 1 downTo 0).mapNotNull { back -> CivilDays.addDays(today, -back)?.let { it to it } }
    StatsPeriod.WEEK -> {
      val start = BusinessDay.weekStartOf(today)
      if (start == null) emptyList() else (period.count - 1 downTo 0).mapNotNull { back ->
        val from = CivilDays.addDays(start, -7 * back) ?: return@mapNotNull null
        val to = CivilDays.addDays(from, 6) ?: return@mapNotNull null
        from to minOf(to, today)
      }
    }
    StatsPeriod.MONTH -> {
      val j = SolarHijri.toSolar(today)
      if (j == null) emptyList() else (period.count - 1 downTo 0).mapNotNull { back ->
        val (y, m) = ReportsModel.stepMonth(j.year, j.month, -back)
        val from = SolarHijri.fromSolar(y, m, 1) ?: return@mapNotNull null
        val last = SolarHijri.fromSolar(y, m, SolarHijri.monthLength(y, m)) ?: return@mapNotNull null
        from to minOf(last, today)
      }
    }
    StatsPeriod.YEAR -> {
      val j = SolarHijri.toSolar(today)
      if (j == null) emptyList() else (period.count - 1 downTo 0).mapNotNull { back ->
        val y = j.year - back
        val from = SolarHijri.fromSolar(y, 1, 1) ?: return@mapNotNull null
        val last = SolarHijri.fromSolar(y, 12, SolarHijri.monthLength(y, 12)) ?: return@mapNotNull null
        from to minOf(last, today)
      }
    }
  }

  fun bars(d: ShopData, period: StatsPeriod, today: String): List<StatsBar> =
    buckets(period, today).map { (from, to) -> ReportEngine.sales(d, from, to).let { StatsBar(from, to, it.net, it.count) } }

  /** «مصارف و خرید» ِ یک بازه — مصارفِ `ReportEngine.sales` + جمعِ `ReportEngine.purchases` */
  fun spend(d: ShopData, from: String, to: String): Double =
    ReportEngine.sales(d, from, to).expenses + ReportEngine.purchases(d, from, to).total

  /** هفتهٔ دکان (از شنبه، `BusinessDay`) تا امروز: کدام روز فروش داشت؛ روزهای آینده جدا */
  fun streak(d: ShopData, today: String): List<StreakDay> {
    val start = BusinessDay.weekStartOf(today) ?: return emptyList()
    return (0 until 7).mapNotNull { i ->
      val day = CivilDays.addDays(start, i) ?: return@mapNotNull null
      StreakDay(day, sold = day <= today && ReportEngine.sales(d, day, day).count > 0, future = day > today)
    }
  }

  /**
   *  پخشِ فاکتورهای لغونشدهٔ `from..to` بر ساعتِ ثبت. خانه‌ها از نخستین تا آخرین ساعتِ
   *  پُر (دست‌کم شش خانه)؛ بیش از دوازده ساعت ⇒ خانه‌های دوساعته. بی فروش ⇒ خالی.
   */
  fun hours(d: ShopData, from: String, to: String, hourOf: (Long) -> Int): List<HourBin> {
    val active = d.sales.filter { it.date in from..to && it.status != "cancelled" && it.createdAt > 0 }
    if (active.isEmpty()) return emptyList()
    val byHour = active.groupBy { hourOf(it.createdAt).coerceIn(0, 23) }
    var lo = byHour.keys.min()
    var hi = byHour.keys.max()
    while (hi - lo + 1 < MIN_BINS) {
      if (lo > 0) lo--
      if (hi - lo + 1 < MIN_BINS && hi < 23) hi++
    }
    val span = if (hi - lo + 1 > 12) 2 else 1
    return (lo..hi step span).map { h ->
      val rows = (h until minOf(h + span, 24)).flatMap { byHour[it].orEmpty() }
      HourBin(h, span, rows.size, rows.sumOf { it.finalTotal })
    }
  }

  /** پُرفروش‌ترین خانه: بیشترین فاکتور، سپس بیشترین مبلغ، سپس زودتر */
  fun peak(bins: List<HourBin>): HourBin? =
    bins.filter { it.count > 0 }.maxWithOrNull(compareBy<HourBin> { it.count }.thenBy { it.net }.thenByDescending { it.hour })

  /**
   *  پلهٔ گردِ محورِ عمودی: بیشینه به ۱، ۲، ۲٫۵ یا ۵ ضربدرِ توانِ ده گرد می‌شود و
   *  چهار پله می‌خورد (۰، ۱۰K، ۲۰K، ۳۰K، ۴۰K).
   */
  fun axisMax(max: Double): Double {
    if (max <= 0.0 || max.isNaN()) return 4.0
    val raw = max / 4.0
    var p = 1.0
    while (p * 10 <= raw) p *= 10
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * p >= raw } * p * 4
  }

  /** برچسبِ کوتاهِ محور: 950 · 12K · 3.5M · 1.2B */
  fun compact(v: Double): String {
    val a = kotlin.math.abs(v)
    fun f(x: Double, s: String): String {
      val r = Math.round(x * 10) / 10.0
      return (if (r == Math.floor(r)) r.toLong().toString() else r.toString()) + s
    }
    return when {
      a >= 1e9 -> f(v / 1e9, "B")
      a >= 1e6 -> f(v / 1e6, "M")
      a >= 1e3 -> f(v / 1e3, "K")
      else -> Math.round(v).toString()
    }
  }

  private const val MIN_BINS = 6
}
