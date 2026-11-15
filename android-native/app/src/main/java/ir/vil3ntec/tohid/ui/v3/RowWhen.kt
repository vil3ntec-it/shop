package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit

/**
 *  تاریخِ کوتاهِ ردیف‌های فهرست — همان تقویم و نامِ ماهِ `displayDate`/`I18n.dateLong`، بی سال در سالِ جاری:
 *   • امروز ⇒ «امروز ۱۴:۰۵»؛ دیروز ⇒ «دیروز ۱۴:۰۵»؛
 *   • همین سال ⇒ «۲۸ میزان ۱۴:۰۵» (انگلیسیِ میلادی «Oct 20 14:05»)؛
 *   • سالِ دیگر ⇒ «۲۸ میزان ۱۴۰۴» (بی ساعت).
 *  خورشیدی وقتی زبان فارسی/دری/پشتو است (تقویمِ خودکار)، میلادی برای انگلیسی یا «تقویمِ میلادی». رقمِ ASCII؛ رقمِ فارسی را
 *  فونت می‌کشد. خالص و بی ساعتِ دستگاه: «امروز» ِ دکان از بیرون می‌آید، پس آزمون‌پذیر است و در ترکیب (composition) سنگین نیست.
 */
object RowWhen {

  /**
   *  @param day روزِ `yyyy-MM-dd` (روزِ دکانِ همان سند)
   *  @param today امروزِ دکان (`todayIso()`)
   *  @param clock ساعتِ آماده («۱۴:۰۵») یا خالی
   */
  fun of(
    day: String,
    today: String,
    clock: String = "",
    calendar: String = LocaleChoice.calendar,
    monthsPref: String = LocaleChoice.months,
  ): String {
    val iso = day.trim().take(10)
    if (CivilDays.jdnOfIso(iso) == null) return listOf(day.trim(), clock).filter { it.isNotBlank() }.joinToString(" ")
    val word = when (iso) {
      today -> Str.today
      CivilDays.addDays(today, -1) -> StrKit.yesterday
      else -> null
    }
    if (word != null) return join(word, clock)
    val lang = I18n.lang
    val gregorian = I18n.gregorian(lang, calendar)
    val parts = parts(iso, lang, gregorian, monthsPref) ?: return join(iso, clock)
    val thisYear = parts(today, lang, gregorian, monthsPref)?.year
    return if (thisYear == parts.year) join(parts.dayMonth, clock) else parts.dayMonth + " " + parts.year
  }

  /** لحظهٔ میلی‌ثانیه‌ای ⇒ همان [of] با روز و ساعتِ تقویمیِ آن لحظه ([offsetMs] = فاصلهٔ منطقهٔ زمانی) */
  fun at(
    ms: Long,
    today: String,
    offsetMs: Int,
    calendar: String = LocaleChoice.calendar,
    monthsPref: String = LocaleChoice.months,
  ): String {
    if (ms <= 0) return ""
    val local = ms + offsetMs
    val day = CivilDays.isoOf((local.floorDiv(DAY_MS) + EPOCH_JDN).toInt())
    val minutes = local.floorDiv(60_000L).mod(1_440L).toInt()
    val clock = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    return of(day, today, clock, calendar, monthsPref)
  }

  /**
   *  روزِ سند ([day]، که کاربر می‌تواند پیش‌تر بگذارد) با ساعتِ ثبتِ [ms] **فقط** وقتی همان لحظه در همان روز است:
   *  بردِ «دیروز» که امروز ۱۴:۰۵ ثبت شد ⇒ «دیروز»، نه «دیروز ۱۴:۰۵» ِ دروغ (`claude/e-rows-63q`، ردیفِ حسابِ قرض‌دار).
   */
  fun dayAt(
    day: String,
    ms: Long,
    today: String,
    offsetMs: Int,
    calendar: String = LocaleChoice.calendar,
    monthsPref: String = LocaleChoice.months,
  ): String {
    val iso = day.trim().take(10)
    if (ms <= 0 || CivilDays.jdnOfIso(iso) == null) return of(day, today, "", calendar, monthsPref)
    val local = ms + offsetMs
    val sameDay = CivilDays.isoOf((local.floorDiv(DAY_MS) + EPOCH_JDN).toInt()) == iso
    if (!sameDay) return of(iso, today, "", calendar, monthsPref)
    val minutes = local.floorDiv(60_000L).mod(1_440L).toInt()
    return of(iso, today, "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}", calendar, monthsPref)
  }

  private class Parts(val dayMonth: String, val year: Int)

  private fun parts(iso: String, lang: Lang, gregorian: Boolean, monthsPref: String): Parts? {
    if (gregorian) {
      val g = CivilDays.jdnOfIso(iso)?.let { CivilDays.gregorianOf(it) } ?: return null
      val month = I18n.gregorianMonths(lang)[g.month - 1]
      return Parts(if (lang == Lang.EN) "${month.take(3)} ${g.day}" else "${g.day} $month", g.year)
    }
    val j = SolarHijri.toSolar(iso) ?: return null
    val month = I18n.solarMonths(lang, monthsPref)[j.month - 1]
    return Parts(if (lang == Lang.EN) "$month ${j.day}" else "${j.day} $month", j.year)
  }

  private fun join(a: String, clock: String): String = if (clock.isBlank()) a else "$a $clock"

  private const val DAY_MS = 86_400_000L

  /** شمارهٔ روزِ ژولینیِ ۱۹۷۰-۰۱-۰۱ (همان حسابِ `CivilDays`، بی فرضِ ثابت) */
  private val EPOCH_JDN = CivilDays.jdnOf(1970, 1, 1).toLong()
}
