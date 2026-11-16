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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FormBar
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
  scroll: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) = V3PageFrame(title, backOrNull, actions, overflow, chips, fab, scroll, content)

/**
 *  همان قابِ [V3RoutePage] برای صفحه‌ای که `RouteScope` ندارد (کشیدنِ بی‌پلتفرمِ `*Content`، راهِ آیفون): برگشت را خودش می‌گیرد.
 *  [scroll] = false ⇒ بدنه خودش فهرستِ Lazy یا زبانه دارد (دفترِ موجودی)؛ قاب فقط جا و فاصله را می‌دهد. فرمِ درونِ قاب دکمهٔ
 *  ذخیره‌اش را با [V3PageSaveBar] می‌دهد و قاب آن را نوارِ چسبانِ پایین (`V3FormBar`) می‌کند — همان نوارِ جزئیاتِ کالا.
 */
@Composable
fun V3PageFrame(
  title: String,
  onBack: (() -> Unit)?,
  actions: List<V3Action> = emptyList(),
  overflow: List<V3MenuItem> = emptyList(),
  chips: (@Composable () -> Unit)? = null,
  fab: (@Composable BoxScope.(expanded: Boolean) -> Unit)? = null,
  scroll: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  val slot = remember { V3PageBarSlot() }
  val bar = slot.bar
  V3Scaffold(
    title = title, onBack = onBack, actions = actions, overflow = overflow, chips = chips, fab = fab,
    bottomBar = bar?.let { b -> { V3FormBar(b.label, b.onClick, primaryEnabled = b.enabled, loading = b.loading, summary = b.summary) } },
  ) { pad ->
    CompositionLocalProvider(LocalV3PageBar provides slot) {
      Column(
        Modifier
          .fillMaxSize()
          .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
          .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + if (fab != null) 72.dp else 0.dp),
      ) { content() }
    }
  }
}

/** دکمهٔ ذخیرهٔ فرمی که درونِ قاب است — نوشته، روشن/خاموش، در راه و خطِ جمعِ کوتاه */
@Immutable
data class V3PageBarSpec(val label: String, val enabled: Boolean, val loading: Boolean, val summary: String?, val onClick: () -> Unit)

/** جای نوارِ چسبانِ همان قاب ([V3PageFrame]) */
@Stable
class V3PageBarSlot {
  var bar by mutableStateOf<V3PageBarSpec?>(null)
}

/** قابِ دربرگیرنده؛ null ⇒ فرم بیرون از قاب است (پنجرهٔ کامپیوتر) و دکمه همان‌جا می‌نشیند */
val LocalV3PageBar = staticCompositionLocalOf<V3PageBarSlot?> { null }

/**
 *  دکمهٔ ذخیرهٔ فرم: درونِ [V3PageFrame] نوارِ چسبانِ پایینِ قاب (همیشه دیده، بالای صفحه‌کلید)، بیرون از قاب (پنجرهٔ کامپیوتر) همان
 *  دکمهٔ تمام‌پهنای پایانِ فرم. فرم آن را فقط وقتی می‌کشد که ذخیره معنا دارد؛ نکشیدنش نوار را برمی‌دارد.
 */
@Composable
fun V3PageSaveBar(label: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false, summary: String? = null, haptic: Boolean = true) {
  val slot = LocalV3PageBar.current
  if (slot == null) {
    if (summary != null) Text(summary, style = V3Type.callout, color = PhoneV3.colors.ink, modifier = Modifier.padding(top = 10.dp))
    V3Button(label, onClick, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = enabled, loading = loading, haptic = haptic)
    return
  }
  val latest = rememberUpdatedState(onClick)
  val click = remember { { latest.value() } }
  //  فقط وقتی چیزی عوض شد (نه هر بار) — وگرنه نوشتنِ حالت در هر ترکیب حلقه می‌سازد
  SideEffect {
    val cur = slot.bar
    if (cur == null || cur.label != label || cur.enabled != enabled || cur.loading != loading || cur.summary != summary || cur.onClick !== click) {
      slot.bar = V3PageBarSpec(label, enabled, loading, summary, click)
    }
  }
  DisposableEffect(slot) { onDispose { if (slot.bar?.onClick === click) slot.bar = null } }
}

/** یادداشتِ آرامِ پایانِ صفحه (پیش‌تر سرِ صفحه) — هیچ نوشته‌ای گم نمی‌شود */
@Composable
fun V3PageFootnote(text: String, modifier: Modifier = Modifier) {
  Text(
    text, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, textAlign = TextAlign.Center,
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
  )
}
