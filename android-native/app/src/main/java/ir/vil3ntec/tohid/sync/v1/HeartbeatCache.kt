package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.account.SubscriptionClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

  private val app = context.applicationContext

  /** همان بدنهٔ ذخیره‌شده، بی هیچ تغییر */
  fun raw(): JsonObject? = runCatching {
    val raw = prefs.getString("body", null) ?: return null
    json.parseToJsonElement(raw) as? JsonObject
  }.getOrNull()

  /**
   *  تپشِ کش‌شده با «اشتراک» ِ زمان‌دار از ساعتِ مطمئنِ همین دستگاه (`SubscriptionClock`؛ خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۲):
   *  روزِ مانده هر روز کم می‌شود و «فعال» پس از پایان خاموش است، حتی اگر دستگاه هرگز آنلاین نشود.
   */
  fun read(): JsonObject? = raw()?.let { withClock(it, now()) }

  private fun now(): Long = runCatching {
    ir.vil3ntec.tohid.sync.LicenseGuard.trustedNow(ir.vil3ntec.tohid.sync.SyncStore(app))
  }.getOrDefault(ir.vil3ntec.tohid.data.ReportBridge.nowMs())

  fun write(body: JsonObject) {
    prefs.edit().putString("body", body.toString()).putLong("at", System.currentTimeMillis()).apply()
  }

  /** تمدید با کد ⇐ تپشِ کهنهٔ «تمام‌شده» دور ریخته می‌شود تا تپشِ بعدی؛ قفل همان لحظه باز */
  fun forget() {
    prefs.edit().remove("body").apply()
  }

  companion object {
    fun cachedOf(sub: JsonObject): SubscriptionClock.Cached = SubscriptionClock.Cached(
      active = ApiJson.bool(sub, "active"), permanent = ApiJson.bool(sub, "permanent"), status = ApiJson.text(sub, "status"),
      endsAt = ApiJson.long(sub, "endsAt"), graceEndsAt = ApiJson.long(sub, "graceEndsAt"), daysLeft = ApiJson.int(sub, "daysLeft"),
    )

    /** `subscription` ِ تپش با `daysLeft`، `active`، `progress` و `color` از [now]؛ بقیهٔ بدنه دست‌نخورده */
    fun withClock(body: JsonObject, now: Long): JsonObject {
      val sub = body["subscription"] as? JsonObject ?: return body
      val c = cachedOf(sub)
      if (c.permanent || SubscriptionClock.end(c) <= 0) return body
      val days = SubscriptionClock.daysLeft(c, now)
      val active = SubscriptionClock.activeNow(c, now)
      val total = ApiJson.int(sub, "totalDays")
      val next = buildJsonObject {
        sub.forEach { (k, v) -> put(k, v) }
        put("daysLeft", days)
        put("active", active)
        if (total > 0) put("progress", (days * 100 / total).coerceIn(0, 100))
        put("color", if (!active) "red" else if (days > 30) "green" else "yellow")
      }
      return buildJsonObject { body.forEach { (k, v) -> put(k, if (k == "subscription") next else v) } }
    }
  }

  /** آیا برنامه باید فقط‌خواندنی شود — بندِ ۲۱٫۸ (با [read] ِ زمان‌دار: پایانِ گذشته ⇒ بله، آنلاین یا نه). */
  fun softLocked(): Boolean {
    val sub = read()?.get("subscription") as? JsonObject ?: return false
    if (ApiJson.bool(sub, "permanent")) return false
    if (ApiJson.bool(sub, "active")) return false
    return ApiJson.text(sub, "status") != "none"
  }
}
