package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 *  SYN-07 — صفِ تصمیم‌های صندوقِ تعارضِ v3 (همان الگوی `core/crm/CustomerAccessOutbox`): صاحب/مدیر بی‌اینترنت هم تصمیم
 *  می‌گیرد؛ تصمیم روی دیسک می‌ماند ([add]) و هر بار که برنامه به سرور برسد ([V3InboxClient.flush]) یکی‌یکی می‌رود.
 *
 *  ⛔ هر تعارض **یک** تصمیم در صف دارد (تازه جای کهنه می‌نشیند) و هر تصمیم تا پاسخِ روشنِ سرور می‌ماند ([fate]):
 *     ۲۰۰ (و `duplicate` ِ تکرارِ همان تصمیم) ⇒ انجام؛ ۴۰۹ ِ `already_resolved` یا ۴۰۴ ِ `conflict_not_found` ⇒ «پیش‌تر
 *     بسته شده بود» (کسِ دیگری یا دستگاهِ دیگری)؛ ۴۰۰/۴۰۳ ⇒ «سرور نپذیرفت» — هر سه از صف بیرون. بی پاسخ، ۴۰۸، ۴۲۹،
 *     ۵xx، ۴۰۱ (نشست) و ۴۰۴ ِ مسیر (v3 ِ سرور خاموش) ⇒ می‌ماند. تکرار بی‌خطر است: سرور با قفلِ ردیف و opِ یکتای
 *     `srv.rv.<شناسه>` هرگز دو بار برنمی‌گرداند.
 *  ⛔ کهنه‌تر از [MAX_AGE_MS] یا بیش از [CAP] ⇒ کهنه‌ترین‌ها کنار (صف بی‌اندازه نمی‌شود).
 */
object V3InboxOutbox {
  const val CAP = 200
  const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

  data class Entry(val decision: V3Inbox.Decision, val at: Long, val tries: Int = 0) {
    val conflictId: String get() = decision.conflictId
  }

  enum class Fate { DONE, CLOSED, REFUSED, RETRY }

  /** صفِ تازه با این تصمیم؛ همان تعارض ⇒ جای قبلی */
  fun add(queue: List<Entry>, e: Entry, now: Long): List<Entry> {
    val fresh = queue.filter { now - it.at <= MAX_AGE_MS && it.conflictId != e.conflictId }
    return (fresh + e).takeLast(CAP)
  }

  /** کهنه‌تر از [MAX_AGE_MS] کنار (پیش از هر فرستادن) */
  fun prune(queue: List<Entry>, now: Long): List<Entry> = queue.filter { now - it.at <= MAX_AGE_MS }

  fun pending(queue: List<Entry>, conflictId: String): Entry? = queue.lastOrNull { it.conflictId == conflictId }

  fun path(e: Entry): String = "${V3Inbox.ROOT}/${e.conflictId}/resolve"

  fun body(e: Entry): JsonObject = JsonObject(
    buildMap {
      put("action", JsonPrimitive(e.decision.action))
      if (e.decision.action == V3Inbox.MERGE) put("fields", JsonArray(e.decision.fields.map(::JsonPrimitive)))
    },
  )

  /** پاسخِ سرور ⇒ سرنوشتِ تصمیم ([status] صفر = به سرور نرسید) */
  fun fate(status: Int, code: String): Fate = when {
    status in 200..299 -> Fate.DONE
    status == 409 && code == "already_resolved" -> Fate.CLOSED
    status == 404 && code == "conflict_not_found" -> Fate.CLOSED
    status <= 0 || status == 401 || status == 404 || status == 408 || status == 429 || status >= 500 -> Fate.RETRY
    else -> Fate.REFUSED
  }

  /** پس از پاسخ: فقط **همان** تصمیم (همان زمان) بیرون می‌رود؛ تصمیمِ تازه‌تری که در این میان آمد می‌ماند */
  fun settle(queue: List<Entry>, e: Entry, fate: Fate): List<Entry> = when (fate) {
    Fate.RETRY -> queue.map { if (it.conflictId == e.conflictId && it.at == e.at) it.copy(tries = it.tries + 1) else it }
    else -> queue.filterNot { it.conflictId == e.conflictId && it.at == e.at }
  }

  fun encode(queue: List<Entry>): String = JsonArray(
    queue.map {
      JsonObject(
        mapOf(
          "c" to JsonPrimitive(it.conflictId), "a" to JsonPrimitive(it.decision.action),
          "f" to JsonArray(it.decision.fields.map(::JsonPrimitive)), "t" to JsonPrimitive(it.at), "n" to JsonPrimitive(it.tries),
        ),
      )
    },
  ).toString()

  private val ACTIONS = setOf(V3Inbox.KEEP, V3Inbox.REVERT, V3Inbox.DISMISS, V3Inbox.MERGE)
  private val ID_RE = Regex("^[A-Za-z0-9_.-]{1,80}$")

  /** متنِ خراب ⇒ صفِ خالی؛ ردیفِ نادرست ⇒ کنار */
  fun decode(text: String?): List<Entry> = runCatching {
    if (text.isNullOrBlank()) return emptyList()
    Json.parseToJsonElement(text).jsonArray.mapNotNull { x ->
      val o = x.jsonObject
      val id = (o["c"] as? JsonPrimitive)?.contentOrNull?.takeIf(ID_RE::matches) ?: return@mapNotNull null
      val action = (o["a"] as? JsonPrimitive)?.contentOrNull?.takeIf { it in ACTIONS } ?: return@mapNotNull null
      val fields = (o["f"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
      if (action == V3Inbox.MERGE && fields.isEmpty()) return@mapNotNull null
      Entry(
        V3Inbox.Decision(id, action, fields),
        at = (o["t"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
        tries = (o["n"] as? JsonPrimitive)?.intOrNull ?: 0,
      )
    }
  }.getOrDefault(emptyList())
}
