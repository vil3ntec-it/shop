package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockActions
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Search
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.V3ContainerTransform
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
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
  /** ثبتِ ورود از صفحهٔ دکمهٔ شناور: null یعنی ثبت شد؛ وگرنه دلیلِ رد */
  val saveEntry: (ir.vil3ntec.tohid.data.WarehouseEngine.EntryDraft) -> String?,
  /** کارهای افزوده (`StockExtras`): پسِ «…» ِ گوشی و تراشه‌های کارِ کامپیوتر */
  val extras: List<V3MenuItem> = emptyList(),
  /** بالای فهرست، زیرِ کارتِ آمار (نوارِ «دفترِ موجودی» با موتورِ تازه) */
  val header: @Composable () -> Unit = {},
)

/**
 *  «انبار» (`stock`): عنوانِ درشتِ جمع‌شونده، اسکن و «…» در سربرگ، جست‌وجو و تراشه‌های
 *  فیلتر که با پیمایش می‌مانند، ردیف‌های کشیدنی، خطِ کم‌رنگِ جمع در پایان (گوشی؛ کامپیوتر کارتِ
 *  آمار با شمارنده) و دکمهٔ شناورِ «ورود کالا». حساب‌ها روی رشتهٔ پس‌زمینه (`StockListModel.overview`) و تا آماده شود
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
  /** INV-01: «محل‌های نگه‌داری» ِ همین دستگاه روشن (و دفترِ تازهٔ موجودی خاموش) ⇒ تراشهٔ هر محل */
  places: Boolean = false,
) {
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  var place by rememberSaveable { mutableStateOf<String?>(null) }
  var filterKey by rememberSaveable(initialFilter) { mutableStateOf(initialFilter.key) }
  var category by rememberSaveable { mutableStateOf<String?>(null) }
  val filter = StockFilter.of(filterKey)
  val overview by produceState<StockOverview?>(null, data, today, places) {
    value = withContext(Dispatchers.Default) { StockListModel.overview(data, today, places) }
  }
  val categories = remember(data) { StockListModel.usedCategories(data) }
  val empty = data.products.isEmpty()
  //  فهرست زیرِ صفحهٔ «ورود کالا» از ترکیب بیرون می‌رود؛ جای پیمایش و پلکان همین‌جا می‌ماند
  val listState = rememberLazyListState()
  val stagger = rememberV3Stagger()
  var entryOpen by rememberSaveable { mutableStateOf(false) }
  var entryRun by rememberSaveable { mutableIntStateOf(0) }

  V3ContainerTransform(selected = if (entryOpen) FAB_KEY else null, modifier = modifier, list = {
  V3Scaffold(
    title = Str.warehouse,
    onBack = onBack,
    //  اسکن آیکنِ سربرگ (و درونِ کادرِ جست‌وجو)؛ جست‌وجو از دکمهٔ نوار؛ فیلترها زیرِ عنوان؛ «ورودِ کالا» تنها دکمهٔ شناور
    actions = listOf(V3Action(Icons.Outlined.QrCodeScanner, StrKit.scanBarcode, onClick = actions.scan)),
    overflow = if (wide) emptyList() else listOf(
      V3MenuItem(Icons.AutoMirrored.Outlined.FactCheck, StrStock.menuCount, onClick = actions.count),
      V3MenuItem(Icons.Outlined.UploadFile, StrStock.menuImport, onClick = actions.import),
      V3MenuItem(Icons.AutoMirrored.Outlined.PlaylistAdd, StrStock.menuBulk, onClick = actions.bulk),
    ) + actions.extras,
    search = if (empty) null else V3Search(query, { query = it }, StrStock.searchHint, onScan = actions.scan),
    chips = if (empty) null else ({
      FilterChips(overview, filter, categories, category, onFilter = { filterKey = it.key }, onCategory = { category = it }, place = place, onPlace = { place = it })
    }),
    //  دکمهٔ شناور با container transform به صفحهٔ «ورود کالا» باز می‌شود و به خودش برمی‌گردد
    fab = if (empty) null else ({ expanded ->
      V3Fab(Icons.Outlined.MoveToInbox, StrStock.fabEntry, expanded, onClick = { entryRun++; entryOpen = true }, modifier = Modifier.v3Container(FAB_KEY))
    }),
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
        place = place,
        wide = wide,
        selectedId = selectedId,
        actions = actions,
        listState = listState,
        stagger = stagger,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + FAB_CLEARANCE),
        onFilter = { filterKey = it.key },
        onClear = { query = ""; filterKey = StockFilter.ALL.key; category = null; place = null },
      )
    }
  }
  }, detail = {
    val flow = rememberEntryFlow(data, product = null, barcode = null, key = entryRun)
    StockEntryPage(
      data = data,
      flow = flow,
      onSave = actions.saveEntry,
      onNewProduct = { entryOpen = false; actions.newProduct() },
      onClose = { entryOpen = false },
      modifier = Modifier.v3Container(FAB_KEY),
    )
  })
}

