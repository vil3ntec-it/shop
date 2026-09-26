package android.content

import android.net.Uri

/**
 *  `Intent` روی کامپیوتر فقط یک پاکت است. `DesktopContext.startActivity`
 *  آن‌هایی را که معنا دارند اجرا می‌کند: بازکردنِ نشانی در مرورگر
 *  (`ACTION_VIEW`) و بقیه بی‌صدا نادیده گرفته می‌شوند.
 */
open class Intent(var action: String? = null, var data: Uri? = null) {
  var flags: Int = 0
  var `package`: String? = null
  var type: String? = null
  val extras: MutableMap<String, Any?> = linkedMapOf()

  constructor(action: String?) : this(action, null)

  fun setPackage(name: String?): Intent = apply { `package` = name }
  fun addFlags(f: Int): Intent = apply { flags = flags or f }
  fun setDataAndType(uri: Uri?, mime: String?): Intent = apply { data = uri; type = mime }
  fun putExtra(key: String, value: String?): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: Int): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: Boolean): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: Long): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: java.io.Serializable?): Intent = apply { extras[key] = value }
  fun getStringExtra(key: String): String? = extras[key] as? String
  @Suppress("UNCHECKED_CAST")
  fun getStringArrayListExtra(key: String): ArrayList<String>? = extras[key] as? ArrayList<String>
  fun resolveActivity(pm: android.content.pm.PackageManager): ComponentName? = null

  companion object {
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_DIAL = "android.intent.action.DIAL"
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SENDTO = "android.intent.action.SENDTO"
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val EXTRA_TEXT = "android.intent.extra.TEXT"
    const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
    const val EXTRA_STREAM = "android.intent.extra.STREAM"
    const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
    const val FLAG_GRANT_READ_URI_PERMISSION = 0x00000001
    fun createChooser(target: Intent, title: CharSequence?): Intent = target
  }
}

class ComponentName(val packageName: String, val className: String)
