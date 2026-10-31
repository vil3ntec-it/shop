package ir.vil3ntec.tohid.ui.v3.sales.returns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3InvoiceItems
import ir.vil3ntec.tohid.ui.v3.V3InvoiceLine
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  رسیدِ برگشت (`sales/returns/{docId}`، RET-12) — جای `ReturnReceiptDialog`ِ پیشین: ردیف‌های
 *  برگشتی با «به موجودی/ضایعات»، خریدِ تازهٔ تعویض، جمع و بازپرداخت، دلیل و تأییدکننده؛ «چاپ»
 *  ⇐ برگهٔ چاپ، «فرستادن» (گوشی).
 */
@Composable
fun ReturnReceiptSheet(
  d: ShopData,
  docId: String,
  onPrint: () -> Unit,
  onSend: (() -> Unit)?,
  onClose: () -> Unit,
) {
  val c = PhoneV3.colors
  val doc = remember(d, docId) { ReturnFlow.doc(d, docId) } ?: return
  V3Sheet(onDismiss = onClose, title = StrSale.returnReceiptOf(doc.label), detents = false) {
    Text(
      (doc.sale?.let { StrSale.fromInvoice(it.invoiceLabel() ?: "0") } ?: StrSale.withoutReceipt) + " · " + displayDate(doc.date),
      style = V3Type.footnote,
      color = c.inkMuted,
    )
    Spacer(Modifier.height(12.dp))
    V3InvoiceItems(doc.rows.map { r ->
      V3InvoiceLine(ReturnFlow.rowName(d, r), "${qty(r.quantity)} · ${ReturnFlow.dispositionName(r.disposition)}", PhoneSaleText.amount(r.amount))
    })
    doc.exchange?.let { ex ->
      Spacer(Modifier.height(12.dp))
      V3InvoiceItems(
        d.saleItems.filter { it.saleId == ex.id }.map { si -> V3InvoiceLine(SalesEngine.itemName(d, si), "× ${qty(si.quantity)}", PhoneSaleText.amount(si.totalPrice)) },
        title = StrSale.newPurchaseOf(ex.invoiceLabel() ?: "0"),
      )
    }
    Spacer(Modifier.height(12.dp))
    V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
      Line(StrSale.returnAmount, doc.total, strong = doc.exchange == null)
      ReturnFlowModel.splitRows(doc.split).forEach { (label, a) -> Line(label, a) }
      doc.due?.let { due -> Line(if (due >= 0) StrSale.receiveFromCustomer else StrSale.payToCustomer, kotlin.math.abs(due), strong = true) }
    }
    Spacer(Modifier.height(8.dp))
    Text(StrSale.reasonIs(doc.reason), style = V3Type.footnote, color = c.inkMuted)
    if (doc.approvedBy.isNotBlank()) Text(StrSale.approvedBy(doc.approvedBy), style = V3Type.footnote, color = c.inkMuted)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrSale.print, onPrint, Modifier.weight(1f), icon = Icons.Outlined.Print)
      if (onSend != null) V3Button(StrSale.send, onSend, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Share)
    }
  }
}

@Composable
private fun Line(label: String, value: Double, strong: Boolean = false) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
    Text(PhoneSaleText.amount(value), style = if (strong) V3Type.headline else V3Type.callout, color = c.ink)
  }
}
