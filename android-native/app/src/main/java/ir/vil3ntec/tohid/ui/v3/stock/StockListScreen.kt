package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** کارهای فهرستِ انبار — همه مسیرند (`StockRoutes`) */
@Immutable
class StockListActions(
  val open: (productId: String) -> Unit,
  val entry: (productId: String?) -> Unit,
  val adjust: (productId: String) -> Unit,
  val scan: () -> Unit,
  val count: () -> Unit,
  val import: () -> Unit,
  val bulk: () -> Unit,
  val newProduct: () -> Unit,
)

/**
 *  «انبار» (`stock`): عنوانِ درشتِ جمع‌شونده، اسکن و «…» در سربرگ، جست‌وجو و تراشه‌های
 *  فیلتر که با پیمایش می‌مانند، کارتِ آمار با شمارنده، ردیف‌های کشیدنی و دکمهٔ شناورِ
 *  «ورود کالا». حساب‌ها روی رشتهٔ پس‌زمینه (`StockListModel.overview`) و تا آماده شود
 *  اسکلت؛ دفترِ تازه که برسد عددِ پیشین می‌ماند و شمارنده تا عددِ تازه می‌رود.
 *
 *  `wide` (کامپیوتر): کارهای «…» دکمه‌های زیرِ جست‌وجو می‌شوند و ردیفِ بازِ کنار
 *  (`selectedId`) مرزِ برند دارد.
 */
@Composable
fun StockListScreen(
  data: ShopData,
  today: String,
  actions: StockListActions,
  modifier: Modifier = Modifier,
  initialFilter: StockFilter = StockFilter.ALL,
  initialQuery: String = "",
  onBack: (() -> Unit)? = null,
  wide: Boolean = false,
  selectedId: String? = null,
) {
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  var filterKey by rememberSaveable(initialFilter) { mutableStateOf(initialFilter.key) }
  var category by rememberSaveable { mutableStateOf<String?>(null) }
  val filter = StockFilter.of(filterKey)
  val overview by produceState<StockOverview?>(null, data, today) {
    value = withContext(Dispatchers.Default) { StockListModel.overview(data, today) }
  }
  val categories = remember(data) { StockListModel.usedCategories(data) }
  val empty = data.products.isEmpty()

  V3Scaffold(
    title = Str.warehouse,
    modifier = modifier,
    onBack = onBack,
    actions = listOf(V3Action(Icons.Outlined.QrCodeScanner, StrKit.scanBarcode, onClick = actions.scan)),
    overflow = if (wide) emptyList() else listOf(
      V3MenuItem(Icons.AutoMirrored.Outlined.FactCheck, StrStock.menuCount, onClick = actions.count),
      V3MenuItem(Icons.Outlined.UploadFile, StrStock.menuImport, onClick = actions.import),
      V3MenuItem(Icons.AutoMirrored.Outlined.PlaylistAdd, StrStock.menuBulk, onClick = actions.bulk),
    ),
    search = if (empty) null else ({ V3SearchField(query, { query = it }, StrStock.searchHint) }),
    chips = if (empty) null else ({
      FilterChips(overview, filter, categories, category, onFilter = { filterKey = it.key }, onCategory = { category = it })
    }),
    fab = if (empty) null else ({ expanded -> V3Fab(Icons.Outlined.MoveToInbox, StrStock.fabEntry, expanded, onClick = { actions.entry(null) }) }),
  ) { pad ->
    val ready = overview
    when {
      empty -> V3EmptyState(
        Icons.Outlined.Inventory2, StrStock.emptyTitle,
        body = StrStock.emptyBody, action = StrStock.emptyAction, onAction = actions.newProduct,
      )
      ready == null -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        V3Skeleton(rows = 6, modifier = Modifier.padding(top = 4.dp))
      }
      else -> StockList(
        overview = ready,
        filter = filter,
        query = query,
        category = category,
        wide = wide,
        selectedId = selectedId,
        actions = actions,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 72.dp),
        onFilter = { filterKey = it.key },
        onClear = { query = ""; filterKey = StockFilter.ALL.key; category = null },
      )
    }
  }
}

@Composable
private fun FilterChips(
  overview: StockOverview?,
  filter: StockFilter,
  categories: List<String>,
  category: String?,
  onFilter: (StockFilter) -> Unit,
  onCategory: (String?) -> Unit,
) {
  val counts = overview?.counts.orEmpty()
  V3ChipRow {
    StockListModel.shownFilters(counts, filter).forEach { f ->
      V3Chip(
        label = filterLabel(f),
        selected = f == filter,
        onClick = { onFilter(f) },
        count = counts[f]?.takeIf { f != StockFilter.ALL && it > 0 },
      )
    }
    categories.forEach { name ->
      V3Chip(name, selected = category == name, icon = Icons.Outlined.Category, onClick = { onCategory(if (category == name) null else name) })
    }
  }
}

internal fun filterLabel(f: StockFilter): String = when (f) {
  StockFilter.ALL -> StrStock.filterAll
  StockFilter.LOW -> StrStock.filterLow
  StockFilter.OUT -> StrStock.filterOut
  StockFilter.EXPIRING -> StrStock.filterExpiring
  StockFilter.EXPIRED -> StrStock.filterExpired
}

@Composable
private fun StockList(
  overview: StockOverview,
  filter: StockFilter,
  query: String,
  category: String?,
  wide: Boolean,
  selectedId: String?,
  actions: StockListActions,
  contentPadding: PaddingValues,
  onFilter: (StockFilter) -> Unit,
  onClear: () -> Unit,
) {
  val rows = remember(overview, filter, query, category) { StockListModel.visible(overview.rows, filter, query, category) }
  val stagger = rememberV3Stagger()
  LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item(key = "stats") {
      StockStatsCard(overview.summary, onFilter, Modifier.padding(bottom = 4.dp).v3Stagger(stagger, "stats", 0))
    }
    if (wide) item(key = "desk-actions") {
      //  کامپیوتر: کارهای «…» همین‌جا دیده می‌شوند — تراشهٔ کار با آیکن، با پیمایشِ افقی اگر جا نشد
      V3ChipRow(contentPadding = PaddingValues(0.dp)) {
        V3Chip(StrStock.menuCount, selected = false, icon = Icons.AutoMirrored.Outlined.FactCheck, onClick = actions.count)
        V3Chip(StrStock.menuBulk, selected = false, icon = Icons.AutoMirrored.Outlined.PlaylistAdd, onClick = actions.bulk)
        V3Chip(StrStock.menuImport, selected = false, icon = Icons.Outlined.UploadFile, onClick = actions.import)
      }
    }
    if (rows.isEmpty()) item(key = "no-match") {
      V3EmptyState(Icons.Outlined.SearchOff, StrKit.nothingFound, body = StrStock.noMatchBody, action = StrStock.clearFilters, onAction = onClear)
    }
    itemsIndexed(rows, key = { _, r -> r.product.id }) { index, row ->
      val id = row.product.id
      StockRowItem(
        row = row,
        selected = id == selectedId,
        onOpen = { actions.open(id) },
        onEntry = { actions.entry(id) },
        onAdjust = { actions.adjust(id) },
        modifier = draftItem().v3Stagger(stagger, id, index),
      )
    }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}
