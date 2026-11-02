package androidx.core.content

import android.content.Context
import android.content.pm.PackageManager
import ir.vil3ntec.tohid.ios.jvm.Runnable
import java.util.concurrent.Executor
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 *  shimِ iOS ِ `ContextCompat`. اجازهٔ دوربین/میکروفون/موقعیت/اعلان در iOS هنوز پرسیده نمی‌شود (گامِ ۳)،
 *  پس `checkSelfPermission` همیشه «ندارد» — کدِ مشترک همان مسیرِ «اجازه نیست» ِ اندروید را می‌رود.
 */
object ContextCompat {
  fun checkSelfPermission(context: Context, permission: String): Int = PackageManager.PERMISSION_DENIED

  fun getMainExecutor(context: Context): Executor = Executor { r: Runnable -> dispatch_async(dispatch_get_main_queue()) { r.run() } }
}
