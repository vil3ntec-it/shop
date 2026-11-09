package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.core.i18n.StrImageChanges
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.i18n.StrTransfer
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.StockStrip
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3TwoPane
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths

/** مسیرهای گروهِ انبار (G3) */
object StockPaths {
  const val LIST = "stock"
  fun list(filter: String? = null, q: String? = null) = RoutePaths.withQuery(LIST, "filter" to filter, "q" to q)
  fun item(id: String) = "stock/" + RoutePaths.encode(id)
  fun entry(product: String? = null, barcode: String? = null) = RoutePaths.withQuery("stock/entry", "product" to product, "barcode" to barcode)
  const val ENTRY_BULK = "stock/entry/bulk"
  fun adjust(id: String) = item(id) + "/adjust"
  fun movements(id: String) = item(id) + "/movements"
  fun deleteEntry(id: String, entryId: String) = item(id) + "/entries/" + RoutePaths.encode(entryId) + "/delete"
  const val COUNT = "stock/count"
  const val IMPORT = "stock/import"
  //  کارهای افزوده‌ای که پس از رابطِ قدیم بی‌راه مانده بودند (`StockExtras`، `StockExtrasAndroid.kt`)
  const val COUNT_TOOLS = "stock/count/tools"
  const val PLACES = "stock/places"
  const val LABELS = "stock/labels"
  const val BOOK = "stock/book"
  fun book(tab: String? = null) = StockExtras.bookPath(tab)
  const val BULK_PRODUCTS = "stock/bulk-products"
  const val IMAGES = "stock/images"
  /** IMG-PRO-17: تغییرِ دسته‌ایِ عکس‌ها و برگشتش — راهش فقط با بانکِ روشن (`ImageChangesGate`) */
  const val IMAGE_CHANGES = "stock/images/changes"
  const val IMAGE_CHANGES_NEW = "stock/images/changes/new"
  fun imageChange(id: String) = "$IMAGE_CHANGES/" + RoutePaths.encode(id)
  const val IMPORT_HISTORY = "stock/import/history"
  /** INV-11: انتقالِ موجودیِ سرور (`StockTransfer`) — راهش فقط با پرچمِ `shop.catalog` و `stock.move` (`StockTransferFlag`) */
  const val TRANSFERS = "stock/transfers"
  const val TRANSFER_NEW = "stock/transfers/new"
  fun transfer(key: String) = "$TRANSFERS/" + RoutePaths.encode(key)
}

/**
 *  گروهِ انبار (G3، `stock.md`) — همهٔ صفحه‌ها روی کیتِ نسخهٔ ۳ (`ui/v3/stock`). این فایل
 *  فقط سیم‌کشی است: مسیر ⇐ صفحه، کارِ صفحه ⇐ مسیرِ دیگر یا موتورِ انبار (`StockWriter`).
 *  پیوند به گروه‌های دیگر فقط با مسیر (`ProductsPaths`، `SalesPaths`، `MoneyPaths`).
 *  دسترسی همان امروز: انبار با اشتراکِ تمام‌شده هم باز است (قفلِ نرم فقط بخش‌های پولی).
 */
