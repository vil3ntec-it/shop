package ir.vil3ntec.tohid.core.orders

import ir.vil3ntec.tohid.core.returns.ReturnReasons
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  RET-11 — صندوقِ «درخواست‌های برگشت» ِ ویترین برای کارمندِ دکان (تصمیمِ ۱۳ «ب»). منطقِ خالصِ صفحه، commonMain؛ هر
 *  تصمیم با سرور (`server/src/routes/shop-return-requests.js`). پرچمِ خاموشِ `shop.storefront.return_requests` ⇒ ۴۰۳
 *  `return_requests_disabled` ⇒ هیچ‌چیز دیده نمی‌شود (همان قاعدهٔ `OrderFeedback`: فقط پاسخِ موفق بخش را نشان می‌دهد).
 *
 *  ⛔ پول و موجودی فقط از راهِ برگشتِ موجود: «پذیرفتن» ⇒ همان برگهٔ برگشت (`ReturnFlowSheet` ⇐ `ReturnFlow.post`) با
 *  اقلامِ پرشده و شناسهٔ سندِ ثابتِ [docIdOf] (دو دستگاه ⇒ یک سند، `ALREADY_POSTED`)؛ «ثبت شد» به سرور فقط با همان شناسه.
 */
object OrderReturnRequests {

  const val ROOT = "/shop/order-returns"
  const val OFF = "return_requests_disabled"

  /** همان `FILTERS` ِ `lib/order-return-requests-shop.js`؛ نخستین = پیش‌فرض */
  val FILTERS: List<String> = listOf("pending", "accepted", "completed", "rejected", "all")
  val STATUSES: List<String> = listOf("pending", "accepted", "completed", "rejected")

  /** همان `REASONS` ِ سرور، به ترتیبِ فهرستِ ویترین */
  val REASONS: List<String> = listOf("damaged", "wrong_item", "expired", "not_as_described", "changed_mind", "other")
  val METHODS: List<String> = listOf("drop_off", "courier")

  /** همان `NOTE` ِ سرور (۲ تا ۱۰۰۰ نویسه) برای «رد با دلیل» */
  const val NOTE_MIN = 2
  const val NOTE_MAX = 1000

  private val ID = Regex("^[0-9A-Z]{26}$")

  data class Photo(val id: String, val mime: String, val width: Int, val height: Int)

  /** یک قلمِ درخواست: `qtyMilli` ِ برگشتی و `boughtMilli` ِ خریده‌شده، به واحدِ همان قلمِ سفارش */
  data class Line(val lineNo: Int, val productId: String, val name: String, val unit: String, val qtyMilli: Long, val boughtMilli: Long)

  data class Request(
    val id: String,
    val orderId: String,
    val orderNo: Long,
    val status: String,
    val reason: String,
    val text: String,
    val method: String,
    val lines: List<Line>,
    val photos: List<Photo>,
    val decisionNote: String?,
    val createdAt: Long,
    val decidedAt: Long?,
    val completedAt: Long?,
    val returnDocId: String?,
  ) {
    val pending: Boolean get() = status == "pending"
    val accepted: Boolean get() = status == "accepted"
    val open: Boolean get() = pending || accepted

    /** شناسهٔ سندِ برگشتی که برنامه برای همین درخواست می‌نویسد */
    val docId: String get() = docIdOf(orderId, id)
  }

  /** همان `docIdOf` ِ سرور (`o-<سفارش>-rq-<درخواست>`)؛ کنارِ `o-<سفارش>-rf-<کلید>` ِ بازپرداخت (`OrderRefunds`) */
  fun docIdOf(orderId: String, requestId: String): String = "o-$orderId-rq-$requestId"

  fun validId(id: String): Boolean = ID.matches(id)

  fun listPath(filter: String): String = "$ROOT?status=${filter.takeIf { it in FILTERS } ?: FILTERS[0]}"

  /** شناسهٔ نادرست ⇒ null (هرگز نشانیِ ساخته از دادهٔ بد) */
  fun photoPath(requestId: String, photoId: String): String? =
    if (validId(requestId) && validId(photoId)) "$ROOT/$requestId/photos/$photoId" else null
  fun acceptPath(requestId: String): String? = if (validId(requestId)) "$ROOT/$requestId/accept" else null
  fun rejectPath(requestId: String): String? = if (validId(requestId)) "$ROOT/$requestId/reject" else null
  fun completePath(requestId: String): String? = if (validId(requestId)) "$ROOT/$requestId/complete" else null

  // ── خواندنِ پاسخ ──

  private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
  private fun long(o: JsonObject, k: String): Long? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

  private fun photo(o: JsonObject?): Photo? {
    o ?: return null
    val id = str(o, "id")?.takeIf(::validId) ?: return null
    return Photo(id, str(o, "mime").orEmpty(), (long(o, "width") ?: 0).toInt(), (long(o, "height") ?: 0).toInt())
  }

  private fun line(o: JsonObject?): Line? {
    o ?: return null
    val no = long(o, "lineNo")?.toInt()?.takeIf { it >= 0 } ?: return null
    val qty = long(o, "qtyMilli")?.takeIf { it > 0 } ?: return null
    return Line(no, str(o, "productId").orEmpty(), str(o, "name").orEmpty(), str(o, "unit").orEmpty(), qty, long(o, "boughtMilli") ?: 0)
  }

