package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  برگهٔ «لغو فروش؟» (`sales/{id}/cancel`): هشدارِ موتور (`SalesEngine.cancelWarning`)، کادرِ
 *  اجباریِ «دلیلِ لغو» (در فاکتور و سابقه می‌ماند) و «لغو فروش» ِ قرمز که بی دلیل خاموش است.
 */
@Composable
fun CancelSheet(warning: String, onConfirm: (String) -> Unit, onClose: () -> Unit, start: String = "") {
  val c = PhoneV3.colors
  var reason by remember { mutableStateOf(start) }
  val chosen = remember { booleanArrayOf(false) }
  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, detents = false) { closer ->
    V3IconTile(Icons.Outlined.Block, danger = true, size = 48.dp)
    Spacer(Modifier.height(12.dp))
    Text(StrSale.cancelSaleQ, style = V3Type.title, color = c.ink)
    Spacer(Modifier.height(6.dp))
    Text(warning, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Start)
    Spacer(Modifier.height(14.dp))
    V3TextField(reason, { reason = it.take(120) }, StrSale.cancelReason, required = true)
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(
        StrSale.cancelSale,
        { chosen[0] = true; closer.close { onConfirm(reason.trim()) } },
        Modifier.weight(1f),
        kind = V3ButtonKind.DANGER,
        enabled = reason.isNotBlank(),
        haptic = true,
      )
    }
  }
}