val StockModule = AppModule(
  id = "stock",
  title = { Str.warehouse },
  icon = Icons.Outlined.Inventory2,
  entries = listOf(
    ModuleEntry("warehouse", { Str.warehouse }, StockPaths.LIST, Icons.Outlined.Inventory2, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.STOCK, V3Hue.ORANGE, order = 10),
  ),
  routes = listOf(
    V3Route(StockPaths.LIST, RouteKind.TAB, { Str.warehouse }, aliases = listOf("warehouse"), chrome = Chrome.OWN) {
      //  پیوندِ قدیمِ `stock?product=` (کامپیوتر، اسکن) ⇐ همان کالا
      val legacy = args.query("product")
      if (legacy != null) LaunchedEffect(legacy) { nav.replace(StockPaths.item(legacy)) }
      StockHome(selected = null)
    },
    V3Route("stock/{id}", RouteKind.PAGE, { Str.warehouse }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
      StockHome(selected = args["id"].orEmpty())
    },
    V3Route("stock/entry", RouteKind.SHEET, { StrStock.entryTitle }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
      val writer = rememberStockWriter()
      StockEntrySheet(
        data = data,
        product = args.query("product"),
        barcode = args.query("barcode"),
        onSave = { draft -> writer.commit(data, WarehouseEngine.addEntry(data, draft, writer.today, writer.now, writer.newId), StrStock.entrySaved) },
        onNewProduct = { code -> nav.replace(ProductsPaths.new(barcode = code)) },
        onClose = { back() },
      )
    },
    V3Route(StockPaths.ENTRY_BULK, RouteKind.PAGE, { StrStock.bulkTitle }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
      val writer = rememberStockWriter()
      BulkEntryScreen(
        data = data,
        today = writer.today,
        onScan = { use -> nav.openForResult(SalesPaths.scan("stock")) { code -> code?.let(use) } },
        onSave = { rows, date ->
          //  همان فراخوانِ صفحهٔ پیشین: تاریخِ ردیف‌ها جای «امروز» ِ موتور
          writer.commit(data, WarehouseEngine.addEntries(data, rows, date, writer.now, writer.newId), StrStock.rowsSaved(money(rows.size.toDouble())))
            .also { if (it == null) back() }
        },
        onBack = { back() },
      )
    },
    V3Route("stock/{id}/adjust", RouteKind.SHEET, { StrStock.adjustAction }, parent = "stock/{id}", chrome = Chrome.OWN) {
      val writer = rememberStockWriter()
      val id = args["id"].orEmpty()
      val product = data.products.find { it.id == id }
      if (product == null) LaunchedEffect(id) { back() }
      else AdjustSheet(
        product = product,
        stock = ShopStore.stock(data, id),
        onSave = { f ->
          writer.commit(data, 
            WarehouseEngine.adjustStock(data, id, f.quantity, f.choice.increase, f.reason, f.choice.kind, writer.today, writer.now, writer.newId, userId = writer.user),
            if (f.choice == AdjustChoice.RETURN) StrStock.returned else StrStock.adjusted,
          )
        },
        onClose = { back() },
      )
    },
    V3Route("stock/{id}/movements", RouteKind.PAGE, { StrStock.movementsRow }, parent = "stock/{id}", chrome = Chrome.OWN) {
      val writer = rememberStockWriter()
      val export = rememberCsvExport { ok -> if (ok) writer.info(StrStock.fileSaved) else writer.fail(StrStock.fileFailed) }
      MovementsScreen(data, args["id"].orEmpty(), writer.today, onOpen = { nav.open(it) }, onBack = { back() }, onExport = export)
    },
    V3Route("stock/{id}/entries/{entryId}/delete", RouteKind.SHEET, { StrStock.deleteEntryTitle }, parent = "stock/{id}", chrome = Chrome.OWN) {
      val writer = rememberStockWriter()
      val entryId = args["entryId"].orEmpty()
      //  `V3ConfirmSheet` پس از هر دو دکمه `onDismiss` را صدا می‌زند ⇒ برگشت فقط همان‌جا
      V3ConfirmSheet(
        title = StrStock.deleteEntryTitle,
        message = WarehouseEngine.deleteEntryWarning(data, entryId),
        confirm = StrStock.delete,
        icon = Icons.Outlined.DeleteOutline,
        onConfirm = {
          writer.commit(data, WarehouseEngine.deleteEntry(data, entryId, writer.today, writer.now, writer.newId), StrStock.deleted)?.let(writer::fail)
        },
        onDismiss = { back() },
      )
    },
    V3Route(StockPaths.COUNT, RouteKind.PAGE, { StrStock.menuCount }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
      //  پیوندِ مستقیم (خانه، اسکن‌های در انتظار) همان راهِ دکمهٔ «انبارگردانی» را می‌رود
      val target = rememberCountRoute()
      if (target != StockPaths.COUNT) { LaunchedEffect(target) { nav.replace(target) }; return@V3Route }
      val writer = rememberStockWriter()
      val export = rememberCsvExport { ok -> if (ok) writer.info(StrStock.fileSaved) else writer.fail(StrStock.fileFailed) }
      StockCountScreen(
        data = data,
        today = writer.today,
        onExport = export,
        onSave = { counted, note, at ->
          val n = WarehouseEngine.countLines(data, counted).size
          writer.commit(data, WarehouseEngine.stockCount(data, counted, note, writer.today, writer.now, writer.newId, userId = writer.user, countedAt = at), StrStock.countSaved(money(n.toDouble())))
        },
        onBack = { back() },
        clock = { writer.now },
      )
    },
    V3Route(StockPaths.IMPORT, RouteKind.PAGE, { StrStock.importTitle }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
      val writer = rememberStockWriter()
      //  PRD-20 (`ShopPolicy.productTools`): xlsx یا CSV، پیش‌نمایشِ کامل، فایلِ ردیف‌های رد و سابقه
      if (rememberProductTools()) { ProductToolsImportRoute(writer); return@V3Route }
      StockImportRoute(
        data = data,
        onSave = { plan ->
          writer.commit(data, WarehouseEngine.addProducts(data, plan.drafts, writer.now, writer.newId), StrStock.imported(money(plan.drafts.size.toDouble())))
            .also { if (it == null) back() }
        },
        onBack = { back() },
      )
    },
    //  ── کارهای افزوده (`StockExtras`) ──
    V3Route(StockPaths.COUNT_TOOLS, RouteKind.SHEET, { StrStock.menuCount }, parent = StockPaths.LIST, chrome = Chrome.OWN) { CountToolsRoute() },
    V3Route(StockPaths.PLACES, RouteKind.PAGE, { StrReach.places }, parent = StockPaths.LIST) { StockPlacesRoute() },
    V3Route(StockPaths.LABELS, RouteKind.SHEET, { StrLabel.title }, parent = StockPaths.LIST, chrome = Chrome.OWN) { StockLabelsRoute() },
    V3Route(StockPaths.BOOK, RouteKind.PAGE, { StrReach.stockBook }, parent = StockPaths.LIST, aliases = listOf("stockbook")) { StockBookRoute() },
    V3Route(StockPaths.BULK_PRODUCTS, RouteKind.SHEET, { StrReach.bulkProducts }, parent = StockPaths.LIST, chrome = Chrome.OWN) { BulkProductsRoute() },
    V3Route(StockPaths.IMAGES, RouteKind.SHEET, { StrReach.imageImport }, parent = StockPaths.LIST, chrome = Chrome.OWN) { ImageImportRoute() },
    V3Route(StockPaths.IMAGE_CHANGES, RouteKind.PAGE, { StrImageChanges.title }, parent = StockPaths.LIST) { ImageChangesRoute() },
    V3Route(StockPaths.IMAGE_CHANGES_NEW, RouteKind.PAGE, { StrImageChanges.newBatch }, parent = StockPaths.IMAGE_CHANGES) { ImageBatchRoute() },
    V3Route("stock/images/changes/{id}", RouteKind.PAGE, { StrImageChanges.batch }, parent = StockPaths.IMAGE_CHANGES) { ImageChangeRoute(args["id"].orEmpty()) },
    V3Route(StockPaths.IMPORT_HISTORY, RouteKind.SHEET, { StrReach.importHistory }, parent = StockPaths.IMPORT, chrome = Chrome.OWN) { ImportHistoryRoute() },
    //  INV-11 — انتقال با «در مسیر»، دریافتِ ناقص و دریافت با اسکن (`TransferRoutesAndroid.kt`)
    V3Route(StockPaths.TRANSFERS, RouteKind.PAGE, { StrTransfer.title }, parent = StockPaths.LIST, chrome = Chrome.OWN) { TransferListRoute() },
    V3Route(StockPaths.TRANSFER_NEW, RouteKind.PAGE, { StrTransfer.newTransfer }, parent = StockPaths.TRANSFERS, chrome = Chrome.OWN) { TransferDraftRoute() },
    V3Route("stock/transfers/{key}", RouteKind.PAGE, { StrTransfer.receiveTitle }, parent = StockPaths.TRANSFERS, chrome = Chrome.OWN) { TransferReceiveRoute(args["key"].orEmpty()) },
  ),
)

