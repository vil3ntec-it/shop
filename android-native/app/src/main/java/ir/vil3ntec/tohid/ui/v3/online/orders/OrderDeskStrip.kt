package ir.vil3ntec.tohid.ui.v3.online.orders

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.i18n.StrOrderSlip
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.OrderAlerts
import ir.vil3ntec.tohid.core.orders.OrderDesk
import ir.vil3ntec.tohid.data.repo.OrderDeskRepository.Desk
import ir.vil3ntec.tohid.ui.OrderAlertsLive
import ir.vil3ntec.tohid.ui.screens.DeskPauseForm
import ir.vil3ntec.tohid.ui.screens.DeskSettingsForm
import ir.vil3ntec.tohid.ui.screens.deskReasonText
import ir.vil3ntec.tohid.ui.screens.OrderDeskController
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow

/*
 *  میزِ سفارش در صفِ نسخهٔ ۳ — ORD-03 (نشانگرِ «باز / شلوغ / بسته»، صفِ باز و هشدارِ قطع)، ORD-08 («شلوغم»، توقف
 *  با دلیل، ادامه)، ORD-04 و ORD-09 (قبول و توقفِ خودکار برای صاحب و مدیر) و ORD-18 (شروعِ سفارشِ زمان‌بندی‌شده).
 *  همان حالت و نوشتنِ رابطِ امروز (`OrderDeskState.kt`، `OrderAlertsLive`)، روی کیت؛ هر کدام فقط با پرچمِ خاموشِ
 *  سرور (`shop.orders.desk`، `presence`، `start_alert`) — خاموش ⇒ هیچ ردیفی.
 */

