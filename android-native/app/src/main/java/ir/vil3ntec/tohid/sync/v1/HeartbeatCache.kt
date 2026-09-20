package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.serialization.json.JsonObject

/**
 *  تپشِ کش‌شده — تا «اشتراکِ من» آفلاین هم چیزی داشته باشد.
 *
 *  ⚠️ همان چیزی که نسخهٔ وب در `tohid-heartbeat-v1` می‌گذارد. دو طرف
 *  یک قرارداد دارند، پس رفتارشان هم یکی است.
 */
class HeartbeatCache(context: android.content.Context) {
  private val prefs = context.applicationContext
    .getSharedPreferences("tohid-heartbeat", android.content.Context.MODE_PRIVATE)
  private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

  fun read(): JsonObject? = runCatching {
    val raw = prefs.getString("body", null) ?: return null
    json.parseToJsonElement(raw) as? JsonObject
  }.getOrNull()

  fun write(body: JsonObject) {
    prefs.edit().putString("body", body.toString()).putLong("at", System.currentTimeMillis()).apply()
  }

  /** آیا برنامه باید فقط‌خواندنی شود — بندِ ۲۱٫۸. */
  fun softLocked(): Boolean {
    val sub = read()?.get("subscription") as? JsonObject ?: return false
    if (ApiJson.bool(sub, "permanent")) return false
    if (ApiJson.bool(sub, "active")) return false
    return ApiJson.text(sub, "status") != "none"
  }
}
