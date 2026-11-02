package javax.net.ssl

import ir.vil3ntec.tohid.ios.jvm.synchronized
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateRef
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.SecTrustRef
import platform.darwin.NSObject

/*
 *  پلِ سنجاقِ گواهیِ JDK ⇐ نمایندهٔ NSURLSession (‎Ssl.kt‎) — گامِ ۴.
 *
 *  دست‌دادنِ TLS در iOS پرسشِ «اعتماد به سرور» (`NSURLAuthenticationMethodServerTrust`) را به نماینده می‌دهد.
 *  اگر اتصال کارخانهٔ سفارشی دارد (کدِ مشترک: `PinnedTls.applyTo` یا `OkHttpClient.sslSocketFactory`)، پاسخ
 *  **همان** قاعدهٔ JDK است، با همان اشیای کدِ مشترک:
 *    ۱) زنجیرهٔ گواهی‌ها (برگ نخست، DER ِ `SecCertificateCopyData`) ⇐ `checkServerTrusted` ِ نخستین
 *       `X509TrustManager` (همان که `SSLContext.init` ِ JDK برمی‌دارد)؛ استثنا ⇐ رد؛
 *    ۲) سپس `HostnameVerifier.verify(host, session)` با همان گواهی‌ها؛ false ⇐ رد.
 *  هر دو پذیرفتند ⇐ `credentialForTrust` (اعتمادِ پیش‌فرضِ سیستم جایش را نمی‌گیرد، مثلِ JDK)؛ رد ⇐ دست‌دادن
 *  لغو و خطای درخواست `SSLHandshakeException`. بی verifier ⇐ رد (کدِ مشترک همیشه هر دو را می‌دهد؛ «بسته» امن‌تر است).
 *  بی کارخانهٔ سفارشی ⇐ رسیدگیِ پیش‌فرضِ iOS.
 */
internal object IosTls {

  private class IosX509(private val der: ByteArray) : X509Certificate() {
    override val encoded: ByteArray get() = der.copyOf()
  }

  private class Session(override val peerHost: String?, override val peerCertificates: Array<Certificate>) : SSLSession

  /** DER ِ گواهی‌های زنجیره، برگ نخست */
  fun chainOf(trust: SecTrustRef): List<ByteArray> {
    val arr = SecTrustCopyCertificateChain(trust) ?: return emptyList()
    try {
      return (0 until CFArrayGetCount(arr)).mapNotNull { i ->
        val cert: SecCertificateRef? = CFArrayGetValueAtIndex(arr, i)?.reinterpret()
        cert?.let { der(it) }
      }
    } finally {
      CFRelease(arr)
    }
  }

  private fun der(cert: SecCertificateRef): ByteArray? {
    val data = SecCertificateCopyData(cert) ?: return null
    try {
      val len = CFDataGetLength(data).toInt()
      val ptr = CFDataGetBytePtr(data) ?: return null
      return ptr.reinterpret<ByteVar>().readBytes(len)
    } finally {
      CFRelease(data)
    }
  }

  /** قاعدهٔ JDK روی زنجیرهٔ DER (خالص؛ آزمونِ شبیه‌ساز همین را با `SecTrust` ِ ساختگی می‌سنجد) */
  fun accepts(chain: List<ByteArray>, host: String, trust: List<TrustManager>, verifier: HostnameVerifier?): Boolean {
    if (chain.isEmpty() || verifier == null) return false
    val tm = trust.firstOrNull { it is X509TrustManager } as? X509TrustManager ?: return false
    val certs: Array<X509Certificate> = chain.map { IosX509(it) as X509Certificate }.toTypedArray()
    try {
      tm.checkServerTrusted(certs, "RSA")
    } catch (_: Throwable) {
      return false
    }
    val peers: Array<Certificate> = certs.map { it as Certificate }.toTypedArray()
    return try {
      verifier.verify(host, Session(host, peers))
    } catch (_: Throwable) {
      false
    }
  }

  /**
   *  پاسخِ پرسشِ اعتماد. [trust] null ⇐ رسیدگیِ پیش‌فرض و برگشتِ null؛ وگرنه true/false (پذیرفته/ردشده).
   */
  fun answer(
    challenge: NSURLAuthenticationChallenge,
    trust: List<TrustManager>?,
    verifier: HostnameVerifier?,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
  ): Boolean? {
    val space = challenge.protectionSpace
    if (trust == null || space.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
      completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
      return null
    }
    val serverTrust = space.serverTrust
    val ok = serverTrust != null && accepts(chainOf(serverTrust), space.host.orEmpty(), trust, verifier)
    if (ok) completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(serverTrust))
    else completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
    return ok
  }
}

