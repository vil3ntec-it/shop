package ir.vil3ntec.tohid.core.hub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  سمتِ دستگاه: وقتی سرور نمی‌رسد، همان بدنهٔ `pushOnce` به هاب (`docs/SYN-09-HUB-fa.md` §۴).
 *
 *  ⛔ پاسخِ هاب هرگز «انجام‌شده» نیست: `hub_queued`/`hub_settled` یعنی «هنوز در انتظار»؛ موتور هیچ ورودی‌ای
 *     را از صف برنمی‌دارد، `lastOkAt` و `attempt` را عوض نمی‌کند و همان شکستِ «به سرور نرسیدیم» را می‌نویسد.
 *     op فقط با پاسخِ خودِ سرور از صفِ دستگاه بیرون می‌رود.
 *  ⛔ پاسخی که وضعیتِ سرور (`applied`…) بگوید از هاب پذیرفته نیست (`hub_bad_reply`).
 */
fun interface HubRelayPort {
  suspend fun relay(body: JsonObject): HubRelay.Outcome
}

object HubRelay {

  val HUB_STATUSES: Set<String> = setOf("hub_queued", "hub_settled")

  /**
   *  @param pending opهایی که هاب نگه داشت (یا پیش‌تر فرستاده بود) — فقط برای شمارش؛ هنوز «در انتظار»
   *  @param refused کدِ ردِ هاب، یا خالی
   *  @param why چرا اصلاً به هاب نرفت (`HubRoute.Why`)؛ `null` یعنی رفت
   */
  data class Outcome(val pending: Int = 0, val refused: String = "", val why: HubRoute.Why? = null)

  private val json = Json { ignoreUnknownKeys = true }

  fun opIdsOf(body: JsonObject): List<String> = (body["ops"] as? JsonArray).orEmpty().mapNotNull { o ->
    ((o as? JsonObject)?.let { it["op_id"] ?: it["opId"] } as? JsonPrimitive)?.content
  }

  fun interpret(status: Int, body: String?, sent: List<String>): Outcome {
    val root = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
    if (status != 200) {
      val code = (root?.get("error") as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() } ?: "hub_$status"
      return Outcome(refused = code)
    }
    if ((root?.get("hub") as? JsonPrimitive)?.content != "true") return Outcome(refused = "hub_bad_reply")
    val results = (root["results"] as? JsonArray) ?: return Outcome(refused = "hub_bad_reply")
    val byId = HashMap<String, String>()
    for (r in results) {
      val o = r as? JsonObject ?: continue
      val id = (o["op_id"] as? JsonPrimitive)?.content ?: continue
      byId[id] = (o["status"] as? JsonPrimitive)?.content.orEmpty()
    }
    if (byId.values.any { it !in HUB_STATUSES }) return Outcome(refused = "hub_bad_reply")
    return Outcome(pending = sent.count { byId[it] in HUB_STATUSES })
  }
}
