package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.rememberCoroutineScope
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.AdjustDialog
import ir.vil3ntec.tohid.ui.screens.BulkEntrySheet
import ir.vil3ntec.tohid.ui.screens.EntryDialog
import ir.vil3ntec.tohid.ui.screens.WarehouseScreen
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.nav.commitWarehouse

/** مسیرهای گروهِ انبار (G3) */
object StockPaths {
  const val LIST = "stock"
  fun list(filter: String? = null, q: String? = null) = RoutePaths.withQuery(LIST, "filter" to filter, "q" to q)
  fun item(id: String) = "stock/" + RoutePaths.encode(id)
  fun entry(product: String? = null, barcode: String? = null) = RoutePaths.withQuery("stock/entry", "product" to product, "barcode" to barcode)
  const val ENTRY_BULK = "stock/entry/bulk"
  fun adjust(id: String) = item(id) + "/adjust"
  fun movements(id: String) = item(id) + "/movements"
  const val COUNT = "stock/count"
  const val IMPORT = "stock/import"
}

/**
 *  گروهِ انبار (G3، `stock.md`). هر مسیرِ NAV-MAP ثبت است و امروز به صفحه یا
 *  پنجرهٔ پیشین اشاره می‌کند؛ G3 هر محتوا را با صفحهٔ تازهٔ خودش عوض می‌کند.
 */
val StockModule = AppModule(
  id = "stock",
  title = { Str.warehouse },
  icon = Icons.Outlined.Inventory2,
  entries = listOf(
    ModuleEntry("warehouse", { Str.warehouse }, StockPaths.LIST, Icons.Outlined.Inventory2, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.STOCK, V3Hue.ORANGE, order = 10),
  ),
  routes = listOf(
    V3Route(StockPaths.LIST, RouteKind.TAB, { Str.warehouse }, aliases = listOf("warehouse")) {
      WarehouseScreen(store, data, snackbar, openProductId = args.query("product"))
    },
    V3Route("stock/{id}", RouteKind.PAGE, { Str.warehouse }, parent = StockPaths.LIST) {
      WarehouseScreen(store, data, snackbar, openProductId = args["id"])
    },
    //  با کالا ⇐ ورودِ همان کالا؛ بی کالا ⇐ ورودِ چندتایی (انتخابِ کالا در ردیف‌ها)
    V3Route("stock/entry", RouteKind.SHEET, { Str.warehouse }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
      val scope = rememberCoroutineScope()
      val product = args.query("product")
      if (product != null) EntryDialog(
        d = data, productId = product,
        onDismiss = { back() },
        onSave = { draft ->
          commitWarehouse(scope, WarehouseEngine.addEntry(data, draft, todayIso(), System.currentTimeMillis(), ::newId), "ورود کالا ثبت شد") { back() }
        },
      ) else BulkEntrySheet(
        d = data,
        onDismiss = { back() },
        onSave = { rows, date ->
          commitWarehouse(scope, WarehouseEngine.addEntries(data, rows, date, System.currentTimeMillis(), ::newId), "${rows.size} ردیف ثبت شد") { back() }
        },
      )
    },
    V3Route(StockPaths.ENTRY_BULK, RouteKind.PAGE, { Str.warehouse }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
      val scope = rememberCoroutineScope()
      BulkEntrySheet(
        d = data,
        onDismiss = { back() },
        onSave = { rows, date ->
          commitWarehouse(scope, WarehouseEngine.addEntries(data, rows, date, System.currentTimeMillis(), ::newId), "${rows.size} ردیف ثبت شد") { back() }
        },
      )
    },
    V3Route("stock/{id}/adjust", RouteKind.SHEET, { Str.warehouse }, parent = "stock/{id}", chrome = Chrome.OWN) {
      val scope = rememberCoroutineScope()
      val id = args["id"].orEmpty()
      AdjustDialog(
        d = data, productId = id,
        onDismiss = { back() },
        onSave = { quantity, increase, reason, kind ->
          commitWarehouse(
            scope,
            WarehouseEngine.adjustStock(data, id, quantity, increase, reason, kind, todayIso(), System.currentTimeMillis(), ::newId),
            "با موفقیت ثبت شد",
          ) { back() }
        },
      )
    },
    //  حرکت‌ها امروز درونِ ردیفِ همان کالاست
    V3Route("stock/{id}/movements", RouteKind.PAGE, { Str.warehouse }, parent = "stock/{id}") { LandOn(StockPaths.item(args["id"].orEmpty())) },
    V3Route("stock/{id}/entries/{entryId}/delete", RouteKind.SHEET, { Str.warehouse }, parent = "stock/{id}", chrome = Chrome.OWN) {
      val scope = rememberCoroutineScope()
      val entryId = args["entryId"].orEmpty()
      V3ConfirmSheet(
        title = "حذف این ورودی انبار؟",
        message = WarehouseEngine.deleteEntryWarning(data, entryId),
        confirm = "حذف",
        icon = Icons.Outlined.DeleteOutline,
        onConfirm = {
          commitWarehouse(scope, WarehouseEngine.deleteEntry(data, entryId, todayIso(), System.currentTimeMillis(), ::newId), "با موفقیت حذف شد")
          back()
        },
        onDismiss = { back() },
      )
    },
    //  انبارگردانی و ورود از فایل امروز از خودِ صفحهٔ انبار باز می‌شوند
    V3Route(StockPaths.COUNT, RouteKind.PAGE, { Str.warehouse }, parent = StockPaths.LIST) { LandOn(StockPaths.LIST) },
    V3Route(StockPaths.IMPORT, RouteKind.PAGE, { Str.warehouse }, parent = StockPaths.LIST) { LandOn(StockPaths.LIST) },
  ),
)
