package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.core.sync.RestoreReplay
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
class SyncV1Client(
  private val api: ApiClient,
  /** `since` ِ push (#481، پرچمِ دکانِ `sync.cursor_from_since`)؛ null ⇒ نمی‌رود */
  private val since: () -> Long? = { null },
) {

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
    /** همان opها با `server_seq` و دستگاهِ سازنده — دفترچهٔ `ReplayBook.seen` (§۹.۵) */
    val seen: List<ReplayBook.Sent> = emptyList(),
    /** §۹.۵: دورانِ سرور — فقط با پرچمِ روشنِ `SYNC_EPOCH_ENABLED` (`V1Restore.pulled`) */
    val epoch: RestoreReplay.Epoch? = null,
    /** پرچمِ دکانِ `sync.keep_pending`: opها پیش از نشاندن از صافیِ «تغییرِ در صف» می‌گذرند (`LedgerDiff.keepPending`) */
    val keepPending: Boolean = false,
  )

  /**
   *  @throws UpgradeRequired وقتی برنامه از سرور جلوتر است (۴۲۶). هیچ
   *          opی اعمال نشده و صف **دست نمی‌خورد**.
   */
  suspend fun push(deviceId: String, ops: List<SyncOp>, queued: Int, caps: Boolean = true): PushResult {
    val body = buildJsonObject {
      put("device_id", JsonPrimitive(deviceId))
      put("schema_version", JsonPrimitive(LedgerDiff.SCHEMA_VERSION))
      put("queued", JsonPrimitive(queued))
      //  A00: دستهٔ دارای op ِ ساختِ پیش از A00 بی نشان (`LedgerDiff.capsFor`)
      if (caps) put("caps", LedgerDiff.capsJson())
      put("ops", JsonArray(ops.map { it.toJson() }))
      //  #481: «تا این‌جا را دیده بودم» — سرور فقط با پرچمِ دکان می‌خواندش
      since()?.let { put("since", JsonPrimitive(it)) }
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
        seq = num(o, "server_seq") ?: 0L,
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
    val ops = ArrayList<SyncOp>()
    val seen = ArrayList<ReplayBook.Sent>()
    for (e in (res["ops"] as? JsonArray).orEmpty()) {
      val o = e as? JsonObject ?: continue
      val op = SyncOp.fromJson(o) ?: continue
      ops += op
      val seq = num(o, "server_seq") ?: 0L
      val device = (o["device_id"] as? JsonPrimitive)?.content.orEmpty()
      if (seq > 0 && device.isNotEmpty()) seen += ReplayBook.Sent(op.opId, seq, op.toJson(), device)
    }
    return PullResult(
      ops = ops,
      cursor = num(res, "cursor") ?: since,
      hasMore = (res["has_more"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true,
      serverSchema = (num(res, "schema_version") ?: 0L).toInt(),
      seen = seen,
      epoch = RestoreReplay.Epoch.from(res["epoch"]),
      keepPending = (res["keep_pending"] as? JsonPrimitive)?.content == "true",
    )
  }

  /**
   *  از ردیف‌های تنهای دستگاه (نه در Snapshot) کدام روی سرور حذف‌شده است — پیش از ساختنِ
   *  دوبارهٔ دفتر پس از بازیابی (§۹.۵، `V1Restore.rebuild`). دسته‌های ۵۰۰تایی.
   *  ۴۰۴ (سرورِ بی این مسیر) ⇒ هیچ — ردیف می‌ماند و «درج» می‌رود (نگه داشتن بهتر از گم کردن).
   */
  suspend fun deleted(rows: List<RestoreMerge.Row>): Set<RestoreMerge.Row> {
    val out = HashSet<RestoreMerge.Row>()
    for (chunk in rows.distinct().chunked(DELETED_CHUNK)) {
      val body = buildJsonObject {
        put("rows", JsonArray(chunk.map { r -> buildJsonObject { put("table", JsonPrimitive(r.table)); put("row_id", JsonPrimitive(r.id)) } }))
      }
      val res = try {
        api.post(ApiEndpoints.SyncV1.DELETED, body)
      } catch (failure: ApiFailure) {
        if (failure.status == 404) return out
        throw failure
      }
      for (e in (res["deleted"] as? JsonArray).orEmpty()) {
        val o = e as? JsonObject ?: continue
        val t = (o["table"] as? JsonPrimitive)?.content ?: continue
        val id = (o["row_id"] as? JsonPrimitive)?.content ?: continue
        out += RestoreMerge.Row(t, id)
      }
    }
    return out
  }

  /**
   *  «دورانِ» سرور برای همین حساب (§۹.۵، `core/sync/RestoreReplay.kt`). فقط وقتی صدا زده می‌شود
   *  که pull ِ پیشین دوران آورد (`V1Restore.asks`) — پرچمِ خاموش ⇒ هرگز.
   *  ۴۰۴ (سرورِ کهنه یا خاموش‌کنِ `SYNC_EPOCH_ENABLED`) ⇒ null ⇒ `V1Restore.closed`.
   */
  suspend fun epoch(): RestoreReplay.Epoch? {
    val res = try {
      api.get(ApiEndpoints.SyncV1.EPOCH)
    } catch (failure: ApiFailure) {
      if (failure.status == 404) return null
      throw failure
    }
    return RestoreReplay.Epoch.from(res["epoch"])
  }

  suspend fun snapshot(): JsonObject = api.get(ApiEndpoints.SyncV1.SNAPSHOT)

  suspend fun status(deviceId: String): JsonObject =
    api.get("${ApiEndpoints.SyncV1.STATUS}?device_id=$deviceId")

  private fun num(o: JsonObject, key: String): Long? =
    (o[key] as? JsonPrimitive)?.content?.toLongOrNull()

  private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

  /** سرور از برنامه عقب‌تر است — opها نگه داشته می‌شوند تا سرور به‌روز شود. */
  class UpgradeRequired(val reason: String) : Exception(reason)

  companion object {
    /** سقفِ `POST /api/shop/sync-epoch/deleted` (`sync-epoch.MAX_ASK`) */
    const val DELETED_CHUNK = 500
  }
}
