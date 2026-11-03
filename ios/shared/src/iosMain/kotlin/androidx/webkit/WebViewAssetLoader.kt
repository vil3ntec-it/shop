package androidx.webkit

import android.content.Context
import android.webkit.WebResourceResponse

/** shimِ iOS (گامِ ۹) — همراهِ `android.webkit.WebView` ِ همین پوشه (`data/Migration.kt`)؛ در آیفون هرگز به کار نمی‌رود */
internal class WebViewAssetLoader private constructor() {
  fun shouldInterceptRequest(url: android.net.Uri): WebResourceResponse? = null

  fun interface PathHandler {
    fun handle(path: String): WebResourceResponse?
  }

  class AssetsPathHandler(context: Context) : PathHandler {
    override fun handle(path: String): WebResourceResponse? = null
  }

  class Builder {
    fun addPathHandler(path: String, handler: PathHandler): Builder = this
    fun build(): WebViewAssetLoader = WebViewAssetLoader()
  }
}
