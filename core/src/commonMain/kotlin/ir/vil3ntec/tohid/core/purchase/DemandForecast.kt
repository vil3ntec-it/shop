package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.ReorderCore.Confidence
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Reason
import ir.vil3ntec.tohid.core.text.LocaleFormat

/**
 *  پیشنهادِ سفارشِ خرید از فروشِ واقعی، زمانِ تأمین و فصل، با دلیل و درجهٔ اطمینان (INV-13) و
 *  «این موجودی چند روز کافی است» (INV-14) — §۶.۵، جریانِ S23.
 *
 *  ── ورودی ──────────────────────────────────────────────────────────
 *  فروشِ خالصِ (فروش − برگشت) هر روزِ یک کالا با JDN (روزهای بی فروش لازم نیستند). پنجره
 *  [windowDays] روز تا امروز (خودِ امروز هم)؛ کالای تازه‌تر از پنجره ⇒ از نخستین روزش ([Input.firstJdn])
 *  تا میانگین با صفرِ روزهایی که کالا نبود پایین نیاید.
 *
 *  ── روزِ هفته ───────────────────────────────────────────────────────
 *  پنجرهٔ دست‌کم [MIN_WEEKDAY_DAYS] روز (هر روزِ هفته دست‌کم چهار بار) ⇒ مصرفِ هر روزِ آینده
 *  میانگینِ همان روزِ هفته در پنجره است (جمعهٔ دو برابر ⇒ جمعهٔ آینده دو برابر). کمتر ⇒ همان
 *  میانگینِ ساده، **بیت‌به‌بیت همان `ReorderCore.suggest`**.
 *
 *  ── فصل ─────────────────────────────────────────────────────────────
 *  فقط با تاریخچهٔ کامل (نخستین روز دست‌کم ۳۶۵ + پنجره روز پیش): نسبتِ فروشِ «همین روزهای
 *  آینده در پارسال» (به اندازهٔ زمانِ تأمین + پوشش) به فروشِ «همان پنجره در پارسال»، هر دو
 *  روزانه، میانِ [SEASON_MIN] و [SEASON_MAX] هزارم. پنجرهٔ پارسالِ بی فروش ⇒ فصل نامعلوم (۱).
 *
 *  ── مقدار ───────────────────────────────────────────────────────────
 *  همان قاعدهٔ ReorderCore با مصرفِ پیش‌بینی‌شده: نقطهٔ سفارش = مصرفِ زمانِ تأمین + حدِ کم؛
 *  جایگاه = موجودی + در راه ≤ نقطهٔ سفارش ⇒ مقدار = مصرفِ (زمانِ تأمین + پوشش) + حدِ کم − جایگاه،
 *  رو به بالا تا واحدِ کامل. ⛔ این فقط پیشنهاد است؛ سفارشِ واقعی بی تأییدِ آدم ساخته یا فرستاده
 *  نمی‌شود (INV-13): «سفارش با یک کلید» فقط پیش‌نویس می‌سازد.
 *
 *  ── چند روز کافی است ───────────────────────────────────────────────
 *  بزرگ‌ترین N (۰ تا [MAX_COVER_DAYS]) که مصرفِ پیش‌بینی‌شدهٔ N روزِ آینده از موجودیِ دست
 *  (بی «در راه») بیشتر نشود. بی فروش ⇒ `null` (تمام نمی‌شود)، موجودیِ ≤ ۰ ⇒ ۰.
 *
 *  ── پیش‌بینیِ قابلِ توضیح (AI-07، [Input.smart]) ─────────────────────
 *  فقط با `smart = true` (تنظیمِ خاموشِ دستگاه): کالای پرفروش ⇐ میانگینِ متحرکِ [SmartDemand.MA_DAYS] روزِ اخیر
 *  با همین ضریبِ روزِ هفته؛ کالای کم‌فروش (دست‌کم نیمی از روزها بی فروش) ⇐ Croston/SBA. اطمینان از درازای داده و
 *  نوسان، پیش‌بینیِ تک‌تکِ روزهای آینده و دلیلِ فارسی از همان عددها — شرح در `SmartDemand`. `smart = false`
 *  (پیش‌فرض) ⇒ همهٔ این پرونده بیت‌به‌بیت همان پیشین است و [Forecast.smart] `null`.
 *
 *  عددها صحیح: مقدار هزارم، ضریب هزارم (permille)، گرد فقط `PurchaseOrderCore.mulDivRound`.
 *  Kotlinِ خالص در commonMain (§۴۰.۳).
 */
