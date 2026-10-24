package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptCard

/**
 *  برگهٔ تسویه پس از ثبت: تیکِ موفقیت (لرزش را `CheckoutRoute` زده)، «فروش ثبت شد» با مبلغ،
 *  رسیدِ همان دفترِ لحظهٔ ثبت، و «چاپ» ⇐ `sales/{id}/print`، «فرستادن» (گوشی) و «فروشِ تازه».
 */
@Composable
fun CheckoutReceipt(
  d: ShopData,
  sale: Sale,
  storeName: String,
  onPrint: () -> Unit,
  onSend: (() -> Unit)?,
  onNewSale: () -> Unit,
) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth()) {
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
      V3SuccessCheck(visible = true, haptic = false)
      Spacer(Modifier.height(10.dp))
      Text(
        StrSale.saleRecorded(ir.vil3ntec.tohid.money(sale.finalTotal)),
        style = V3Type.title,
        color = c.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
      )
      Spacer(Modifier.height(16.dp))
      ReceiptCard(d, sale, storeName)
      Spacer(Modifier.height(16.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrSale.print, onPrint, Modifier.weight(1f), icon = Icons.Outlined.Print)
      if (onSend != null) V3Button(StrSale.send, onSend, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Share)
    }
    Spacer(Modifier.height(10.dp))
    V3Button(StrSale.newSale, onNewSale, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
  }
}
