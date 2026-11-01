package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  برگهٔ یک قلمِ سفارشِ دستی: نام، مقدار و فی (افغانی) با خطای درجا. «ذخیره» فقط قلمِ
 *  درست را برمی‌گرداند؛ «حذف» (اگر هست) قلم را برمی‌دارد و صفحه «برگرداندن» می‌دهد.
 */
@Composable
fun OrderLineSheet(
  index: Int,
  start: OrderDraft.LineInput,
  onSave: (OrderDraft.LineInput) -> Unit,
  onDismiss: () -> Unit,
  onDelete: (() -> Unit)? = null,
) {
  var line by remember { mutableStateOf(start) }
  var tried by remember { mutableIntStateOf(0) }
  val errors: Map<NewOrderModel.LineField, String> = if (tried == 0) emptyMap()
  else NewOrderModel.lineErrors(line).ifEmpty {
    if (NewOrderModel.isBlank(line)) mapOf(NewOrderModel.LineField.NAME to StrOnline.errLineName) else emptyMap()
  }
  V3Sheet(onDismiss = onDismiss, title = StrOnline.lineN(index + 1), detents = false) { closer ->
    V3TextField(
      line.name, { line = line.copy(name = it.take(NewOrderModel.LINE_NAME_MAX)) }, StrOnline.lineName,
      required = true, error = errors[NewOrderModel.LineField.NAME], modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(
        line.qty, { line = line.copy(qty = it.take(NewOrderModel.NUMBER_MAX)) }, StrOnline.qty,
        kind = V3FieldKind.NUMBER, error = errors[NewOrderModel.LineField.QTY], modifier = Modifier.weight(1f),
      )
      V3TextField(
        line.price, { line = line.copy(price = it.take(NewOrderModel.PRICE_MAX)) }, StrOnline.unitPrice,
        kind = V3FieldKind.MONEY, error = errors[NewOrderModel.LineField.PRICE], modifier = Modifier.weight(1.3f),
      )
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (onDelete != null) {
        V3Button(StrOnline.removeLine, { closer.close(onDelete) }, Modifier.weight(1f), kind = V3ButtonKind.DANGER, icon = Icons.Outlined.DeleteOutline)
      }
      V3Button(
        StrKit.save,
        { if (NewOrderModel.lineErrors(line).isEmpty() && !NewOrderModel.isBlank(line)) closer.close { onSave(line) } else tried++ },
        Modifier.weight(1.4f).v3Shake(tried.takeIf { it > 0 }),
        haptic = true,
      )
    }
  }
}
