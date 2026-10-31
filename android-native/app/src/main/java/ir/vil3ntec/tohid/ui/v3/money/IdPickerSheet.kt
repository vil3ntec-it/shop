package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Sheet

/** یک گزینهٔ برگهٔ انتخاب — با شناسه؛ `id = null` یعنی «همه» */
internal data class PickOption(val id: String?, val title: String, val subtitle: String? = null)

/**
 *  برگهٔ انتخاب با **شناسه** (قرض‌دار، تأمین‌کننده، کالا): دو نامِ یکسان هرگز جای هم انتخاب
 *  نمی‌شوند (`V3PickerSheet` ِ کیت با متن انتخاب می‌کند). جستجو وقتی گزینه‌ها زیادند؛ نشانِ
 *  انتخاب کنارِ گزینهٔ فعلی. انتخاب ⇐ اول `onPick` و بعد برگه با حرکت بسته
 *  (`onDismiss`) — برگه‌ای که خودش مسیر است با همین ترتیب نتیجه را پیش از «برگشت» می‌دهد.
 */
@Composable
internal fun IdPickerSheet(
  title: String,
  options: List<PickOption>,
  selected: String?,
  onPick: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val term = query.trim()
  val shown = if (term.isEmpty()) options else options.filter { it.title.contains(term, ignoreCase = true) || it.subtitle?.contains(term, ignoreCase = true) == true }
  V3Sheet(onDismiss = onDismiss, title = title) { closer ->
    if (options.size > 6) {
      V3SearchField(query, { query = it }, StrKit.searchHint)
      Spacer(Modifier.height(10.dp))
    }
    if (shown.isEmpty()) {
      V3EmptyState(Icons.Outlined.SearchOff, StrKit.nothingFound)
    } else {
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(PhoneV3.tileRadius)).background(PhoneV3.colors.tile)) {
        itemsIndexed(shown, key = { _, o -> o.id ?: "" }) { i, o ->
          if (i > 0) V3Divider(start = 16.dp)
          V3ListRow(
            title = o.title,
            subtitle = o.subtitle,
            tile = false,
            chevron = false,
            selected = o.id == selected,
            onClick = { onPick(o.id); closer.close() },
          )
        }
      }
    }
  }
}
