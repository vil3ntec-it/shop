package ir.vil3ntec.tohid.core.orders

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  گزارش‌های مشکلِ سفارش با عکس (NTF-07) و نظرهای مشتری با بازبینی (NTF-09) برای کارمندِ دکان. منطقِ خالصِ صفحه،
 *  commonMain؛ هر تصمیم با سرور (`server/src/routes/order-feedback.js`). پرچم‌های خاموشِ `shop.orders.issue_reports` و
 *  `shop.orders.reviews` ⇒ ۴۰۳ `issue_reports_disabled` / `reviews_disabled` ⇒ هیچ‌چیز دیده نمی‌شود.
 *  ⛔ هیچ‌چیز «روشن» فرض نمی‌شود: فقط پاسخِ موفقِ همان فهرست بخش را نشان می‌دهد؛ ۴۰۳/۴۰۴ (پرچم، اجازه یا سرورِ کهنه)
 *  پنهانش می‌کند و خطای گذرا (شبکه) همان حالِ پیشین را نگه می‌دارد.
 */
object OrderFeedback {

  const val ROOT = "/shop/order-feedback"
  const val ISSUES_OFF = "issue_reports_disabled"
  const val REVIEWS_OFF = "reviews_disabled"

  /** همان `status` ِ `GET /issues` و `GET /reviews` ِ سرور؛ نخستین = پیش‌فرض */
  val ISSUE_FILTERS: List<String> = listOf("open", "resolved", "all")
  val REVIEW_FILTERS: List<String> = listOf("pending", "approved", "rejected", "all")

  /** همان `KINDS` ِ `lib/order-issues.js` */
  val KINDS: List<String> = listOf("missing_item", "wrong_item", "damaged", "quality", "late", "other")

  /** همان `REPLY` ِ سرور (۲ تا ۱۰۰۰ نویسه) */
  const val REPLY_MIN = 2
  const val REPLY_MAX = 1000
  const val MAX_STARS = 5

  const val APPROVE = "approve"
  const val REJECT = "reject"

  private val ID = Regex("^[0-9A-Z]{26}$")

  data class Photo(val id: String, val mime: String, val width: Int, val height: Int)

  data class Issue(
    val id: String,
    val orderId: String,
    val orderNo: Long,
    val kind: String,
    val text: String,
    val status: String,
    val reply: String?,
    val photos: List<Photo>,
    val createdAt: Long,
    val resolvedAt: Long?,
  ) {
    val open: Boolean get() = status == "open"
  }

  data class Review(
    val id: String,
    val orderId: String,
    val orderNo: Long,
    val stars: Int,
    val comment: String,
    val status: String,
    val createdAt: Long,
    val moderatedAt: Long?,
  ) {
    val pending: Boolean get() = status == "pending"
  }

  fun validId(id: String): Boolean = ID.matches(id)

  fun issuesPath(filter: String): String = "$ROOT/issues?status=${filter.takeIf { it in ISSUE_FILTERS } ?: ISSUE_FILTERS[0]}"
  fun reviewsPath(filter: String): String = "$ROOT/reviews?status=${filter.takeIf { it in REVIEW_FILTERS } ?: REVIEW_FILTERS[0]}"

  /** شناسهٔ نادرست ⇒ null (هرگز نشانیِ ساخته از دادهٔ بد) */
  fun photoPath(issueId: String, photoId: String): String? =
    if (validId(issueId) && validId(photoId)) "$ROOT/issues/$issueId/photos/$photoId" else null
  fun resolvePath(issueId: String): String? = if (validId(issueId)) "$ROOT/issues/$issueId/resolve" else null
  fun moderatePath(reviewId: String): String? = if (validId(reviewId)) "$ROOT/reviews/$reviewId/moderate" else null

  // ── خواندنِ پاسخ ──

  private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
  private fun long(o: JsonObject, k: String): Long? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

