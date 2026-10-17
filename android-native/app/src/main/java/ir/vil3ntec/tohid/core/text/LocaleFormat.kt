package ir.vil3ntec.tohid.core.text

/**
 *  ارقام (LOC-05)، افغانی (LOC-07) و تلفن (LOC-09).
 *
 *  همتای دقیقِ `license/text-core.js`؛ هر دو با
 *  `core/test-vectors/locale-format.json` سنجیده می‌شوند.
 *
 *  `latinDigits` ِ `Format.kt` (رفتارِ امروزِ کادرهای عدد) عمداً عوض
 *  نشده: «٬» را نقطه می‌کند، در حالی که این‌جا کاما می‌شود. فایلِ مرجع
 *  هر دو رفتار را نگه می‌دارد تا فرق دیده شود.
 */
object LocaleFormat {

  const val DIGITS_LATN = "latn"
  const val DIGITS_FA = "fa"
  const val SYMBOL_SIGN = "sign"
  const val SYMBOL_WORD = "word"
  const val SYMBOL_NONE = "none"

  const val AFN_SIGN = "؋"   // ؋
  const val AFN_WORD = "افغانی"

  /** ورودی به ASCII: ۰-۹ و ٠-٩ لاتین، «٫» نقطه، «٬» کاما. چیزِ دیگری دست نمی‌خورد. */
  fun asciiDigits(text: String): String {
    val out = StringBuilder(text.length)
    for (c in text) {
      out.append(
        when (c) {
          in '۰'..'۹' -> '0' + (c - '۰')
          in '٠'..'٩' -> '0' + (c - '٠')
          '٫' -> '.'
          '٬' -> ','
          else -> c
        },
      )
    }
    return out.toString()
  }

  /** برای نمایش: style = "fa" ⇒ ارقامِ فارسی (۰-۹)؛ هر چیزِ دیگر ⇒ لاتین */
  fun displayDigits(text: String, style: String): String {
    val s = asciiDigits(text)
    if (style != DIGITS_FA) return s
    val out = StringBuilder(s.length)
    for (c in s) out.append(if (c in '0'..'9') '۰' + (c - '0') else c)
    return out.toString()
  }

  /**
   *  مبلغ بی اعشار، با جداکنندهٔ هزارگان. گرد کردن همان `Math.round` است
   *  (نیمه رو به بالا) تا با `money()` و `formatMoney` ِ وب یکی بماند.
   *  symbol: "sign" ⇒ «؋»، "word" ⇒ «افغانی»، غیرِ آن ⇒ بی نماد.
   *  digits: "fa" ⇒ ۱۲۳٬۴۵۶؛ غیرِ آن ⇒ 123,456 (نه جداکنندهٔ عجیبِ CLDR ِ پشتو).
   */
  fun formatAfghani(amount: Double, symbol: String, digits: String): String {
    val n = if (amount.isNaN() || amount.isInfinite()) 0L else Math.round(amount)
    val raw = kotlin.math.abs(n).toString()
    val sb = StringBuilder()
    if (n < 0) sb.append('-')
    raw.forEachIndexed { i, c ->
      if (i > 0 && (raw.length - i) % 3 == 0) sb.append(',')
      sb.append(c)
    }
    var out = sb.toString()
    if (digits == DIGITS_FA) out = displayDigits(out, DIGITS_FA).replace(',', '٬')
    return when (symbol) {
      SYMBOL_SIGN -> "$out $AFN_SIGN"
      SYMBOL_WORD -> "$out $AFN_WORD"
      else -> out
    }
  }

  private val NSN = Regex("^[1-9][0-9]{8}$")
  private val E164_REST = Regex("^[1-9][0-9]{7,14}$")
  private val ALL_DIGITS = Regex("^[0-9]+$")
  private val AF_E164 = Regex("^\\+93[1-9][0-9]{8}$")

  /**
   *  شماره به قالبِ ذخیره: ‎+93 و ۹ رقمِ ملی، یا E.164ِ کشورِ دیگر.
   *  07xxxxxxxx، 7xxxxxxxx، 937xxxxxxxx، 00937…، ‎+93 07… همه پذیرفته‌اند.
   *  پیش‌شماره به اپراتور نگاشت نمی‌شود (نامِ برندها عوض شده). نامعتبر ⇒ null.
   */
  fun normalizePhone(text: String): String? {
    var clean = asciiDigits(text).filterNot {
      TextNormalizer.isSpace(it) || TextNormalizer.isInvisible(it) || it == '-' || it == '(' || it == ')' || it == '.'
    }
    if (clean.startsWith("00")) clean = "+" + clean.substring(2)
    val intl = clean.startsWith("+")
    val d = if (intl) clean.substring(1) else clean
    if (!ALL_DIGITS.matches(d)) return null
    var nsn: String? = null
    if (intl) {
      if (!d.startsWith("93")) return if (E164_REST.matches(d)) "+$d" else null
      nsn = d.substring(2).let { if (it.startsWith("0")) it.substring(1) else it }
    } else if (d.startsWith("0")) {
      nsn = d.substring(1)
    } else if (d.length == 9) {
      nsn = d
    } else if (d.length == 11 && d.startsWith("93")) {
      nsn = d.substring(2)
    }
    return if (nsn != null && NSN.matches(nsn)) "+93$nsn" else null
  }

  /** نمایش: ‎+93 7X XXX XXXX. شماره‌ای که این شکل را ندارد همان‌طور برمی‌گردد. */
  fun formatPhone(e164: String): String {
    if (!AF_E164.matches(e164)) return e164
    val n = e164.substring(3)
    return "+93 ${n.substring(0, 2)} ${n.substring(2, 5)} ${n.substring(5)}"
  }
}
