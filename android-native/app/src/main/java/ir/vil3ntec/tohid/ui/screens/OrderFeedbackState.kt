package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import ir.vil3ntec.tohid.core.i18n.StrOrderFeedback
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.orders.OrderFeedback
import ir.vil3ntec.tohid.data.repo.OrderFeedbackRepository
import ir.vil3ntec.tohid.data.repo.OrderFeedbackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 *  حالِ «گزارش‌ها و نظرها» (NTF-07/09) برای `OrderFeedbackPanel`. همهٔ تصمیم‌ها با سرور؛ منطقِ خالص در
 *  `core/orders/OrderFeedback`. پرچم‌های خاموش (یا بی اجازه، یا سرورِ کهنه) ⇒ ۴۰۳/۴۰۴ ⇒ `board.visible` نادرست ⇒ هیچ‌چیز.
 */

/** «سرور گفت نه» (پرچم، اجازه یا درِ ناموجود) — نه خطای گذرای شبکه */
internal fun feedbackRefused(f: ApiFailure): Boolean = f is ApiFailure.Forbidden || f is ApiFailure.NotFound

internal fun feedbackKind(kind: String): String = when (kind) {
  "missing_item" -> StrOrderFeedback.kMissing
  "wrong_item" -> StrOrderFeedback.kWrong
  "damaged" -> StrOrderFeedback.kDamaged
  "quality" -> StrOrderFeedback.kQuality
  "late" -> StrOrderFeedback.kLate
  else -> StrOrderFeedback.kOther
}

internal fun issueFilterText(f: String): String = when (f) {
  "open" -> StrOrderFeedback.fOpen
  "resolved" -> StrOrderFeedback.fResolved
  else -> StrOrderFeedback.fAll
}

internal fun reviewFilterText(f: String): String = when (f) {
  "pending" -> StrOrderFeedback.fPending
  "approved" -> StrOrderFeedback.fApproved
  "rejected" -> StrOrderFeedback.fRejected
  else -> StrOrderFeedback.fAll
}

@Stable
internal class OrderFeedbackController(private val src: OrderFeedbackSource, private val scope: CoroutineScope) {
  var board by mutableStateOf(OrderFeedback.Board())
    private set

  /** شناسهٔ گزارش یا نظری که همین حالا نوشته می‌شود (یکی در هر لحظه) */
  var busy by mutableStateOf<String?>(null)
    private set

  /** پیامِ آخرین نوشتن: (موفق؟، متن) */
  var note by mutableStateOf<Pair<Boolean, String>?>(null)
    private set

  /** خطای گذرای فهرستِ برگهٔ باز (نشان داده می‌شود؛ بخش پنهان نمی‌شود) */
  var listError by mutableStateOf<String?>(null)
    private set

  /** عکس‌های رسیده، با شناسهٔ عکس؛ `failed` = نیامد (زدنِ دوباره) */
  val images = mutableStateMapOf<String, ImageBitmap>()
  val failed = mutableStateMapOf<String, Boolean>()

  /** کشفِ پرچم و شمارش: «باز» و «در انتظار»؛ برگهٔ دیگری باز است ⇒ همان هم */
  suspend fun refresh() {
    loadIssues(OrderFeedback.ISSUE_FILTERS[0])
    loadReviews(OrderFeedback.REVIEW_FILTERS[0])
    if (board.issuesOn == true && board.issueFilter != OrderFeedback.ISSUE_FILTERS[0]) loadIssues(board.issueFilter)
    if (board.reviewsOn == true && board.reviewFilter != OrderFeedback.REVIEW_FILTERS[0]) loadReviews(board.reviewFilter)
  }

  suspend fun loadIssues(filter: String) {
    src.issues(filter)
      .onSuccess { board = OrderFeedback.issuesLoaded(board, filter, it); if (filter == board.issueFilter) listError = null }
      .onFailure { f ->
        board = OrderFeedback.issuesFailed(board, feedbackRefused(f))
        if (!feedbackRefused(f) && filter == board.issueFilter) listError = f.userMessage
      }
  }

