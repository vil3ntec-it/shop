package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettingsHelp
import ir.vil3ntec.tohid.ui.screens.SettingsHelp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.LocalV3RowHue
import ir.vil3ntec.tohid.ui.v3.LocalV3RowLook
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3NavRowSpec
import ir.vil3ntec.tohid.ui.v3.V3NavRows
import ir.vil3ntec.tohid.ui.v3.V3RowLook
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/**
 *  قابِ هر صفحهٔ تنظیمات: `V3Scaffold` با **عنوانِ کوچکِ وسطِ سربرگ** (۶۵r، مثلِ «قرض‌داران» — عنوانِ درشت در بدنه
 *  نیست) و برگشتِ گرد، و یک فهرستِ پیمایشی با کناره‌های «بیشتر» (`V3NavRowSpec.GUTTER`). روی کامپیوتر همین قاب در
 *  ستونِ پایان کشیده می‌شود، بی دکمهٔ برگشت (`onBack = null`). هر ردیفِ فهرست بارِ اول با ورودِ پلکانیِ کیت می‌آید
 *  (`v3Stagger`: محو + بالا آمدن، ۳۰ms، ده تای اول). رنگِ کاشیِ ردیف‌های صفحهٔ یک بخش از همان بخش است
 *  (`LocalSettingsHue` ⇐ `SettingsHues.of`).
 */
@Composable
internal fun SettingsPageFrame(
  title: String,
  onBack: (() -> Unit)?,
  subtitle: String? = null,
  largeTitle: Boolean = false,
  actions: List<V3Action> = emptyList(),
  state: LazyListState = rememberLazyListState(),
  bottomBar: (@Composable () -> Unit)? = null,
  /** جست‌وجوی صفحه — گوشی: دکمهٔ جست‌وجوی نوار (`V3ScopedSearch`)؛ کامپیوتر: زیرِ عنوان */
  search: ir.vil3ntec.tohid.ui.v3.V3Search? = null,
  content: LazyListScope.() -> Unit,
) {
  val stagger = rememberV3Stagger(title)
  //  صفحهٔ یک بخش (`LocalSettingsKey`) ⇒ کارتِ «این چه می‌کند؟» با یک مثال بالای صفحه؛ قابِ تودرتو دوباره نمی‌کشد
  val whatKey = LocalSettingsKey.current
  //  ۶۵r: هر ردیفِ صفحه همان ردیفِ «بیشتر» (`V3RowLook.NAV`)، کاشی به رنگِ همین بخش
  val pageHue = whatKey?.let { SettingsHues.of(it) } ?: LocalV3RowHue.current
  CompositionLocalProvider(
    LocalSettingsKey provides null,
    LocalV3RowLook provides V3RowLook.NAV,
    LocalV3RowHue provides pageHue,
  ) {
    //  ۶۵s: عنوان **فقط** در سربرگ است (یک منبع، هرگز دوبار): سربرگِ `V3Scaffold` جای عنوان را از شمارِ واقعیِ دکمه‌ها می‌سنجد و
    //  کارِ اضافه را به «…» می‌برد تا عنوان کامل وسط جا شود (`V3HeaderActions.visibleFor`، `V3HeaderBar`)
    V3Scaffold(title = title, subtitle = subtitle.takeIf { largeTitle }, onBack = onBack, actions = actions, largeTitle = largeTitle, bottomBar = bottomBar, search = search) { pad ->
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
          Modifier.widthIn(max = 720.dp).fillMaxSize(),
          state = state,
          contentPadding = PaddingValues(start = V3NavRowSpec.GUTTER, end = V3NavRowSpec.GUTTER, top = 4.dp, bottom = pad.calculateBottomPadding()),
        ) {
          val list = StaggeredList(this, stagger)
          //  عنوان در سربرگ است (نه درشت در بدنه)؛ زیرعنوانِ صفحه («روشن»، «آخرین پشتیبان …») یک خطِ کم‌رنگِ وسط زیرِ آن
          if (!largeTitle && subtitle != null) list.item(key = "settings-subtitle") { SettingsSubtitle(subtitle) }
          if (whatKey != null && SettingsHelp.example(whatKey) != null) list.item(key = "settings-what") { SettingsWhatCard(whatKey) }
          list.content()
        }
      }
    }
  }
}

