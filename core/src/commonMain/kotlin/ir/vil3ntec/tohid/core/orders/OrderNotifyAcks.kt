package ir.vil3ntec.tohid.core.orders

/*
 *  رسیدِ «باز شد» ِ اعلانِ سفارش — M6ِ قراردادِ دکان ⇐ بازار، v3 §۳۰.
 *
 *  ── قراردادِ سرور (`server/src/routes/order-notify.js`) ─────────────
 *  `GET  /api/shop/order-notify/pending` ⇒ `{notifications:[{id, orderId, orderNo, …}]}`
 *     — اعلان‌های بازنشده‌ای که سفارششان هنوز «تازه» است.
 *  `POST /api/shop/order-notify/:id/ack` با `{kind:'opened'}` ⇒ ردیفِ outbox
 *     `acked` با `ack_source = 'device'`؛ تکرار همان نتیجه (بی‌خطر).
 *  بی پرچمِ `shop.order_notify` ⇒ ۴۰۳ `order_notify_disabled`.
 *
 *  ── قاعدهٔ صف ───────────────────────────────────────────────────────
 *    • لمسِ اعلان همان لحظه در صف می‌نشیند (آفلاین هم)؛ هیچ لمسی گم نمی‌شود.
 *    • ۲xx ⇒ بیرون (کار انجام شد).
 *    • ۴۰۴ (اعلان نیست) یا ۴۰۰ (رسیدِ بدشکل) ⇒ بیرون؛ فرستادنِ دوباره همان را می‌گیرد.
 *    • بقیه (شبکه، ۵xx، ۴۰۱، ۴۰۳، ۴۲۹) ⇒ می‌ماند و با فاصلهٔ فزاینده دوباره.
 *    • رسیدِ کهنه‌تر از [TTL_MS] بیرون: سرور تا آن موقع سفارش را با وضعیتش بسته است.
 *  «نشان‌داده‌شده‌ها» ([AckState.shown]) نمی‌گذارند یک اعلان در هر دورِ پرسش دوباره بیاید.
 *
 *  منطقِ خالص، بی android.* و بی JSON — برنامهٔ مدیریت (اندروید/کامپیوتر/آیفون)
 *  همین را می‌گیرد و آزمونش (`OrderNotifyAcksTest`) بی دستگاه اجرا می‌شود.
 */

/**
 *  اعلانی که سرور می‌گوید هنوز باز نشده — فقط آن‌چه برای نشان دادن و لمس لازم است.
 *  [event]: `order_created` (سفارشِ تازه) یا `order_review` (M5: «سفارش به بررسی رفت»)؛ ناشناخته ⇒ همان «تازه».
 */
data class OrderNotice(
  val id: String,
  val orderId: String,
  val orderNo: Long,
  val event: String = OrderNotifyAcks.EVENT_CREATED,
) {
  val isReview: Boolean get() = event == OrderNotifyAcks.EVENT_REVIEW
}

/** یک لمسِ در صف. */
data class PendingAck(
  val notifId: String,
  val orderId: String,
  val tappedAt: Long,
  val attempts: Int = 0,
  val nextAt: Long = 0,
)

data class AckState(
  val pending: List<PendingAck> = emptyList(),
  /** اعلان‌هایی که روی این دستگاه نشان داده شده‌اند (به ترتیب، تازه‌ترین آخر) */
  val shown: List<String> = emptyList(),
)

object OrderNotifyAcks {

  /** شناسهٔ اعلان (ULID ِ سرور) و سفارش — جداکننده‌های قالبِ ذخیره هرگز در آن نیست */
  private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")
  const val MAX_PENDING = 200
  const val MAX_SHOWN = 300
  const val MAX_BATCH = 20
  const val BASE_BACKOFF_MS = 15_000L
  const val MAX_BACKOFF_MS = 30 * 60_000L
  const val TTL_MS = 7L * 24 * 3600 * 1000

  enum class Outcome { ACKED, GONE, RETRY }

  /** همان `EVENT` و `REVIEW_EVENT` ِ `server/src/lib/order-notify.js` */
  const val EVENT_CREATED = "order_created"
  const val EVENT_REVIEW = "order_review"

  fun validId(s: String): Boolean = ID.matches(s)

  /**
   *  عنوان و متنِ اعلان بر اساسِ رویداد — همان `messageFor` ِ سرور، بی دادهٔ مشتری (§۳۰.۱ بندِ ۴).
   *  `order_review` «در بررسی» است، نه «سفارشِ تازه».
   */
  fun label(n: OrderNotice): Pair<String, String> {
    val known = n.orderNo > 0
    //  عنوانِ «در بررسی» همان `StockHold.notice` ِ نگهبان است
    return if (n.isReview) {
      (if (known) "سفارش به بررسی رفت — #${n.orderNo}" else "سفارش به بررسی رفت") to
        "فروشِ حضوری کالای نگه‌داشتهٔ این سفارش را برد؛ برای تصمیم لمس کنید"
    } else {
      (if (known) "سفارشِ تازه #${n.orderNo}" else "سفارشِ تازه") to "برای دیدن و پذیرفتن لمس کنید"
    }
  }

