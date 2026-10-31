package ir.vil3ntec.tohid.core.calendar

import ir.vil3ntec.tohid.core.calendar.CivilDays.digits

/**
 *  هجری قمری — جدولیِ مدنی (همان `islamic-civil` ِ Intl)، اختیاری روی اسناد.
 *
 *  ماهِ واقعی با دیدنِ هلال است و جدولی تا یکی‌دو روز می‌لغزد؛ پس
 *  `adjust` (روز، مثبت یا منفی) را تنظیمِ فروشگاه می‌دهد. همتای
 *  `license/calendar-core.js`، با `core/test-vectors/calendar.json`.
 */
object LunarHijri {

  /** ۱ محرم ۱ = ۱۶ ژوئیهٔ ۶۲۲ (ژولینی) */
  private const val EPOCH = 1948440

  val MONTHS = listOf(
    "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
    "رجب", "شعبان", "رمضان", "شوال", "ذوالقعده", "ذوالحجه",
  )

  private fun start(y: Int, m: Int): Int =
    (y - 1) * 354 + (3 + 11 * y).floorDiv(30) + (59 * (m - 1) + 1) / 2 + EPOCH

  fun toLunar(iso: String, adjust: Int = 0): Ymd? {
    val jdn = (CivilDays.jdnOfIso(iso) ?: return null) + adjust
    if (jdn < EPOCH) return null
    val y = (30 * (jdn - EPOCH) + 10646).floorDiv(10631)
    val m = (-(-2 * (jdn - 29 - start(y, 1))).floorDiv(59) + 1).coerceIn(1, 12)
    return Ymd(y, m, jdn - start(y, m) + 1)
  }

  fun monthName(m: Int): String = MONTHS.getOrElse(m - 1) { "" }

  /** `2 جمادی‌الاول 1448` */
  fun formatLong(iso: String, digits: String, adjust: Int = 0): String {
    val h = toLunar(iso, adjust) ?: return iso
    return "${digits(h.day.toString(), digits)} ${monthName(h.month)} ${digits(h.year.toString(), digits)}"
  }
}
