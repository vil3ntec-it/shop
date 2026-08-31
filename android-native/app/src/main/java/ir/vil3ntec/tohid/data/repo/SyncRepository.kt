package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  همگام‌سازیِ دفترِ دکان و مجوزِ اشتراک.
 *
 *  این مخزن — برخلافِ بقیه — استثنا پرتاب می‌کند و `ApiResult` نمی‌دهد.
 *  عمدی است: صدازننده‌اش `Syncer` است که یک دورِ چندمرحله‌ای دارد و
 *  شکستِ هر مرحله باید همان‌جا کلِ دور را متوقف کند. `AutoSync` هم آن را
 *  در `runCatching` می‌گیرد و به کاربر نشان می‌دهد.
 *
 *  ── چرا «فرستادن» هیچ‌وقت دوباره فرستاده نمی‌شود ──────────────────
 *  `push` نوشتن است. اگر درخواست برود و پاسخش در راه گم شود، تلاشِ
 *  دوباره یعنی همان فروش دو بار روی سرور. سرور خودش با
 *  `client_operation_id` جلوی تکرار را می‌گیرد، ولی برنامه هم نباید
 *  کورکورانه دوباره بفرستد — پس `ApiClient.post` تلاشِ دوباره ندارد و
 *  فقط خواندن‌ها تکرار می‌شوند.
 *  ──────────────────────────────────────────────────────────────────
 */
class SyncRepository(private val api: ApiClient) {

  /**
   *  تعارض — تغییری که سرور قبول نکرد، همراه با آنچه واقعاً آنجا هست.
   *
   *  `reason` یکی از این دوتاست:
   *    • `stale` — نسخهٔ تازه‌تری روی سرور بود (شریک زودتر عوض کرده)
   *    • `delete_not_allowed` — شاگرد رکوردِ کسِ دیگری را حذف کرده بود
   *
   *  `record` نسخهٔ خودِ سرور است؛ با آن، گوشی می‌تواند خودش را اصلاح
   *  کند. `deleted` یعنی سرور آن رکورد را ندارد.
   */
  data class Conflict(
    val collection: String,
    val id: String,
    val reason: String,
    val deleted: Boolean,
    val record: JsonObject?,
  )

  data class PushResult(
    val applied: Int,
    val skipped: Int,
    val conflicts: List<Conflict>,
  )

  /** فرستادنِ تغییرهای محلی */
  suspend fun push(deviceId: String, changes: JsonArray, settings: JsonObject): PushResult {
    val body = api.post(
      ApiEndpoints.Sync.PUSH,
      buildJsonObject {
        put("deviceId", JsonPrimitive(deviceId))
        put("changes", changes)
        put("settings", settings)
      },
    )
    return PushResult(
      applied = ApiJson.long(body, "applied", 0).toInt(),
      skipped = ApiJson.long(body, "skipped", 0).toInt(),
      conflicts = (body["conflicts"] as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val collection = ApiJson.text(row, "collection").ifBlank { return@mapNotNull null }
        val id = ApiJson.text(row, "id").ifBlank { return@mapNotNull null }
        Conflict(
          collection = collection,
          id = id,
          reason = ApiJson.text(row, "reason").ifBlank { "unknown" },
          deleted = ApiJson.bool(row, "deleted"),
          record = row["data"] as? JsonObject,
        )
      },
    )
  }

  data class Page(
    val changes: JsonArray,
    val settings: JsonObject?,
    val rev: Long,
    val hasMore: Boolean,
  )

  /** گرفتنِ تغییرهای دیگران، صفحه‌به‌صفحه */
  suspend fun pull(since: Long, deviceId: String): Page {
    val body = api.get(
      ApiEndpoints.withQuery(ApiEndpoints.Sync.PULL, mapOf("since" to since, "deviceId" to deviceId))
    )
    return Page(
      changes = body["changes"] as? JsonArray ?: JsonArray(emptyList()),
      settings = body["settings"] as? JsonObject,
      rev = ApiJson.long(body, "rev", since),
      hasMore = ApiJson.bool(body, "hasMore"),
    )
  }

  /* ------------------------------ مجوزِ اشتراک ------------------------------ */

  /** کلیدِ عمومیِ سرور — با آن، امضای مجوز سنجیده می‌شود */
  suspend fun publicKey(): String =
    ApiJson.text(api.getPublic(ApiEndpoints.License.PUBLIC_KEY), "publicKey")
      .ifBlank { throw ApiFailure.InvalidResponse("کلید عمومی نیامد") }

  /** گرفتن یا تازه کردنِ مجوز برای همین دستگاه */
  suspend fun license(deviceUid: String, deviceName: String): JsonObject =
    api.post(
      ApiEndpoints.License.SYNC,
      buildJsonObject {
        put("device", buildJsonObject {
          put("uid", JsonPrimitive(deviceUid))
          put("name", JsonPrimitive(deviceName))
        })
      },
    )
}
