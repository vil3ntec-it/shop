package ir.vil3ntec.tohid.core.net

import ir.vil3ntec.tohid.ios.jvm.Charsets
import java.io.InputStream
import java.net.HttpURLConnection

/**
 *  SYN-04 روی iOS — همتای `HttpBodyAndroid.kt` (که به `java.util.zip` تکیه دارد و در `ios-exclude.txt` است). shim ِ
 *  `HttpURLConnection` روی NSURLSession است و NSURLSession پاسخِ `Content-Encoding: gzip` را خودش باز می‌کند، پس این‌جا
 *  همان خواندنِ سادهٔ پیشین می‌ماند (باز کردنِ دوباره بدنهٔ بازشده را خراب می‌کرد).
 */
internal object HttpBody {
  @Suppress("UNUSED_PARAMETER")
  fun read(connection: HttpURLConnection, stream: InputStream?): String =
    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}
