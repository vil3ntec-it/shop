package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.DemandForecast.divRound
import ir.vil3ntec.tohid.core.purchase.ReorderCore.Confidence
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlin.math.sqrt

/**
 *  AI-07 — پیش‌بینیِ تقاضای قابلِ توضیح (§۶.۲۲، S23): روی همان پنجره و همان ضریب‌های `DemandForecast`، فقط
 *  وقتی `DemandForecast.Input.smart` روشن است (تنظیمِ خاموشِ دستگاه). بی سرویسِ بیرونی و بی شبکه.
 *
 *  ── انتخابِ روش ─────────────────────────────────────────────────────
 *  روزهای بی فروشِ پنجره دست‌کم [INTERMITTENT_PERMILLE] هزارم (نیمی از روزها) و دست‌کم یک فروش ⇒ **Croston**
 *  (کم‌فروش)؛ وگرنه **میانگینِ متحرک**. بی فروش ⇒ میانگینِ متحرکِ صفر («در N روز فروشی نبود»).
 *
 *  ── میانگینِ متحرک با روزِ هفته ─────────────────────────────────────
 *  سطح = فروشِ خالصِ [MA_DAYS] روزِ اخیر ÷ همان روزها (کالای تازه‌تر ⇒ همهٔ روزهایش). شکلِ هفته همان میانگینِ هر
 *  روزِ هفتهٔ پنجره (`weekdayPermille`)؛ پس مصرفِ هر روزِ آینده = میانگینِ همان روزِ هفته × (سطحِ اخیر ÷ میانگینِ
 *  پنجره) × فصل. ۲۸ روز چهار هفتهٔ کامل است، پس سطح از روزِ هفته کج نمی‌شود.
 *
 *  ── Croston/SBA ─────────────────────────────────────────────────────
 *  فقط سرِ روزهای فروش: اندازهٔ فروش (z) و فاصله تا فروشِ پیشین (p) جدا با α = [ALPHA] هزارم هموار می‌شوند. آغاز
 *  از میانگینِ پنجره (z₀ = جمع ÷ شمارِ فروش، p₀ = روزهای پنجره ÷ شمارِ فروش)، پس روزهای بی فروشِ پیش از نخستین و
 *  پس از آخرین فروش هم در فاصله هستند. فروشِ روزانه = z ÷ p × (۱ − α/۲) (تصحیحِ سوگیریِ Syntetos–Boylan) × فصل،
 *  یکنواخت (روزِ هفتهٔ کالای کم‌فروش معنا ندارد).
 *
 *  ── اطمینان ─────────────────────────────────────────────────────────
 *  نوسان = ضریبِ تغییرات: میانگینِ متحرک ⇐ انحرافِ هر روز از میانگینِ همان روزِ هفته ÷ میانگینِ روزانه؛ Croston ⇐
 *  بیشینهٔ نوسانِ اندازه‌ها و فاصله‌ها. «بالا» = دادهٔ بس (۲۸ روز و ۸ روزِ فروش؛ Croston هشت فروش) + نوسان ≤ [CV_STEADY]
 *  + زمانِ تأمینِ سنجیده؛ «متوسط» = ۱۴ روز (Croston سه فروش) + نوسان ≤ [CV_NOISY]؛ بقیه «پایین».
 *
 *  ⛔ عددِ مقدار و روزها صحیح (هزارم، `PurchaseOrderCore.mulDivRound`)؛ `Double` فقط برای نوسان که دسته‌بندیِ اطمینان
 *  است و هیچ مقداری از آن ساخته نمی‌شود. برگشتی همان روزِ فروشِ اصلی کم شده است (`PurchaseSuggest`)؛ روزِ خالصِ منفی
 *  «بی فروش» شمرده می‌شود. موجودیِ منفی همان قاعدهٔ `DemandForecast`: صفر روز و مقدارِ بیشتر.
 */
object SmartDemand {

  const val MA_DAYS = 28
  /** روزهای بی فروش از این هزارم (نیمی از پنجره) کمتر نباشد ⇒ Croston */
  const val INTERMITTENT_PERMILLE = 500
  /** ضریبِ هموارسازیِ Croston (۰٫۱) */
  const val ALPHA = 100
  /** تصحیحِ SBA: ۱ − α/۲ */
  const val SBA = 1000 - ALPHA / 2
  const val FORECAST_DAYS = 14
  const val CV_STEADY = 500
  const val CV_NOISY = 1000
  const val MEDIUM_DAYS = 14
  const val MEDIUM_SALES = 3
  const val CV_CAP = 99_999

