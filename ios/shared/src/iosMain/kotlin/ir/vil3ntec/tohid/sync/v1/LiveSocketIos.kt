package ir.vil3ntec.tohid.sync.v1

import android.content.Context

/*
 *  جایگزینِ iOS ِ `sync/v1/LiveSocket.kt` (سوکتِ زندهٔ OkHttp: فروشِ هر دستگاه همان لحظه روی بقیه).
 *  سوکتِ زندهٔ iOS (NSURLSessionWebSocketTask) با همگام‌سازی گامِ ۳ است؛ تا آن روز «وصل نیست» — همان
 *  حالِ اندرویدِ بی نت: صفحه‌ها «زنده نیست» نشان می‌دهند و «گوشی به‌عنوانِ بارکدخوان» پیشنهاد نمی‌شود.
 */
class LiveSocket(context: Context, private val onChanged: () -> Unit) {
  val connected: Boolean get() = false

  fun send(json: String): Boolean = false

  fun connect() {}

  fun disconnect() {}

  companion object {
    var current: LiveSocket? = null
  }
}
