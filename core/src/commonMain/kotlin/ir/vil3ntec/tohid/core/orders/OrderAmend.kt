package ir.vil3ntec.tohid.core.orders

import ir.vil3ntec.tohid.core.pos.DeliverLater

/**
 *  ORD-14 — «ویرایشِ سفارش» و «تسویهٔ تفاوت» در برنامهٔ مدیریت (`POST /api/shop/orders/:id/amendments`،
 *  `…/:amendId/withdraw`، `…/:amendId/settlement`؛ `lib/order-amend.js`، پرچم‌های خاموشِ `shop.orders.amend` و
 *  `shop.orders.amend_paid`). منطقِ خالصِ صفحه: کی دکمه هست، کدام قلم عوض شد و پیش‌نمایشِ جمع با همان گردکردنِ
 *  سرور (`lineTotalMinor`). مبلغِ نهایی را فقط سرور می‌سازد؛ قیمتِ واحد هرگز از این‌جا نمی‌رود.
 */
object OrderAmend {

  /** همان `MAX_QTY_MILLI` ِ سرور */
  const val MAX_QTY_MILLI: Long = 10_000_000L

  /** همان `REASONS` و `CONSENT_VIA` ِ سرور (متن فقط برای همین‌ها هست) */
  val REASONS: List<String> = listOf("out_of_stock", "customer_request", "price_error", "other")
  val CONSENT_VIA: List<String> = listOf("phone", "in_person", "telegram", "whatsapp")

  /** پیشوندِ نوعِ رویدادهای `note` (`amend_proposed`، `amend_applied`، `amend_closed`، `amend_settled`) */
  const val EVENT_PREFIX: String = "amend_"

  /**
   *  دکمهٔ «ویرایشِ سفارش»: پرچم ([statuses] ناتهی)، وضعیتِ فهرستِ سرور، `orders.manage`، و پرداخت‌نشده — یا
   *  پرداخت‌شده وقتی سرور `paid` را گفته (پرچمِ `shop.orders.amend_paid`). در جریان/بخشی‌بازپرداخت‌شده هرگز.
   */
  fun can(status: String, paymentStatus: String, statuses: List<String>?, paidAllowed: Boolean, manage: Boolean): Boolean =
    manage && !statuses.isNullOrEmpty() && status in statuses &&
      (paymentStatus == "unpaid" || (paidAllowed && paymentStatus == "paid"))

  /** سفارشِ کارمند رضایتِ ثبت‌شده می‌خواهد؛ سفارشِ ویترین را خودِ مشتری با پیوندِ پیگیری می‌پذیرد */
  fun needsConsent(source: String): Boolean = source != "storefront"

  data class Line(val lineNo: Int, val qtyMilli: Long, val unitPriceMinor: Long, val lineTotalMinor: Long)

  data class Change(val lineNo: Int, val qtyMilli: Long)

  enum class Problem { NONE, NO_CHANGE, BAD_QTY, ALL_REMOVED, DISCOUNT }

  data class Plan(val changes: List<Change>, val subtotalMinor: Long, val totalMinor: Long, val differenceMinor: Long, val problem: Problem)

  /**
   *  [edited]: مقدارِ تازهٔ هر قلم (هزارم؛ ۰ = برداشتن؛ null = متنِ نادرست). همان `plan` ِ سرور: فقط قلمِ عوض‌شده،
   *  دست‌کم یک قلم می‌ماند، تخفیف بیش از جمعِ تازهٔ اقلام نه؛ هزینهٔ ارسال و تخفیف دست نمی‌خورند.
   */
  fun plan(lines: List<Line>, edited: Map<Int, Long?>, discountMinor: Long, deliveryFeeMinor: Long, prevTotalMinor: Long): Plan {
    fun bad(p: Problem) = Plan(emptyList(), 0, prevTotalMinor, 0, p)
    if (edited.values.any { it == null || it < 0 || it > MAX_QTY_MILLI }) return bad(Problem.BAD_QTY)
    val changes = lines.mapNotNull { l -> edited[l.lineNo]?.takeIf { it != l.qtyMilli }?.let { Change(l.lineNo, it) } }
    if (changes.isEmpty()) return bad(Problem.NO_CHANGE)
    val byNo = changes.associateBy { it.lineNo }
    if (lines.all { (byNo[it.lineNo]?.qtyMilli ?: it.qtyMilli) == 0L }) return bad(Problem.ALL_REMOVED)
    val subtotal = lines.sumOf { l ->
      val c = byNo[l.lineNo]
      when {
        c == null -> l.lineTotalMinor
        c.qtyMilli == 0L -> 0L
        else -> DeliverLater.lineTotalMinor(c.qtyMilli, l.unitPriceMinor)
      }
    }
    if (discountMinor > subtotal) return bad(Problem.DISCOUNT)
    val total = subtotal - discountMinor + deliveryFeeMinor
    return Plan(changes, subtotal, total, total - prevTotalMinor, Problem.NONE)
  }

  /** دلیلِ فهرست یا تهی، «دیگر» با توضیح، و برای سفارشِ کارمند راهِ رضایت */
  fun valid(reason: String, note: String, consentVia: String, staffOrder: Boolean): Boolean =
    (reason.isEmpty() || reason in REASONS) && (reason != "other" || note.isNotBlank()) &&
      (!staffOrder || consentVia in CONSENT_VIA)

  /** «ثبتِ تسویهٔ نقدی»: ویرایشِ اعمال‌شدهٔ سفارشِ پرداخت‌شده با تفاوت، هنوز تسویه‌نشده، با `orders.refund` */
  fun canSettle(status: String, kind: String, amountMinor: Long, settledAt: Long?, refund: Boolean): Boolean =
    refund && status == "applied" && kind in setOf("refund", "charge") && amountMinor > 0 && settledAt == null

  /** «پس گرفتن» ِ پیشنهادِ بازِ ویترین با `orders.manage` */
  fun canWithdraw(status: String, manage: Boolean): Boolean = manage && status == "pending"
}
