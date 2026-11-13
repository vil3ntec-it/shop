package ir.vil3ntec.tohid.core.orders

/**
 *  زنگِ سفارشِ تازه تا «دیدم» — NOTIF-06 (§۳۰.۵: «برنامهٔ Windows در tray فعال باشد؛ اعلان/صدا ایجاد و با تأیید کاربر
 *  متوقف شود»)، ORD-02 (صدای تکراری تا تأیید، حتی در سینی) و S36 (کامپیوتر: هشدار ظرفِ ≤ ۱۰ ثانیه، حتی در سینی).
 *
 *  ورودی فقط فهرستِ `GET /api/shop/order-notify/pending` است (اعلانِ بازنشده‌ای که سفارشش هنوز «تازه» است؛ پرچمِ
 *  `shop.order_notify`)، پس زنگ خودبه‌خود می‌خوابد وقتی:
 *    • هر دستگاهی سفارش را پذیرفت/رد کرد (دیگر «تازه» نیست) یا اعلان را «باز شد» زد (ردیف `acked`)؛
 *    • همین دستگاه «دیدم» زد ([seen]) — حتی اگر رسید هنوز به سرور نرسیده یا نقش اجازهٔ بستنِ اعلان ندارد.
 *  بی اینترنت ([onOffline]) همان‌هایی که زنگ می‌زدند تا «دیدم» می‌زنند و هیچ «تازه‌ای» ساخته نمی‌شود.
 *
 *  منطقِ خالص، بی ساعت و بی شبکه: زمان از بیرون می‌آید (`now`) و آزمونش (`OrderRingTest`) بی دستگاه اجرا می‌شود.
 *  نمایش و صدا کارِ لبهٔ کامپیوتر است (`desktop/OrderTrayAlarmDesktop.kt`).
 */
object OrderRing {

  /** فاصلهٔ پرسیدنِ `/pending` — کمتر از ۱۰ ثانیهٔ S36 */
  const val POLL_MS = 8_000L

  /** فاصلهٔ تکرارِ صدا تا «دیدم» (با پرسشِ ۸ ثانیه‌ای: هر دو دور یک بار) */
  const val RING_EVERY_MS = 15_000L

  /** سقفِ اعلان‌های هم‌زمان (همان سقفِ فهرستِ سرور بس است؛ این فقط حافظه را بسته نگه می‌دارد) */
  const val MAX_RINGING = 50

  data class State(
    /** اعلان‌هایی که زنگ می‌زنند — به ترتیبِ سرور (تازه‌ترین اول) */
    val ringing: List<OrderNotice> = emptyList(),
    /** «دیدم» ِ همین دستگاه — فقط تا وقتی سرور هنوز همان را برمی‌گرداند */
    val seen: Set<String> = emptySet(),
    /** آخرین صدا؛ null ⇒ هنوز هیچ */
    val lastRingAt: Long? = null,
  ) {
    val active: Boolean get() = ringing.isNotEmpty()
  }

  /** نتیجهٔ یک دور: وضعِ تازه، اعلان‌هایی که همین دور رسیدند و این‌که همین حالا صدا بزند یا نه */
  data class Step(val state: State, val arrived: List<OrderNotice>, val ring: Boolean)

  /** پاسخِ `/pending` رسید */
  fun onPending(s: State, pending: List<OrderNotice>, now: Long): Step {
    val valid = pending
      .filter { OrderNotifyAcks.validId(it.id) && OrderNotifyAcks.validId(it.orderId) }
      .distinctBy { it.id }
      .take(MAX_RINGING)
    val ids = valid.mapTo(HashSet()) { it.id }
    val seen = s.seen.filterTo(HashSet()) { it in ids }
    val ringing = valid.filter { it.id !in seen }
    val before = s.ringing.mapTo(HashSet()) { it.id }
    val arrived = ringing.filter { it.id !in before }
    val ring = ringing.isNotEmpty() && (arrived.isNotEmpty() || due(s.lastRingAt, now))
    return Step(State(ringing, seen, if (ring) now else s.lastRingAt), arrived, ring)
  }

  /** پرسش نشد (بی اینترنت، خطای سرور): همان‌هایی که زنگ می‌زدند، سرِ وقتشان */
  fun onOffline(s: State, now: Long): Step {
    val ring = s.ringing.isNotEmpty() && due(s.lastRingAt, now)
    return Step(if (ring) s.copy(lastRingAt = now) else s, emptyList(), ring)
  }

  /** «دیدم» برای [ids]؛ null ⇒ همهٔ آن‌هایی که زنگ می‌زنند */
  fun seen(s: State, ids: Collection<String>?): State {
    val drop = ids?.toHashSet() ?: s.ringing.mapTo(HashSet()) { it.id }
    if (drop.isEmpty()) return s
    return s.copy(ringing = s.ringing.filter { it.id !in drop }, seen = s.seen + drop)
  }

  /** کلیک روی اعلان/سینی ⇒ کدام سفارش باز شود: فقط یکی ⇒ همان؛ چند تا یا هیچ ⇒ null (فهرستِ سفارش‌ها) */
  fun target(s: State): String? = s.ringing.singleOrNull()?.orderId

  private fun due(last: Long?, now: Long): Boolean = last == null || now - last >= RING_EVERY_MS
}