  private const val SAFE = 1L shl 40

  enum class Method { MOVING_AVERAGE, CROSTON }

  data class Smart(
    val method: Method,
    /** روزهای بی فروشِ پنجره، هزارم */
    val zeroPermille: Int,
    /** فروشِ روزانه بی فصل (هزارم): میانگینِ [levelDays] روزِ اخیر، یا z ÷ p × SBA */
    val levelMilli: Long,
    val levelDays: Int,
    /** Croston: اندازهٔ هموارِ هر فروش (هزارم)؛ میانگینِ متحرک ⇒ `null` */
    val sizeMilli: Long?,
    /** Croston: فاصلهٔ هموار میانِ دو فروش (هزارمِ روز)؛ میانگینِ متحرک ⇒ `null` */
    val intervalMilli: Long?,
    /** نوسان (ضریبِ تغییرات) هزارم؛ `null` = بی فروش */
    val cvPermille: Int?,
    /** فردا، پس‌فردا، … (هزارم، با روزِ هفته و فصل)؛ جمعِ n خانهٔ نخست = مصرفِ n روز */
    val perDayMilli: List<Long>,
  )

  /** پنجره‌ای که `DemandForecast.forecast` ساخت — همان عددها، بی حسابِ دوباره */
  internal class Window(
    val byDay: Map<Int, Long>,
    val start: Int,
    val today: Int,
    val eff: Int,
    val total: Long,
    val saleDays: Int,
    val sums: LongArray?,
    val counts: IntArray?,
    val season: Int,
    val leadMeasured: Boolean,
  )

  internal class Fit(val smart: Smart, val model: DemandForecast.Demand, val confidence: Confidence)

  /** Croston: هر روز همان z ÷ p × ضریب (هزارم) */
  internal class Flat(override val total: Long, val size: Long, val interval: Long, val factor: Long) : DemandForecast.Demand {
    override fun demand(n: Int): Long =
      if (n <= 0 || total == 0L) 0L else PurchaseOrderCore.mulDivRound(size, n.toLong() * factor, interval)
  }

  /** روش با قاعدهٔ روزهای بی فروش */
  fun methodOf(eff: Int, saleDays: Int): Method =
    if (saleDays >= 1 && (eff - saleDays).toLong() * 1000 >= INTERMITTENT_PERMILLE.toLong() * eff) Method.CROSTON else Method.MOVING_AVERAGE

  private fun cvOf(x: Double): Int = if (x.isNaN()) 0 else minOf(CV_CAP.toDouble(), x * 1000 + 0.5).toInt()

  /** ضریبِ تغییراتِ جمعیتی؛ کمتر از دو عدد ⇒ صفر */
  private fun cv(xs: List<Long>): Int {
    if (xs.size < 2) return 0
    val mean = xs.sumOf { it.toDouble() } / xs.size
    if (mean <= 0.0) return 0
    val ss = xs.sumOf { (it - mean) * (it - mean) }
    return cvOf(sqrt(ss / xs.size) / mean)
  }

