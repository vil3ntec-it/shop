package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsSearchIndex
import ir.vil3ntec.tohid.ui.v3.V3TwoPane
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.devices.DevicesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.SettingsShotState

/**
 *  تنظیماتِ صفحهٔ پهن (کامپیوتر، تبلتِ افقی — `shell.wide`)، نسخهٔ دوم (۱۴۰۵/۰۸/۱۱): ستونِ آغاز همان صفحهٔ اولِ
 *  گوشی است — کادرِ جست‌وجو، شش دسته و پیوندِ «قابلیت‌ها» — و ستونِ پایان صفحهٔ دستهٔ انتخاب‌شده (همان بخش‌های کوتاه)؛
 *  زدنِ ردیف همان صفحهٔ گوشی (`SettingsPages`) را در ستونِ پایان باز می‌کند، با برگشت به صفحهٔ دسته. «دستگاه‌ها» همان
 *  صفحهٔ همهٔ دستگاه‌هاست. پیوندِ هر بخش (`settings/shop/app-lock`) همان بخش را باز می‌کند — بخشِ پنهان (A) و آزمایشی هم.
 *
 *  @param group دستهٔ باز (`SettingsLayout`) یا `SettingsPaths.FEATURES`
 *  @param section بخشی که صفحه‌اش باز باشد
 */
@Composable
internal fun RouteScope.DeskSettingsScreen(group: String? = null, section: String? = null) {
  val facts = rememberSettingsFacts(theme)
  val docs = rememberSearchDocs(facts)
  var query by rememberSaveable { mutableStateOf(SettingsShotState.query) }
  val start = group ?: section?.let(SettingsLayout::groupOf) ?: SettingsLayout.GROUPS.first().title
  var cat by rememberSaveable(group, section) { mutableStateOf(start) }
  var pick by rememberSaveable(group, section) { mutableStateOf(section?.takeIf { SettingsPages.of(it) != null }) }
  val openRow: (String) -> Unit = { key ->
    val link = SettingsLayout.LINKS[key]
    if (link != null) nav.open(link) else pick = key
  }
  fun open(d: SettingsSearchIndex.Doc) {
    when (d.kind) {
      SettingsSearchIndex.Kind.CATEGORY -> { cat = d.key; pick = null }
      SettingsSearchIndex.Kind.FEATURES -> { cat = SettingsPaths.FEATURES; pick = null }
      SettingsSearchIndex.Kind.SECTION -> { SettingsLayout.groupOf(d.key)?.let { cat = it }; openRow(d.key) }
      SettingsSearchIndex.Kind.EXTRA -> nav.open(d.key)
    }
  }
  V3TwoPane(
    listWidth = 320.dp,
    list = {
      SettingsPageFrame(title = Str.settings, onBack = null, largeTitle = false) {
        item(key = "search") { SettingsSearchBox(query) { query = it } }
        if (query.isBlank()) {
          item(key = "home") { SettingsHomeCard(selected = cat) { cat = it; pick = null } }
          item(key = "features") { FeaturesLink { cat = SettingsPaths.FEATURES; pick = null } }
        } else {
          searchResults(SettingsSearchIndex.search(docs, query), query, ::open)
        }
      }
    },
    detail = {
      AnimatedContent(cat to pick, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) }, label = "deskSettings") { (c, key) ->
        when {
          //  «این چه می‌کند؟» بالای صفحهٔ همان بخش (`SettingsPageFrame`)؛ برگشت ⇐ صفحهٔ دسته
          key != null -> CompositionLocalProvider(LocalSettingsKey provides key) { SettingsPages.of(key)?.content?.invoke(this@DeskSettingsScreen, { pick = null }) }
          c == SettingsPaths.FEATURES -> FeaturesScreen(onBack = null, onOptions = { k -> SettingsLayout.groupOf(k)?.let { cat = it }; pick = k })
          c in SettingsLayout.GROUP_LINKS -> DevicesScreen(data, tab = null, onBack = null)
          else -> CategoryBody(c, facts, onBack = null, onRow = openRow, onExtra = { nav.open(it) })
        }
      }
    },
  )
}
