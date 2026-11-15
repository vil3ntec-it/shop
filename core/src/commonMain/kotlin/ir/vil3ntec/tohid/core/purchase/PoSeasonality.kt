package ir.vil3ntec.tohid.core.purchase

/**
 *  PUR-03 — فصل و درجهٔ اطمینان در پیشنهادِ سفارشِ خرید (تصمیمِ هماهنگ‌کننده به سپردهٔ صاحب‌کار، ۱۴۰۵/۰۸/۲۴). روی همان
 *  پیش‌بینیِ INV-13 (`DemandForecast`) سوار است و هر دو جا آن را صدا می‌زنند: صفحهٔ «پیشنهادِ سفارش» و «پیشنهاد با فروش و
 *  زمانِ تأمین» ِ فرمِ سفارش (`data/PurchaseSuggest`).
 *
 *  ── فصل فقط با دست‌کم ۱۲ ماه تاریخچهٔ فروش ─────────────────────────
 *  تاریخچه = روزهای از **نخستین فروشِ** همین کالا تا امروز. کمتر از [MIN_HISTORY_DAYS] ⇒ همان حسابِ امروز بیت‌به‌بیت
 *  (`DemandForecast.forecast` بی تغییر) و اطمینانِ «کم».
 *  دست‌کم ۱۲ ماه ⇒ ضریبِ فصل = فروشِ روزانهٔ «همین ماهِ پارسال» ([MONTH_DAYS] روزِ پیشِ رو، یک سال پیش) ÷ میانگینِ
 *  روزانهٔ «پنجرهٔ اخیر»: همان پنجره در پارسال اگر تاریخچه آن را دارد (رشدِ امسال سرِ جایش می‌ماند)، وگرنه پنجرهٔ اخیرِ
 *  امسال؛ میانِ ۰٫۵ و ۳ (`DemandForecast.SEASON_MIN/MAX`). پایهٔ بی فروش ⇒ بی فصل. این ضریب جای «همین روزهای پارسال» ِ
 *  INV-13 را می‌گیرد (`DemandForecast.Input.seasonOverride`)؛ بقیه (روزِ هفته، زمانِ تأمین، «در راه»، حدِ کم) همان.
 *
 *  ── اطمینان: درازای تاریخچه و نوسان ─────────────────────────────────
 *  نوسان = ضریبِ پراکندگیِ فروشِ روزانهٔ پنجره (انحرافِ معیار ÷ میانگین، روزهای بی فروش صفر).
 *    کم ..... کمتر از ۱۲ ماه، یا پنجره بی فروش، یا نوسان بیش از [CV_LOW] هزارم (فروشِ پراکنده)
 *    زیاد ... دست‌کم [SURE_HISTORY_DAYS] روز (فصل دو بار دیده شده) و نوسان تا [CV_SURE] هزارم
 *    متوسط .. بقیه
 *  ⛔ فقط پیشنهاد است: سفارش بی تأییدِ آدم ساخته یا فرستاده نمی‌شود (INV-13). Kotlin ِ خالص در commonMain؛ آزمون:
 *  `PoSeasonalityTest`.
 */
object PoSeasonality {

  /** ۱۲ ماه */
  const val MIN_HISTORY_DAYS = 365
  /** ۲۴ ماه */
  const val SURE_HISTORY_DAYS = 730
  /** «همین ماهِ پارسال» */
  const val MONTH_DAYS = 30
  /** نوسانِ «زیاد»: تا ۱ (هزارم) */
  const val CV_SURE = 1000
  /** بیش از ۲ ⇒ «کم» */
  const val CV_LOW = 2000

  enum class Confidence(val wire: String) { LOW("low"), MEDIUM("medium"), HIGH("high") }

  data class Season(
    /** ضریبِ به‌کاررفته به هزارم؛ `null` = بی فصل (تاریخچهٔ کوتاه، پایهٔ بی فروش، یا ضریبِ ۱) */
    val permille: Int?,
    /** روزهای از نخستین فروش تا امروز (خودِ امروز هم)؛ ۰ = هنوز فروشی نبود */
    val historyDays: Int,
    /** نوسانِ فروشِ روزانهٔ پنجره به هزارم؛ `null` = پنجره بی فروش */
    val cvPermille: Int?,
    val confidence: Confidence,
    /** پایه «همان پنجره در پارسال» بود (نه پنجرهٔ امسال) */
    val baseLastYear: Boolean,
  ) {
    val seasonal: Boolean get() = historyDays >= MIN_HISTORY_DAYS
  }

