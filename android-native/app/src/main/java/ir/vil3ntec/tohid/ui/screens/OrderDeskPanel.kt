package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderSlip
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDesk
import ir.vil3ntec.tohid.data.repo.OrderDeskRepository.Desk
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  میزِ سفارش در صفحهٔ سفارش‌ها — ORD-03 (نشانگرِ «باز / شلوغ / بسته» و صفِ باز)، ORD-08 («شلوغم» با پایانِ خودکار،
 *  توقف با دلیل، ادامه)، ORD-04 و ORD-09 (قبول و توقفِ خودکار برای صاحب و مدیر). پشتِ پرچمِ خاموشِ `shop.orders.desk`:
 *  `GET /orders/desk` ⇒ `{enabled:false}` ⇒ هیچ‌چیز دیده نمی‌شود. همهٔ تصمیم‌ها با سرور (`lib/order-desk.js`).
 *  حالتِ مشترک با نسخهٔ ۳: `OrderDeskState.kt`.
 */

/** حالت و نوشتن در `OrderDeskState.kt` (همان که نسخهٔ ۳ می‌کشد)؛ این‌جا فقط نمایشِ رابطِ امروز */
@Composable
internal fun OrderDeskPanel(api: ApiClient) {
  val c = Shop.colors
  val ctl = rememberOrderDesk(api)
  val repo = ctl.repo
  val busy = ctl.busy
  var pausing by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf(false) }
  val d = ctl.desk ?: return
  if (!d.enabled) return
  val now = d.serverTime

  fun act(call: suspend () -> ApiResult<Desk>) = ctl.act(call)

  val status = OrderDesk.status(d.busyUntil, d.pausedUntil, now)
  Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    when (status) {
      "paused" -> StatusPill(StrOrderSlip.deskPaused(OrderDesk.minutesLeft(d.pausedUntil, now)), PillTone.DANGER)
      "busy" -> StatusPill(StrOrderSlip.deskBusy(OrderDesk.minutesLeft(d.busyUntil, now)), PillTone.WARNING)
      else -> StatusPill(StrOrderSlip.deskOpen, PillTone.SUCCESS)
    }
    StatusPill(StrOrderSlip.deskQueue(d.queueOpen), PillTone.NEUTRAL)
    if (d.canManage) {
      if (status == "open") {
        for (m in OrderDesk.BUSY_MINUTES) TohidSecondaryButton(StrOrderSlip.deskBusyFor(m), onClick = { act { repo.busy(d, m) } }, enabled = !busy)
        TohidSecondaryButton(StrOrderSlip.deskPause, onClick = { pausing = true }, enabled = !busy)
      } else {
        TohidButton(StrOrderSlip.deskResume, onClick = { act { repo.resume(d) } }, enabled = !busy)
      }
    }
    if (d.canSettings) TextButton(onClick = { editing = !editing }) { Text(StrOrderSlip.deskSettings, color = c.primary) }
  }
  if (status == "paused" && d.pauseSource == "auto") Text(StrOrderSlip.deskAutoPaused, style = MaterialTheme.typography.labelSmall, color = c.warning)
  else if (status == "paused" && d.pauseReason.isNotBlank()) Text(deskReasonText(d.pauseReason), style = MaterialTheme.typography.labelSmall, color = c.muted)
  ctl.note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }

  if (pausing) PauseSheet(onDismiss = { pausing = false }) { m, r, n -> pausing = false; act { repo.pause(d, m, r, n) } }
  if (editing) SettingsSheet(d, onDismiss = { editing = false }) { next ->
    editing = false
    act { repo.settings(d, next) }
  }
}

@Composable
private fun PauseSheet(onDismiss: () -> Unit, onDone: (Int, String, String) -> Unit) {
  val f = remember { DeskPauseForm() }
  TohidBottomSheet(title = StrOrderSlip.deskPause, onDismiss = onDismiss) {
    SheetBody {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in OrderDesk.PAUSE_MINUTES) TohidFilterChip(label = StrOrderSlip.deskMinutes(m), selected = f.minutes == m, onClick = { f.minutes = m })
      }
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in OrderDesk.PAUSE_REASONS) TohidFilterChip(label = deskReasonText(r), selected = f.reason == r, onClick = { f.reason = r })
      }
      TohidTextField(f.text, f::edit, label = StrOrderSlip.deskNote)
      TohidButton(StrOrderSlip.deskPause, onClick = { onDone(f.minutes, f.reason, f.text) }, enabled = f.ok)
    }
  }
}

@Composable
private fun SettingsSheet(d: Desk, onDismiss: () -> Unit, onSave: (Desk) -> Unit) {
  val f = remember { DeskSettingsForm(d) }
  TohidBottomSheet(title = StrOrderSlip.deskSettings, onDismiss = onDismiss) {
    SheetBody {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrOrderSlip.deskAutoAccept, Modifier.weight(1f), color = Shop.colors.text)
        Switch(checked = f.aaOn, onCheckedChange = { f.aaOn = it })
      }
      NumberField(f.aaMax, { f.aaMax = it }, StrOrderSlip.deskMaxOpen)
      NumberField(f.aaReady, { f.aaReady = it }, StrOrderSlip.deskReadyMinutes)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrOrderSlip.deskAutoPause, Modifier.weight(1f), color = Shop.colors.text)
        Switch(checked = f.apOn, onCheckedChange = { f.apOn = it })
      }
      NumberField(f.apMax, { f.apMax = it }, StrOrderSlip.deskMaxOpen)
      NumberField(f.apOld, { f.apOld = it }, StrOrderSlip.deskOldestMinutes)
      NumberField(f.apMin, { f.apMin = it }, StrOrderSlip.deskPauseMinutes)
      val next = f.result(d)
      TohidButton(StrOrderSlip.deskSave, enabled = next != null, onClick = { next?.let(onSave) })
    }
  }
}
