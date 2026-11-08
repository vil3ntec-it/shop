package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.perm.RemoteApproval
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** آنچه صندوق برای «پرسیدن از مدیر» می‌خواهد (`RemoteAsk`؛ آزمون‌ها جایگزینِ بی شبکه می‌گذارند) */
interface ApprovalRequestsApi {
  suspend fun config(): ApiResult<RemoteApproval.Config>
  suspend fun create(body: Map<String, Any?>): ApiResult<RemoteApproval.Request>
  suspend fun get(id: String): ApiResult<RemoteApproval.Request>
  suspend fun cancel(id: String): ApiResult<RemoteApproval.Request>
}

/**
 *  «پرسیدن از مدیر» — `/shop/approval-requests` (`server/src/routes/shop-approval-requests.js`، STF-02).
 *  قاعده‌ها و شکلِ پیام در `core/perm/RemoteApproval`؛ این‌جا فقط رفت‌وبرگشت.
 *  ⛔ پیش‌فرض خاموش: بی `shop.approval_requests` فقط `/config` جواب می‌دهد (`enabled: false`) و بقیه ۴۰۳.
 *  ⛔ سرور تنها داور است: ساختن، تأیید/رد، عمر و یک‌بارمصرف همه آن‌جا سنجیده می‌شوند.
 */
class ApprovalRequestsRepository(private val api: ApiClient) : ApprovalRequestsApi {

  override suspend fun config(): ApiResult<RemoteApproval.Config> = result { RemoteApproval.parseConfig(plain(api.get("$ROOT/config"))) }

  /** همان کلید ⇒ همان درخواست (بی‌خطرِ تکرار)؛ بدنه از `RemoteApproval.body` */
  override suspend fun create(body: Map<String, Any?>): ApiResult<RemoteApproval.Request> = result {
    val key = body["key"] as? String ?: error("bad body")
    RemoteApproval.parse(plain(api.postKeyed(ROOT, json(body), key))) ?: error("bad response")
  }

  override suspend fun get(id: String): ApiResult<RemoteApproval.Request> = result {
    RemoteApproval.parse(plain(api.get("$ROOT/$id"))) ?: error("bad response")
  }

  /** `open` = در انتظار و نگذشته (صاحب/مدیر همه، بقیه فقط خودشان) */
  suspend fun list(status: String = "open"): ApiResult<List<RemoteApproval.Request>> = result {
    RemoteApproval.parseList(plain(api.get("$ROOT?status=$status")))
  }

  suspend fun approve(id: String, note: String = ""): ApiResult<RemoteApproval.Request> = decide(id, "approve", note)

  suspend fun deny(id: String, note: String = ""): ApiResult<RemoteApproval.Request> = decide(id, "deny", note)

  override suspend fun cancel(id: String): ApiResult<RemoteApproval.Request> = result {
    RemoteApproval.parse(plain(api.post("$ROOT/$id/cancel"))) ?: error("bad response")
  }

  private suspend fun decide(id: String, verb: String, note: String): ApiResult<RemoteApproval.Request> = result {
    RemoteApproval.parse(plain(api.post("$ROOT/$id/$verb", buildJsonObject { if (note.isNotBlank()) put("note", note.trim()) })))
      ?: error("bad response")
  }

  companion object {
    const val ROOT = "/shop/approval-requests"

    /** JSON ⇒ درختِ ساده (Map/List/String/Long/Double/Boolean) برای `RemoteApproval.parse` */
    fun plain(e: JsonElement?): Map<*, *>? = value(e) as? Map<*, *>

    private fun value(e: JsonElement?): Any? = when (e) {
      null, JsonNull -> null
      is JsonObject -> e.mapValues { (_, v) -> value(v) }
      is JsonArray -> e.map { value(it) }
      is JsonPrimitive -> when {
        e.isString -> e.content
        e.booleanOrNull != null -> e.booleanOrNull
        e.longOrNull != null -> e.longOrNull
        else -> e.doubleOrNull
      }
    }

    fun json(m: Map<String, Any?>): JsonObject = buildJsonObject {
      m.forEach { (k, v) ->
        when (v) {
          null -> Unit
          is Number -> put(k, JsonPrimitive(v))
          is Boolean -> put(k, JsonPrimitive(v))
          else -> put(k, JsonPrimitive(v.toString()))
        }
      }
    }
  }
}
