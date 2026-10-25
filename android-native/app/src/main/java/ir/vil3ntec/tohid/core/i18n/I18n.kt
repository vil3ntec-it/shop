package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.text.LocaleFormat

/**
 *  زبانِ نوشته‌های برنامه — فارسی (پیش‌فرض)، انگلیسی و پشتو.
 *
 *  این فقط زبانِ **صفحه** است: داده، کلیدهای همگام‌سازی، CSV و یادداشت‌های
 *  ذخیره‌شده هیچ‌وقت با آن عوض نمی‌شوند. انتخاب روی همین دستگاه می‌ماند
 *  (`LocaleChoice.lang`) و همگام نمی‌شود.
 */
enum class Lang(val code: String, val rtl: Boolean, /** نامِ زبان به خودِ آن زبان — برای انتخاب‌گر */ val native: String) {
  FA("fa", true, "فارسی"),
  EN("en", false, "English"),
  PS("ps", true, "پښتو");

  companion object {
    /** هر مقدارِ ناشناخته یا خالی ⇒ فارسی، یعنی رفتارِ امروز */
    fun of(code: String?): Lang = entries.firstOrNull { it.code == code } ?: FA
  }
}

/**
 *  جدولِ نوشته‌ها — کاتلینِ خالص، مشترکِ اندروید و کامپیوتر (بی `R.string`).
 *
 *  هر نوشته یک ویژگیِ نام‌دار در یکی از شیءهای `Str*` است و سه زبانش کنارِ هم
 *  نوشته شده: `val save get() = t("ذخیره", "Save", "خوندي کول")`. پس:
 *   - کلید غلط کامپایل نمی‌شود (نوعی‌امن)؛
 *   - فارسی همیشه هست، چون آرگومانِ اجباری است؛
 *   - انگلیسی یا پشتوی جاافتاده (`null`) ⇒ همان فارسی، نه کرش و نه نامِ کلید؛
 *   - `I18nCoverageTest` همهٔ ویژگی‌ها را صدا می‌زند و جاافتاده‌ها را می‌شمارد.
 *
 *  ⚠️ کامپیوتر: `desktop/build.gradle.kts` (جدولِ `phrases`) پیش از کامپایل
 *  «گوشی/دوربین» را در **متنِ فارسیِ** همین فایل‌ها هم به «کامپیوتر/بارکدخوان»
 *  برمی‌گرداند. برای انگلیسی و پشتو، نوشته‌ای که به گوشی یا دوربین بند است با
 *  `td(…)` نسخهٔ کامپیوتریِ خودش را کنارش دارد.
 *
 *  ⚠️ نوشته را در `remember { }` یا `val` ِ سطحِ بالا نگه ندارید: ویژگی هر بار
 *  که خوانده شود زبانِ همان لحظه را می‌دهد، و با عوض‌شدنِ زبان، صفحه‌ای که
 *  آن را در ترکیب (composition) خوانده خودش دوباره کشیده می‌شود.
 */
object I18n {

  /**
   *  زبانِ همین لحظه. برنامه آن را به `LocaleChoice.lang` (حالتِ Compose) وصل
   *  می‌کند؛ خواندنش در ترکیب ثبت می‌شود و با زدنِ کلید، صفحه بی بازکردنِ
   *  دوباره عوض می‌شود. آزمون‌ها مستقیم عوضش می‌کنند.
   */
  @Volatile var source: () -> Lang = { Lang.FA }

  /** نسخهٔ کامپیوتر؟ (`ui.Platform.DESKTOP`) — فقط برای `td` */
  @Volatile var desktop: Boolean = false

  val lang: Lang get() = source()

  /** یک ردیفِ جدول، برای آزمونِ پوشش */
  class Entry(val fa: String, val en: String?, val ps: String?, val enDesk: String? = null, val psDesk: String? = null)

  /** فقط آزمون: هر `t` و `td` که خوانده شود این‌جا هم گزارش می‌شود */
  @Volatile var audit: ((Entry) -> Unit)? = null

  /** نوشتهٔ یک زبان؛ جاافتاده ⇒ فارسی */
  fun pick(lang: Lang, fa: String, en: String?, ps: String?): String = when (lang) {
    Lang.FA -> fa
    Lang.EN -> en ?: fa
    Lang.PS -> ps ?: fa
  }

  /* ------------------------------ رنگِ پیام ------------------------------ */

  enum class Tone { ERROR, SUCCESS, INFO }

  private val ERROR_FA = listOf("نشد", "نیست", "نبود", "ناموفق", "خطا", "معتبر")
  private val SUCCESS_FA = listOf("شد", "موفق")
  private val ERROR_EN = listOf("n't", "not ", "failed", "error", "invalid", "rejected", "denied")
  private val SUCCESS_EN = listOf(
    "saved", "created", "recorded", "added", "sent", "copied", "restored", "succeeded",
    "changed", "removed", "archived", "signed in", "turned on", "closed", "erased", "ready",
  )
  /** «نه» و «ونه» ِ جدا (نفیِ پشتو)، نه تکه‌ای از واژه («بدلونونه» خطا نیست) */
  private val ERROR_PS = Regex("(^|\\s)(ونه|نه)\\s|ناکام|تېروتنه|نشته")
  private val SUCCESS_PS = Regex("(^|\\s)(شو|شوه|شول|شوې|شوي)([\\s.،!]|$)|بریالی|بریالۍ")

