package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.data.Sale
import kotlin.math.roundToInt

/**
 *  عددهای کادرِ «فروشِ امروز» (HOME-V2 §۲٫۲) — حسابِ خالص، بی Compose.
 *
 *  • مقایسه با **همین ساعتِ دیروز**، نه کلِ دیروز — وگرنه صبح‌ها همیشه سرخ است. فاکتورِ دیروز که
 *    ساعتش معلوم نیست (`createdAt == 0`، دادهٔ کهنه) همان کلِ روز حساب می‌شود.
 *  • خطِ کوچک = جمعِ فروشِ هر روز در ۷ روزِ تا امروز (امروز تا همین لحظه).
 *  • فروشِ لغوشده هیچ‌جا نیست؛ مبلغ همان `finalTotal` ِ عددِ درشت (`DashboardNumbers`).
 */
object HomeTrend {
  /** چند روز در خطِ کوچک */
  const val DAYS = 7

  private const val DAY_MS = 24L * 60 * 60 * 1000

  enum class Direction { UP, DOWN, SAME }

  /**
   *  @param pct درصدِ گردشده نسبت به همین ساعتِ دیروز؛ `null` یعنی دیروز تا این ساعت فروشی نبود
   *    (مقایسه بی‌معنا ⇒ خطِ مقایسه پنهان)
   *  @param series ۷ عدد، کهنه‌ترین اول، امروز آخر
   */
  data class View(val today: Double, val yesterday: Double, val pct: Int?, val series: List<Double>) {
    val direction: Direction? get() = pct?.let { if (it > 0) Direction.UP else if (it < 0) Direction.DOWN else Direction.SAME }

    /** خط فقط وقتی چیزی برای نشان دادن دارد (همه صفر ⇒ خطِ صاف معنایی ندارد) */
    val hasSeries: Boolean get() = series.any { it > 0.0 }
  }

  fun of(sales: List<Sale>, today: String, nowMs: Long): View {
    val live = sales.filter { it.status != "cancelled" }
    val yesterday = CivilDays.addDays(today, -1)
    val cutoff = nowMs - DAY_MS
    val todayTotal = live.filter { it.date == today }.sumOf { it.finalTotal }
    val sameTime = live
      .filter { it.date == yesterday && (it.createdAt <= 0L || it.createdAt <= cutoff) }
      .sumOf { it.finalTotal }
    val days = (DAYS - 1 downTo 0).map { CivilDays.addDays(today, -it) ?: "" }
    val byDay = live.filter { it.date in days }.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.finalTotal } }
    return View(
      today = todayTotal,
      yesterday = sameTime,
      pct = percent(todayTotal, sameTime),
      series = days.map { byDay[it] ?: 0.0 },
    )
  }

  /** درصدِ تغییر، گرد؛ پایهٔ صفر ⇒ `null` */
  fun percent(now: Double, before: Double): Int? =
    if (before <= 0.0) null else ((now - before) / before * 100.0).roundToInt()
}