object DemandForecast {

  const val WINDOW_DAYS = 90
  const val MAX_WINDOW_DAYS = 400
  const val MIN_WEEKDAY_DAYS = 28
  const val MAX_COVER_DAYS = 365
  const val YEAR_DAYS = 365
  const val SEASON_MIN = 500
  const val SEASON_MAX = 3000
  const val NEUTRAL = 1000
  /** ضریبِ روزِ هفته از این بیشتر ⇒ در دلیل گفته می‌شود (۱٫۲ برابر) */
  const val PEAK_SHOWN = 1200
  /** فصل دست‌کم ۱۰٪ دور از ۱ ⇒ در دلیل گفته می‌شود */
  const val SEASON_SHOWN = 100
  /** روزهای فروش برای اطمینانِ «بالا» */
  const val SURE_SALE_DAYS = 8

  /** بالاتر از این (۲^۴۰ هزارم ≈ یک میلیارد واحد) ضریب‌ها خنثی می‌مانند تا ضربِ میانی سرریز نکند */
  private const val SAFE_SUM = 1L shl 40

  data class Day(val jdn: Int, val qtyMilli: Long)

  data class Input(
    val todayJdn: Int,
    /** فروشِ خالصِ هر روز؛ چند ردیفِ یک روز جمع می‌شوند */
    val history: List<Day>,
    val onHandMilli: Long,
    val onOrderMilli: Long = 0,
    val minStockMilli: Long = 0,
    /** `null` ⇒ زمانِ تأمینِ سنجیده نیست و [ReorderCore.DEFAULT_LEAD_DAYS] به کار می‌رود */
    val leadDays: Int?,
    val coverDays: Int = ReorderCore.DEFAULT_COVER_DAYS,
    val windowDays: Int = WINDOW_DAYS,
    /** نخستین روزِ کالا (نخستین فروش یا خرید)؛ `null` ⇒ کالا از آغازِ پنجره بوده و فصل سنجیده نمی‌شود */
    val firstJdn: Int? = null,
    val wholeUnits: Boolean = true,
    /** AI-07: میانگینِ متحرک یا Croston با اطمینان از نوسان ([SmartDemand])؛ `false` ⇒ همان حسابِ پیشین */
    val smart: Boolean = false,
    /** AI-07: چند روزِ آینده تک‌تک پیش‌بینی شود ([SmartDemand.Smart.perDayMilli]) */
    val smartDays: Int = SmartDemand.FORECAST_DAYS,
  )

  data class Forecast(
    val qtyMilli: Long,
    val reason: Reason,
    val confidence: Confidence,
    /** میانگینِ روزانهٔ پنجره */
    val dailyMilli: Long,
    val reorderPointMilli: Long,
    val positionMilli: Long,
    val leadDaysUsed: Int,
    val leadMeasured: Boolean,
    /** چند روز کافی است؛ `null` = فروشی نبود (تمام نمی‌شود) */
    val daysLeft: Int?,
    /** دست‌کم [MAX_COVER_DAYS] روز */
    val daysLeftBeyond: Boolean,
    /** ضریبِ هر روزِ هفته به هزارم، خانهٔ ۰ = دوشنبه … ۶ = یکشنبه؛ `null` = پنجره کوتاه یا بی فروش */
    val weekdayPermille: List<Int>?,
    /** روزِ پرفروش (ISO: ۱ دوشنبه … ۵ جمعه … ۷ یکشنبه) وقتی ضریبش دست‌کم [PEAK_SHOWN] است */
    val peakWeekday: Int?,
    /** ضریبِ فصل به هزارم؛ `null` = نامعلوم (۱ به کار رفت) */
    val seasonPermille: Int?,
    val onOrderMilli: Long,
    /** روزهایی از پنجره که فروشِ مثبت داشتند */
    val saleDays: Int,
    /** پنجرهٔ به‌کاررفته (کالای تازه ⇒ کوتاه‌تر) */
    val windowDaysUsed: Int,
    /** AI-07: روش، پیش‌بینیِ هر روز و نوسان؛ `null` = [Input.smart] خاموش */
    val smart: SmartDemand.Smart? = null,
  )

  /** روزِ هفتهٔ ISO از JDN (JDN ِ ۰ دوشنبه است) */
  fun isoWeekday(jdn: Int): Int = jdn.mod(7) + 1