  internal fun fit(w: Window, days: Int): Fit {
    val series = LongArray(w.eff) { k -> maxOf(0L, w.byDay[w.start + k] ?: 0L) }
    val positive = series.fold(0L) { a, b -> PurchaseOrderCore.add(a, b) }
    val zeroPermille = divRound((w.eff - w.saleDays).toLong() * 1000, w.eff.toLong()).toInt()
    val method = if (positive in 1..SAFE) methodOf(w.eff, w.saleDays) else Method.MOVING_AVERAGE

    val model: DemandForecast.Demand
    val level: Long
    val levelDays: Int
    var size: Long? = null
    var interval: Long? = null
    val cvPermille: Int?
    if (method == Method.CROSTON) {
      val events = (0 until w.eff).filter { series[it] > 0 }
      var z = divRound(positive, events.size.toLong())
      var p = divRound(w.eff.toLong() * 1000, events.size.toLong())
      for (e in 1 until events.size) {
        val q = (events[e] - events[e - 1]).toLong()
        z += divRound(ALPHA * (series[events[e]] - z), 1000)
        p += divRound(ALPHA * (q * 1000 - p), 1000)
      }
      p = maxOf(1L, p)
      size = z
      interval = p
      model = Flat(w.total, z, p, divRound(SBA.toLong() * w.season, 1000))
      level = PurchaseOrderCore.mulDivRound(z, SBA.toLong(), p)
      levelDays = w.eff
      val sizes = events.map { series[it] }
      val gaps = (1 until events.size).map { (events[it] - events[it - 1]).toLong() }
      cvPermille = if (w.total <= 0) null else maxOf(cv(sizes), if (gaps.size >= 2) cv(gaps) else 0)
    } else {
      val n = minOf(MA_DAYS, w.eff)
      var recentNet = 0L
      for (j in w.today - n + 1..w.today) recentNet = PurchaseOrderCore.add(recentNet, w.byDay[j] ?: 0L)
      val recent = maxOf(0L, recentNet)
      //  سطحِ اخیر ÷ میانگینِ پنجره (هزارم)، در ضریبِ فصل: همان شکلِ هفته، سطحِ تازه
      val ratio = if (w.total <= 0L || w.total > SAFE || n == w.eff) DemandForecast.NEUTRAL.toLong()
      else divRound(recent * w.eff * 1000, n.toLong() * w.total)
      model = DemandForecast.Model(w.total, w.eff, w.sums, w.counts, divRound(w.season * ratio, 1000).toInt(), w.today)
      level = if (w.total <= 0L) 0L else divRound(recent, n.toLong())
      levelDays = n
      cvPermille = if (w.total <= 0L) null else {
        val mean = w.total.toDouble() / w.eff
        var ss = 0.0
        for (k in 0 until w.eff) {
          val wd = DemandForecast.isoWeekday(w.start + k) - 1
          val expected = if (w.sums != null) w.sums[wd].toDouble() / w.counts!![wd] else mean
          val d = series[k] - expected
          ss += d * d
        }
        cvOf(sqrt(ss / w.eff) / mean)
      }
    }

    val confidence = when {
      w.total <= 0L || cvPermille == null -> Confidence.LOW
      method == Method.MOVING_AVERAGE && w.eff >= DemandForecast.MIN_WEEKDAY_DAYS && w.saleDays >= DemandForecast.SURE_SALE_DAYS &&
        cvPermille <= CV_STEADY && w.leadMeasured -> Confidence.HIGH
      method == Method.MOVING_AVERAGE && w.eff >= MEDIUM_DAYS && cvPermille <= CV_NOISY -> Confidence.MEDIUM
      method == Method.CROSTON && w.saleDays >= DemandForecast.SURE_SALE_DAYS && cvPermille <= CV_STEADY && w.leadMeasured -> Confidence.HIGH
      method == Method.CROSTON && w.saleDays >= MEDIUM_SALES && cvPermille <= CV_NOISY -> Confidence.MEDIUM
      else -> Confidence.LOW
    }
    val perDay = List(days) { d -> model.demand(d + 1) - model.demand(d) }
    return Fit(Smart(method, zeroPermille, level, levelDays, size, interval, cvPermille, perDay), model, confidence)
  }

  /* ── متن (فارسی/دری) ── */

  /** هزارم ⇒ «۲۵٪» (نیمه رو به بالا) */
  fun percentText(permille: Int, digits: String = LocaleFormat.DIGITS_FA): String =
    LocaleFormat.displayDigits(divRound(permille.toLong(), 10).toString(), digits) + "٪"

  /** «فروشِ گاه‌به‌گاه (۸۳٪ روزها بی فروش)، هر ۶ روز یک بار حدودِ ۲، روزانه ۰.۳۱۷» */
  fun crostonText(s: Smart, digits: String = LocaleFormat.DIGITS_FA): String =
    "فروشِ گاه‌به‌گاه (${percentText(s.zeroPermille, digits)} روزها بی فروش)، " +
      "هر ${DemandForecast.factorText((s.intervalMilli ?: 0L).toInt(), digits)} روز یک بار حدودِ ${DemandForecast.qtyText(s.sizeMilli ?: 0L, digits)}، " +
      "روزانه ${DemandForecast.qtyText(s.levelMilli, digits)}"

  fun methodText(m: Method): String = when (m) {
    Method.MOVING_AVERAGE -> "میانگینِ متحرک و روزِ هفته"
    Method.CROSTON -> "Croston (فروشِ گاه‌به‌گاه)"
  }

  /** «۱۰، ۱۰، ۱۰، ۲۴، …» — پیش‌بینیِ [n] روزِ آینده */
  fun nextDaysText(s: Smart, n: Int = 7, digits: String = LocaleFormat.DIGITS_FA): String =
    s.perDayMilli.take(n).joinToString("، ") { DemandForecast.qtyText(it, digits) }
}
