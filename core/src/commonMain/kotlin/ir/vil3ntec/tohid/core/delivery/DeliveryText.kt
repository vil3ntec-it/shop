package ir.vil3ntec.tohid.core.delivery

/**
 *  واژه‌های فارسیِ میزِ تحویل — همان کدهای `delivery-states.js`.
 *  کدِ ناشناخته (سرورِ تازه‌تر) خودِ کد نشان داده می‌شود، نه خالی.
 */
object DeliveryText {

  private val STATUS = mapOf(
    "requested" to "منتظرِ قبولِ پیک",
    "assigned" to "سپرده شد",
    "picked_up" to "بسته را گرفت",
    "moving" to "در راه",
    "arrived" to "رسید",
    "delivered" to "تحویل شد",
    "failed" to "ناموفق",
    "rejected" to "پیک رد کرد",
    "withdrawn" to "پس گرفته شد",
    "returned" to "برگشت به دکان",
  )

  private val MODE = mapOf(
    "shop_courier" to "پیکِ دکان",
    "external" to "شرکتِ بیرونی",
    "pickup" to "دریافتِ حضوری",
    "platform" to "شبکهٔ پیک",
  )

  private val REASON = mapOf(
    "busy" to "سرش شلوغ است",
    "too_far" to "خیلی دور است",
    "cash_limit" to "سقفِ نقد",
    "reassign" to "سپردن به دیگری",
    "order_cancelled" to "سفارش لغو شد",
    "customer_request" to "خواستِ مشتری",
    "no_answer" to "مشتری نبود / جواب نداد",
    "wrong_address" to "نشانی پیدا نشد",
    "refused" to "مشتری نپذیرفت",
    "recalled" to "فراخوانده شد",
    "out_of_stock" to "کالا تمام شد",
    "wrong_order" to "سفارشِ اشتباه",
    "partial_payment" to "پرداختِ ناقص",
    "refused_amount" to "مبلغ را نپذیرفت",
    "damaged_or_short" to "آسیب یا کسریِ کالا",
    "other" to "دیگر (با توضیح)",
  )

  fun status(id: String) = STATUS[id] ?: id
  fun mode(id: String) = MODE[id] ?: id
  fun reason(id: String) = REASON[id] ?: id

  /** دلیل‌هایی که اگر سرور نفرستاد (سرورِ قدیمی) به کار می‌روند — همان `D.REASONS` */
  val FALLBACK_REASONS: Map<String, List<String>> = mapOf(
    "withdrawn" to listOf("reassign", "order_cancelled", "customer_request", "other"),
    "failed" to listOf("no_answer", "wrong_address", "refused", "recalled", "other"),
    "recall" to listOf("customer_request", "out_of_stock", "wrong_order", "other"),
    "cash" to listOf("partial_payment", "refused_amount", "damaged_or_short", "other"),
  )

  /** وضعیت‌های «تمام‌شده‌ی خوب» / «نیازِ توجه» برای رنگِ نشان (متن همیشه کنارش هست) */
  val GOOD = setOf("delivered")
  val ATTENTION = setOf("failed", "rejected", "returned")
}
