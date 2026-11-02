package java.text

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 *  shimِ iOS ِ `java.text.SimpleDateFormat` — فقط `format`، با الگوهای عددیِ JDK:
 *  `yyyy yy MM M dd d HH H mm m ss s SSS` و متنِ `'…'` (و `''` برای خودِ «'»). حرفِ دیگر
 *  (`EEE`، `MMM`، `a`، `Z`…) ⇐ IllegalArgumentException، نه متنِ غلط. رقم همیشه لاتین (مثلِ Locale.US).
 */
class SimpleDateFormat(private val pattern: String, private val locale: Locale = Locale.getDefault()) {
  var timeZone: TimeZone = TimeZone.getDefault()

  fun format(date: Date): String {
    val c = Calendar.getInstance(timeZone).apply { timeInMillis = date.getTime() }
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
      val ch = pattern[i]
      if (ch == '\'') {
        val end = pattern.indexOf('\'', i + 1)
        if (end == i + 1) { out.append('\''); i += 2; continue }
        require(end > 0) { "SimpleDateFormat: «'» ِ بسته‌نشده" }
        out.appendRange(pattern, i + 1, end); i = end + 1; continue
      }
      if (!ch.isLetter()) { out.append(ch); i++; continue }
      var n = 1
      while (i + n < pattern.length && pattern[i + n] == ch) n++
      val v = when (ch) {
        'y' -> c.get(Calendar.YEAR).let { if (n == 2) it % 100 else it }
        'M' -> if (n <= 2) c.get(Calendar.MONTH) + 1 else throw IllegalArgumentException("iOS SimpleDateFormat: نامِ ماه ($pattern)")
        'd' -> c.get(Calendar.DAY_OF_MONTH)
        'H' -> c.get(Calendar.HOUR_OF_DAY)
        'm' -> c.get(Calendar.MINUTE)
        's' -> c.get(Calendar.SECOND)
        'S' -> c.get(Calendar.MILLISECOND)
        else -> throw IllegalArgumentException("iOS SimpleDateFormat: حرفِ «$ch» در $pattern")
      }
      out.append(v.toString().padStart(n, '0'))
      i += n
    }
    return out.toString()
  }
}
