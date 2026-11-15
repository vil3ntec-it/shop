package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.V3InboxReply
import ir.vil3ntec.tohid.core.sync.V3InboxTransport
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 *  SYN-07 — لایهٔ HTTP ِ صندوقِ تعارضِ v3 روی همان `ApiClient` ِ برنامه (توکن، نوسازی و سرآیندها از همان‌جا؛ همان راهی
 *  که `HttpV3Transport` ِ موتور می‌رود). هر خطا ⇒ [V3InboxReply] با کدِ HTTP و `error.code` ِ سرور؛ پرتاب نمی‌کند (جز لغوِ
 *  کوروتین). منطقِ صف و سرنوشتِ پاسخ در `core/sync/V3InboxClient`.
 */
class V3InboxHttp(private val api: ApiClient) : V3InboxTransport {
  override suspend fun get(path: String): V3InboxReply = call { api.get(path) }

  override suspend fun post(path: String, body: JsonObject): V3InboxReply = call { api.post(path, body) }

  private inline fun call(block: () -> JsonObject): V3InboxReply = try {
    V3InboxReply(200, block())
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (e: Throwable) {
    val f = ApiFailure.fromException(e)
    //  شکستِ شبکه (بی پاسخ) کدِ HTTP ندارد ⇒ صفر = «نرسید»، تصمیم در صف می‌ماند
    V3InboxReply(if (f.retryable && f.status <= 0) 0 else f.status, null, f.code)
  }
}
