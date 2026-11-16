package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.product.ProductBarcodes
import ir.vil3ntec.tohid.core.product.ProductLabels
import ir.vil3ntec.tohid.core.product.SimilarNames
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.core.stock.StockLevelRules
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.common.*
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.components.pos.ProductThumb
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskEmpty
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskLabeledField
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskSidePanel
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskStatTile
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskToggleRow
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskNegativeTone
import ir.vil3ntec.tohid.ui.desk.DeskTone
import ir.vil3ntec.tohid.ui.desk.deskTone

/**
 *  محصولاتِ کامپیوتر — الگوی ثابتِ صفحهٔ پنل (VISUAL-DIRECTION §۳، v3 §۱۹.۵).
 *
 *  خواستهٔ صاحب مخزن: «چرا هر محصول به این بدبختی می‌شه رسوند». روی
 *  گوشی هر کالا یک پنجرهٔ جدا دارد؛ روی کامپیوتر:
 *
 *  • **عنوان و توضیح**، «خروجی»/«چاپ»/«ورودِ انبار» (ثانویه) و عملِ اصلیِ
 *    **«+ افزودن کالا»** (Ctrl+N) که فوکوس را به ردیفِ «کالای تازه» می‌برد.
 *  • **کارت‌های آماری** از دادهٔ واقعی (`ProductListModel.kpis`): همه، فعال،
 *    کم‌موجود، ناموجود — با کلیک، زبانهٔ همان عدد باز می‌شود.
 *  • **نوارِ ابزار**: جست‌وجو (Ctrl+F)، فیلترِ دسته، نمای کارتی/جدولی
 *    (ترجیحِ هر کاربر روی همین دستگاه، `ViewModePref`) و زبانه‌های وضعیت.
 *  • **ردیفِ «کالای تازه»**: نام، بارکد، دسته، واحد، خرید، فروش، موجودیِ اول.
 *    Tab بینِ خانه‌ها، **Enter ثبت** و ردیفِ بعدی آماده با فوکوس روی نام.
 *    بارکدخوان هم می‌تواند مستقیم در خانهٔ بارکد بخواند.
 *  • **جدول** (چک‌باکس، عکس+نام+دسته، بارکد و تاریخ، خرید/فروش، موجودی،
 *    نشانِ وضعیت) یا **شبکهٔ کارت‌ها** — هر دو همان داده، فیلتر و انتخاب.
 *    مرتب‌سازی با سرستون. کلیک ⇐ پنلِ ویرایش کنارِ فهرست (Enter/Ctrl+S ثبت؛ Esc).
 *  • **نوارِ کارِ گروهی** پس از انتخاب: خروجی، فعال/غیرفعال، حذف (فقط
 *    صاحب/مدیر، با دلیل) — هر کدام با پیش‌نمایش و گزارشِ نتیجهٔ هر ردیف.
 *
 *  منطق همان `WarehouseEngine` است که گوشی دارد — فقط ظاهر کامپیوتری است.
 */