  private fun photo(o: JsonObject?): Photo? {
    o ?: return null
    val id = str(o, "id")?.takeIf(::validId) ?: return null
    return Photo(id, str(o, "mime").orEmpty(), (long(o, "width") ?: 0).toInt(), (long(o, "height") ?: 0).toInt())
  }

  /** یک گزارش؛ شناسه، سفارش یا وضعیتِ نادرست ⇒ null */
  fun issue(o: JsonObject?): Issue? {
    o ?: return null
    val id = str(o, "id")?.takeIf(::validId) ?: return null
    val orderId = str(o, "orderId")?.takeIf { it.isNotBlank() } ?: return null
    val status = str(o, "status")?.takeIf { it == "open" || it == "resolved" } ?: return null
    return Issue(
      id = id, orderId = orderId, orderNo = long(o, "orderNo") ?: 0,
      kind = str(o, "kind")?.takeIf { it in KINDS } ?: "other",
      text = str(o, "text").orEmpty(), status = status, reply = str(o, "reply")?.takeIf { it.isNotBlank() },
      photos = (o["photos"] as? JsonArray).orEmpty().mapNotNull { photo(it as? JsonObject) },
      createdAt = long(o, "createdAt") ?: 0, resolvedAt = long(o, "resolvedAt"),
    )
  }

  fun issues(o: JsonObject): List<Issue> = (o["issues"] as? JsonArray).orEmpty().mapNotNull { issue(it as? JsonObject) }

  /** یک نظر؛ شناسه، ستارهٔ بیرون از ۱ تا ۵ یا وضعیتِ نادرست ⇒ null */
  fun review(o: JsonObject?): Review? {
    o ?: return null
    val id = str(o, "id")?.takeIf(::validId) ?: return null
    val orderId = str(o, "orderId")?.takeIf { it.isNotBlank() } ?: return null
    val stars = long(o, "stars")?.toInt()?.takeIf { it in 1..MAX_STARS } ?: return null
    val status = str(o, "status")?.takeIf { it in REVIEW_FILTERS && it != "all" } ?: return null
    return Review(
      id = id, orderId = orderId, orderNo = long(o, "orderNo") ?: 0, stars = stars, comment = str(o, "comment").orEmpty(),
      status = status, createdAt = long(o, "createdAt") ?: 0, moderatedAt = long(o, "moderatedAt"),
    )
  }

  fun reviews(o: JsonObject): List<Review> = (o["reviews"] as? JsonArray).orEmpty().mapNotNull { review(it as? JsonObject) }

  // ── نوشتن ──

  fun cleanReply(raw: String): String = raw.trim()

  /** طول به نویسه، نه واحدِ UTF-16 (همان `[...s].length` ِ سرور) */
  fun replyOk(raw: String): Boolean = cleanReply(raw).count { !it.isLowSurrogate() } in REPLY_MIN..REPLY_MAX

  /** «★★★★☆» — فقط نشانه؛ متنِ خوانا جدا (`StrOrderFeedback.stars`) */
  fun starLine(stars: Int): String {
    val n = stars.coerceIn(0, MAX_STARS)
    return "★".repeat(n) + "☆".repeat(MAX_STARS - n)
  }

  // ── حالِ صفحه ──

  /**
   *  `issuesOn` / `reviewsOn`: null = هنوز پاسخی نیامده (پنهان)، true = سرور فهرست داد، false = سرور نداد (پنهان).
   *  `openIssues` / `pendingReviews` از آخرین فهرستِ «باز» / «در انتظار».
   */
  data class Board(
    val issuesOn: Boolean? = null,
    val reviewsOn: Boolean? = null,
    val issueFilter: String = ISSUE_FILTERS[0],
    val reviewFilter: String = REVIEW_FILTERS[0],
    val issues: List<Issue> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val issuesLoaded: Boolean = false,
    val reviewsLoaded: Boolean = false,
    val openIssues: Int = 0,
    val pendingReviews: Int = 0,
  ) {
    val visible: Boolean get() = issuesOn == true || reviewsOn == true
    val waiting: Int get() = (if (issuesOn == true) openIssues else 0) + (if (reviewsOn == true) pendingReviews else 0)
  }

