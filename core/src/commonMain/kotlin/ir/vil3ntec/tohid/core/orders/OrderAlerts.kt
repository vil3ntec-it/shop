package ir.vil3ntec.tohid.core.orders

/**
 *  هشدارهای درون‌برنامه‌ایِ بخشِ «سفارش‌ها» — منطقِ خالص، commonMain.
 *
 *   ORD-18 ── «شروعِ سفارشِ زمان‌بندی‌شده»: فهرست را فقط سرور می‌سازد (`startAlerts` ِ `GET /orders/summary`،
 *             پرچمِ خاموشِ `shop.orders.start_alert`؛ همان هشدارِ یک‌بارهٔ `lib/order-start-alert.js` که هنوز
 *             شروع نشده). این‌جا فقط: کدام تازه است (صدا یک بار برای هر سفارش روی هر دستگاه)، کدام را این
 *             دستگاه «دیدم» زده، و چند دقیقه مانده — با ساعتِ همان پاسخِ سرور، نه ساعتِ دستگاه.
 *   ORD-03 ── «اتصال قطع است»: پرسشِ صف به سرور نرسید (شبکه، مهلت یا ۵xx = `retryable`)؛ ۴xx قطع نیست.
 *             نخستین پاسخِ درست ⇒ وصل.
 */
object OrderAlerts {

  data class Start(val id: String, val orderNo: Long, val status: String, val scheduledFor: Long, val alertAt: Long)

  /** هشدارهایی که این دستگاه هنوز با صدا اعلام نکرده */
  fun fresh(alerts: List<Start>, announced: Set<String>): List<Start> = alerts.filter { it.id !in announced }

  /**
   *  حافظهٔ اعلام‌شده یا «دیدم»‌زده پس از پاسخِ تازه: فقط شناسه‌هایی که هنوز در فهرستِ سرورند (+ [add])؛
   *  سفارشِ شروع‌شده خودش بیرون می‌رود و حافظه بی‌حد بزرگ نمی‌شود.
   */
  fun keep(memory: Set<String>, alerts: List<Start>, add: Collection<String> = emptyList()): Set<String> {
    val live = alerts.mapTo(HashSet()) { it.id }
    return (memory + add).filterTo(LinkedHashSet()) { it in live }
  }

  /** نشان‌های صفحه: «دیدم»‌نزده‌ها، به ترتیبِ سرور (زودتر اول)، حداکثر [max] */
  fun visible(alerts: List<Start>, dismissed: Set<String>, max: Int = 3): List<Start> =
    alerts.filter { it.id !in dismissed }.take(max)

  /** دقیقه‌های مانده تا زمانِ سفارش، گرد به بالا؛ ۰ یا کمتر ⇒ «وقتش رسیده» */
  fun minutesLeft(scheduledFor: Long, serverNow: Long): Int {
    val ms = scheduledFor - serverNow
    return if (ms <= 0) 0 else ((ms + 59_999) / 60_000).toInt()
  }

  /** حالِ اتصالِ همین دستگاه به سرور؛ [lastOkAt] = ۰ ⇒ هنوز هیچ پاسخِ درستی نیامده */
  data class Net(val down: Boolean = false, val lastOkAt: Long = 0, val failedAt: Long = 0)

  fun ok(at: Long): Net = Net(down = false, lastOkAt = at)

  /** شکست: فقط «نرسید» (`retryable`) قطع است؛ ۴xx (پرچم، اجازه) حالِ پیشین را نگه می‌دارد */
  fun failed(net: Net, retryable: Boolean, at: Long): Net = if (retryable) net.copy(down = true, failedAt = at) else net

  /** «آخرین به‌روزرسانی: n دقیقه پیش» (همان ساعتِ دستگاه برای هر دو سر)؛ null ⇒ هیچ پاسخِ درستی نبوده */
  fun minutesSinceOk(net: Net): Int? =
    if (net.lastOkAt <= 0) null else (((net.failedAt - net.lastOkAt).coerceAtLeast(0)) / 60_000).toInt()
}
