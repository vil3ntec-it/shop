package ir.vil3ntec.tohid.core.money

/**
 *  برگردانِ بیت‌به‌بیتِ `Math.round(double)` ِ JDK (نسخهٔ ۸ به بعد) برای همهٔ سکوها:
 *  ⌊a + ½⌋ ِ ریاضیِ دقیق (نیمه به سمتِ +∞)؛ NaN ⇒ ۰؛ بیرون از Long ⇒ اشباع.
 *
 *  چرا نه `roundToLong`: روی Kotlin/Native همان `floor(a + 0.5)` ِ اعشاری است و
 *  در لبه‌ها (مثلاً 0.49999999999999994 ⇒ ۱ به جای ۰) با JVM فرق می‌کند.
 *  آزمونِ `MathRoundTest` (jvmTest) آن را با خودِ `Math.round` می‌سنجد.
 */
internal fun mathRound(a: Double): Long {
  val bits = a.toRawBits()
  val biasedExp = (bits and 0x7FF0000000000000L) shr 52
  val shift = (53 - 2 + 1023) - biasedExp
  if ((shift and -64L) == 0L) {
    //  a متناهی است و 2^-64 ≤ ulp(a) < 1
    var r = (bits and 0x000FFFFFFFFFFFFFL) or 0x0010000000000000L
    if (bits < 0) r = -r
    return ((r shr shift.toInt()) + 1) shr 1
  }
  //  |a| < ½، یا a خودش صحیح است، یا بی‌نهایت/NaN
  return a.toLong()
}
