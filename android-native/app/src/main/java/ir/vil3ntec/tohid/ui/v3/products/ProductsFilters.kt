package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Filter as F

/** نامِ هر مرتب‌سازی */
internal fun sortLabel(s: ProductsListModel.Sort): String = when (s) {
  ProductsListModel.Sort.NAME -> StrProducts.sortName
  ProductsListModel.Sort.LOW_FIRST -> StrProducts.sortLowFirst
  ProductsListModel.Sort.MOST_STOCK -> StrProducts.sortMostStock
  ProductsListModel.Sort.PRICIEST -> StrProducts.sortPriciest
  ProductsListModel.Sort.NEWEST -> StrProducts.sortNewest
}

private fun filterLabel(f: F): String = when (f) {
  F.ALL -> Str.all
  F.LOW -> StrProducts.filterLow
  F.OUT -> StrProducts.filterOut
  F.INACTIVE -> StrProducts.filterInactive
}

/**
 *  ردیفِ فیلتر: «همه» + دسته‌ها، خطِ جداکننده، و سه وضعیت («کم»، «تمام‌شده»،
 *  «غیرفعال» با شمارشان)؛ و در پایان دکمهٔ گردِ «مرتب‌سازی». زدنِ تراشهٔ
 *  انتخاب‌شده آن را برمی‌دارد.
 */
@Composable
internal fun ProductsFilterRow(
  query: ProductsListModel.Query,
  categories: List<String>,
  counts: Map<F, Int>,
  onQuery: (ProductsListModel.Query) -> Unit,
  onSort: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    V3ChipRow(Modifier.weight(1f), contentPadding = PaddingValues(start = 0.dp, end = 8.dp)) {
      V3Chip(Str.all, selected = query.category == null && query.filter == F.ALL, onClick = { onQuery(query.copy(category = null, filter = F.ALL)) })
      categories.forEach { cat ->
        V3Chip(cat, selected = query.category == cat, onClick = { onQuery(query.copy(category = if (query.category == cat) null else cat)) })
      }
      Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(24.dp).background(c.hairline))
      listOf(F.LOW, F.OUT, F.INACTIVE).forEach { f ->
        V3Chip(
          filterLabel(f), selected = query.filter == f, count = counts[f]?.takeIf { it > 0 },
          onClick = { onQuery(query.copy(filter = if (query.filter == f) F.ALL else f)) },
        )
      }
    }
    Spacer(Modifier.width(4.dp))
    V3IconButton(Icons.Outlined.SwapVert, StrProducts.sort, size = 40.dp, iconSize = 20.dp, onClick = onSort)
  }
}

/** برگهٔ «مرتب‌سازی»: فهرستِ تک‌انتخابی با تیکِ برند کنارِ گزینهٔ فعلی */
@Composable
internal fun SortSheet(current: ProductsListModel.Sort, onPick: (ProductsListModel.Sort) -> Unit, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrProducts.sort) { closer ->
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      ProductsListModel.Sort.entries.forEachIndexed { i, s ->
        if (i > 0) V3Divider(start = 16.dp)
        V3ListRow(
          title = sortLabel(s),
          tile = false,
          chevron = false,
          minHeight = 50.dp,
          selected = null,
          trailing = if (s == current) ({ Icon(Icons.Outlined.Check, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) }) else null,
          onClick = { closer.close { onPick(s) } },
        )
      }
    }
  }
}
