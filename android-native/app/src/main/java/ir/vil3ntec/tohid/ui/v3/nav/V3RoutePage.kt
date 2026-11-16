package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  کالبدِ یکسانِ صفحه‌ای که پیش‌تر `Chrome.SHELL` بود (کارِ `claude/e-phone-p0`، صاحب‌کار: «یک بخش یک مدل، یک بخش مدلِ دیگه»):
 *  خودِ صفحه `V3Scaffold` دارد (مسیرش `Chrome.OWN`) — عنوان وسطِ نوار، برگشت فقط وقتی چیزی زیرش است، تراشه‌ها در جای قاب
 *  ([chips])، کارِ اصلی یک `V3Fab` از [fab] (همه‌جا همان گوشه)، کارهای دیگر [actions] و «…» ([overflow]). بدنه ستونِ پیمایش‌پذیر با
 *  فاصلهٔ ۱۶ و جای دکمهٔ شناور؛ یادداشتِ بخش [V3PageFootnote] پایانِ صفحه است، نه سرِ آن.
 */
@Composable
fun RouteScope.V3RoutePage(
  title: String,
  actions: List<V3Action> = emptyList(),
  overflow: List<V3MenuItem> = emptyList(),
  chips: (@Composable () -> Unit)? = null,
  fab: (@Composable BoxScope.(expanded: Boolean) -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  V3Scaffold(title = title, onBack = backOrNull, actions = actions, overflow = overflow, chips = chips, fab = fab) { pad ->
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + if (fab != null) 72.dp else 0.dp),
    ) { content() }
  }
}

/** یادداشتِ آرامِ پایانِ صفحه (پیش‌تر سرِ صفحه) — هیچ نوشته‌ای گم نمی‌شود */
@Composable
fun V3PageFootnote(text: String, modifier: Modifier = Modifier) {
  Text(
    text, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, textAlign = TextAlign.Center,
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
  )
}
