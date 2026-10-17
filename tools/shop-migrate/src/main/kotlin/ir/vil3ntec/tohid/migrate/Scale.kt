package ir.vil3ntec.tohid.migrate

import java.math.BigDecimal
import java.math.RoundingMode

/**
 *  تبدیلِ عددِ Doubleِ دفترِ قدیم به عددِ صحیحِ مدلِ تازه.
 *
 *  پول به یک‌صدم (`Minor`)، مقدار به هزارم (`Milli`). گردکردن «نیمه به سمتِ
 *  بی‌نهایتِ مثبت» است — همان `Math.round`ی که بخشِ ۳.۴ برای کلِ برنامه
 *  قاعده کرده — ولی روی **نمایشِ دهدهیِ** عدد (`Double.toString`)، نه روی
 *  `x * 100`ی که خطای دودویی دارد (۰٫۲۸۵ × ۱۰۰ = ۲۸٫۴۹۹۹…).
 *
 *  عددِ ناعدد (NaN/بی‌نهایت) صفر می‌شود؛ نگاشت آن را جدا گزارش می‌کند.
 */
object Scale {

  const val MONEY = 100L
  const val QTY = 1000L
  /** حاصل‌ضربِ مقدار در بها: هزارم × یک‌صدم */
  const val COST = MONEY * QTY

  fun toMinor(x: Double): Long = scaled(x, 2)
  fun toMilli(x: Double): Long = scaled(x, 3)

  /** همان عدد، گردشده به دقتِ مدلِ تازه، ولی هنوز Double — برای «محاسبهٔ قدیم روی ورودیِ گردشده» */
  fun r2(x: Double): Double = toMinor(x) / 100.0
  fun r3(x: Double): Double = toMilli(x) / 1000.0

  /** آیا عدد بیش از `digits` رقمِ اعشار دارد؟ */
  fun hasExtraDecimals(x: Double, digits: Int): Boolean {
    if (!x.isFinite()) return true
    return BigDecimal(x.toString()).stripTrailingZeros().scale() > digits
  }

  fun scaled(x: Double, digits: Int): Long {
    if (!x.isFinite()) return 0
    return BigDecimal(x.toString())
      .movePointRight(digits)
      .add(BigDecimal("0.5"))
      .setScale(0, RoundingMode.FLOOR)
      .longValueExact()
  }

  /** عددِ Double (محاسبهٔ قدیم) در مقیاسِ `scale`، گردشده به نزدیک‌ترین عددِ صحیح */
  fun ofDouble(x: Double, scale: Long): Long = scaled(x, digitsOf(scale))

  private fun digitsOf(scale: Long): Int {
    require(scale > 0 && scale.toString().trimStart('1').all { it == '0' }) { "scale must be a power of ten: $scale" }
    return scale.toString().length - 1
  }

  fun format(value: Long, scale: Long): String {
    if (scale == 1L) return value.toString()
    val digits = scale.toString().length - 1
    return BigDecimal.valueOf(value, digits).toPlainString()
  }
}
