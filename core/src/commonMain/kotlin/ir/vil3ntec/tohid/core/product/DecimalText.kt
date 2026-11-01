package ir.vil3ntec.tohid.core.product

/*
 *  عدد از متنِ کاربر یا فایل، دقیق و بی Double — پول به یک‌صدم (scale ۲) و مقدار به هزارم (scale ۳)،
 *  §۳.۴. رقمِ فارسی/عربی، «٬» و «,» ِ هزارگان، «٫» ِ اعشار، «؋» و نمادِ علمیِ اکسل («1.2E+3») خوانده می‌شوند.
 *  گرد به نزدیک‌ترین، نیمه به سمتِ بزرگ‌تر (قدرِ مطلق)؛ نادرست یا بیرون از ±(2^53−1) ⇒ null.
 *  منطقِ خالص؛ آزمون: `ProductImportPlanTest.moneyAndQuantityParsing`.
 */
object DecimalText {

  /** رقمِ فارسی/عربی ⇐ لاتین، «٫» ⇐ «.»، و هزارگان («٬» «,»)، فاصله و «؋» بیرون */
  fun clean(text: String): String = buildString {
    for (c in text.trim()) when (c) {
      in '۰'..'۹' -> append('0' + (c - '۰'))
      in '٠'..'٩' -> append('0' + (c - '٠'))
      '٫' -> append('.')
      '٬', ',', ' ', '\u00A0', '\u202F', '؋' -> {}
      else -> append(c)
    }
  }

  const val MAX_EXACT = 9_007_199_254_740_991L

  /** «۱٬۲۵۰٫۵» ⇐ ۱۲۵۰۵۰ (scale ۲) */
  fun scaled(text: String, scale: Int): Long? {
    val s = clean(text)
    if (s.isEmpty()) return null
    var i = 0
    val negative = s[0] == '-'
    if (s[0] == '-' || s[0] == '+') i++
    val digits = StringBuilder()
    var frac = 0
    var dot = false
    var any = false
    while (i < s.length && (s[i].isAsciiDigit() || s[i] == '.')) {
      if (s[i] == '.') { if (dot) return null; dot = true } else { digits.append(s[i]); any = true; if (dot) frac++ }
      i++
    }
    if (!any) return null
    var exp = 0
    if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
      i++
      var expNeg = false
      if (i < s.length && (s[i] == '+' || s[i] == '-')) { expNeg = s[i] == '-'; i++ }
      val start = i
      while (i < s.length && s[i].isAsciiDigit()) i++
      if (i == start || i - start > 4) return null
      exp = s.substring(start, i).toInt().let { if (expNeg) -it else it }
    }
    if (i != s.length) return null
    val d = digits.toString().trimStart('0')
    val shift = exp - frac + scale
    val magnitude: Long = if (shift >= 0) {
      if (d.isEmpty()) 0L
      else {
        if (d.length + shift > 16) return null
        (d + "0".repeat(shift)).toLong()
      }
    } else {
      val drop = -shift
      val keep = if (d.length > drop) d.substring(0, d.length - drop) else ""
      val next = if (d.length >= drop && d.length - drop < d.length) d[d.length - drop] else '0'
      if (keep.length > 16) return null
      (if (keep.isEmpty()) 0L else keep.toLong()) + if (next >= '5') 1 else 0
    }
    if (magnitude > MAX_EXACT) return null
    return if (negative) -magnitude else magnitude
  }

  private fun Char.isAsciiDigit() = this in '0'..'9'

  /** در متن بی ممیز و بی توان نوشته شده؟ («42» آری، «42.00» و «4.2E1» نه) */
  fun whole(text: String): Boolean = clean(text).none { it == '.' || it == 'e' || it == 'E' }
}
