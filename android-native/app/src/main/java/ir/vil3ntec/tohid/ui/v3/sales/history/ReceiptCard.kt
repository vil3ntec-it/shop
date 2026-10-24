package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3InvoiceItems
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  رسیدِ یک فاکتور (صفحهٔ `sales/{id}` و پس از ثبت در تسویه): سرِ رسید (نامِ دکان، «فاکتور
 *  فروش #…»، تاریخ · ساعت · روشِ پرداخت، قرصِ حال)، مشتری (زدن ⇐ حسابش)، اقلام و جمع‌ها.
 *  همه از `ReceiptModel` — عددی این‌جا ساخته نمی‌شود.
 */
@Composable
fun ReceiptCard(
  d: ShopData,
  sale: Sale,
  storeName: String,
  modifier: Modifier = Modifier,
  onDebtor: ((String) -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val state = ReceiptModel.state(sale)
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    V3Card(radius = 16.dp, padding = PaddingValues(16.dp)) {
      if (storeName.isNotBlank()) {
        Text(storeName, style = V3Type.subhead, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
      }
      Text(
        ReceiptModel.title(sale),
        style = V3Type.title,
        color = c.ink,
        textAlign = TextAlign.Center,
        textDecoration = if (state == SaleState.CANCELLED) TextDecoration.LineThrough else null,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(2.dp))
      Text(ReceiptModel.subtitle(sale), style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(10.dp))
      V3StatusPill(statusOf(state), ReceiptModel.stateLabel(sale), Modifier.align(Alignment.CenterHorizontally))
    }
    ReceiptModel.debtorName(d, sale)?.let { name ->
      V3Card(radius = 16.dp, padding = PaddingValues(0.dp)) {
        V3ListRow(
          title = name,
          subtitle = StrSale.customerWord,
          icon = Icons.Outlined.Person,
          hue = V3Hue.CORAL,
          chevron = onDebtor != null,
          onClick = sale.debtorId?.let { id -> onDebtor?.let { { it(id) } } },
        )
      }
    }
    V3InvoiceItems(ReceiptModel.lines(d, sale), title = StrSale.itemsWord)
    SaleTotals(ReceiptModel.totals(sale))
  }
}

/** کارتِ جمع‌ها — رسید و تسویه یکی‌اند */
@Composable
fun SaleTotals(rows: List<TotalRow>, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  V3Card(modifier, radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
    rows.forEach { r ->
      if (r.strong) V3Divider()
      Row(Modifier.fillMaxWidth().padding(vertical = if (r.strong) 8.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(r.label, style = if (r.strong) V3Type.headline else V3Type.callout, color = if (r.strong) c.ink else c.inkMuted, modifier = Modifier.weight(1f))
        Text(
          PhoneSaleText.amount(r.amount),
          style = if (r.strong) V3Type.headline else V3Type.callout,
          color = if (r.warn) PhoneV3Roles.toneIcon(c, V3Tone.WARNING) else c.ink,
        )
      }
    }
  }
}

private fun statusOf(s: SaleState): V3Status = when (s) {
  SaleState.DONE -> V3Status.DELIVERED
  SaleState.CANCELLED -> V3Status.CANCELLED
  SaleState.ONLINE -> V3Status.IN_DELIVERY
}
