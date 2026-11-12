package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.orders.OrderDelay
import ir.vil3ntec.tohid.data.repo.OrdersRepository

/*
 *  ORD-07 — «عقب انداختن» در جزئیاتِ سفارش (برنامهٔ مدیریتِ اندروید و کامپیوتر). فقط وقتی `/config` ِ سرور
 *  `delay` دارد (پرچمِ خاموشِ `shop.orders.delay`)، وضعیت در فهرستِ سرور است و کاربر `orders.manage` دارد.
 *  زمانِ تازه را سرور روی زمانِ ذخیره‌شدهٔ خودش می‌سازد؛ کلیدِ یکتایی یک بار برای هر پنجره.
 */

internal fun delayReasonText(r: String): String = when (r) {
  "busy" -> StrOrderOps.delayReasonBusy
  "out_of_stock" -> StrOrderOps.delayReasonStock
  "courier" -> StrOrderOps.delayReasonCourier
  "customer_request" -> StrOrderOps.delayReasonCustomer
  else -> StrOrderOps.delayReasonOther
}

/**
 *  حالتِ برگهٔ «عقب انداختن» برای `OrderDelayV3` ِ نسخهٔ ۳ (برگهٔ رابطِ امروز بی‌راه بود و برداشته شد، `claude/e-rows-39m`). یک کلید برای همین پنجره:
 *  «تأیید» ِ دوباره (قطعِ نت پس از ثبت) همان پاسخ است، نه عقب‌انداختنِ دوم.
 */
@Stable
internal class OrderDelayForm(o: OrderDto, val maxMinutes: Int, val reasons: List<String>) {
  val key = OrdersRepository.newKey()
  var seen by mutableStateOf(o)
    private set
  var minutes by mutableStateOf(OrderDelay.minutes(maxMinutes).getOrElse(1) { 0 })
  var reason by mutableStateOf("")
    private set
  var note by mutableStateOf("")
    private set
  var busy by mutableStateOf(false)
    private set
  var error by mutableStateOf<String?>(null)
    private set

  val valid: Boolean get() = OrderDelay.valid(minutes, reason, note, maxMinutes, reasons)

  fun pick(r: String) { reason = if (reason == r) "" else r }
  fun edit(raw: String) { note = raw.take(300) }

  /** ثبت؛ موفق ⇒ سفارشِ تازهٔ سرور و پیامِ صفحه، وگرنه null و `error` */
  suspend fun submit(repo: OrdersRepository, stamp: (Long) -> String): Pair<OrderDto, String>? {
    if (busy) return null
    busy = true
    error = null
    var out: Pair<OrderDto, String>? = null
    repo.delay(seen.id, key, minutes, reason, note, seen.readyAt)
      .onSuccess { fresh -> out = fresh to StrOrderOps.delayDone(fresh.orderNo.toString(), fresh.readyAt?.let(stamp).orEmpty()) }
      .onFailure { f ->
        error = when (f.code) {
          "ready_changed" -> StrOrderOps.delayChanged
          "delay_not_allowed" -> StrOrderOps.delayNotAllowed
          else -> if (f.retryable) StrOrderOps.delayRetry else f.userMessage
        }
        //  زمانِ تازهٔ سرور ⇒ «اکنون آماده تا …» و `expectedReadyAt` ِ تلاشِ بعدی همان
        if (f.code == "ready_changed" || f.code == "delay_not_allowed") repo.get(seen.id).onSuccess { seen = it }
      }
    busy = false
    return out
  }
}

/** عنوانِ رویدادِ `ready_delay` در خطِ زمانی، یا null (رویدادهای دیگر، یا پرچمِ خاموش [on]: همان نوشتهٔ امروزِ صفحه) */
internal fun orderDelayEventTitle(e: OrderEventDto, on: Boolean, stamp: (Long) -> String): String? {
  if (!on || e.kind != "note" || e.detail.type != OrderDelay.EVENT) return null
  val at = e.detail.readyAt ?: return null
  val prev = e.detail.prevReadyAt?.let { " · " + StrOrderOps.delayEventPrev(stamp(it)) }.orEmpty()
  return StrOrderOps.delayEvent(stamp(at)) + prev
}
