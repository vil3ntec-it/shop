package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperSender
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 *  فرستندهٔ اندروید برای `ShopperApi` ِ core: HttpURLConnection ِ خودِ سیستم (بی کتابخانهٔ شبکهٔ افزوده).
 *  ⛔ ریدایرکت دنبال نمی‌شود: `store_moved` (۳۰۸) را کارخواه می‌خواند. همان رفتارِ `JdkShopperHttp` ِ آزمونِ
 *  سرتاسری (`core/src/jvmTest/.../ShopperLiveTest.kt`). [base] هر بار خوانده می‌شود (نشانیِ سرور از تنظیمات).
 */
class HttpAndroid(private val base: () -> String) : ShopperHttp, ShopperSender {
  override fun send(request: ShopperRequest): ShopperResponse = send(base(), request)

  /** همان، با نشانیِ پایهٔ داده‌شده (`ShopperRoutedHttp`، پرچمِ خاموشِ `tohid.multiServer`)؛ [ShopperRequest.timeoutMs] ِ ۰ ⇒ مهلتِ همیشگی */
  override fun send(base: String, request: ShopperRequest): ShopperResponse {
    val c = URL(base.trimEnd('/') + request.path).openConnection() as HttpURLConnection
    try {
      c.instanceFollowRedirects = false
      c.connectTimeout = if (request.timeoutMs > 0) request.timeoutMs else 15_000
      c.readTimeout = if (request.timeoutMs > 0) request.timeoutMs * 2 else 30_000
      c.useCaches = false
      c.requestMethod = request.method
      for ((k, v) in request.headers) c.setRequestProperty(k, v)
      //  بدنهٔ JSON، یا بایت‌های خام (عکسِ گفت‌وگو با `Content-Type` ِ خودش)
      (request.bytes ?: request.body?.toByteArray(Charsets.UTF_8))?.let { b ->
        c.doOutput = true
        c.outputStream.use { it.write(b) }
      }
      val status = c.responseCode
      val raw = (if (status >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
      val headers = c.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(",") }
      return ShopperResponse(status, if (request.binary && status == 200) "" else raw.toString(Charsets.UTF_8), headers, raw.takeIf { request.binary })
    } finally {
      c.disconnect()
    }
  }

  companion object {
    /** درخواست اصلاً نرفت (وصل نشد، نامِ میزبان پیدا نشد، دست‌دادنِ TLS نشد) ⇒ هر درخواستی را می‌شود جای دیگر فرستاد */
    fun notSent(e: Throwable): Boolean =
      e is ConnectException || e is UnknownHostException || e is NoRouteToHostException || e is SSLHandshakeException
  }
}
