package ir.vil3ntec.tohid.core.calendar

/**
 *  قاعده‌های هفتهٔ یک دکان (LOC-03). روزِ هفته به شمارِ ISO: ۱ دوشنبه …
 *  ۵ جمعه، ۶ شنبه، ۷ یکشنبه. پیش‌فرض: هفته از شنبه، جمعه تعطیل — هر دو
 *  قابلِ تنظیم (بیشترِ دکان‌ها هفت روز بازند ⇒ `weekend = emptySet()`).
 *  `holidays` جدولِ تعطیلاتِ سرور است و هیچ‌جا ثابت نوشته نمی‌شود.
 */
data class WeekRules(
  val weekStart: Int = 6,
  val weekend: Set<Int> = setOf(5),
  val holidays: Set<String> = emptySet(),
)

enum class DayKind(val code: String) { HOLIDAY("holiday"), WEEKEND("weekend"), WORK("work") }

/** همتای بخشِ «هفته و روزِ کاری» ِ `license/calendar-core.js` */
object BusinessDay {

  /** نخستین روزِ هفته‌ای که این روز در آن است */
  fun weekStartOf(iso: String, rules: WeekRules = WeekRules()): String? {
    val wd = CivilDays.isoWeekday(iso) ?: return null
    return CivilDays.addDays(iso, -((wd - rules.weekStart + 7) % 7))
  }

  fun dayKind(iso: String, rules: WeekRules = WeekRules()): DayKind? {
    val wd = CivilDays.isoWeekday(iso) ?: return null
    return when {
      iso in rules.holidays -> DayKind.HOLIDAY
      wd in rules.weekend -> DayKind.WEEKEND
      else -> DayKind.WORK
    }
  }

  fun isWorkingDay(iso: String, rules: WeekRules = WeekRules()): Boolean = dayKind(iso, rules) == DayKind.WORK

  private val LOCAL = Regex("^([0-9]{4}-[0-9]{2}-[0-9]{2})T([0-9]{2}):([0-9]{2})(:[0-9]{2})?$")

  /**
   *  روزِ کاری که از نیمه‌شب می‌گذرد: دکانی که تا ۲ بامداد باز است
   *  (dayStartMinutes = 120)، فروشِ ۱:۳۰ را به نامِ دیروز می‌نویسد.
   *  local = `YYYY-MM-DDTHH:MM` (ثانیه اختیاری) به وقتِ دکان.
   */
  fun businessDate(local: String, dayStartMinutes: Int): String? {
    val m = LOCAL.matchEntire(local) ?: return null
    val h = m.groupValues[2].toInt()
    val min = m.groupValues[3].toInt()
    val date = m.groupValues[1]
    if (h > 23 || min > 59 || CivilDays.jdnOfIso(date) == null) return null
    return if (h * 60 + min < dayStartMinutes) CivilDays.addDays(date, -1) else date
  }

  /** همان، از ساعتِ میلی‌ثانیه‌ای و اختلافِ ساعتِ دکان با UTC (کابل: ۲۷۰ دقیقه) */
  fun businessDateAt(epochMs: Long, utcOffsetMinutes: Int, dayStartMinutes: Int): String {
    val local = epochMs + utcOffsetMinutes * 60_000L
    val days = local.floorDiv(86_400_000L)
    val minutes = (local - days * 86_400_000L) / 60_000L
    val jdn = 2440588 + days.toInt() - if (minutes < dayStartMinutes) 1 else 0
    return CivilDays.isoOf(jdn)
  }
}
