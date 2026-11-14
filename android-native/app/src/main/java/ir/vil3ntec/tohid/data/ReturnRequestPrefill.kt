package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.orders.OrderReturnRequests

/**
 *  RET-11 — درخواستِ برگشتِ ویترین ⇒ پیش‌پرِ همان برگهٔ برگشتِ موجود (`ReturnFlowSheet` ⇐ `ReturnFlow.post`): هر قلمِ
 *  درخواست ⇒ ردیفِ فروشِ همان سفارش (`OrderFulfillment.itemIdOf`) با مقدار به واحدِ موجودی (`OrderFulfillment.stockQty`،
 *  همان تبدیلِ فروشِ تحویل)، و هرگز بیش از ماندهٔ قابلِ برگشتِ همان ردیف (`ReturnLeft.left`، برگشت‌های دستگاه‌های دیگر
 *  هم). خالص — بی Context. پول و موجودی فقط با «ثبت» ِ همان برگه عوض می‌شوند.
 */
object ReturnRequestPrefill {

  /** پیش‌پرِ برگه: شناسهٔ سندِ ثابت، مقدارِ هر ردیف (نوشتهٔ کادر) و دلیلِ پیشنهادی از فهرستِ همین دکان */
  data class Preset(val docId: String, val qtys: Map<String, String>, val reason: String?)

  /** فروشِ همان سفارش در دفترِ همین دستگاه؛ نبود ⇒ `null` (هنوز همگام نشده — پذیرفتن نه) */
  fun saleOf(d: ShopData, r: OrderReturnRequests.Request): Sale? =
    OrderFulfillment.saleOf(d, r.orderId)?.takeIf { it.status != "cancelled" }

  fun of(d: ShopData, r: OrderReturnRequests.Request, shopReasons: List<String>): Preset {
    val sale = saleOf(d, r)
    val qtys = LinkedHashMap<String, String>()
    if (sale != null) for (l in r.lines) {
      val item = d.saleItems.firstOrNull { it.saleId == sale.id && it.id == OrderFulfillment.itemIdOf(r.orderId, l.lineNo) } ?: continue
      val p = d.products.firstOrNull { it.id == item.productId }
      val want = if (p != null) OrderFulfillment.stockQty(p, l.unit, l.qtyMilli) else l.qtyMilli / 1000.0
      val q = minOf(want, ReturnLeft.left(d, item))
      if (q > 1e-9) qtys[item.id] = text(q)
    }
    return Preset(r.docId, qtys, OrderReturnRequests.suggestedReason(r.reason, shopReasons))
  }

  /** عدد ⇒ نوشتهٔ کادرِ مقدار با رقمِ لاتین (همان `ReturnFlowModel.number` می‌خواندش)؛ تا سه رقمِ اعشار، بی صفرِ پایانی */
  fun text(q: Double): String {
    val milli = kotlin.math.round(q * 1000).toLong()
    val whole = milli / 1000
    val frac = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
  }
}
