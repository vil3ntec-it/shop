package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3ActionBar
import ir.vil3ntec.tohid.ui.v3.V3BarAction
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Toggle
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem

class ProductsActions(
  val onOpen: (StorefrontProductDto) -> Unit,
  val onPublish: (StorefrontProductDto, Boolean) -> Unit,
  val onBulk: (Set<String>, Boolean) -> Unit,
  val onRetry: () -> Unit,
)

/**
 *  برگهٔ «کالاها»: جست‌وجو و فیلترِ «همه / در ویترین / بیرون» با شمار، هر کالا یک ردیف با عکس
 *  (یا کاشیِ حرف)، قیمت (آنلاین/دفتر/بی‌قیمت) و کلیدِ «در ویترین»؛ زدن ⇐ برگهٔ قیمت و سقف و توضیح.
 *  نگه‌داشتن ⇐ انتخابِ گروهی با نوارِ «انتشار / برداشتن» و گزارشِ همان کار.
 */
@Composable
fun ProductsPane(
  products: List<StorefrontProductDto>?,
  error: String?,
  bulkBusy: Boolean,
  bulkReport: String?,
  bulkOk: Boolean,
  actions: ProductsActions,
  pad: PaddingValues,
) {
  var query by rememberSaveable { mutableStateOf("") }
  var filter by rememberSaveable { mutableStateOf(StorefrontModel.Filter.ALL) }
  var selected by remember { mutableStateOf(emptySet<String>()) }
  val list = products
  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 72.dp)) {
      item(key = "search") { V3SearchField(query, { query = it }, StrOnline.sfSearch, Modifier.padding(top = 4.dp, bottom = 8.dp)) }
      item(key = "chips") {
        V3ChipRow(contentPadding = PaddingValues(0.dp)) {
          StorefrontModel.Filter.entries.forEach { f ->
            V3Chip(filterLabel(f), filter == f, { filter = f }, count = list?.let { StorefrontModel.count(it, f) })
          }
        }
      }
      if (bulkBusy || bulkReport != null) item(key = "report") {
        if (bulkBusy) V3Message(V3Tone.INFO, StrKit.working, Modifier.padding(top = 12.dp))
        else V3Message(if (bulkOk) V3Tone.SUCCESS else V3Tone.WARNING, bulkReport.orEmpty(), Modifier.padding(top = 12.dp))
      }
      when {
        list == null && error != null -> item(key = "error") {
          V3EmptyState(Icons.Outlined.CloudOff, StrOnline.listFailed, body = error, action = StrKit.retry, onAction = actions.onRetry)
        }
        list == null -> item(key = "skeleton") { V3Skeleton(rows = 6, modifier = Modifier.padding(top = 12.dp)) }
        list.isEmpty() -> item(key = "empty") { V3EmptyState(Icons.Outlined.Inventory2, StrOnline.sfNoProducts, body = StrOnline.sfNoProductsBody) }
        else -> {
          val shown = StorefrontModel.filter(list, query, filter)
          if (shown.isEmpty()) item(key = "nomatch") { V3EmptyState(Icons.Outlined.SearchOff, StrOnline.sfNoMatch) }
          else itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
            V3Card(Modifier.padding(top = if (i == 0) 12.dp else 8.dp).v3AnimateItem(this), padding = PaddingValues(0.dp)) {
              ProductRow(
                p,
                selected = if (selected.isEmpty()) null else p.id in selected,
                onToggle = { on -> actions.onPublish(p, on) },
                onLongClick = { selected = selected + p.id },
                onClick = {
                  if (selected.isEmpty()) actions.onOpen(p)
                  else selected = if (p.id in selected) selected - p.id else selected + p.id
                },
              )
            }
          }
        }
      }
    }
    V3ActionBar(
      count = selected.size,
      actions = listOf(
        V3BarAction(Icons.Outlined.Publish, StrOnline.publish) { actions.onBulk(selected, true); selected = emptySet() },
        V3BarAction(Icons.Outlined.VisibilityOff, StrOnline.unpublish) { actions.onBulk(selected, false); selected = emptySet() },
      ),
      onClear = { selected = emptySet() },
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = pad.calculateBottomPadding()),
    )
  }
}

@Composable
private fun ProductRow(p: StorefrontProductDto, selected: Boolean?, onToggle: (Boolean) -> Unit, onLongClick: () -> Unit, onClick: () -> Unit) {
  V3ListRow(
    title = p.name,
    subtitle = listOf(StorefrontModel.priceText(p), StorefrontModel.stateLabel(p), p.category).filter { it.isNotBlank() }.joinToString(" · "),
    leading = { V3ProductThumb(p.id, p.name, size = 44.dp) },
    trailing = if (selected == null) ({ V3Toggle(p.published, StrOnline.inStoreToggle(p.name), onToggle) }) else null,
    selected = selected,
    onLongClick = onLongClick,
    onClick = onClick,
  )
}

private fun filterLabel(f: StorefrontModel.Filter): String = when (f) {
  StorefrontModel.Filter.ALL -> StrOnline.all
  StorefrontModel.Filter.ON -> StrOnline.sfInStore
  StorefrontModel.Filter.OFF -> StrOnline.sfOutStore
}
