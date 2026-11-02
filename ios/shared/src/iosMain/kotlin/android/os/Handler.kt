package android.os

import ir.vil3ntec.tohid.ios.jvm.Runnable
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/*
 *  shimِ iOS ِ `Looper`/`Handler` (همان نام‌های `desktop/src/shim/kotlin/android/os/Misc.kt`): فقط رشتهٔ اصلی،
 *  روی صفِ اصلیِ GCD — کدِ مشترک (`sync/v1/LiveSocket`: «دوباره وصل شو، n ثانیه بعد») همین را می‌خواهد.
 *  `removeCallbacksAndMessages(null)` کارهای مانده‌ی همین Handler را بی‌اثر می‌کند (مثلِ اندروید).
 */
class Looper private constructor() {
  companion object {
    private val main = Looper()
    fun getMainLooper(): Looper = main
    fun myLooper(): Looper? = main
  }
}

class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper? = null) {
  /** نسلِ کارها: `removeCallbacksAndMessages` آن را جلو می‌برد و کارِ نسلِ کهنه اجرا نمی‌شود */
  private var generation = 0

  fun post(r: Runnable): Boolean {
    val gen = generation
    dispatch_async(dispatch_get_main_queue()) { if (gen == generation) r.run() }
    return true
  }

  fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
    val gen = generation
    val at = dispatch_time(DISPATCH_TIME_NOW, delayMillis.coerceAtLeast(0) * 1_000_000L)
    dispatch_after(at, dispatch_get_main_queue()) { if (gen == generation) r.run() }
    return true
  }

  fun removeCallbacksAndMessages(@Suppress("UNUSED_PARAMETER") token: Any?) {
    generation++
  }
}
