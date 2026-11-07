package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrScanPages
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.product.PackBarcodes
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  PRD-04 — بسته‌ها و بارکدِ هر واحد در فرمِ کالا (گوشی و کامپیوتر)، فقط با `ShopPolicy.productTools`.
 *  هر بسته: نام (کارتن)، اندازه به واحدِ کالا (۲۴ دانه)، بارکدهای روی همان بسته و قیمتِ اختیاریِ یک بسته در
 *  لیستِ قیمت (S05؛ صندوق فقط وقتی واحدِ ردیف را عوض کند و `TillGoodsPref.units` روشن باشد). سنجش کارِ موتور است
 *  (`WarehouseEngine` ⇐ `PackBarcodes.check`): نام و اندازه و بارکد لازم، بارکد بین کالاها و واحدها تکراری نه.
 */
@Composable
fun PackEditor(packs: List<PackBarcodes.Draft>, unit: String, onChange: (List<PackBarcodes.Draft>) -> Unit) {
  val c = Shop.colors
  val u = unit.trim().ifEmpty { StrProducts.unitWord }
  fun set(i: Int, p: PackBarcodes.Draft) = onChange(packs.mapIndexed { j, old -> if (j == i) p else old })
  Column(Modifier.fillMaxWidth()) {
    Text(StrScanPages.packsTitle, style = MaterialTheme.typography.labelMedium, color = c.muted)
    Text(
      StrScanPages.packHelp1(u) +
        StrScanPages.packHelp2(u),
      style = MaterialTheme.typography.labelSmall,
      color = c.muted,
    )
    packs.forEachIndexed { i, p ->
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = p.name,
          onValueChange = { set(i, p.copy(name = it)) },
          label = { Text(StrScanPages.packName) },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = p.size,
          onValueChange = { set(i, p.copy(size = ir.vil3ntec.tohid.numericInput(it))) },
          label = { Text(StrScanPages.howMany(u)) },
          singleLine = true,
          modifier = Modifier.weight(0.8f),
        )
        TextButton(onClick = { onChange(packs.filterIndexed { j, _ -> j != i }) }) { Text(StrSettings.remove) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = p.barcodes,
          onValueChange = { set(i, p.copy(barcodes = it)) },
          label = { Text(StrScanPages.packBarcodes(p.name.trim().ifEmpty { StrScanPages.pack })) },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = p.price,
          onValueChange = { set(i, p.copy(price = ir.vil3ntec.tohid.numericInput(it))) },
          label = { Text(StrScanPages.packPrice(p.name.trim().ifEmpty { StrScanPages.pack })) },
          singleLine = true,
          modifier = Modifier.weight(0.6f),
        )
      }
    }
    if (packs.size < PackBarcodes.MAX_PACKS) {
      TextButton(onClick = { onChange(packs + PackBarcodes.Draft()) }) { Text(StrScanPages.addPack) }
    }
  }
}
