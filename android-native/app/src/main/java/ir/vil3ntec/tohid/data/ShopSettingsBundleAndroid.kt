package ir.vil3ntec.tohid.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 *  DATA-SAFETY — تنظیماتِ **دکان** در هر پشتیبانِ کامل (دستی، پوشه، ابریِ دوازده‌ساعته).
 *
 *  ── اشکال ────────────────────────────────────────────────────────────
 *  قاعده‌های دکان (`tohid-policy`: گردکردنِ نقد، کارمزدِ خودکار، قاعدهٔ برگشت، یادآوریِ قرض…)، شکلِ رسید
 *  (`tohid-receipt`) و بخش‌های روشن (`tohid-modules`) فقط در `SharedPreferences` ِ همان دستگاه بودند — نه در
 *  همگام‌سازی، نه در پشتیبان. گوشیِ تازه پس از ورود دفتر را از سرور می‌گرفت ولی همهٔ این‌ها پیش‌فرض می‌شدند.
 *
 *  ── حالا ─────────────────────────────────────────────────────────────
 *  `BackupBundle.write` همین سه را در `settings.json` ِ ZIP می‌گذارد (نوعِ هر مقدار کنارش)، پس با پشتیبانِ
 *  ابری به سرور هم می‌رسند. بازیابیِ همان ZIP ([stage] ⇐ [commit] پس از تأییدِ کاربر) فقط کلیدهایی را
 *  می‌نشاند که روی این دستگاه **هنوز تنظیم نشده‌اند**: گوشیِ تازه تنظیماتِ دکان را پس می‌گیرد و دستگاهی که
 *  تنظیمِ خودش را دارد هیچ تغییری نمی‌بیند (هیچ قاعدهٔ پولیِ موجودی بی‌صدا عوض نمی‌شود).
 *
 *  ⛔ فقط [SHOP_PREFS]: پشتیبانِ دست‌ساز نمی‌تواند نشست، توکن، سایهٔ همگام‌سازی یا قفل را بنویسد.
 *  ⛔ رمزِ کارمندان (`tohid-staff-pins`)، چاپگر و دستگاه‌های جفت‌شده، زبان و ظاهر عمداً در پشتیبان نیستند —
 *     فهرستِ کامل و دلیلِ هر کدام: `docs/DATA-SAFETY-fa.md` §۶.
 */
object ShopSettingsBundle {

  const val ENTRY = "settings.json"

  /** تنظیم‌هایی که مالِ دکان‌اند، نه این دستگاه */
  val SHOP_PREFS = listOf("tohid-policy", "tohid-receipt", "tohid-modules")

  /** بیش از این، فایلِ تنظیمات نیست */
  const val MAX_BYTES = 512 * 1024

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /** همهٔ [SHOP_PREFS] با نوعِ هر مقدار (`s`/`i`/`l`/`f`/`b`/`set`) — همان قالبِ `DesktopPrefs` */
  fun export(context: Context): String = buildJsonObject {
    for (name in SHOP_PREFS) {
      val all = runCatching { context.getSharedPreferences(name, Context.MODE_PRIVATE).all }.getOrNull().orEmpty()
      put(name, buildJsonObject { for ((k, v) in all) encode(v)?.let { put(k, it) } })
    }
  }.toString()

  private fun encode(v: Any?): JsonObject? {
    val (t, e) = when (v) {
      is String -> "s" to JsonPrimitive(v)
      is Int -> "i" to JsonPrimitive(v)
      is Long -> "l" to JsonPrimitive(v)
      is Float -> "f" to JsonPrimitive(v)
      is Boolean -> "b" to JsonPrimitive(v)
      is Set<*> -> "set" to JsonArray(v.filterIsInstance<String>().map { JsonPrimitive(it) })
      else -> return null
    }
    return buildJsonObject { put("t", JsonPrimitive(t)); put("v", e) }
  }

  /** پروندهٔ موقتِ بازیابی — تا تأییدِ کاربر هیچ تنظیمی نمی‌نشیند (همان قاعدهٔ عکس‌ها) */
  private fun staged(context: Context): File = File(context.cacheDir, "restore-settings.json")

  /** `BackupBundle.read`: تنظیماتِ ZIP (حداکثر [MAX_BYTES]) تا تأییدِ بازیابی کنار گذاشته می‌شود */
  fun stageFrom(context: Context, input: InputStream) {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    while (out.size() <= MAX_BYTES) {
      val n = input.read(buf, 0, buf.size)
      if (n <= 0) break
      out.write(buf, 0, n)
    }
    if (out.size() > MAX_BYTES) return
    runCatching { staged(context).writeText(out.toString(Charsets.UTF_8.name())) }
  }

  fun dropStaged(context: Context) { runCatching { staged(context).delete() } }

  /** پس از تأییدِ بازیابی (`BackupBundle.commitPhotos`) — @return چند کلید نشست */
  fun commit(context: Context): Int {
    val f = staged(context)
    if (!f.isFile) return 0
    val n = runCatching { apply(context, f.readText()) }.getOrDefault(0)
    f.delete()
    return n
  }

  /**
   *  @param onlyMissing فقط کلیدی که این دستگاه هنوز ندارد (پیش‌فرض و تنها راهِ بازیابی)
   *  @return چند کلید نوشته شد
   */
  fun apply(context: Context, text: String, onlyMissing: Boolean = true): Int {
    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return 0
    var written = 0
    for (name in SHOP_PREFS) {
      val entries = root[name] as? JsonObject ?: continue
      val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
      val edit = prefs.edit()
      var any = false
      for ((key, raw) in entries) {
        if (key.isBlank() || (onlyMissing && prefs.contains(key))) continue
        val o = raw as? JsonObject ?: continue
        val v = o["v"] ?: continue
        val ok = runCatching {
          when ((o["t"] as? JsonPrimitive)?.content) {
            "s" -> edit.putString(key, v.jsonPrimitive.content)
            "i" -> edit.putInt(key, v.jsonPrimitive.content.toInt())
            "l" -> edit.putLong(key, v.jsonPrimitive.content.toLong())
            "f" -> edit.putFloat(key, v.jsonPrimitive.content.toFloat())
            "b" -> edit.putBoolean(key, v.jsonPrimitive.booleanOrNull ?: error("bool"))
            "set" -> edit.putStringSet(key, (v as JsonArray).map { it.jsonPrimitive.content }.toSet())
            else -> error("type")
          }
        }.isSuccess
        if (ok) { written++; any = true }
      }
      if (any) edit.commit()
    }
    return written
  }
}
