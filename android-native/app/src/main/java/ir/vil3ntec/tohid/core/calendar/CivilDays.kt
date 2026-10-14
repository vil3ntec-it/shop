package ir.vil3ntec.tohid.core.calendar

/** یک روز در هر تقویم: سال، ماه، روز */
data class Ymd(val year: Int, val month: Int, val day: Int)

/**
 *  میلادی ⇄ شمارهٔ روزِ ژولینی (JDN) — پایهٔ هر سه تقویم و روزِ هفته.
 *
 *  تاریخ همیشه رشتهٔ `YYYY-MM-DD` ِ میلادی است، همان که در فایلِ داده
 *  ذخیره می‌شود. همتای دقیقِ `license/calendar-core.js`: `/` و `%` ِ کاتلین
 *  مثلِ `~~` و `%` ِ JS رو به صفر می‌بُرند، پس فرمول‌ها عیناً یکی‌اند.
 *  JDK تقویمِ فارسی ندارد و `java.util.Calendar` هم به منطقهٔ زمانی بسته
 *  است؛ این‌جا فقط حسابِ صحیح است.
 */
object CivilDays {

  /** JDN ِ یک تاریخِ میلادی — بی‌سنجش (همان `g2d` ِ الگوریتمِ jalaali) */
  fun jdnOf(gy: Int, gm: Int, gd: Int): Int {
    val d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 +
      (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
    return d - ((gy + 100100 + (gm - 8) / 6) / 100 * 3) / 4 + 752
  }

  fun gregorianOf(jdn: Int): Ymd {
    var j = 4 * jdn + 139361631
    j += (((4 * jdn + 183187720) / 146097) * 3) / 4 * 4 - 3908
    val i = ((j % 1461) / 4) * 5 + 308
    val gd = (i % 153) / 5 + 1
    val gm = (i / 153) % 12 + 1
    val gy = j / 1461 - 100100 + (8 - gm) / 6
    return Ymd(gy, gm, gd)
  }

  private fun gregorianDays(y: Int, m: Int): Int = when (m) {
    2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
  }

  private val ISO = Regex("^([0-9]{4})-([0-9]{2})-([0-9]{2})$")

  /** `YYYY-MM-DD` ِ درست ⇒ JDN؛ هر چیزِ دیگر (۲۰۲۶-۰۲-۳۰ هم) ⇒ null */
  fun jdnOfIso(iso: String): Int? {
    //  `[0-9]` نه `\d`: در اندروید (ICU) `\d` رقمِ فارسی را هم می‌گیرد
    val m = ISO.matchEntire(iso) ?: return null
    val y = m.groupValues[1].toInt()
    val mo = m.groupValues[2].toInt()
    val d = m.groupValues[3].toInt()
    if (y < 1 || mo !in 1..12 || d < 1 || d > gregorianDays(y, mo)) return null
    return jdnOf(y, mo, d)
  }

  fun isoOf(jdn: Int): String {
    val g = gregorianOf(jdn)
    return g.year.toString().padStart(4, '0') + "-" +
      g.month.toString().padStart(2, '0') + "-" + g.day.toString().padStart(2, '0')
  }

  /** چند روز جلو یا عقب */
  fun addDays(iso: String, n: Int): String? = jdnOfIso(iso)?.let { isoOf(it + n) }

  /** روزِ هفته به شمارِ ISO: ۱ دوشنبه … ۵ جمعه، ۶ شنبه، ۷ یکشنبه */
  fun isoWeekday(iso: String): Int? = jdnOfIso(iso)?.let { it % 7 + 1 }

  internal fun digits(text: String, style: String): String =
    if (style != "fa") text
    else buildString(text.length) { for (c in text) append(if (c in '0'..'9') '۰' + (c - '0') else c) }

  internal fun pad2(n: Int): String = n.toString().padStart(2, '0')
}
