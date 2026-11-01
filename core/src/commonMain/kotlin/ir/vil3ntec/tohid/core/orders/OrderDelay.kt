package ir.vil3ntec.tohid.core.orders

/**
 *  ORD-07 — «عقب انداختنِ» سفارشِ قبول‌شده در برنامهٔ مدیریت (`POST /api/shop/orders/:id/delay`،
 *  `lib/order-delay.js`، پشتِ پرچمِ خاموشِ `shop.orders.delay`). منطقِ خالصِ صفحه؛ زمانِ تازه را فقط سرور
 *  می‌سازد (روی زمانِ ذخیره‌شدهٔ خودش)، این‌جا فقط «کی دکمه هست» و «بدنه درست است؟».
 */
object OrderDelay {

  /** همان گزینه‌های وب (`license/orders-desk.js`) — دقیقه، روی زمانِ آمادهٔ فعلی */
  val MINUTES: List<Int> = listOf(10, 15, 20, 30, 45, 60)

  /** همان `DELAY_REASONS` ِ سرور؛ تهی = بی دلیل (سرور می‌پذیرد) */
  val REASONS: List<String> = listOf("busy", "out_of_stock", "courier", "customer_request", "other")

  /** نوعِ رویدادِ `note` در خطِ زمانی (`detail.type`) */
  const val EVENT: String = "ready_delay"

  /** دکمه فقط وقتی سرور پرچم را روشن گفته (`statuses` ناتهی)، وضعیت در فهرستِ سرور است و کاربر `orders.manage` دارد */
  fun can(status: String, statuses: List<String>?, manage: Boolean): Boolean =
    manage && !statuses.isNullOrEmpty() && status in statuses

  /** گزینه‌های مدت در سقفِ سرور (`maxMinutes`؛ ۰ یا کمتر ⇒ هیچ) */
  fun minutes(maxMinutes: Int): List<Int> = MINUTES.filter { it <= maxMinutes }

  /** دلیل‌هایی که هم سرور می‌گوید هم صفحه متنش را دارد (کدِ تازهٔ سرور بی متن نشان داده نمی‌شود) */
  fun reasons(server: List<String>): List<String> = REASONS.filter { it in server }

  /** همان `parse` ِ سرور: ۱ تا سقف، دلیلِ فهرست یا تهی، «دیگر» با توضیح */
  fun valid(minutes: Int, reason: String, note: String, maxMinutes: Int, reasons: List<String> = REASONS): Boolean =
    minutes in 1..maxMinutes && (reason.isEmpty() || reason in reasons) && (reason != "other" || note.isNotBlank())
}
