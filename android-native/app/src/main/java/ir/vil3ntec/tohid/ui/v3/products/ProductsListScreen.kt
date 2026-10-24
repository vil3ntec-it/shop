package ir.vil3ntec.tohid.ui.v3.products

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.components.common.Selection
import ir.vil3ntec.tohid.ui.screens.rememberVoiceSearch
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3ActionBar
import ir.vil3ntec.tohid.ui.v3.V3BarAction
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Layout
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Query

/** ناوبریِ فهرست — مسیرها را `ProductsRoutes` می‌دهد */
internal class ProductsListNav(
  val open: (String) -> Unit,
  val edit: (String) -> Unit,
  val entry: (String) -> Unit,
  /** فرمِ «کالای تازه» (کالاهای ثبت‌شده با `RecentlyAdded` در فهرست می‌درخشند) */
  val new: () -> Unit,
  val scan: () -> Unit,
  /** برگهٔ دستهٔ گروهی؛ پس از تغییر `onDone` (انتخاب پاک می‌شود) */
  val bulkCategory: (ids: Set<String>, onDone: () -> Unit) -> Unit,
)

/**
 *  «کالاها» (`products`، تبِ نوار): جست‌وجو، کارتِ آمار، فیلترِ دسته و وضعیت،
 *  مرتب‌سازی، فهرست یا شبکه، کشیدنِ ردیف (ورود / ویرایش / حذف)، انتخابِ گروهی با
 *  نگه‌داشتن و دکمهٔ شناورِ «کالای تازه».
 */
@Composable
internal fun ProductsListScreen(
  store: ShopStore,
  data: ShopData,
  toasts: V3Toasts,
  initial: Query,
  bottomReserve: Dp,
  nav: ProductsListNav,
  /** `products?view=grid|list` — وگرنه ترجیحِ همین دستگاه */
  initialLayout: Layout? = null,
  /** حالتِ انتخابِ آغازین (عکس‌های صفحه) */
  initialSelection: Set<String> = emptySet(),
) {
  val context = LocalContext.current
  val ops = rememberProductOps(store, toasts)
  val loaded by store.loaded.collectAsState()
  var query by rememberSaveable(stateSaver = QuerySaver) { mutableStateOf(initial) }
  var layout by remember { mutableStateOf(initialLayout ?: loadLayout(context)) }
  var selection by rememberSaveable(stateSaver = SetSaver) { mutableStateOf(initialSelection) }
  var sortOpen by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf<Product?>(null) }
  var confirmBulkDelete by remember { mutableStateOf(false) }

  val stock = remember(data) { ShopStore.index(data) }
  val flash = RecentlyAdded.fresh()
  val stockOf: (String) -> Double = remember(stock) { { id -> stock.stock(id) } }
  val rows = remember(data, query) { ProductsListModel.rows(data.products, stockOf, query) }
  val stats = remember(data) { ProductsListModel.stats(data.products, stockOf) }
  val counts = remember(data) { ProductsListModel.counts(data.products, stockOf) }
  val empty = ProductsListModel.empty(data.products.size, rows.size)
  //  کالایی که در این میان رفته (حذف، همگام‌سازی) از انتخاب بیرون می‌رود
  val picked = remember(selection, data) { Selection.prune(selection, data.products.mapTo(HashSet()) { it.id }) }
  val selecting = picked.isNotEmpty()
  val stagger = rememberV3Stagger(layout)
  val voice = rememberVoiceSearch { query = query.copy(text = it) }

  fun toggle(id: String) { selection = Selection.toggle(picked, id) }

  V3Scaffold(
    title = if (selecting) StrProducts.selectedTitle(picked.size) else StrProducts.listTitle,
    actions = if (selecting) listOf(V3Action(Icons.Outlined.Close, StrProducts.cancelSelection) { selection = emptySet() })
    else listOf(
      V3Action(Icons.Outlined.QrCodeScanner, StrKit.scanBarcode, onClick = nav.scan),
      if (layout == Layout.LIST) V3Action(Icons.Outlined.GridView, StrProducts.viewGrid) { layout = Layout.GRID; saveLayout(context, Layout.GRID) }
      else V3Action(Icons.Outlined.ViewAgenda, StrProducts.viewList) { layout = Layout.LIST; saveLayout(context, Layout.LIST) },
    ),
    search = { V3SearchField(query.text, { query = query.copy(text = it) }, StrProducts.searchHint, onVoice = voice) },
    bottomReserve = bottomReserve,
    fab = if (selecting) null else ({ expanded ->
      V3Fab(Icons.Outlined.Add, StrProducts.newProduct, expanded, onClick = nav.new)
    }),
    bottomBar = if (!selecting) null else ({
      V3ActionBar(
        count = picked.size,
        onClear = { selection = emptySet() },
        actions = listOf(
          V3BarAction(Icons.Outlined.ToggleOn, StrProducts.activate) { ops.setActive(picked, true) { selection = emptySet() } },
          V3BarAction(Icons.Outlined.ToggleOff, StrProducts.deactivate) { ops.setActive(picked, false) { selection = emptySet() } },
          V3BarAction(Icons.Outlined.Category, StrProducts.bulkCategory) { nav.bulkCategory(picked) { selection = emptySet() } },
          V3BarAction(Icons.Outlined.DeleteOutline, StrProducts.delete, danger = true) { confirmBulkDelete = true },
        ),
      )
    }),
  ) { pad ->
    val cols = if (layout == Layout.GRID) 2 else 1
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
      columns = GridCells.Fixed(cols),
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + FAB_SPACE),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(if (layout == Layout.GRID) 10.dp else 0.dp),
    ) {
      if (!loaded) {
        item(key = "skeleton", span = full) { V3Skeleton(rows = 6) }
        return@LazyVerticalGrid
      }
      if (data.products.isNotEmpty()) {
        item(key = "stats", span = full) { ProductsStatsCard(stats, Modifier.padding(bottom = 12.dp)) }
        item(key = "filters", span = full) {
          ProductsFilterRow(query, data.productCategories, counts, onQuery = { query = it }, onSort = { sortOpen = true }, modifier = Modifier.padding(bottom = 12.dp))
        }
      }
      when (empty) {
        ProductsListModel.Empty.NO_PRODUCTS -> item(key = "empty", span = full) {
          V3EmptyState(Icons.Outlined.Inventory2, StrProducts.emptyTitle, body = StrProducts.emptyBody, action = StrProducts.emptyAction, onAction = nav.new)
        }
        ProductsListModel.Empty.NO_MATCH -> item(key = "nomatch", span = full) {
          V3EmptyState(
            Icons.Outlined.SearchOff, StrKit.nothingFound,
            action = if (query.narrowed) StrProducts.clearFilters else null,
            onAction = { query = Query(sort = query.sort) },
          )
        }
        ProductsListModel.Empty.NONE -> itemsIndexed(rows, key = { _, p -> p.id }) { i, p ->
          val s = stock.stock(p.id)
          val badge = ProductsListModel.badge(p, s)
          val sel = if (selecting) p.id in picked else null
          val click = { if (selecting) toggle(p.id) else nav.open(p.id) }
          val long = { toggle(p.id) }
          val m = Modifier.draftAnimateGridItem(this).v3Stagger(stagger, p.id, i)
          if (layout == Layout.GRID) {
            ProductCell(p, s, badge, sel, p.id in flash, click, long, m)
          } else {
            Column(m.draftGroupItem(first = i == 0, last = i == rows.lastIndex)) {
              ProductRow(
                p, s, badge, sel, p.id in flash,
                swipes = if (selecting) null else RowSwipes(
                  onEntry = { nav.entry(p.id) }, onEdit = { nav.edit(p.id) }, onDelete = { confirmDelete = p },
                ),
                onClick = click,
                onLongClick = long,
              )
              if (i < rows.lastIndex) RowDivider()
            }
          }
        }
      }
    }
  }

  if (sortOpen) SortSheet(query.sort, onPick = { query = query.copy(sort = it) }, onDismiss = { sortOpen = false })
  confirmDelete?.let { p ->
    V3ConfirmSheet(
      title = StrProducts.deleteTitle(p.name),
      message = WarehouseEngine.deleteWarning(data, p.id),
      confirm = StrProducts.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { ops.delete(p.id) },
      onDismiss = { confirmDelete = null },
    )
  }
  if (confirmBulkDelete) {
    V3ConfirmSheet(
      title = StrProducts.bulkDeleteTitle(picked.size),
      message = StrProducts.bulkDeleteBody,
      confirm = StrProducts.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { ops.deleteMany(picked) { selection = emptySet() } },
      onDismiss = { confirmBulkDelete = false },
    )
  }
}

