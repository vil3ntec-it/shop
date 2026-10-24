package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Type

/** کالاهای پیدا در جست‌وجوی انتخاب‌گر: نام یا بارکد، فقط کالاهای فعال */
internal fun pickable(d: ShopData, query: String): List<Product> {
  val term = query.trim()
  return d.products.filter { it.active }
    .filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) || it.barcodes.any { b -> b.contains(term) } }
}

/**
 *  انتخابِ کالا (برگهٔ ورود و ردیف‌های ورودِ چندقلم): جست‌وجو، ردیفِ «+ محصولِ تازه» در
 *  بالا، و فهرستِ کالاها با عکس و موجودیِ همین حالا؛ تیکِ برند کنارِ انتخابِ فعلی.
 */
@Composable
internal fun ProductPickerList(
  data: ShopData,
  selectedId: String?,
  onPick: (Product) -> Unit,
  onNew: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  var query by remember { mutableStateOf("") }
  val shown = remember(data, query) { pickable(data, query) }
  Column(modifier.fillMaxWidth()) {
    V3SearchField(query, { query = it }, StrStock.searchHint)
    Spacer(Modifier.height(10.dp))
    LazyColumn(
      Modifier
        .fillMaxWidth()
        .heightIn(max = 440.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(if (c.isDark) c.tile else c.bg),
    ) {
      if (onNew != null) item(key = "new") {
        V3ListRow(
          title = StrStock.newProduct,
          subtitle = StrStock.newProductHint,
          icon = Icons.Outlined.AddCircleOutline,
          hue = V3Hue.BRAND,
          onClick = onNew,
        )
      }
      itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
        if (i > 0 || onNew != null) V3Divider(start = 64.dp)
        val unit = listOf(qty(ShopStore.stock(data, p.id)), p.unit).filter { it.isNotBlank() }.joinToString(" ")
        V3ListRow(
          title = p.name,
          subtitle = StrStock.inStock(unit),
          leading = { V3ProductThumb(p.id, p.name, size = 40.dp) },
          chevron = false,
          trailing = if (p.id == selectedId) ({
            Icon(Icons.Outlined.Check, contentDescription = StrKit.selected, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp))
          }) else null,
          onClick = { onPick(p) },
        )
      }
      if (shown.isEmpty()) item(key = "none") {
        Text(StrKit.nothingFound, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.fillMaxWidth().padding(16.dp))
      }
    }
  }
}
