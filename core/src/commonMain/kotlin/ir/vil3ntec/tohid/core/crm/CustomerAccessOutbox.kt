package ir.vil3ntec.tohid.core.crm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 *  CRM-15 (د۱۸) — صفِ دیدنِ پروندهٔ مشتری برای نقشی که `auditLog` نمی‌نویسد (حسابدار) وقتی **بی‌اینترنت** است.
 *
 *  پیش‌تر `CustomerAccessNote` برای این نقش فقط درِ `app` ِ سرور را می‌زد و بی‌اینترنت «ننوشت» برمی‌گرداند — دیدن گم
 *  می‌شد. حالا همان دیدن با زمانِ خودش در این صف می‌ماند ([add]) و پس از هر دورِ همگام‌سازی دسته‌دسته ([batch]، [body])
 *  به `POST /api/shop/customer-access/offline` می‌رود؛ پاسخِ هر دیدن (`recorded`/`duplicate`/`rejected`) آن را از صف
 *  برمی‌دارد ([settle])، بی پاسخ می‌ماند. ⛔ پرچمِ خاموشِ سرور (`shop.customer_access_offline`) ⇒ ۴۰۴ ⇒ صف خالی می‌شود
 *  (همان امروز: دیدنِ بی‌اینترنت ثبت نمی‌شد).
 *
 *  قاعده‌ها: همان دیدن (مشتری + نوع) در [CustomerAccess.WINDOW_MS] ِ دیدنِ پیشینِ صف ⇒ هیچ (همان یکی کردنِ سرور)؛ دیدنِ
 *  کهنه‌تر از [MAX_AGE_MS] (سرور رد می‌کند) کنار؛ بیش از [CAP] ⇒ کهنه‌ترین‌ها کنار. بی دادهٔ شخصی: فقط شناسهٔ پرونده.
 */
object CustomerAccessOutbox {
  const val PATH = "/shop/customer-access/offline"
  const val BATCH = 50
  const val CAP = 500
  const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

  data class Note(val id: String, val customerId: String, val kind: CustomerAccess.Kind, val at: Long)

  /** صفِ تازه با این دیدن؛ تکرارِ پنجره، شناسهٔ نادرست یا دیدنِ کهنه ⇒ همان صف */
  fun add(queue: List<Note>, note: Note, now: Long): List<Note> {
    val fresh = queue.filter { now - it.at <= MAX_AGE_MS }
    val ok = CustomerAccess.noteBody(note.customerId, note.kind) != null && now - note.at <= MAX_AGE_MS
    if (!ok) return fresh
    if (fresh.any { it.customerId == note.customerId.trim() && it.kind == note.kind && note.at - it.at in 0 until CustomerAccess.WINDOW_MS }) return fresh
    return (fresh + note.copy(customerId = note.customerId.trim())).takeLast(CAP)
  }

  fun batch(queue: List<Note>): List<Note> = queue.take(BATCH)

  fun body(batch: List<Note>): JsonObject = JsonObject(
    mapOf(
      "notes" to JsonArray(
        batch.map {
          JsonObject(
            mapOf(
              "id" to JsonPrimitive(it.id), "customerId" to JsonPrimitive(it.customerId),
              "kind" to JsonPrimitive(it.kind.key), "at" to JsonPrimitive(it.at),
            ),
          )
        },
      ),
    ),
  )

  /** پاسخِ سرور ⇒ صفِ مانده: هر دیدنی که پاسخ گرفت (ثبت، تکراری یا رد) بیرون، بی پاسخ می‌ماند */
  fun settle(queue: List<Note>, response: JsonObject): List<Note> {
    val done = (response["results"] as? JsonArray).orEmpty().mapNotNullTo(HashSet()) { r ->
      val o = r as? JsonObject ?: return@mapNotNullTo null
      val st = (o["status"] as? JsonPrimitive)?.contentOrNull
      if (st !in setOf("recorded", "duplicate", "rejected")) null else (o["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
    }
    return queue.filterNot { it.id in done }
  }

  fun encode(queue: List<Note>): String = JsonArray(
    queue.map { JsonObject(mapOf("i" to JsonPrimitive(it.id), "c" to JsonPrimitive(it.customerId), "k" to JsonPrimitive(it.kind.key), "t" to JsonPrimitive(it.at))) },
  ).toString()

  /** متنِ خراب ⇒ صفِ خالی */
  fun decode(text: String): List<Note> = runCatching {
    Json.parseToJsonElement(text).jsonArray.mapNotNull { x ->
      val o = x.jsonObject
      val kind = CustomerAccess.Kind.of((o["k"] as? JsonPrimitive)?.contentOrNull.orEmpty()) ?: return@mapNotNull null
      Note(
        id = (o["i"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
        customerId = (o["c"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
        kind = kind, at = (o["t"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
      )
    }
  }.getOrDefault(emptyList())
}