  private fun toneFa(message: String): Tone = when {
    ERROR_FA.any { message.contains(it) } -> Tone.ERROR
    SUCCESS_FA.any { message.contains(it) } -> Tone.SUCCESS
    else -> Tone.INFO
  }

  /**
   *  رنگِ پیامِ کوتاه (Toast) از خودِ متن. فارسی دقیقاً همان قاعدهٔ پیشین
   *  (اول خطا، بعد موفقیت)؛ انگلیسی و پشتو اول واژه‌های خودشان را می‌سنجند و
   *  اگر چیزی نیافتند همان قاعدهٔ فارسی (برای پیامی که هنوز ترجمه نشده).
   */
  fun toneOf(message: String, lang: Lang = this.lang): Tone = when (lang) {
    Lang.FA -> toneFa(message)
    Lang.EN -> {
      val low = message.lowercase()
      when {
        ERROR_EN.any { low.contains(it) } -> Tone.ERROR
        SUCCESS_EN.any { low.contains(it) } -> Tone.SUCCESS
        else -> toneFa(message)
      }
    }
    Lang.PS -> when {
      ERROR_PS.containsMatchIn(message) -> Tone.ERROR
      SUCCESS_PS.containsMatchIn(message) -> Tone.SUCCESS
      else -> toneFa(message)
    }
  }

  /* -------------------------------- شمار -------------------------------- */

  /** صورتِ مفرد یا جمع (انگلیسی، پشتو). فارسی پس از عدد مفرد است و یک صورت می‌دهد. */
  fun plural(n: Long, one: String, other: String): String = if (n == 1L || n == -1L) one else other

  /**
   *  رقمِ صفحه. فارسی و پشتو همان انتخابِ دکان (`LocaleChoice.digits`، رقمِ
   *  فارسی از ویژگیِ `ss01` ِ فونت)؛ انگلیسی همیشه لاتین.
   */
  fun screenDigits(lang: Lang, pref: String): String =
    if (lang == Lang.EN) LocaleFormat.DIGITS_LATN else pref

  /** عدد با جداکنندهٔ هزارگان، با رقمِ خواسته‌شده (برای فاکتورِ چاپی که متن را خودمان می‌کشیم) */
  fun number(value: Long, digits: String): String =
    LocaleFormat.formatAfghani(value.toDouble(), LocaleFormat.SYMBOL_NONE, digits)

  /* --------------------------------- پول --------------------------------- */

  /** واژهٔ پول: «افغانی» / «AFN» / «افغانۍ» */
  fun currencyWord(lang: Lang): String = when (lang) {
    Lang.FA -> LocaleFormat.AFN_WORD
    Lang.EN -> "AFN"
    Lang.PS -> "افغانۍ"
  }

  /**
   *  مبلغ با نماد. فارسی دقیقاً همان `LocaleFormat.formatAfghani` است؛ «؋» در هر
   *  سه زبان یکی است، فقط واژه عوض می‌شود.
   */
  fun money(value: Double, lang: Lang, digits: String, symbol: String = LocaleFormat.SYMBOL_WORD): String {
    val d = screenDigits(lang, digits)
    if (symbol != LocaleFormat.SYMBOL_WORD || lang == Lang.FA) return LocaleFormat.formatAfghani(value, symbol, d)
    return LocaleFormat.formatAfghani(value, LocaleFormat.SYMBOL_NONE, d) + " " + currencyWord(lang)
  }

  /* -------------------------------- تاریخ -------------------------------- */

  const val CAL_AUTO = "auto"
  const val CAL_SOLAR = "solar"
  const val CAL_GREGORIAN = "gregorian"

  /** هر مقدارِ ناشناخته ⇒ خودکار (فارسی و پشتو خورشیدی، انگلیسی میلادی) */
  fun calendarOf(value: String?): String = when (value) {
    CAL_SOLAR, CAL_GREGORIAN -> value
    else -> CAL_AUTO
  }

  fun gregorian(lang: Lang, calendar: String): Boolean = when (calendarOf(calendar)) {
    CAL_GREGORIAN -> true
    CAL_SOLAR -> false
    else -> lang == Lang.EN
  }

  /** نامِ ماه‌های خورشیدی به انگلیسی (همان نام‌های دری، با حرفِ لاتین) */
  val SOLAR_MONTHS_EN = listOf(
    "Hamal", "Sawr", "Jawza", "Saratan", "Asad", "Sunbula",
    "Mizan", "Aqrab", "Qaws", "Jadi", "Dalw", "Hut",
  )

