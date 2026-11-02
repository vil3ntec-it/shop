package ir.vil3ntec.tohid.sync.v1

import android.content.Context

/*
 *  جایگزینِ iOS ِ `sync/v1/SyncV1Worker.kt` (WorkManager ِ اندروید؛ همان API ِ همراه).
 *  همگام‌سازی روی آیفون هنوز نیست (شبکهٔ `HttpEngine` و `BGAppRefreshTask` ⇐ گامِ ۲)؛ این سه کار
 *  فعلاً هیچ نمی‌کنند و دفترِ آیفون فقط محلی است — چیزی گم نمی‌شود، فقط به سرور نمی‌رود.
 */
class SyncV1Worker private constructor() {
  companion object {
    fun schedule(context: Context) {}

    fun now(context: Context) {}

    fun cancel(context: Context) {}
  }
}
