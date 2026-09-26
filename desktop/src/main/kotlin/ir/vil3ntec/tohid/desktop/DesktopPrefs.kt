package ir.vil3ntec.tohid.desktop

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/**
 *  `SharedPreferences` روی دیسک — یک فایلِ JSON برای هر نام.
 *
 *  همان رفتارِ اندروید: خواندن از حافظه، `apply()` نوشتنِ پس‌زمینه و
 *  `commit()` نوشتنِ همان لحظه. نوشتن اتمی است (فایلِ موقت ⇒ جابه‌جایی)
 *  تا خاموش شدنِ ناگهانیِ کامپیوتر فایلِ نیمه‌نوشته جا نگذارد.
 *
 *  نوعِ هر مقدار کنارش نوشته می‌شود (`s`/`i`/`l`/`f`/`b`/`set`)، چون
 *  JSON میانِ Int و Long فرقی نمی‌گذارد و `getLong` روی مقدارِ Int در
 *  اندروید خطا می‌دهد — این‌جا هم همان قاعده.
 */
class DesktopPrefs(private val file: File) : SharedPreferences {

  private val map = LinkedHashMap<String, Any>()
  private val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

  init { load() }

  @Synchronized override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
  @Suppress("UNCHECKED_CAST")
  @Synchronized override fun getStringSet(key: String, defValues: Set<String>?) =
    (map[key] as? Set<String>)?.toSet() ?: defValues
  @Synchronized override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
  @Synchronized override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
  @Synchronized override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
  @Synchronized override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
  @Synchronized override fun contains(key: String) = map.containsKey(key)
  override val all: Map<String, *> @Synchronized get() = LinkedHashMap(map)

  override fun edit(): SharedPreferences.Editor = Editor()

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    synchronized(listeners) { listeners.add(listener) }
  }

  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    synchronized(listeners) { listeners.remove(listener) }
  }

  private inner class Editor : SharedPreferences.Editor {
    private val puts = LinkedHashMap<String, Any?>()
    private var wipe = false

    override fun putString(key: String, value: String?) = apply { puts[key] = value }
    override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values?.toSet() }
    override fun putInt(key: String, value: Int) = apply { puts[key] = value }
    override fun putLong(key: String, value: Long) = apply { puts[key] = value }
    override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
    override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
    override fun remove(key: String) = apply { puts[key] = null }
    override fun clear() = apply { wipe = true }

    private fun merge(): Pair<String, Set<String>> = synchronized(this@DesktopPrefs) {
      if (wipe) map.clear()
      for ((k, v) in puts) if (v == null) map.remove(k) else map[k] = v
      encode() to puts.keys.toSet()
    }

    override fun commit(): Boolean {
      val (text, keys) = merge()
      val ok = write(text)
      notify(keys)
      return ok
    }

    override fun apply() {
      val (text, keys) = merge()
      writer.execute { write(text) }
      notify(keys)
    }
  }

  private fun notify(keys: Set<String>) {
    val ls = synchronized(listeners) { listeners.toList() }
    if (ls.isEmpty()) return
    for (k in keys) for (l in ls) runCatching { l.onSharedPreferenceChanged(this, k) }
  }

  private fun encode(): String = buildJsonObject {
    for ((k, v) in map) {
      put(k, buildJsonObject {
        when (v) {
          is String -> { put("t", "s"); put("v", v) }
          is Int -> { put("t", "i"); put("v", v) }
          is Long -> { put("t", "l"); put("v", v) }
          is Float -> { put("t", "f"); put("v", v) }
          is Boolean -> { put("t", "b"); put("v", v) }
          is Set<*> -> { put("t", "set"); put("v", JsonArray(v.map { JsonPrimitive(it.toString()) })) }
        }
      })
    }
  }.toString()

  private val lock = Any()

  private fun write(text: String): Boolean = synchronized(lock) {
    runCatching {
      file.parentFile?.mkdirs()
      val tmp = File(file.parentFile, file.name + ".tmp")
      tmp.writeText(text, Charsets.UTF_8)
      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }.recoverCatching {
      val tmp = File(file.parentFile, file.name + ".tmp")
      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }.isSuccess
  }

  private fun load() {
    if (!file.isFile) return
    val root = runCatching { Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject }.getOrNull() ?: return
    for ((k, e) in root) {
      val o = e as? JsonObject ?: continue
      val t = o["t"]?.jsonPrimitive?.contentOrNull
      val v = o["v"] ?: continue
      val value: Any? = runCatching {
        when (t) {
          "s" -> v.jsonPrimitive.content
          "i" -> v.jsonPrimitive.content.toInt()
          "l" -> v.jsonPrimitive.content.toLong()
          "f" -> v.jsonPrimitive.content.toFloat()
          "b" -> v.jsonPrimitive.booleanOrNull
          "set" -> (v as JsonArray).map { it.jsonPrimitive.content }.toSet()
          else -> null
        }
      }.getOrNull()
      if (value != null) map[k] = value
    }
  }

  private companion object {
    /** یک رشته برای همهٔ نوشتن‌ها — ترتیبِ `apply`ها حفظ می‌شود */
    val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "prefs-writer").apply { isDaemon = true } }

    init {
      //  بستنِ برنامه نباید `apply()`ِ در صف را گم کند
      Runtime.getRuntime().addShutdownHook(Thread {
        writer.shutdown()
        runCatching { writer.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS) }
      })
    }
  }
}
