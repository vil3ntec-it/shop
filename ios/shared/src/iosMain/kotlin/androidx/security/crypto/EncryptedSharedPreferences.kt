package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences
import ir.vil3ntec.tohid.ios.keychain.Keychain
import kotlinx.serialization.json.Json
import platform.Foundation.NSLock
import platform.Foundation.NSRecursiveLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/*
 *  shim ِ iOS ِ `androidx.security.crypto` (گامِ ۶) — `EncryptedSharedPreferences` روی کلیددانِ iOS (Keychain).
 *
 *  اندروید: هر کلید و مقدار با AES-SIV/AES-GCM زیرِ کلیدِ اصلیِ Keystore رمز می‌شود. آیفون: کلِ پرونده یک قلمِ
 *  کلیددان است (`prefs:<نامِ پرونده>`، JSON ِ کلید ⇐ مقدار) با همان قاعدهٔ `Keychain`: «پس از نخستین بازگشایی، فقط همین
 *  دستگاه» — رمزِ سخت‌افزاریِ خودِ iOS، نه در پشتیبانِ iCloud، نه انتقال به گوشیِ تازه. پس خودِ `core/net/TokenStore.kt` ِ
 *  اندروید (توکن‌ها، «سرورِ بعدی» با `stashTo`/`openFrom`، انتقالِ پروندهٔ قدیمی) روی آیفون همان است و جایگزینِ
 *  `TokenStoreIos` حذف شد. خطای کلیددان در خواندن ⇐ پروندهٔ خالی (همان «وارد نشده» ِ پیشینِ آیفون)؛ در نوشتن ⇐
 *  `commit() = false` (اندروید هم خطای نوشتن را به `apply` نمی‌دهد).
 */
object MasterKeys {
  /** فقط نام: کلیدِ اصلی در iOS خودِ کلیددان است */
  class Spec internal constructor()

  val AES256_GCM_SPEC: Spec = Spec()

  fun getOrCreate(spec: Spec): String = "_androidx_security_master_key_"
}

class EncryptedSharedPreferences private constructor() {
  enum class PrefKeyEncryptionScheme { AES256_SIV }
  enum class PrefValueEncryptionScheme { AES256_GCM }

  companion object {
    private val open = HashMap<String, KeychainPreferences>()
    private val openLock = NSLock()

    fun create(
      fileName: String,
      masterKeyAlias: String,
      context: Context,
      prefKeyEncryptionScheme: PrefKeyEncryptionScheme,
      prefValueEncryptionScheme: PrefValueEncryptionScheme,
    ): SharedPreferences {
      openLock.lock()
      try { return open.getOrPut(fileName) { KeychainPreferences(fileName) } } finally { openLock.unlock() }
    }
  }
}

/** یک پرونده = یک قلمِ کلیددان؛ خواندن از حافظه، نوشتن یک‌جا (`commit`) */
internal class KeychainPreferences(name: String) : SharedPreferences {
  private val account = "prefs:$name"
  private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
  private var cache: MutableMap<String, Any>? = null

  /*
   *  قفلِ خودِ همین پرونده (بازگشتی: `value` ⇐ `map` تودرتوست)، نه قفلِ سراسریِ `ios/jvm/Jvm.kt`. درونش فقط `Keychain`
   *  (قفلِ برگ) و JSON — هیچ‌گاه قفلِ سراسری؛ شنونده‌ها بیرون از قفل. پس خواندنِ توکن (`TokenStore`) روی رشتهٔ اصلی دیگر پشتِ
   *  دارندهٔ قفلِ سراسری (`ShopStore.update` ⇐ قفلِ `engine`) نمی‌ماند — بن‌بستِ ABBA با `runLock` ِ Compose (#1037، #1039).
   */
  private val lock = NSRecursiveLock()
  private inline fun <R> locked(block: () -> R): R {
    lock.lock()
    try { return block() } finally { lock.unlock() }
  }

