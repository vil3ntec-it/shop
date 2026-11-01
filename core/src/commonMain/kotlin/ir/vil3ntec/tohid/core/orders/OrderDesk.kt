package ir.vil3ntec.tohid.core.orders

/**
 *  میزِ سفارشِ برنامهٔ مدیریت — ORD-03 (نشانگرِ «باز / شلوغ / بسته»)، ORD-04 (قبولِ خودکار)، ORD-08 («شلوغم» با پایانِ
 *  خودکار و توقف)، ORD-09 (توقفِ خودکار). منطقِ خالصِ صفحه، commonMain؛ وضعیت و تصمیم فقط از سرور
 *  (`GET /api/shop/orders/desk`، `lib/order-desk.js`، پشتِ پرچمِ خاموشِ `shop.orders.desk`).
 */
object OrderDesk {

  /** همان `PAUSE_REASONS` ِ سرور */
  val PAUSE_REASONS: List<String> = listOf("busy", "out_of_stock", "staff", "technical", "other")

  /** همان بازه‌های سرور: «شلوغم» ۰ تا ۲۴۰ دقیقه (۰ = پایان)، توقف ۵ تا ۱۴۴۰ */
  val BUSY_MINUTES: List<Int> = listOf(15, 30, 60)
  val PAUSE_MINUTES: List<Int> = listOf(15, 30, 60, 120)

  /** `paused` بر `busy` جلو می‌افتد (ویترین سفارش نمی‌گیرد)؛ گذشته ⇒ باز (همان `view` ِ سرور با ساعتِ همین لحظه) */
  fun status(busyUntil: Long?, pausedUntil: Long?, now: Long): String = when {
    pausedUntil != null && pausedUntil > now -> "paused"
    busyUntil != null && busyUntil > now -> "busy"
    else -> "open"
  }

  /** دقیقه‌های مانده، گرد به بالا؛ گذشته ⇒ ۰ */
  fun minutesLeft(until: Long?, now: Long): Int =
    if (until == null || until <= now) 0 else (((until - now) + 59_999) / 60_000).toInt()

  /** توقف فقط با دلیلِ فهرست؛ «دیگر» توضیح می‌خواهد (همان `pause` ِ سرور) */
  fun canPause(minutes: Int, reason: String, note: String): Boolean =
    minutes in 5..1440 && reason in PAUSE_REASONS && (reason != "other" || note.isNotBlank())

  /** عددِ تنظیم در بازهٔ سرور، وگرنه null (دکمهٔ «ذخیره» خاموش) */
  fun bounded(text: String, min: Int, max: Int): Int? = text.trim().toIntOrNull()?.takeIf { it in min..max }
}
