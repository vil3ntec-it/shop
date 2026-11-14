package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import ir.vil3ntec.tohid.core.i18n.StrReturnRequests
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.orders.OrderReturnRequests
import ir.vil3ntec.tohid.data.repo.ReturnRequestsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 *  حالِ «درخواست‌های برگشت» (RET-11) برای صفِ سفارش‌ها (`ui/v3/online/orders/ReturnRequestsAndroid.kt`). همهٔ تصمیم‌ها با
 *  سرور؛ منطقِ خالص در `core/orders/OrderReturnRequests`. پرچمِ خاموش (یا بی اجازه، یا سرورِ کهنه) ⇒ ۴۰۳/۴۰۴ ⇒ پنهان.
 *  ⛔ پول و موجودی این‌جا نیست: «پذیرفتن» فقط وضعیتِ سرور است و پس از آن همان برگهٔ برگشتِ موجود باز می‌شود.
 */
@Stable
internal class ReturnRequestsController(private val src: ReturnRequestsSource, private val scope: CoroutineScope) {
  var board by mutableStateOf(OrderReturnRequests.Board())
    private set

  /** شناسهٔ درخواستی که همین حالا نوشته می‌شود (یکی در هر لحظه) */
  var busy by mutableStateOf<String?>(null)
    private set

  /** پیامِ آخرین نوشتن: (موفق؟، متن) */
  var note by mutableStateOf<Pair<Boolean, String>?>(null)
    private set

  var listError by mutableStateOf<String?>(null)
    private set

  val images = mutableStateMapOf<String, ImageBitmap>()
  val failed = mutableStateMapOf<String, Boolean>()

  private fun refused(f: ApiFailure): Boolean = f is ApiFailure.Forbidden || f is ApiFailure.NotFound

  /** کشفِ پرچم و شمارش؛ فیلترِ دیگری باز است ⇒ همان هم */
  suspend fun refresh() {
    load(OrderReturnRequests.FILTERS[0])
    if (board.on == true && board.filter != OrderReturnRequests.FILTERS[0]) load(board.filter)
  }

  suspend fun load(filter: String) {
    src.list(filter)
      .onSuccess { (list, waiting) -> board = OrderReturnRequests.loaded(board, filter, list, waiting); if (filter == board.filter) listError = null }
      .onFailure { f ->
        board = OrderReturnRequests.failed(board, refused(f))
        if (!refused(f) && filter == board.filter) listError = f.userMessage
      }
  }

  fun show(filter: String): Job? {
    val next = OrderReturnRequests.filter(board, filter)
    if (next == board) return null
    board = next
    listError = null
    return scope.launch { load(filter) }
  }

  fun reload(): Job = scope.launch { refresh() }

  private fun write(r: OrderReturnRequests.Request, ok: String, call: suspend () -> ir.vil3ntec.tohid.core.net.ApiResult<OrderReturnRequests.Request>, then: (OrderReturnRequests.Request) -> Unit = {}): Job? {
    if (busy != null) return null
    busy = r.id
    note = null
    return scope.launch {
      try {
        call()
          .onSuccess { u -> board = OrderReturnRequests.updated(board, u); note = true to ok; then(u) }
          .onFailure { f ->
            board = OrderReturnRequests.writeRefused(board, f.code)
            note = false to f.userMessage
            if (f is ApiFailure.Conflict) load(board.filter)
          }
      } finally {
        busy = null
      }
    }
  }

  /** «پذیرفتن» (فقط در انتظار؛ دستگاهِ دیگر پیش‌تر پذیرفته ⇒ همان) و سپس `then` ⇒ برگهٔ برگشت */
  fun accept(r: OrderReturnRequests.Request, then: (OrderReturnRequests.Request) -> Unit): Job? =
    if (!r.pending) null else write(r, StrReturnRequests.accepted, { src.accept(r.id) }, then)

  /** «رد با دلیل» — فقط درخواستِ باز و دلیلِ ۲ تا ۱۰۰۰ نویسه */
  fun reject(r: OrderReturnRequests.Request, reason: String): Job? =
    if (!r.open || !OrderReturnRequests.noteOk(reason)) null else write(r, StrReturnRequests.rejected, { src.reject(r.id, reason) })

  /** «برگشت ثبت شد» — فقط پس از پذیرفتن و فقط با شناسهٔ سندِ همین درخواست */
  fun complete(r: OrderReturnRequests.Request, docId: String): Job? =
    if (!r.accepted || docId != r.docId) null else write(r, StrReturnRequests.completed, { src.complete(r.id, docId) })

  /** بایتِ عکس با توکنِ کارمند، یک بار برای هر شناسه؛ `decode` همان رمزگشاییِ رسانهٔ چت (`feedbackImage`) */
  suspend fun photo(requestId: String, photoId: String, decode: (ByteArray) -> ImageBitmap?) {
    if (images.containsKey(photoId)) return
    failed.remove(photoId)
    val img = src.photo(requestId, photoId).valueOrNull()?.let(decode)
    if (img != null) images[photoId] = img else failed[photoId] = true
  }

  companion object {
    /** هر دو دقیقه، و هر بار که صفِ سفارش عوض شد (`OrdersLive.rev`) */
    const val POLL_MS = 120_000L
  }
}
