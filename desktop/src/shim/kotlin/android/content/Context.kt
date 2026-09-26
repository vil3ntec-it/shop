package android.content

import android.content.pm.PackageManager
import java.io.File

/**
 *  `Context`ِ اندروید، روی کامپیوتر.
 *
 *  برنامهٔ اندروید همه‌چیز را از `Context` می‌گیرد: پوشهٔ داده، تنظیمات،
 *  سرویس‌های سیستم. این‌جا یک نمونهٔ یگانه (`DesktopContext`) همان‌ها را
 *  از پوشهٔ دادهٔ کاربر روی کامپیوتر می‌دهد، پس فایل‌های اندروید
 *  دست‌نخورده کار می‌کنند.
 */
abstract class Context {

  open val applicationContext: Context get() = this

  abstract val filesDir: File
  abstract val cacheDir: File
  open val packageName: String get() = "ir.vil3ntec.tohid"

  abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
  open fun deleteSharedPreferences(name: String): Boolean = false

  abstract val contentResolver: ContentResolver
  abstract val packageManager: PackageManager

  abstract fun getSystemService(name: String): Any?
  abstract fun <T> getSystemService(serviceClass: Class<T>): T?

  abstract fun startActivity(intent: Intent)

  open fun getString(id: Int): String = ""

  open fun getDir(name: String, mode: Int): File = File(filesDir.parentFile, "app_$name").apply { mkdirs() }
  open fun getExternalFilesDir(type: String?): File? = File(filesDir.parentFile, "external/${type ?: ""}").apply { mkdirs() }
  open val noBackupFilesDir: File get() = File(filesDir.parentFile, "no_backup").apply { mkdirs() }
  open val dataDir: File get() = filesDir.parentFile!!

  companion object {
    const val MODE_PRIVATE = 0
    const val CONNECTIVITY_SERVICE = "connectivity"
    const val VIBRATOR_SERVICE = "vibrator"
    const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
    const val NOTIFICATION_SERVICE = "notification"
    const val LOCATION_SERVICE = "location"
    const val PRINT_SERVICE = "print"
    const val USB_SERVICE = "usb"
    const val BLUETOOTH_SERVICE = "bluetooth"
    const val ACTIVITY_SERVICE = "activity"
    const val CLIPBOARD_SERVICE = "clipboard"
    const val INPUT_METHOD_SERVICE = "input_method"
  }
}
