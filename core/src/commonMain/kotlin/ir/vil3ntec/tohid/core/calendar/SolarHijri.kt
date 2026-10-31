package ir.vil3ntec.tohid.core.calendar

import ir.vil3ntec.tohid.core.calendar.CivilDays.digits
import ir.vil3ntec.tohid.core.calendar.CivilDays.pad2

/**
 *  هجری خورشیدی (بخشِ ۳.۱۴، LOC-01).
 *
 *  همان الگوریتمِ jalaali (جدولِ «شکست»‌ها) که `ir.vil3ntec.tohid.Jalali`
 *  از روزِ اول داشت؛ آزمونِ `CoreVectorsTest` نشان می‌دهد هر روزِ ۱۳۰۰ تا
 *  ۱۵۰۰ در هر دو یکی است. همتای `license/calendar-core.js`، با فایلِ مرجعِ
 *  `core/test-vectors/calendar.json`.
 *
 *  نامِ ماه‌ها جدولِ خودمان است، نه Intl/ICU: هم برای اندرویدهای قدیمی،
 *  هم چون `fa-AF` ماهِ ششم را «سنبلهٔ» می‌نویسد و `ps-AF` پسوندِ «AP» دارد.
 */
object SolarHijri {

  private val BREAKS = intArrayOf(
    -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
    1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
  )

  const val MIN_YEAR = 1
  const val MAX_YEAR = 3177

  val MONTHS_FA = listOf("حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله", "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت")
  val MONTHS_PS = listOf("وری", "غویی", "غبرګولی", "چنګاښ", "زمری", "وږی", "تله", "لړم", "لیندۍ", "مرغومی", "سلواغه", "کب")

  private class Cal(val leap: Int, val gy: Int, val march: Int)

  private fun cal(jy: Int): Cal {
    val gy = jy + 621
    var leapJ = -14
    var jp = BREAKS[0]
    var jump = 0
    for (i in 1 until BREAKS.size) {
      val jm = BREAKS[i]
      jump = jm - jp
      if (jy < jm) break
      leapJ += (jump / 33) * 8 + (jump % 33) / 4
      jp = jm
    }
    var n = jy - jp
    leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
    if (jump % 33 == 4 && jump - n == 4) leapJ += 1
    val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
    val march = 20 + leapJ - leapG
    if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
    var leap = (((n + 1) % 33) - 1) % 4
    if (leap == -1) leap = 4
    return Cal(leap, gy, march)
  }

  fun isLeap(jy: Int): Boolean = jy in MIN_YEAR..MAX_YEAR && cal(jy).leap == 0

  /** ۳۱ برای شش ماهِ اول، ۳۰ برای پنج ماهِ بعد، حوت ۲۹ یا ۳۰؛ ماهِ نادرست ⇒ ۰ */
  fun monthLength(jy: Int, jm: Int): Int = when (jm) {
    in 1..6 -> 31
    in 7..11 -> 30
    12 -> if (isLeap(jy)) 30 else 29
    else -> 0
  }

  /** از شمارهٔ روزِ ژولینی؛ بیرون از دامنه ⇒ null */
  fun ofJdn(jdn: Int): Ymd? {
    val gy = CivilDays.gregorianOf(jdn).year
    var jy = gy - 621
    if (jy !in MIN_YEAR..MAX_YEAR) return null
    val c = cal(jy)
    var k = jdn - CivilDays.jdnOf(gy, 3, c.march)
    if (k >= 0) {
      if (k <= 185) return Ymd(jy, 1 + k / 31, k % 31 + 1)
      k -= 186
    } else {
      jy -= 1
      if (jy < MIN_YEAR) return null
      k += 179
      if (c.leap == 1) k += 1
    }
    return Ymd(jy, 7 + k / 30, k % 30 + 1)
  }

  /** میلادی ⇒ خورشیدی؛ تاریخِ نادرست ⇒ null */
  fun toSolar(iso: String): Ymd? = CivilDays.jdnOfIso(iso)?.let(::ofJdn)

  /** خورشیدی ⇒ `YYYY-MM-DD` ِ میلادی؛ روزِ نادرست (۳۰ حوتِ سالِ غیرکبیسه هم) ⇒ null */
  fun fromSolar(jy: Int, jm: Int, jd: Int): String? {
    if (jy !in MIN_YEAR..MAX_YEAR || jd < 1 || jd > monthLength(jy, jm)) return null
    val c = cal(jy)
    val jdn = CivilDays.jdnOf(c.gy, 3, c.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    return CivilDays.isoOf(jdn)
  }

  /** lang: "ps" ⇒ پشتو؛ هر چیزِ دیگر ⇒ دری */
  fun monthName(m: Int, lang: String): String =
    (if (lang == "ps") MONTHS_PS else MONTHS_FA).getOrElse(m - 1) { "" }

  /** `1405/07/22` — همان شکلِ `formatDate`؛ digits = "fa" ⇒ ۱۴۰۵/۰۷/۲۲ */
  fun formatNumeric(iso: String, digits: String): String {
    val j = toSolar(iso) ?: return iso
    return digits("${j.year}/${pad2(j.month)}/${pad2(j.day)}", digits)
  }

  /** `22 میزان 1405` — از اجزا ساخته می‌شود */
  fun formatLong(iso: String, lang: String, digits: String): String {
    val j = toSolar(iso) ?: return iso
    return "${digits(j.day.toString(), digits)} ${monthName(j.month, lang)} ${digits(j.year.toString(), digits)}"
  }

  /** سالِ مالی: حمل تا حوت ⇒ (از، تا) میلادی */
  fun fiscalYearRange(jy: Int): Pair<String, String>? {
    val first = fromSolar(jy, 1, 1) ?: return null
    val last = fromSolar(jy, 12, monthLength(jy, 12)) ?: return null
    return Pair(first, last)
  }
}