/**
 *  کالاهایی که همین حالا در فرمِ «کالای تازه» ثبت شدند — فهرست وقتی دوباره دیده
 *  می‌شود (فرم بسته شده و صفحهٔ فهرست از نو ساخته شده) همان ردیف‌ها را کوتاه
 *  می‌درخشاند. پس از چند ثانیه کهنه است و دیگر نمی‌درخشد.
 */
internal object RecentlyAdded {
  private var ids by mutableStateOf<Set<String>>(emptySet())
  private var at = 0L

  fun mark(added: Collection<String>) { ids = added.toSet(); at = System.currentTimeMillis() }

  fun fresh(now: Long = System.currentTimeMillis()): Set<String> = if (now - at < FRESH_MS) ids else emptySet()

  private const val FRESH_MS = 4_000L
}

/** جای خالیِ زیرِ آخرین ردیف تا دکمهٔ شناور رویش ننشیند */
private val FAB_SPACE = 80.dp

private const val PREFS = "tohid-ui"
private const val LAYOUT_KEY = "products_layout"

private fun loadLayout(context: Context): Layout =
  runCatching { Layout.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAYOUT_KEY, null)) }.getOrDefault(Layout.LIST)

private fun saveLayout(context: Context, layout: Layout) {
  runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAYOUT_KEY, layout.id).apply() }
}

private val QuerySaver = listSaver<Query, String>(
  save = { listOf(it.text, it.category.orEmpty(), it.filter.id.orEmpty(), it.sort.id) },
  restore = { ProductsListModel.fromArgs(it[0], it[1], it[2], it[3]) },
)

private val SetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
