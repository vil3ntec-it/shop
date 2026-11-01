package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.orders.OrderStates

/**
 *  حالتِ خالصِ برگهٔ گذار (`orders/{id}/action?to=`) — همان قاعده‌های پنجرهٔ پیشین (ORD-06/07):
 *  دلیل از `OrderStates.reasonChoices` (تک‌گزینه ⇐ از پیش انتخاب)، «دلیلِ دیگر» توضیح
 *  می‌خواهد، زمانِ آماده فقط هنگامِ قبول (پیش‌فرض ۱۵ دقیقه). آزمون: `OrderActionModelTest`.
 */
object OrderActionModel {

  data class Form(val reason: String = "", val note: String = "", val ready: Int? = DEFAULT_READY)

  const val DEFAULT_READY = 15
  const val NOTE_MAX = 300

  fun choices(o: OrderDto, to: String): List<String> = OrderStates.reasonChoices(o.status, to)

  fun start(o: OrderDto, to: String): Form = choices(o, to).let { if (it.size == 1) Form(reason = it[0]) else Form() }

  fun asksReason(to: String): Boolean = OrderStates.needsReason(to)

  fun asksReady(to: String): Boolean = to == "accepted"

  fun refund(to: String): Boolean = to in OrderStates.MONEY_TARGETS

  /** توضیح لازم است و خالی است */
  fun noteMissing(to: String, f: Form): Boolean = asksReason(to) && f.reason == "other" && f.note.isBlank()

  fun valid(to: String, f: Form): Boolean = !asksReason(to) || (f.reason.isNotEmpty() && !noteMissing(to, f))

  /** آنچه به `transition` می‌رود: زمانِ آماده فقط برای قبول */
  fun readyOf(to: String, f: Form): Int? = if (asksReady(to)) f.ready else null
}
