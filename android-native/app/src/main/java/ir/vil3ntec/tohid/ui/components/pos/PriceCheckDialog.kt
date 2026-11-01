package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.StrPriceCheck
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.PriceCheckData
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

// i18n: سه‌زبانه

/**
 *  قیمت‌پرسی بی فروش (POS-15، Ctrl+I در صندوقِ کامپیوتر): کادرِ بارکد یا نام، و برای کالای پیداشده قیمت‌ها،
 *  موجودی، آخرین خرید و نزدیک‌ترین انقضا (`PriceCheckData` ⇐ هستهٔ `PriceCheck`). هیچ چیز نمی‌نویسد و
 *  به فاکتور نمی‌افزاید. قیمتِ خرید، سود و فیِ آخرین خرید فقط با [showCost] (`reports.money`).
 */
@Composable
fun PriceCheckDialog(d: ShopData, today: String, showCost: Boolean, onDismiss: () -> Unit) {
  val c = Shop.colors
  var query by remember { mutableStateOf("") }
  var picked by remember { mutableStateOf<String?>(null) }
  val term = query.trim()
  val index = remember(d.products) { ShopStore.barcodeIndex(d) }
  val matches = remember(term, d.products) { PosCatalog.matches(d.products, term).take(8) }
  val exact = remember(term, index) { (BarcodeMatch.find(term, index) as? BarcodeMatch.Hit.Product)?.id }
  val id = picked ?: exact ?: matches.singleOrNull()?.id
  val view = remember(id, d, today) { id?.let { i -> d.products.find { it.id == i } }?.let { PriceCheckData.of(d, it, today) } }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

  Dialog(onDismissRequest = onDismiss) {
    Surface(color = c.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp)) {
        Text(StrPriceCheck.title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = query,
          onValueChange = { query = it; picked = null },
          singleLine = true,
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted) },
          placeholder = { Text(StrPriceCheck.hint, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.muted) },
          modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (view == null && matches.size > 1) {
          Spacer(Modifier.height(6.dp))
          matches.forEach { p ->
            Text(
              p.name,
              Modifier.fillMaxWidth().clickable { picked = p.id }.padding(vertical = 8.dp, horizontal = 4.dp),
              style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (view == null && term.isNotEmpty() && matches.isEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text(StrPriceCheck.notFound, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        view?.let { PriceCard(it, showCost) }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(StrPriceCheck.close) }
      }
    }
  }
}

@Composable
private fun PriceCard(v: PriceCheckData.View, showCost: Boolean) {
  val c = Shop.colors
  val p = v.product
  val unit = p.unit.ifBlank { "" }
  Spacer(Modifier.height(12.dp))
  Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.text)
  Spacer(Modifier.height(6.dp))
  CardRow(StrPriceCheck.retail, money(p.salePrice) + " ؋")
  if (p.wholesalePrice > 0) CardRow(StrPriceCheck.wholesale, money(p.wholesalePrice) + " ؋")
  if (showCost && p.purchasePrice > 0) {
    CardRow(StrPriceCheck.cost, money(p.purchasePrice) + " ؋")
    v.card.marginBp?.let { CardRow("", StrPriceCheck.margin((it / 100).toString()), if (it < 0) c.danger else c.muted) }
  }
  CardRow(StrPriceCheck.stock, (qty(v.stock) + " " + unit).trim(), if (v.stock <= 0) c.danger else c.text)
  val last = v.card.last
  CardRow(
    StrPriceCheck.lastPurchase,
    if (last == null) StrPriceCheck.noPurchase
    else listOfNotNull(
      formatDate(last.date),
      v.supplier,
      if (showCost) money(last.unitMinor / 100.0) + " ؋" else null,
    ).joinToString(" · "),
  )
  val near = v.card.nearest
  val days = v.card.nearestDays
  CardRow(
    StrPriceCheck.expiry,
    if (near == null || days == null) StrPriceCheck.noExpiry else formatDate(near.expiry) + " · " + StrPriceCheck.daysLeft(days),
    if (v.card.near) c.warning else c.text,
  )
  if (v.card.expiredMilli > 0) {
    CardRow("", StrPriceCheck.expired((qty(v.card.expiredMilli / 1000.0) + " " + unit).trim()), c.danger)
  }
}

@Composable
private fun CardRow(label: String, value: String, tint: Color = Shop.colors.text) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    Spacer(Modifier.width(12.dp))
    Text(value, style = MaterialTheme.typography.bodyMedium, color = tint, maxLines = 2)
  }
}

/** بخشِ تنظیماتِ «قیمت‌پرسی در صندوق» — مقدار و نوشتن از صدازننده (`PosPriceCheckPrefs`) */
@Composable
fun PosPriceCheckSettings(on: Boolean, onChange: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.Search,
    title = StrPriceCheck.section,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrPriceCheck.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
  }
}