  private fun map(): MutableMap<String, Any> = locked {
    cache ?: load().also { cache = it }
  }

  private fun load(): MutableMap<String, Any> {
    val raw = runCatching { Keychain.read(account) }.getOrNull() ?: return LinkedHashMap()
    return runCatching { decode(Json.parseToJsonElement(raw.decodeToString()).jsonObject) }.getOrElse { LinkedHashMap() }
  }

  private fun value(key: String): Any? = locked { map()[key] }

  override fun getString(key: String, defValue: String?): String? = value(key) as? String ?: defValue
  @Suppress("UNCHECKED_CAST")
  override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (value(key) as? Set<String>) ?: defValues
  override fun getInt(key: String, defValue: Int): Int = (value(key) as? Number)?.toInt() ?: defValue
  override fun getLong(key: String, defValue: Long): Long = (value(key) as? Number)?.toLong() ?: defValue
  override fun getFloat(key: String, defValue: Float): Float = (value(key) as? Number)?.toFloat() ?: defValue
  override fun getBoolean(key: String, defValue: Boolean): Boolean = value(key) as? Boolean ?: defValue
  override fun contains(key: String): Boolean = value(key) != null
  override val all: Map<String, *> get() = locked { HashMap(map()) }

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    locked { listeners += listener }
  }

  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    locked { listeners -= listener }
  }

  override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
    private val ops = ArrayList<Pair<String, Any?>>()
    private var wipe = false
    private fun op(key: String, value: Any?): SharedPreferences.Editor { ops += key to value; return this }
    override fun putString(key: String, value: String?) = op(key, value)
    override fun putStringSet(key: String, values: Set<String>?) = op(key, values?.toSet())
    override fun putInt(key: String, value: Int) = op(key, value.toLong())
    override fun putLong(key: String, value: Long) = op(key, value)
    override fun putFloat(key: String, value: Float) = op(key, value)
    override fun putBoolean(key: String, value: Boolean) = op(key, value)
    override fun remove(key: String) = op(key, null)
    override fun clear(): SharedPreferences.Editor { wipe = true; return this }

    override fun commit(): Boolean {
      val (ok, changed, heard) = locked {
        val next = LinkedHashMap(map())
        if (wipe) next.clear()
        for ((k, v) in ops) if (v == null) next.remove(k) else next[k] = v
        val saved = runCatching { Keychain.put(account, encode(next).toString().encodeToByteArray()) }.isSuccess
        if (saved) cache = next
        Triple(saved, ops.map { it.first }.distinct(), listeners.toList())
      }
      ops.clear()
      if (ok) for (key in changed) for (l in heard) l.onSharedPreferenceChanged(this@KeychainPreferences, key)
      return ok
    }

    override fun apply() { commit() }
  }

  /** رمزگذار/رمزگشای JSON ِ قلمِ کلیددان — `internal` برای آزمونِ `Phase6IosTest` */
  internal companion object {
    fun encode(map: Map<String, Any>): JsonObject = JsonObject(
      map.mapValues { (_, v) ->
        when (v) {
          is String -> JsonPrimitive(v)
          is Boolean -> JsonPrimitive(v)
          is Long -> JsonObject(mapOf("l" to JsonPrimitive(v)))
          is Float -> JsonObject(mapOf("f" to JsonPrimitive(v)))
          is Set<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
          else -> JsonPrimitive(v.toString())
        }
      },
    )

    fun decode(obj: JsonObject): MutableMap<String, Any> {
      val out = LinkedHashMap<String, Any>()
      for ((k, e) in obj) decodeOne(e)?.let { out[k] = it }
      return out
    }

    private fun decodeOne(e: JsonElement): Any? = when (e) {
      is JsonArray -> e.map { it.jsonPrimitive.content }.toSet()
      is JsonObject -> e["l"]?.jsonPrimitive?.longOrNull ?: e["f"]?.jsonPrimitive?.doubleOrNull?.toFloat()
      is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull
    }
  }
}
