package androidx.core.content

import android.content.Context
import android.content.pm.PackageManager
import ir.vil3ntec.tohid.ios.jvm.Runnable
import java.util.concurrent.Executor
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 *  shimِ iOS ِ `ContextCompat`. `checkSelfPermission` حالِ اجازهٔ خودِ iOS است (دوربین، موقعیت، اعلان —
 *  ‎ir/…/ios/IosPermissions.kt‎)؛ اجازهٔ بی همتا در iOS (میکروفون، بلوتوث…) «ندارد».
 */
object ContextCompat {
  fun checkSelfPermission(context: Context, permission: String): Int =
    if (ir.vil3ntec.tohid.ios.IosPermissions.granted(permission)) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

  fun getMainExecutor(context: Context): Executor = Executor { r: Runnable -> dispatch_async(dispatch_get_main_queue()) { r.run() } }

  /** سرویسِ پیش‌رو در iOS نیست (‎android/app/Service.kt‎) */
  fun startForegroundService(context: Context, intent: android.content.Intent) {
    context.startForegroundService(intent)
  }

  fun registerReceiver(context: Context, receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter, flags: Int): android.content.Intent? =
    context.registerReceiver(receiver, filter, flags)

  const val RECEIVER_VISIBLE_TO_INSTANT_APPS = 1
  const val RECEIVER_EXPORTED = 2
  const val RECEIVER_NOT_EXPORTED = 4
}