@Composable
fun DeskProductsScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onOpenWarehouse: (String?) -> Unit,
  onOpenProduct: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }

  var search by remember { mutableStateOf("") }
  var tab by remember { mutableStateOf(ProductListModel.Tab.ALL) }
  var category by remember { mutableStateOf<String?>(null) }
  var sortBy by remember { mutableStateOf(ProductListModel.Sort.NAME) }
  var asc by remember { mutableStateOf(true) }
  var openId by remember { mutableStateOf<String?>(null) }
  var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
  var confirm by remember { mutableStateOf<BulkKind?>(null) }
  var priceOpen by remember { mutableStateOf(false) }
  var gridIds by remember { mutableStateOf<List<String>?>(null) } // PRD-22: جدولِ ویرایش، همان پرچمِ productTools
  val productTools = remember { ShopPolicy.productTools(context) }
  var results by remember { mutableStateOf<Pair<String, List<BulkRowResult>>?>(null) }
  //  INV-12: ویرایشِ گروهیِ حدها — فقط با تنظیمِ «نقطهٔ سفارش و بیشینه» (پیش‌فرض خاموش)
  val levelsOn = remember { ir.vil3ntec.tohid.data.ShopPolicy.stockLevels(context) }
  var levelsInput by remember { mutableStateOf(false) }
  var levelsPatch by remember { mutableStateOf<StockLevelRules.Patch?>(null) }
  val searchFocus = remember { FocusRequester() }
  val addFocus = remember { FocusRequester() }
  val view = rememberViewMode("products", ScreenKind.MANAGEMENT)
  //  حذف کارِ خطرناک است — فقط صاحب/مدیر (v3 §۱۹.۵ بندهای ۴ و ۶)
  val canDelete = remember(context) { !ir.vil3ntec.tohid.data.ShopRole.isStaff(context) }

  //  اسکن روی همین صفحه (`ScanRouter`): کالای ثبت‌شده ⇐ پنلِ ویرایش؛ ناشناس ⇐ ردیفِ «کالای تازه» با همان بارکد
  val incoming = remember { mutableStateOf<String?>(null) }
  val latest by rememberUpdatedState(d)
  val owner = remember { Any() }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { code ->
      val id = ShopStore.barcodeIndex(latest)[code]
      if (id != null) { openId = id; search = "" } else { incoming.value = code; toast(ir.vil3ntec.tohid.core.i18n.StrProductPages.newBarcode) }
      true
    }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(owner) }
  }
  //  «+ افزودن کالا» و Ctrl+N (`AppNav.addAction`) ⇐ خانهٔ نامِ ردیفِ «کالای تازه»
  val focusAdd = remember { { runCatching { addFocus.requestFocus() }; Unit } }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.ui.AppNav.addAction.value = focusAdd
    onDispose { if (ir.vil3ntec.tohid.ui.AppNav.addAction.value === focusAdd) ir.vil3ntec.tohid.ui.AppNav.addAction.value = null }
  }

  val index = remember(d) { ShopStore.index(d) }
  val stockOf: (String) -> Double = remember(index) { { id -> index.stock(id) } }
  val kpis = remember(d) { ProductListModel.kpis(d.products, stockOf, System.currentTimeMillis()) }
  val counts = remember(d) { ProductListModel.counts(d.products, stockOf) }
  val categories = remember(d.products) { ProductListModel.categories(d.products) }
  //  PRD-02 (پشتِ `ProductInfoPrefs`، پیش‌فرض خاموش): نام‌های دیگر و واژه‌های جست‌وجو هم
  val infoSearch = ir.vil3ntec.tohid.ui.components.product.productInfoOn()
  val rows = remember(d.products, index, search, tab, category, sortBy, asc, infoSearch) {
    ProductListModel.rows(d.products, stockOf, search, tab, category, sortBy, asc, infoSearch)
  }
  //  کالایی که رفته (حذف یا همگام‌سازی) از انتخاب بیرون می‌رود
  LaunchedEffect(d.products) { selected = Selection.prune(selected, d.products.mapTo(HashSet()) { it.id }) }
  fun sortOn(s: ProductListModel.Sort) { if (sortBy == s) asc = !asc else { sortBy = s; asc = true } }
  fun showTab(t: ProductListModel.Tab) { tab = t; category = null; search = "" }

  //  عکس‌های CI (`DevShot`): نما و انتخاب از بیرون
  val shotView = DeskProductsShot.view.value
  LaunchedEffect(shotView) { shotView?.let { ViewMode.parse(it)?.let(view::set); DeskProductsShot.view.value = null } }
  val shotSelect = DeskProductsShot.select.value
  LaunchedEffect(shotSelect) { if (shotSelect >= 0) { selected = rows.take(shotSelect).map { it.id }.toSet(); DeskProductsShot.select.value = -1 } }

  val dc = Desk.colors
  val tone = DeskHues.tone("products")
  //  انقضای نزدیک/گذشتهٔ هر کالا — یک بار برای کلِ دفتر (ستونِ «انقضا»)
  val expiry = remember(d) { deskExpiryOf(d, ir.vil3ntec.tohid.todayIso()) }
  fun exportAll() {
    val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", d, "", "", ir.vil3ntec.tohid.todayIso())
    ir.vil3ntec.tohid.ui.Platform.saveFile("${sheet.name}.xlsx", ir.vil3ntec.tohid.data.Xlsx.of(sheet))?.let { toast(ir.vil3ntec.tohid.core.i18n.StrSale.savedTo(it)) }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(dc.app)
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.isPrimaryModifier && e.key == Key.F -> { runCatching { searchFocus.requestFocus() }; true }
          //  Esc: اول پنلِ ویرایش خودش می‌بندد؛ بعد انتخاب پاک می‌شود
          e.key == Key.Escape && openId == null && selected.isNotEmpty() -> { selected = emptySet(); true }
          else -> false
        }
      }
      .padding(horizontal = DeskSpace.page, vertical = DeskSpace.x4),
  ) {
    /* ─────────── ۱. توضیح و کارها: سه کارِ دوم (خطِ دور) و «+ افزودن کالا» ِ اصلی با Ctrl+N ─────────── */
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
      //  نسخهٔ ۲: عنوانِ «محصولات» فقط در سربرگ است (نقدِ «همه‌چیز دو جا»)؛ این‌جا یک جملهٔ توضیح
      Text(ir.vil3ntec.tohid.core.i18n.StrProductPages.subtitle, Modifier.weight(1f), style = DeskType.secondary, color = dc.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrProductPages.stockIn, { onOpenWarehouse(null) }, icon = Icons.Filled.MoveToInbox)
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrHistory.print, {
        val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", d, "", "", ir.vil3ntec.tohid.todayIso())
        ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet, sheet.name)
      }, icon = Icons.Filled.Print)
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrProductPages.excelExport, ::exportAll, icon = Icons.Filled.FileDownload)
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrProductPages.addItem, focusAdd, icon = Icons.Filled.Add, kind = DeskButtonKind.PRIMARY, shortcut = "Ctrl+N")
    }
    Spacer(Modifier.height(DeskSpace.x4))

    /* ─────────── ۲. کارت‌های عدد — هر کدام زبانهٔ خودش را باز می‌کند ─────────── */
    Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
      DeskStatTile(
        ir.vil3ntec.tohid.core.i18n.StrStockPages.allItems, plain(kpis.total), Icons.Filled.Inventory2, tone, Modifier.weight(1f),
        note = (if (kpis.added > 0) ir.vil3ntec.tohid.core.i18n.StrProductPages.newItems(plain(kpis.added)) else ir.vil3ntec.tohid.core.i18n.StrProductPages.noNewItems) + " • " + ir.vil3ntec.tohid.core.i18n.StrHome.pastDays(plain(kpis.periodDays)),
        selected = tab == ProductListModel.Tab.ALL, onClick = { showTab(ProductListModel.Tab.ALL) },
      )
      DeskStatTile(
        ir.vil3ntec.tohid.core.i18n.StrSettings.active, plain(kpis.active), Icons.Filled.CheckCircle, Desk.colors.deskTone(DeskTone.POSITIVE), Modifier.weight(1f),
        note = ir.vil3ntec.tohid.core.i18n.StrOnline.dlNow, selected = tab == ProductListModel.Tab.ACTIVE, onClick = { showTab(ProductListModel.Tab.ACTIVE) },
      )
      DeskStatTile(
        ir.vil3ntec.tohid.core.i18n.Str.lowStock, plain(kpis.low), Icons.Filled.WarningAmber, Desk.colors.deskTone(DeskTone.WARNING), Modifier.weight(1f),
        note = ir.vil3ntec.tohid.core.i18n.StrProductPages.nowBelowAlert, selected = tab == ProductListModel.Tab.LOW, onClick = { showTab(ProductListModel.Tab.LOW) },
      )
      DeskStatTile(
        ir.vil3ntec.tohid.core.i18n.Str.outOfStock, plain(kpis.out), Icons.Filled.RemoveShoppingCart, Desk.colors.deskNegativeTone(), Modifier.weight(1f),
        note = ir.vil3ntec.tohid.core.i18n.StrProductPages.nowActiveItems, selected = tab == ProductListModel.Tab.OUT, onClick = { showTab(ProductListModel.Tab.OUT) },
      )
    }
    Spacer(Modifier.height(DeskSpace.x4))

    /* ─────────── ۳. نوارِ ابزار: جست‌وجو (Ctrl+F)، دسته (کشویی)، زبانه‌های وضعیت با شمار، نمای جدول/کارت ─────────── */
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
      DeskSearchField(
        search, { search = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.searchHint,
        Modifier.weight(1f).widthIn(max = 460.dp), focusRequester = searchFocus, clearLabel = ir.vil3ntec.tohid.core.i18n.StrSale.clearIt,
      )
      DeskDropdown(
        label = ir.vil3ntec.tohid.core.i18n.StrGrid.category,
        options = categories.map { DeskOption(it, it.ifEmpty { ir.vil3ntec.tohid.core.i18n.StrSale.uncategorized }) },
        selected = category,
        onSelect = { category = it },
        allLabel = ir.vil3ntec.tohid.core.i18n.StrCount.allCategories,
      )
      DeskSegmented(ProductListModel.Tab.entries.map { it to "${it.label} ${plain(counts[it] ?: 0)}" }, tab, { tab = it })
      if (view.toggleVisible) DeskSegmented(listOf(ViewMode.TABLE to ir.vil3ntec.tohid.core.i18n.Str.tableView, ViewMode.CARD to ir.vil3ntec.tohid.core.i18n.Str.cardView), view.mode, { view.set(it) })
    }
    Spacer(Modifier.height(DeskSpace.x3))

    Row(Modifier.weight(1f)) {
      Column(Modifier.weight(1f).fillMaxHeight()) {
        QuickAddRow(store, d, ::toast, incoming, addFocus); VisualSearchEntry(rememberVisualSearchApi(), d.products, ir.vil3ntec.tohid.core.product.VisualSearch.Place.LIST, Modifier.padding(top = 8.dp)) { t -> if (t is ir.vil3ntec.tohid.core.product.VisualSearch.Tap.Open) openId = t.productId } // AI-11: «مشابه» همان بازکردنِ کالا
        Spacer(Modifier.height(DeskSpace.x3))

        /* ─────────── ۴. جدول یا کارت‌ها + نوارِ کارِ گروهی ─────────── */
        Box(Modifier.weight(1f).fillMaxWidth()) {
          val toggle: (String) -> Unit = { id -> selected = Selection.toggle(selected, id) }
          when {
            d.products.isEmpty() -> DeskEmpty(
              Icons.Filled.Inventory2, ir.vil3ntec.tohid.core.i18n.StrProductPages.noItemsYet, ir.vil3ntec.tohid.core.i18n.StrProductPages.noItemsHint, tone,
              action = ir.vil3ntec.tohid.core.i18n.StrProductPages.addItem, onAction = focusAdd,
            )
            rows.isEmpty() -> DeskEmpty(
              Icons.Filled.Search, ir.vil3ntec.tohid.core.i18n.StrKit.nothingFound, ir.vil3ntec.tohid.core.i18n.StrProductPages.noMatch, tone,
              action = ir.vil3ntec.tohid.core.i18n.StrProductPages.clearSearchFilter, onAction = { showTab(ProductListModel.Tab.ALL) },
            )
            view.mode == ViewMode.TABLE -> DeskProductsTable(
              rows, stockOf, expiry, selected, openId, sortBy, asc, ::sortOn,
              onToggle = toggle,
              onToggleAll = { selected = Selection.toggleAll(selected, rows.map { it.id }) },
              onOpen = { openId = it },
              bottomSpace = if (selected.isNotEmpty()) 72.dp else 0.dp,
              photos = PhotoPrefs.show.value,
            )
            else -> DeskProductsGrid(
              rows, stockOf, selected, openId,
              onToggle = toggle,
              onOpen = { openId = it },
              bottomSpace = if (selected.isNotEmpty()) 72.dp else 0.dp,
              photos = PhotoPrefs.show.value,
            )
          }
          if (selected.isNotEmpty()) {
            BulkActionBar(
              count = selected.size,
              actions = buildList {
                add(BulkAction(ir.vil3ntec.tohid.core.i18n.StrSettings.exportTitle, Icons.Filled.FileDownload) {
                  val part = d.copy(products = d.products.filter { it.id in selected })
                  val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", part, "", "", ir.vil3ntec.tohid.todayIso())
                  ir.vil3ntec.tohid.ui.Platform.saveFile("${sheet.name}.xlsx", ir.vil3ntec.tohid.data.Xlsx.of(sheet))
                    ?.let { toast(ir.vil3ntec.tohid.core.i18n.StrProductPages.itemsSaved(plain(part.products.size), it)) }
                })
                //  PRD-21 (پیش‌فرض خاموش): قیمتِ گروهی با پیش‌نمایش، هشدار، تأیید و دلیل
                if (productTools) add(BulkAction(ir.vil3ntec.tohid.core.i18n.StrSale.price, Icons.Filled.Sell) { priceOpen = true })
                if (productTools) add(BulkAction(ir.vil3ntec.tohid.core.i18n.StrGrid.open, Icons.Filled.TableChart) { gridIds = rows.map { it.id }.filter { it in selected } })
                add(BulkAction(ir.vil3ntec.tohid.core.i18n.StrProducts.activate, Icons.Filled.CheckCircle) { confirm = BulkKind.ACTIVATE })
                add(BulkAction(ir.vil3ntec.tohid.core.i18n.StrProducts.deactivate, Icons.Filled.Block) { confirm = BulkKind.DEACTIVATE })
                if (levelsOn) add(BulkAction(ir.vil3ntec.tohid.core.i18n.StrProductPages.stockLevels, Icons.Filled.Tune) { levelsInput = true })
                if (canDelete) add(BulkAction(ir.vil3ntec.tohid.core.i18n.StrMoney.delete, Icons.Filled.Delete, destructive = true) { confirm = BulkKind.DELETE })
              },
              onClose = { selected = emptySet() },
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
          }
        }
        Text(
          ir.vil3ntec.tohid.core.i18n.StrProductPages.keysHint,
          Modifier.padding(top = DeskSpace.x2), style = DeskType.caption, color = dc.inkSecondary,
        )
      }

      /* ─────────── پنلِ ویرایش ─────────── */
      val open = openId?.let { id -> d.products.find { it.id == id } }
      if (open != null) {
        Spacer(Modifier.width(DeskSpace.gap))
        key(open.id) {
          EditPanel(
            store, d, open, stockOf(open.id), ::toast,
            onClose = { openId = null },
            onWarehouse = { onOpenWarehouse(open.id) },
            onDetail = { onOpenProduct(open.id) },
          )
        }
      }
    }
  }

  /* ─────────── پیش‌نمایش و نتیجهٔ کارِ گروهی ─────────── */
  gridIds?.let { ids -> ProductGridSheet(store, d, ids, remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }, ::toast) { gridIds = null; selected = emptySet() } }
  if (priceOpen) {
    BulkPriceDialog(d, selected, onDismiss = { priceOpen = false }) { r ->
      when (r) {
        is WarehouseEngine.Result.Failed -> toast(r.message)
        is WarehouseEngine.Result.Ok -> {
          scope.launch { store.save(d, r.data) }
          toast(ir.vil3ntec.tohid.core.i18n.StrProductPages.priceChanged(plain(selected.size)))
          selected = emptySet(); priceOpen = false
        }
      }
    }
  }
  confirm?.let { kind ->
    val hidden = Selection.hiddenCount(selected, rows.map { it.id })
    val warnings = if (hidden > 0) listOf(ir.vil3ntec.tohid.core.i18n.StrProductPages.hiddenSelected(plain(hidden))) else emptyList()
    when (kind) {
      BulkKind.ACTIVATE, BulkKind.DEACTIVATE -> {
        val on = kind == BulkKind.ACTIVATE
        val preview = remember(d, selected, on) { ProductListModel.previewActive(d.products, selected, on) }
        BulkConfirmDialog(
          title = if (on) ir.vil3ntec.tohid.core.i18n.StrProductPages.activateN(plain(preview.size)) else ir.vil3ntec.tohid.core.i18n.StrProductPages.deactivateN(plain(preview.size)),
          confirmLabel = if (on) ir.vil3ntec.tohid.core.i18n.StrProductPages.activateIt else ir.vil3ntec.tohid.core.i18n.StrProductPages.deactivateIt,
          rows = preview,
          note = if (on) ir.vil3ntec.tohid.core.i18n.StrProductPages.activeNote
          else ir.vil3ntec.tohid.core.i18n.StrProductPages.inactiveNote,
          warnings = warnings,
          onDismiss = { confirm = null },
          onConfirm = { _ ->
            val ids = preview.filter { it.changes }.map { it.id }.toSet()
            val r = WarehouseEngine.bulkUpdate(d, ids, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId) { it.copy(active = on) }
            val error = (r as? WarehouseEngine.Result.Failed)?.message
            if (r is WarehouseEngine.Result.Ok) { scope.launch { store.save(d, r.data) }; selected = emptySet() }
            results = (if (on) ir.vil3ntec.tohid.core.i18n.StrProductPages.activateResult else ir.vil3ntec.tohid.core.i18n.StrProductPages.deactivateResult) to ProductListModel.resultActive(preview, on, error)
            confirm = null
          },
        )
      }
      BulkKind.DELETE -> {
        val preview = remember(d, selected) { ProductListModel.previewDelete(d.products, selected) { ShopStore.soldQty(d, it) } }
        BulkConfirmDialog(
          title = ir.vil3ntec.tohid.core.i18n.StrProductPages.deleteN(plain(preview.size)),
          confirmLabel = ir.vil3ntec.tohid.core.i18n.StrProductPages.deleteIt,
          rows = preview,
          destructive = true,
          note = ir.vil3ntec.tohid.core.i18n.StrProductPages.deleteNote,
          warnings = warnings,
          onDismiss = { confirm = null },
          onConfirm = { reason ->
            val ids = preview.map { it.id }.toSet()
            val now = System.currentTimeMillis()
            val r = WarehouseEngine.bulkDelete(d, ids, ir.vil3ntec.tohid.todayIso(), now, ::newId)
            val error = (r as? WarehouseEngine.Result.Failed)?.message
            if (r is WarehouseEngine.Result.Ok) {
              val next = ProductListModel.withDeleteReason(r.data, ids, now, reason)
              scope.launch { store.save(d, next) }
              selected = emptySet()
              if (openId in ids) openId = null
            }
            results = ir.vil3ntec.tohid.core.i18n.StrProductPages.deleteResult to ProductListModel.resultDelete(preview, error)
            confirm = null
          },
        )
      }
    }
  }
  /* ─────────── حدهای موجودی (INV-12): ورودی ⇐ پیش‌نمایشِ هر ردیف ⇐ نتیجه ─────────── */
  if (levelsInput) {
    LevelsInputDialog(selected.size, onDismiss = { levelsInput = false }) { patch -> levelsInput = false; levelsPatch = patch }
  }
  levelsPatch?.let { patch ->
    val plan = remember(d, selected, patch) { WarehouseEngine.previewLevels(d, selected, patch) }
    val names = remember(d) { d.products.associate { it.id to it.name } }
    val hidden = Selection.hiddenCount(selected, rows.map { it.id })
    BulkConfirmDialog(
      title = ir.vil3ntec.tohid.core.i18n.StrProductPages.levelsN(plain(plan.size)),
      confirmLabel = ir.vil3ntec.tohid.core.i18n.StrProductPages.saveIt,
      rows = plan.map { BulkPreviewRow(it.id, names[it.id].orEmpty(), changes = it.changes, note = levelsNote(it)) },
      note = ir.vil3ntec.tohid.core.i18n.StrProductPages.levelsNote,
      warnings = if (hidden > 0) listOf(ir.vil3ntec.tohid.core.i18n.StrProductPages.hiddenSelected(plain(hidden))) else emptyList(),
      onDismiss = { levelsPatch = null },
      onConfirm = { _ ->
        val r = WarehouseEngine.bulkLevels(d, selected, patch, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)
        val error = (r as? WarehouseEngine.Result.Failed)?.message
        if (r is WarehouseEngine.Result.Ok) { scope.launch { store.save(d, r.data) }; selected = emptySet() }
        results = ir.vil3ntec.tohid.core.i18n.StrProductPages.levelsResult to plan.map { p ->
          val label = names[p.id].orEmpty()
          when {
            !p.ok -> BulkRowResult(p.id, label, ok = false, note = p.problems.joinToString("، ") { it.fa })
            error != null && p.changes -> BulkRowResult(p.id, label, ok = false, note = error)
            p.changes -> BulkRowResult(p.id, label, ok = true, note = ir.vil3ntec.tohid.core.i18n.StrProductPages.savedNote(levelsNote(p)))
            else -> BulkRowResult(p.id, label, ok = true, note = ir.vil3ntec.tohid.core.i18n.StrProductPages.unchangedAlready)
          }
        }
        levelsPatch = null
      },
    )
  }
  results?.let { (title, list) -> BulkResultDialog(title, list) { results = null } }
}

