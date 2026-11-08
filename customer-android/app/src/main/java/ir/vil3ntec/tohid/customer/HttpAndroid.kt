package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import java.net.HttpURLConnection
import java.net.URL

/**
 *  فرستندهٔ اندروید برای `ShopperApi` ِ core: HttpURLConnection ِ خودِ سیستم (بی کتابخانهٔ شبکهٔ افزوده).
 *  ⛔ ریدایرکت دنبال نمی‌شود: `store_moved` (۳۰۸) را کارخواه می‌خواند. همان رفتارِ `JdkShopperHttp` ِ آزمونِ
 *  سرتاسری (`core/src/jvmTest/.../ShopperLiveTest.kt`). [base] هر بار خوانده می‌شود (نشانیِ سرور از تنظیمات).
 */
class HttpAndroid(private val base: () -> String) : ShopperHttp {
  override fun send(request: ShopperRequest): ShopperResponse {
    val c = URL(base().trimEnd('/') + request.path).openConnection() as HttpURLConnection
    try {
      c.instanceFollowRedirects = false
      c.connectTimeout = 15_000
      c.readTimeout = 30_000
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
}
