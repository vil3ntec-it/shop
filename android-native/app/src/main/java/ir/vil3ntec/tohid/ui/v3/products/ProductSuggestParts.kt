package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProductSuggest
import ir.vil3ntec.tohid.core.product.ProductSuggest
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  AI-06 — «پیشنهادِ دسته و نام» در فرمِ کالای نسخهٔ ۳ (`NewProductsScreen`، `EditProductScreen`). منطقِ خالص در
 *  `core/product/ProductSuggest` (commonMain، آیفون هم)؛ این‌جا فقط رابط.
 *  ⛔ هیچ پیشنهادی بی زدنِ کاربر کادری را پر نمی‌کند؛ قیمت، بارکد، وزن و برند هرگز نوشته نمی‌شوند.
 *  ⛔ کلیدِ همین دستگاه (`ProductSuggestPref`، ردهٔ A، پیش‌فرض روشن) خاموش ⇒ فرم همان پیشین؛ خواندنش در
 *     `ProductSuggestAndroid.kt` (مرزِ پلتفرم).
 */

/** تراشه‌های «دستهٔ پیشنهادی» زیرِ نام، با دلیلِ نخستین؛ زدن ⇐ همان دسته (تأییدِ کاربر) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategorySuggestions(picks: List<ProductSuggest.Pick>, onPick: (String) -> Unit) {
  val c = PhoneV3.colors
  val top = picks.firstOrNull() ?: return
  Column(Modifier.padding(top = 10.dp)) {
    Text(
      StrProductSuggest.suggested,
      style = V3Type.subhead.copy(fontWeight = FontWeight.SemiBold),
      color = c.inkMuted,
      modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      picks.forEach { p -> V3Chip(p.category, false, { onPick(p.category) }, icon = Icons.Outlined.Category) }
    }
    Text(
      if (top.byName) StrProductSuggest.whyName(top.word, top.category) else StrProductSuggest.why(top.word, top.count, top.category),
      style = V3Type.footnote,
      color = c.inkMuted,
      modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
  }
}

/**
 *  برگهٔ «افزودنِ کالا از متن»: متن ⇐ پیش‌نمایشِ نام‌ها (با «قیمت/بارکد برداشته شد» و «از پیش هست») ⇐ «افزودنِ N ردیف».
 *  [existing] نامِ کالاهای دکان؛ [onAdd] فقط نام‌های تازه.
 */
@Composable
internal fun PasteProductsSheet(existing: List<String>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
  val c = PhoneV3.colors
  var text by remember { mutableStateOf("") }
  val have = remember(existing) { ProductSuggest.keysOf(existing) }
  val paste = remember(text, have) { ProductSuggest.paste(text, have) }
  val fresh = paste.fresh.map { it.name }
  V3Sheet(onDismiss = onDismiss, title = StrProductSuggest.pasteTitle) { closer ->
    V3TextField(
      text, { text = it }, StrProductSuggest.pasteLabel, Modifier.fillMaxWidth(),
      placeholder = StrProductSuggest.pastePlaceholder, helper = StrProductSuggest.pasteHelp, singleLine = false,
    )
    if (text.isNotBlank()) {
      Spacer(Modifier.height(12.dp))
      if (paste.lines.isEmpty()) Text(StrProductSuggest.none, style = V3Type.subhead, color = c.inkMuted)
      paste.lines.forEach { line -> PasteLine(line) }
      val notes = listOfNotNull(
        paste.skipped.takeIf { it > 0 }?.let(StrProductSuggest::skipped),
        ProductSuggest.MAX_LINES.takeIf { paste.truncated }?.let(StrProductSuggest::truncated),
      )
      notes.forEach { Text(it, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 6.dp)) }
    }
    Spacer(Modifier.height(14.dp))
    V3Button(
      StrProductSuggest.addRows(fresh.size),
      { closer.close { onAdd(fresh) } },
      Modifier.fillMaxWidth(),
      enabled = fresh.isNotEmpty(),
      haptic = true,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasteLine(line: ProductSuggest.Line) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Text(line.name, style = V3Type.body, color = if (line.exists) c.inkMuted else c.ink, maxLines = 2)
    val pills = buildList {
      if (line.exists) add(V3Status.INACTIVE to StrProductSuggest.exists)
      if (ProductSuggest.Dropped.PRICE in line.dropped) add(V3Status.WARNING to StrProductSuggest.droppedPrice)
      if (ProductSuggest.Dropped.BARCODE in line.dropped) add(V3Status.WARNING to StrProductSuggest.droppedBarcode)
    }
    if (pills.isNotEmpty()) {
      FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pills.forEach { (s, label) -> V3StatusPill(s, label) }
      }
    }
  }
}
