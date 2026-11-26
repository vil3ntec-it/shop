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
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3NavRow
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
  //  جست‌وجو با دکمهٔ نوار (گوشی) یا زیرِ عنوان (کامپیوتر) — هرگز ردیفی بالای نوارِ پایین
  SettingsPageFrame(
    title = Str.settings, onBack = onBack, largeTitle = false,
    search = ir.vil3ntec.tohid.ui.v3.V3Search(query, { query = it }, StrSettingsHome.searchHint),
  ) {
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
  var expanded by rememberSaveable(group) { mutableStateOf(false) }
  val common = parts.map(CategoryPart::commonOnly).filter { it.rows.isNotEmpty() }
  val canExpand = common.isNotEmpty() && parts.any(CategoryPart::hasAdvanced)
  val shown = if (canExpand && !expanded) common else parts
  SettingsPageFrame(title = SettingsLayout.groupLabel(group), onBack = onBack) {
    shown.forEach { p ->
      item(key = p.id) {
        PartCard(
          p, facts, onRow, onExtra, lockToggle,
          footer = if (p.id == "access") StrSettings.securityHelp else null,
        )
      }
    }
    if (canExpand) {
      item(key = "advanced-settings") {
        V3TextButton(if (expanded) StrSettingsHome.hideAdvanced else StrSettingsHome.showAdvanced, { expanded = !expanded })
      }
    }
  }
}

/** کادرِ جست‌وجوی تنظیمات — عنوان، خطِ ساده و واژه‌های هر ردیف به هر سه زبان و دری */
@Composable
internal fun SettingsSearchBox(query: String, onChange: (String) -> Unit) {
  V3SearchField(query, onChange, StrSettingsHome.searchHint, Modifier.fillMaxWidth().padding(top = 8.dp))
}

/** شش دستهٔ صفحهٔ اول: همان ردیفِ «بیشتر» (`V3NavRow`) — کاشیِ رنگیِ دسته، نام و یک خطِ ساده؛ `selected` (کامپیوتر) دستهٔ بازِ ستونِ کناری */
@Composable
internal fun SettingsHomeCard(selected: String?, onOpen: (String) -> Unit) {
  V3Section(null) {
    SettingsLayout.GROUPS.forEachIndexed { i, g ->
      if (i > 0) V3Divider(start = ROW_DIVIDER_START)
      Box {
        V3NavRow(
          SettingsLayout.groupLabel(g.title),
          icon = SettingsPages.icon(g.title),
          hue = SettingsHues.of(g.title),
          help = SettingsLayout.groupLine(g.title),
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
    Text(StrSettingsHome.featuresLine, style = V3Type.rowHelp, color = PhoneV3.colors.inkMuted, modifier = Modifier.weight(1f))
    V3TextButton(StrSettingsHome.features, onOpen)
  }
}

/** ردیف‌های پیداشدنیِ همین سکو و همین لحظه (پرچم‌های سرور، ردیف‌های ماژول‌ها) به زبانِ صفحه */
@Composable
internal fun RouteScope.rememberSearchDocs(facts: SettingsFacts): List<SettingsSearchIndex.Doc> {
  val entries = remember(access) { V3Modules.settingsEntries(access) }
  val extras = entries.filter { it.shown() }.map { SettingsSearchIndex.Extra(it.route, it.label(), it.subtitle(), it.group) }
  //  ردیفِ فقط‌صاحب («سرورها») در جست‌وجوی دیگران هم نیست
  return SettingsSearchIndex.docs(Platform.DESKTOP, facts.serverOn, extras).filter { facts.owner || it.key !in SettingsLayout.OWNER_ONLY }
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
        V3NavRow(
          d.title,
          icon = when (d.kind) {
            SettingsSearchIndex.Kind.FEATURES -> Icons.Outlined.Extension
            else -> SettingsPages.icon(d.key)
          },
          hue = searchHue(d),
          help = d.place.ifBlank { d.subtitle ?: StrSettingsHome.categoryHit },
          trailing = if (d.experimental) ({ V3StatusPill(V3Status.WARNING, StrSettingsHome.experimental) }) else null,
          onClick = { onOpen(d) },
        )
      }
    }
  }
}

/** رنگِ کاشیِ نتیجهٔ جست‌وجو — همان رنگی که ردیف در جای خودش دارد (`SettingsHues`) */
private fun searchHue(d: SettingsSearchIndex.Doc): ir.vil3ntec.tohid.ui.v3.V3Hue = when (d.kind) {
  SettingsSearchIndex.Kind.FEATURES -> SettingsHues.FEATURES
  SettingsSearchIndex.Kind.EXTRA ->
    V3Modules.ALL.asSequence().flatMap { it.settings.asSequence() }.firstOrNull { it.route == d.key }?.let { SettingsHues.extra(it.group, it.part) } ?: SettingsHues.FEATURES
  else -> SettingsHues.of(d.key)
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