/**
 *  کلیدِ بخشی که صفحه‌اش همین حالا کشیده می‌شود (`SettingsLayout`، یا `SettingsHelp.LATE_APOLOGY`) — مسیرِ بخش
 *  (`SettingsSection`) و ستونِ پایانِ کامپیوتر می‌دهند؛ `SettingsPageFrame` کارتِ «این چه می‌کند؟» را از آن می‌کشد.
 */
internal val LocalSettingsKey = compositionLocalOf<String?> { null }

/**
 *  «این چه می‌کند؟» (تنظیماتِ کم، ۱۴۰۵/۰۸/۱۱): کاشیِ رنگیِ همان ردیف (۶۵r — همان کاشیِ «بیشتر»، رنگِ بخش) و یک
 *  مثالِ ملموس به زبانِ دکان‌دار. بخشِ بی مثال ⇒ هیچ. صفحه‌هایی که قابِ خودشان را دارند (نوارِ پایین، رمزِ حساب)
 *  همین را دستی بالای ستون می‌گذارند.
 */
@Composable
internal fun SettingsWhatCard(key: String, modifier: Modifier = Modifier) {
  val example = SettingsHelp.example(key) ?: return
  val c = PhoneV3.colors
  V3Card(modifier.padding(top = 8.dp), padding = PaddingValues(14.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(V3NavRowSpec.GAP), verticalAlignment = Alignment.Top) {
      V3IconTile(SettingsPages.icon(key), SettingsHues.of(key), size = V3NavRowSpec.TILE)
      Column(Modifier.weight(1f)) {
        Text(StrSettingsHelp.whatTitle, style = V3Type.rowTitle, color = c.ink)
        Text(example, style = V3Type.rowHelp, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp))
      }
    }
  }
}

/**
 *  صفحه‌ای از تنظیمات که قابِ خودش را دارد (`V3Scaffold` ِ «نوارِ پایین»، «رمزِ حساب»): ردیف‌هایش همان ردیفِ «بیشتر»
 *  با رنگِ [key] — همان چیزی که `SettingsPageFrame` خودش می‌دهد.
 */
@Composable
internal fun SettingsRowLook(key: String, content: @Composable () -> Unit) = V3NavRows(SettingsHues.of(key), content)

/** زیرِ سربرگ، وسط: زیرعنوانِ کم‌رنگ («روشن»، «آخرین پشتیبان…»)؛ نامِ صفحه فقط در سربرگ است (۶۵s، بی تکرار) */
@Composable
private fun SettingsSubtitle(subtitle: String) {
  Text(
    subtitle, style = V3Type.rowHelp, color = PhoneV3.colors.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2,
    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
  )
}

/** بندِ توضیح زیر یا میانِ کارت‌ها — خاکستری و کوتاه، همان اندازهٔ خطِ راهنمای ردیف (۱۳، ۶۵r) */
@Composable
internal fun SettingsNote(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = V3Type.rowHelp,
    color = PhoneV3.colors.inkMuted,
    modifier = modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp),
  )
}

/** کارتِ مرزدار با فاصلهٔ ۱۶ برای محتوای آزاد (توضیح، کادر، دکمه) */
@Composable
internal fun SettingsBlock(modifier: Modifier = Modifier, top: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
  V3Card(modifier.padding(top = if (top) 16.dp else 0.dp), padding = PaddingValues(16.dp), content = content)
}

/** همان فهرست، با ورودِ پلکانیِ کیت برای هر `item` به ترتیبِ آمدن */
private class StaggeredList(private val base: LazyListScope, private val stagger: V3StaggerState) : LazyListScope by base {
  private var next = 0

  override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
    val index = next++
    val id = key ?: index
    //  بیرونِ لامبدا: نشانگرِ DSL ِ فهرست دسترسی به گیرندهٔ بیرونی را درونِ ردیف نمی‌پذیرد
    val state = stagger
    base.item(key, contentType) {
      val scope = this
      //  ردیفِ کلیددار آمدن و رفتنش هم حرکت دارد (`v3AnimateItem`)
      //  ستون، نه Box: ردیفی که چند بخش دارد (کارت + توضیح) روی هم نمی‌افتد
      Column((if (key != null) Modifier.v3AnimateItem(scope) else Modifier).v3Stagger(state, id, index)) { scope.content() }
    }
  }
}
