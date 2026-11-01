package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  برگهٔ «تعداد و قیمتِ این ردیف» (`sale/line/{i}`، نگه داشتنِ ردیفِ سبد): واحد (اگر کالا
 *  واحدِ فروشِ دوم دارد)، «تعداد (کیلوگرم)»، «قیمت هر واحد» با یادِ قیمتِ خودِ کالا، جمعِ
 *  شمارندهٔ همین ردیف، «ثبت» و «برداشتن از سبد». خطا زیرِ همان کادر و لرزشِ کوتاه.
 *
 *  @param onSave نوشته‌های فرم و حالتِ آغاز — صدازننده `LineModel.apply` را روی سبدِ همان لحظه می‌زند
 */
@Composable
fun LineSheet(
  info: LineInfo,
  onSave: (form: LineForm, start: LineForm) -> Unit,
  onRemove: () -> Unit,
  onClose: () -> Unit,
  /** نوشته‌های آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  initial: LineForm? = null,
) {
  val c = PhoneV3.colors
  val start = remember(info.id) { LineModel.start(info) }
  var form by remember(info.id) { mutableStateOf(initial ?: start) }
  var tried by remember { mutableStateOf(false) }
  var shake by remember { mutableIntStateOf(0) }
  val unit = LineModel.shownUnit(form, info)
  val qtyError = LineModel.qtyError(form, info).takeIf { tried || form.qtyText != start.qtyText }
  val priceError = LineModel.priceError(form)
  val total = rememberCountUp(LineModel.total(form, info, start))

  V3Sheet(onDismiss = onClose, title = info.name, detents = false) { closer ->
    if (info.hasSaleUnit) {
      V3Segmented(
        listOf(info.unit.ifBlank { StrSale.unitFallback }, info.saleUnit),
        if (form.inSaleUnit) 1 else 0,
        { form = LineModel.switchUnit(form, info, toSale = it == 1) },
      )
      Spacer(Modifier.height(14.dp))
    }
    Column(Modifier.v3Shake(shake.takeIf { it > 0 })) {
      V3TextField(
        form.qtyText, { form = form.copy(qtyText = it) },
        if (unit.isNotBlank()) StrSale.qtyIn(unit) else StrSale.qtyLabel,
        Modifier.fillMaxWidth(),
        kind = V3FieldKind.NUMBER,
        required = true,
        error = qtyError,
        helper = if (form.inSaleUnit) LineModel.quantity(form, info)?.let { "= ${qty(it)} ${info.unit}" } else null,
        suffix = unit.ifBlank { null },
      )
      Spacer(Modifier.height(12.dp))
      V3TextField(
        form.priceText, { form = form.copy(priceText = it, resetPrice = false) },
        StrSale.unitPrice,
        Modifier.fillMaxWidth(),
        kind = V3FieldKind.MONEY,
        error = priceError,
        helper = if (info.free) null else StrSale.basePriceHelper(PhoneSaleText.amount(info.basePrice)),
      )
      if (!info.free && info.unitPrice != null && !form.resetPrice) {
        V3TextButton(StrSale.backToItemPrice, { form = form.copy(priceText = qty(info.basePrice), resetPrice = true) }, Modifier.padding(top = 4.dp))
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(StrSale.lineTotal, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
      Text(PhoneSaleText.amount(total), style = V3Type.title, color = c.ink)
    }
    Spacer(Modifier.height(18.dp))
    V3Button(
      StrSale.submit,
      {
        tried = true
        if (LineModel.valid(form, info)) closer.close { onSave(form, start) } else shake++
      },
      Modifier.fillMaxWidth(),
      haptic = true,
    )
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      V3TextButton(StrSale.removeFromCartAlt, { closer.close(onRemove) }, danger = true)
    }
  }
}
