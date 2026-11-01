package ir.vil3ntec.tohid.ui.v3.online.orders

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreTime
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
import ir.vil3ntec.tohid.core.orders.OrderDelay
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.screens.OrderDelayForm
import ir.vil3ntec.tohid.ui.screens.delayReasonText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/*
 *  ORD-07 — «عقب انداختن» در جزئیاتِ سفارشِ نسخهٔ ۳. همان شرطِ رابطِ امروز (`OrderDelayEntry`): فقط با `delay` ِ
 *  `/config` (پرچمِ خاموشِ `shop.orders.delay`)، وضعیت در فهرستِ سرور و `orders.manage`؛ همان حالت و ثبت
 *  (`OrderDelayForm`، یک کلیدِ یکتا برای هر برگه). زمانِ تازه را سرور روی زمانِ ذخیره‌شدهٔ خودش می‌سازد.
 */

/** دکمهٔ «عقب انداختن» و برگه‌اش؛ [onDone] سفارشِ تازهٔ سرور و پیام */
@Composable
internal fun OrderDelayEntryV3(o: OrderDto, cfg: OrdersConfigDto?, repo: OrdersRepository, onDone: (OrderDto, String) -> Unit) {
  val c = cfg ?: return
  val d = c.delay
  if (d == null || !OrderDelay.can(o.status, d.statuses, c.permissions.manage)) return
  var open by remember(o.id) { mutableStateOf(false) }
  V3Button(
    StrOrderOps.delayAction, { open = true }, Modifier.fillMaxWidth().padding(top = 12.dp),
    kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.MoreTime,
  )
  if (open) DelaySheet(o, d.maxMinutes, OrderDelay.reasons(d.reasons), repo, onClose = { open = false }) { fresh, msg ->
    open = false
    onDone(fresh, msg)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DelaySheet(
  o: OrderDto,
  maxMinutes: Int,
  reasons: List<String>,
  repo: OrdersRepository,
  onClose: () -> Unit,
  onDone: (OrderDto, String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val f = remember(o.id) { OrderDelayForm(o, maxMinutes, reasons) }
  V3Sheet(onDismiss = onClose, title = StrOrderOps.delayTitle(o.orderNo.toString())) { _ ->
    val now = f.seen.readyAt
    Text(
      if (now != null) StrOrderOps.delayNow(OrdersModel.stamp(now)) else StrOrderOps.delayNoTime,
      style = V3Type.subhead, color = PhoneV3.colors.inkMuted,
    )
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for (m in OrderDelay.minutes(maxMinutes)) V3Chip(StrOrderOps.delayMinutes(m), selected = f.minutes == m, onClick = { f.minutes = m })
    }
    Spacer(Modifier.height(14.dp))
    Text(StrOrderOps.delayReason, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for (r in reasons) V3Chip(delayReasonText(r), selected = f.reason == r, onClick = { f.pick(r) })
    }
    Spacer(Modifier.height(12.dp))
    V3TextField(f.note, f::edit, StrOrderOps.delayNote, Modifier.fillMaxWidth(), helper = StrOrderOps.delayNoteHint, singleLine = false)
    f.error?.let {
      Spacer(Modifier.height(8.dp))
      V3Message(V3Tone.ERROR, it)
    }
    Spacer(Modifier.height(18.dp))
    V3Button(
      StrOrderOps.delayConfirm,
      { scope.launch { f.submit(repo, OrdersModel::stamp)?.let { (fresh, msg) -> onDone(fresh, msg) } } },
      Modifier.fillMaxWidth(), enabled = f.valid && !f.busy, loading = f.busy,
    )
    Spacer(Modifier.height(8.dp))
    V3Button(StrOrderOps.delayCancel, onClose, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
  }
}
