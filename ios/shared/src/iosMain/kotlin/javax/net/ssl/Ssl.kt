package javax.net.ssl

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/*
 *  shimِ iOS ِ TLS ِ JDK — گامِ ۴، فقط آنچه سنجاقِ گواهیِ «سرورِ خودِ دکان» به کار می‌برد (`PinnedTls` و
 *  `ServerSwitchAndroid.probe` در `platform/ServerSwitchAndroid.kt`، همان فایلِ اندروید):
 *    `SSLContext.init(null, [trustManager], null).socketFactory` ⇐ کارخانه‌ای که فقط «مدیرِ اعتماد» را نگه می‌دارد؛
 *    `HttpsURLConnection.sslSocketFactory`/`hostnameVerifier` ⇐ درخواستِ همان اتصال روی نشستِ NSURLSession ِ
 *    سنجاق‌دار (‎IosTls.kt‎): گواهی‌های دست‌دادن ⇐ `checkServerTrusted` ِ همان مدیرِ اعتماد، بعد همان `verify`.
 *  هر دو باید بپذیرند؛ وگرنه دست‌دادن بسته و `SSLHandshakeException` (همان ردهٔ JDK که `probe` می‌گیرد).
 *  بی کارخانهٔ سفارشی ⇐ همان اعتمادِ پیش‌فرضِ iOS (مثلِ JDK).
 */

interface TrustManager

interface KeyManager

interface X509TrustManager : TrustManager {
  fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?)
  fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?)
  fun getAcceptedIssuers(): Array<X509Certificate>
}

interface SSLSession {
  val peerHost: String?
  val peerCertificates: Array<Certificate>
}

fun interface HostnameVerifier {
  fun verify(hostname: String, session: SSLSession): Boolean
}

/** فقط حاملِ مدیرهای اعتماد؛ سوکتِ خام ندارد (همهٔ TLS ِ آیفون در NSURLSession است) */
class SSLSocketFactory internal constructor(internal val trustManagers: List<TrustManager>)

class SSLContext private constructor(val protocol: String) {
  private var trust: List<TrustManager> = emptyList()

  fun init(km: Array<out KeyManager>?, tm: Array<out TrustManager>?, random: SecureRandom?) {
    trust = tm?.toList().orEmpty()
  }

  val socketFactory: SSLSocketFactory get() = SSLSocketFactory(trust)

  companion object {
    fun getInstance(protocol: String): SSLContext = SSLContext(protocol)
  }
}

open class HttpsURLConnection internal constructor(url: URL) : HttpURLConnection(url) {
  /** null ⇐ اعتمادِ پیش‌فرضِ iOS */
  var sslSocketFactory: SSLSocketFactory? = null
  var hostnameVerifier: HostnameVerifier? = null
}

open class SSLHandshakeException(message: String? = null) : SSLException(message)

open class SSLPeerUnverifiedException(message: String? = null) : SSLException(message)
