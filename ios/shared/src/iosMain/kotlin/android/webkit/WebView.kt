package android.webkit

import android.content.Context

/*
 *  shimِ iOS (گامِ ۹) — فقط برای این‌که `data/Migration.kt` ِ اندروید (کوچِ دفتر از برنامهٔ وبیِ قدیمیِ اندروید) خودش روی
 *  آیفون کامپایل شود. آن برنامهٔ وبی هرگز روی آیفون نبود: پوشهٔ `app_webview` در بستهٔ آیفون نیست، پس
 *  `Migration.hasLegacyWebData` نادرست و `readLegacyData` همان null است (رفتارِ `MigrationIos` ِ پیشین) و هیچ WebView ای
 *  ساخته نمی‌شود. اگر روزی ساخته شود ⇐ خطای روشن (`runCatching` ِ `AppRoot` ⇐ «دفترِ قدیمی نیست»)، نه کارِ بی‌صدا.
 */
internal class WebView(context: Context) {
  init {
    throw UnsupportedOperationException("iOS: WebView ِ اندروید نیست (کوچ از برنامهٔ وبیِ قدیمیِ اندروید فقط در اندروید)")
  }

  val settings: WebSettings = WebSettings()
  var webViewClient: WebViewClient = WebViewClient()

  fun loadUrl(url: String) {}

  fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {}

  fun destroy() {}
}

internal class WebSettings internal constructor() {
  var javaScriptEnabled: Boolean = false
  var domStorageEnabled: Boolean = false
}

internal open class WebViewClient {
  open fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = null

  open fun onPageFinished(view: WebView, url: String) {}
}

internal interface WebResourceRequest {
  val url: android.net.Uri
}

internal class WebResourceResponse internal constructor()

internal fun interface ValueCallback<T> {
  fun onReceiveValue(value: T)
}
