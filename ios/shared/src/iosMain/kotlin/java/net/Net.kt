package java.net

import java.io.IOException

/*
 *  shimِ iOS ِ چند نامِ `java.net` که کدِ مشترک صدا می‌زند: رده‌های خطای شبکه (برای `ApiFailure`)
 *  و رمزگذاریِ فرم (`URLEncoder`/`URLDecoder`) — رفتارِ هر دو **همان JDK** است:
 *  `application/x-www-form-urlencoded` روی بایت‌های UTF-8؛ فاصله ⇐ `+`؛ فقط `a-z A-Z 0-9 . - * _`
 *  دست‌نخورده؛ بقیه `%XX` با حرفِ بزرگ.
 */
open class SocketException(message: String? = null) : IOException(message)
open class SocketTimeoutException(message: String? = null) : IOException(message)
open class UnknownHostException(message: String? = null) : IOException(message)
open class ConnectException(message: String? = null) : SocketException(message)
open class NoRouteToHostException(message: String? = null) : SocketException(message)

object URLEncoder {
  private const val HEX = "0123456789ABCDEF"

  fun encode(s: String, enc: String): String {
    require(enc.equals("UTF-8", ignoreCase = true)) { "iOS: فقط UTF-8" }
    val out = StringBuilder(s.length + 16)
    for (b in s.encodeToByteArray()) {
      val c = (b.toInt() and 0xFF)
      val ch = c.toChar()
      when {
        c < 0x80 && (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '*' || ch == '_') -> out.append(ch)
        ch == ' ' -> out.append('+')
        else -> out.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
      }
    }
    return out.toString()
  }
}

object URLDecoder {
  /** همان JDK: `+` ⇐ فاصله؛ `%XX` ⇐ بایت؛ `%` ِ ناقص ⇐ IllegalArgumentException */
  fun decode(s: String, enc: String): String {
    require(enc.equals("UTF-8", ignoreCase = true)) { "iOS: فقط UTF-8" }
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
      val ch = s[i]
      when (ch) {
        '+' -> { out.append(' '); i++ }
        '%' -> {
          val bytes = ArrayList<Byte>()
          while (i < s.length && s[i] == '%') {
            if (i + 2 >= s.length) throw IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern")
            val v = s.substring(i + 1, i + 3).toIntOrNull(16)
              ?: throw IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern")
            bytes += v.toByte()
            i += 3
          }
          out.append(bytes.toByteArray().decodeToString())
        }
        else -> { out.append(ch); i++ }
      }
    }
    return out.toString()
  }
}
