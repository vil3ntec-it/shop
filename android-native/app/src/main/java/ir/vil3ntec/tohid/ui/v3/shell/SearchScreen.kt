package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSearch
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.LocalV3Notice
import ir.vil3ntec.tohid.ui.v3.V3BackButton
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.screens.V3GroupTitle
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** درنگِ جست‌وجو پس از هر حرف (shell.md) */
private const val DEBOUNCE_MS = 120L
/** نتیجهٔ هر گروه پیش از «همهٔ n نتیجه» */
private const val PREVIEW = 5

/**
 *  جست‌وجو (`search?q=&scan=1`) — phone v3 §F روی کیت: دکمهٔ گردِ برگشت و کادرِ «کالا،
 *  مشتری، فاکتور یا بخش…» با اسکن و پاک کردن؛ کادرِ خالی ⇐ شبکهٔ «همهٔ بخش‌ها». نتیجه‌ها
 *  گروه‌به‌گروه (حداکثر ۵ + «همهٔ n نتیجه»)، تکهٔ پیداشده برندرنگ، و هر نتیجه **همان سند** را
 *  باز می‌کند (`SearchIndex`). Enter ⇐ نخستین سند.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
  d: ShopData,
  sections: List<SearchIndex.Section>,
  initialQuery: String,
  onOpen: (String) -> Unit,
  onScan: () -> Unit,
  onBack: (() -> Unit)?,
  bottomReserve: Dp,
) {
  val p = PhoneV3.colors
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  var results by remember { mutableStateOf(SearchIndex.search(d, initialQuery, sections)) }
  var expanded by remember(query) { mutableStateOf(emptySet<SearchIndex.Kind>()) }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { if (initialQuery.isEmpty()) runCatching { focus.requestFocus() } }
  LaunchedEffect(query, d, sections) {
    if (query != results.query) delay(DEBOUNCE_MS)
    results = withContext(Dispatchers.Default) { SearchIndex.search(d, query, sections) }
  }

  Column(Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.statusBars)) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      if (onBack != null) { V3BackButton(onBack); Spacer(Modifier.width(10.dp)) }
      V3SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = StrSearch.placeholder,
        modifier = Modifier.weight(1f).focusRequester(focus),
        onScan = onScan,
        onSubmit = { results.first?.let { onOpen(it.route) } },
      )
    }
    LocalV3Notice.current?.let { notice -> Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) { notice() } }
    AnimatedContent(
      targetState = results,
      transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) },
      contentKey = { it.query.isBlank() to it.isEmpty },
      modifier = Modifier.weight(1f),
      label = "searchResults",
    ) { r ->
      when {
        r.query.isBlank() -> SectionsGrid(r.of(SearchIndex.Kind.SECTION)?.hits.orEmpty(), onOpen, bottomReserve)
        r.isEmpty -> Box(Modifier.fillMaxSize().padding(bottom = bottomReserve)) {
          V3EmptyState(Icons.Outlined.SearchOff, StrSearch.nothing, body = StrSearch.nothingBody)
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomReserve + 16.dp)) {
          r.groups.filter { it.hits.isNotEmpty() }.forEach { g ->
            stickyHeader(key = "h:" + g.kind) {
              Box(Modifier.fillMaxWidth().background(p.bg).padding(top = 14.dp)) { V3GroupTitle(groupTitle(g.kind)) }
            }
            item(key = "g:" + g.kind) {
              val open = g.kind in expanded
              HitCard(g, if (open) g.hits else g.hits.take(PREVIEW), showMore = !open && g.total > PREVIEW, onMore = { expanded = expanded + g.kind }, onOpen = onOpen)
            }
          }
        }
      }
    }
  }
}

private fun groupTitle(k: SearchIndex.Kind): String = when (k) {
  SearchIndex.Kind.PRODUCT -> StrSearch.products
  SearchIndex.Kind.DEBTOR -> StrSearch.customers
  SearchIndex.Kind.INVOICE -> StrSearch.invoices
  SearchIndex.Kind.SECTION -> StrSearch.sections
}

/** آیکن و رنگِ یک بخش از فهرستِ ماژول‌ها (همان که «بیشتر» و نوار نشان می‌دهند) */
internal fun sectionLook(route: String): Pair<ImageVector, V3Hue?> {
  val base = ShellTargets.base(route)
  val e = V3Modules.ALL.asSequence().flatMap { it.entries.asSequence() }.firstOrNull { ShellTargets.base(it.route) == base }
  return (e?.icon ?: Icons.Outlined.GridView) to (e?.hue ?: V3Hue.BRAND)
}

internal fun hitLook(h: SearchIndex.Hit): Pair<ImageVector, V3Hue?> = when (h.kind) {
  SearchIndex.Kind.PRODUCT -> Icons.Outlined.LocalOffer to V3Hue.BRAND
  SearchIndex.Kind.DEBTOR -> Icons.Outlined.Person to V3Hue.BLUE
  SearchIndex.Kind.INVOICE -> Icons.AutoMirrored.Outlined.ReceiptLong to V3Hue.PURPLE
  SearchIndex.Kind.SECTION -> sectionLook(h.route)
}

@Composable
private fun HitCard(g: SearchIndex.Group, hits: List<SearchIndex.Hit>, showMore: Boolean, onMore: () -> Unit, onOpen: (String) -> Unit) {
  val c = PhoneV3.colors
  val brand = PhoneV3Roles.brandText(c)
  val stagger = rememberV3Stagger(g.kind)
  V3Card(padding = PaddingValues(vertical = 4.dp)) {
    hits.forEachIndexed { i, h ->
      if (i > 0) V3Divider(start = 64.dp)
      val (icon, hue) = hitLook(h)
      DraftHitRow(highlighted(h.title, h.match, brand), h.hint, icon, hue, onClick = { onOpen(h.route) }, modifier = Modifier.v3Stagger(stagger, h.kind.name + h.id, i))
    }
    if (showMore) {
      V3Divider(start = 16.dp)
      Text(
        StrSearch.showAll(g.total),
        style = v3Text(14, FontWeight.SemiBold, 20),
        color = brand,
        modifier = Modifier.fillMaxWidth().v3Pressable(label = StrSearch.showAll(g.total), onClick = onMore).padding(horizontal = 16.dp, vertical = 14.dp),
      )
    }
  }
}

/** «همهٔ بخش‌ها» — شبکهٔ چهارستونهٔ میان‌برها با کاشیِ رنگیِ همان بخش */
@Composable
private fun SectionsGrid(sections: List<SearchIndex.Hit>, onOpen: (String) -> Unit, bottomReserve: Dp) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger()
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomReserve + 16.dp)) {
    item(key = "title") { V3GroupTitle(StrSearch.allSections) }
    item(key = "grid") {
      V3Card(padding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
        sections.chunked(4).forEachIndexed { r, row ->
          if (r > 0) Spacer(Modifier.height(12.dp))
          Row(Modifier.fillMaxWidth()) {
            row.forEachIndexed { col, s ->
              val (icon, hue) = sectionLook(s.route)
              Column(
                Modifier.weight(1f).v3Stagger(stagger, s.route, r * 4 + col).v3Pressable(label = s.title, scale = 0.94f) { onOpen(s.route) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                V3IconTile(icon, hue, size = 48.dp)
                Spacer(Modifier.height(6.dp))
                Text(s.title, style = v3Text(12, FontWeight.Medium, 16), color = c.ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}