  /** نامِ ماه‌های میلادی: انگلیسی، دری (افغانستان) و پشتو */
  val GREGORIAN_MONTHS_EN = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
  )
  val GREGORIAN_MONTHS_FA = listOf(
    "جنوری", "فبروری", "مارچ", "اپریل", "می", "جون",
    "جولای", "اگست", "سپتمبر", "اکتوبر", "نومبر", "دسمبر",
  )
  val GREGORIAN_MONTHS_PS = listOf(
    "جنوري", "فبروري", "مارچ", "اپرېل", "مۍ", "جون",
    "جولای", "اګست", "سپتمبر", "اکتوبر", "نومبر", "ډسمبر",
  )

  /**
   *  نامِ دوازده ماهِ خورشیدی برای صفحه. پشتو ⇒ همیشه وری، غویی…؛ انگلیسی ⇒
   *  Hamal, Sawr…؛ فارسی ⇒ انتخابِ دکان (`LocaleChoice.months`: دری یا پشتو).
   */
  fun solarMonths(lang: Lang, monthsPref: String): List<String> = when (lang) {
    Lang.PS -> SolarHijri.MONTHS_PS
    Lang.EN -> SOLAR_MONTHS_EN
    Lang.FA -> if (monthsPref == "ps") SolarHijri.MONTHS_PS else SolarHijri.MONTHS_FA
  }

  fun gregorianMonths(lang: Lang): List<String> = when (lang) {
    Lang.FA -> GREGORIAN_MONTHS_FA
    Lang.EN -> GREGORIAN_MONTHS_EN
    Lang.PS -> GREGORIAN_MONTHS_PS
  }

  /** `YYYY-MM-DD` (یا با ساعت پس از آن) ⇒ اجزای میلادی؛ خوانده نشد ⇒ null */
  private fun civil(iso: String) =
    CivilDays.jdnOfIso(iso.trim().take(10))?.let { CivilDays.gregorianOf(it) }

  /**
   *  تاریخِ عددی برای صفحه، با رقمِ ASCII (رقمِ فارسی را فونت می‌کشد).
   *  خورشیدی ⇒ `1405/07/28` (همان `formatDate`)؛ میلادی ⇒ `2026-10-20`.
   *  تاریخِ ناخوانا همان‌طور که هست برمی‌گردد تا چیزی گم نشود.
   */
  fun dateNumeric(iso: String, lang: Lang, calendar: String): String {
    if (!gregorian(lang, calendar)) return SolarHijri.formatNumeric(iso, LocaleFormat.DIGITS_LATN)
    val g = civil(iso) ?: return iso
    return g.year.toString().padStart(4, '0') + "-" + g.month.toString().padStart(2, '0') + "-" + g.day.toString().padStart(2, '0')
  }

  /** تاریخِ بلند: `28 میزان 1405` · `28 تله 1405` · `20 October 2026` */
  fun dateLong(iso: String, lang: Lang, calendar: String, monthsPref: String, digits: String = LocaleFormat.DIGITS_LATN): String {
    val d = screenDigits(lang, digits)
    if (gregorian(lang, calendar)) {
      val g = civil(iso) ?: return iso
      val name = gregorianMonths(lang)[g.month - 1]
      return "${LocaleFormat.displayDigits(g.day.toString(), d)} $name ${LocaleFormat.displayDigits(g.year.toString(), d)}"
    }
    val j = SolarHijri.toSolar(iso.trim().take(10)) ?: return iso
    val name = solarMonths(lang, monthsPref)[j.month - 1]
    return "${LocaleFormat.displayDigits(j.day.toString(), d)} $name ${LocaleFormat.displayDigits(j.year.toString(), d)}"
  }
}

/**
 *  یک نوشته به سه زبان. `en` یا `ps` ِ `null` یعنی «هنوز ترجمه نشده» ⇒ فارسی.
 */
fun t(fa: String, en: String? = null, ps: String? = null): String {
  I18n.audit?.invoke(I18n.Entry(fa, en, ps))
  return I18n.pick(I18n.lang, fa, en, ps)
}

/**
 *  مفرد یا جمع، برای جای عدد در نوشتهٔ انگلیسی و پشتو: `"$n ${pl(n, "item", "items")}"`.
 *  عدد یا متنِ عددی (`"1,250"`) هر دو پذیرفته است؛ ناخوانا ⇒ جمع.
 */
fun pl(n: Any, one: String, other: String): String {
  val v = when (n) {
    is Number -> n.toDouble()
    else -> n.toString().filter { it in '0'..'9' || it == '-' || it == '.' }.toDoubleOrNull() ?: 0.0
  }
  return if (v == 1.0 || v == -1.0) one else other
}

/**
 *  نوشته‌ای که در گوشی از «گوشی/دوربین» حرف می‌زند و در کامپیوتر نباید. فارسیِ
 *  کامپیوتر را جدولِ `phrases` ِ `desktop/build.gradle.kts` می‌سازد؛ انگلیسی و
 *  پشتوی کامپیوتر همین‌جا کنارش است.
 */
fun td(fa: String, en: String, ps: String, enDesk: String, psDesk: String): String {
  I18n.audit?.invoke(I18n.Entry(fa, en, ps, enDesk, psDesk))
  return if (I18n.desktop) I18n.pick(I18n.lang, fa, enDesk, psDesk) else I18n.pick(I18n.lang, fa, en, ps)
}