  /** یک درخواست؛ شناسه، سفارش، وضعیت یا قلمِ نادرست ⇒ null */
  fun request(o: JsonObject?): Request? {
    o ?: return null
    val id = str(o, "id")?.takeIf(::validId) ?: return null
    val orderId = str(o, "orderId")?.takeIf { it.isNotBlank() } ?: return null
    val status = str(o, "status")?.takeIf { it in STATUSES } ?: return null
    val lines = (o["lines"] as? JsonArray).orEmpty().mapNotNull { line(it as? JsonObject) }
    if (lines.isEmpty()) return null
    return Request(
      id = id, orderId = orderId, orderNo = long(o, "orderNo") ?: 0, status = status,
      reason = str(o, "reason")?.takeIf { it in REASONS } ?: "other", text = str(o, "text").orEmpty(),
      method = str(o, "method")?.takeIf { it in METHODS } ?: METHODS[0], lines = lines,
      photos = (o["photos"] as? JsonArray).orEmpty().mapNotNull { photo(it as? JsonObject) },
      decisionNote = str(o, "decisionNote")?.takeIf { it.isNotBlank() },
      createdAt = long(o, "createdAt") ?: 0, decidedAt = long(o, "decidedAt"), completedAt = long(o, "completedAt"),
      returnDocId = str(o, "returnDocId")?.takeIf { it.isNotBlank() },
    )
  }

  fun requests(o: JsonObject): List<Request> = (o["requests"] as? JsonArray).orEmpty().mapNotNull { request(it as? JsonObject) }

  /** شمارِ «در انتظار» ِ همان پاسخ (نشانِ دکمه) */
  fun waiting(o: JsonObject): Int = (long(o, "waiting") ?: 0).toInt().coerceAtLeast(0)

  // ── نوشتن ──

  fun cleanNote(raw: String): String = raw.trim()

  /** طول به نویسه، نه واحدِ UTF-16 (همان `[...s].length` ِ سرور) */
  fun noteOk(raw: String): Boolean = cleanNote(raw).count { !it.isLowSurrogate() } in NOTE_MIN..NOTE_MAX

  /**
   *  دلیلِ پیشنهادیِ برگهٔ برگشت: دلیلِ مشتری ⇒ همان ردیفِ فهرستِ پیش‌فرضِ برنامه (`ReturnReasons.DEFAULT`)، فقط اگر در
   *  فهرستِ همین دکان هست (`ReturnReasons.pick`)؛ وگرنه `null` و کارمند خودش برمی‌گزیند (دلیلِ دکان همیشه اجباری).
   */
  fun suggestedReason(code: String, shopReasons: List<String>): String? {
    val i = when (code) {
      "damaged" -> 0
      "wrong_item" -> 1
      "changed_mind" -> 2
      "expired" -> 3
      "not_as_described" -> 4
      else -> 5
    }
    return ReturnReasons.DEFAULT.getOrNull(i)?.let { ReturnReasons.pick(it, shopReasons) }
  }

  // ── حالِ صفحه ──

  /** `on`: null = هنوز پاسخی نیامده (پنهان)، true = سرور فهرست داد، false = سرور نداد (پنهان) */
  data class Board(
    val on: Boolean? = null,
    val filter: String = FILTERS[0],
    val list: List<Request> = emptyList(),
    val loaded: Boolean = false,
    val waiting: Int = 0,
  ) {
    val visible: Boolean get() = on == true
  }

  /** پاسخِ موفقِ `GET ?status=filter`؛ پاسخِ فیلترِ کهنه فقط شمارش را تازه می‌کند */
  fun loaded(b: Board, filter: String, list: List<Request>, waiting: Int): Board = b.copy(
    on = true, list = if (filter == b.filter) list else b.list, loaded = b.loaded || filter == b.filter, waiting = waiting,
  )

  /** فهرست نیامد: `refused` (۴۰۳/۴۰۴) ⇒ پنهان؛ وگرنه (گذرا) همان حالِ پیشین */
  fun failed(b: Board, refused: Boolean): Board = if (refused) b.copy(on = false, list = emptyList(), waiting = 0) else b

  /** فیلترِ تازه ⇒ فهرستِ خالی تا پاسخ برسد */
  fun filter(b: Board, filter: String): Board =
    if (filter !in FILTERS || filter == b.filter) b else b.copy(filter = filter, list = emptyList(), loaded = false)

  /** پس از پذیرفتن/رد/ثبت‌شد: بیرون از فیلتر ⇒ برداشته، وگرنه جایگزین؛ شمارِ «در انتظار» فقط اگر در انتظار بود */
  fun updated(b: Board, r: Request): Board {
    val before = b.list.firstOrNull { it.id == r.id }
    val keep = b.filter == "all" || b.filter == r.status
    val list = if (keep) b.list.map { if (it.id == r.id) r else it } else b.list.filterNot { it.id == r.id }
    val drop = before != null && before.pending && !r.pending
    return b.copy(list = list, waiting = if (drop) (b.waiting - 1).coerceAtLeast(0) else b.waiting)
  }

  /** نوشتن با کدِ «خاموش» ⇒ بخش پنهان (پرچم میانِ کار خاموش شد) */
  fun writeRefused(b: Board, code: String): Board = if (code == OFF) failed(b, refused = true) else b
}