/** نشانگر و دکمه‌های میز (حالت در `rememberOrderDesk` ِ مسیر، تا با پیمایشِ صف از نو بار نشود)؛ `{enabled:false}` ⇒ هیچ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderDeskStrip(ctl: OrderDeskController) {
  var pausing by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf(false) }
  val d = ctl.desk ?: return
  if (!d.enabled) return
  val now = d.serverTime
  val repo = ctl.repo
  val status = OrderDesk.status(d.busyUntil, d.pausedUntil, now)
  Column(Modifier.fillMaxWidth()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      when (status) {
        "paused" -> V3StatusPill(V3Status.CANCELLED, StrOrderSlip.deskPaused(OrderDesk.minutesLeft(d.pausedUntil, now)))
        "busy" -> V3StatusPill(V3Status.WARNING, StrOrderSlip.deskBusy(OrderDesk.minutesLeft(d.busyUntil, now)))
        else -> V3StatusPill(V3Status.DELIVERED, StrOrderSlip.deskOpen)
      }
      V3StatusPill(V3Status.INACTIVE, StrOrderSlip.deskQueue(d.queueOpen))
    }
    if (d.canManage || d.canSettings) {
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (d.canManage && status == "open") {
          for (m in OrderDesk.BUSY_MINUTES) V3Chip(StrOrderSlip.deskBusyFor(m), selected = false, onClick = { if (!ctl.busy) ctl.act { repo.busy(d, m) } }, icon = Icons.Outlined.Schedule)
          V3Chip(StrOrderSlip.deskPause, selected = false, onClick = { if (!ctl.busy) pausing = true }, icon = Icons.Outlined.PauseCircle)
        } else if (d.canManage) {
          V3Chip(StrOrderSlip.deskResume, selected = true, onClick = { ctl.act { repo.resume(d) } }, icon = Icons.Outlined.PlayCircle)
        }
        if (d.canSettings) V3Chip(StrOrderSlip.deskSettings, selected = editing, onClick = { editing = true }, icon = Icons.Outlined.Tune)
      }
    }
    if (status == "paused" && d.pauseSource == "auto") {
      Spacer(Modifier.height(8.dp))
      V3Message(V3Tone.WARNING, StrOrderSlip.deskAutoPaused)
    } else if (status == "paused" && d.pauseReason.isNotBlank()) {
      Spacer(Modifier.height(8.dp))
      V3Message(V3Tone.INFO, deskReasonText(d.pauseReason))
    }
    ctl.note?.let { (ok, text) ->
      Spacer(Modifier.height(8.dp))
      V3Message(if (ok) V3Tone.SUCCESS else V3Tone.ERROR, text)
    }
  }
  if (pausing) PauseSheet(onDismiss = { pausing = false }) { m, r, n -> pausing = false; ctl.act { repo.pause(d, m, r, n) } }
  if (editing) SettingsSheet(d, onDismiss = { editing = false }) { next -> editing = false; ctl.act { repo.settings(d, next) } }
}

/** ORD-03 «اتصال قطع است» و ORD-18 «شروعِ سفارشِ زمان‌بندی‌شده» — همان `OrderAlertBanners` ِ امروز */
@Composable
internal fun OrderAlertNotes(cfg: OrdersConfigDto, onOpen: (String) -> Unit) {
  val net = OrderAlertsLive.net.value
  if (net.down && (cfg.presence || OrderAlertsLive.deskOn.value)) {
    val ago = OrderAlerts.minutesSinceOk(net)?.let { StrOrderOps.netLastOk(it) + " " }.orEmpty()
    V3Message(V3Tone.ERROR, StrOrderOps.netDown, body = ago + StrOrderOps.netBack)
  }
  val now = OrderAlertsLive.serverNow.value
  for (a in OrderAlerts.visible(OrderAlertsLive.start.value, OrderAlertsLive.dismissed.value)) {
    val left = OrderAlerts.minutesLeft(a.scheduledFor, now)
    Column(Modifier.fillMaxWidth()) {
      V3Message(
        V3Tone.WARNING, StrOrderOps.startTitle(a.orderNo.toString()),
        body = if (left > 0) StrOrderOps.startIn(left) else StrOrderOps.startDue,
        action = StrOrderOps.startOpen, onAction = { onOpen(a.id) },
      )
      Row { V3TextButton(StrOrderOps.startSeen, { OrderAlertsLive.dismiss(a.id) }) }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseSheet(onDismiss: () -> Unit, onDone: (Int, String, String) -> Unit) {
  val f = remember { DeskPauseForm() }
  V3Sheet(onDismiss = onDismiss, title = StrOrderSlip.deskPause) { _ ->
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for (m in OrderDesk.PAUSE_MINUTES) V3Chip(StrOrderSlip.deskMinutes(m), selected = f.minutes == m, onClick = { f.minutes = m })
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for (r in OrderDesk.PAUSE_REASONS) V3Chip(deskReasonText(r), selected = f.reason == r, onClick = { f.reason = r })
    }
    Spacer(Modifier.height(12.dp))
    V3TextField(f.text, f::edit, StrOrderSlip.deskNote, Modifier.fillMaxWidth(), singleLine = false)
    Spacer(Modifier.height(18.dp))
    V3Button(StrOrderSlip.deskPause, { onDone(f.minutes, f.reason, f.text) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, enabled = f.ok)
  }
}

@Composable
private fun SettingsSheet(d: Desk, onDismiss: () -> Unit, onSave: (Desk) -> Unit) {
  val f = remember { DeskSettingsForm(d) }
  V3Sheet(onDismiss = onDismiss, title = StrOrderSlip.deskSettings) { _ ->
    V3Card(padding = PaddingValues(0.dp)) {
      V3ToggleRow(StrOrderSlip.deskAutoAccept, f.aaOn, { f.aaOn = it })
      V3Divider(start = 16.dp)
      V3ToggleRow(StrOrderSlip.deskAutoPause, f.apOn, { f.apOn = it })
    }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      V3TextField(f.aaMax, { f.aaMax = it }, StrOrderSlip.deskAutoAccept + " · " + StrOrderSlip.deskMaxOpen, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER)
      V3TextField(f.aaReady, { f.aaReady = it }, StrOrderSlip.deskReadyMinutes, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER)
      V3TextField(f.apMax, { f.apMax = it }, StrOrderSlip.deskAutoPause + " · " + StrOrderSlip.deskMaxOpen, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER)
      V3TextField(f.apOld, { f.apOld = it }, StrOrderSlip.deskOldestMinutes, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER)
      V3TextField(f.apMin, { f.apMin = it }, StrOrderSlip.deskPauseMinutes, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER)
    }
    Spacer(Modifier.height(18.dp))
    val next = f.result(d)
    V3Button(StrOrderSlip.deskSave, { next?.let(onSave) }, Modifier.fillMaxWidth(), enabled = next != null)
  }
}
