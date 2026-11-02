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
 *  `startActivity` نشانی‌ها را باز می‌کند و `ACTION_SEND` را به برگهٔ اشتراکِ iOS می‌دهد (‎Intent.kt‎)؛
 *  سرویس‌های سیستم: اتصال، تخته‌برش و اعلان (UNUserNotificationCenter)؛ بقیه null.
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
  open val packageManager: android.content.pm.PackageManager get() = android.content.pm.PackageManager
  open val applicationInfo: android.content.pm.ApplicationInfo get() = android.content.pm.ApplicationInfo.ios
  /** سرویسِ سیستمیِ اندروید در iOS نیست ⇐ null (کدِ مشترک همیشه `as?` می‌گیرد) */
  open fun getSystemService(name: String): Any? = null
  open fun <T : Any> getSystemService(serviceClass: Class<T>): T? = null
  /** اندروید: برنامهٔ دیگر را باز می‌کند؛ نبودنش ⇐ ActivityNotFoundException (کدِ مشترک همین را می‌گیرد) */
  open fun startActivity(intent: Intent) {
    throw ActivityNotFoundException("iOS: ${intent.action}")
  }

  /** پخشِ سیستمیِ اندروید و سرویس در iOS نیست (‎BroadcastReceiver.kt‎، ‎android/app/Service.kt‎): ثبت و آغاز ⇐ هیچ */
  open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? = null
  open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int): Intent? = null
  open fun unregisterReceiver(receiver: BroadcastReceiver) {}
  open fun startService(service: Intent): ComponentName? = null
  open fun startForegroundService(service: Intent): ComponentName? = null
  open fun stopService(service: Intent): Boolean = false

  companion object {
    const val MODE_PRIVATE = 0
    const val CONNECTIVITY_SERVICE = "connectivity"
    const val VIBRATOR_SERVICE = "vibrator"
    const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
    const val NOTIFICATION_SERVICE = "notification"
    const val CLIPBOARD_SERVICE = "clipboard"
    const val INPUT_METHOD_SERVICE = "input_method"
    const val BLUETOOTH_SERVICE = "bluetooth"
    const val USB_SERVICE = "usb"
    const val LOCATION_SERVICE = "location"
    const val PRINT_SERVICE = "print"
    const val ACTIVITY_SERVICE = "activity"
  }
}

private fun appDir(kind: ULong, name: String): File {
  val base = NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull()?.toString() ?: "/tmp"
  return File(base, name).apply { mkdirs() }
}

/**
 *  در اندروید راهِ خواندنِ تنظیمِ سیستم (`Settings.Global`) و فایلِ برگزیده است. در iOS فقط نشانیِ
 *  `file://` (خروجیِ انتخابگرهای iOS ‎ios/IosPickers.kt‎ و `Uri.fromFile`) باز می‌شود؛ بقیه ⇐ FileNotFoundException.
 */
object ContentResolver {
  fun openInputStream(uri: android.net.Uri): java.io.InputStream? = java.io.FileInputStream(fileOf(uri))
  fun openOutputStream(uri: android.net.Uri): java.io.OutputStream? = openOutputStream(uri, "w")
  /** پروندهٔ «ذخیره به‌عنوان» (`CreateDocument`) ⇐ جریانی که با بسته شدن «ذخیره در Files» ِ iOS را باز می‌کند */
  fun openOutputStream(uri: android.net.Uri, mode: String): java.io.OutputStream? {
    val file = fileOf(uri)
    val append = mode.contains('a')
    return ir.vil3ntec.tohid.ios.IosPickers.exportStream(file, append) ?: java.io.FileOutputStream(file, append = append)
  }
  fun getType(uri: android.net.Uri): String? = null

  /**
   *  گامِ ۶: فقط ستونِ نامِ پرونده (`OpenableColumns.DISPLAY_NAME`) ِ نشانیِ `file://` — همان چیزی که `data/FileName.kt` ِ
   *  اندروید می‌پرسد (ورودِ گروهیِ عکس: «نامِ پرونده = بارکد یا نامِ کالا»). نشانیِ دیگر ⇐ null (همان «ردیفی نیست» ِ اندروید).
   */
  fun query(
    uri: android.net.Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?,
  ): android.database.Cursor? {
    val path = uri.path?.takeIf { uri.scheme == null || uri.scheme == "file" } ?: return null
    val cols = projection ?: arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    return android.database.Cursor.ofRow(cols, cols.map { if (it == android.provider.OpenableColumns.DISPLAY_NAME) File(path).name else null })
  }

  private fun fileOf(uri: android.net.Uri): File =
    uri.path?.let { File(it) } ?: throw java.io.FileNotFoundException("iOS: فقط file:// ($uri)")
}

/** Context ِ یگانهٔ برنامهٔ آیفون — تنظیمات روی NSUserDefaults، هر پرونده یک suite ِ جدا */
object IosContext : Context() {
  override fun getSystemService(name: String): Any? = when (name) {
    CONNECTIVITY_SERVICE -> android.net.ConnectivityManager.ios
    CLIPBOARD_SERVICE -> ClipboardManager.ios
    NOTIFICATION_SERVICE -> android.app.NotificationManager.ios
    PRINT_SERVICE -> android.print.PrintManager.ios
    else -> null
  }

  /**
   *  نشانیِ http/tel/mailto/sms ⇐ `openURL` ِ iOS؛ `ACTION_SEND` ⇐ برگهٔ اشتراکِ iOS (‎IosPickers.share‎)؛
   *  بقیه ⇐ ActivityNotFoundException (همان «برنامه‌ای نیست» ِ اندروید)
   */
  override fun startActivity(intent: Intent) {
    if (intent.action == Intent.ACTION_SEND) return ir.vil3ntec.tohid.ios.IosPickers.share(intent)
    //  «تنظیماتِ این برنامه» ِ اندروید ⇐ صفحهٔ همین برنامه در تنظیماتِ iOS (اجازه‌ها)
    if (intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS || intent.action == android.provider.Settings.ACTION_SETTINGS) {
      NSURL.URLWithString(platform.UIKit.UIApplicationOpenSettingsURLString)?.let {
        UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
        return
      }
    }
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
