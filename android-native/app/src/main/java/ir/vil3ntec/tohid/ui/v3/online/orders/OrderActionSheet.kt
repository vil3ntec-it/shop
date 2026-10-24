package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  برگهٔ گذارِ سفارش: عنوانِ «{کار} — سفارش #…»، تراشه‌های دلیل (و توضیحِ لازم برای «دلیلِ
 *  دیگر»)، تراشه‌های زمانِ آماده هنگامِ قبول، هشدارِ بازپرداخت، و دکمهٔ همان کار (قرمز برای
 *  رد/لغو). خطای سرور درجا با لرزش؛ برگه فقط پس از پاسخِ موفق بسته می‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderActionSheet(
  order: OrderDto,
  to: String,
  busy: Boolean,
  error: String?,
  onConfirm: (OrderActionModel.Form) -> Unit,
  onDismiss: () -> Unit,
  start: OrderActionModel.Form = OrderActionModel.start(order, to),
) {
  val c = PhoneV3.colors
  var form by remember(order.id, to) { mutableStateOf(start) }
  var tried by remember { mutableIntStateOf(0) }
  val choices = OrderActionModel.choices(order, to)
  val danger = OrdersModel.danger(to)
  V3Sheet(onDismiss = onDismiss, title = StrOnline.actionTitle(StrOnline.orderAction(to), order.orderNo)) { _ ->
    Text(
      listOf(order.customer.name, StrOnline.afn(order.totalMinor)).filter { it.isNotBlank() }.joinToString(" · ") + "\n" +
        StrOnline.movesTo(StrOnline.orderStatus(to)),
      style = V3Type.callout,
      color = c.inkMuted,
    )
    if (OrderActionModel.asksReason(to)) {
      Spacer(Modifier.height(16.dp))
      Text(StrOnline.reason, style = V3Type.subhead, color = c.ink)
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { r -> V3Chip(StrOnline.orderReason(r), form.reason == r, { form = form.copy(reason = r) }) }
      }
      Spacer(Modifier.height(12.dp))
      V3TextField(
        form.note, { if (it.length <= OrderActionModel.NOTE_MAX) form = form.copy(note = it) },
        label = StrOnline.explain,
        helper = StrOnline.explainHelper,
        error = if (tried > 0 && OrderActionModel.noteMissing(to, form)) StrOnline.explainHelper else null,
        required = form.reason == "other",
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (OrderActionModel.asksReady(to)) {
      Spacer(Modifier.height(16.dp))
      Text(StrOnline.readyIn, style = V3Type.subhead, color = c.ink)
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V3Chip(StrOnline.noTime, form.ready == null, { form = form.copy(ready = null) })
        OrderText.READY_MINUTES.forEach { m -> V3Chip(StrOnline.minutes(m), form.ready == m, { form = form.copy(ready = m) }) }
      }
    }
    if (OrderActionModel.refund(to)) {
      Spacer(Modifier.height(16.dp))
      V3Message(V3Tone.WARNING, StrOnline.refundWarn(StrOnline.afn(order.totalMinor)))
    }
    if (error != null) {
      Spacer(Modifier.height(12.dp))
      V3Message(V3Tone.ERROR, error, Modifier.v3Shake(error))
    }
    Spacer(Modifier.height(20.dp))
    V3Button(
      StrOnline.orderAction(to),
      {
        if (OrderActionModel.valid(to, form)) onConfirm(form) else tried++
      },
      Modifier.fillMaxWidth().padding(bottom = 4.dp).v3Shake(tried.takeIf { it > 0 }),
      kind = if (danger) V3ButtonKind.DANGER else V3ButtonKind.PRIMARY,
      enabled = !OrderActionModel.asksReason(to) || form.reason.isNotEmpty(),
      loading = busy,
      haptic = true,
    )
  }
}
