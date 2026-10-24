package ir.vil3ntec.tohid.ui.v3.sales.returns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel

/**
 *  برگهٔ مرجوعیِ ساده (`sales/{id}/return`، «برگشت و تعویض» خاموش) — جای `ReturnDialog`ِ
 *  پیشین: هر قلمِ قابلِ برگشت «{قلم} — قابل مرجوعی: ۳ عدد» با −/+ و کادرِ مقدار، مبلغِ مرجوعی
 *  (همان که ثبت می‌شود)، دلیلِ اختیاری و «ثبت مرجوعی». چیزی برای برگشت نیست ⇒ متنِ خالی.
 */
@Composable
fun ReturnSheet(
  d: ShopData,
  sale: Sale,
  onConfirm: (quantities: Map<String, Double>, reason: String) -> Unit,
  onClose: () -> Unit,
  start: Map<String, String> = emptyMap(),
) {
  val c = PhoneV3.colors
  val lines = remember(d, sale.id) { ReturnModel.lines(d, sale) }
  var texts by remember { mutableStateOf(start) }
  var reason by remember { mutableStateOf("") }
  val total = ReturnModel.total(sale, lines, texts)
  val shown = rememberCountUp(total)
  val bad = lines.any { ReturnModel.error(texts[it.item.id], it) != null }
  val chosen = remember { booleanArrayOf(false) }

  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = StrSale.returnTitle(ReceiptModel.number(sale)), detents = false) { closer ->
    if (lines.isEmpty()) {
      V3EmptyState(Icons.AutoMirrored.Outlined.AssignmentReturn, StrSale.nothingToReturn)
      return@V3Sheet
    }
    V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
      lines.forEachIndexed { i, l ->
        if (i > 0) V3Divider(start = 14.dp)
        val text = texts[l.item.id].orEmpty()
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
          Text(l.name, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(ReturnModel.leftText(l), style = V3Type.footnote, color = c.inkMuted)
          Spacer(Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V3IconButton(Icons.Outlined.Remove, StrSale.oneLess, size = 40.dp) { texts = texts + (l.item.id to ReturnModel.bump(text, l, -1)) }
            V3TextField(
              value = text,
              onValueChange = { texts = texts + (l.item.id to it) },
              label = StrSale.returnQty,
              kind = V3FieldKind.NUMBER,
              suffix = l.unit.ifBlank { null },
              error = ReturnModel.error(text, l),
              modifier = Modifier.weight(1f),
            )
            V3IconButton(Icons.Outlined.Add, StrSale.oneMore, size = 40.dp) { texts = texts + (l.item.id to ReturnModel.bump(text, l, +1)) }
          }
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(StrSale.returnAmount, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
      Text(PhoneSaleText.amount(shown), style = V3Type.headline, color = c.ink)
    }
    Spacer(Modifier.height(12.dp))
    V3TextField(reason, { reason = it }, StrSale.reasonOptional)
    Spacer(Modifier.height(16.dp))
    V3Button(
      StrSale.recordReturn,
      {
        val q = ReturnModel.quantities(texts)
        chosen[0] = true
        closer.close { onConfirm(q, reason.trim()) }
      },
      Modifier.fillMaxWidth(),
      enabled = ReturnModel.quantities(texts).isNotEmpty() && !bad,
      haptic = true,
    )
  }
}