/**
 *  «انبار» و «یک کالا» با هم: گوشی ⇐ فهرست یا صفحهٔ کالا؛ چیدمانِ کامپیوتری (`desk`) ⇐ فهرست | کالا
 *  در دو ستون، و کارهای «…» دکمهٔ زیرِ جست‌وجو.
 */
@Composable
private fun RouteScope.StockHome(selected: String?) {
  val twoPane = desk
  val writer = rememberStockWriter()
  val today = writer.today
  val countRoute = rememberCountRoute()
  val placesOn = rememberStockPlacesOn()
  val listActions = StockListActions(
    //  کامپیوتر (دو ستون): کالای دیگر جای همین ستون می‌نشیند، نه پلهٔ تازه در پشته
    open = { id -> if (twoPane && selected != null) nav.replace(StockPaths.item(id)) else nav.open(StockPaths.item(id)) },
    entry = { nav.open(StockPaths.entry(product = it)) },
    adjust = { nav.open(StockPaths.adjust(it)) },
    scan = { nav.openForResult(SalesPaths.scan("stock")) { code -> code?.let { nav.open(StockListModel.scanTarget(data, it)) } } },
    count = { nav.open(countRoute) },
    import = { nav.open(StockPaths.IMPORT) },
    bulk = { nav.open(StockPaths.ENTRY_BULK) },
    newProduct = { nav.open(ProductsPaths.new()) },
    saveEntry = { draft -> writer.commit(data, WarehouseEngine.addEntry(data, draft, today, writer.now, writer.newId), StrStock.entrySaved) },
    extras = rememberStockExtras(),
    //  موتورِ تازه روشن ⇒ ارزش و وظیفه‌های بازِ «دفترِ موجودی» بالای فهرست (خاموش ⇒ هیچ)
    header = { StockStrip(data) { nav.open(StockPaths.book()) } },
  )
  val itemActions = StockItemActions(
    edit = { nav.open(ProductsPaths.edit(it)) },
    view = { nav.open(ProductsPaths.item(it)) },
    deleteProduct = { id ->
      val failed = writer.commit(data, WarehouseEngine.deleteProduct(data, id, today, writer.now, writer.newId), StrStock.deleted)
      if (failed != null) writer.fail(failed) else if (twoPane) nav.replace(StockPaths.LIST) else back()
    },
    entry = { nav.open(StockPaths.entry(product = it)) },
    adjust = { nav.open(StockPaths.adjust(it)) },
    deleteEntry = { entryId -> selected?.let { nav.open(StockPaths.deleteEntry(it, entryId)) } },
    movements = { nav.open(StockPaths.movements(it)) },
    copied = { writer.info(StrStock.barcodeCopied) },
  )
  val list = @Composable {
    StockListScreen(
      data = data,
      today = today,
      actions = listActions,
      initialFilter = StockFilter.of(args.query("filter")),
      initialQuery = args.query("q").orEmpty(),
      onBack = if (selected == null) backOrNull else null,
      wide = twoPane,
      selectedId = selected,
      places = placesOn,
    )
  }
  when {
    twoPane -> V3TwoPane(list = list, detail = {
      if (selected != null) StockItemScreen(data, today, selected, itemActions, onBack = null)
      else V3EmptyState(Icons.Outlined.Inventory2, StrStock.pickOne)
    })
    selected == null -> list()
    else -> StockItemScreen(data, today, selected, itemActions, onBack = backOrNull)
  }
}
