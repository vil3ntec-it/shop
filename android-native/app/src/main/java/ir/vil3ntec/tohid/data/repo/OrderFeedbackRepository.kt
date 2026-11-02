package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderFeedback
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** درهای کارمندِ «گزارش‌ها و نظرها» — جدا تا حالِ صفحه بی سرور آزموده شود */
interface OrderFeedbackSource {
  suspend fun issues(filter: String): ApiResult<List<OrderFeedback.Issue>>
  suspend fun reviews(filter: String): ApiResult<List<OrderFeedback.Review>>
  suspend fun resolve(issueId: String, reply: String): ApiResult<OrderFeedback.Issue>
  suspend fun moderate(reviewId: String, decision: String): ApiResult<OrderFeedback.Review>
  suspend fun photo(issueId: String, photoId: String): ApiResult<ByteArray>
}

/**
 *  گزارشِ مشکل (NTF-07) و نظرِ مشتری (NTF-09) — `/api/shop/order-feedback` (`server/src/routes/order-feedback.js`).
 *  پرچمِ خاموش ⇒ ۴۰۳ `issue_reports_disabled` / `reviews_disabled`؛ خواندن با `orders.view`، نوشتن با `orders.manage`.
 *  بایتِ عکس با توکنِ کارمند و همان `getBytes` ِ رسانهٔ چتِ دکان (هر دیدن در سابقهٔ سرور).
 */
class OrderFeedbackRepository(private val api: ApiClient) : OrderFeedbackSource {

  private fun path(p: String?): String = p ?: throw ApiFailure.InvalidResponse("id")

  override suspend fun issues(filter: String): ApiResult<List<OrderFeedback.Issue>> =
    result { OrderFeedback.issues(api.get(OrderFeedback.issuesPath(filter))) }

  override suspend fun reviews(filter: String): ApiResult<List<OrderFeedback.Review>> =
    result { OrderFeedback.reviews(api.get(OrderFeedback.reviewsPath(filter))) }

  override suspend fun resolve(issueId: String, reply: String): ApiResult<OrderFeedback.Issue> = result {
    val out = api.post(path(OrderFeedback.resolvePath(issueId)), buildJsonObject { put("reply", OrderFeedback.cleanReply(reply)) })
    OrderFeedback.issue(out["issue"] as? JsonObject) ?: throw ApiFailure.InvalidResponse("issue")
  }

  override suspend fun moderate(reviewId: String, decision: String): ApiResult<OrderFeedback.Review> = result {
    require(decision == OrderFeedback.APPROVE || decision == OrderFeedback.REJECT)
    val out = api.post(path(OrderFeedback.moderatePath(reviewId)), buildJsonObject { put("decision", decision) })
    OrderFeedback.review(out["review"] as? JsonObject) ?: throw ApiFailure.InvalidResponse("review")
  }

  override suspend fun photo(issueId: String, photoId: String): ApiResult<ByteArray> =
    result { api.getBytes(path(OrderFeedback.photoPath(issueId, photoId))) }
}
