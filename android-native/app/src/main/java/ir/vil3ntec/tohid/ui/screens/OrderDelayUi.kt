package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.OrderDelay
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

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
 *  حالتِ برگهٔ «عقب انداختن» برای هر دو رابط (این‌جا و `OrderDelayV3` ِ نسخهٔ ۳). یک کلید برای همین پنجره:
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

/** دکمهٔ «عقب انداختن» کنارِ «آماده تا …»؛ [onDone] سفارشِ تازهٔ سرور، [stamp] زمانِ محلیِ صفحهٔ سفارش‌ها */
@Composable
internal fun OrderDelayEntry(o: OrderDto, cfg: OrdersConfigDto, repo: OrdersRepository, stamp: (Long) -> String, onDone: (OrderDto, String) -> Unit) {
  val d = cfg.delay
  if (d == null || !OrderDelay.can(o.status, d.statuses, cfg.permissions.manage)) return
  var open by remember(o.id) { mutableStateOf(false) }
  TohidSecondaryButton(StrOrderOps.delayAction, onClick = { open = true })
  if (open) OrderDelaySheet(o, d.maxMinutes, OrderDelay.reasons(d.reasons), repo, stamp, onClose = { open = false }) { fresh, msg ->
    open = false
    onDone(fresh, msg)
  }
}

@Composable
private fun OrderDelaySheet(
  o: OrderDto,
  maxMinutes: Int,
  reasons: List<String>,
  repo: OrdersRepository,
  stamp: (Long) -> String,
  onClose: () -> Unit,
  onDone: (OrderDto, String) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val f = remember(o.id) { OrderDelayForm(o, maxMinutes, reasons) }

  TohidBottomSheet(title = StrOrderOps.delayTitle(o.orderNo.toString()), onDismiss = onClose) {
    SheetBody {
      val now = f.seen.readyAt
      Text(if (now != null) StrOrderOps.delayNow(stamp(now)) else StrOrderOps.delayNoTime, style = MaterialTheme.typography.bodySmall, color = c.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in OrderDelay.minutes(maxMinutes)) TohidFilterChip(label = StrOrderOps.delayMinutes(m), selected = f.minutes == m, onClick = { f.minutes = m })
      }
      Text(StrOrderOps.delayReason, style = MaterialTheme.typography.labelLarge, color = c.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in reasons) TohidFilterChip(label = delayReasonText(r), selected = f.reason == r, onClick = { f.pick(r) })
      }
      TohidTextField(f.note, f::edit, label = StrOrderOps.delayNote, supporting = StrOrderOps.delayNoteHint, singleLine = false)
      f.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger) }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TohidButton(StrOrderOps.delayConfirm, enabled = f.valid && !f.busy, busy = f.busy, onClick = {
          scope.launch { f.submit(repo, stamp)?.let { (fresh, msg) -> onDone(fresh, msg) } }
        })
        TohidSecondaryButton(StrOrderOps.delayCancel, onClick = onClose)
      }
    }
  }
}

/** عنوانِ رویدادِ `ready_delay` در خطِ زمانی، یا null (رویدادهای دیگر، یا پرچمِ خاموش [on]: همان نوشتهٔ امروزِ صفحه) */
internal fun orderDelayEventTitle(e: OrderEventDto, on: Boolean, stamp: (Long) -> String): String? {
  if (!on || e.kind != "note" || e.detail.type != OrderDelay.EVENT) return null
  val at = e.detail.readyAt ?: return null
  val prev = e.detail.prevReadyAt?.let { " · " + StrOrderOps.delayEventPrev(stamp(it)) }.orEmpty()
  return StrOrderOps.delayEvent(stamp(at)) + prev
}
