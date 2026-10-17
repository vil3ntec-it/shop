package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.text.LocaleFormat

/**
 *  سیم‌کشیِ `core/text` و `core/calendar` به صفحه و فاکتور (LOC-01، ‎-05، ‎-07، ‎-09).
 *
 *  همه‌اش تابعِ خالص است (بی Context، بی Compose) تا آزمونِ واحد مستقیم
 *  بسنجدش. انتخابِ دکان در `LocaleChoice` نگه داشته می‌شود.
 *
 *  ⛔ دادهٔ ذخیره‌شده همیشه ASCII است. رقمِ فارسیِ **صفحه** از ویژگیِ `ss01`ِ
 *  فونتِ وزیرمتن می‌آید (همان رقمِ لاتین، با شکلِ ۱۲۳)، نه از عوض‌کردنِ متن —
 *  `money()` و `qty()` و `formatDate()` در یادداشتِ سابقه، CSV و کادرهای
 *  ویرایش هم به کار می‌روند و نباید رقمِ فارسی در آن‌ها بنشیند. فقط فاکتورِ
 *  چاپی (تصویری که خودمان می‌کشیم) متنِ رقم‌فارسی می‌سازد.
 */
object LocaleText {

  const val MONTHS_DARI = "fa"
  const val MONTHS_PASHTO = "ps"

  /** هر مقدارِ ناشناخته ⇒ رقمِ لاتین، یعنی رفتارِ امروز */
  fun digitsOf(value: String?): String =
    if (value == LocaleFormat.DIGITS_FA) LocaleFormat.DIGITS_FA else LocaleFormat.DIGITS_LATN

  /** هر مقدارِ ناشناخته ⇒ دری، یعنی رفتارِ امروز */
  fun monthsOf(value: String?): String = if (value == MONTHS_PASHTO) MONTHS_PASHTO else MONTHS_DARI

  /** نامِ دوازده ماهِ خورشیدی به زبانِ انتخاب‌شده */
  fun monthNames(lang: String): List<String> =
    if (lang == MONTHS_PASHTO) SolarHijri.MONTHS_PS else SolarHijri.MONTHS_FA

  /**
   *  ویژگیِ فونت برای رقمِ صفحه. `ss01`ِ وزیرمتن فقط ۰-۹ و ٠-٩ را به ۰-۹
   *  می‌کشد (با fontTools سنجیده شد؛ هیچ نویسهٔ دیگری در آن نیست). لاتین ⇒ null،
   *  یعنی همان سبکِ امروز بی هیچ تغییری.
   */
  fun fontFeatures(digits: String): String? = if (digits == LocaleFormat.DIGITS_FA) "ss01" else null

  /* ------------------------------ فاکتورِ چاپی ------------------------------ */

  /** عدد یا مبلغ بی نماد — در حالتِ لاتین دقیقاً همان `money()` */
  fun amount(value: Double, digits: String): String =
    LocaleFormat.formatAfghani(value, LocaleFormat.SYMBOL_NONE, digits)

  /** مبلغ با «افغانی» — در حالتِ لاتین دقیقاً همان `"${money(x)} افغانی"` ِ امروز */
  fun afghani(value: Double, digits: String): String =
    LocaleFormat.formatAfghani(value, LocaleFormat.SYMBOL_WORD, digits)

  /** متنی که رقم دارد (شمارهٔ فاکتور، ساعت) */
  fun digitsIn(text: String, digits: String): String = LocaleFormat.displayDigits(text, digits)

  /** تاریخِ خورشیدیِ عددی: `1405/07/22` یا `۱۴۰۵/۰۷/۲۲` */
  fun date(iso: String, digits: String): String = LocaleFormat.displayDigits(formatDate(iso), digits)

  /* --------------------------------- تلفن --------------------------------- */

  /**
   *  تلفن به شکلِ ذخیره (LOC-09): شمارهٔ درست ⇒ ‎+93 و نُه رقم (یا E.164ِ کشورِ
   *  دیگر)، پیش‌شماره به اپراتور نگاشت نمی‌شود. چیزی که شماره نیست — کوتاه،
   *  ناقص، با حرف — **گم نمی‌شود**: همان‌طور که نوشته شده (با رقمِ ASCII) می‌ماند.
   */
  fun phoneForSave(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    return LocaleFormat.normalizePhone(t) ?: LocaleFormat.asciiDigits(t)
  }

  /**
   *  نمایشِ تلفن: ‎+93 7X XXX XXXX. نشانهٔ LRM اول می‌آید، وگرنه در صفحهٔ
   *  راست‌به‌چپ «+» به تهِ شماره می‌پرد (‎93…+). مقدارِ قدیمی (07…) همان‌طور
   *  که هست نشان داده می‌شود.
   */
  fun phoneDisplay(phone: String): String =
    if (phone.startsWith("+")) "‎" + LocaleFormat.formatPhone(phone) else phone

  /**
   *  جست‌وجوی تلفن. شرطِ اول همان `phone.contains(query)` ِ امروز است، پس
   *  هر چه امروز پیدا می‌شود باز پیدا می‌شود؛ بعد، شمارهٔ ذخیره‌شده با ‎+93
   *  با «07…» ِ عادتی و رقمِ فارسی هم پیدا می‌شود.
   */
  fun phoneMatches(phone: String, query: String): Boolean {
    if (phone.contains(query)) return true
    val q = LocaleFormat.asciiDigits(query).filter { it in '0'..'9' }
    if (q.isEmpty()) return false
    val p = LocaleFormat.asciiDigits(phone).filter { it in '0'..'9' }
    if (p.contains(q)) return true
    return phone.startsWith("+93") && ("0" + p.removePrefix("93")).contains(q)
  }
}
