package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiFailure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  لایهٔ شبکهٔ Sync v1 — چهار مسیر، و بس.
 *
 *  ⛔ **شناسهٔ حساب هیچ‌جا فرستاده نمی‌شود.** سرور آن را از خودِ توکن
 *  برمی‌دارد (`requireSyncAccount`)؛ فرستادنش یعنی ساختنِ راهی که
 *  می‌شد با آن در دفترِ دکانِ دیگری نوشت.
 */
class SyncV1Client(private val api: ApiClient) {

  data class PushResult(
    val results: List<OpResult>,
    val head: Long,
    val serverSchema: Int,
    val upgradeAvailable: Boolean,
  )

  data class PullResult(
    val ops: List<SyncOp>,
    val cursor: Long,
    val hasMore: Boolean,
    val serverSchema: Int,
  )

  /**
   *  @throws UpgradeRequired وقتی برنامه از سرور جلوتر است (۴۲۶). هیچ
   *          opی اعمال نشده و صف **دست نمی‌خورد**.
   */
  suspend fun push(deviceId: String, ops: List<SyncOp>, queued: Int): PushResult {
    val body = buildJsonObject {
      put("device_id", JsonPrimitive(deviceId))
      put("schema_version", JsonPrimitive(LedgerDiff.SCHEMA_VERSION))
      put("queued", JsonPrimitive(queued))
      put("ops", JsonArray(ops.map { it.toJson() }))
    }
    val res = try {
      api.post(ApiEndpoints.SyncV1.PUSH, body)
    } catch (failure: ApiFailure) {
      if (failure.status == 426) throw UpgradeRequired(failure.userMessage)
      throw failure
    }
    val results = (res["results"] as? JsonArray).orEmpty().mapNotNull { e ->
      val o = e as? JsonObject ?: return@mapNotNull null
      OpResult(
        opId = (o["op_id"] as? JsonPrimitive)?.content.orEmpty(),
        status = (o["status"] as? JsonPrimitive)?.content.orEmpty(),
        reason = (o["reason"] as? JsonPrimitive)?.content.orEmpty(),
      )
    }
    return PushResult(
      results = results,
      head = num(res, "cursor") ?: num(res, "head") ?: 0L,
      serverSchema = (num(res, "schema_version") ?: 0L).toInt(),
      upgradeAvailable = (res["upgrade_available"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true,
    )
  }

  suspend fun pull(deviceId: String, since: Long): PullResult {
    val res = api.get(ApiEndpoints.SyncV1.pull(deviceId, since))
    val ops = (res["ops"] as? JsonArray).orEmpty().mapNotNull { e ->
      (e as? JsonObject)?.let { SyncOp.fromJson(it) }
    }
    return PullResult(
      ops = ops,
      cursor = num(res, "cursor") ?: since,
      hasMore = (res["has_more"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true,
      serverSchema = (num(res, "schema_version") ?: 0L).toInt(),
    )
  }

  suspend fun snapshot(): JsonObject = api.get(ApiEndpoints.SyncV1.SNAPSHOT)

  suspend fun status(deviceId: String): JsonObject =
    api.get("${ApiEndpoints.SyncV1.STATUS}?device_id=$deviceId")

  private fun num(o: JsonObject, key: String): Long? =
    (o[key] as? JsonPrimitive)?.content?.toLongOrNull()

  private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

  /** سرور از برنامه عقب‌تر است — opها نگه داشته می‌شوند تا سرور به‌روز شود. */
  class UpgradeRequired(val reason: String) : Exception(reason)
}
