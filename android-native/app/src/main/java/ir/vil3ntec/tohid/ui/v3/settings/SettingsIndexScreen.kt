package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSettingsHome
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsSearchIndex
import ir.vil3ntec.tohid.ui.screens.settingsHue
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.sections.RemovePinSheet
import ir.vil3ntec.tohid.ui.v3.settings.sections.SettingsShotState

/**
 *  صفحهٔ اولِ تنظیمات (`settings`، نسخهٔ دوم — تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۱۱، مثلِ Shopify POS / Square / تنظیماتِ iOS):
 *  یک کادرِ جست‌وجو، **شش دسته** (هر کدام یک ردیف ⇐ صفحهٔ خودش) و زیرشان یک پیوندِ کوچکِ «قابلیت‌ها». فهرستِ بلند،
 *  «پیشرفته» و سرخطِ تکراری نیست. با نوشتن در کادر، جای دسته‌ها نتیجه‌های جست‌وجو می‌آید (`SettingsSearchIndex`).
 */
@Composable
internal fun RouteScope.SettingsIndexScreen(onBack: (() -> Unit)?) {
  val facts = rememberSettingsFacts(theme)
  val docs = rememberSearchDocs(facts)
  var query by rememberSaveable { mutableStateOf(SettingsShotState.query) }
  SettingsPageFrame(title = Str.settings, onBack = onBack, largeTitle = false) {
    item(key = "search") { SettingsSearchBox(query) { query = it } }
    if (query.isBlank()) {
      item(key = "home") { SettingsHomeCard(selected = null) { nav.open(SettingsPaths.home(it)) } }
      item(key = "features") { FeaturesLink { nav.open(SettingsPaths.FEATURES) } }
    } else {
      searchResults(SettingsSearchIndex.search(docs, query), query) { nav.open(SettingsPaths.of(it)) }
    }
  }
}

/**
 *  صفحهٔ یک دسته (`settings/sales`…) — همان ردیفی که «بیشتر ⇐ تنظیمات» می‌زند یک‌راست این‌جا می‌آید: عنوانِ درشت =
 *  نامِ دسته و چند بخشِ کوتاه با سرخطِ خودشان (`SettingsLayout.Part`).
 */
@Composable
internal fun RouteScope.SettingsCategoryScreen(group: String, onBack: (() -> Unit)?) {
  CategoryBody(group, rememberSettingsFacts(theme), onBack, onRow = { nav.open(SettingsPaths.section(it)) }, onExtra = { nav.open(it) })
}

/** بدنهٔ صفحهٔ دسته — گوشی (صفحهٔ خودش) و کامپیوتر (ستونِ پایان) همین را می‌کشند */
@Composable
internal fun RouteScope.CategoryBody(
  group: String,
  facts: SettingsFacts,
  onBack: (() -> Unit)?,
  onRow: (String) -> Unit,
  onExtra: (String) -> Unit,
) {
  val parts = rememberCategoryParts(group, facts, rememberFeatureStates().on)
  val lockToggle = rememberLockToggle()
  SettingsPageFrame(title = SettingsLayout.groupLabel(group), onBack = onBack) {
    parts.forEach { p ->
      item(key = p.id) {
        PartCard(
          p, facts, onRow, onExtra, lockToggle,
          footer = if (p.id == "access") StrSettings.securityHelp else null,
        )
      }
    }
  }
}

/** کادرِ جست‌وجوی تنظیمات — عنوان، خطِ ساده و واژه‌های هر ردیف به هر سه زبان و دری */
@Composable
internal fun SettingsSearchBox(query: String, onChange: (String) -> Unit) {
  V3SearchField(query, onChange, StrSettingsHome.searchHint, Modifier.fillMaxWidth().padding(top = 8.dp))
}

/** شش دستهٔ صفحهٔ اول: کاشیِ رنگی، نام و یک خطِ ساده؛ `selected` (کامپیوتر) دستهٔ بازِ ستونِ کناری */
@Composable
internal fun SettingsHomeCard(selected: String?, onOpen: (String) -> Unit) {
  V3Section(null) {
    SettingsLayout.GROUPS.forEachIndexed { i, g ->
      if (i > 0) V3Divider(start = ROW_DIVIDER_START + 12.dp)
      Box {
        V3ListRow(
          SettingsLayout.groupLabel(g.title),
          subtitle = SettingsLayout.groupLine(g.title),
          icon = SettingsPages.icon(g.title),
          hue = settingsHue(g.title),
          onClick = { onOpen(g.title) },
        )
        if (g.title == selected) SelectedMark(Modifier.align(Alignment.CenterStart))
      }
    }
  }
}

/** پیوندِ کوچکِ «قابلیت‌ها» زیرِ شش دسته — نوشتهٔ کوچک و یک دکمهٔ متنی، نه ردیفِ هفتم */
@Composable
internal fun FeaturesLink(onOpen: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(top = 14.dp, start = 6.dp, end = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(StrSettingsHome.featuresLine, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.weight(1f))
    V3TextButton(StrSettingsHome.features, onOpen)
  }
}

/** ردیف‌های پیداشدنیِ همین سکو و همین لحظه (پرچم‌های سرور، ردیف‌های ماژول‌ها) به زبانِ صفحه */
@Composable
internal fun RouteScope.rememberSearchDocs(facts: SettingsFacts): List<SettingsSearchIndex.Doc> {
  val entries = remember(access) { V3Modules.settingsEntries(access) }
  val extras = entries.filter { it.shown() }.map { SettingsSearchIndex.Extra(it.route, it.label(), it.subtitle(), it.group) }
  return SettingsSearchIndex.docs(Platform.DESKTOP, facts.serverOn, extras)
}

/** نتیجه‌های جست‌وجو: هر ردیف نام، جایش در تنظیمات («فروش و پرداخت · پرداخت») و برچسبِ «آزمایشی» اگر باشد */
internal fun LazyListScope.searchResults(hits: List<SettingsSearchIndex.Doc>, query: String, onOpen: (SettingsSearchIndex.Doc) -> Unit) {
  if (hits.isEmpty()) {
    item(key = "no-results") { V3EmptyState(Icons.Outlined.SearchOff, StrSettingsHome.noResults(query), body = StrSettingsHome.noResultsHint) }
    return
  }
  item(key = "results") {
    V3Section(null) {
      hits.forEachIndexed { i, d ->
        if (i > 0) V3Divider(start = ROW_DIVIDER_START)
        V3ListRow(
          d.title,
          subtitle = d.place.ifBlank { d.subtitle ?: StrSettingsHome.categoryHit },
          icon = when (d.kind) {
            SettingsSearchIndex.Kind.FEATURES -> Icons.Outlined.Extension
            else -> SettingsPages.icon(d.key)
          },
          tile = false,
          trailing = if (d.experimental) ({ V3StatusPill(V3Status.WARNING, StrSettingsHome.experimental) }) else null,
          onClick = { onOpen(d) },
        )
      }
    }
  }
}

/**
 *  کلیدِ «قفلِ برنامه» در ردیف: روشن ⇐ برگهٔ رمز (`settings/shop/app-lock/pin`)؛
 *  خاموش ⇐ برگهٔ تأییدِ «برداشتن رمز».
 */
@Composable
internal fun RouteScope.rememberLockToggle(): (Boolean) -> Unit {
  var askRemove by remember { mutableStateOf(false) }
  if (askRemove) RemovePinSheet(onDismiss = { askRemove = false })
  return { on -> if (on) nav.open(SettingsPaths.PIN) else askRemove = true }
}
