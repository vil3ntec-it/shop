package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.ShopWeek
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.calendar.WeekRules
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.data.Sale

/**
 *  کادرِ بالای خانه (HOME-V3، انتخابِ صاحب‌کار ۱۴۰۵/۰۸/۱۳، `docs/design/home-hero/chosen-reference.jpg`) — حسابِ خالص،
 *  بی Compose، بی ساعت و بی Context؛ کدِ مشترکِ اندروید و آیفون. جای دو کادرِ «صندوق» و «فروشِ امروز» را گرفته.
 *
 *  • دو صفحه: «صندوق» (فروش در بازه) و «گاوصندوق» (ماندهٔ گاوصندوق در بازه، از `SafeCore.view`).
 *  • سه بازه: امروز (۲۴ ساعت)، هفته (هفتهٔ دکان از `WeekRules`: پیش‌فرض شنبه تا جمعه، همان هفتهٔ افغانستان؛ LOC-03)
 *    و ماه (ماهِ خورشیدیِ جاری، مثلِ آمار).
 *  • «همین حالا» خانهٔ `now` است؛ خانه‌های پس از آن هنوز نیامده‌اند و خطِ صافِ نقطه‌چین می‌گیرند.
 *  • همه از دادهٔ واقعی: فاکتورهای لغونشده (`finalTotal`، همان عددِ «فروشِ امروز») و ردیف‌های گاوصندوق — بی عددِ ساختگی.
 */
object HomeHero {

  enum class Range { TODAY, WEEK, MONTH }

  enum class Page { TILL, SAFE }

  const val HOURS = 24
  const val HOUR_MS = 60L * 60 * 1000
  const val DAY_MS = 24 * HOUR_MS

  /** برچسبِ ساعت هر چند ساعت یک بار (۰، ۴، ۸ …) */
  private const val HOUR_TICK = 4

  /** روزهای ماه که برچسب دارند */
  private val MONTH_TICKS = setOf(1, 5, 10, 15, 20, 25, 30)

  /**
   *  @param values یک عدد (افغانی) برای هر خانهٔ محور، از آغازِ بازه؛ خانه‌های پس از `now` صفرند
   *  @param now خانهٔ «همین حالا»
   *  @param ticks برچسبِ محور برای هر خانه؛ خالی یعنی بی‌برچسب
   *  @param empty در این بازه هیچ داده‌ای نیست ⇒ خطِ آرام و راهنما
   */
  data class Series(val values: List<Double>, val now: Int, val ticks: List<String>, val empty: Boolean) {
    /** عددِ همین حالا — همان که نقطه و برچسبِ نمودار نشان می‌دهند */
    val current: Double get() = values.getOrElse(now) { 0.0 }
  }

  /** ورود و خروجِ امروزِ گاوصندوق (به پول) — شمارش نه، چون شمارش پول جابه‌جا نمی‌کند */
  data class Flow(val inMinor: Long, val outMinor: Long)

  /** جای روز در هفتهٔ دکان: آغازِ هفته = ۰ … ۶ (پیش‌فرض شنبه = ۰ … جمعه = ۶) — `ShopWeek.indexOf` */
  fun weekIndex(iso: String, rules: WeekRules = WeekRules()): Int? = ShopWeek.indexOf(iso, rules)

  /** روزهای بازه (ISO، از آغاز) و جای امروز در آن */
  fun days(range: Range, today: String, rules: WeekRules = WeekRules()): Pair<List<String>, Int> = when (range) {
    Range.TODAY -> listOf(today) to 0
    Range.WEEK -> {
      val i = weekIndex(today, rules) ?: 0
      (0 until 7).map { CivilDays.addDays(today, it - i).orEmpty() } to i
    }
    Range.MONTH -> {
      val j = SolarHijri.toSolar(today)
      if (j == null) listOf(today) to 0
      else (1..SolarHijri.monthLength(j.year, j.month)).map { SolarHijri.fromSolar(j.year, j.month, it).orEmpty() } to (j.day - 1)
    }
  }

  /**
   *  برچسب‌های محور: ساعت (رقمِ لاتین؛ رقمِ صفحه از قلم می‌آید)، حرفِ روزِ هفته، روزِ ماه. `weekLetters` همیشه از
   *  شنبه است (`StrHero.weekLetters`)؛ خانهٔ `i` ِ هفته حرفِ روزِ `ShopWeek.order` ِ همان آغاز را می‌گیرد.
   */
  fun ticks(range: Range, size: Int, weekLetters: List<String>, rules: WeekRules = WeekRules()): List<String> = when (range) {
    Range.TODAY -> (0 until size).map { if (it % HOUR_TICK == 0) it.toString() else "" }
    Range.WEEK -> {
      val order = ShopWeek.order(rules.weekStart)
      //  ISO ⇒ جای همان روز در فهرستِ از-شنبه: شنبه (۶) ۰، یکشنبه (۷) ۱ … جمعه (۵) ۶
      (0 until size).map { i -> order.getOrNull(i)?.let { weekLetters.getOrElse((it + 1) % 7) { "" } }.orEmpty() }
    }
    Range.MONTH -> (0 until size).map { if (it + 1 in MONTH_TICKS) (it + 1).toString() else "" }
  }

