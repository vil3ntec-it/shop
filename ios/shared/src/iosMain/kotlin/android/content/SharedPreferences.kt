package android.content

import ir.vil3ntec.tohid.ios.prefs.PrefTypes
import kotlinx.cinterop.toKString
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/*
 *  shimِ iOS ِ `android.content.SharedPreferences` — همان رابطِ `desktop/src/shim`. هر نامِ پرونده
 *  (همان ۱۵ نامِ اندروید) یک suite ِ NSUserDefaults است با پیشوندِ `tohid.prefs.` — کلیدها
 *  همان کلیدهای اندروید، پس انتقالِ داده میان دستگاه‌ها نامِ تازه نمی‌خواهد.
 *  ⛔ رمز و توکن این‌جا نمی‌آیند: NSUserDefaults رمزگذاری نمی‌شود — پروندهٔ «رمزدار» ِ اندروید
 *  (`EncryptedSharedPreferences`، مثلاً توکنِ ورود در `TokenStore`) روی کلیددان است (‎androidx/security/crypto‎، گامِ ۶).
 */
interface SharedPreferences {
  fun getString(key: String, defValue: String?): String?
  fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
  fun getInt(key: String, defValue: Int): Int
  fun getLong(key: String, defValue: Long): Long
  fun getFloat(key: String, defValue: Float): Float
  fun getBoolean(key: String, defValue: Boolean): Boolean
  fun contains(key: String): Boolean
  /** `getAll()` ِ جاوا — کدِ Kotlin ِ اندروید آن را `prefs.all` می‌خواند */
  val all: Map<String, *>
  fun edit(): Editor
  fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
  fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

  fun interface OnSharedPreferenceChangeListener {
    fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?)
  }

  interface Editor {
    fun putString(key: String, value: String?): Editor
    fun putStringSet(key: String, values: Set<String>?): Editor
    fun putInt(key: String, value: Int): Editor
    fun putLong(key: String, value: Long): Editor
    fun putFloat(key: String, value: Float): Editor
    fun putBoolean(key: String, value: Boolean): Editor
    fun remove(key: String): Editor
    fun clear(): Editor
    fun commit(): Boolean
    fun apply()
  }
}

/**
 *  @param declared نوعِ اعلام‌شدهٔ هر کلید (`b`، `s`، `i`، `l`، `f`، `set`) یا `null` — پیش‌فرض: تنظیم‌های دکان
 *    (`ShopSettingsKeys`)؛ فقط [all] از آن می‌خواند (‎commonMain/…/ios/prefs/PrefTypes.kt‎)
 */
internal class UserDefaultsPreferences(
  name: String,
  private val declared: (String) -> String? = { key -> declaredType(name, key) },
) : SharedPreferences {
  private val defaults = NSUserDefaults(suiteName = suite(name))
  private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

  override fun getString(key: String, defValue: String?): String? = defaults.stringForKey(key) ?: defValue
  override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
    defaults.stringArrayForKey(key)?.map { it.toString() }?.toSet() ?: defValues
  override fun getInt(key: String, defValue: Int): Int = if (contains(key)) defaults.integerForKey(key).toInt() else defValue
  override fun getLong(key: String, defValue: Long): Long = if (contains(key)) defaults.integerForKey(key) else defValue
  override fun getFloat(key: String, defValue: Float): Float = if (contains(key)) defaults.floatForKey(key) else defValue
  override fun getBoolean(key: String, defValue: Boolean): Boolean = if (contains(key)) defaults.boolForKey(key) else defValue
  override fun contains(key: String): Boolean = defaults.objectForKey(key) != null
  /**
   *  ⛔ DATA-SAFETY: همان نوع‌های `getAll()` ِ اندروید (`Boolean`، `Int`، `Long`، `Float`، `String`، `Set<String>`) — نه
   *  متنِ هر عدد (`stringValue` ِ پیشین). شرح و آزمون: ‎commonMain/…/ios/prefs/PrefTypes.kt‎، `PrefTypesJvmTest`،
   *  `DataSafetyIosTest`. (کلیدهای دامنهٔ سراسریِ سیستم که NSUserDefaults کنارِ suite می‌دهد مثلِ پیش می‌آیند؛ کدِ مشترک
   *  فقط کلیدهای خودش را می‌خواند.)
   */
  override val all: Map<String, *>
    get() = defaults.dictionaryRepresentation().entries.associate { (k, v) ->
      val key = k.toString()
      val type = declared(key)
      val value: Any? =
        if (v is NSNumber) PrefTypes.number(v.objCType?.toKString(), type, v.longLongValue, v.doubleValue, v.boolValue)
        else PrefTypes.typed(v, type)
      key to value
    }

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    listeners += listener
  }

  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    listeners -= listener
  }

  override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
    private val ops = ArrayList<Pair<String, Any?>>()
    private var wipe = false
    private fun op(key: String, value: Any?): SharedPreferences.Editor { ops += key to value; return this }
    override fun putString(key: String, value: String?) = op(key, value)
    override fun putStringSet(key: String, values: Set<String>?) = op(key, values?.toList())
    override fun putInt(key: String, value: Int) = op(key, value.toLong())
    override fun putLong(key: String, value: Long) = op(key, value)
    override fun putFloat(key: String, value: Float) = op(key, value)
    override fun putBoolean(key: String, value: Boolean) = op(key, value)
    override fun remove(key: String) = op(key, null)
    override fun clear(): SharedPreferences.Editor { wipe = true; return this }
    override fun commit(): Boolean {
      if (wipe) defaults.dictionaryRepresentation().keys.forEach { defaults.removeObjectForKey(it.toString()) }
      for ((k, v) in ops) {
        when (v) {
          null -> defaults.removeObjectForKey(k)
          is Long -> defaults.setInteger(v, k)
          is Float -> defaults.setFloat(v, k)
          is Boolean -> defaults.setBool(v, k)
          else -> defaults.setObject(v, k)
        }
      }
      val changed = ops.map { it.first }.distinct()
      ops.clear()
      for (key in changed) for (l in listeners.toList()) l.onSharedPreferenceChanged(this@UserDefaultsPreferences, key)
      return true
    }
    override fun apply() { commit() }
  }

  companion object {
    fun suite(name: String) = "tohid.prefs.$name"

    /** نوعِ اعلام‌شدهٔ تنظیم‌های دکان (`tohid-policy`، `tohid-receipt`، `tohid-modules`) — Int و Long را از هم جدا می‌کند */
    private val shopTypes: Map<String, String> by lazy {
      ir.vil3ntec.tohid.data.ShopSettingsKeys.KEYS.associate { it.id to it.type }
    }

    fun declaredType(store: String, key: String): String? = shopTypes["$store/$key"]
  }
}
