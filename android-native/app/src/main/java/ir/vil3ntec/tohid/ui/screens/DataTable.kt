package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.pressMenu
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  جدولِ حرفه‌ای برای صفحه‌های پهن (کامپیوتر و تبلتِ افقی).
 *
 *  • زدنِ سرستون ⇐ مرتب‌سازی؛ زدنِ دوباره ⇐ برعکس (پیکانِ ▲/▼ کنارِ نام)
 *  • کشیدنِ لبهٔ سرستون ⇐ پهنای ستون (ستونِ اول — نام — باقیِ جا را می‌گیرد)
 *  • خانهٔ انتخاب ⇐ انتخابِ چندتایی؛ خانهٔ سرستون ⇐ همه/هیچ
 *  • زدنِ ردیف ⇐ باز کردن؛ کلیکِ راست یا لمسِ طولانی ⇐ منوی کارها
 *
 *  ردیف‌ها آیتم‌های همان `LazyColumn`ِ صفحه‌اند (نه فهرستِ تو در تو)،
 *  پس با هزاران کالا هم فقط ردیف‌های پیدا ساخته می‌شوند.
 */
class TableColumn<T>(
  val title: String,
  val width: Dp,
  val sortKey: ((T) -> Comparable<*>)? = null,
  val numeric: Boolean = false,
  val tint: (@Composable (T) -> Color)? = null,
  val text: (T) -> String,
)

class TableState(columns: Int, widths: List<Dp>) {
  var sortBy by mutableStateOf(-1)
  var ascending by mutableStateOf(true)
  val widths = mutableStateListOf<Dp>().apply { addAll(widths.take(columns)) }
  var selected by mutableStateOf(setOf<String>())
}

@Composable
fun <T> rememberTableState(columns: List<TableColumn<T>>): TableState =
  remember(columns.size) { TableState(columns.size, columns.map { it.width }) }

/** ردیف‌ها با مرتب‌سازیِ انتخاب‌شده — صفحه همین را به `dataTable` می‌دهد */
@Suppress("UNCHECKED_CAST")
fun <T> sortRows(rows: List<T>, columns: List<TableColumn<T>>, state: TableState): List<T> {
  val col = columns.getOrNull(state.sortBy) ?: return rows
  val key = col.sortKey ?: return rows
  val cmp = Comparator<T> { a, b -> (key(a) as Comparable<Any>).compareTo(key(b) as Any) }
  return if (state.ascending) rows.sortedWith(cmp) else rows.sortedWith(cmp.reversed())
}

fun <T> LazyListScope.dataTable(
  rows: List<T>,
  columns: List<TableColumn<T>>,
  state: TableState,
  key: (T) -> String,
  onOpen: (T) -> Unit,
  onMenu: (T) -> Unit,
) {
  item(key = "table-header") { TableHeader(rows, columns, state, key) }
  items(rows, key = { "row-" + key(it) }) { row ->
    val id = key(row)
    val checked = id in state.selected
    Row(
      Modifier
        .fillMaxWidth()
        .background(if (checked) Shop.colors.primary.copy(alpha = 0.08f) else Shop.colors.surface)
        .pressMenu(onClick = { onOpen(row) }, onMenu = { onMenu(row) })
        .padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(
        checked = checked,
        onCheckedChange = { state.selected = if (it) state.selected + id else state.selected - id },
      )
      columns.forEachIndexed { i, c ->
        val cellModifier = if (i == 0) Modifier.weight(1f) else Modifier.width(state.widths[i])
        Text(
          c.text(row),
          style = MaterialTheme.typography.bodySmall,
          color = c.tint?.invoke(row) ?: Shop.colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = if (c.numeric) TextAlign.End else TextAlign.Start,
          modifier = cellModifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
      }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Shop.colors.border))
  }
}

@Composable
private fun <T> TableHeader(rows: List<T>, columns: List<TableColumn<T>>, state: TableState, key: (T) -> String) {
  val density = LocalDensity.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val allIds = rows.map(key).toSet()
  Row(
    Modifier.fillMaxWidth().background(Shop.colors.surface2),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = allIds.isNotEmpty() && state.selected.containsAll(allIds),
      onCheckedChange = { state.selected = if (it) state.selected + allIds else state.selected - allIds },
    )
    columns.forEachIndexed { i, c ->
      val cellModifier = if (i == 0) Modifier.weight(1f) else Modifier.width(state.widths[i])
      Row(cellModifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
          c.title + when {
            state.sortBy != i -> ""
            state.ascending -> " ▲"
            else -> " ▼"
          },
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = Shop.colors.muted,
          maxLines = 1,
          textAlign = if (c.numeric) TextAlign.End else TextAlign.Start,
          modifier = Modifier
            .weight(1f)
            .then(if (c.sortKey != null) Modifier.clickable {
              if (state.sortBy == i) state.ascending = !state.ascending else { state.sortBy = i; state.ascending = true }
            } else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        //  لبهٔ کشیدنی — ستونِ نام کشیدنی نیست چون باقیِ جا را می‌گیرد
        if (i > 0) {
          Box(
            Modifier
              .width(6.dp)
              .height(28.dp)
              .background(Shop.colors.border)
              .draggable(
                orientation = Orientation.Horizontal,
                reverseDirection = rtl,
                state = rememberDraggableState { delta ->
                  val dp = with(density) { delta.toDp() }
                  state.widths[i] = (state.widths[i] + dp).coerceIn(56.dp, 420.dp)
                },
              ),
          )
        }
      }
    }
  }
}
