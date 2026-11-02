package ir.vil3ntec.tohid.ios.jvm

import java.util.Locale

/*
 *  `String.format` و `"…".format(…)` ِ JVM برای iOS — فقط زیرمجموعه‌ای که کدِ مشترک به کار
 *  می‌برد: `%d %s %f %x %X %c %%` با پرچم‌های `- 0 , +` و پهنا و دقت (`%02d`، `%04d`، `%02x`،
 *  `%.2f`، `%.0f`، `%f`…). نشانهٔ ناشناخته ⇐ IllegalArgumentException (مثلِ JDK، نه متنِ غلط).
 *
 *  ⛔ رفتار همان `Locale.ROOT` ِ JDK است: رقمِ لاتین، نقطهٔ اعشار، جداکنندهٔ «,».
 *  `%f` مثلِ JDK روی رقم‌های کوتاه‌ترین نمایشِ دهدهی (`Double.toString`) گرد می‌کند، نیمه رو به بالا
 *  (`"%.2f".format(0.125)` ⇐ `0.13`، `"%.1f".format(0.15)` ⇐ `0.2`).
 *  تفاوتِ دانسته: در اندروید `format` ِ بی Locale رقمِ زبانِ دستگاه را می‌نویسد (فارسی ⇐ ۰۱۲)؛
 *  این‌جا همیشه لاتین — کدِ برنامه برای عددِ نمایشی خودش رقم را فارسی می‌کند.
 */

fun String.Companion.format(format: String, vararg args: Any?): String = formatJvm(format, args)

fun String.Companion.format(locale: Locale?, format: String, vararg args: Any?): String = formatJvm(format, args)

fun String.format(vararg args: Any?): String = formatJvm(this, args)

fun String.format(locale: Locale?, vararg args: Any?): String = formatJvm(this, args)

private val SPEC = Regex("%(\\d+\\$)?([-#+ 0,(]*)(\\d+)?(\\.\\d+)?([a-zA-Z%])")

internal fun formatJvm(format: String, args: Array<out Any?>): String {
  val out = StringBuilder()
  var last = 0
  var next = 0
  for (m in SPEC.findAll(format)) {
    out.appendRange(format, last, m.range.first)
    last = m.range.last + 1
    val flags = m.groupValues[2]
    val width = m.groupValues[3].toIntOrNull() ?: 0
    val precision = m.groupValues[4].takeIf { it.isNotEmpty() }?.drop(1)?.toInt()
    val conv = m.groupValues[5][0]
    if (conv == '%') { out.append(pad("%", width, flags)); continue }
    if (conv == 'n') { out.append('\n'); continue }
    val index = m.groupValues[1].takeIf { it.isNotEmpty() }?.dropLast(1)?.toInt()?.minus(1) ?: next++
    if (index >= args.size) throw IllegalArgumentException("Format specifier '${m.value}' بی آرگومان")
    val arg = args[index]
    val body = when (conv) {
      'd' -> integer(arg, flags, width)
      'x', 'X' -> hex(arg, conv == 'X')
      'f' -> fixed(arg, precision ?: 6, flags, width)
      's', 'S' -> (arg?.toString() ?: "null").let { s -> if (precision != null) s.take(precision) else s }
        .let { if (conv == 'S') it.uppercase() else it }
      'c' -> when (arg) { is Char -> arg.toString(); is Int -> arg.toChar().toString(); else -> throw IllegalArgumentException("%c: ${arg?.let { it::class.simpleName }}") }
      'b' -> (if (arg is Boolean) arg else arg != null).toString()
      else -> throw IllegalArgumentException("iOS: نشانهٔ قالبِ پشتیبانی‌نشده '${m.value}'")
    }
    out.append(if (conv == 'x' || conv == 'X') pad(body, width, flags) else if (conv == 'd' || conv == 'f') body else pad(body, width, flags))
  }
  out.appendRange(format, last, format.length)
  return out.toString()
}

private fun pad(s: String, width: Int, flags: String): String = when {
  s.length >= width -> s
  '-' in flags -> s.padEnd(width)
  '0' in flags -> {
    val neg = s.startsWith('-')
    if (neg) "-" + s.drop(1).padStart(width - 1, '0') else s.padStart(width, '0')
  }
  else -> s.padStart(width)
}

private fun group(digits: String): String {
  val sb = StringBuilder()
  digits.forEachIndexed { i, c -> if (i > 0 && (digits.length - i) % 3 == 0) sb.append(','); sb.append(c) }
  return sb.toString()
}

