package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Numbers
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  «سفارشِ دستی» (`orders/new`، V3Form): از کجا (منبع‌های سرور)، تحویل «حضوری | ارسال»
 *  (ارسال ⇐ نشانی، نشانه، هزینهٔ ارسال)، مشتری، اقلام (ردیف «مقدار × فی»، زدن ⇐ برگهٔ قلم،
 *  کشیدن ⇐ حذف با «برگرداندن»)، یادداشت و «سفارشِ آزمایشی». خطای هر کادر زیرِ خودش؛
 *  نوشتهٔ ذخیره‌نشده با برگشت «دور ریخته شود؟» می‌پرسد. ثبت یک کلیدِ یکتا دارد (صدازننده).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewOrderScreen(
  sources: List<String>,
  busy: Boolean,
  failure: String?,
  onSubmit: (OrderDraft.Form) -> Unit,
  onBack: () -> Unit,
  start: OrderDraft.Form = OrderDraft.Form(),
  showErrors: Boolean = false,
) {
  val toasts = LocalV3Toasts.current
  var form by remember { mutableStateOf(start) }
  var tried by remember { mutableIntStateOf(if (showErrors) 1 else 0) }
  var editing by remember { mutableStateOf<Int?>(null) }
  val errors = if (tried > 0) NewOrderModel.errors(form) else NewOrderModel.Errors()
  val delivery = form.fulfillment == "delivery"
  fun err(f: NewOrderModel.Field) = errors.fields[f]
  fun lines(l: List<OrderDraft.LineInput>) { form = form.copy(lines = l) }

  V3Form(
    title = StrOnline.manualOrder,
    primary = StrOnline.placeOrder,
    onPrimary = {
      if (NewOrderModel.errors(form).isEmpty) onSubmit(form) else tried++
    },
    onBack = onBack,
    loading = busy,
    dirty = NewOrderModel.dirty(form),
    summary = NewOrderModel.filled(form).takeIf { it > 0 }?.let(StrOnline::items),
  ) {
    if (failure != null) V3Message(V3Tone.WARNING, failure, Modifier.padding(top = 12.dp).v3Shake(failure))

    V3Section(StrOnline.fromWhere) {
      FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.forEach { s -> V3Chip(StrOnline.orderSource(s), form.source == s, { form = form.copy(source = s) }) }
      }
    }

    V3Section(StrOnline.fulfilment) {
      Column(Modifier.padding(12.dp)) {
        V3Segmented(
          listOf(StrOnline.pickupFromShop, StrOnline.delivery),
          if (delivery) 1 else 0,
          { form = form.copy(fulfillment = if (it == 1) "delivery" else "pickup") },
        )
        V3Collapse(delivery) {
          Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            V3TextField(
              form.addressText, { form = form.copy(addressText = it.take(NewOrderModel.ADDRESS_MAX)) }, StrOnline.address,
              error = err(NewOrderModel.Field.ADDRESS), singleLine = false, modifier = Modifier.fillMaxWidth(),
            )
            V3TextField(form.landmark, { form = form.copy(landmark = it.take(NewOrderModel.LANDMARK_MAX)) }, StrOnline.landmark, modifier = Modifier.fillMaxWidth())
            V3TextField(
              form.deliveryFee, { form = form.copy(deliveryFee = it.take(NewOrderModel.NUMBER_MAX)) }, StrOnline.deliveryFee,
              kind = V3FieldKind.MONEY, error = err(NewOrderModel.Field.FEE), modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }

    V3Section(StrOnline.customer) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        V3TextField(
          form.name, { form = form.copy(name = it.take(NewOrderModel.NAME_MAX)) }, StrOnline.customerName,
          required = true, error = err(NewOrderModel.Field.NAME), modifier = Modifier.fillMaxWidth(),
        )
        V3TextField(
          form.phone, { form = form.copy(phone = it.take(NewOrderModel.PHONE_MAX)) }, StrOnline.phone,
          kind = V3FieldKind.PHONE, required = delivery, error = err(NewOrderModel.Field.PHONE), modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    V3Section(StrOnline.itemsTitle, footer = err(NewOrderModel.Field.LINES)) {
      form.lines.forEachIndexed { i, l ->
        if (i > 0) V3Divider(start = 64.dp)
        val lineError = errors.line(i)
        V3ListRow(
          title = l.name.ifBlank { StrOnline.lineN(i + 1) },
          subtitle = lineError ?: lineDetail(l),
          icon = Icons.Outlined.ShoppingBag,
          hue = V3Hue.AMBER,
          trailing = if (lineError != null) ({ Icon(Icons.Outlined.ErrorOutline, contentDescription = lineError, tint = PhoneV3Roles.errorText(PhoneV3.colors)) }) else null,
          endActions = if (form.lines.size > 1) listOf(
            V3SwipeAction(Icons.Outlined.DeleteOutline, StrOnline.removeLine, V3ButtonKind.DANGER) {
              val before = form.lines
              lines(before.filterIndexed { j, _ -> j != i })
              toasts?.undo(StrOnline.lineRemoved) { lines(before) }
            },
          ) else emptyList(),
          onClick = { editing = i },
        )
      }
      V3Divider()
      FlowRow(Modifier.padding(12.dp)) {
        V3AddChip(StrOnline.addLine) {
          lines(form.lines + OrderDraft.LineInput(qty = "1"))
          editing = form.lines.lastIndex
        }
      }
    }

    V3Section(StrOnline.note) {
      V3TextField(
        form.note, { form = form.copy(note = it.take(NewOrderModel.NOTE_MAX)) }, StrOnline.note,
        singleLine = false, modifier = Modifier.fillMaxWidth().padding(12.dp),
      )
    }
    V3Section(null) {
      V3ToggleRow(StrOnline.testOrder, form.test, { form = form.copy(test = it) }, subtitle = StrOnline.testHelper, icon = Icons.Outlined.Science, hue = V3Hue.VIOLET)
    }
    Spacer(Modifier.height(24.dp))
  }

  editing?.let { i -> form.lines.getOrNull(i) }?.let { line ->
    val i = editing ?: return@let
    OrderLineSheet(
      index = i,
      start = line,
      onSave = { n -> lines(form.lines.toMutableList().also { it[i] = n }) },
      onDismiss = { editing = null },
      onDelete = if (form.lines.size > 1) ({
        val before = form.lines
        lines(before.filterIndexed { j, _ -> j != i })
        toasts?.undo(StrOnline.lineRemoved) { lines(before) }
      }) else null,
    )
  }
}

/** «۲ × ۱۵۰ افغانی» — همان چیزی که کاربر نوشته، بی حساب (مبلغ را سرور می‌سازد) */
private fun lineDetail(l: OrderDraft.LineInput): String =
  StrOnline.lineDetail(l.qty.ifBlank { "1" }, l.price.takeIf { it.isNotBlank() }?.let(V3Numbers::group) ?: "—")
