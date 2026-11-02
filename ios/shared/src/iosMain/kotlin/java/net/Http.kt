package java.net

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import javax.net.ssl.SSLException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setHTTPShouldHandleCookies
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy

/*
 *  shimِ iOS ِ `java.net.URL` و `HttpURLConnection` روی NSURLSession (گامِ ۲-الف). `core/net/HttpEngine`
 *  دست‌نخورده روی آن کار می‌کند — همان مهلت‌ها، سرآیندها، بدنه و همان خطاهای JDK:
 *    مهلت ⇐ SocketTimeoutException، نامِ ناپیدا/بی‌نت ⇐ UnknownHostException، وصل‌نشدن ⇐
 *    ConnectException، TLS ⇐ SSLException، بقیه ⇐ IOException (`ApiFailure.fromException` همین‌ها را می‌شناسد).
 *  درخواست همگام است (سمافور)، پس فقط درونِ `Dispatchers.IO` صدا زده شود — همان قاعدهٔ اندروید.
 *  ⛔ تفاوت‌های دانسته: بدنهٔ بالارفته یک‌جا فرستاده می‌شود (گزارشِ پیشرفتِ تکه‌تکه پیش از فرستادن پر می‌شود)؛
 *  کوکی و کشِ سیستم خاموش‌اند (مثلِ HttpURLConnection ِ بی CookieManager)؛ http ِ بی TLS را ATS ِ iOS نمی‌گذارد.
 */
open class MalformedURLException(message: String? = null) : IOException(message)

class URL(private val spec: String) {
  internal val ns: NSURL = NSURL.URLWithString(spec)?.takeIf { it.scheme != null && it.host != null }
    ?: throw MalformedURLException("no protocol: $spec")

  val protocol: String get() = ns.scheme.orEmpty()
  val host: String get() = ns.host.orEmpty()
  val port: Int get() = ns.port?.intValue ?: -1
  val path: String get() = ns.path.orEmpty()
  val query: String? get() = ns.query

  /** https ⇐ `HttpsURLConnection` (سنجاقِ گواهیِ «سرورِ خودِ دکان»، ‎javax/net/ssl‎)، مثلِ JDK */
  fun openConnection(): URLConnection =
    if (protocol.equals("https", ignoreCase = true)) javax.net.ssl.HttpsURLConnection(this) else HttpURLConnection(this)
  override fun toString(): String = spec
  override fun equals(other: Any?): Boolean = other is URL && other.spec == spec
  override fun hashCode(): Int = spec.hashCode()
}

abstract class URLConnection internal constructor(val url: URL) {
  var connectTimeout: Int = 0
  var readTimeout: Int = 0
  var doOutput: Boolean = false
  var doInput: Boolean = true
  var useCaches: Boolean = false
  internal val headers = LinkedHashMap<String, String>()

  fun setRequestProperty(key: String, value: String) { headers[key] = value }
  fun addRequestProperty(key: String, value: String) { headers[key] = headers[key]?.let { "$it, $value" } ?: value }
  fun getRequestProperty(key: String): String? = headers.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

  abstract val outputStream: OutputStream
  abstract val inputStream: InputStream
  abstract val contentLengthLong: Long
  val contentLength: Int get() = contentLengthLong.let { if (it > Int.MAX_VALUE) -1 else it.toInt() }
  abstract fun getHeaderField(name: String): String?
  val contentType: String? get() = getHeaderField("Content-Type")
  open fun connect() {}
}

open class HttpURLConnection internal constructor(url: URL) : URLConnection(url) {
  var requestMethod: String = "GET"
  var instanceFollowRedirects: Boolean = true
  private val body = ByteArrayOutputStream()
  private var done: Response? = null

  private class Response(val code: Int, val message: String, val headers: Map<String, String>, val data: ByteArray, val length: Long)

  fun setFixedLengthStreamingMode(contentLength: Int) {}
  fun setFixedLengthStreamingMode(contentLength: Long) {}
  fun setChunkedStreamingMode(chunkLen: Int) {}

  override val outputStream: OutputStream
    get() {
      if (!doOutput) throw java.net.ProtocolException("cannot write request body (doOutput=false)")
      if (requestMethod == "GET") requestMethod = "POST"
      return body
    }

  val responseCode: Int get() = response().code
  val responseMessage: String get() = response().message

