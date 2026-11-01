package ir.vil3ntec.tohid.core.orders

/**
 *  ORD-16 — «پیام‌ها» ِ سفارش در برنامهٔ مدیریت: پیام‌های وضعیت به مشتری (`GET /orders/:id/notices`، NTF-01/04) و
 *  «فرستادنِ دوباره» (`POST /orders/:id/notices/:noticeId/resend`، `lib/notice-resend.js`، پرچمِ خاموشِ
 *  `shop.orders.notice_resend`). منطقِ خالص؛ تصمیمِ نهایی با سرور (همان ردیف، همان زنجیره، سقفِ مشتری).
 */
object OrderNotices {

  /** همان `RESENDABLE` ِ سرور (پیش‌فرض وقتی `/config` فهرست نداد) */
  val RESENDABLE: List<String> = listOf("failed", "no_channel", "rate_limited")

  /** نوعِ رویدادِ `note` در خطِ زمانی (`detail.type`) */
  const val EVENT: String = "notice_resend"

  data class Item(val id: String, val status: String, val createdAt: Long)

  /**
   *  دکمهٔ «فرستادنِ دوباره» برای [item]: پرچم روشن ([statuses] ناتهی)، `orders.manage`، حالِ نرفته، و تازه‌ترین
   *  پیامِ همین سفارش (پیامِ کهنه‌تر را سرور هم نمی‌پذیرد: `notice_superseded`).
   */
  fun canResend(item: Item, all: List<Item>, statuses: List<String>?, manage: Boolean): Boolean =
    manage && !statuses.isNullOrEmpty() && item.status in statuses && all.none { it.id != item.id && it.createdAt > item.createdAt }

  /** حال‌هایی که «رفت» یا «در راه» نیستند و کاربر باید بداند (رنگِ هشدار) */
  fun failed(status: String): Boolean = status in RESENDABLE || status == "expired" || status == "cancelled"

  /** کدهای ۴۰۹ ِ سرور که یعنی «فهرست کهنه است» ⇒ فهرستِ تازه از سرور */
  fun stale(code: String): Boolean = code in setOf("notice_not_resendable", "notice_superseded", "notice_too_old", "resend_limit", "notify_disabled")
}
