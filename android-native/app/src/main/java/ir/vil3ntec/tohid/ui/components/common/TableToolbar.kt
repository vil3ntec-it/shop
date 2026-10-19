package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  نوارِ ابزارِ جدول — VISUAL-DIRECTION §۳ بند ۳.
 *
 *  جست‌وجو (نام، کد، بارکد)، فیلتر (فقط وقتی صفحه فیلترِ واقعی دارد — دکمهٔ
 *  بی‌اثر نمی‌آید)، کلیدِ نمای کارتی/جدولی (`ViewModeToggle`؛ روی گوشی
 *  پنهان) و جای زبانه‌های وضعیت در انتهای نوار.
 */
@Composable
fun TableToolbar(
  query: String,
  onQuery: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  viewMode: ViewModeState? = null,
  searchFocus: FocusRequester? = null,
  filter: (@Composable () -> Unit)? = null,
  tabs: (@Composable RowScope.() -> Unit)? = null,
) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    SearchBox(query, onQuery, placeholder, Modifier.weight(1f, fill = false).widthIn(min = 220.dp, max = 380.dp), searchFocus)
    if (filter != null) {
      Spacer(Modifier.width(8.dp))
      filter()
    }
    if (viewMode != null && viewMode.toggleVisible) {
      Spacer(Modifier.width(8.dp))
      ViewModeToggle(viewMode)
    }
    Spacer(Modifier.weight(1f))
    if (tabs != null) {
      Spacer(Modifier.width(8.dp))
      tabs()
    }
  }
}

/** کادرِ جست‌وجوی جمع‌وجور با ذره‌بین و ×ِ پاک‌کردن */
@Composable
fun SearchBox(
  query: String,
  onQuery: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  focus: FocusRequester? = null,
) {
  val c = Shop.colors
  val h = if (Platform.DESKTOP) 38.dp else 48.dp
  Row(
    modifier
      .height(h)
      .clip(Shape.field)
      .background(c.surfaceSolid)
      .border(1.dp, c.fieldBorder, Shape.field)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (query.isEmpty()) {
        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
        cursorBrush = SolidColor(c.primary),
        modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
      )
    }
    if (query.isNotEmpty()) {
      Icon(
        Icons.Filled.Close, "پاک‌کردنِ جست‌وجو", tint = c.muted,
        modifier = Modifier.size(28.dp).clip(Shape.button).clickable(role = Role.Button) { onQuery("") }.padding(5.dp),
      )
    }
  }
}

/**
 *  فیلترِ تک‌انتخابی (مثلاً دسته) — دکمهٔ «فیلتر» با منوی گزینه‌ها.
 *  `null` یعنی «همه». برچسبِ دکمه گزینهٔ فعلی را می‌گوید.
 */
@Composable
fun <T> FilterMenu(
  label: String,
  options: List<Pair<T, String>>,
  selected: T?,
  onSelect: (T?) -> Unit,
  allLabel: String,
  modifier: Modifier = Modifier,
) {
  val c = Shop.colors
  var open by remember { mutableStateOf(false) }
  val h = if (Platform.DESKTOP) 38.dp else 48.dp
  Box(modifier) {
    Row(
      Modifier
        .height(h)
        .clip(Shape.button)
        .background(if (selected != null) c.primaryTint else c.surfaceSolid)
        .border(1.dp, c.border, Shape.button)
        .clickable(role = Role.DropdownList) { open = true }
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Filled.FilterList, null, tint = c.muted, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      val current = options.firstOrNull { it.first == selected }?.second
      Text(
        if (current != null) "$label: $current" else label,
        style = MaterialTheme.typography.labelLarge, color = c.text,
        fontWeight = if (current != null) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
      )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(text = { Text(allLabel, fontWeight = if (selected == null) FontWeight.Bold else FontWeight.Normal) }, onClick = { open = false; onSelect(null) })
      for ((id, text) in options) {
        DropdownMenuItem(text = { Text(text, fontWeight = if (id == selected) FontWeight.Bold else FontWeight.Normal) }, onClick = { open = false; onSelect(id) })
      }
    }
  }
}