  suspend fun loadReviews(filter: String) {
    src.reviews(filter)
      .onSuccess { board = OrderFeedback.reviewsLoaded(board, filter, it); if (filter == board.reviewFilter) listError = null }
      .onFailure { f ->
        board = OrderFeedback.reviewsFailed(board, feedbackRefused(f))
        if (!feedbackRefused(f) && filter == board.reviewFilter) listError = f.userMessage
      }
  }

  fun showIssues(filter: String): Job? {
    val next = OrderFeedback.issueFilter(board, filter)
    if (next == board) return null
    board = next
    listError = null
    return scope.launch { loadIssues(filter) }
  }

  fun showReviews(filter: String): Job? {
    val next = OrderFeedback.reviewFilter(board, filter)
    if (next == board) return null
    board = next
    listError = null
    return scope.launch { loadReviews(filter) }
  }

  fun reload(): Job = scope.launch { refresh() }

  /** «رسیدگی شد» با پاسخ — فقط گزارشِ باز و پاسخِ ۲ تا ۱۰۰۰ نویسه؛ ۴۰۹ (پاسخِ دیگری از دستگاهِ دیگر) ⇒ فهرست از نو */
  fun resolve(issue: OrderFeedback.Issue, reply: String): Job? {
    if (busy != null || !issue.open || !OrderFeedback.replyOk(reply)) return null
    busy = issue.id
    note = null
    return scope.launch {
      try {
        src.resolve(issue.id, reply)
          .onSuccess { board = OrderFeedback.resolved(board, it); note = true to StrOrderFeedback.resolvedOk }
          .onFailure { f ->
            board = OrderFeedback.writeRefused(board, f.code)
            note = false to f.userMessage
            if (f is ApiFailure.Conflict) loadIssues(board.issueFilter)
          }
      } finally {
        busy = null
      }
    }
  }

  /** «تأیید» / «رد» — همان تصمیمِ کنونی ⇒ هیچ (سرور هم `unchanged` می‌دهد) */
  fun moderate(review: OrderFeedback.Review, decision: String): Job? {
    val to = when (decision) { OrderFeedback.APPROVE -> "approved"; OrderFeedback.REJECT -> "rejected"; else -> return null }
    if (busy != null || review.status == to) return null
    busy = review.id
    note = null
    return scope.launch {
      try {
        src.moderate(review.id, decision)
          .onSuccess { r ->
            board = OrderFeedback.moderated(board, r)
            note = true to (if (r.status == "approved") StrOrderFeedback.approvedOk else StrOrderFeedback.rejectedOk)
          }
          .onFailure { f -> board = OrderFeedback.writeRefused(board, f.code); note = false to f.userMessage }
      } finally {
        busy = null
      }
    }
  }

  /** بایتِ عکس با توکنِ کارمند، یک بار برای هر شناسه؛ `decode` همان رمزگشاییِ رسانهٔ چت (`feedbackImage`) */
  suspend fun photo(issueId: String, photoId: String, decode: (ByteArray) -> ImageBitmap?) {
    if (images.containsKey(photoId)) return
    failed.remove(photoId)
    val img = src.photo(issueId, photoId).valueOrNull()?.let(decode)
    if (img != null) images[photoId] = img else failed[photoId] = true
  }

  fun retryPhoto(issueId: String, photoId: String, decode: (ByteArray) -> ImageBitmap?): Job =
    scope.launch { photo(issueId, photoId, decode) }

  companion object {
    /** هر دو دقیقه، و هر بار که صفِ سفارش عوض شد (`OrdersLive.rev`) */
    const val POLL_MS = 120_000L
  }
}

@Composable
internal fun rememberOrderFeedback(api: ApiClient): OrderFeedbackController {
  val scope = rememberCoroutineScope()
  return remember(api) { OrderFeedbackController(OrderFeedbackRepository(api), scope) }
}
