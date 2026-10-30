package ir.vil3ntec.tohid.core.onboarding

/*
 *  دو کارِ متنیِ دروازهٔ ورود بی `java.*` (تا همین مدل‌ها در آیفون هم ساخته شوند — §۴۰.۳):
 *  رقمِ فارسی/عربی ⇐ لاتین (همان `latinDigits` ِ `Format.kt` ِ اندروید) و `%.5f` ِ `Locale.US`.
 */
internal object OnboardingText {

  /** ۰-۹ و ٠-٩ ⇐ 0-9، ممیز و جداکنندهٔ عربی ⇐ نقطه؛ بقیه همان — یک نویسه به یک نویسه */
  fun latinDigits(raw: String): String = buildString(raw.length) {
    for (c in raw) append(
      when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        '٫', '٬' -> '.'
        else -> c
      },
    )
  }

  /**
   *  همان `String.format(Locale.US, "%.5f", x)`: پنج رقمِ اعشار، گردِ نیمه به بالا روی رقم‌های دهدهیِ
   *  `toString` ِ عدد (نه مقدارِ دودوییِ دقیق)، منفیِ نزدیکِ صفر «-0.00000». برابری با `String.format`
   *  را `AuthModelTest` روی عددهای تصادفی و مرزی می‌سنجد.
   */
  fun fixed5(x: Double): String {
    if (x.isNaN() || x.isInfinite()) return if (x.isNaN()) "NaN" else if (x > 0) "Infinity" else "-Infinity"
    val neg = x < 0.0 || (x == 0.0 && 1.0 / x < 0.0)
    val s = kotlin.math.abs(x).toString()
    val e = s.indexOfFirst { it == 'E' || it == 'e' }
    val mant = if (e < 0) s else s.substring(0, e)
    val exp = if (e < 0) 0 else s.substring(e + 1).toInt()
    val dot = mant.indexOf('.')
    var digits = if (dot < 0) mant else mant.removeRange(dot, dot + 1)
    var point = (if (dot < 0) mant.length else dot) + exp
    if (point <= 0) {
      digits = "0".repeat(1 - point) + digits
      point = 1
    }
    if (digits.length < point + FRACTION + 1) digits += "0".repeat(point + FRACTION + 1 - digits.length)
    val kept = digits.substring(0, point + FRACTION).toCharArray()
    var overflow = false
    if (digits[point + FRACTION] >= '5') {
      var i = kept.size - 1
      while (i >= 0 && kept[i] == '9') kept[i--] = '0'
      if (i >= 0) kept[i] = kept[i] + 1 else overflow = true
    }
    val whole = ((if (overflow) "1" else "") + kept.concatToString(0, point)).trimStart('0').ifEmpty { "0" }
    return (if (neg) "-" else "") + whole + "." + kept.concatToString(point, kept.size)
  }

  private const val FRACTION = 5
}
