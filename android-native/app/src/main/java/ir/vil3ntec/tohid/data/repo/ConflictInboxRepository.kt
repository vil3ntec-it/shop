package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** آنچه صفحهٔ صندوق و نشانِ فاکتور از سرور می‌خواهند (آزمون‌ها جایگزینِ بی شبکه می‌گذارند) */
interface ConflictInboxApi {
  suspend fun list(status: String = ConflictInbox.OPEN): ApiResult<ConflictInbox.Page>
  suspend fun get(id: String): ApiResult<ConflictInbox.Item>
  suspend fun mine(): ApiResult<List<ConflictInbox.Mine>>
  suspend fun resubmit(id: String, note: String = ""): ApiResult<ConflictInboxRepository.Outcome>
  suspend fun dismiss(id: String, reason: String, fixedSaleId: String? = null): ApiResult<ConflictInboxRepository.Outcome>
}

/**
 *  صندوقِ سندهای ردشده — `/shop/conflict-inbox` (`server/src/routes/shop-conflict-inbox.js`). قاعده‌ها و شکلِ پیام در
 *  `core/sync/ConflictInbox`؛ این‌جا فقط رفت‌وبرگشت.
 *  ⛔ سرور تنها داور است: «دوباره ثبت» همان زنجیرهٔ همگام‌سازی با نقشِ تأییدکننده است و تکرارش (پاسخِ گم‌شده، دو لمس) همان
 *     پاسخ را می‌دهد (`duplicate`)، نه فروشِ دوم. سرورِ کهنه ⇒ ۴۰۴ (صفحه و نوار هیچ نشان نمی‌دهند).
 */
class ConflictInboxRepository(private val api: ApiClient) : ConflictInboxApi {

  /** نتیجهٔ یک کار: ردیفِ تازهٔ سرور و «پیش‌تر انجام شده بود» */
  data class Outcome(val item: ConflictInbox.Item, val duplicate: Boolean)

  override suspend fun list(status: String): ApiResult<ConflictInbox.Page> = result {
    ConflictInbox.parsePage(ApprovalRequestsRepository.plain(api.get("$ROOT?status=$status&limit=200")))
  }

  override suspend fun get(id: String): ApiResult<ConflictInbox.Item> = result {
    ConflictInbox.parseItem(ApprovalRequestsRepository.plain(api.get("$ROOT/${safe(id)}"))) ?: error("bad response")
  }

  override suspend fun mine(): ApiResult<List<ConflictInbox.Mine>> = result {
    ConflictInbox.parseMine(ApprovalRequestsRepository.plain(api.get("$ROOT/mine")))
  }

  override suspend fun resubmit(id: String, note: String): ApiResult<Outcome> = result {
    outcome(api.post("$ROOT/${safe(id)}/resubmit", buildJsonObject { if (note.isNotBlank()) put("note", note.trim().take(ConflictInbox.MAX_NOTE)) }))
  }

  override suspend fun dismiss(id: String, reason: String, fixedSaleId: String?): ApiResult<Outcome> = result {
    val body = ConflictInbox.dismissBody(reason, fixedSaleId) ?: error("empty reason")
    outcome(api.post("$ROOT/${safe(id)}/dismiss", ApprovalRequestsRepository.json(body)))
  }

  private fun outcome(o: kotlinx.serialization.json.JsonObject): Outcome {
    val m = ApprovalRequestsRepository.plain(o)
    val item = ConflictInbox.parseItem(m) ?: error("bad response")
    return Outcome(item, m?.get("duplicate") == true)
  }

  companion object {
    const val ROOT = ConflictInbox.ROOT

    /** شناسه فقط از سرور می‌آید؛ هر نویسهٔ دیگر ⇒ ۴۰۰ ِ خودِ برنامه، نه نشانیِ دیگر */
    private fun safe(id: String): String = id.takeIf { Regex("^[A-Za-z0-9_.-]{1,80}$").matches(it) } ?: error("bad id")

    /** دلیلِ «سرور دوباره هم نپذیرفت» از بدنهٔ خطا (`error.reason`) */
    fun rejectReason(f: ApiFailure): String? =
      (f.detail?.get("reason") as? JsonPrimitive)?.content?.takeIf { f.code == "resubmit_rejected" && it.isNotBlank() }
  }
}