private fun signed(neg: Boolean, digits: String, flags: String, width: Int): String {
  val sign = if (neg) "-" else if ('+' in flags) "+" else if (' ' in flags) " " else ""
  val core = if (',' in flags) {
    val dot = digits.indexOf('.')
    if (dot < 0) group(digits) else group(digits.substring(0, dot)) + digits.substring(dot)
  } else digits
  return when {
    sign.length + core.length >= width -> sign + core
    '-' in flags -> (sign + core).padEnd(width)
    '0' in flags -> sign + core.padStart(width - sign.length, '0')
    else -> (sign + core).padStart(width)
  }
}

private fun integer(arg: Any?, flags: String, width: Int): String {
  val v: Long = when (arg) {
    is Int -> arg.toLong()
    is Long -> arg
    is Short -> arg.toLong()
    is Byte -> arg.toLong()
    else -> throw IllegalArgumentException("%d != ${arg?.let { it::class.simpleName }}")
  }
  val digits = if (v == Long.MIN_VALUE) "9223372036854775808" else kotlin.math.abs(v).toString()
  return signed(v < 0, digits, flags, width)
}

/** `%x` ِ JDK: منفی ⇐ مکملِ دو در پهنای همان نوع (Byte ⇐ ۲ رقم، Int ⇐ ۸، Long ⇐ ۱۶) */
private fun hex(arg: Any?, upper: Boolean): String {
  val s = when (arg) {
    is Byte -> (arg.toInt() and 0xFF).toString(16)
    is Short -> (arg.toInt() and 0xFFFF).toString(16)
    is Int -> arg.toUInt().toString(16)
    is Long -> arg.toULong().toString(16)
    else -> throw IllegalArgumentException("%x != ${arg?.let { it::class.simpleName }}")
  }
  return if (upper) s.uppercase() else s
}

private fun fixed(arg: Any?, precision: Int, flags: String, width: Int): String {
  val d: Double = when (arg) {
    is Double -> arg
    is Float -> arg.toString().toDouble()
    is Int -> arg.toDouble()
    is Long -> arg.toDouble()
    else -> throw IllegalArgumentException("%f != ${arg?.let { it::class.simpleName }}")
  }
  if (d.isNaN()) return pad("NaN", width, flags.replace("0", ""))
  if (d.isInfinite()) return pad(if (d > 0) (if ('+' in flags) "+Infinity" else "Infinity") else "-Infinity", width, flags.replace("0", ""))
  val neg = d < 0 || (d == 0.0 && 1.0 / d < 0)
  return signed(neg, roundHalfUp(kotlin.math.abs(d).toString(), precision), flags, width)
}

/**
 *  رقم‌های کوتاه‌ترین نمایش (مثلِ "1.0E-5" یا "123.456") را به `precision` رقمِ اعشار، نیمه رو به
 *  بالا، گرد می‌کند — همان `FormattedFloatingDecimal` ِ JDK.
 */
internal fun roundHalfUp(repr: String, precision: Int): String {
  val e = repr.indexOfFirst { it == 'E' || it == 'e' }
  val mant = if (e < 0) repr else repr.substring(0, e)
  val exp = if (e < 0) 0 else repr.substring(e + 1).toInt()
  val dot = mant.indexOf('.')
  val intPart = if (dot < 0) mant else mant.substring(0, dot)
  val frac = if (dot < 0) "" else mant.substring(dot + 1)
  var digits = (intPart + frac).trimStart('0')
  //  جایِ نقطه نسبت به آغازِ `digits`
  var point = intPart.length + exp - ((intPart + frac).length - (intPart + frac).trimStart('0').length)
  if (digits.isEmpty()) { digits = "0"; point = 1 }
  //  رقم‌های پیش از نقطه و پس از آن، با صفرِ لازم
  val whole: String
  val fraction: String
  if (point <= 0) {
    whole = "0"
    fraction = "0".repeat(-point) + digits
  } else if (point >= digits.length) {
    whole = digits + "0".repeat(point - digits.length)
    fraction = ""
  } else {
    whole = digits.substring(0, point)
    fraction = digits.substring(point)
  }
  if (fraction.length <= precision) {
    val f = fraction.padEnd(precision, '0')
    return if (precision == 0) whole else "$whole.$f"
  }
  val roundUp = fraction[precision] >= '5'
  var kept = (whole + fraction.substring(0, precision)).toCharArray()
  if (roundUp) {
    var i = kept.size - 1
    while (i >= 0) {
      if (kept[i] == '9') { kept[i] = '0'; i-- } else { kept[i] = kept[i] + 1; break }
    }
    if (i < 0) kept = charArrayOf('1') + kept
  }
  val s = kept.concatToString()
  val w = s.substring(0, s.length - precision)
  return if (precision == 0) w else w + "." + s.substring(s.length - precision)
}
