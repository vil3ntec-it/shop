package ir.vil3ntec.tohid.core.text

/**
 *  رفتارِ رشته‌ایِ JS برای هم‌زادهای Kotlin ِ هسته‌های JS (گزارش، موجودی، دفترِ قرض) — تا هر دو سو یک عدد بدهند.
 *
 *  فازِ تفاضلی (`server/scripts/core-fuzz.js`) نشان داد:
 *  • `trim()`/`isBlank()` ِ Kotlin فاصله را با `Char.isWhitespace` می‌شناسد: U+001C..U+001F آری، U+FEFF (BOM) نه؛
 *    `String.prototype.trim` ِ JS درست برعکس. پس «U+FEFF احمد» در گزارشِ گوشی کاربرِ دیگری می‌شد و شناسهٔ «U+001F» سند را رد می‌کرد.
 *  • `toDoubleOrNull` «0x1F» را رد و «5d» را ۵ می‌خواند؛ `Number()` ِ JS درست برعکس (۳۱ و NaN).
 *  قاعدهٔ سمتِ سرور (JS) ملاک است؛ این‌جا فقط همان، بی وابستگیِ پلتفرم (commonMain).
 */
object JsText {

  /** WhiteSpace ∪ LineTerminator ِ ECMAScript (همان `\s` ِ JS) */
  fun isSpace(c: Char): Boolean =
    c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r' || c == ' ' || c == '\u00A0' || c == '\u1680' ||
      c in '\u2000'..'\u200A' || c == '\u2028' || c == '\u2029' || c == '\u202F' || c == '\u205F' || c == '\u3000' || c == '\uFEFF'

  /** `s.trim()` ِ JS */
  fun trim(s: String): String {
    var a = 0
    var b = s.length
    while (a < b && isSpace(s[a])) a++
    while (b > a && isSpace(s[b - 1])) b--
    return s.substring(a, b)
  }

  /** `s.trim() === ''` ِ JS */
  fun isBlank(s: String?): Boolean = s == null || s.all(::isSpace)

  /**
   *  `s.toLowerCase()` ِ JS. تنها قاعدهٔ وابسته به بافت (بی زبان) سیگمای پایانی است و `lowercase()` ِ JVM آن را جورِ دیگر
   *  می‌بیند: «ΑΣعدد…g» ⇐ JS «ας…» (حرفِ عربی حرفِ حالت‌دار نیست) و JVM «ασ…»؛ «ΑΣ’a» برعکس (فازِ تفاضلی، دسته‌ی کالای جمع‌آوری).
   *  پس هر «Σ» با قاعدهٔ Unicode ِ Final_Sigma (همان V8/ICU) و میانِ آن‌ها همان `lowercase()`.
   */
  fun lowercase(s: String): String {
    if ('Σ' !in s) return s.lowercase()
    val out = StringBuilder(s.length)
    var from = 0
    for (i in s.indices) {
      if (s[i] != 'Σ') continue
      out.append(s.substring(from, i).lowercase()).append(if (finalSigma(s, i)) 'ς' else 'σ')
      from = i + 1
    }
    return out.append(s.substring(from).lowercase()).toString()
  }

  /** پیش از آن حرفِ حالت‌دار (با نویسه‌های بی‌حالتِ میانی) و پس از آن نه */
  private fun finalSigma(s: String, at: Int): Boolean {
    var i = at - 1
    while (i >= 0 && caseIgnorable(s[i])) i--
    if (i < 0 || !cased(s[i])) return false
    i = at + 1
    while (i < s.length && caseIgnorable(s[i])) i++
    return i >= s.length || !cased(s[i])
  }

  private fun cased(c: Char) = c.isUpperCase() || c.isLowerCase() || c.isTitleCase()

  /** Case_Ignorable ِ Unicode: Mn، Me، Cf، Lm، Sk و نشانه‌های MidLetter/MidNumLet/Single_Quote */
  private fun caseIgnorable(c: Char): Boolean = when (c.category) {
    CharCategory.NON_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.FORMAT, CharCategory.MODIFIER_LETTER, CharCategory.MODIFIER_SYMBOL -> true
    else -> c in "'.:\u00B7\u0387\u055F\u05F4\u2018\u2019\u2024\u2027\uFE13\uFE52\uFE55\uFF07\uFF0E\uFF1A"
  }

  private val DECIMAL = Regex("^[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?$")

  /**
   *  `Number(s)` ِ JS برای رشته (StringToNumber): دو سو بریده؛ خالی ⇒ ۰؛ `0x`/`0o`/`0b` بی علامت؛ `Infinity` با علامت؛
   *  دهدهیِ ساده (گردِ درستِ IEEE، مثلِ JS)؛ هر چیزِ دیگر (`5d`، `1_000`، `0x-1`) ⇒ NaN.
   */
  fun toNumber(s: String): Double {
    val t = trim(s)
    if (t.isEmpty()) return 0.0
    if (t.length > 2 && t[0] == '0') {
      val radix = when (t[1]) { 'x', 'X' -> 16; 'o', 'O' -> 8; 'b', 'B' -> 2; else -> 0 }
      if (radix != 0) {
        var v = 0.0
        for (i in 2 until t.length) {
          //  فقط رقمِ لاتین (`digitToIntOrNull` رقمِ فارسی را هم می‌پذیرد، JS نه)
          val d = when (val c = t[i]) { in '0'..'9' -> c - '0'; in 'a'..'f' -> c - 'a' + 10; in 'A'..'F' -> c - 'A' + 10; else -> 99 }
          if (d >= radix) return Double.NaN
          v = v * radix + d   // دقیق تا ۲^۵۳ — بیرون از بازهٔ امنِ هسته‌ها
        }
        return v
      }
    }
    return when (t) {
      "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY
      "-Infinity" -> Double.NEGATIVE_INFINITY
      else -> if (DECIMAL.matches(t)) t.toDouble() else Double.NaN
    }
  }
}
