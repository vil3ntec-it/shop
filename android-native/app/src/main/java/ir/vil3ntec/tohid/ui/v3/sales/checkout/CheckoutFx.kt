package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.FxTill
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.data.FxTillFlow
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime

/**
 *  PAY-10 — کادرِ «ارزِ نقد» در پرداختِ ترکیبی (`CheckoutPaySplit`)، فقط با جدولِ نرخِ روشنِ دکان (`state.fxRates`):
 *  ارز با تراشه، مبلغ به همان ارز، خطِ نرخ با منبع و زمان، و ارزشِ افغانیِ همان لحظه. حساب همه `FxTillFlow`/هسته؛
 *  پیامِ خطا (نرخِ کهنه، ارزِ نالازم…) همان پیامِ زیرِ خلاصهٔ پنل است (`PayFlow.message`).
 */
@Composable
internal fun CheckoutFxBox(state: PayPanelState, bad: Boolean, onSubmit: () -> Unit) {
  val c = PhoneV3.colors
  val t = state.fxTender() ?: return
  val rate = t.rates.firstOrNull { it.currency == t.currency }
  val afn = when (val r = FxTillFlow.record(t)) {
    is PayResult.Ok -> r.value.toAmount
    else -> null
  }
  V3Card(radius = 16.dp, padding = PaddingValues(vertical = 10.dp)) {
    Text(StrFx.tillTitle, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(horizontal = 12.dp))
    V3ChipRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
      t.rates.forEach { r -> V3Chip(FxTillFlow.name(r.currency), r.currency == t.currency, { state.fxCurrency = r.currency }) }
    }
    V3TextField(
      value = state.fxAmount,
      onValueChange = { state.fxAmount = it },
      label = StrFx.amountIn(FxTillFlow.name(t.currency)),
      kind = V3FieldKind.NUMBER,
      suffix = t.currency,
      error = if (bad) StrSale.checkRow else null,
      imeAction = androidx.compose.ui.text.input.ImeAction.Done,
      onImeAction = onSubmit,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
    )
    rate?.let { r ->
      Text(
        StrFx.rateNote(FxTill.rateLine(r.rate, r.per, r.currency), r.source, SaleTime.stamp(r.at)),
        style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp),
      )
    }
    afn?.let { Text(StrFx.afnValue(PhoneSaleText.amount(PayFlow.major(it))), style = V3Type.callout, color = c.ink, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) }
    Text(StrFx.changeNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp))
  }
}
