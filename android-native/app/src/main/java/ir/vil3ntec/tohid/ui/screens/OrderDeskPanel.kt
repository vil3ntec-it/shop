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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderSlip
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDesk
import ir.vil3ntec.tohid.data.repo.OrderDeskRepository
import ir.vil3ntec.tohid.data.repo.OrderDeskRepository.Desk
import ir.vil3ntec.tohid.ui.OrderAlertsLive
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 *  میزِ سفارش در صفحهٔ سفارش‌ها — ORD-03 (نشانگرِ «باز / شلوغ / بسته» و صفِ باز)، ORD-08 («شلوغم» با پایانِ خودکار،
 *  توقف با دلیل، ادامه)، ORD-04 و ORD-09 (قبول و توقفِ خودکار برای صاحب و مدیر). پشتِ پرچمِ خاموشِ `shop.orders.desk`:
 *  `GET /orders/desk` ⇒ `{enabled:false}` ⇒ هیچ‌چیز دیده نمی‌شود. همهٔ تصمیم‌ها با سرور (`lib/order-desk.js`).
 */

private fun reasonText(r: String): String = when (r) {
  "busy" -> StrOrderSlip.deskReasonBusy
  "out_of_stock" -> StrOrderSlip.deskReasonStock
  "staff" -> StrOrderSlip.deskReasonStaff
  "technical" -> StrOrderSlip.deskReasonTech
  else -> StrOrderSlip.deskReasonOther
}

@Composable
internal fun OrderDeskPanel(api: ApiClient) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val repo = remember(api) { OrderDeskRepository(api) }
  var desk by remember { mutableStateOf<Desk?>(null) }
  var note by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
  var busy by remember { mutableStateOf(false) }
  var pausing by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf(false) }
  //  هر نیم دقیقه از سرور؛ «دقیقهٔ دیگر» با زمانِ همان پاسخِ سرور (ساعتِ دستگاه مرجع نیست)
  LaunchedEffect(Unit) {
    while (true) {
      repo.state().onSuccess { desk = it; OrderAlertsLive.deskOn.value = it.enabled }
      delay(30_000)
    }
  }
  val d = desk ?: return
  if (!d.enabled) return
  val now = d.serverTime

  fun act(call: suspend () -> ApiResult<Desk>) {
    if (busy) return
    busy = true
    note = null
    scope.launch {
      //  پاسخِ نوشتن زمانِ سرور ندارد ⇒ پرسشِ تازه، تا «دقیقهٔ دیگر» با همان ساعتِ سرور باشد
      call().onSuccess { repo.state().onSuccess { fresh -> desk = fresh } }.onFailure { f -> note = false to (if (f.code == "version_conflict") StrOrderSlip.deskChanged else f.userMessage) }
      busy = false
    }
  }

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
  else if (status == "paused" && d.pauseReason.isNotBlank()) Text(reasonText(d.pauseReason), style = MaterialTheme.typography.labelSmall, color = c.muted)
  note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }

  if (pausing) PauseSheet(onDismiss = { pausing = false }) { m, r, n -> pausing = false; act { repo.pause(d, m, r, n) } }
  if (editing) SettingsSheet(d, onDismiss = { editing = false }) { next ->
    editing = false
    act { repo.settings(d, next) }
  }
}

@Composable
private fun PauseSheet(onDismiss: () -> Unit, onDone: (Int, String, String) -> Unit) {
  var minutes by remember { mutableStateOf(OrderDesk.PAUSE_MINUTES[1]) }
  var reason by remember { mutableStateOf("") }
  var text by remember { mutableStateOf("") }
  TohidBottomSheet(title = StrOrderSlip.deskPause, onDismiss = onDismiss) {
    SheetBody {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in OrderDesk.PAUSE_MINUTES) TohidFilterChip(label = StrOrderSlip.deskMinutes(m), selected = minutes == m, onClick = { minutes = m })
      }
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in OrderDesk.PAUSE_REASONS) TohidFilterChip(label = reasonText(r), selected = reason == r, onClick = { reason = r })
      }
      TohidTextField(text, { text = it.take(200) }, label = StrOrderSlip.deskNote)
      TohidButton(StrOrderSlip.deskPause, onClick = { onDone(minutes, reason, text) }, enabled = OrderDesk.canPause(minutes, reason, text))
    }
  }
}

@Composable
private fun SettingsSheet(d: Desk, onDismiss: () -> Unit, onSave: (Desk) -> Unit) {
  var aaOn by remember { mutableStateOf(d.autoAcceptOn) }
  var aaMax by remember { mutableStateOf("${d.autoAcceptMaxOpen}") }
  var aaReady by remember { mutableStateOf("${d.autoAcceptReadyMinutes}") }
  var apOn by remember { mutableStateOf(d.autoPauseOn) }
  var apMax by remember { mutableStateOf("${d.autoPauseMaxOpen}") }
  var apOld by remember { mutableStateOf("${d.autoPauseOldestMinutes}") }
  var apMin by remember { mutableStateOf("${d.autoPauseMinutes}") }
  val aaMaxN = OrderDesk.bounded(aaMax, 1, 500)
  val aaReadyN = OrderDesk.bounded(aaReady, 1, 1440)
  val apMaxN = OrderDesk.bounded(apMax, 1, 500)
  val apOldN = OrderDesk.bounded(apOld, 1, 1440)
  val apMinN = OrderDesk.bounded(apMin, 5, 1440)
  TohidBottomSheet(title = StrOrderSlip.deskSettings, onDismiss = onDismiss) {
    SheetBody {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrOrderSlip.deskAutoAccept, Modifier.weight(1f), color = Shop.colors.text)
        Switch(checked = aaOn, onCheckedChange = { aaOn = it })
      }
      NumberField(aaMax, { aaMax = it }, StrOrderSlip.deskMaxOpen)
      NumberField(aaReady, { aaReady = it }, StrOrderSlip.deskReadyMinutes)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrOrderSlip.deskAutoPause, Modifier.weight(1f), color = Shop.colors.text)
        Switch(checked = apOn, onCheckedChange = { apOn = it })
      }
      NumberField(apMax, { apMax = it }, StrOrderSlip.deskMaxOpen)
      NumberField(apOld, { apOld = it }, StrOrderSlip.deskOldestMinutes)
      NumberField(apMin, { apMin = it }, StrOrderSlip.deskPauseMinutes)
      val ok = aaMaxN != null && aaReadyN != null && apMaxN != null && apOldN != null && apMinN != null
      TohidButton(StrOrderSlip.deskSave, enabled = ok, onClick = {
        if (aaMaxN != null && aaReadyN != null && apMaxN != null && apOldN != null && apMinN != null) {
          onSave(d.copy(
            autoAcceptOn = aaOn, autoAcceptMaxOpen = aaMaxN, autoAcceptReadyMinutes = aaReadyN,
            autoPauseOn = apOn, autoPauseMaxOpen = apMaxN, autoPauseOldestMinutes = apOldN, autoPauseMinutes = apMinN,
          ))
        }
      })
    }
  }
}