  data class Result(val forecast: DemandForecast.Forecast, val season: Season)

  /** [firstSaleJdn] = نخستین روزِ فروشِ همین کالا (نه خرید)؛ `null` = هنوز فروشی نبود */
  fun forecast(i: DemandForecast.Input, firstSaleJdn: Int?): Result {
    val s = season(i.todayJdn, i.history, firstSaleJdn, i.windowDays)
    //  کمتر از ۱۲ ماه ⇒ همان ورودی، همان حساب
    val f = if (!s.seasonal) DemandForecast.forecast(i)
    else DemandForecast.forecast(i.copy(seasonOverride = s.permille ?: DemandForecast.NEUTRAL))
    return Result(f, s)
  }

  fun season(todayJdn: Int, history: List<DemandForecast.Day>, firstSaleJdn: Int?, windowDays: Int = DemandForecast.WINDOW_DAYS): Season {
    val t = todayJdn
    val historyDays = if (firstSaleJdn == null || firstSaleJdn > t) 0 else t - firstSaleJdn + 1
    val byDay = HashMap<Int, Long>()
    for (d in history) byDay[d.jdn] = PurchaseOrderCore.add(byDay[d.jdn] ?: 0L, d.qtyMilli)
    fun sum(from: Int, to: Int): Long {
      var s = 0L
      for ((j, q) in byDay) if (j in from..to) s = PurchaseOrderCore.add(s, q)
      return maxOf(0L, s)
    }
    //  پنجرهٔ اخیر: همان پنجرهٔ INV-13، کالای تازه‌تر از آن از نخستین فروش
    val start = maxOf(t - windowDays + 1, if (historyDays > 0) firstSaleJdn!! else t - windowDays + 1)
    val eff = t - start + 1
    val cv = cvPermille(List(eff) { maxOf(0L, byDay[start + it] ?: 0L) })

    var permille: Int? = null
    var baseLastYear = false
    if (historyDays >= MIN_HISTORY_DAYS) {
      val ly = t - DemandForecast.YEAR_DAYS
      val month = sum(ly + 1, ly + MONTH_DAYS)
      baseLastYear = firstSaleJdn!! <= ly - eff + 1
      val base = if (baseLastYear) sum(ly - eff + 1, ly) else sum(start, t)
      if (base > 0L && base <= SAFE_SUM && month <= SAFE_SUM) {
        //  هر دو روزانه: (ماه ÷ ۳۰) ÷ (پایه ÷ پنجره)
        val f = DemandForecast.divRound(month * eff * DemandForecast.NEUTRAL, MONTH_DAYS.toLong() * base)
        permille = f.coerceIn(DemandForecast.SEASON_MIN.toLong(), DemandForecast.SEASON_MAX.toLong()).toInt()
          .takeIf { it != DemandForecast.NEUTRAL }
      }
    }
    val confidence = when {
      historyDays < MIN_HISTORY_DAYS || cv == null || cv > CV_LOW -> Confidence.LOW
      historyDays >= SURE_HISTORY_DAYS && cv <= CV_SURE -> Confidence.HIGH
      else -> Confidence.MEDIUM
    }
    return Season(permille, historyDays, cv, confidence, baseLastYear && permille != null)
  }

  /** بالاتر از این (۲^۴۰ هزارم) ضریب ساخته نمی‌شود تا ضربِ میانی سرریز نکند */
  private const val SAFE_SUM = 1L shl 40

  /** انحرافِ معیار ÷ میانگین به هزارم؛ بی فروش ⇒ `null` */
  internal fun cvPermille(days: List<Long>): Int? {
    if (days.isEmpty()) return null
    val n = days.size.toDouble()
    val mean = days.sumOf { it.toDouble() } / n
    if (mean <= 0.0) return null
    val variance = days.sumOf { (it - mean) * (it - mean) } / n
    return kotlin.math.round(kotlin.math.sqrt(variance) / mean * 1000.0).toInt()
  }

}