/**
 *  ورودیِ ویرایشِ گروهیِ حدها (INV-12): کادرِ خالی ⇒ «بی‌تغییر»؛ تیکِ «پاک» آن حد را برمی‌دارد؛
 *  حد کم بودنِ ۰ همان «بی هشدار» ِ امروز است. عددِ نادرست دکمه را خاموش نگه می‌دارد.
 */
@Composable
private fun LevelsInputDialog(count: Int, onDismiss: () -> Unit, onNext: (StockLevelRules.Patch) -> Unit) {
  var min by remember { mutableStateOf("") }
  var reorder by remember { mutableStateOf("") }
  var max by remember { mutableStateOf("") }
  var clearMin by remember { mutableStateOf(false) }
  var clearReorder by remember { mutableStateOf(false) }
  var clearMax by remember { mutableStateOf(false) }
  val patch = StockLevelRules.Patch(
    min = levelEdit(min, clearMin, zeroClears = true) ?: StockLevelRules.Edit.Keep,
    reorder = levelEdit(reorder, clearReorder) ?: StockLevelRules.Edit.Keep,
    max = levelEdit(max, clearMax) ?: StockLevelRules.Edit.Keep,
  )
  val bad = listOf(min to clearMin, reorder to clearReorder, max to clearMax).any { (t, c) -> !c && t.isNotBlank() && levelEdit(t, false) == null }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(ir.vil3ntec.tohid.core.i18n.StrProductPages.levelsFor(plain(count)), color = Shop.colors.text) },
    text = {
      Column(Modifier.widthIn(min = 340.dp, max = 480.dp)) {
        Text(ir.vil3ntec.tohid.core.i18n.StrProductPages.emptyUntouched, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        @Composable fun Line(v: String, set: (String) -> Unit, label: String, clear: Boolean, setClear: (Boolean) -> Unit) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = v, onValueChange = { set(ir.vil3ntec.tohid.numericInput(it)) }, enabled = !clear,
              label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Checkbox(checked = clear, onCheckedChange = setClear)
            Text(ir.vil3ntec.tohid.core.i18n.StrSale.clearIt, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
          }
          Spacer(Modifier.height(6.dp))
        }
        Line(min, { min = it }, ir.vil3ntec.tohid.core.i18n.StrStock.minStock, clearMin) { clearMin = it }
        Line(reorder, { reorder = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.reorderPoint, clearReorder) { clearReorder = it }
        Line(max, { max = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.maxStock, clearMax) { clearMax = it }
        if (bad) Text(ir.vil3ntec.tohid.core.i18n.StrSale.badNumberShort, style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger)
      }
    },
    confirmButton = { Button(onClick = { onNext(patch) }, enabled = !bad && !patch.isNoop) { Text(ir.vil3ntec.tohid.core.i18n.StrDevicePages.preview) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(ir.vil3ntec.tohid.core.i18n.Str.cancel) } },
  )
}

