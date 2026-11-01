package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  برگهٔ «قلمِ آزاد» (`sale/free-line`): راهنمای امروز، «نام» (مثلاً: کیسه)، «قیمت هر
 *  واحد» و «تعداد» کنارِ هم، جمعِ شمارنده و «افزودن». خطای هر کادر زیرِ خودش پس از
 *  نخستین زدن؛ نادرست ⇒ لرزشِ کوتاه.
 */
@Composable
fun FreeLineSheet(
  onAdd: (FreeLineForm) -> Unit,
  onClose: () -> Unit,
  /** نوشته‌های آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  initial: FreeLineForm = FreeLineForm(),
  showErrors: Boolean = false,
) {
  val c = PhoneV3.colors
  var form by remember { mutableStateOf(initial) }
  var tried by remember { mutableStateOf(showErrors) }
  var shake by remember { mutableIntStateOf(0) }
  val errors = if (tried) FreeLineModel.errors(form) else emptyMap()
  val total = rememberCountUp(FreeLineModel.total(form))

  V3Sheet(onDismiss = onClose, title = StrSale.freeItem, detents = false) { closer ->
    Text(StrSale.freeItemHint, style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))
    Column(Modifier.v3Shake(shake.takeIf { it > 0 })) {
      V3TextField(
        form.label, { form = form.copy(label = it) }, StrSale.nameLabel, Modifier.fillMaxWidth(),
        placeholder = StrSale.freeNameHint, required = true, error = errors[FreeLineModel.LABEL],
      )
      Spacer(Modifier.height(12.dp))
      Row {
        V3TextField(
          form.priceText, { form = form.copy(priceText = it) }, StrSale.unitPrice, Modifier.weight(1.4f),
          kind = V3FieldKind.MONEY, required = true, error = errors[FreeLineModel.PRICE],
        )
        Spacer(Modifier.width(10.dp))
        V3TextField(
          form.qtyText, { form = form.copy(qtyText = it) }, StrSale.qtyLabel, Modifier.weight(1f),
          kind = V3FieldKind.NUMBER, error = errors[FreeLineModel.QTY],
        )
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(StrSale.totalLabel, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
      Text(PhoneSaleText.amount(total), style = V3Type.title, color = c.ink)
    }
    Spacer(Modifier.height(18.dp))
    V3Button(
      StrKit.add,
      {
        tried = true
        if (FreeLineModel.errors(form).isEmpty()) closer.close { onAdd(form) } else shake++
      },
      Modifier.fillMaxWidth(),
      haptic = true,
    )
  }
}
