package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrKit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import kotlinx.coroutines.launch

/** بستنِ برگه با همان حرکتِ پایین‌رفتن، سپس کارِ بعدی */
@Stable
class V3SheetCloser internal constructor(private val close: (after: () -> Unit) -> Unit) {
  fun close(after: () -> Unit = {}) = close.invoke(after)
}

/**
 *  برگهٔ پایینِ نسخهٔ ۳: کارتِ سفید (شب: سطحِ یک) با گوشهٔ بالای ۲۸، دستگیرهٔ
 *  کوچک، عنوانِ وسط و محتوا. با کشیدن، زدنِ بیرون یا «برگشت» بسته می‌شود —
 *  مگر `dismissible = false` (مثلاً نمایشِ یک‌بارهٔ کد): آن‌وقت فقط `closer` می‌بندد.
 *  محتوا `V3SheetCloser` می‌گیرد تا پس از انتخاب، برگه **با حرکت** برود و
 *  بعد کار انجام شود (نه پرشِ ناگهانی).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3Sheet(
  onDismiss: () -> Unit,
  title: String? = null,
  dismissible: Boolean = true,
  content: @Composable ColumnScope.(V3SheetCloser) -> Unit,
) {
  val c = PhoneV3.colors
  //  `closer` همیشه می‌بندد؛ زدنِ بیرون، کشیدن و «برگشت» فقط وقتی `dismissible`
  val allowHide = remember { booleanArrayOf(false) }
  val canHide by rememberUpdatedState(dismissible)
  val state = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,
    confirmValueChange = { it != SheetValue.Hidden || canHide || allowHide[0] },
  )
  val scope = rememberCoroutineScope()
  //  صفحهٔ پشتِ برگه تا ۰٫۹۶ کوچک می‌شود (`v3BehindSheet` ِ پوسته)
  DisposableEffect(Unit) {
    V3Depth.sheets.intValue++
    onDispose { V3Depth.sheets.intValue-- }
  }
  val closer = remember(state) {
    V3SheetCloser { after ->
      allowHide[0] = true
      scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); after() }
    }
  }
  ModalBottomSheet(
    onDismissRequest = { if (canHide || allowHide[0]) onDismiss() },
    sheetState = state,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = c.card,
    contentColor = c.ink,
    scrimColor = c.black.copy(alpha = if (c.isDark) 0.6f else 0.32f),
    dragHandle = {
      Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(c.hairline))
    },
  ) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
      if (title != null) {
        Text(
          title,
          style = v3Text(17, FontWeight.SemiBold, 24),
          color = c.ink,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
      }
      content(closer)
    }
  }
}

/**
 *  تأییدِ کارِ پاک‌کننده — برگه، نه پنجرهٔ وسط: آیکن در دایرهٔ رنگِ خطا، عنوان،
 *  توضیحِ پیامدِ کار (از موتور، مثلِ `deleteWarning`)، دکمهٔ قرمزِ کار و دکمهٔ
 *  خنثیِ انصراف. جایی که کار برگشت‌پذیر است، به‌جای این برگه `V3Snackbar.undo`.
 */
@Composable
fun V3ConfirmSheet(
  title: String,
  message: String,
  confirm: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  icon: ImageVector? = null,
  danger: Boolean = true,
) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss) { closer ->
    if (icon != null) {
      val tone = PhoneV3Roles.tone(c, if (danger) V3Tone.ERROR else V3Tone.INFO)
      Box(
        Modifier.align(Alignment.CenterHorizontally).size(56.dp).clip(RoundedCornerShape(50)).background(tone.bg),
        contentAlignment = Alignment.Center,
      ) { Icon(icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(26.dp)) }
      Spacer(Modifier.height(12.dp))
    }
    Text(title, style = v3Text(18, FontWeight.Bold, 26), color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Text(message, style = v3Text(14, FontWeight.Medium, 21), color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(confirm, { closer.close(onConfirm) }, Modifier.weight(1f), kind = if (danger) V3ButtonKind.DANGER else V3ButtonKind.PRIMARY, haptic = true)
    }
  }
}

/**
 *  برگهٔ انتخاب از فهرست (دسته، واحد، تأمین‌کننده): جست‌وجو بالای فهرست وقتی
 *  گزینه‌ها زیادند، تیکِ برند کنارِ انتخابِ فعلی، و ردیفِ «+ تازه» که همان‌جا
 *  نامِ تازه را می‌گیرد. انتخاب ⇐ برگه با حرکت بسته و بعد `onPick`.
 */
@Composable
fun V3PickerSheet(
  title: String,
  options: List<String>,
  selected: String?,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit,
  allowNew: Boolean = true,
  newLabel: String = StrKit.addNew,
) {
  val c = PhoneV3.colors
  var query by remember { mutableStateOf("") }
  var adding by remember { mutableStateOf(false) }
  V3Sheet(onDismiss = onDismiss, title = title) { closer ->
    if (options.size > 6 || adding) {
      V3TextField(
        value = query,
        onValueChange = { query = it },
        label = if (adding) newLabel else StrKit.search,
        placeholder = if (adding) null else StrKit.searchHint,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(10.dp))
    }
    val shown = if (adding) emptyList() else options.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }
    if (shown.isNotEmpty()) {
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(16.dp)).background(if (c.isDark) c.tile else c.bg)) {
        itemsIndexed(shown, key = { _, o -> o }) { i, o ->
          if (i > 0) V3Divider(start = 16.dp)
          V3ListRow(
            title = o,
            minHeight = 50.dp,
            chevron = false,
            trailing = if (o == selected) ({ Icon(Icons.Outlined.Check, contentDescription = StrKit.selected, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) }) else null,
            onClick = { closer.close { onPick(o) } },
          )
        }
      }
    } else if (!adding && query.isNotBlank()) {
      Text(StrKit.nothingFound, style = v3Text(14, FontWeight.Medium, 20), color = c.inkMuted, modifier = Modifier.padding(vertical = 12.dp))
    }
    if (allowNew) {
      Spacer(Modifier.height(12.dp))
      if (adding) {
        V3Button(
          StrKit.add,
          { val v = query.trim(); if (v.isNotEmpty()) closer.close { onPick(v) } },
          Modifier.fillMaxWidth(),
          enabled = query.isNotBlank(),
        )
      } else {
        V3Button(newLabel, { adding = true; query = "" }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Add)
      }
    }
  }
}
