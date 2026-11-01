package ir.vil3ntec.tohid.ui.v3.online.orders

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.OrderBulk
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.print.OrderSlipPrint
import ir.vil3ntec.tohid.ui.screens.OrderBulkState
import ir.vil3ntec.tohid.ui.screens.print
import ir.vil3ntec.tohid.ui.screens.send
import ir.vil3ntec.tohid.ui.screens.targets
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/*
 *  ORD-19 — انتخابِ چندتایی در صفِ نسخهٔ ۳: «چاپِ برگه‌ها» و گذارِ گروهی. همان حالت و همان کارِ رابطِ امروز
 *  (`OrderBulkState` و `targets`/`print`/`send` ِ `OrderBulkUi.kt`)؛ فقط با `bulk` ِ `/config` (پرچمِ خاموشِ
 *  `shop.orders.bulk`). گذار فقط برای `orders.manage` و فقط وقتی همهٔ انتخاب‌شده‌ها یک وضعیت دارند.
 */

/** «انتخاب» / «تمام»؛ پرچمِ خاموش ⇒ هیچ */
@Composable
internal fun OrderBulkToggleV3(s: OrderBulkState, cfg: OrdersConfigDto?) {
  if (cfg?.bulk != true) return
  V3Chip(
    if (s.on) StrOrderOps.bulkDone else StrOrderOps.bulkSelect, selected = s.on,
    onClick = { s.on = !s.on; if (!s.on) s.ids = emptySet() }, icon = Icons.Outlined.Checklist,
  )
}

/** نوارِ کارِ گروهی بالای صف؛ [onMessage] پیامِ صفحه، [onChanged] صف و شمارش‌ها تازه شوند */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderBulkBarV3(
  s: OrderBulkState,
  cfg: OrdersConfigDto?,
  items: List<OrderDto>,
  repo: OrdersRepository,
  printer: OrderSlipPrint.Bulk,
  onMessage: (String) -> Unit,
  onChanged: () -> Unit,
) {
  val c = cfg ?: return
  if (!c.bulk || !s.on) return
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var confirm by remember { mutableStateOf<String?>(null) }
  val selected = items.filter { it.id in s.ids }
  val targets = s.targets(c, selected)
  Column(Modifier.fillMaxWidth()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      V3StatusPill(V3Status.INACTIVE, StrOrderOps.bulkCount(selected.size))
      V3TextButton(StrOrderOps.bulkAll, { s.ids = OrderBulk.all(s.ids, items.map { it.id }) })
      V3TextButton(StrOrderOps.bulkNone, { s.ids = emptySet() })
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      V3Button(
        StrOrderOps.bulkPrint(selected.size),
        { if (!busy) { busy = true; scope.launch { onMessage(s.print(repo, printer, selected)); busy = false } } },
        kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.Print, enabled = selected.isNotEmpty() && !busy, height = 40.dp,
      )
      for (to in targets) {
        V3Button(
          StrOrderOps.bulkMove(OrderText.action(to), selected.size), { confirm = to },
          kind = if (to in OrderText.DANGER) V3ButtonKind.DANGER else V3ButtonKind.PRIMARY, enabled = !busy, height = 40.dp,
        )
      }
    }
    val hint = when {
      selected.isEmpty() -> StrOrderOps.bulkPick(OrderBulk.MAX)
      c.permissions.manage && targets.isEmpty() && selected.map { it.status }.distinct().size > 1 -> StrOrderOps.bulkMixed
      else -> null
    }
    hint?.let {
      Spacer(Modifier.height(6.dp))
      Text(it, style = V3Type.caption, color = PhoneV3.colors.inkMuted)
    }
  }
  val to = confirm
  val from = selected.firstOrNull()?.status
  if (to != null && from != null) {
    ConfirmSheet(to, from, selected.size, busy, onDismiss = { confirm = null }) { reason, note, ready ->
      if (!busy) {
        busy = true
        scope.launch {
          s.send(repo, printer, items, selected, to, reason, note, ready, onMessage) { confirm = null; onChanged() }
          confirm = null
          busy = false
        }
      }
    }
  }
}

/** تأییدِ گذارِ گروهی: دلیل (همان فهرستِ گذارِ تکی) و زمانِ آماده برای «قبول» */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmSheet(to: String, from: String, n: Int, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String, String, Int?) -> Unit) {
  val choices = remember(from, to) { OrderStates.reasonChoices(from, to) }
  var reason by remember(to) { mutableStateOf(if (choices.size == 1) choices[0] else "") }
  var note by remember(to) { mutableStateOf("") }
  var ready by remember(to) { mutableStateOf<Int?>(15) }
  val need = OrderStates.needsReason(to)
  val valid = !need || (reason.isNotEmpty() && (reason != "other" || note.isNotBlank()))
  V3Sheet(onDismiss = onDismiss, title = StrOrderOps.bulkConfirmTitle(OrderText.action(to), n)) { _ ->
    if (need) {
      Text(StrOrderOps.bulkReason, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in choices) V3Chip(OrderText.reason(r), selected = reason == r, onClick = { reason = r })
      }
      Spacer(Modifier.height(12.dp))
      V3TextField(note, { if (it.length <= 300) note = it }, StrOrderOps.bulkNote, Modifier.fillMaxWidth(), singleLine = false)
      Spacer(Modifier.height(12.dp))
    }
    if (to == "accepted") {
      Text(StrOrderOps.bulkReady, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V3Chip(StrOrderOps.bulkNoTime, selected = ready == null, onClick = { ready = null })
        for (m in OrderText.READY_MINUTES) V3Chip(StrOrderOps.bulkMinutes(m), selected = ready == m, onClick = { ready = m })
      }
      Spacer(Modifier.height(12.dp))
    }
    Spacer(Modifier.height(6.dp))
    V3Button(
      StrOrderOps.bulkConfirm, { onConfirm(reason, note, if (to == "accepted") ready else null) }, Modifier.fillMaxWidth(),
      kind = if (to in OrderText.DANGER) V3ButtonKind.DANGER else V3ButtonKind.PRIMARY, enabled = valid && !busy, loading = busy,
    )
    Spacer(Modifier.height(8.dp))
    V3Button(StrOrderOps.bulkCancel, onDismiss, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
  }
}
