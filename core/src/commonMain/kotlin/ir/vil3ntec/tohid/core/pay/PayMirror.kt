package ir.vil3ntec.tohid.core.pay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 *  PAY-07 (د۱۸) — آینهٔ وضعیتِ **بعدیِ** قصدِ حساب‌پیِ خودکار روی ردیفِ درگاهِ فاکتور.
 *
 *  ردیفِ درگاه (`verification = gateway`) را برنامه دستی عوض نمی‌کند ([PayStatus.manual]) — سرور مرجع است. ولی پس از
 *  بستنِ فروش سرور هنوز «نیازمند بررسی» (پرداختِ دیرِ تأییدنشده، S35) یا «بازپرداخت» (مدیر) می‌گذارد و دستگاه آن را
 *  نمی‌دید. حالا پس از هر دورِ همگام‌سازی برنامه `GET /api/shop/payments/mirror` را از cursor ِ خودش می‌خواند ([path]،
 *  [parse])، در حالتِ دستگاه ([State]: فاکتور ⇐ روش ⇐ وضعیت، تازه‌ترین برنده) می‌گذارد ([merge]) و صفحه‌ها همان را روی
 *  ردیفِ درگاه می‌نشانند ([overlay]).
 *
 *  ⛔ سندِ دفتر نمی‌سازد و فاکتور دست نمی‌خورد: آینه فقط نمایش است (هر دستگاه از سرور می‌خواند؛ کسی نمی‌تواند وضعیتِ درگاه
 *     را با سندِ دفتر جعل کند). X/Z و جمعِ پولِ دریافتی همان امروز (بازپرداختِ مدیرِ سامانه رویدادِ برگشتِ دستگاه نیست).
 *  ⛔ پرچمِ خاموشِ `shop.pay_status_mirror` ⇒ سرور ۴۰۴ ⇒ حالت خالی ⇒ [overlay] بایت‌به‌بایت همان امروز.
 */
object PayMirror {
  const val PATH = "/shop/payments/mirror"
  const val PAGE = 200
  private val CURSOR = Regex("^\\d{1,16}~[A-Za-z0-9_]{1,64}$")

  data class Entry(val intentId: String, val saleId: String, val method: String, val status: String, val needsReview: Boolean, val at: Long)

  data class Page(val entries: List<Entry>, val cursor: String, val more: Boolean)

  /** وضعیتِ آینه‌شدهٔ یک روشِ یک فاکتور، با زمانِ تغییرِ سرور (تازه‌ترین برنده) */
  data class Mirrored(val status: String, val at: Long)

  /** حالتِ دستگاه: cursor و فاکتور ⇐ روش ⇐ وضعیت */
  data class State(val cursor: String, val sales: Map<String, Map<String, Mirrored>>)

  /** وضعیتِ سرور ⇒ وضعیتِ ردیف؛ وضعیتی که فاکتورِ بسته را عوض نمی‌کند ⇒ null */
  fun statusOf(e: Entry): String? = when (e.status) {
    "refunded" -> PayStatus.REFUNDED
    "paid" -> if (e.needsReview) PayStatus.NEEDS_REVIEW else PayStatus.CONFIRMED
    else -> null
  }

  fun path(cursor: String): String =
    if (CURSOR.matches(cursor)) "$PATH?after=$cursor&limit=$PAGE" else "$PATH?limit=$PAGE"

  private fun str(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()

  /** پاسخِ سرور ⇒ صفحه؛ ردیفِ ناقص یا وضعیتِ ناشناخته کنار؛ بی فهرست ⇒ null */
  fun parse(o: JsonObject): Page? {
    val list = o["intents"] as? JsonArray ?: return null
    val entries = list.mapNotNull { x ->
      val r = x as? JsonObject ?: return@mapNotNull null
      val e = Entry(
        intentId = str(r, "id"), saleId = str(r, "refId"), method = str(r, "method"), status = str(r, "status"),
        needsReview = (r["needsReview"] as? JsonPrimitive)?.booleanOrNull == true,
        at = (r["updatedAt"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0L,
      )
      e.takeIf { it.intentId.isNotEmpty() && it.saleId.isNotEmpty() && it.method.isNotEmpty() && statusOf(it) != null }
    }
    val more = (o["more"] as? JsonPrimitive)?.booleanOrNull == true
    return Page(entries, str(o, "cursor"), more)
  }

  /** صفحهٔ تازه روی حالت: هر فاکتور/روش تازه‌ترین وضعیت (زمانِ سرور) */
  fun merge(current: Map<String, Map<String, Mirrored>>, page: List<Entry>): Map<String, Map<String, Mirrored>> {
    if (page.isEmpty()) return current
    val out = current.mapValues { it.value.toMutableMap() }.toMutableMap()
    for (e in page) {
      val st = statusOf(e) ?: continue
      val m = out.getOrPut(e.saleId) { mutableMapOf() }
      val had = m[e.method]
      if (had == null || had.at <= e.at) m[e.method] = Mirrored(st, e.at)
    }
    return out.filterValues { it.isNotEmpty() }.mapValues { it.value.toMap() }
  }

  /** فاکتور ⇐ روش ⇐ وضعیت (برای [overlay]) */
  fun view(sales: Map<String, Map<String, Mirrored>>): Map<String, Map<String, String>> =
    sales.mapValues { (_, m) -> m.mapValues { it.value.status } }

  /** وضعیتِ ردیف‌های **درگاهِ** فاکتور ⇐ آینهٔ سرور برای همان روش؛ ردیفِ دیگر و آینهٔ خالی دست نمی‌خورند */
  fun overlay(rows: List<PayStatus.Row>, statuses: List<String>, mirrored: Map<String, String>): List<String> {
    if (mirrored.isEmpty()) return statuses
    return statuses.mapIndexed { i, st ->
      val r = rows.getOrNull(i) ?: return@mapIndexed st
      if (r.verification in PayStatus.SERVER_VERIFIED) mirrored[r.method] ?: st else st
    }
  }

  fun encode(s: State): String = JsonObject(
    mapOf(
      "cursor" to JsonPrimitive(s.cursor),
      "sales" to JsonObject(s.sales.mapValues { (_, m) -> JsonObject(m.mapValues { (_, v) -> JsonObject(mapOf("s" to JsonPrimitive(v.status), "at" to JsonPrimitive(v.at))) }) }),
    ),
  ).toString()

  /** متنِ خراب ⇒ حالتِ خالی (دوباره از آغاز) */
  fun decode(text: String): State = runCatching {
    val o = Json.parseToJsonElement(text).jsonObject
    val sales = (o["sales"] as? JsonObject).orEmpty().mapValues { (_, v) ->
      (v as JsonObject).mapValues { (_, x) ->
        val r = x.jsonObject
        Mirrored(str(r, "s"), (r["at"] as JsonPrimitive).longOrNull ?: 0L)
      }
    }
    State(str(o, "cursor"), sales)
  }.getOrDefault(State("", emptyMap()))
}
