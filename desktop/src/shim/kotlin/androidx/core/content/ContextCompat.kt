package androidx.core.content

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.Executor

/** روی کامپیوتر هیچ اجازه‌ای پرسیدنی نیست — همه داده شده‌اند. */
object ContextCompat {
  fun checkSelfPermission(context: Context, permission: String): Int = PackageManager.PERMISSION_GRANTED
  fun getMainExecutor(context: Context): Executor = Executor { javax.swing.SwingUtilities.invokeLater(it) }
}
