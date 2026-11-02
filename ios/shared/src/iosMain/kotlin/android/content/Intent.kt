package android.content

import android.net.Uri
import ir.vil3ntec.tohid.ios.jvm.Class

/*
 *  shimِ iOS ِ `android.content.Intent` (همان شکلِ کامپیوتر). `IosContext.startActivity`:
 *  «نما» (`ACTION_VIEW`/`ACTION_DIAL`/`ACTION_SENDTO`) با نشانیِ http/tel/mailto/sms ⇐ `openURL` ِ
 *  خودِ iOS؛ اشتراک‌گذاری (`ACTION_SEND`، متن و/یا `EXTRA_STREAM`) ⇐ UIActivityViewController؛ برنامهٔ دیگر ⇐ نه.
 */
open class Intent(var action: String? = null, var data: Uri? = null) {
  var flags: Int = 0
  var `package`: String? = null
  var type: String? = null
  val extras: MutableMap<String, Any?> = linkedMapOf()

  constructor(action: String?) : this(action, null)

  /** «همین برنامه، این رده» (سرویس/گیرنده) — iOS هیچ‌کدام را اجرا نمی‌کند (‎Context.startService‎) */
  constructor(packageContext: Context, cls: Class<*>) : this(null as String?) {
    component = ComponentName(packageContext, cls)
  }

  var component: ComponentName? = null

  fun setPackage(name: String?): Intent = apply { `package` = name }
  fun addFlags(f: Int): Intent = apply { flags = flags or f }
  fun setDataAndType(uri: Uri?, mime: String?): Intent = apply { data = uri; type = mime }
  fun putExtra(key: String, value: String?): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: Int): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: Boolean): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: Long): Intent = apply { extras[key] = value }
  fun putExtra(key: String, value: Uri?): Intent = apply { extras[key] = value }
  fun getStringExtra(key: String): String? = extras[key] as? String
  fun removeExtra(key: String) { extras.remove(key) }
  fun putStringArrayListExtra(key: String, value: ArrayList<String>?): Intent = apply { extras[key] = value }
  @Suppress("UNCHECKED_CAST")
  fun getStringArrayListExtra(key: String): ArrayList<String>? = extras[key] as? ArrayList<String>
  fun getIntExtra(key: String, def: Int): Int = extras[key] as? Int ?: def
  fun getBooleanExtra(key: String, def: Boolean): Boolean = extras[key] as? Boolean ?: def
  fun hasExtra(key: String): Boolean = extras.containsKey(key)
  @Suppress("UNCHECKED_CAST")
  fun <T : Any> getParcelableExtra(key: String, clazz: Class<T>): T? = extras[key] as? T
  @Suppress("UNCHECKED_CAST")
  fun <T> getParcelableExtra(key: String): T? = extras[key] as? T

  /** در iOS هیچ برنامه‌ای «قصد» ِ اندروید را نمی‌گیرد */
  fun resolveActivity(pm: android.content.pm.PackageManager): ComponentName? = null

  companion object {
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_DIAL = "android.intent.action.DIAL"
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SENDTO = "android.intent.action.SENDTO"
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
    const val EXTRA_TEXT = "android.intent.extra.TEXT"
    const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
    const val EXTRA_STREAM = "android.intent.extra.STREAM"
    const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
    const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000
    const val FLAG_GRANT_READ_URI_PERMISSION = 0x00000001

    fun createChooser(target: Intent, title: CharSequence?): Intent = target
  }
}

data class ComponentName(val packageName: String, val className: String) {
  constructor(pkg: Context, cls: Class<*>) : this(pkg.packageName, cls.name)

  fun flattenToString(): String = "$packageName/$className"

  companion object {
    fun unflattenFromString(str: String): ComponentName? {
      val sep = str.indexOf('/')
      if (sep < 0 || sep + 1 >= str.length) return null
      val pkg = str.substring(0, sep)
      val cls = str.substring(sep + 1).let { if (it.startsWith(".")) pkg + it else it }
      return ComponentName(pkg, cls)
    }
  }
}

class ActivityNotFoundException(message: String? = null) : RuntimeException(message)