  /** پاسخِ موفقِ `GET /issues?status=filter`؛ پاسخِ فیلترِ کهنه فقط شمارش را تازه می‌کند */
  fun issuesLoaded(b: Board, filter: String, list: List<Issue>): Board = b.copy(
    issuesOn = true,
    issues = if (filter == b.issueFilter) list else b.issues,
    issuesLoaded = b.issuesLoaded || filter == b.issueFilter,
    openIssues = when (filter) { "open" -> list.size; "all" -> list.count { it.open }; else -> b.openIssues },
  )

  fun reviewsLoaded(b: Board, filter: String, list: List<Review>): Board = b.copy(
    reviewsOn = true,
    reviews = if (filter == b.reviewFilter) list else b.reviews,
    reviewsLoaded = b.reviewsLoaded || filter == b.reviewFilter,
    pendingReviews = when (filter) { "pending" -> list.size; "all" -> list.count { it.pending }; else -> b.pendingReviews },
  )

  /** فهرست نیامد: `refused` (۴۰۳/۴۰۴) ⇒ پنهان؛ وگرنه (گذرا) همان حالِ پیشین */
  fun issuesFailed(b: Board, refused: Boolean): Board = if (refused) b.copy(issuesOn = false, issues = emptyList(), openIssues = 0) else b
  fun reviewsFailed(b: Board, refused: Boolean): Board = if (refused) b.copy(reviewsOn = false, reviews = emptyList(), pendingReviews = 0) else b

  /** فیلترِ تازه ⇒ فهرستِ خالی تا پاسخ برسد */
  fun issueFilter(b: Board, filter: String): Board =
    if (filter !in ISSUE_FILTERS || filter == b.issueFilter) b else b.copy(issueFilter = filter, issues = emptyList(), issuesLoaded = false)
  fun reviewFilter(b: Board, filter: String): Board =
    if (filter !in REVIEW_FILTERS || filter == b.reviewFilter) b else b.copy(reviewFilter = filter, reviews = emptyList(), reviewsLoaded = false)

  /** پس از «رسیدگی شد» (یا پاسخِ `unchanged`): در فهرستِ «باز» برداشته، در بقیه جایگزین؛ شمارش فقط اگر باز بود */
  fun resolved(b: Board, updated: Issue): Board {
    val before = b.issues.firstOrNull { it.id == updated.id }
    val keep = b.issueFilter == "all" || b.issueFilter == updated.status
    val list = if (keep) b.issues.map { if (it.id == updated.id) updated else it } else b.issues.filterNot { it.id == updated.id }
    val drop = before != null && before.open && !updated.open
    return b.copy(issues = list, openIssues = if (drop) (b.openIssues - 1).coerceAtLeast(0) else b.openIssues)
  }

  /** پس از «تأیید» / «رد»: بیرون از فیلتر ⇒ برداشته؛ شمارشِ «در انتظار» فقط اگر در انتظار بود */
  fun moderated(b: Board, updated: Review): Board {
    val before = b.reviews.firstOrNull { it.id == updated.id }
    val keep = b.reviewFilter == "all" || b.reviewFilter == updated.status
    val list = if (keep) b.reviews.map { if (it.id == updated.id) updated else it } else b.reviews.filterNot { it.id == updated.id }
    val drop = before != null && before.pending && !updated.pending
    return b.copy(reviews = list, pendingReviews = if (drop) (b.pendingReviews - 1).coerceAtLeast(0) else b.pendingReviews)
  }

  /** نوشتن با کدِ «خاموش» ⇒ همان بخش پنهان (پرچم میانِ کار خاموش شد) */
  fun writeRefused(b: Board, code: String): Board = when (code) {
    ISSUES_OFF -> issuesFailed(b, refused = true)
    REVIEWS_OFF -> reviewsFailed(b, refused = true)
    else -> b
  }
}
