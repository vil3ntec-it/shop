package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PauseCircleOutline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet

/**
 *  «…» ِ سربرگِ صندوق: سه کار، هر کدام مسیرِ خودش — «قلمِ آزاد» (`sale/free-line`)، «فروشِ
 *  سریع» (`sale/quick`) و «تاریخچهٔ فروش» (`sales`). زدن ⇒ برگه با حرکت پایین و بعد همان کار.
 */
@Composable
fun SaleMenuSheet(
  onFreeLine: () -> Unit,
  onQuickSale: () -> Unit,
  onHistory: () -> Unit,
  onClose: () -> Unit,
  /** «چند فاکتورِ هم‌زمان» روشن (POS-12) ⇒ نگه‌داشتن و فهرستِ فاکتورهای باز */
  onHold: (() -> Unit)? = null,
  onHeldList: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  val chosen = remember { booleanArrayOf(false) }
  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = StrSale.saleActions, detents = false) { closer ->
    fun go(action: () -> Unit) { chosen[0] = true; closer.close { onClose(); action() } }
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.hairline, shape)) {
      V3ListRow(StrSale.freeItem, subtitle = StrSale.freeItemShort, icon = Icons.Outlined.AddCircleOutline, hue = V3Hue.AMBER, onClick = { go(onFreeLine) })
      V3Divider(start = 64.dp)
      V3ListRow(StrSale.quickSale, subtitle = StrSale.quickHint, icon = Icons.Outlined.GridView, hue = V3Hue.BRAND, onClick = { go(onQuickSale) })
      V3Divider(start = 64.dp)
      V3ListRow(Str.salesHistoryAlt, icon = Icons.Outlined.History, hue = V3Hue.VIOLET, onClick = { go(onHistory) })
      onHold?.let { hold ->
        V3Divider(start = 64.dp)
        V3ListRow(StrPos.holdInvoice, icon = Icons.Outlined.PauseCircleOutline, hue = V3Hue.TEAL, onClick = { go(hold) })
      }
      onHeldList?.let { list ->
        V3Divider(start = 64.dp)
        V3ListRow(StrPos.heldInvoices, icon = Icons.Outlined.Layers, hue = V3Hue.SKY, onClick = { go(list) })
      }
    }
  }
}
