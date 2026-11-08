package ir.vil3ntec.tohid.customer.ios

import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setHTTPShouldHandleCookies
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy

/**
 *  فرستندهٔ آیفون برای `ShopperApi` ِ core — NSURLSession ِ خودِ سیستم، همان رفتارِ `HttpAndroid.kt`:
 *  هم‌گام (سمافور؛ فقط از `Dispatchers.IO`)، بی کوکی و بی کش، و ⛔ **بی دنبال کردنِ ریدایرکت** (۳۰۸ ِ `store_moved` را
 *  کارخواه می‌خواند). بایت‌های خام (عکسِ گفت‌وگو) با `Content-Type` ِ خودشان؛ [ShopperRequest.binary] ⇒ بایت‌های پاسخ.
 *  نرسیدن به سرور ⇒ استثنا ⇒ خطای `network` ِ کارخواه.
 */
class HttpIos(private val base: () -> String) : ShopperHttp {
  private class NoRedirect : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
      session: NSURLSession,
      task: NSURLSessionTask,
      willPerformHTTPRedirection: NSHTTPURLResponse,
      newRequest: NSURLRequest,
      completionHandler: (NSURLRequest?) -> Unit,
    ) = completionHandler(null)
  }

  private val delegate = NoRedirect()

  private val session: NSURLSession by lazy {
    val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
    config.HTTPCookieStorage = null
    config.URLCache = null
    config.requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
    config.timeoutIntervalForRequest = 30.0
    NSURLSession.sessionWithConfiguration(config, delegate = delegate, delegateQueue = null)
  }

  override fun send(request: ShopperRequest): ShopperResponse {
    val url = NSURL.URLWithString(base().trimEnd('/') + request.path) ?: throw IllegalArgumentException("نشانیِ نادرست")
    val req = NSMutableURLRequest.requestWithURL(url)
    req.setHTTPMethod(request.method)
    req.setHTTPShouldHandleCookies(false)
    request.headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
    (request.bytes ?: request.body?.encodeToByteArray())?.let { req.setHTTPBody(it.toNSData()) }
    val signal = dispatch_semaphore_create(0)
    var data: NSData? = null
    var meta: NSURLResponse? = null
    var failure: NSError? = null
    session.dataTaskWithRequest(req) { d, r, e ->
      data = d; meta = r; failure = e
      dispatch_semaphore_signal(signal)
    }.resume()
    dispatch_semaphore_wait(signal, DISPATCH_TIME_FOREVER)
    failure?.let { throw IllegalStateException(it.localizedDescription) }
    val http = meta as? NSHTTPURLResponse ?: throw IllegalStateException("پاسخِ HTTP نیست")
    val status = http.statusCode.toInt()
    val raw = data?.toByteArray() ?: ByteArray(0)
    val headers = http.allHeaderFields.entries.associate { (k, v) -> k.toString() to v.toString() }
    return ShopperResponse(status, if (request.binary && status == 200) "" else raw.decodeToString(), headers, raw.takeIf { request.binary })
  }
}

internal fun ByteArray.toNSData(): NSData =
  if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
  val out = ByteArray(length.toInt())
  if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
  return out
}
