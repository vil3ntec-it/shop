package ir.vil3ntec.tohid.data

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 *  آوردنِ داده از نسخهٔ وب — یک بار، هنگام اولین اجرا.
 *
 *  نسخهٔ قبلی همان برنامه بود داخلِ WebView، و دفترِ دکان را در localStorage
 *  می‌نوشت. آن داده در پوشهٔ خودِ همین برنامه است، ولی در قالبِ LevelDB که
 *  خواندنش از بیرون دردسر است.
 *
 *  راهِ مطمئن‌تر: یک WebView نامرئی روی همان مبدأ باز می‌شود و همان کلید را
 *  می‌خواند. چون بستهٔ برنامه و امضایش عوض نشده، همان داده سرِ جایش است.
 *
 *  اگر چیزی نبود، یعنی کاربر تازه است — نه اینکه خطایی رخ داده.
 */
object Migration {

  private const val ORIGIN = "https://appassets.androidplatform.net/assets/migrate.html"
  private const val KEY = "tohid-shop-data-v1"

  /**
   *  آیا اصلاً نسخهٔ WebViewِ قدیمی روی این گوشی بوده؟ اگر پوشهٔ دادهٔ
   *  WebView (`app_webview`) نباشد، هیچ دفترِ قدیمی‌ای هم نیست.
   *
   *  پیش‌تر هر بار که دفتر خالی بود — یعنی هر کاربرِ تازه، در هر بار
   *  باز کردن — یک WebView ساخته می‌شد: بالا آمدنِ کُند، و برخلافِ قاعدهٔ
   *  صاحب مخزن که برنامهٔ اندروید «حتی یک درصد» وب نباشد. حالا WebView فقط
   *  برای همان یک بارِ نجاتِ دفترِ نسخهٔ قدیمی ساخته می‌شود و برای هیچ
   *  کاربرِ دیگری هرگز.
   */
  fun hasLegacyWebData(context: Context): Boolean =
    runCatching { java.io.File(context.applicationInfo.dataDir, "app_webview").isDirectory }.getOrDefault(false)

  /** @return متنِ JSON دفترِ دکان، یا null اگر داده‌ای نبود */
  suspend fun readLegacyData(context: Context): String? {
    if (!hasLegacyWebData(context)) return null
    return readViaWebView(context)
  }

  private suspend fun readViaWebView(context: Context): String? =
    suspendCancellableCoroutine { cont ->
      val web = WebView(context)
      web.settings.javaScriptEnabled = true
      web.settings.domStorageEnabled = true

      val loader = androidx.webkit.WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
        .build()

      web.webViewClient = object : android.webkit.WebViewClient() {
        override fun shouldInterceptRequest(
          view: WebView, request: android.webkit.WebResourceRequest
        ) = loader.shouldInterceptRequest(request.url)

        override fun onPageFinished(view: WebView, url: String) {
          view.evaluateJavascript("localStorage.getItem('$KEY')") { value ->
            val raw = decode(value)
            if (!cont.isCompleted) cont.resume(raw)
            view.destroy()
          }
        }
      }
      web.loadUrl(ORIGIN)

      cont.invokeOnCancellation { runCatching { web.destroy() } }
    }

  /**
   * evaluateJavascript رشته را به‌صورتِ یک مقدارِ JSON برمی‌گرداند، یعنی
   * رشتهٔ ما داخلِ گیومه و با کاراکترهای فرار. باید یک لایه باز شود.
   */
  private fun decode(value: String?): String? {
    if (value.isNullOrBlank() || value == "null") return null
    return runCatching {
      org.json.JSONTokener(value).nextValue() as? String
    }.getOrNull()
  }
}