  internal fun divRound(n: Long, d: Long): Long = (2 * n + d).floorDiv(2 * d)

  /** مصرفِ پیش‌بینی‌شدهٔ فردا تا N روزِ دیگر — [Model] ِ پیشین یا مدل‌های AI-07 */
  internal interface Demand {
    /** جمعِ فروشِ خالصِ پنجره؛ صفر ⇒ «بی فروش» */
    val total: Long
    fun demand(n: Int): Long
  }

  internal class Model(
    override val total: Long,
    val eff: Int,
    /** جمع و شمارِ هر روزِ هفته؛ `null` ⇒ میانگینِ ساده */
    val sums: LongArray?,
    val counts: IntArray?,
    val season: Int,
    val todayJdn: Int,
  ) : Demand {
    /** مصرفِ پیش‌بینی‌شدهٔ روزهای فردا تا N روزِ دیگر */
    override fun demand(n: Int): Long {
      if (n <= 0 || total == 0L) return 0L
      val s = sums ?: return PurchaseOrderCore.mulDivRound(total, n.toLong() * season, eff.toLong() * NEUTRAL)
      val c = counts!!
      var out = 0L
      for (w in 0 until 7) {
        //  شمارِ همین روزِ هفته در فردا … فردا + n − ۱
        val firstOffset = (w - isoWeekday(todayJdn + 1) + 1).mod(7)
        val k = if (firstOffset >= n) 0 else (n - 1 - firstOffset) / 7 + 1
        if (k == 0 || s[w] <= 0L) continue
        out = PurchaseOrderCore.add(out, PurchaseOrderCore.mulDivRound(s[w], k.toLong() * season, c[w].toLong() * NEUTRAL))
      }
      return out
    }
  }

  fun forecast(i: Input): Forecast {
    require(i.windowDays in 1..MAX_WINDOW_DAYS) { "demand-forecast: 0 < windowDays ≤ $MAX_WINDOW_DAYS" }
    require(i.coverDays in 0..MAX_COVER_DAYS) { "demand-forecast: 0 ≤ coverDays ≤ $MAX_COVER_DAYS" }
    require(i.leadDays == null || i.leadDays in 0..MAX_COVER_DAYS) { "demand-forecast: 0 ≤ leadDays ≤ $MAX_COVER_DAYS" }
    require(!i.smart || i.smartDays in 1..MAX_COVER_DAYS) { "demand-forecast: 0 < smartDays ≤ $MAX_COVER_DAYS" }
    val today = i.todayJdn
    val windowStart = today - i.windowDays + 1
    val start = if (i.firstJdn != null && i.firstJdn > windowStart) minOf(i.firstJdn, today) else windowStart
    val eff = today - start + 1

    //  جمعِ هر روز (چند ردیفِ یک روز یکی)
    val byDay = HashMap<Int, Long>()
    for (d in i.history) byDay[d.jdn] = PurchaseOrderCore.add(byDay[d.jdn] ?: 0L, d.qtyMilli)
    fun sumRange(from: Int, to: Int): Long {
      var s = 0L
      for ((j, q) in byDay) if (j in from..to) s = PurchaseOrderCore.add(s, q)
      return s
    }

    var rawTotal = 0L
    var saleDays = 0
    val sums = LongArray(7)
    val counts = IntArray(7)
    for (j in start..today) counts[isoWeekday(j) - 1]++
    for ((j, q) in byDay) {
      if (j !in start..today) continue
      rawTotal = PurchaseOrderCore.add(rawTotal, q)
      if (q > 0) saleDays++
      sums[isoWeekday(j) - 1] = PurchaseOrderCore.add(sums[isoWeekday(j) - 1], q)
    }
    val total = maxOf(0L, rawTotal)
    val weekly = eff >= MIN_WEEKDAY_DAYS && total in 1..SAFE_SUM
    for (w in 0 until 7) if (sums[w] < 0) sums[w] = 0L
    val weekdayPermille = if (!weekly) null else List(7) { w ->
      divRound(sums[w] * eff * NEUTRAL, counts[w].toLong() * total).toInt()
    }
    var peak = weekdayPermille?.withIndex()?.maxByOrNull { it.value }?.takeIf { it.value >= PEAK_SHOWN }?.let { it.index + 1 }

    val leadUsed = i.leadDays ?: ReorderCore.DEFAULT_LEAD_DAYS
    val horizon = maxOf(1, leadUsed + i.coverDays)
    val season = seasonOf(i, today, eff, horizon, ::sumRange)

    val base = Model(total, eff, if (weekly) sums else null, if (weekly) counts else null, season ?: NEUTRAL, today)
    //  AI-07: فقط با `smart`؛ خاموش ⇒ همان `base` و همان حسابِ پیشین
    val fit = if (!i.smart) null else SmartDemand.fit(
      SmartDemand.Window(byDay, start, today, eff, total, saleDays, if (weekly) sums else null, if (weekly) counts else null, season ?: NEUTRAL, i.leadDays != null),
      i.smartDays,
    )
    val model: Demand = fit?.model ?: base
    if (fit != null && fit.smart.method == SmartDemand.Method.CROSTON) peak = null
    val daily = fit?.smart?.levelMilli ?: PurchaseOrderCore.mulDivRound(total, 1, eff.toLong())
    val min = maxOf(0L, i.minStockMilli)
    val onOrder = maxOf(0L, i.onOrderMilli)
    val reorderPoint = PurchaseOrderCore.add(model.demand(leadUsed), min)
    val position = PurchaseOrderCore.add(i.onHandMilli, onOrder)
    val confidence = fit?.confidence ?: when {
      total > 0 && i.leadDays != null && saleDays >= SURE_SALE_DAYS && eff >= MIN_WEEKDAY_DAYS -> Confidence.HIGH
      total > 0 && (i.leadDays != null || saleDays >= SURE_SALE_DAYS) -> Confidence.MEDIUM
      else -> Confidence.LOW
    }
    val (left, beyond) = daysLeft(model, i.onHandMilli)

    val due = if (total > 0) position <= reorderPoint else min > 0 && position < min
    var qty = 0L
    if (due) {
      val target = PurchaseOrderCore.add(model.demand(leadUsed + i.coverDays), min)
      qty = maxOf(0L, target - position)
      if (i.wholeUnits) qty = (qty + PurchaseOrderCore.MILLI - 1) / PurchaseOrderCore.MILLI * PurchaseOrderCore.MILLI
      if (qty == 0L) qty = if (i.wholeUnits) PurchaseOrderCore.MILLI else 1L
    }
    val reason = when {
      !due -> Reason.ENOUGH
      total > 0 -> Reason.REORDER_POINT
      else -> Reason.BELOW_MIN
    }
    return Forecast(
      qty, reason, confidence, daily, reorderPoint, position, leadUsed, i.leadDays != null, left, beyond,
      weekdayPermille, peak, season, onOrder, saleDays, eff, fit?.smart,
    )
  }

