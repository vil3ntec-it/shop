package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderNoticeDto
import ir.vil3ntec.tohid.core.model.OrderNoticesDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.OrderNotices
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  ORD-16 — «پیام‌ها به مشتری» در جزئیاتِ سفارش: کدام رویداد، از کدام کانال، چه شد (`GET /:id/notices`) و
 *  «فرستادنِ دوباره» برای پیامِ نرفتهٔ تازه‌ترین رویداد. همهٔ بخش فقط با `noticeResend` ِ `/config` (پرچمِ خاموشِ
 *  `shop.orders.notice_resend`)؛ خاموش ⇒ نه پرسشی، نه بخشی (همان جزئیاتِ امروز). بی شماره، متن یا لینک.
 */

private fun noticeEventText(e: String): String = when (e) {
  "order_received" -> StrOrderOps.noticeEventReceived
  "order_accepted" -> StrOrderOps.noticeEventAccepted
  "order_rejected" -> StrOrderOps.noticeEventRejected
  "order_ready" -> StrOrderOps.noticeEventReady
  "order_on_the_way" -> StrOrderOps.noticeEventOnTheWay
  "order_delivery_failed" -> StrOrderOps.noticeEventFailed
  "order_delivery_retry" -> StrOrderOps.noticeEventRetry
  "order_completed" -> StrOrderOps.noticeEventCompleted
  "order_cancelled" -> StrOrderOps.noticeEventCancelled
  else -> e
}

private fun noticeStatusText(s: String): String = when (s) {
  "sent" -> StrOrderOps.noticeSent
  "pending", "sending" -> StrOrderOps.noticePending
  "no_channel" -> StrOrderOps.noticeNoChannel
  "failed" -> StrOrderOps.noticeFailed
  "rate_limited" -> StrOrderOps.noticeRateLimited
  "superseded" -> StrOrderOps.noticeSuperseded
  "expired" -> StrOrderOps.noticeExpired
  "cancelled" -> StrOrderOps.noticeCancelled
  else -> s
}

private fun channelText(c: String): String = when (c) {
  "push" -> StrOrderOps.channelPush
  "whatsapp" -> StrOrderOps.channelWhatsapp
  "telegram" -> StrOrderOps.channelTelegram
  "sms" -> StrOrderOps.channelSms
  else -> c
}

/** بخشِ «پیام‌ها به مشتری» زیرِ خطِ زمانی؛ [rev] با هر تغییرِ سفارش دوباره می‌پرسد */
@Composable
internal fun OrderNoticesSection(o: OrderDto, cfg: OrdersConfigDto, repo: OrdersRepository, stamp: (Long) -> String, onMessage: (String) -> Unit) {
  val rc = cfg.noticeResend ?: return
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var data by remember(o.id) { mutableStateOf<OrderNoticesDto?>(null) }
  var failed by remember(o.id) { mutableStateOf(false) }
  var busy by remember(o.id) { mutableStateOf<String?>(null) }
  //  یک کلید برای هر پیام در همین برگه ⇒ دو کلیک یا قطعِ نت پس از ثبت دو بار نمی‌فرستد
  val keys = remember(o.id) { HashMap<String, String>() }
  suspend fun load() {
    repo.notices(o.id).onSuccess { data = it; failed = false }.onFailure { failed = data == null }
  }
  LaunchedEffect(o.id, o.version) { load() }

  Spacer(Modifier.height(14.dp))
  Text(StrOrderOps.noticesTitle, style = MaterialTheme.typography.labelLarge, color = c.muted)
  Spacer(Modifier.height(4.dp))
  val d = data
  when {
    d == null -> if (failed) Text(StrOrderOps.noticesLoadFailed, style = MaterialTheme.typography.bodySmall, color = c.muted)
    !d.eligible -> Text(StrOrderOps.noticesStaffOrder, style = MaterialTheme.typography.bodySmall, color = c.muted)
    d.items.isEmpty() -> Text(if (d.enabled) StrOrderOps.noticesNone else StrOrderOps.noticesOff, style = MaterialTheme.typography.bodySmall, color = c.muted)
    else -> {
      val all = d.items.map { OrderNotices.Item(it.id, it.status, it.createdAt) }
      for (n in d.items) NoticeRow(n, stamp, canResend = OrderNotices.canResend(OrderNotices.Item(n.id, n.status, n.createdAt), all, rc.statuses, cfg.permissions.manage), busy = busy == n.id) {
        if (busy == null) {
          busy = n.id
          scope.launch {
            repo.resendNotice(o.id, n.id, keys.getOrPut(n.id) { OrdersRepository.newKey() })
              .onSuccess { onMessage(StrOrderOps.noticeResent); keys.remove(n.id); load() }
              .onFailure { f -> onMessage(f.userMessage); if (OrderNotices.stale(f.code)) { keys.remove(n.id); load() } }
            busy = null
          }
        }
      }
    }
  }
}

@Composable
private fun NoticeRow(n: OrderNoticeDto, stamp: (Long) -> String, canResend: Boolean, busy: Boolean, onResend: () -> Unit) {
  val c = Shop.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(
        StrOrderOps.noticeLine(noticeEventText(n.event), noticeStatusText(n.status)),
        color = if (OrderNotices.failed(n.status)) c.warning else c.text, fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
      )
      val at = n.sentAt ?: n.updatedAt
      val via = if (n.status == "sent" && n.channel.isNotEmpty()) " · " + StrOrderOps.noticeVia(channelText(n.channel)) else ""
      if (at > 0) Text(stamp(at) + via, style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
    if (canResend) {
      Spacer(Modifier.width(8.dp))
      TohidSecondaryButton(StrOrderOps.noticeResend, onClick = onResend, enabled = !busy)
    }
  }
}

/** عنوانِ رویدادِ `notice_resend` در خطِ زمانی، یا null (پرچمِ خاموش [on] یا رویدادِ دیگر: همان نوشتهٔ امروز) */
internal fun orderNoticeEventTitle(e: OrderEventDto, on: Boolean): String? {
  if (!on || e.kind != "note" || e.detail.type != OrderNotices.EVENT) return null
  return StrOrderOps.noticeEvent(noticeEventText(e.detail.event))
}
