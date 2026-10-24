package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.runtime.rememberCoroutineScope
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.BulkProductSheet
import ir.vil3ntec.tohid.ui.screens.DeskProductsScreen
import ir.vil3ntec.tohid.ui.screens.ProductDetailScreen
import ir.vil3ntec.tohid.ui.screens.ProductDialog
import ir.vil3ntec.tohid.ui.screens.ProductFormState
import ir.vil3ntec.tohid.ui.screens.ProductsScreen
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.nav.commitWarehouse
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/** مسیرهای گروهِ کالاها (G2) — پیوندِ گروه‌های دیگر فقط از همین‌ها */
object ProductsPaths {
  const val LIST = "products"
  fun list(filter: String? = null, q: String? = null) = RoutePaths.withQuery(LIST, "filter" to filter, "q" to q)
  fun item(id: String) = "products/" + RoutePaths.encode(id)
  fun new(barcode: String? = null) = RoutePaths.withQuery("products/new", "barcode" to barcode)
  fun edit(id: String) = item(id) + "/edit"
  fun categories(pick: Boolean = false) = RoutePaths.withQuery("products/categories", "pick" to if (pick) "1" else null)
}

/**
 *  گروهِ کالاها (G2، `products.md`). هر مسیرِ NAV-MAP ثبت است و امروز به صفحهٔ
 *  پیشین اشاره می‌کند؛ G2 محتوای هر مسیر را با صفحهٔ تازهٔ خودش (در همین پوشه)
 *  عوض می‌کند و جز این فایل جای دیگری را دست نمی‌زند.
 */
val ProductsModule = AppModule(
  id = "products",
  title = { Str.products },
  icon = Icons.Outlined.Sell,
  entries = listOf(
    ModuleEntry("products", { Str.products }, ProductsPaths.LIST, Icons.Outlined.Sell, setOf(Place.NAV, Place.HOME), hue = V3Hue.BRAND, order = 30),
  ),
  routes = listOf(
    V3Route(ProductsPaths.LIST, RouteKind.TAB, { Str.products }) {
      if (desk) DeskProductsScreen(
        store = store, d = data, snackbar = snackbar,
        onOpenWarehouse = { id -> nav.open(if (id == null) StockPaths.LIST else StockPaths.item(id)) },
        onOpenProduct = { id -> nav.open(ProductsPaths.item(id)) },
      ) else ProductsScreen(
        store = store, d = data, snackbar = snackbar,
        onOpenWarehouse = { id -> nav.open(StockPaths.item(id)) },
        onOpenProduct = { id -> nav.open(ProductsPaths.item(id)) },
      )
    },
    V3Route("products/{id}", RouteKind.PAGE, { Str.products }, parent = ProductsPaths.LIST, aliases = listOf("product"), chrome = Chrome.OWN) {
      val id = args["id"].orEmpty()
      ProductDetailScreen(
        store = store, d = data, productId = id,
        onBack = { back() },
        onEdit = { nav.open(ProductsPaths.edit(id)) },
        onEntry = { nav.open(StockPaths.entry(product = id)) },
      )
    },
    //  بارکدِ ناشناس (صندوق، انبار، جست‌وجو) ⇐ فرمِ یک کالا با همان بارکد و بعد ورودِ انبارش؛ بی بارکد ⇐ ثبتِ چندتایی
    V3Route("products/new", RouteKind.PAGE, { Str.products }, parent = ProductsPaths.LIST, chrome = Chrome.OWN) {
      val scope = rememberCoroutineScope()
      val barcode = args.query("barcode")
      if (barcode != null) ProductDialog(
        d = data,
        state = ProductFormState(barcode = barcode),
        onDismiss = { back() },
        onSave = { draft ->
          commitWarehouse(scope, WarehouseEngine.addProduct(data, draft, System.currentTimeMillis(), ::newId), "کالا ثبت شد") { ok ->
            nav.replace(StockPaths.entry(product = ok.id))
          }
        },
      ) else BulkProductSheet(
        d = data,
        onDismiss = { back() },
        onSave = { drafts ->
          commitWarehouse(scope, WarehouseEngine.addProducts(data, drafts, System.currentTimeMillis(), ::newId), "${drafts.size} کالا ثبت شد") { back() }
        },
      )
    },
    V3Route("products/{id}/edit", RouteKind.PAGE, { Str.products }, parent = "products/{id}", chrome = Chrome.OWN) {
      val scope = rememberCoroutineScope()
      val id = args["id"].orEmpty()
      val product = data.products.find { it.id == id }
      if (product == null) LandOn(ProductsPaths.LIST)
      else ProductDialog(
        d = data,
        state = ProductFormState.of(product),
        onDismiss = { back() },
        onSave = { draft ->
          commitWarehouse(scope, WarehouseEngine.editProduct(data, id, draft, todayIso(), System.currentTimeMillis(), ::newId), "با موفقیت ویرایش شد") { back() }
        },
      )
    },
    //  عکس، استودیو، دسته‌ها و تغییرِ گروهیِ دسته امروز درونِ همان صفحه‌ها‌اند
    V3Route("products/{id}/photo", RouteKind.SHEET, { Str.products }, parent = "products/{id}") { LandOn(ProductsPaths.item(args["id"].orEmpty())) },
    V3Route("products/{id}/photo/studio", RouteKind.PAGE, { Str.products }, parent = "products/{id}") { LandOn(ProductsPaths.item(args["id"].orEmpty())) },
    V3Route("products/categories", RouteKind.SHEET, { Str.products }, parent = ProductsPaths.LIST) { LandOn(ProductsPaths.LIST) },
    V3Route("products/bulk/category", RouteKind.SHEET, { Str.products }, parent = ProductsPaths.LIST) { LandOn(ProductsPaths.LIST) },
  ),
)