  /** ضریبِ فصل؛ `null` = تاریخچهٔ پارسال نیست یا پنجرهٔ پارسال بی فروش است */
  private fun seasonOf(i: Input, today: Int, eff: Int, horizon: Int, sumRange: (Int, Int) -> Long): Int? {
    val first = i.firstJdn ?: return null
    val lyToday = today - YEAR_DAYS
    if (first > lyToday - eff + 1) return null
    val base = sumRange(lyToday - eff + 1, lyToday)
    val up = maxOf(0L, sumRange(lyToday + 1, lyToday + horizon))
    if (base <= 0L || base > SAFE_SUM || up > SAFE_SUM) return null
    val f = divRound(up * eff * NEUTRAL, horizon.toLong() * base)
    return f.coerceIn(SEASON_MIN.toLong(), SEASON_MAX.toLong()).toInt()
  }

  /** بزرگ‌ترین N که مصرفِ N روزِ آینده ≤ موجودی (مصرف با N کم نمی‌شود ⇒ جست‌وجوی دودویی) */
  private fun daysLeft(m: Demand, onHand: Long): Pair<Int?, Boolean> {
    if (m.total == 0L) return null to false
    if (onHand <= 0L) return 0 to false
    if (m.demand(MAX_COVER_DAYS) <= onHand) return MAX_COVER_DAYS to true
    var lo = 0
    var hi = MAX_COVER_DAYS
    while (hi - lo > 1) {
      val mid = (lo + hi) / 2
      if (m.demand(mid) <= onHand) lo = mid else hi = mid
    }
    return lo to false
  }

  /* ── متنِ دلیل (فارسی/دری) ── */

  private val WEEKDAYS = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

  fun weekdayName(iso: Int): String = WEEKDAYS[(iso - 1).mod(7)]

