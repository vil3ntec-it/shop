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
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.ui.components.pos.ProductThumb
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch

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
      if (id != null) { openId = id; search = "" } else { incoming.value = code; toast("بارکدِ تازه — نام و قیمت را بنویسید و Enter بزنید") }
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

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.isPrimaryModifier && e.key == Key.F -> { runCatching { searchFocus.requestFocus() }; true }
          //  Esc: اول پنلِ ویرایش خودش می‌بندد؛ بعد انتخاب پاک می‌شود
          e.key == Key.Escape && openId == null && selected.isNotEmpty() -> { selected = emptySet(); true }
          else -> false
        }
      }
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    /* ─────────── ۱. عنوان، توضیح و کارها ─────────── */
    Row(verticalAlignment = Alignment.CenterVertically) {
      //  نسخهٔ ۲: عنوانِ «محصولات» فقط در سربرگ است (نقدِ «همه‌چیز دو جا»)؛ این‌جا یک جملهٔ توضیح
      Text("موجودی، قیمت و وضعیتِ کالاهای دکان — جست‌وجو، فیلتر و کارِ گروهی", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
      OutlinedButton(onClick = { onOpenWarehouse(null) }) { Text("ورودِ انبار") }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = {
        val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", d, "", "", ir.vil3ntec.tohid.todayIso())
        ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet, sheet.name)
      }) { Text("چاپ") }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = {
        val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", d, "", "", ir.vil3ntec.tohid.todayIso())
        ir.vil3ntec.tohid.ui.Platform.saveFile("${sheet.name}.xlsx", ir.vil3ntec.tohid.data.Xlsx.of(sheet))?.let { toast("ذخیره شد: $it") }
      }) {
        Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("خروجیِ اکسل")
      }
      Spacer(Modifier.width(8.dp))
      Button(onClick = focusAdd) {
        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("افزودن کالا  Ctrl+N")
      }
    }
    Spacer(Modifier.height(12.dp))

    /* ─────────── ۲. کارت‌های آماری — هر کدام زبانهٔ خودش را باز می‌کند ─────────── */
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatCard(
        "همهٔ کالاها", plain(kpis.total), "${plain(kpis.periodDays)} روزِ گذشته", Modifier.weight(1f),
        icon = Icons.Filled.Inventory2,
        delta = if (kpis.added > 0) StatDelta("${plain(kpis.added)} کالای تازه", Trend.UP) else StatDelta("کالای تازه‌ای نیامده", Trend.FLAT),
        selected = tab == ProductListModel.Tab.ALL,
        onClick = { showTab(ProductListModel.Tab.ALL) },
      )
      StatCard(
        "فعال", plain(kpis.active), "اکنون", Modifier.weight(1f), icon = Icons.Filled.CheckCircle,
        selected = tab == ProductListModel.Tab.ACTIVE, onClick = { showTab(ProductListModel.Tab.ACTIVE) },
      )
      StatCard(
        "کم‌موجود", plain(kpis.low), "اکنون — زیرِ حدِ هشدار", Modifier.weight(1f), icon = Icons.Filled.WarningAmber,
        selected = tab == ProductListModel.Tab.LOW, onClick = { showTab(ProductListModel.Tab.LOW) },
      )
      StatCard(
        "ناموجود", plain(kpis.out), "اکنون — کالای فعال", Modifier.weight(1f), icon = Icons.Filled.RemoveShoppingCart,
        selected = tab == ProductListModel.Tab.OUT, onClick = { showTab(ProductListModel.Tab.OUT) },
      )
    }
    Spacer(Modifier.height(12.dp))

    /* ─────────── ۳. نوارِ ابزار ─────────── */
    TableToolbar(
      query = search,
      onQuery = { search = it },
      placeholder = "جست‌وجو در نام، دسته یا بارکد (Ctrl+F)",
      viewMode = view,
      searchFocus = searchFocus,
      filter = {
        FilterMenu(
          label = "دسته",
          options = categories.map { it to it.ifEmpty { "بی‌دسته" } },
          selected = category,
          onSelect = { category = it },
          allLabel = "همهٔ دسته‌ها",
        )
      },
      tabs = {
        SegmentedTabs(
          ProductListModel.Tab.entries.map { SegmentTab(it, it.label, counts[it]) },
          selected = tab,
          onSelect = { tab = it },
        )
      },
    )
    Spacer(Modifier.height(10.dp))

    Row(Modifier.weight(1f)) {
      Column(Modifier.weight(1f).fillMaxHeight()) {
        QuickAddRow(store, d, ::toast, incoming, addFocus)
        Spacer(Modifier.height(8.dp))

        /* ─────────── ۴. جدول یا کارت‌ها + نوارِ کارِ گروهی ─────────── */
        Box(Modifier.weight(1f).fillMaxWidth()) {
          val toggle: (String) -> Unit = { id -> selected = Selection.toggle(selected, id) }
          when {
            d.products.isEmpty() -> TohidEmptyState(
              title = "هنوز کالایی نیست",
              description = "در ردیفِ «کالای تازه» نام و قیمت را بنویسید و Enter بزنید، یا بارکد را بخوانید.",
              icon = Icons.Filled.Inventory2,
              actionText = "افزودن کالا",
              onAction = focusAdd,
            )
            rows.isEmpty() -> TohidEmptyState(
              title = "چیزی پیدا نشد",
              description = "کالایی با این جست‌وجو، دسته یا زبانه نیست.",
              icon = Icons.Filled.Search,
              actionText = "پاک‌کردنِ جست‌وجو و فیلتر",
              onAction = { showTab(ProductListModel.Tab.ALL) },
            )
            view.mode == ViewMode.TABLE -> ProductTable(
              rows, stockOf, selected, openId, sortBy, asc, ::sortOn,
              onToggle = toggle,
              onToggleAll = { selected = Selection.toggleAll(selected, rows.map { it.id }) },
              onOpen = { openId = it },
              bottomSpace = if (selected.isNotEmpty()) 72.dp else 0.dp,
            )
            else -> ProductGrid(
              rows, stockOf, selected, openId,
              onToggle = toggle,
              onOpen = { openId = it },
              bottomSpace = if (selected.isNotEmpty()) 72.dp else 0.dp,
            )
          }
          if (selected.isNotEmpty()) {
            BulkActionBar(
              count = selected.size,
              actions = buildList {
                add(BulkAction("خروجی", Icons.Filled.FileDownload) {
                  val part = d.copy(products = d.products.filter { it.id in selected })
                  val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", part, "", "", ir.vil3ntec.tohid.todayIso())
                  ir.vil3ntec.tohid.ui.Platform.saveFile("${sheet.name}.xlsx", ir.vil3ntec.tohid.data.Xlsx.of(sheet))
                    ?.let { toast("${plain(part.products.size)} کالا ذخیره شد: $it") }
                })
                //  PRD-21 (پیش‌فرض خاموش): قیمتِ گروهی با پیش‌نمایش، هشدار، تأیید و دلیل
                if (productTools) add(BulkAction("قیمت", Icons.Filled.Sell) { priceOpen = true })
                add(BulkAction("فعال", Icons.Filled.CheckCircle) { confirm = BulkKind.ACTIVATE })
                add(BulkAction("غیرفعال", Icons.Filled.Block) { confirm = BulkKind.DEACTIVATE })
                if (levelsOn) add(BulkAction("حدِ موجودی", Icons.Filled.Tune) { levelsInput = true })
                if (canDelete) add(BulkAction("حذف", Icons.Filled.Delete, destructive = true) { confirm = BulkKind.DELETE })
              },
              onClose = { selected = emptySet() },
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
          }
        }
        Text(
          "ردیفِ «کالای تازه»: Tab خانهٔ بعد · Enter ثبت · کلیک روی کالا ⇐ ویرایش کنارِ فهرست · Ctrl+F جست‌وجو · Esc لغوِ انتخاب",
          Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
        )
      }

      /* ─────────── پنلِ ویرایش ─────────── */
      val open = openId?.let { id -> d.products.find { it.id == id } }
      if (open != null) {
        Spacer(Modifier.width(12.dp))
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
  if (priceOpen) {
    BulkPriceDialog(d, selected, onDismiss = { priceOpen = false }) { r ->
      when (r) {
        is WarehouseEngine.Result.Failed -> toast(r.message)
        is WarehouseEngine.Result.Ok -> {
          scope.launch { store.save(r.data) }
          toast("قیمتِ ${plain(selected.size)} کالا عوض شد")
          selected = emptySet(); priceOpen = false
        }
      }
    }
  }
  confirm?.let { kind ->
    val hidden = Selection.hiddenCount(selected, rows.map { it.id })
    val warnings = if (hidden > 0) listOf("${plain(hidden)} مورد از انتخاب‌ها در فیلترِ فعلی دیده نمی‌شوند ولی در این کار هستند.") else emptyList()
    when (kind) {
      BulkKind.ACTIVATE, BulkKind.DEACTIVATE -> {
        val on = kind == BulkKind.ACTIVATE
        val preview = remember(d, selected, on) { ProductListModel.previewActive(d.products, selected, on) }
        BulkConfirmDialog(
          title = if (on) "فعال‌کردنِ ${plain(preview.size)} کالا" else "غیرفعال‌کردنِ ${plain(preview.size)} کالا",
          confirmLabel = if (on) "فعال کن" else "غیرفعال کن",
          rows = preview,
          note = if (on) "کالای فعال در فروش و جست‌وجوی فروش دیده می‌شود. برگشت‌پذیر است."
          else "کالای غیرفعال فروخته نمی‌شود ولی سابقه و موجودی‌اش می‌ماند. برگشت‌پذیر است.",
          warnings = warnings,
          onDismiss = { confirm = null },
          onConfirm = { _ ->
            val ids = preview.filter { it.changes }.map { it.id }.toSet()
            val r = WarehouseEngine.bulkUpdate(d, ids, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId) { it.copy(active = on) }
            val error = (r as? WarehouseEngine.Result.Failed)?.message
            if (r is WarehouseEngine.Result.Ok) { scope.launch { store.save(r.data) }; selected = emptySet() }
            results = (if (on) "نتیجهٔ فعال‌کردن" else "نتیجهٔ غیرفعال‌کردن") to ProductListModel.resultActive(preview, on, error)
            confirm = null
          },
        )
      }
      BulkKind.DELETE -> {
        val preview = remember(d, selected) { ProductListModel.previewDelete(d.products, selected) { ShopStore.soldQty(d, it) } }
        BulkConfirmDialog(
          title = "حذفِ ${plain(preview.size)} کالا",
          confirmLabel = "حذف کن",
          rows = preview,
          destructive = true,
          note = "کالاها و سوابقِ ورودِ انبارشان حذف می‌شوند و برگشت ندارد. فاکتورهای قبلی می‌مانند. دلیل در سابقهٔ عملیات ثبت می‌شود.",
          warnings = warnings,
          onDismiss = { confirm = null },
          onConfirm = { reason ->
            val ids = preview.map { it.id }.toSet()
            val now = System.currentTimeMillis()
            val r = WarehouseEngine.bulkDelete(d, ids, ir.vil3ntec.tohid.todayIso(), now, ::newId)
            val error = (r as? WarehouseEngine.Result.Failed)?.message
            if (r is WarehouseEngine.Result.Ok) {
              val next = ProductListModel.withDeleteReason(r.data, ids, now, reason)
              scope.launch { store.save(next) }
              selected = emptySet()
              if (openId in ids) openId = null
            }
            results = "نتیجهٔ حذف" to ProductListModel.resultDelete(preview, error)
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
      title = "حدِ موجودیِ ${plain(plan.size)} کالا",
      confirmLabel = "ذخیره کن",
      rows = plan.map { BulkPreviewRow(it.id, names[it.id].orEmpty(), changes = it.changes, note = levelsNote(it)) },
      note = "ترتیبِ درست: حد کم بودن ≤ نقطهٔ سفارش < بیشینه. کالایی که با این عددها جور نیست عوض نمی‌شود. برگشت‌پذیر است.",
      warnings = if (hidden > 0) listOf("${plain(hidden)} مورد از انتخاب‌ها در فیلترِ فعلی دیده نمی‌شوند ولی در این کار هستند.") else emptyList(),
      onDismiss = { levelsPatch = null },
      onConfirm = { _ ->
        val r = WarehouseEngine.bulkLevels(d, selected, patch, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)
        val error = (r as? WarehouseEngine.Result.Failed)?.message
        if (r is WarehouseEngine.Result.Ok) { scope.launch { store.save(r.data) }; selected = emptySet() }
        results = "نتیجهٔ حدِ موجودی" to plan.map { p ->
          val label = names[p.id].orEmpty()
          when {
            !p.ok -> BulkRowResult(p.id, label, ok = false, note = p.problems.joinToString("، ") { it.fa })
            error != null && p.changes -> BulkRowResult(p.id, label, ok = false, note = error)
            p.changes -> BulkRowResult(p.id, label, ok = true, note = "ذخیره شد — ${levelsNote(p)}")
            else -> BulkRowResult(p.id, label, ok = true, note = "بی‌تغییر — از قبل همین است")
          }
        }
        levelsPatch = null
      },
    )
  }
  results?.let { (title, list) -> BulkResultDialog(title, list) { results = null } }
}

/** «کمینه ۵ ⇐ ۱۰، نقطهٔ سفارش — ⇐ ۲۰» یا ایرادِ ردیف */
private fun levelsNote(p: StockLevelRules.Planned): String {
  if (!p.ok) return p.problems.joinToString("، ") { it.fa }
  fun t(m: Long?) = m?.let { qty(it / 1000.0) } ?: "—"
  val parts = buildList {
    if (p.before.minMilli != p.after.minMilli) add("حد کم بودن ${t(p.before.minMilli)} ⇐ ${t(p.after.minMilli)}")
    if (p.before.reorderMilli != p.after.reorderMilli) add("نقطهٔ سفارش ${t(p.before.reorderMilli)} ⇐ ${t(p.after.reorderMilli)}")
    if (p.before.maxMilli != p.after.maxMilli) add("بیشینه ${t(p.before.maxMilli)} ⇐ ${t(p.after.maxMilli)}")
  }
  return if (parts.isEmpty()) "از قبل همین است" else parts.joinToString("، ")
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
    title = { Text("حدِ موجودی برای ${plain(count)} کالا", color = Shop.colors.text) },
    text = {
      Column(Modifier.widthIn(min = 340.dp, max = 480.dp)) {
        Text("کادرِ خالی دست نمی‌خورد. مقدار به واحدِ خودِ هر کالا است.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        @Composable fun Line(v: String, set: (String) -> Unit, label: String, clear: Boolean, setClear: (Boolean) -> Unit) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = v, onValueChange = { set(ir.vil3ntec.tohid.numericInput(it)) }, enabled = !clear,
              label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Checkbox(checked = clear, onCheckedChange = setClear)
            Text("پاک شود", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
          }
          Spacer(Modifier.height(6.dp))
        }
        Line(min, { min = it }, "حد کم بودن", clearMin) { clearMin = it }
        Line(reorder, { reorder = it }, "نقطهٔ سفارش", clearReorder) { clearReorder = it }
        Line(max, { max = it }, "بیشینهٔ موجودی", clearMax) { clearMax = it }
        if (bad) Text("عدد درست نیست", style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger)
      }
    },
    confirmButton = { Button(onClick = { onNext(patch) }, enabled = !bad && !patch.isNoop) { Text("پیش‌نمایش") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}

/** متنِ کادر ⇒ تغییرِ حد؛ `null` یعنی خالی (بی‌تغییر) یا نادرست — `bad` این دو را جدا می‌کند */
private fun levelEdit(text: String, clear: Boolean, zeroClears: Boolean = false): StockLevelRules.Edit? {
  if (clear) return StockLevelRules.Edit.Clear
  val v = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
  if (zeroClears && v == 0.0) return StockLevelRules.Edit.Clear
  return runCatching { StockLevelRules.Edit.To(ir.vil3ntec.tohid.core.money.Qty.fromLegacy(v).milli) }.getOrNull()
}

/** کارِ گروهیِ در انتظارِ تأیید */
private enum class BulkKind { ACTIVATE, DEACTIVATE, DELETE }

/** قلاب‌های عکس‌گیریِ CI (`desktop/DevShot.kt`: prodview / prodsel) — در برنامه کسی چیزی نمی‌گذارد */
object DeskProductsShot {
  val view = mutableStateOf<String?>(null)
  val select = mutableStateOf(-1)
}

private fun pill(s: ProductListModel.Status): Pair<String, PillTone> = when (s) {
  ProductListModel.Status.ACTIVE -> "فعال" to PillTone.SUCCESS
  ProductListModel.Status.LOW -> "کم‌موجود" to PillTone.WARNING
  ProductListModel.Status.OUT -> "ناموجود" to PillTone.DANGER
  ProductListModel.Status.INACTIVE -> "غیرفعال" to PillTone.NEUTRAL
}

/** نمای جدولی — ستون‌ها مرتب‌شدنی، فهرستِ تنبل (فقط ردیف‌های دیده‌شده کشیده می‌شوند) */
@Composable
private fun ProductTable(
  rows: List<Product>,
  stockOf: (String) -> Double,
  selected: Set<String>,
  openId: String?,
  sortBy: ProductListModel.Sort,
  asc: Boolean,
  onSort: (ProductListModel.Sort) -> Unit,
  onToggle: (String) -> Unit,
  onToggleAll: () -> Unit,
  onOpen: (String) -> Unit,
  bottomSpace: Dp,
) {
  val c = Shop.colors
  val ids = remember(rows) { rows.map { it.id } }
  val photos = PhotoPrefs.show.value
  //  نسخهٔ ۲: یک کارتِ نرم؛ بی خطِ میانِ ردیف‌ها — ردیف‌ها را زمینهٔ یک‌درمیان جدا می‌کند
  Column(Modifier.fillMaxSize().padding(2.dp).softCard(c, Shape.card)) {
    Row(Modifier.fillMaxWidth().background(c.surface2).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      TriStateCheckbox(
        state = when {
          Selection.allSelected(selected, ids) -> ToggleableState.On
          Selection.someSelected(selected, ids) -> ToggleableState.Indeterminate
          else -> ToggleableState.Off
        },
        onClick = onToggleAll,
        modifier = Modifier.semantics { contentDescription = "انتخابِ همهٔ ردیف‌های دیده‌شده" },
      )
      @Composable fun H(title: String, s: ProductListModel.Sort, mod: Modifier, end: Boolean = false) {
        Text(
          title + if (sortBy == s) (if (asc) " ▲" else " ▼") else "",
          mod.clip(Shape.button).clickable(role = Role.Button) { onSort(s) }.padding(horizontal = 4.dp, vertical = 6.dp),
          style = MaterialTheme.typography.labelMedium, color = c.muted, fontWeight = FontWeight.SemiBold,
          textAlign = if (end) TextAlign.End else TextAlign.Start, maxLines = 1,
        )
      }
      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        H("نام", ProductListModel.Sort.NAME, Modifier)
        H("/ دسته", ProductListModel.Sort.CATEGORY, Modifier)
      }
      //  ستونِ نام اولویت دارد (بازبینیِ ۱۴۰۵/۰۷/۲۷: نامِ بلند با «…» بریده می‌شد و بارکد جا داشت)
      H("بارکد · تاریخ", ProductListModel.Sort.CODE, Modifier.width(COL_CODE))
      H("خرید (؋)", ProductListModel.Sort.BUY, Modifier.width(COL_PRICE), end = true)
      H("فروش (؋)", ProductListModel.Sort.SELL, Modifier.width(COL_PRICE), end = true)
      H("موجودی", ProductListModel.Sort.STOCK, Modifier.width(COL_STOCK), end = true)
      Spacer(Modifier.width(12.dp))
      H("وضعیت", ProductListModel.Sort.STATUS, Modifier.width(COL_STATUS))
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = bottomSpace)) {
      itemsIndexed(rows, key = { _, p -> p.id }) { i, p ->
        val stock = stockOf(p.id)
        val status = ProductListModel.status(p, stock)
        val isSel = p.id in selected
        Row(
          Modifier
            .fillMaxWidth()
            .background(
              when {
                p.id == openId -> c.primaryTint
                isSel -> c.primary.copy(alpha = 0.06f)
                i % 2 == 1 -> c.surface2
                else -> c.surface
              }
            )
            .clickable { onOpen(p.id) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = isSel, onCheckedChange = { onToggle(p.id) },
            modifier = Modifier.semantics { contentDescription = "انتخابِ ${p.name}" },
          )
          Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { ProductThumb(p.id, p.name, 36.dp, photos, tintKey = p.category) }
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            //  نام تا دو خط می‌شکند، بریده نمی‌شود
            Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(p.category.ifBlank { "بی‌دسته" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = c.muted)
          }
          Column(Modifier.width(COL_CODE).padding(horizontal = 4.dp)) {
            Text(p.barcodes.firstOrNull().orEmpty().ifEmpty { "—" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.text)
            if (p.createdAt > 0) Text(ir.vil3ntec.tohid.formatMillis(p.createdAt), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = c.muted)
          }
          Cell(money(p.purchasePrice), COL_PRICE, end = true)
          Cell(money(p.salePrice), COL_PRICE, end = true, bold = true)
          Text(
            "${qty(stock)} ${p.unit}",
            Modifier.width(COL_STOCK), textAlign = TextAlign.End, maxLines = 1,
            style = MaterialTheme.typography.bodyMedium, color = c.text,
          )
          Spacer(Modifier.width(12.dp))
          Box(Modifier.width(COL_STATUS)) { val (t, tone) = pill(status); StatusPill(t, tone) }
        }
      }
    }
  }
}

/** پهنای ستون‌های ثابتِ جدولِ کالا — به‌اندازهٔ محتوا، تا باقیِ جا مالِ نام باشد */
private val COL_CODE = 116.dp
private val COL_PRICE = 84.dp
private val COL_STOCK = 100.dp
private val COL_STATUS = 100.dp

/** نمای کارتی — همان داده، همان نشان و همان چک‌باکسِ انتخاب */
@Composable
private fun ProductGrid(
  rows: List<Product>,
  stockOf: (String) -> Double,
  selected: Set<String>,
  openId: String?,
  onToggle: (String) -> Unit,
  onOpen: (String) -> Unit,
  bottomSpace: Dp,
) {
  val c = Shop.colors
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 200.dp),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = bottomSpace + 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    gridItems(rows, key = { it.id }) { p ->
      val stock = stockOf(p.id)
      val isSel = p.id in selected
      val highlighted = isSel || p.id == openId
      Column(
        Modifier
          .softCard(c, Shape.card, fill = if (p.id == openId) c.primaryTint.compositeOver(c.surface) else c.surface)
          .then(if (highlighted) Modifier.border(2.dp, c.focus, Shape.card) else Modifier)
          .clickable { onOpen(p.id) }
          .padding(10.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = isSel, onCheckedChange = { onToggle(p.id) },
            modifier = Modifier.semantics { contentDescription = "انتخابِ ${p.name}" },
          )
          Spacer(Modifier.weight(1f))
          val (t, tone) = pill(ProductListModel.status(p, stock))
          StatusPill(t, tone)
        }
        //  همان دایرهٔ کوچکِ عکس/حرفِ اولِ صندوق کنارِ نام — نه جای عکسِ بزرگِ خاکستری
        Row(verticalAlignment = Alignment.Top) {
          ProductThumb(p.id, p.name, 44.dp, PhotoPrefs.show.value, tintKey = p.category)
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(p.category.ifBlank { "بی‌دسته" }, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = c.muted)
          }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${money(p.salePrice)} ؋", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
          Text("${qty(stock)} ${p.unit}", style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 1)
        }
        Text("خرید: ${money(p.purchasePrice)} ؋", style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
      }
    }
  }
}

@Composable
private fun RowScope.Cell(text: String, w: Dp, end: Boolean = false, bold: Boolean = false) {
  Text(
    text, Modifier.width(w).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
    textAlign = if (end) TextAlign.End else TextAlign.Start,
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    color = Shop.colors.text,
  )
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
  //  نسخهٔ ۲: کادرِ کوچکِ پرشده (۴۰) با برچسبِ درون — نه کادرِ ۵۶ پیکسلیِ خط‌دار
  val c = Shop.colors
  var focused by remember { mutableStateOf(false) }
  Box(
    modifier
      .height(40.dp)
      .clip(Shape.field)
      .background(c.fieldBg)
      .border(1.dp, if (focused) c.focus else c.fieldBorder, Shape.field)
      .padding(horizontal = 10.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1)
    androidx.compose.foundation.text.BasicTextField(
      value = value,
      onValueChange = { onChange(if (numeric) ir.vil3ntec.tohid.numericInput(it) else it) },
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
      cursorBrush = androidx.compose.ui.graphics.SolidColor(c.focus),
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = label }
        .onPreviewKeyEvent { e ->
          if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onEnter(); true } else false
        },
    )
  }
}

@Composable
private fun QuickAddRow(store: ShopStore, d: ShopData, toast: (String) -> Unit, incoming: MutableState<String?>, focus: FocusRequester? = null) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var barcode by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("") }
  var unit by remember { mutableStateOf("دانه") }
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

  fun save() {
    val similar = similarIndex?.find(name).orEmpty()
    if (similar.isNotEmpty() && warnedFor != name.trim()) {
      warnedFor = name.trim()
      toast("نامِ مشابه در دکان: " + similar.joinToString("، ") { "«${it.name}»" } + " — اگر کالای دیگری است، دوباره Enter بزنید")
      return
    }
    val draft = WarehouseEngine.ProductDraft(
      name = name, category = category.trim(), unit = unit.trim(),
      purchasePrice = buy.toDoubleOrNull() ?: 0.0, salePrice = sell.toDoubleOrNull() ?: 0.0,
      barcode = if (tools) "" else barcode.trim(),
      barcodes = if (tools) ProductBarcodes.parse(barcode) else null,
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
        scope.launch { store.save(next) }
        toast("«${name.trim()}» ثبت شد")
        //  دسته و واحد برای کالای بعدی می‌مانند — معمولاً چند کالای یک دسته پشتِ هم ثبت می‌شوند
        name = ""; barcode = ""; buy = ""; sell = ""; count = ""
        runCatching { nameFocus.requestFocus() }
      }
    }
  }

  //  نسخهٔ ۲: ردیفِ «کالای تازه» یک کارتِ نرم با تهِ رنگِ اصلی — نه قابِ بنفشِ خط‌دار
  Surface(
    color = Shop.colors.surface,
    shape = Shape.card,
    modifier = Modifier.fillMaxWidth().padding(2.dp).softCard(Shop.colors, Shape.card),
  ) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
      QuickField(name, { name = it }, "کالای تازه", Modifier.weight(1f).focusRequester(nameFocus), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(barcode, { barcode = it }, if (tools) "بارکدها (،)" else "بارکد", Modifier.width(150.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(category, { category = it }, "دسته", Modifier.width(120.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(unit, { unit = it }, "واحد", Modifier.width(80.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(buy, { buy = it }, "خرید", Modifier.width(95.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      QuickField(sell, { sell = it }, "فروش", Modifier.width(95.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      QuickField(count, { count = it }, "موجودی", Modifier.width(90.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::save, enabled = name.isNotBlank()) { Text("ثبت ↵") }
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
  var packs by remember {
    mutableStateOf(p.packs.orEmpty().map { ir.vil3ntec.tohid.core.product.PackBarcodes.draftOf(ShopStore.coreOf(it)) })
  }
  var category by remember { mutableStateOf(p.category) }
  var unit by remember { mutableStateOf(p.unit) }
  var buy by remember { mutableStateOf(qty(p.purchasePrice)) }
  var sell by remember { mutableStateOf(qty(p.salePrice)) }
  var min by remember { mutableStateOf(qty(p.minStock)) }
  var active by remember { mutableStateOf(p.active) }
  //  PRD-02/PRD-26: خاموش ⇒ همان پنلِ پیشین و `info = null` (فیلدها دست نمی‌خورند)
  val infoOn = ir.vil3ntec.tohid.ui.components.product.productInfoOn()
  var info by remember {
    mutableStateOf(ir.vil3ntec.tohid.ui.components.product.ProductInfoForm.of(ir.vil3ntec.tohid.data.ProductInfoMap.of(p)))
  }
  //  INV-12 — فقط با تنظیمِ «نقطهٔ سفارش و بیشینه» (پیش‌فرض خاموش)
  val context = LocalContext.current
  val levelsOn = remember { ir.vil3ntec.tohid.data.ShopPolicy.stockLevels(context) }
  var reorder by remember { mutableStateOf(p.reorderPoint?.let { qty(it) }.orEmpty()) }
  var maxStock by remember { mutableStateOf(p.maxStock?.let { qty(it) }.orEmpty()) }

  fun save() {
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
      editLevels = levelsOn,
      reorderPoint = if (levelsOn) levelNumber(reorder) else null,
      maxStock = if (levelsOn) levelNumber(maxStock) else null,
    )
    when (val r = WarehouseEngine.editProduct(d, p.id, draft, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)) {
      is WarehouseEngine.Result.Failed -> toast(r.message)
      is WarehouseEngine.Result.Ok -> { scope.launch { store.save(r.data) }; toast("«${name.trim()}» ذخیره شد") }
    }
  }

  Surface(
    color = Shop.colors.surfaceSolid,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .width(340.dp)
      .fillMaxHeight()
      .border(1.dp, Shop.colors.border, RoundedCornerShape(10.dp))
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
    Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ویرایشِ کالا", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        TextButton(onClick = onClose) { Text("بستن Esc") }
      }
      Text("موجودی: ${qty(stock)} ${p.unit}", style = MaterialTheme.typography.bodySmall, color = if (stock <= p.minStock) Shop.colors.danger else Shop.colors.muted)
      Spacer(Modifier.height(10.dp))
      @Composable fun F(v: String, set: (String) -> Unit, label: String, numeric: Boolean = false) {
        OutlinedTextField(
          value = v, onValueChange = { set(if (numeric) ir.vil3ntec.tohid.numericInput(it) else it) },
          label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
      }
      F(name, { name = it }, "نام")
      if (similar.isNotEmpty()) {
        Text(
          "نامِ مشابه در دکان: " + similar.joinToString("، ") { "«${it.name}»" },
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning,
        )
        Spacer(Modifier.height(6.dp))
      }
      F(barcode, { barcode = it }, if (tools) "بارکدها (با «،» جدا)" else "بارکد")
      F(category, { category = it }, "دسته")
      if (tools) {
        F(brand, { brand = it }, "برند")
        F(tags, { tags = it }, "برچسب‌ها (با «،» جدا)")
      }
      F(unit, { unit = it }, "واحد")
      if (tools) {
        PackEditor(packs, unit) { packs = it }
        Spacer(Modifier.height(6.dp))
      }
      Row {
        Box(Modifier.weight(1f)) { F(buy, { buy = it }, "قیمتِ خرید", true) }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { F(sell, { sell = it }, "قیمتِ فروش", true) }
      }
      if (tools && (buy != qty(p.purchasePrice) || sell != qty(p.salePrice))) {
        F(priceReason, { priceReason = it }, "دلیلِ تغییرِ قیمت (با نامِ شما در سابقه)")
      }
      F(min, { min = it }, "حدِ هشدارِ موجودی", true)
      if (levelsOn) {
        Row {
          Box(Modifier.weight(1f)) { F(reorder, { reorder = it }, "نقطهٔ سفارش", true) }
          Spacer(Modifier.width(6.dp))
          Box(Modifier.weight(1f)) { F(maxStock, { maxStock = it }, "بیشینهٔ موجودی", true) }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = active, onCheckedChange = { active = it })
        Text("فعال (در فروش دیده می‌شود)", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      }
      if (infoOn) {
        Spacer(Modifier.height(8.dp))
        ir.vil3ntec.tohid.ui.components.product.ProductInfoFields(info, d, p.id) { info = it }
      }
      Spacer(Modifier.height(10.dp))
      Button(onClick = { save(); focus.clearFocus() }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره  (Enter / Ctrl+S)") }
      Spacer(Modifier.height(6.dp))
      Row {
        OutlinedButton(onClick = onWarehouse, modifier = Modifier.weight(1f)) { Text("ورودِ انبار") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f)) { Text("جزئیات و عکس") }
      }
    }
  }
}