/** کارِ گروهیِ در انتظارِ تأیید */
private enum class BulkKind { ACTIVATE, DEACTIVATE, DELETE }

/** قلاب‌های عکس‌گیریِ CI (`desktop/DevShot.kt`: prodview / prodsel) — در برنامه کسی چیزی نمی‌گذارد */
object DeskProductsShot {
  val view = mutableStateOf<String?>(null)
  val select = mutableStateOf(-1)
}

/** خانهٔ کوچکِ ردیفِ افزودن: Enter ⇐ ثبت */
@Composable
private fun QuickField(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  modifier: Modifier,
  onEnter: () -> Unit,
  numeric: Boolean = false,
) {
  //  کامپیوتر (`ui/desk`): کادرِ ۳۶dp با راهنمای درون و حلقهٔ تمرکز؛ Enter ⇐ ثبت
  DeskTextField(
    value = value,
    onValueChange = { onChange(if (numeric) ir.vil3ntec.tohid.numericInput(it) else it) },
    modifier = modifier,
    placeholder = label,
    description = label,
    onKey = { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onEnter(); true } else false },
  )
}

@Composable
private fun QuickAddRow(store: ShopStore, d: ShopData, toast: (String) -> Unit, incoming: MutableState<String?>, focus: FocusRequester? = null) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var barcode by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("") }
  var unit by remember { mutableStateOf("دانه") } // داده: واحدِ پیش‌فرضِ کالای تازه (داده)
  var buy by remember { mutableStateOf("") }
  var sell by remember { mutableStateOf("") }
  var count by remember { mutableStateOf("") }
  //  «+ افزودن کالا» / Ctrl+N از بیرون همین خانهٔ نام را فوکوس می‌کند
  val ownFocus = remember { FocusRequester() }
  val nameFocus = focus ?: ownFocus
  LaunchedEffect(incoming.value) {
    incoming.value?.let { barcode = it; incoming.value = null; runCatching { nameFocus.requestFocus() } }
  }
  //  PRD-19 (پیش‌فرض خاموش): نامِ مشابه ⇒ هشدار و Enterِ دوم برای ثبت؛ نامِ یکسان را موتور رد می‌کند
  val context = LocalContext.current
  val tools = remember { ShopPolicy.productTools(context) }
  val similarIndex = remember(d.products, tools) { if (tools) SimilarNames.Index(d.products.map { it.name }) else null }
  var warnedFor by remember { mutableStateOf("") }
  //  PRD-12: «قیمتِ عمده» فقط با «لیستِ قیمت در صندوق» (خاموش ⇒ همان ردیف و `wholesalePrice = null`، همان فرمِ تازهٔ نسخهٔ ۳)
  val wholesaleOn = remember { ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context) }
  var wholesale by remember { mutableStateOf("") }

  fun save() {
    val similar = similarIndex?.find(name).orEmpty()
    if (similar.isNotEmpty() && warnedFor != name.trim()) {
      warnedFor = name.trim()
      toast(ir.vil3ntec.tohid.core.i18n.StrProductPages.similarName + similar.joinToString("، ") { "«${it.name}»" } + ir.vil3ntec.tohid.core.i18n.StrProductPages.enterAgain)
      return
    }
    val draft = WarehouseEngine.ProductDraft(
      name = name, category = category.trim(), unit = unit.trim(),
      purchasePrice = buy.toDoubleOrNull() ?: 0.0, salePrice = sell.toDoubleOrNull() ?: 0.0,
      barcode = if (tools) "" else barcode.trim(),
      barcodes = if (tools) ProductBarcodes.parse(barcode) else null,
      wholesalePrice = if (wholesaleOn) deskWholesale(wholesale) else null,
    )
    when (val r = WarehouseEngine.addProduct(d, draft, System.currentTimeMillis(), ::newId)) {
      is WarehouseEngine.Result.Failed -> toast(r.message)
      is WarehouseEngine.Result.Ok -> {
        var next = r.data
        val n = count.toDoubleOrNull() ?: 0.0
        if (n > 0) {
          val e = WarehouseEngine.addEntry(
            next,
            WarehouseEngine.EntryDraft(productId = r.id, units = n, unit = unit.trim(), price = buy.toDoubleOrNull() ?: 0.0),
            ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId,
          )
          if (e is WarehouseEngine.Result.Ok) next = e.data else if (e is WarehouseEngine.Result.Failed) toast(e.message)
        }
        scope.launch { store.save(d, next) }
        toast(ir.vil3ntec.tohid.core.i18n.StrProductPages.nameRecorded(name.trim()))
        //  دسته و واحد برای کالای بعدی می‌مانند — معمولاً چند کالای یک دسته پشتِ هم ثبت می‌شوند
        name = ""; barcode = ""; buy = ""; sell = ""; count = ""; wholesale = ""
        runCatching { nameFocus.requestFocus() }
      }
    }
  }

  //  ردیفِ «کالای تازه»: کارتِ کامپیوتر با کادرهای ۳۶dp؛ Tab بینِ خانه‌ها، Enter ثبت
  Box(Modifier.fillMaxWidth().deskCard(Desk.colors)) {
    Row(Modifier.padding(DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
      QuickField(name, { name = it }, ir.vil3ntec.tohid.core.i18n.StrHome.newItem, Modifier.weight(1f).focusRequester(nameFocus), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(barcode, { barcode = it }, if (tools) ir.vil3ntec.tohid.core.i18n.StrProductPages.barcodesComma else ir.vil3ntec.tohid.core.i18n.StrGrid.barcode, Modifier.width(150.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(category, { category = it }, ir.vil3ntec.tohid.core.i18n.StrGrid.category, Modifier.width(120.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(unit, { unit = it }, ir.vil3ntec.tohid.core.i18n.StrGrid.unit, Modifier.width(80.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(buy, { buy = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.buyShort, Modifier.width(95.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      QuickField(sell, { sell = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.sellShort, Modifier.width(95.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      if (wholesaleOn) { QuickField(wholesale, { wholesale = it }, ir.vil3ntec.tohid.core.i18n.StrPriceCheck.wholesale, Modifier.width(95.dp), ::save, numeric = true); Spacer(Modifier.width(6.dp)) }
      QuickField(count, { count = it }, ir.vil3ntec.tohid.core.i18n.StrSale.stock, Modifier.width(90.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.saveKey, ::save, kind = DeskButtonKind.PRIMARY, enabled = name.isNotBlank())
    }
  }
}

@Composable
private fun EditPanel(
  store: ShopStore,
  d: ShopData,
  p: Product,
  stock: Double,
  toast: (String) -> Unit,
  onClose: () -> Unit,
  onWarehouse: () -> Unit,
  onDetail: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val focus = LocalFocusManager.current
  //  PRD-19 و PRD-04 — پیش‌فرض خاموش (تنظیمِ همین دستگاه)
  val context = LocalContext.current
  val tools = remember { ShopPolicy.productTools(context) }
  var name by remember { mutableStateOf(p.name) }
  var barcode by remember { mutableStateOf(if (tools) p.barcodes.joinToString("، ") else p.barcodes.firstOrNull().orEmpty()) }
  val similarIndex = remember(d.products, tools) { if (tools) SimilarNames.Index(d.products.map { it.name }) else null }
  val similar = remember(name, similarIndex) { similarIndex?.find(name, except = d.products.indexOfFirst { it.id == p.id }).orEmpty() }
  //  PRD-14: تغییرِ قیمت با نامِ کاربر و دلیل در سابقه (همان پرچم)
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  var priceReason by remember { mutableStateOf("") }
  //  PRD-06 (همان پرچم): برند و برچسب‌ها
  var brand by remember { mutableStateOf(p.brand.orEmpty()) }
  var tags by remember { mutableStateOf(p.tags.orEmpty().joinToString("، ")) }
  //  PRD-04 (همان پرچم): بسته‌ها با بارکدِ هر واحد
  var packs by remember { mutableStateOf(p.packs.orEmpty().map { ir.vil3ntec.tohid.core.product.PackBarcodes.draftOf(ShopStore.coreOf(it)) }) }
  //  PRD-07 (همان پرچم): واحدهای خریدِ چندگانه؛ نوشتن فقط با نقشی که کالا می‌نویسد
  var purchaseUnits by remember { mutableStateOf(purchaseUnitDrafts(p)) }
  val canEditUnits = remember { ir.vil3ntec.tohid.data.ShopRole.canWrite(context, "products") }
  var cycle by remember { mutableStateOf(ir.vil3ntec.tohid.data.ProductCycle.text(p.consumptionDays)) }   //  MKT-06 چرخهٔ مصرف (روز، اختیاری)
  var category by remember { mutableStateOf(p.category) }
  var unit by remember { mutableStateOf(p.unit) }
  var buy by remember { mutableStateOf(qty(p.purchasePrice)) }
  var sell by remember { mutableStateOf(qty(p.salePrice)) }
  //  PRD-12 (فقط با «لیستِ قیمت در صندوق»؛ خاموش ⇒ `wholesalePrice = null` = دست نمی‌خورد): تغییرش در سابقه با کاربر و دلیل (PRD-14)
  val wholesaleOn = remember { ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context) }
  var wholesale by remember { mutableStateOf(deskWholesaleText(p.wholesalePrice)) }
  var min by remember { mutableStateOf(qty(p.minStock)) }
  var active by remember { mutableStateOf(p.active) }
  //  PRD-02/PRD-26: خاموش ⇒ همان پنلِ پیشین و `info = null` (فیلدها دست نمی‌خورند)
  val infoOn = ir.vil3ntec.tohid.ui.components.product.productInfoOn()
  var info by remember {
    mutableStateOf(ir.vil3ntec.tohid.ui.components.product.ProductInfoForm.of(ir.vil3ntec.tohid.data.ProductInfoMap.of(p)))
  }
  //  INV-12 — فقط با تنظیمِ «نقطهٔ سفارش و بیشینه» (پیش‌فرض خاموش)
  val levelsOn = remember { ir.vil3ntec.tohid.data.ShopPolicy.stockLevels(context) }
  var reorder by remember { mutableStateOf(p.reorderPoint?.let { qty(it) }.orEmpty()) }
  var maxStock by remember { mutableStateOf(p.maxStock?.let { qty(it) }.orEmpty()) }

  fun save() {
    if (ir.vil3ntec.tohid.data.ProductCycle.bad(cycle)) { toast(ir.vil3ntec.tohid.core.i18n.StrProducts.cycleBad); return }
    val draft = WarehouseEngine.ProductDraft(
      name = name, category = category.trim(), unit = unit.trim(),
      purchasePrice = buy.toDoubleOrNull() ?: 0.0, salePrice = sell.toDoubleOrNull() ?: 0.0,
      minStock = min.toDoubleOrNull() ?: 0.0, barcode = if (tools) "" else barcode.trim(), active = active,
      barcodes = if (tools) ProductBarcodes.parse(barcode) else null,
      changedBy = if (tools) me else null,
      reason = if (tools) priceReason else null,
      info = if (infoOn) info.toInfo(ir.vil3ntec.tohid.data.ProductInfoMap.refs(d), p.id) else null,
      brand = if (tools) brand else null,
      tags = if (tools) ProductLabels.tags(tags) else null,
      packs = if (tools) packs else null,
      purchaseUnits = if (tools && canEditUnits) purchaseUnits else null,
      editLevels = levelsOn,
      reorderPoint = if (levelsOn) levelNumber(reorder) else null,
      maxStock = if (levelsOn) levelNumber(maxStock) else null,
      wholesalePrice = if (wholesaleOn) deskWholesale(wholesale) else null,
    )
    when (val r = WarehouseEngine.editProduct(d, p.id, draft, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)) {
      is WarehouseEngine.Result.Failed -> toast(r.message)
      is WarehouseEngine.Result.Ok -> { scope.launch { store.save(d, ir.vil3ntec.tohid.data.ProductCycle.apply(r.data, p.id, cycle)) }; toast(ir.vil3ntec.tohid.core.i18n.StrProductPages.nameSaved(name.trim())) }
    }
  }

  DeskSidePanel(
    title = ir.vil3ntec.tohid.core.i18n.StrProductPages.editItem,
    closeLabel = ir.vil3ntec.tohid.core.i18n.StrProductPages.closeEsc,
    onClose = onClose,
    subtitle = ir.vil3ntec.tohid.core.i18n.StrProductPages.stockIs(qty(stock), p.unit),
    modifier = Modifier
      .width(360.dp).fillMaxHeight()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.key == Key.Escape -> { onClose(); true }
          e.isPrimaryModifier && e.key == Key.S -> { save(); true }
          e.key == Key.Enter || e.key == Key.NumPadEnter -> { save(); true }
          else -> false
        }
      },
  ) {
    Column(Modifier.padding(DeskSpace.card).verticalScroll(rememberScrollState())) {
      @Composable fun F(v: String, set: (String) -> Unit, label: String, numeric: Boolean = false) {
        DeskLabeledField(label, v, { set(if (numeric) ir.vil3ntec.tohid.numericInput(it) else it) }, Modifier.fillMaxWidth(), textAlign = if (numeric) TextAlign.End else TextAlign.Start)
      }
      F(name, { name = it }, ir.vil3ntec.tohid.core.i18n.StrGrid.name)
      if (similar.isNotEmpty()) {
        Text(
          ir.vil3ntec.tohid.core.i18n.StrProductPages.similarName + similar.joinToString("، ") { "«${it.name}»" },
          Modifier.padding(bottom = DeskSpace.x3), style = DeskType.caption, color = Desk.colors.warning,
        )
      }
      F(barcode, { barcode = it }, if (tools) ir.vil3ntec.tohid.core.i18n.StrProductPages.barcodesSep else ir.vil3ntec.tohid.core.i18n.StrGrid.barcode)
      F(category, { category = it }, ir.vil3ntec.tohid.core.i18n.StrGrid.category)
      if (tools) {
        F(brand, { brand = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.brand)
        F(tags, { tags = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.tagsSep)
      }
      F(unit, { unit = it }, ir.vil3ntec.tohid.core.i18n.StrGrid.unit)
      if (tools) {
        PackEditor(packs, unit) { packs = it }
        Spacer(Modifier.height(6.dp))
        PurchaseUnitEditor(purchaseUnits, unit, d.suppliers, canEditUnits) { purchaseUnits = it }
        Spacer(Modifier.height(6.dp))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
        Box(Modifier.weight(1f)) { F(buy, { buy = it }, ir.vil3ntec.tohid.core.i18n.StrGrid.buy, true) }
        Box(Modifier.weight(1f)) { F(sell, { sell = it }, ir.vil3ntec.tohid.core.i18n.StrGrid.sell, true) }
      }
      if (wholesaleOn) F(wholesale, { wholesale = it }, ir.vil3ntec.tohid.core.i18n.StrPriceCheck.wholesale, true)
      if (tools && (buy != qty(p.purchasePrice) || sell != qty(p.salePrice) || wholesale != deskWholesaleText(p.wholesalePrice))) {
        F(priceReason, { priceReason = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.priceReason)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
        Box(Modifier.weight(1f)) { F(min, { min = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.alertLevel, true) }
        Box(Modifier.weight(1f)) { F(cycle, { cycle = it.filter(Char::isDigit) }, ir.vil3ntec.tohid.core.i18n.StrProducts.cycle, true) }
      }
      if (levelsOn) {
        Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
          Box(Modifier.weight(1f)) { F(reorder, { reorder = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.reorderPoint, true) }
          Box(Modifier.weight(1f)) { F(maxStock, { maxStock = it }, ir.vil3ntec.tohid.core.i18n.StrProductPages.maxStock, true) }
        }
      }
      DeskToggleRow(active, ir.vil3ntec.tohid.core.i18n.StrProductPages.activeSeen, { active = it })
      if (infoOn) {
        Spacer(Modifier.height(8.dp))
        ir.vil3ntec.tohid.ui.components.product.ProductInfoFields(info, d, p.id) { info = it }
      }
      Spacer(Modifier.height(DeskSpace.x3))
      DeskButton(ir.vil3ntec.tohid.core.i18n.StrProductPages.saveKeys, { save(); focus.clearFocus() }, Modifier.fillMaxWidth(), kind = DeskButtonKind.PRIMARY)
      Spacer(Modifier.height(DeskSpace.x2))
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
        DeskButton(ir.vil3ntec.tohid.core.i18n.StrProductPages.stockIn, onWarehouse, Modifier.weight(1f))
        DeskButton(ir.vil3ntec.tohid.core.i18n.StrProductPages.detailsPhoto, onDetail, Modifier.weight(1f))
      }
    }
  }
}
