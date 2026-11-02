package android.content

import ir.vil3ntec.tohid.ios.jvm.Class
import java.io.File
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

/*
 *  shimِ iOS ِ `android.content.Context` — همان نقشِ `desktop/src/shim/kotlin/android/content/Context.kt`:
 *  کدِ مشترک Context را برای تنظیمات (`getSharedPreferences`) و نامِ بسته می‌گیرد. در iOS یک
 *  نمونه هست (`IosContext`) که ریشهٔ آیفون با `LocalContext` می‌دهد.
 *
 *  فقط آنچه فایل‌های رسیده به iOS لازم دارند. `filesDir` ⇐ `Application Support/tohid` ِ همین
 *  برنامه (پشتیبانِ iCloud ِ دستگاه هم می‌گیردش، مثلِ پشتیبانِ خودکارِ اندروید)؛ `cacheDir` ⇐ `Caches`.
 *  `startActivity` فقط نشانی‌ها را باز می‌کند (‎Intent.kt‎)؛ سرویس‌های سیستم (لرزش، اعلان، چاپ…)
 *  هنوز نیستند ⇐ فایل‌هایشان در `ios/shared/ios-exclude.txt` (گامِ ۲).
 */
abstract class Context {
  open val applicationContext: Context get() = this
  open val packageName: String get() = "ir.vil3ntec.tohid"
  open val filesDir: File get() = appDir(NSApplicationSupportDirectory, "tohid")
  open val cacheDir: File get() = appDir(NSCachesDirectory, "tohid")
  open val noBackupFilesDir: File get() = appDir(NSCachesDirectory, "tohid-no-backup")
  open fun getDir(name: String, mode: Int): File = File(filesDir.parentFile, "app_$name").apply { mkdirs() }
  open fun getExternalFilesDir(type: String?): File? = null
  abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
  open fun deleteSharedPreferences(name: String): Boolean = false
  open val contentResolver: ContentResolver get() = ContentResolver
  /** سرویسِ سیستمیِ اندروید در iOS نیست ⇐ null (کدِ مشترک همیشه `as?` می‌گیرد) */
  open fun getSystemService(name: String): Any? = null
  open fun <T : Any> getSystemService(serviceClass: Class<T>): T? = null
  /** اندروید: برنامهٔ دیگر را باز می‌کند؛ نبودنش ⇐ ActivityNotFoundException (کدِ مشترک همین را می‌گیرد) */
  open fun startActivity(intent: Intent) {
    throw ActivityNotFoundException("iOS: ${intent.action}")
  }

  companion object {
    const val MODE_PRIVATE = 0
    const val CONNECTIVITY_SERVICE = "connectivity"
    const val VIBRATOR_SERVICE = "vibrator"
    const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
    const val NOTIFICATION_SERVICE = "notification"
    const val CLIPBOARD_SERVICE = "clipboard"
    const val INPUT_METHOD_SERVICE = "input_method"
  }
}

private fun appDir(kind: ULong, name: String): File {
  val base = NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull()?.toString() ?: "/tmp"
  return File(base, name).apply { mkdirs() }
}

/** در اندروید راهِ خواندنِ تنظیمِ سیستم است (`Settings.Global`)؛ در iOS فقط نشانه */
object ContentResolver

/** Context ِ یگانهٔ برنامهٔ آیفون — تنظیمات روی NSUserDefaults، هر پرونده یک suite ِ جدا */
object IosContext : Context() {
  override fun getSystemService(name: String): Any? = when (name) {
    CONNECTIVITY_SERVICE -> android.net.ConnectivityManager.ios
    else -> null
  }

  /** نشانیِ http/tel/mailto/sms ⇐ `openURL` ِ iOS؛ بقیه ⇐ ActivityNotFoundException (همان «برنامه‌ای نیست» ِ اندروید) */
  override fun startActivity(intent: Intent) {
    val uri = intent.data?.toString().orEmpty()
    val viewable = intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_SENDTO
    val url = if (viewable) NSURL.URLWithString(uri) else null
    if (url == null || !UIApplication.sharedApplication.canOpenURL(url)) throw ActivityNotFoundException("iOS: ${intent.action} $uri")
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
  }

  private val prefs = HashMap<String, SharedPreferences>()

  override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
    prefs.getOrPut(name) { UserDefaultsPreferences(name) }

  override fun deleteSharedPreferences(name: String): Boolean {
    prefs.remove(name)
    NSUserDefaults.standardUserDefaults.removePersistentDomainForName(UserDefaultsPreferences.suite(name))
    return true
  }
}