private const val FAB_KEY = "stock-entry-fab"

/** نشانِ آزمونِ فهرستِ انبار (فقط `testTag`) */
object StockListSpec {
  const val LIST_TAG = "stock-list"
  const val FOOTER_TAG = "stock-footer"
}

/**
 *  جای دکمهٔ شناور زیرِ آخرین ردیف (۵۶ + ۱۶) تا ردیفِ آخر زیرِ «ورود کالا» نماند — نه جای
 *  نوارِ پایین: آن را پوسته خودش نگه می‌دارد (AUDIT-NAV S1/S6) و `pad` ِ `V3Scaffold` نوارِ سیستم را.
 */
private val FAB_CLEARANCE = 72.dp

@Composable
private fun FilterChips(
  overview: StockOverview?,
  filter: StockFilter,
  categories: List<String>,
  category: String?,
  onFilter: (StockFilter) -> Unit,
  onCategory: (String?) -> Unit,
  place: String?,
  onPlace: (String?) -> Unit,
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
    //  INV-01: موجودیِ هر محل (همان تراشه دوباره ⇒ همهٔ محل‌ها)
    overview?.let(StockListModel::placeChips).orEmpty().forEach { loc ->
      V3Chip(StockActions.locationLabel(loc), selected = place == loc, icon = Icons.Outlined.Place, onClick = { onPlace(if (place == loc) null else loc) })
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
  place: String?,
  wide: Boolean,
  selectedId: String?,
  actions: StockListActions,
  listState: LazyListState,
  stagger: V3StaggerState,
  contentPadding: PaddingValues,
  onFilter: (StockFilter) -> Unit,
  onClear: () -> Unit,
) {
  val rows = remember(overview, filter, query, category, place) { StockListModel.visible(StockListModel.atPlace(overview, place), filter, query, category) }
  //  ۶۸c: ردیف‌ها یک فهرستِ پیوسته با خطِ مویی (کالبدِ `V3DataRow`)، نه کارتِ جدا با فاصله؛ فاصلهٔ ۸ فقط میانِ کارت‌های بالا
  LazyColumn(Modifier.fillMaxSize().testTag(StockListSpec.LIST_TAG), state = listState, contentPadding = contentPadding) {
    //  ۱۴۰۵/۰۸/۲۴ (صاحب‌کار: «سربرگِ گدام رو می‌خوام چی کار آخه؟»): گوشی بی کارتِ خلاصهٔ بالا — همان عددها یک خطِ کم‌رنگ در پایانِ
    //  فهرست ([StockListFooter]) و هشدارهای «تمام‌شده/کم» همان تراشه‌های شمارداری فیلتر. کامپیوتر (دو ستون) کارت را نگه می‌دارد.
    if (wide) item(key = "stats") {
      StockStatsCard(overview.summary, onFilter, Modifier.padding(bottom = 12.dp).v3Stagger(stagger, "stats", 0))
    }
    if (wide) item(key = "desk-actions") {
      //  کامپیوتر: کارهای «…» همین‌جا دیده می‌شوند — تراشهٔ کار با آیکن، با پیمایشِ افقی اگر جا نشد
      V3ChipRow(Modifier.padding(bottom = 8.dp), contentPadding = PaddingValues(0.dp)) {
        V3Chip(StrStock.menuCount, selected = false, icon = Icons.AutoMirrored.Outlined.FactCheck, onClick = actions.count)
        V3Chip(StrStock.menuBulk, selected = false, icon = Icons.AutoMirrored.Outlined.PlaylistAdd, onClick = actions.bulk)
        V3Chip(StrStock.menuImport, selected = false, icon = Icons.Outlined.UploadFile, onClick = actions.import)
        actions.extras.forEach { x -> V3Chip(x.label, selected = false, icon = x.icon, onClick = x.onClick) }
      }
    }
    item(key = "header") { Box(Modifier.padding(bottom = 8.dp)) { actions.header() } }
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
        modifier = Modifier.v3AnimateItem(this).v3Stagger(stagger, id, index),
        first = index == 0,
        last = index == rows.lastIndex,
      )
    }
    if (!wide && rows.isNotEmpty()) item(key = "footer") { StockListFooter(overview.summary) }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}
