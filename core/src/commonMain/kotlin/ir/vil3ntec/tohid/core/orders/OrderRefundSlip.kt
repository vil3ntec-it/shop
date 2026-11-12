package ir.vil3ntec.tohid.core.orders

/**
 *  E2E-RET-11 — چاپِ «رسیدِ اصلاحیِ بازپرداخت» ِ سفارشِ اینترنتی در برنامهٔ دکان (کاغذِ ۵۸/۸۰، همان صفِ چاپِ
 *  `OrderSlip`). ⛔ هیچ عددی این‌جا ساخته یا گرد نمی‌شود: شماره و همهٔ مبلغ‌ها همان `order.refund.receipts` ِ سرورند
 *  (`server/src/lib/order-refund-receipt.js`، پرچمِ خاموشِ `shop.orders.refund_receipt`؛ خاموش ⇒ سرور کلید را نمی‌دهد ⇒
 *  برنامه هیچ بخشی نشان نمی‌دهد). رسیدِ ناهمخوان (پیش + همین ≠ پس، جمع − پس ≠ درست، شمارهٔ بیگانه) چاپ نمی‌شود:
 *  کاغذِ پولیِ نادرست بدتر از نبودنش است.
 */
object OrderRefundSlip {

  /** یک ردیفِ `refund.receipts` ِ سرور (همه به واحدِ خرد) */
  data class Receipt(
    val no: String,
    val orderNo: Long,
    val at: Long,
    val method: String,
    val final: Boolean,
    val totalMinor: Long,
    val refundedBeforeMinor: Long,
    val amountMinor: Long,
    val refundedAfterMinor: Long,
    val netMinor: Long,
    val reason: String = "",
  )

  /** نوشته‌های رسید — جدولِ سه‌زبانهٔ برنامه (`StrRefundSlip`) پیاده‌اش می‌کند. */
  interface Texts {
    fun title(no: String): String
    fun forOrder(orderNo: String): String
    fun issuedAt(at: String): String
    fun orderTotal(amount: String): String
    fun refundedBefore(amount: String): String
    fun thisRefund(amount: String): String
    fun refundedAfter(amount: String): String
    fun net(amount: String): String
    val cash: String
    val hesabpay: String
    val closed: String
    fun reason(text: String): String
    fun printedAt(at: String): String
  }

  private val NO = Regex("^([0-9]{1,12})-R([0-9]{1,6})$")

  /** همان ناوردای سازندهٔ سرور: شماره از همین سفارش، مبلغِ مثبت، پیش + همین = پس، جمع − پس = درست و نامنفی */
  fun consistent(r: Receipt): Boolean {
    val m = NO.matchEntire(r.no) ?: return false
    return m.groupValues[1] == r.orderNo.toString() && r.orderNo > 0 &&
      r.amountMinor > 0 && r.refundedBeforeMinor >= 0 &&
      r.refundedBeforeMinor + r.amountMinor == r.refundedAfterMinor &&
      r.totalMinor - r.refundedAfterMinor == r.netMinor && r.netMinor >= 0
  }

  /**
   *  ردیف‌های کاغذ به ترتیب؛ ناهمخوان ⇒ `null` (هیچ کاغذی). [mark] (OPS-07) همان مهرِ «آموزشی» ِ `OrderSlip.rows`.
   *  دلیل فقط برای کارمند می‌آید (سرور آن را فقط در پاسخِ کارمند می‌دهد).
   */
  fun rows(r: Receipt, tx: Texts, printedAt: Long, time: (Long) -> String, mark: String? = null): List<OrderSlip.Row>? {
    if (!consistent(r)) return null
    val out = ArrayList<OrderSlip.Row>()
    out += OrderSlip.Row(tx.title(r.no), OrderSlip.Kind.TITLE, center = true)
    mark?.trim()?.takeIf { it.isNotEmpty() }?.let { out += OrderSlip.Row(it, OrderSlip.Kind.HEAD, center = true) }
    out += OrderSlip.Row(tx.forOrder(r.orderNo.toString()), OrderSlip.Kind.HEAD, center = true)
    if (r.at > 0) out += OrderSlip.Row(tx.issuedAt(time(r.at)), OrderSlip.Kind.SMALL, center = true)
    out += OrderSlip.Row(OrderSlip.RULE, OrderSlip.Kind.SMALL, center = true)
    out += OrderSlip.Row(tx.orderTotal(OrderText.money(r.totalMinor)))
    if (r.refundedBeforeMinor > 0) out += OrderSlip.Row(tx.refundedBefore(OrderText.money(r.refundedBeforeMinor)))
    out += OrderSlip.Row(tx.thisRefund(OrderText.money(r.amountMinor)), OrderSlip.Kind.HEAD)
    out += OrderSlip.Row(if (r.method == "hesabpay") tx.hesabpay else tx.cash)
    if (r.refundedBeforeMinor > 0) out += OrderSlip.Row(tx.refundedAfter(OrderText.money(r.refundedAfterMinor)))
    out += OrderSlip.Row(OrderSlip.RULE, OrderSlip.Kind.SMALL, center = true)
    out += OrderSlip.Row(tx.net(OrderText.money(r.netMinor)), OrderSlip.Kind.HEAD)
    if (r.final) out += OrderSlip.Row(tx.closed, OrderSlip.Kind.SMALL)
    if (r.reason.isNotBlank()) out += OrderSlip.Row(tx.reason(r.reason.trim()), OrderSlip.Kind.SMALL)
    out += OrderSlip.Row(tx.printedAt(time(printedAt)), OrderSlip.Kind.SMALL, center = true)
    return out
  }
}
