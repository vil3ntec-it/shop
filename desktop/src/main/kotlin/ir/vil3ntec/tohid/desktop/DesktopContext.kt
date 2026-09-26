package ir.vil3ntec.tohid.desktop

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 *  تنها `Context`ِ برنامهٔ کامپیوتر.
 *
 *  پوشهٔ داده جای استانداردِ هر سیستم است و با به‌روزرسانی و نصبِ دوباره
 *  پاک نمی‌شود:
 *
 *  | سیستم | پوشه |
 *  |---|---|
 *  | ویندوز | `%LOCALAPPDATA%\Tohid` |
 *  | مک | `~/Library/Application Support/Tohid` |
 *  | لینوکس | `$XDG_DATA_HOME/tohid` یا `~/.local/share/tohid` |
 *
 *  `TOHID_DATA_DIR` فقط برای آزمون است.
 */
object DesktopContext : Context() {

  val root: File by lazy { resolveRoot().apply { mkdirs() } }

  override val filesDir: File get() = File(root, "files").apply { mkdirs() }
  override val cacheDir: File get() = File(root, "cache").apply { mkdirs() }

  private val prefsDir: File get() = File(root, "shared_prefs").apply { mkdirs() }
  private val prefs = HashMap<String, DesktopPrefs>()

  override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
    synchronized(prefs) { prefs.getOrPut(name) { DesktopPrefs(File(prefsDir, "$name.json")) } }

  override fun deleteSharedPreferences(name: String): Boolean = synchronized(prefs) {
    prefs.remove(name)?.edit()?.clear()?.commit()
    File(prefsDir, "$name.json").delete()
  }

  override val contentResolver = ContentResolver()
  override val packageManager = PackageManager()

  private val services = HashMap<String, Any?>()

  override fun getSystemService(name: String): Any? = synchronized(services) {
    services.getOrPut(name) {
      when (name) {
        CONNECTIVITY_SERVICE -> android.net.ConnectivityManager()
        USB_SERVICE -> android.hardware.usb.UsbManager()
        BLUETOOTH_SERVICE -> android.bluetooth.BluetoothManager()
        NOTIFICATION_SERVICE -> android.app.NotificationManager()
        ACTIVITY_SERVICE -> android.app.ActivityManager()
        CLIPBOARD_SERVICE -> android.content.ClipboardManager()
        //  لرزش، لوکیشن و چاپِ سیستمیِ اندروید روی کامپیوتر نیستند؛ همهٔ
        //  صدازننده‌ها `as?` یا `runCatching` دارند
        else -> null
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getSystemService(serviceClass: Class<T>): T? {
    val name = when (serviceClass) {
      android.app.NotificationManager::class.java -> NOTIFICATION_SERVICE
      android.app.ActivityManager::class.java -> ACTIVITY_SERVICE
      android.net.ConnectivityManager::class.java -> CONNECTIVITY_SERVICE
      android.hardware.usb.UsbManager::class.java -> USB_SERVICE
      android.content.ClipboardManager::class.java -> CLIPBOARD_SERVICE
      else -> return null
    }
    return getSystemService(name) as T?
  }

  /**
   *  `ACTION_VIEW` با نشانیِ وب ⇒ مرورگرِ سیستم؛ `tel:`/`smsto:`/`mailto:`
   *  ⇒ همان برنامه‌ای که سیستم برایش دارد (اسکایپ، واتساپِ دسکتاپ…).
   *  فایل ⇒ باز با برنامهٔ پیش‌فرضش.
   */
  override fun startActivity(intent: Intent) {
    val uri = intent.data?.toString() ?: return
    openExternal(uri)
  }

  fun openExternal(uri: String): Boolean = runCatching {
    val target = uri.replace(Regex("^geo:([^?]+)\\?q=.*$")) { m ->
      "https://www.google.com/maps/search/?api=1&query=${m.groupValues[1]}"
    }
    val d = Desktop.getDesktop()
    if (target.startsWith("file:")) d.open(File(URI(target)))
    else if (target.startsWith("mailto:") && d.isSupported(Desktop.Action.MAIL)) d.mail(URI(target))
    else d.browse(URI(target))
    true
  }.getOrElse {
    //  لینوکسِ بی‌میزکار: xdg-open
    runCatching { ProcessBuilder("xdg-open", uri).start(); true }.getOrDefault(false)
  }

  private fun resolveRoot(): File {
    System.getenv("TOHID_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home")
    return when {
      os.contains("win") -> File(System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local", "Tohid")
      os.contains("mac") -> File(home, "Library/Application Support/Tohid")
      else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "tohid")
    }
  }
}
