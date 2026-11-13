package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderReturnRequests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** درهای کارمندِ «درخواست‌های برگشت» — جدا تا حالِ صفحه بی سرور آزموده شود */
interface ReturnRequestsSource {
  suspend fun list(filter: String): ApiResult<Pair<List<OrderReturnRequests.Request>, Int>>
  suspend fun accept(id: String): ApiResult<OrderReturnRequests.Request>
  suspend fun reject(id: String, note: String): ApiResult<OrderReturnRequests.Request>
  suspend fun complete(id: String, docId: String): ApiResult<OrderReturnRequests.Request>
  suspend fun photo(id: String, photoId: String): ApiResult<ByteArray>
}

/**
 *  RET-11 — `/api/shop/order-returns` (`server/src/routes/shop-return-requests.js`). پرچمِ خاموش ⇒ ۴۰۳
 *  `return_requests_disabled`؛ خواندن با `orders.view`، نوشتن با `orders.manage` و مجوزِ برگشت (سرور دوباره می‌سنجد).
 *  بایتِ عکس با توکنِ کارمند و همان `getBytes` ِ رسانهٔ چتِ دکان (هر دیدن در سابقهٔ سرور).
 */
class ReturnRequestsRepository(private val api: ApiClient) : ReturnRequestsSource {

  private fun path(p: String?): String = p ?: throw ApiFailure.InvalidResponse("id")
  private fun one(o: JsonObject): OrderReturnRequests.Request =
    OrderReturnRequests.request(o["request"] as? JsonObject) ?: throw ApiFailure.InvalidResponse("request")

  override suspend fun list(filter: String): ApiResult<Pair<List<OrderReturnRequests.Request>, Int>> = result {
    val o = api.get(OrderReturnRequests.listPath(filter))
    OrderReturnRequests.requests(o) to OrderReturnRequests.waiting(o)
  }

  override suspend fun accept(id: String): ApiResult<OrderReturnRequests.Request> =
    result { one(api.post(path(OrderReturnRequests.acceptPath(id)), buildJsonObject { })) }

  override suspend fun reject(id: String, note: String): ApiResult<OrderReturnRequests.Request> =
    result { one(api.post(path(OrderReturnRequests.rejectPath(id)), buildJsonObject { put("note", OrderReturnRequests.cleanNote(note)) })) }

  override suspend fun complete(id: String, docId: String): ApiResult<OrderReturnRequests.Request> =
    result { one(api.post(path(OrderReturnRequests.completePath(id)), buildJsonObject { put("returnDocId", docId) })) }

  override suspend fun photo(id: String, photoId: String): ApiResult<ByteArray> =
    result { api.getBytes(path(OrderReturnRequests.photoPath(id, photoId))) }
}
