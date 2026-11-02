package java.math

/*
 *  shimِ iOS ِ کمترینِ `BigDecimal` — کدِ مشترک فقط «عدد ⇐ متنِ دهدهیِ ساده بی صفرِ پایانی» می‌خواهد
 *  (`x.toBigDecimal().stripTrailingZeros().toPlainString()`). عدد همان رقم‌های دهدهیِ ورودی است
 *  (`Double.toBigDecimal()` ِ Kotlin هم از `toString()` می‌سازد)، بی هیچ گرد کردن یا حسابی.
 */
class BigDecimal(value: String) : Comparable<BigDecimal> {
  private val negative: Boolean
  /** رقم‌ها بی صفرِ آغازین، و جای نقطه نسبت به آغازشان */
  private val digits: String
  private val point: Int

  init {
    val s = value.trim()
    val m = Regex("^([+-]?)(\\d*)(?:\\.(\\d*))?(?:[eE]([+-]?\\d+))?$").matchEntire(s)
      ?: throw NumberFormatException("Character array is missing \"exponent\" mark 'e' or 'E'.")
    val int = m.groupValues[2]
    val frac = m.groupValues[3]
    if (int.isEmpty() && frac.isEmpty()) throw NumberFormatException("No digits found.")
    val exp = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt() ?: 0
    val all = int + frac
    val lead = all.length - all.trimStart('0').length
    val d = all.trimStart('0')
    digits = d.ifEmpty { "0" }
    point = if (d.isEmpty()) 1 else int.length + exp - lead
    negative = m.groupValues[1] == "-" && d.isNotEmpty()
  }

  private constructor(neg: Boolean, digits: String, point: Int) : this((if (neg) "-" else "") + "0." + digits + "E" + point)

  fun stripTrailingZeros(): BigDecimal {
    val t = digits.trimEnd('0')
    return if (t.isEmpty()) BigDecimal("0") else BigDecimal(negative, t, point)
  }

  fun toPlainString(): String {
    if (digits == "0") return "0"
    val body = when {
      point <= 0 -> "0." + "0".repeat(-point) + digits
      point >= digits.length -> digits + "0".repeat(point - digits.length)
      else -> digits.substring(0, point) + "." + digits.substring(point)
    }
    return if (negative) "-$body" else body
  }

  fun signum(): Int = if (digits == "0") 0 else if (negative) -1 else 1

  override fun toString(): String = toPlainString()

  override fun compareTo(other: BigDecimal): Int = toPlainString().toDouble().compareTo(other.toPlainString().toDouble())

  override fun equals(other: Any?): Boolean = other is BigDecimal && other.toPlainString() == toPlainString()
  override fun hashCode(): Int = toPlainString().hashCode()
}
