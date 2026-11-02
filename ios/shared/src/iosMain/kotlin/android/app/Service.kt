package android.app

import android.content.ContextWrapper
import android.content.Intent
import android.content.IosContext
import android.os.IBinder

/*
 *  shimِ iOS ِ `Service` ِ اندروید (دستهٔ یازدهم: «اسکن در پس‌زمینه» ِ `ScanCaptureService`، شنوندهٔ اعلانِ حساب‌پی).
 *  iOS سرویسِ پیش‌رو و اجرای پس‌زمینهٔ دلخواه ندارد: `startForegroundService` ⇐ هیچ (‎ContextCompat‎)، پس این
 *  رده‌ها فقط کامپایل می‌شوند و نمونه‌ای از آن‌ها ساخته نمی‌شود. همگام‌سازیِ پس‌زمینهٔ آیفون همان `BGAppRefreshTask` است.
 */
abstract class Service : ContextWrapper(IosContext) {
  abstract fun onBind(intent: Intent?): IBinder?
  open fun onCreate() {}
  open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
  open fun onDestroy() {}
  fun stopSelf() {}
  fun stopSelf(startId: Int) {}

  companion object {
    const val START_STICKY = 1
    const val START_NOT_STICKY = 2
    const val START_REDELIVER_INTENT = 3
    const val STOP_FOREGROUND_REMOVE = 1
  }
}