  /** لمسِ اعلان ⇒ رسید در صف (تکراری ⇒ همان صف). شناسهٔ بدشکل ⇒ بی اثر. */
  fun tap(state: AckState, notifId: String, orderId: String, now: Long): AckState {
    if (!validId(notifId) || (orderId.isNotEmpty() && !validId(orderId))) return state
    val shown = remember(state.shown, notifId)
    if (state.pending.any { it.notifId == notifId }) return state.copy(shown = shown)
    val next = (state.pending + PendingAck(notifId, orderId, now, nextAt = now)).takeLast(MAX_PENDING)
    return state.copy(pending = next, shown = shown)
  }

  /** رسیدهایی که وقتِ فرستادنشان رسیده — قدیمی‌ترین اول، حداکثر [MAX_BATCH]. */
  fun due(state: AckState, now: Long): List<PendingAck> =
    state.pending.filter { it.nextAt <= now && now - it.tappedAt <= TTL_MS }
      .sortedBy { it.tappedAt }.take(MAX_BATCH)

  /** پاسخِ سرور ⇒ نتیجه. [code] همان `error.code` ِ سرور. */
  fun outcomeOf(status: Int, code: String = ""): Outcome = when {
    status in 200..299 -> Outcome.ACKED
    status == 404 || code == "notification_not_found" -> Outcome.GONE
    status == 400 || code == "bad_ack_kind" -> Outcome.GONE
    else -> Outcome.RETRY
  }

  /** نتیجهٔ یک فرستادن را روی صف می‌نشاند. */
  fun applyResult(state: AckState, notifId: String, outcome: Outcome, now: Long): AckState {
    val cur = state.pending.firstOrNull { it.notifId == notifId } ?: return state
    return when (outcome) {
      Outcome.ACKED, Outcome.GONE -> state.copy(pending = state.pending - cur)
      Outcome.RETRY -> {
        val attempts = cur.attempts + 1
        val next = cur.copy(attempts = attempts, nextAt = now + backoffMs(attempts))
        state.copy(pending = state.pending.map { if (it.notifId == notifId) next else it })
      }
    }
  }

  fun backoffMs(attempts: Int): Long {
    var ms = BASE_BACKOFF_MS
    repeat((attempts - 1).coerceIn(0, 20)) { ms = (ms * 2).coerceAtMost(MAX_BACKOFF_MS) }
    return ms
  }

  /** رسیدهای کهنه بیرون. */
  fun expire(state: AckState, now: Long): AckState {
    val keep = state.pending.filter { now - it.tappedAt <= TTL_MS }
    return if (keep.size == state.pending.size) state else state.copy(pending = keep)
  }

  /**
   *  از فهرستِ سرور، آن‌هایی که هنوز روی این دستگاه نشان داده نشده‌اند (و لمس‌شده نیستند)؛
   *  وضعیتِ برگشتی آن‌ها را «نشان‌داده‌شده» ثبت کرده است.
   */
  fun fresh(state: AckState, notices: List<OrderNotice>): Pair<AckState, List<OrderNotice>> {
    val seen = state.shown.toHashSet() + state.pending.map { it.notifId }
    val out = notices.filter { validId(it.id) && validId(it.orderId) && it.id !in seen }.distinctBy { it.id }
    if (out.isEmpty()) return state to out
    var shown = state.shown
    for (n in out) shown = remember(shown, n.id)
    return state.copy(shown = shown) to out
  }

  private fun remember(shown: List<String>, id: String): List<String> =
    if (id in shown) shown else (shown + id).takeLast(MAX_SHOWN)

  // ── ذخیره روی دستگاه: متنِ ساده، سطر به سطر ─────────────────────────

  private const val VERSION = "v1"

  fun encode(state: AckState): String = buildString {
    append(VERSION).append('\n')
    for (a in state.pending) {
      append("a|").append(a.notifId).append('|').append(a.orderId).append('|')
        .append(a.tappedAt).append('|').append(a.attempts).append('|').append(a.nextAt).append('\n')
    }
    for (s in state.shown) append("s|").append(s).append('\n')
  }

  /** متنِ خراب یا نسخهٔ ناشناخته ⇒ صفِ خالی (هرگز استثنا). سطرِ بدشکل نادیده. */
  fun decode(raw: String?): AckState {
    val lines = raw?.split('\n') ?: return AckState()
    if (lines.firstOrNull() != VERSION) return AckState()
    val pending = ArrayList<PendingAck>()
    val shown = ArrayList<String>()
    for (line in lines.drop(1)) {
      val p = line.split('|')
      when {
        p.size == 6 && p[0] == "a" && validId(p[1]) && (p[2].isEmpty() || validId(p[2])) -> {
          val at = p[3].toLongOrNull() ?: continue
          val n = p[4].toIntOrNull() ?: continue
          val next = p[5].toLongOrNull() ?: continue
          if (pending.none { it.notifId == p[1] }) pending += PendingAck(p[1], p[2], at, n.coerceAtLeast(0), next)
        }
        p.size == 2 && p[0] == "s" && validId(p[1]) -> if (p[1] !in shown) shown += p[1]
      }
    }
    return AckState(pending.takeLast(MAX_PENDING), shown.takeLast(MAX_SHOWN))
  }
}
