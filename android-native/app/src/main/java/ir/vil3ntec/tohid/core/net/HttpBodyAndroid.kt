package ir.vil3ntec.tohid.core.net

import java.io.InputStream
import java.net.HttpURLConnection
import java.util.zip.GZIPInputStream

/**
 *  مرزِ پلتفرمِ `HttpEngine` برای خواندنِ بدنهٔ پاسخ (SYN-04): `Content-Encoding: gzip` (پاسخِ همان `Accept-Encoding` ِ
 *  `HttpEngine.attempt`) ⇒ باز شده؛ بدنهٔ خالی یا نبودِ جریان ⇒ "". همان روی اندروید (OkHttp با سرآیندِ صریح خودش باز
 *  نمی‌کند) و کامپیوتر (JDK هرگز خودش باز نمی‌کند). iOS: `ios/shared/…/core/net/HttpBodyIos.kt` (NSURLSession خودش باز می‌کند).
 */
internal object HttpBody {
  fun read(connection: HttpURLConnection, stream: InputStream?): String {
    val bytes = stream?.use { it.readBytes() } ?: return ""
    val gzip = connection.getHeaderField("Content-Encoding")?.contains("gzip", ignoreCase = true) == true
    return (if (gzip && bytes.isNotEmpty()) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes).toString(Charsets.UTF_8)
  }
}
