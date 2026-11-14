package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 *  S44 (د۱۸) — صفِ نرفتهٔ همگام‌سازی درونِ پشتیبان (`pending-ops.json` ِ `BackupBundle`) و فرستادنش برای دستگاهِ گم‌شده.
 *
 *  پیش‌تر پشتیبان فقط دفتر را داشت و «بازیابی هرگز op نمی‌سازد» (S143)، پس فروشِ آفلاینِ گوشیِ گم‌شده فقط ردیفی بی‌صاحب در
 *  پشتیبان بود. حالا همان opهای صف — همان `op_id`، همان فیلدها، `$inc` هم — با شناسهٔ همگام‌سازیِ همان دستگاه در پشتیبان
 *  می‌مانند ([encode]) و صاحب پس از غیرفعال کردنِ دستگاه آن‌ها را دسته‌دسته ([batches]، [body]) به
 *  `POST /api/shop/devices/:id/replay` می‌فرستد (سرور: `lost-device-replay.js`؛ پرچمِ خاموشِ `shop.lost_device_replay`).
 *  خالص؛ مرزِ سکو در `LostDevicePendingAndroid.kt`.
 */
object LostDevicePending {
  const val ENTRY = "pending-ops.json"
  const val BATCH = 500
  /** بیش از این، پشتیبان صف را نمی‌برد (فایلِ پشتیبان بزرگ نشود؛ همان سقفِ هشدارِ صف ۱۰ برابر) */
  const val MAX_OPS = 100_000

  data class Pending(val deviceId: String, val ops: List<JsonObject>)

  data class Counts(val applied: Int, val duplicate: Int, val rejected: Int)

  fun encode(deviceId: String, ops: List<JsonObject>): String? {
    if (deviceId.isBlank() || ops.isEmpty() || ops.size > MAX_OPS) return null
    return JsonObject(mapOf("v" to JsonPrimitive(1), "deviceId" to JsonPrimitive(deviceId), "ops" to JsonArray(ops))).toString()
  }

  /** متنِ قلم ⇒ صف؛ خراب یا نسخهٔ ناشناخته ⇒ null (بازیابیِ دفتر بی آن همان) */
  fun parse(text: String): Pending? = runCatching {
    val o = Json.parseToJsonElement(text).jsonObject
    if ((o["v"] as? JsonPrimitive)?.intOrNull != 1) return@runCatching null
    val device = (o["deviceId"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val ops = (o["ops"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    if (ops.isEmpty()) null else Pending(device, ops)
  }.getOrNull()

  fun batches(p: Pending): List<List<JsonObject>> = p.ops.chunked(BATCH)

  fun body(deviceId: String, ops: List<JsonObject>): JsonObject =
    JsonObject(mapOf("deviceId" to JsonPrimitive(deviceId), "ops" to JsonArray(ops)))

  /** پاسخِ سرور ⇒ شمارش‌ها؛ بی `counts` ⇒ همه رد */
  fun counts(o: JsonObject, sent: Int): Counts {
    val c = o["counts"] as? JsonObject ?: return Counts(0, 0, sent)
    fun n(k: String) = (c[k] as? JsonPrimitive)?.intOrNull ?: 0
    return Counts(n("applied"), n("duplicate"), n("rejected"))
  }

  operator fun Counts.plus(o: Counts) = Counts(applied + o.applied, duplicate + o.duplicate, rejected + o.rejected)

  fun ok(o: JsonObject): Boolean = (o["ok"] as? JsonPrimitive)?.booleanOrNull == true
}
