package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.shell.SearchIndex
import ir.vil3ntec.tohid.ui.v3.shell.V3Palette

/**
 *  یک نتیجهٔ فهرستِ فرمان برای صدازننده‌های امروز (`AppRoot`، انتخابِ کالا در دستگاه‌ها):
 *  `kind` = `product` / `debtor` با شناسهٔ همان سند، یا `page` با **مسیرِ کامل** در `id`
 *  (بخش‌ها و فاکتور ⇐ `sales/{saleId}`) که مستقیم باز می‌شود.
 */
class PaletteHit(val kind: String, val id: String, val title: String, val hint: String, val icon: ImageVector)

/**
 *  جست‌وجوی سریع (Ctrl+K) — همان فهرستِ فرمانِ نسخهٔ ۳ (`V3Palette` روی `SearchIndex`)
 *  با قراردادِ پیشینِ `PaletteHit`، تا صدازننده‌های امروز دست نخورند.
 */
@Composable
fun CommandPalette(d: ShopData, onDismiss: () -> Unit, onPick: (PaletteHit) -> Unit) {
  V3Palette(d, SearchIndex.sections(), onDismiss) { h -> onPick(h.toPaletteHit()) }
}

internal fun SearchIndex.Hit.toPaletteHit(): PaletteHit = when (kind) {
  SearchIndex.Kind.PRODUCT -> PaletteHit("product", id, title, hint.orEmpty(), Icons.Outlined.Inventory2)
  SearchIndex.Kind.DEBTOR -> PaletteHit("debtor", id, title, hint.orEmpty(), Icons.Outlined.Person)
  else -> PaletteHit("page", route, title, hint.orEmpty(), Icons.Outlined.SpaceDashboard)
}
