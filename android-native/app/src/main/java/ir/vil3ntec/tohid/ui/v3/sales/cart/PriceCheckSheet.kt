package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPriceCheck
import ir.vil3ntec.tohid.data.PriceCheckData
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  POS-15 — برگهٔ `sale/price-check` (مرزِ پلتفرم: `PriceCheckRouteAndroid.kt`): «قیمت‌پرسی» ِ صندوقِ گوشی (منوی «…»)، همان کارِ Ctrl+I ِ کامپیوتر با همان سطرها
 *  (`PriceCheckData.lines`): نام یا بارکد ⇒ قیمت‌ها، موجودی، آخرین خرید و نزدیک‌ترین انقضا؛ قیمتِ خرید و سود فقط با
 *  `reports.money`. **فقط می‌خواند** — چیزی به سبد افزوده نمی‌شود. تنظیمِ خاموش (`PosPriceCheckPrefs`) ⇒ برگشت.
 */
@Composable
fun PriceCheckSheet(d: ShopData, today: String, showCost: Boolean, onClose: () -> Unit, places: Boolean = false) {
  val c = PhoneV3.colors
  var query by remember { mutableStateOf("") }
  var picked by remember { mutableStateOf<String?>(null) }
  val index = remember(d.products) { ShopStore.barcodeIndex(d) }
  val matches = remember(query, d.products) { PriceCheckData.matches(d, query) }
  val id = remember(query, picked, index) { PriceCheckData.pick(d, index, query, picked) }
  val view = remember(id, d, today, places) { id?.let { i -> d.products.find { it.id == i } }?.let { PriceCheckData.of(d, it, today, places) } }
  V3Sheet(onDismiss = onClose, title = StrPriceCheck.title) {
    V3SearchField(query, { query = it; picked = null }, StrPriceCheck.hint)
    Spacer(Modifier.height(10.dp))
    if (view == null && matches.size > 1) {
      V3Card(radius = 16.dp, padding = PaddingValues(0.dp)) {
        matches.forEachIndexed { i, p ->
          if (i > 0) V3Divider(start = 16.dp)
          V3ListRow(title = p.name, icon = Icons.Outlined.Inventory2, onClick = { picked = p.id })
        }
      }
    }
    if (view == null && query.isNotBlank() && matches.isEmpty()) Text(StrPriceCheck.notFound, style = V3Type.callout, color = c.inkMuted)
    view?.let { v ->
      Text(v.product.name, style = V3Type.headline, color = c.ink)
      Spacer(Modifier.height(8.dp))
      V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        PriceCheckData.lines(v, showCost).forEach { l ->
          Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(l.label, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
            Text(
              l.value, style = V3Type.callout,
              color = when (l.tone) {
                PriceCheckData.Tone.NORMAL -> c.ink
                PriceCheckData.Tone.MUTED -> c.inkMuted
                PriceCheckData.Tone.WARN -> PhoneV3Roles.toneIcon(c, ir.vil3ntec.tohid.ui.theme.V3Tone.WARNING)
                PriceCheckData.Tone.DANGER -> PhoneV3Roles.errorText(c)
              },
            )
          }
        }
      }
    }
  }
}
