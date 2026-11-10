package ir.vil3ntec.tohid.core.calendar

/**
 *  هفتهٔ دکان — LOC-03، «شروعِ هفته شنبه، جمعه تعطیل، روزهای کاریِ فروشگاه» (§۳.۱۴: «همهٔ این‌ها قابلِ تنظیم‌اند»).
 *
 *  تنظیمِ دکان‌دار در «تاریخ و رقم» دو مقدار است: آغازِ هفته (یکی از [STARTS]) و روزهای تعطیلِ هفتگی (متنِ
 *  [encodeClosed]). هر دو با [rules] به همان `WeekRules` می‌رسند و هر حسابِ هفته از `BusinessDay` (دوقلوی
 *  `license/calendar-core.js`) می‌گذرد؛ این‌جا فقط خواندن و سنجیدنِ مقدارِ ذخیره‌شده و ترتیبِ روزها برای نمایش است.
 *  تنظیم‌نشده یا مقدارِ ناشناخته ⇒ همان `WeekRules()`: هفته از شنبه، جمعه تعطیل — یعنی رفتارِ پیشین.
 */
object ShopWeek {

  /** شنبه — آغازِ هفتهٔ افغانستان (پیش‌فرض) */
  const val DEFAULT_START: Int = 6

  /** آغازهایی که تنظیم می‌دهد (ISO): شنبه، یکشنبه، دوشنبه */
  val STARTS: List<Int> = listOf(6, 7, 1)

  /** تعطیلِ هفتگیِ پیش‌فرض: جمعه */
  val DEFAULT_CLOSED: Set<Int> = setOf(5)

  /** آغازِ ذخیره‌شدهٔ ناشناخته ⇒ شنبه */
  fun clampStart(day: Int): Int = if (day in STARTS) day else DEFAULT_START

  /** هفت روزِ هفته به شمارِ ISO از آغازِ هفته؛ آغازِ بیرون از ۱..۷ ⇒ شنبه (همان قاعدهٔ `SalesHeatmap`) */
  fun order(weekStart: Int): List<Int> {
    val s = if (weekStart in 1..7) weekStart else DEFAULT_START
    return (0 until 7).map { (s - 1 + it) % 7 + 1 }
  }

  /** جای روز در هفتهٔ دکان: ۰ = آغازِ هفته … ۶ (از `BusinessDay.weekStartOf`)؛ تاریخِ نادرست ⇒ null */
  fun indexOf(iso: String, rules: WeekRules = WeekRules()): Int? {
    val start = BusinessDay.weekStartOf(iso, rules) ?: return null
    val a = CivilDays.jdnOfIso(iso) ?: return null
    val b = CivilDays.jdnOfIso(start) ?: return null
    return a - b
  }

  /**
   *  روزهای تعطیلِ ذخیره‌شده: `"5"`، `"4,5"`؛ `""` یعنی هر هفت روز باز. تنظیم‌نشده (`null`)، بی هیچ روزِ درست، یا
   *  هر هفت روز بسته ⇒ جمعه: دکانِ بی روزِ کاری معنا ندارد و هیچ هفته‌ای سراسر «تعطیل» نمی‌شود.
   */
  fun parseClosed(raw: String?): Set<Int> {
    if (raw == null) return DEFAULT_CLOSED
    if (raw.isBlank()) return emptySet()
    val days = raw.split(',').mapNotNull { part -> part.trim().toIntOrNull()?.takeIf { it in 1..7 } }.toSet()
    return if (days.isEmpty() || days.size == 7) DEFAULT_CLOSED else days
  }

  /** همان، برای نوشتن: شماره‌های درست، مرتب، با ویرگول؛ هیچ ⇒ `""` (هر هفت روز باز) */
  fun encodeClosed(days: Set<Int>): String = days.filter { it in 1..7 }.sorted().joinToString(",")

  /** بستن یا باز کردنِ یک روز؛ آخرین روزِ کاری بسته نمی‌شود و روزِ نادرست چیزی را عوض نمی‌کند */
  fun toggle(closed: Set<Int>, day: Int): Set<Int> = when {
    day !in 1..7 -> closed
    day in closed -> closed - day
    (closed + day).count { it in 1..7 } >= 7 -> closed
    else -> closed + day
  }

  /** قاعدهٔ هفتهٔ دکان از دو مقدارِ ذخیره‌شده؛ `holidays` جدولِ تعطیلاتِ سرور است (LOC-04) */
  fun rules(weekStart: Int, closedRaw: String?, holidays: Set<String> = emptySet()): WeekRules =
    WeekRules(weekStart = clampStart(weekStart), weekend = parseClosed(closedRaw), holidays = holidays)
}