/**
 *  نشست‌های NSURLSession ِ `HttpURLConnection` ِ shim (‎java/net/Http.kt‎): [پراکسیِ سیستم | بی پراکسی (`Proxy.NO_PROXY`)]
 *  × [اعتمادِ پیش‌فرضِ iOS | سنجاقِ گواهی]؛ هر چهار تنبل و یکی برای همه. هر نشست نمایندهٔ خودش و دفترِ کارهای خودش را
 *  دارد (شناسهٔ کار فقط درونِ یک نشست یکتاست) و برای هر درخواست تا پایانش نگه می‌دارد:
 *    - کارخانه و verifier ِ سنجاق (فقط نشستِ سنجاق‌دار؛ کارِ ناشناس در آن ⇐ رد، نه اعتمادِ پیش‌فرض)؛
 *    - `instanceFollowRedirects = false` ⇐ ریدایرکت دنبال نمی‌شود و پاسخِ ۳xx همان‌جا برمی‌گردد (مثلِ JDK؛
 *      `ServerSwitchAndroid.probe` و `HubRelayAndroid` ِ کدِ مشترک همین را می‌خواهند).
 */
internal class ShimHttp private constructor(private val pinned: Boolean, direct: Boolean) {
  private class Entry(val trust: List<TrustManager>?, val verifier: HostnameVerifier?, val follow: Boolean) {
    var rejected = false
  }

  private val lock = Any()
  private val tasks = HashMap<ULong, Entry>()

  private class Delegate(private val owner: ShimHttp) : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
      session: NSURLSession,
      task: NSURLSessionTask,
      didReceiveChallenge: NSURLAuthenticationChallenge,
      completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
      val entry = owner.entry(task)
      //  سنجاق‌دار: کارِ ناشناس ⇐ فهرستِ خالی ⇐ رد؛ بی سنجاق ⇐ `null` ⇐ رسیدگیِ پیش‌فرضِ iOS
      val trust = if (owner.pinned) entry?.trust ?: emptyList() else null
      val ok = IosTls.answer(didReceiveChallenge, trust, entry?.verifier, completionHandler)
      if (ok == false) synchronized(owner.lock) { entry?.rejected = true }
    }

    override fun URLSession(
      session: NSURLSession,
      task: NSURLSessionTask,
      willPerformHTTPRedirection: NSHTTPURLResponse,
      newRequest: NSURLRequest,
      completionHandler: (NSURLRequest?) -> Unit,
    ) {
      completionHandler(if (owner.entry(task)?.follow == false) null else newRequest)
    }
  }

  private val delegate = Delegate(this)

  val session: NSURLSession by lazy {
    val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
    config.HTTPCookieStorage = null
    config.URLCache = null
    config.requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
    //  دیکشنریِ خالی = «بی پراکسی» (null یعنی تنظیمِ سیستم)
    if (direct) config.connectionProxyDictionary = emptyMap<Any?, Any?>()
    NSURLSession.sessionWithConfiguration(config, delegate = delegate, delegateQueue = null)
  }

  private fun entry(task: NSURLSessionTask): Entry? = synchronized(lock) { tasks[task.taskIdentifier] }

  /** پیش از `resume`؛ [factory] فقط در نشستِ سنجاق‌دار */
  fun register(task: NSURLSessionTask, factory: SSLSocketFactory?, verifier: HostnameVerifier?, follow: Boolean) {
    synchronized(lock) { tasks[task.taskIdentifier] = Entry(factory?.trustManagers, verifier, follow) }
  }

  /** پایانِ درخواست؛ true یعنی سنجاق دست‌دادن را رد کرد */
  fun finish(task: NSURLSessionTask): Boolean = synchronized(lock) { tasks.remove(task.taskIdentifier)?.rejected == true }

  companion object {
    private val all = Array(4) { i -> lazy { ShimHttp(pinned = i and 1 == 1, direct = i and 2 == 2) } }

    fun of(pinned: Boolean, direct: Boolean): ShimHttp = all[(if (pinned) 1 else 0) + (if (direct) 2 else 0)].value
  }
}
