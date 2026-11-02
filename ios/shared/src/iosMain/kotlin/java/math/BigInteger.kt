package java.math

/*
 *  shimِ iOS ِ `java.math.BigInteger` — فقط آنچه `core/stock/StockCore.mulDivRound` در مسیرِ سرریز
 *  می‌خواهد (ضرب و جمع و تقسیمِ صحیح روی بیش از ۶۴ بیت). دقیق، نه تقریبی: اندازه با تکه‌های ۳۲
 *  بیتی، تقسیم بیت‌به‌بیت. قاعده‌های JDK: تقسیم رو به صفر، باقی‌مانده هم‌علامتِ مقسوم،
 *  `bitLength` ِ عددِ منفی = طولِ |x|−1، `toLong` = ۶۴ بیتِ پایینِ مکملِ دو.
 */
class BigInteger private constructor(private val sign: Int, private val mag: IntArray) : Comparable<BigInteger> {

  fun signum(): Int = sign

  fun add(o: BigInteger): BigInteger = when {
    o.sign == 0 -> this
    sign == 0 -> o
    sign == o.sign -> of(sign, addMag(mag, o.mag))
    else -> {
      val c = cmpMag(mag, o.mag)
      when {
        c == 0 -> ZERO
        c > 0 -> of(sign, subMag(mag, o.mag))
        else -> of(o.sign, subMag(o.mag, mag))
      }
    }
  }

  fun negate(): BigInteger = if (sign == 0) this else BigInteger(-sign, mag)
  fun subtract(o: BigInteger): BigInteger = add(o.negate())

  fun multiply(o: BigInteger): BigInteger {
    if (sign == 0 || o.sign == 0) return ZERO
    val r = LongArray(mag.size + o.mag.size)
    for (i in mag.indices) {
      var carry = 0uL
      val a = mag[i].toUInt().toULong()
      for (j in o.mag.indices) {
        val t = a * o.mag[j].toUInt().toULong() + r[i + j].toULong() + carry
        r[i + j] = (t and 0xFFFFFFFFuL).toLong()
        carry = t shr 32
      }
      r[i + o.mag.size] = carry.toLong()
    }
    return of(sign * o.sign, IntArray(r.size) { r[it].toInt() })
  }

  fun shiftLeft(n: Int): BigInteger {
    require(n >= 0) { "iOS BigInteger: shiftLeft منفی" }
    if (sign == 0 || n == 0) return this
    var r = this
    repeat(n) { r = of(r.sign, addMag(r.mag, r.mag)) }
    return r
  }

  /** [خارج‌قسمت، باقی‌مانده]، مثلِ JDK */
  fun divideAndRemainder(d: BigInteger): Array<BigInteger> {
    if (d.sign == 0) throw ArithmeticException("BigInteger divide by zero")
    if (sign == 0) return arrayOf(ZERO, ZERO)
    val bits = bitLen(mag)
    val q = IntArray(mag.size)
    var r = IntArray(0)
    for (i in bits - 1 downTo 0) {
      r = addMag(r, r)
      if ((mag[i / 32] ushr (i % 32)) and 1 == 1) r = addMag(r, intArrayOf(1))
      if (cmpMag(r, d.mag) >= 0) {
        r = subMag(r, d.mag)
        q[i / 32] = q[i / 32] or (1 shl (i % 32))
      }
    }
    return arrayOf(of(sign * d.sign, q), of(sign, r))
  }

  fun divide(d: BigInteger): BigInteger = divideAndRemainder(d)[0]

  fun bitLength(): Int = if (sign >= 0) bitLen(mag) else bitLen(subMag(mag, intArrayOf(1)))

  fun toLong(): Long {
    val lo = (if (mag.isNotEmpty()) mag[0].toUInt().toLong() else 0L) or
      ((if (mag.size > 1) mag[1].toUInt().toLong() else 0L) shl 32)
    return if (sign < 0) -lo else lo
  }

  fun longValueExact(): Long {
    if (bitLength() > 63) throw ArithmeticException("BigInteger out of long range")
    return toLong()
  }

  override fun compareTo(other: BigInteger): Int = when {
    sign != other.sign -> sign.compareTo(other.sign)
    else -> sign * cmpMag(mag, other.mag)
  }

  override fun equals(other: Any?): Boolean = other is BigInteger && compareTo(other) == 0
  override fun hashCode(): Int = mag.contentHashCode() * 31 + sign

  override fun toString(): String {
    if (sign == 0) return "0"
    val digits = StringBuilder()
    var cur = BigInteger(1, mag)
    val ten = valueOf(10)
    while (cur.sign != 0) {
      val (q, r) = cur.divideAndRemainder(ten)
      digits.append(r.toLong().toInt())
      cur = q
    }
    if (sign < 0) digits.append('-')
    return digits.reverse().toString()
  }

  companion object {
    val ZERO = BigInteger(0, IntArray(0))
    val ONE = valueOf(1)
    val TWO = valueOf(2)
    val TEN = valueOf(10)

    fun valueOf(v: Long): BigInteger {
      if (v == 0L) return ZERO
      val m = if (v == Long.MIN_VALUE) 1uL shl 63 else kotlin.math.abs(v).toULong()
      return of(if (v < 0) -1 else 1, intArrayOf(m.toInt(), (m shr 32).toInt()))
    }

    private fun of(sign: Int, mag: IntArray): BigInteger {
      var n = mag.size
      while (n > 0 && mag[n - 1] == 0) n--
      return if (n == 0) ZERO else BigInteger(sign, if (n == mag.size) mag else mag.copyOf(n))
    }

    private fun bitLen(m: IntArray): Int {
      var n = m.size
      while (n > 0 && m[n - 1] == 0) n--
      return if (n == 0) 0 else (n - 1) * 32 + (32 - m[n - 1].countLeadingZeroBits())
    }

    private fun cmpMag(a: IntArray, b: IntArray): Int {
      val la = bitLen(a)
      val lb = bitLen(b)
      if (la != lb) return la.compareTo(lb)
      for (i in (la + 31) / 32 - 1 downTo 0) {
        val x = a[i].toUInt()
        val y = b[i].toUInt()
        if (x != y) return x.compareTo(y)
      }
      return 0
    }

    private fun addMag(a: IntArray, b: IntArray): IntArray {
      val r = IntArray(maxOf(a.size, b.size) + 1)
      var carry = 0uL
      for (i in 0 until r.size - 1) {
        val t = (a.getOrElse(i) { 0 }.toUInt().toULong()) + (b.getOrElse(i) { 0 }.toUInt().toULong()) + carry
        r[i] = t.toInt()
        carry = t shr 32
      }
      r[r.size - 1] = carry.toInt()
      return r
    }

    /** a − b با a ≥ b */
    private fun subMag(a: IntArray, b: IntArray): IntArray {
      val r = IntArray(a.size)
      var borrow = 0L
      for (i in a.indices) {
        var t = a[i].toUInt().toLong() - b.getOrElse(i) { 0 }.toUInt().toLong() - borrow
        borrow = if (t < 0) { t += 1L shl 32; 1L } else 0L
        r[i] = t.toInt()
      }
      return r
    }
  }
}