  override val inputStream: InputStream
    get() {
      val r = response()
      if (r.code >= 400) {
        if (r.code == 404 || r.code == 410) throw FileNotFoundException(url.toString())
        throw IOException("Server returned HTTP response code: ${r.code} for URL: $url")
      }
      return ByteArrayInputStream(r.data)
    }

  /** فقط پاسخِ خطا (≥ ۴۰۰) بدنه دارد؛ وگرنه null — همان JDK */
  val errorStream: InputStream?
    get() = done?.takeIf { it.code >= 400 }?.let { ByteArrayInputStream(it.data) }

  override val contentLengthLong: Long get() = response().length

  override fun getHeaderField(name: String): String? =
    response().headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

  override fun connect() { response() }

  fun disconnect() {}

  private fun response(): Response = done ?: execute().also { done = it }

  private fun execute(): Response {
    val request = NSMutableURLRequest.requestWithURL(url.ns)
    request.setHTTPMethod(requestMethod)
    val timeout = maxOf(connectTimeout, readTimeout)
    if (timeout > 0) request.setTimeoutInterval(timeout / 1000.0)
    request.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
    request.setHTTPShouldHandleCookies(false)
    headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
    if (body.size() > 0) request.setHTTPBody(body.toByteArray().toNSData())

    //  گواهیِ سنجاق‌شده (`PinnedTls.applyTo` ِ کدِ مشترک) ⇐ نشستِ سنجاق‌دار؛ وگرنه اعتمادِ پیش‌فرضِ iOS
    val https = this as? javax.net.ssl.HttpsURLConnection
    val pinned = https?.sslSocketFactory
    val signal = dispatch_semaphore_create(0)
    var data: NSData? = null
    var meta: NSURLResponse? = null
    var failure: NSError? = null
    val task = (if (pinned != null) javax.net.ssl.PinnedHttp.session else session).dataTaskWithRequest(request) { d, r, e ->
      data = d; meta = r; failure = e
      dispatch_semaphore_signal(signal)
    }
    if (pinned != null) javax.net.ssl.PinnedHttp.register(task, pinned, https.hostnameVerifier)
    task.resume()
    dispatch_semaphore_wait(signal, DISPATCH_TIME_FOREVER)

    //  دست‌دادنی که سنجاق رد کرد ⇐ همان ردهٔ JDK (`ServerSwitchAndroid.probe` همین را می‌گیرد)، نه «لغو شد»
    if (pinned != null && javax.net.ssl.PinnedHttp.finish(task)) throw javax.net.ssl.SSLHandshakeException("certificate pin mismatch")
    failure?.let { throw it.toIOException() }
    val http = meta as? NSHTTPURLResponse ?: throw IOException("پاسخِ HTTP نیست: $url")
    val headers = http.allHeaderFields.entries.associate { (k, v) -> k.toString() to v.toString() }
    return Response(
      code = http.statusCode.toInt(),
      message = NSHTTPURLResponse.localizedStringForStatusCode(http.statusCode),
      headers = headers,
      data = data?.toByteArray() ?: ByteArray(0),
      length = http.expectedContentLength,
    )
  }

  companion object {
    const val HTTP_OK = 200
    const val HTTP_CREATED = 201
    const val HTTP_NO_CONTENT = 204
    const val HTTP_NOT_MODIFIED = 304
    const val HTTP_BAD_REQUEST = 400
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_NOT_FOUND = 404
    const val HTTP_CONFLICT = 409
    const val HTTP_INTERNAL_ERROR = 500
    const val HTTP_UNAVAILABLE = 503

    /** بی کوکی و بی کش، مثلِ HttpURLConnection ِ بی CookieManager ِ اندروید */
    private val session: NSURLSession by lazy {
      val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
      config.HTTPCookieStorage = null
      config.URLCache = null
      NSURLSession.sessionWithConfiguration(config)
    }
  }
}

open class ProtocolException(message: String? = null) : IOException(message)

private fun NSError.toIOException(): IOException {
  val text = localizedDescription
  if (domain != NSURLErrorDomain) return IOException(text)
  return when (code) {
    NSURLErrorTimedOut -> SocketTimeoutException(text)
    NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed, NSURLErrorNotConnectedToInternet -> UnknownHostException(text)
    NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost -> ConnectException(text)
    NSURLErrorCancelled -> InterruptedIOException(text)
    in -1206L..-1200L, -1022L -> SSLException(text)
    else -> IOException(text)
  }
}

internal fun ByteArray.toNSData(): NSData =
  if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
  val out = ByteArray(length.toInt())
  if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
  return out
}
