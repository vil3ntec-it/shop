package ir.vil3ntec.tohid.ui.components.pos

// i18n: سه‌زبانه (`StrSale`)

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.product.ProductAlternatives
import ir.vil3ntec.tohid.core.sale.AutoFees
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  POS-36 — بخشِ «هزینهٔ خودکار» در تنظیمات: کادرِ چندخطی (هر خط «نام = مبلغ» یا «کالا، کالا: نام = مبلغ»)،
 *  ایرادِ نخستین خط همان‌جا، و ذخیره فقط وقتی همه درست‌اند. خودش نمی‌خواند و نمی‌نویسد (`AutoFeesPref`).
 */
@Composable
fun AutoFeesSettings(rules: List<AutoFees.Rule>, d: ShopData, onSave: (List<AutoFees.Rule>) -> Unit) {
  val c = Shop.colors
  val names = remember(d.products) { d.products.associate { it.id to it.name } }
  fun shown(r: List<AutoFees.Rule>) = AutoFees.format(r, { names[it] ?: it }, { ir.vil3ntec.tohid.qty(it / 100.0) })
  var text by remember(rules) { mutableStateOf(shown(rules)) }
  var saved by remember { mutableStateOf(false) }
  val resolve = remember(d.products) { resolver(d.products.map { ProductAlternatives.Ref(it.id, it.name, it.barcodes) }) }
  val parsed = AutoFees.parse(text, resolve)
  SettingsSection(
    icon = Icons.Filled.ShoppingBag,
    title = StrSale.feesTitle,
    subtitle = if (rules.isEmpty()) StrShell.switchOff else StrSale.feesCount(ir.vil3ntec.tohid.plain(rules.size)),
    tint = if (rules.isEmpty()) c.muted else c.success,
  ) {
    Text(StrSale.feesNote, style = MaterialTheme.typography.labelSmall, color = c.muted2)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = text,
      onValueChange = { text = it.take(AutoFees.MAX_RULES * 120); saved = false },
      label = { Text(StrSale.feesTitle) },
      supportingText = { parsed.problems.firstOrNull()?.let { Text(StrSale.feesBad(it.first), color = c.danger) } },
      singleLine = false,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(enabled = parsed.problems.isEmpty(), onClick = { onSave(parsed.rules); saved = true }) { Text(StrSale.feesSave) }
      if (saved) { Spacer(Modifier.width(8.dp)); Text(StrSale.feesSaved, color = c.success, style = MaterialTheme.typography.labelSmall) }
    }
  }
}

/** نام یا بارکد ⇒ شناسه: بارکدِ کامل، سپس نامِ دقیق با کلیدِ مقایسه */
private fun resolver(refs: List<ProductAlternatives.Ref>): (String) -> String? {
  val byCode = HashMap<String, String>()
  val byName = HashMap<String, String>()
  refs.forEach { r -> r.barcodes.forEach { byCode.putIfAbsent(it.trim(), r.id) }; byName.putIfAbsent(TextNormalizer.compareKey(r.name), r.id) }
  return { t -> byCode[ir.vil3ntec.tohid.latinDigits(t.trim())] ?: byName[TextNormalizer.compareKey(t)] }
}
