package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrder

/**
 *  پیگیریِ سفارش برای مشتری — همان پله‌های `K.orderSteps` و دسته‌های `K.orderKind` ِ وب
 *  (`server/public/kit/order-kit.js`، `kit.js`) تا برنامه و وب یک داستان بگویند.
 */
object TrackRules {

  enum class StepState { DONE, NOW, TODO, STOP }

  /** [id]: placed · accepted · prep · ready · way · picked · delivered، یا برای پلهٔ پایانیِ ردشده خودِ وضعیت */
  data class Step(val id: String, val at: Long?, val state: StepState)

  /** ردشده، لغوشده، برگشته — پله‌های بعدی نمی‌آیند */
  val STOP: List<String> = listOf("rejected", "auto_rejected", "cancelled_by_customer", "cancelled", "returned_to_store", "restocked")

  /** ok · err · warn · prep · review · info (رنگِ نشان) */
  fun kind(status: String): String = when (status) {
    in STOP -> "err"
    "delivered", "picked_up", "refunded_partial", "refunded_full" -> "ok"
    "new" -> "warn"
    "accepted", "picking", "packed", "ready_for_pickup" -> "prep"
    "return_requested", "delivery_failed" -> "review"
    else -> "info"
  }

  private class Def(val id: String, val keys: List<String>)

  fun steps(o: PublicOrder): List<Step> {
    val pickup = o.fulfillment == CheckoutRules.PICKUP
    val after = listOf("return_requested", "refunded_partial", "refunded_full")
    val defs = listOf(
      Def("placed", listOf("new")),
      Def("accepted", listOf("accepted")),
      Def("prep", listOf("picking", "packed")),
      if (pickup) Def("ready", listOf("ready_for_pickup")) else Def("way", listOf("out_for_delivery", "delivery_failed", "retry_scheduled")),
      if (pickup) Def("picked", listOf("picked_up") + after) else Def("delivered", listOf("delivered") + after),
    )
    val at = HashMap<String, Long>()
    for (e in o.timeline) at[e.status] = e.at
    val stopped = o.status in STOP
    var idx = 0
    defs.forEachIndexed { i, d -> if (d.keys.any { at.containsKey(it) || o.status == it }) idx = maxOf(idx, i) }
    val out = defs.mapIndexed { i, d ->
      val t = d.keys.lastOrNull { at.containsKey(it) }?.let { at[it] }
      var state = when {
        i < idx -> StepState.DONE
        i == idx -> if (stopped) StepState.DONE else StepState.NOW
        else -> StepState.TODO
      }
      if (i == idx && (o.status == "delivered" || o.status == "picked_up")) state = StepState.DONE
      Step(d.id, t, state)
    }
    return if (stopped) out.take(idx + 1) + Step(o.status, at[o.status] ?: o.updatedAt, StepState.STOP) else out
  }

  /** پرداخت‌شده (فقط «پرداخت شد» ِ سرور، هرگز برگشتِ مرورگر/برنامه) */
  fun paidMinor(o: PublicOrder): Long = if (o.paymentStatus == "paid") o.totalMinor else 0

  fun dueMinor(o: PublicOrder): Long = (o.totalMinor - paidMinor(o)).coerceAtLeast(0)

  /** پایان یافته؟ (تحویل‌شده، ردشده، لغوشده، بازپرداخت‌شده…) ⇒ دیگر خودکار تازه نمی‌شود */
  fun finished(o: PublicOrder): Boolean =
    o.status in STOP || o.status in listOf("delivered", "picked_up", "refunded_partial", "refunded_full")

  /** هر چند وقت خودکار تازه شود؛ null ⇒ نه (سرکشیِ بی‌پایان نه — فقط وقتی صفحه باز است) */
  fun refreshEveryMs(o: PublicOrder): Long? = if (finished(o)) null else 30_000L
}