  /** ساعتِ `ms` در روزِ کاری‌ای که از `dayStartMs` آغاز شد (۰ تا ۲۳؛ پس از نیمه‌شبِ روزِ کاریِ دراز ⇒ ۲۳) */
  fun hourOf(ms: Long, dayStartMs: Long): Int = ((ms - dayStartMs) / HOUR_MS).toInt().coerceIn(0, HOURS - 1)

  /**
   *  فروشِ بازه برای صفحهٔ «صندوق». هفته و ماه: فروشِ هر روز. امروز: **جمعِ فروش تا پایانِ هر ساعت** (خطِ رو به بالا،
   *  نه تیزیِ تک‌ساعت‌ها) — پس نقطهٔ «همین حالا» همان «فروشِ امروز» ِ پایینِ کادر است. فاکتورِ امروزِ بی‌ساعت
   *  (`createdAt == 0`، دادهٔ کهنه) در خانهٔ همین حالا می‌نشیند تا جمع همان بماند.
   */
  fun sales(
    sales: List<Sale>,
    range: Range,
    today: String,
    dayStartMs: Long,
    nowMs: Long,
    weekLetters: List<String>,
    rules: WeekRules = WeekRules(),
  ): Series {
    if (range == Range.TODAY) {
      val v = DoubleArray(HOURS)
      val now = hourOf(nowMs, dayStartMs)
      var any = false
      for (s in sales) {
        if (s.status == "cancelled" || s.date != today) continue
        any = true
        v[if (s.createdAt > 0L) minOf(hourOf(s.createdAt, dayStartMs), now) else now] += s.finalTotal
      }
      for (i in 1..now) v[i] += v[i - 1]
      return Series(v.toList(), now, ticks(range, HOURS, weekLetters, rules), !any)
    }
    val (dates, now) = days(range, today, rules)
    val at = HashMap<String, Int>(dates.size * 2).apply { dates.forEachIndexed { i, d -> if (d.isNotEmpty()) put(d, i) } }
    val v = DoubleArray(dates.size)
    var any = false
    for (s in sales) {
      if (s.status == "cancelled") continue
      val i = at[s.date] ?: continue
      if (i > now) continue
      v[i] += s.finalTotal
      any = true
    }
    return Series(v.toList(), now, ticks(range, dates.size, weekLetters, rules), !any)
  }

  /**
   *  ماندهٔ گاوصندوق در پایانِ هر خانهٔ بازه (افغانی) برای صفحهٔ «گاوصندوق». `history` همان `SafeCore.View.history`
   *  است (تازه‌ترین اول، ردیفِ نادرست بیرون)؛ مانده پس از هر ردیف از خودِ هسته می‌آید، این‌جا فقط خوانده می‌شود.
   *  روزهای هفته و ماه از `dayStartMs` ِ امروز با گامِ ۲۴ ساعت.
   */
  fun safe(
    history: List<SafeCore.Line>,
    range: Range,
    today: String,
    dayStartMs: Long,
    nowMs: Long,
    weekLetters: List<String>,
    rules: WeekRules = WeekRules(),
  ): Series {
    val lines = history.asReversed()
    val (size, now, ends) = when (range) {
      Range.TODAY -> Triple(HOURS, hourOf(nowMs, dayStartMs), (0 until HOURS).map { dayStartMs + (it + 1) * HOUR_MS })
      else -> {
        val (dates, today0) = days(range, today, rules)
        Triple(dates.size, today0, dates.indices.map { dayStartMs + (it - today0 + 1) * DAY_MS })
      }
    }
    val v = DoubleArray(size)
    var k = 0
    var balance = 0L
    for (i in 0..now.coerceAtMost(size - 1)) {
      //  خانهٔ همین حالا همهٔ ردیف‌ها را می‌گیرد (ساعتِ دستگاهِ دیگر کمی جلوتر هم باشد، مانده همان ماندهٔ کادر است)
      val end = if (i == now) Long.MAX_VALUE else ends[i]
      while (k < lines.size && lines[k].entry.at < end) { balance = lines[k].balanceMinor; k++ }
      v[i] = balance / 100.0
    }
    return Series(v.toList(), now, ticks(range, size, weekLetters, rules), lines.isEmpty())
  }

  /** «واریزِ امروز» و «برداشتِ امروز» ِ گاوصندوق: اثرِ ردیف‌های امروز جز شمارش (ورود و از‌دخل + ؛ خروج، به‌دخل و به‌بانک −) */
  fun safeToday(history: List<SafeCore.Line>, dayStartMs: Long): Flow {
    var inn = 0L
    var out = 0L
    for (l in history) {
      if (l.entry.kind == SafeCore.COUNT || l.entry.at < dayStartMs || l.entry.at >= dayStartMs + DAY_MS) continue
      if (l.deltaMinor > 0) inn += l.deltaMinor else out -= l.deltaMinor
    }
    return Flow(inn, out)
  }

  /** «ضررِ امروز» همان سودِ خالصِ منفیِ کادرِ پیشینِ «صندوق» (`DashboardNumbers.todayProfit`)؛ روزِ سود ⇒ ۰ */
  fun loss(profit: Double): Double = if (profit < 0.0) -profit else 0.0
}
