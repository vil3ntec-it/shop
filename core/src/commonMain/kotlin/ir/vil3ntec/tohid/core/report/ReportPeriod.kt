package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri

/**
 *  RPT-13 — کار با گزارش با کیبورد (§۵.۲): «دورهٔ قبل / بعد» (− و +) و «مقایسه با دورهٔ قبل» (Ctrl+Shift+C).
 *
 *  هر بازهٔ گزارش یک «واحد» دارد و جابه‌جایی به همان واحد است:
 *  - [Unit.DAYS] (امروز، ۷ روز، «از … تا …»): همان طول، چند بار پس یا پیش.
 *  - [Unit.GMONTH] («این ماه» = از اولِ ماهِ میلادی تا امروز): ماهِ میلادیِ کامل؛ ماهی که امروز در آن است
 *    تا امروز می‌رود (همان معنای `ReportEngine.rangeOf(MONTH)`).
 *  - [Unit.JMONTH] / [Unit.JYEAR]: ماه یا سالِ خورشیدیِ کامل (حوتِ ۲۹ یا ۳۰ روزه از `SolarHijri`).
 *  - [Unit.NONE] («همه»): جابه‌جا نمی‌شود.
 *
 *  هیچ دوره‌ای که سراسر در آینده است ساخته نمی‌شود (`null`): «دورهٔ بعد» در دورهٔ جاری کاری نمی‌کند.
 *  تاریخ‌ها `YYYY-MM-DD` ِ میلادی‌اند؛ ورودیِ نادرست ⇒ `null`.
 *
 *  Kotlin ِ خالص (commonMain)، بی android.* و java.*.
 */
object ReportPeriod {

  enum class Unit { DAYS, GMONTH, JMONTH, JYEAR, NONE }

  data class Range(val from: String, val to: String)

  /** بازهٔ `steps` دوره پس (منفی) یا پیش (مثبت) از `from..to`؛ صفر ⇒ همان */
  fun shift(unit: Unit, from: String, to: String, steps: Int, today: String): Range? {
    if (steps == 0) return Range(from, to)
    val out = when (unit) {
      Unit.NONE -> null
      Unit.DAYS -> {
        val a = CivilDays.jdnOfIso(from) ?: return null
        val b = CivilDays.jdnOfIso(to) ?: return null
        if (b < a) return null
        val len = b - a + 1
        Range(CivilDays.isoOf(a + steps * len), CivilDays.isoOf(b + steps * len))
      }
      Unit.GMONTH -> {
        CivilDays.jdnOfIso(from) ?: return null
        val y = from.substring(0, 4).toInt()
        val m = from.substring(5, 7).toInt()
        val idx = y * 12 + (m - 1) + steps
        val ny = idx.floorDiv(12)
        val nm = idx.mod(12) + 1
        val first = "${ny.toString().padStart(4, '0')}-${nm.toString().padStart(2, '0')}-01"
        val nextIdx = idx + 1
        val next = "${nextIdx.floorDiv(12).toString().padStart(4, '0')}-${(nextIdx.mod(12) + 1).toString().padStart(2, '0')}-01"
        val last = CivilDays.addDays(next, -1) ?: return null
        Range(first, if (today in first..last) today else last)
      }
      Unit.JMONTH -> {
        val s = SolarHijri.toSolar(from) ?: return null
        val idx = s.year * 12 + (s.month - 1) + steps
        val jy = idx.floorDiv(12)
        val jm = idx.mod(12) + 1
        val a = SolarHijri.fromSolar(jy, jm, 1) ?: return null
        val b = SolarHijri.fromSolar(jy, jm, SolarHijri.monthLength(jy, jm)) ?: return null
        Range(a, b)
      }
      Unit.JYEAR -> {
        val s = SolarHijri.toSolar(from) ?: return null
        val jy = s.year + steps
        val a = SolarHijri.fromSolar(jy, 1, 1) ?: return null
        val b = SolarHijri.fromSolar(jy, 12, SolarHijri.monthLength(jy, 12)) ?: return null
        Range(a, b)
      }
    } ?: return null
    //  دورهٔ سراسر آینده نه
    return if (out.from > today) null else out
  }

  /** `diff` = جاری − پیشین؛ `bp` = تغییر به ده‌هزارم نسبت به |پیشین| (نیمه دور از صفر)؛ پیشینِ صفر ⇒ null */
  data class Change(val diff: Long, val bp: Long?)

  private const val MAX_DIFF = Long.MAX_VALUE / 10_000L

  fun change(current: Long, previous: Long): Change {
    val diff = current - previous
    //  پیشینِ صفر، یا تفاضلی که ×۱۰٬۰۰۰ از Long می‌گذرد ⇒ بی درصد
    if (previous == 0L || diff > MAX_DIFF || diff < -MAX_DIFF) return Change(diff, null)
    val base = if (previous < 0) -previous else previous
    val scaled = diff * 10_000L
    val q = scaled / base
    val r = scaled % base
    val bp = if (2 * (if (r < 0) -r else r) >= base) q + (if (scaled < 0) -1 else 1) else q
    return Change(diff, bp)
  }
}