  /** مقدارِ هزارم ⇒ «۱۲»، «۱۲.۵»، «۰.۲۵» (بی صفرِ پایانی؛ جداکنندهٔ اعشار همان `LocaleFormat.displayDigits`) */
  fun qtyText(milli: Long, digits: String): String {
    val neg = milli < 0
    val a = if (neg) -milli else milli
    val whole = a / PurchaseOrderCore.MILLI
    val frac = (a % PurchaseOrderCore.MILLI).toString().padStart(3, '0').trimEnd('0')
    val s = (if (neg) "-" else "") + whole + (if (frac.isEmpty()) "" else ".$frac")
    return LocaleFormat.displayDigits(s, digits)
  }

  /** ضریبِ هزارم ⇒ «۲»، «۱.۵» (یک رقمِ اعشار، نیمه رو به بالا) */
  fun factorText(permille: Int, digits: String): String {
    val tenths = divRound(permille.toLong(), 100)
    val s = (tenths / 10).toString() + (if (tenths % 10 == 0L) "" else "." + (tenths % 10))
    return LocaleFormat.displayDigits(s, digits)
  }

  /**
   *  دلیلِ پیشنهاد، همان شکلِ S23: «میانگینِ روزانه ۱۲، روزِ جمعه ۲ برابر، ۴ روز موجودی مانده، زمانِ تأمین ۵ روز».
   *  بی فروش ⇒ «در N روز فروشی نبود» با پنجرهٔ به‌کاررفته.
   */
  fun why(f: Forecast, digits: String = LocaleFormat.DIGITS_FA): String {
    fun n(x: Int) = LocaleFormat.displayDigits(x.toString(), digits)
    val parts = ArrayList<String>()
    val smart = f.smart
    //  AI-07: کم‌فروش با اندازه و فاصلهٔ Croston، پرفروش با پنجرهٔ میانگینِ متحرک؛ خاموش ⇒ همان دو خطِ پیشین
    if (smart != null && smart.method == SmartDemand.Method.CROSTON) parts += SmartDemand.crostonText(smart, digits)
    else if (f.dailyMilli > 0 || f.saleDays > 0) parts += "میانگینِ روزانه ${qtyText(f.dailyMilli, digits)}" +
      (if (smart != null) " (${n(smart.levelDays)} روزِ اخیر)" else "")
    else parts += "در ${n(f.windowDaysUsed)} روز فروشی نبود"
    val peak = f.peakWeekday
    if (peak != null) parts += "روزِ ${weekdayName(peak)} ${factorText(f.weekdayPermille!![peak - 1], digits)} برابر"
    val s = f.seasonPermille
    if (s != null && kotlin.math.abs(s - NEUTRAL) >= SEASON_SHOWN) parts += "همین روزهای پارسال ${factorText(s, digits)} برابر"
    val left = f.daysLeft
    when {
      left == null -> {}
      f.daysLeftBeyond -> parts += "بیش از ${n(MAX_COVER_DAYS)} روز موجودی"
      left == 0 -> parts += "موجودی تمام است"
      else -> parts += "${n(left)} روز موجودی مانده"
    }
    if (f.reason == Reason.BELOW_MIN) parts += "زیرِ حدِ کم"
    if (f.onOrderMilli > 0) parts += "${qtyText(f.onOrderMilli, digits)} در راه"
    parts += "زمانِ تأمین ${n(f.leadDaysUsed)} روز" + (if (f.leadMeasured) "" else " (سنجیده نشده)")
    smart?.cvPermille?.let { parts += "نوسان ${SmartDemand.percentText(it, digits)}" }
    return parts.joinToString("، ")
  }

  fun confidenceText(c: Confidence): String = when (c) {
    Confidence.HIGH -> "اطمینانِ بالا"
    Confidence.MEDIUM -> "اطمینانِ متوسط"
    Confidence.LOW -> "اطمینانِ پایین"
  }

  /** «این موجودی چند روز کافی است» به‌تنهایی (ستونِ فهرست) */
  fun coverText(f: Forecast, digits: String = LocaleFormat.DIGITS_FA): String {
    val left = f.daysLeft ?: return "بی فروش"
    return when {
      f.daysLeftBeyond -> "بیش از ${LocaleFormat.displayDigits(MAX_COVER_DAYS.toString(), digits)} روز"
      left == 0 -> "تمام"
      else -> "${LocaleFormat.displayDigits(left.toString(), digits)} روز"
    }
  }
}
