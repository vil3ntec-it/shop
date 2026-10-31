package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ops.CrashReport
import ir.vil3ntec.tohid.core.ops.CrashReports
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  OPS-03 — صفِ گزارش‌های خطا روی دیسک (فایلِ `crash-reports.json` ِ `filesDir`) به JSON.
 *  فقط گزارشِ پوشانده‌شدهٔ `core/ops/CrashReports` این‌جا می‌آید. خواندنِ فایلِ خراب یا
 *  ناجور ⇒ صفِ خالی (هرگز خطا: این کد سرِ راهِ بالا آمدنِ برنامه است)؛ ردیفِ ناجور کنار.
 */
object CrashStore {

  private val json = Json { ignoreUnknownKeys = true }

  fun encode(list: List<CrashReport>): String = buildJsonArray {
    for (r in list) add(buildJsonObject {
      put("id", r.id)
      put("at", r.atMs)
      put("v", r.appVersion)
      put("message", r.message)
      put("stack", r.stack)
      put("sig", r.signature)
      put("count", r.count)
      put("shown", r.shown)
      put("sent", r.sent)
    })
  }.toString()

  fun decode(text: String?): List<CrashReport> {
    if (text.isNullOrBlank()) return emptyList()
    val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
    return arr.mapNotNull { e ->
      val o = e as? JsonObject ?: return@mapNotNull null
      fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
      val id = str("id")?.takeIf { CrashReports.ID.matches(it) } ?: return@mapNotNull null
      val at = (o["at"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
      CrashReport(
        id = id, atMs = at, appVersion = str("v").orEmpty(),
        //  آن‌چه از دیسک می‌آید دوباره پوشانده می‌شود (نسخه‌ای دیگر نوشته باشد)
        message = CrashReports.scrub(str("message").orEmpty(), CrashReports.MAX_MESSAGE),
        stack = CrashReports.scrub(str("stack").orEmpty(), CrashReports.MAX_STACK),
        signature = str("sig").orEmpty(),
        count = ((o["count"] as? JsonPrimitive)?.intOrNull ?: 1).coerceIn(1, 1_000_000),
        shown = (o["shown"] as? JsonPrimitive)?.booleanOrNull ?: false,
        sent = (o["sent"] as? JsonPrimitive)?.booleanOrNull ?: false,
      )
    }
  }

  /** بدنهٔ JSON ِ `POST /api/errors` */
  fun body(r: CrashReport): JsonObject = buildJsonObject {
    for ((k, v) in CrashReports.payload(r)) put(k, v)
  }
}
