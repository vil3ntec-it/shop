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
}
