package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PriceCheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrVisualSearch
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
 *  AI-11: «جست‌وجو با عکس» فقط وقتی سرور «روشن» گفت ([onVisualSearch] غیرِ null).
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
  /** POS-15: «قیمت‌پرسی» (`PosPriceCheckPrefs`)؛ `null` ⇒ بی ردیف */
  onPriceCheck: (() -> Unit)? = null,
  /** AI-11: برگهٔ دوربین/گالریِ «جست‌وجو با عکس»؛ `null` (پرچمِ خاموش، بی حساب) ⇒ بی ردیف */
  onVisualSearch: (() -> Unit)? = null,
  /** OPS-06: «راهنما» ِ صندوق (`V3GuideSheet`)؛ `null` (بی راهنمای آماده) ⇒ بی ردیف */
  onGuide: (() -> Unit)? = null,
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
      onPriceCheck?.let { check ->
        V3Divider(start = 64.dp)
        V3ListRow(ir.vil3ntec.tohid.core.i18n.StrPriceCheck.title, subtitle = ir.vil3ntec.tohid.core.i18n.StrPriceCheck.menuHint, icon = Icons.Outlined.PriceCheck, hue = V3Hue.TEAL, onClick = { go(check) })
      }
      onVisualSearch?.let { find ->
        V3Divider(start = 64.dp)
        V3ListRow(StrVisualSearch.button, icon = Icons.Outlined.PhotoCamera, hue = V3Hue.SKY, onClick = { go(find) })
      }
      onHold?.let { hold ->
        V3Divider(start = 64.dp)
        V3ListRow(StrPos.holdInvoice, icon = Icons.Outlined.PauseCircleOutline, hue = V3Hue.TEAL, onClick = { go(hold) })
      }
      onHeldList?.let { list ->
        V3Divider(start = 64.dp)
        V3ListRow(StrPos.heldInvoices, icon = Icons.Outlined.Layers, hue = V3Hue.SKY, onClick = { go(list) })
      }
      onGuide?.let { help ->
        V3Divider(start = 64.dp)
        V3ListRow(ir.vil3ntec.tohid.core.i18n.StrKit.guide, icon = Icons.AutoMirrored.Outlined.HelpOutline, hue = V3Hue.BRAND, onClick = { go(help) })
      }
    }
  }
}
