package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto

/**
 *  CRM-07 — سفارش‌های اینترنتیِ **هنوز تحویل‌نشده یا لغوشدهٔ** یک مشتری در پروندهٔ او (گوشیِ نسخهٔ ۳ و کامپیوتر،
 *  `ui/screens/CustomerOnlineOrdersPanel`). تحویل‌شده‌ها فروشِ عادیِ دفترند (`OrderFulfillment`) و با نشانِ «اینترنتی»
 *  در فاکتورهای همان پرونده‌اند؛ این‌ها هنوز فروش نیستند پس فقط از سرور (`GET /shop/orders/customer`) می‌آیند.
 *
 *  ⛔ فقط با بخشِ سفارشِ روشن و اجازهٔ «تماس» (`orders.contact`، صاحب و مدیر) — همان دروازهٔ سرور؛ وگرنه هیچ
 *  پرسشی و هیچ چیزی در پرونده (همان امروز). شماره‌ها همان شمارهٔ اول و دومِ مشتری (`CustomerCrm.phones`)؛
 *  یکی‌سازی (‎07…‎، ‎+93…‎، رقمِ فارسی) کارِ سرور است با همان `validate.phone` ِ ثبتِ سفارش. منطقِ خالص؛
 *  آزمون: `CustomerOnlineOrdersTest`.
 */
object CustomerOnlineOrders {

  /** وضعیت‌های «هنوز تحویل‌نشده» — همان `OPEN_STATUSES` ِ سرور */
  val OPEN: Set<String> = setOf(
    "new", "accepted", "picking", "packed", "ready_for_pickup", "out_for_delivery", "delivery_failed", "retry_scheduled",
  )

  /** لغو، رد و برگشت به دکان پیش از تحویل — همان `CUSTOMER_OTHER` ِ سرور */
  val CANCELLED: Set<String> = setOf(
    "cancelled", "cancelled_by_customer", "rejected", "auto_rejected", "returned_to_store", "restocked",
  )

  /** پرسیدن فقط با بخشِ روشن و اجازهٔ «تماس» */
  fun canAsk(cfg: OrdersConfigDto?): Boolean = cfg != null && cfg.enabled && cfg.permissions.contact

  /** شماره‌های مشتری برای پرسش (اول، سپس دوم؛ خالی و تکراری نه؛ مشتریِ ادغام‌شده هیچ) */
  fun phones(debtor: Debtor?): List<String> =
    if (debtor == null || !CustomerCrm.isActive(debtor)) emptyList()
    else CustomerCrm.phones(debtor).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(2)

  /** ردیف‌های پرونده: فقط باز و لغوشده (سرورِ دیگر هر چه داد)، تازه‌ترین بالا، هر سفارش یک بار */
  fun rows(items: List<OrderDto>): List<OrderDto> =
    items.filter { it.id.isNotBlank() && (it.status in OPEN || it.status in CANCELLED) }
      .distinctBy { it.id }
      .sortedWith(compareByDescending<OrderDto> { it.createdAt }.thenByDescending { it.id })

  /** مبلغِ کارت به افغانی (پولِ خرد ⇒ افغانی) */
  fun amount(o: OrderDto): Double = o.totalMinor / 100.0
}
