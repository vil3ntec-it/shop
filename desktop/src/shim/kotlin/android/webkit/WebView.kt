package android.webkit

import android.content.Context
import android.print.PrintDocumentAdapter

/**
 *  فقط همان تکه از `WebView` که `ReportPrint` برای **چاپ** لازم دارد:
 *  HTML را نگه می‌دارد و `createPrintDocumentAdapter` آن را به
 *  `PrintManager` می‌دهد، که در مرورگرِ سیستم با پنجرهٔ چاپ بازش می‌کند.
 *  (چاپ ⇒ «ذخیره به PDF» هم همان‌جاست.)
 */
open class WebView(@Suppress("UNUSED_PARAMETER") context: Context) {
  var webViewClient: WebViewClient = WebViewClient()
  internal var html: String = ""

  fun loadDataWithBaseURL(base: String?, data: String, mime: String?, encoding: String?, history: String?) {
    html = data
    webViewClient.onPageFinished(this, base ?: "about:blank")
  }

  fun createPrintDocumentAdapter(name: String): PrintDocumentAdapter = PrintDocumentAdapter(name, html)
  fun destroy() {}
}

open class WebViewClient {
  open fun onPageFinished(view: WebView, url: String) {}
}
