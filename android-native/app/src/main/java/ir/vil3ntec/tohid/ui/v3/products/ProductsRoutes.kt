package ir.vil3ntec.tohid.ui.v3.products

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.screens.DeskProductsScreen
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/** مسیرهای گروهِ کالاها (G2) — پیوندِ گروه‌های دیگر فقط از همین‌ها */
object ProductsPaths {
  const val LIST = "products"
  fun list(filter: String? = null, q: String? = null) = RoutePaths.withQuery(LIST, "filter" to filter, "q" to q)
  fun item(id: String) = "products/" + RoutePaths.encode(id)
  fun new(barcode: String? = null) = RoutePaths.withQuery("products/new", "barcode" to barcode)
  fun edit(id: String) = item(id) + "/edit"
  fun photo(id: String) = item(id) + "/photo"
  fun studio(id: String, source: String) = RoutePaths.withQuery(photo(id) + "/studio", "src" to source)
  /** برگهٔ دسته‌ها؛ `pick` ⇐ انتخاب برای نتیجه (`nav.openForResult`)، `selected` دستهٔ فعلی */
  fun categories(pick: Boolean = false, selected: String? = null) =
    RoutePaths.withQuery("products/categories", "pick" to if (pick) "1" else null, "selected" to selected?.takeIf { pick })
  /** دستهٔ گروهی برای `ids` (نتیجه «1» یعنی عوض شد) */
  fun bulkCategory(ids: Collection<String>) = RoutePaths.withQuery("products/bulk/category", "ids" to ids.joinToString(","))
}

/**
 *  گروهِ کالاها (G2، `products.md`): فهرست، صفحهٔ کالا، فرمِ چندردیفیِ «کالای تازه» و
 *  «ویرایش کالا»، برگهٔ عکس و استودیو، برگهٔ دسته‌ها و دستهٔ گروهی — همه روی کیتِ
 *  نسخهٔ ۳ (`ui/v3/products`). روی کامپیوتر فهرست هنوز `DeskProductsScreen` است.
 */
val ProductsModule = AppModule(
  id = "products",
  title = { Str.products },
  icon = Icons.Outlined.Sell,
  entries = listOf(
    ModuleEntry("products", { Str.products }, ProductsPaths.LIST, Icons.Outlined.Sell, setOf(Place.NAV, Place.HOME), hue = V3Hue.BRAND, order = 30),
  ),
  routes = listOf(
    V3Route(ProductsPaths.LIST, RouteKind.TAB, { StrProducts.listTitle }, chrome = Chrome.OWN) {
      if (desk) DeskProductsScreen(
        store = store, d = data, snackbar = snackbar,
        onOpenWarehouse = { id -> nav.open(if (id == null) StockPaths.LIST else StockPaths.item(id)) },
        onOpenProduct = { id -> nav.open(ProductsPaths.item(id)) },
      ) else ProductsListScreen(
        store = store, data = data, toasts = toasts,
        initial = ProductsListModel.fromArgs(args.query("q"), args.query("category"), args.query("filter"), args.query("sort")),
        nav = listNav(),
        initialLayout = args.query("view")?.let(ProductsListModel.Layout::parse),
      )
    },
    V3Route("products/{id}", RouteKind.PAGE, { StrProducts.listTitle }, parent = ProductsPaths.LIST, aliases = listOf("product"), chrome = Chrome.OWN) {
      val id = args["id"].orEmpty()
      ProductDetailScreen(
        store = store, data = data, toasts = toasts, productId = id,
        nav = ProductDetailNav(
          back = { back() },
          edit = { nav.open(ProductsPaths.edit(id)) },
          photo = { nav.open(ProductsPaths.photo(id)) },
          entry = { nav.open(StockPaths.entry(product = id)) },
          stock = { nav.open(StockPaths.item(id)) },
          movements = { nav.open(StockPaths.movements(id)) },
        ),
      )
    },
    //  بارکدِ ناشناس (صندوق، انبار، جست‌وجو) ⇐ همین فرم با بارکدِ پرشده؛ یک کالا ثبت شد ⇐ ورودِ انبارِ همان (مثلِ امروز)
    V3Route("products/new", RouteKind.PAGE, { StrProducts.newProduct }, parent = ProductsPaths.LIST, chrome = Chrome.OWN) {
      val barcode = args.query("barcode")?.takeIf { it.isNotBlank() }
      NewProductsScreen(store, data, toasts, barcode, formNav()) { ids ->
        if (barcode != null && ids.size == 1) nav.replace(StockPaths.entry(product = ids.first())) else back()
      }
    },
    V3Route("products/{id}/edit", RouteKind.PAGE, { StrProducts.editTitle }, parent = "products/{id}", chrome = Chrome.OWN) {
      EditProductScreen(store, data, toasts, args["id"].orEmpty(), formNav()) { back() }
    },
    V3Route("products/{id}/photo", RouteKind.SHEET, { StrProducts.photoTitle }, parent = "products/{id}", chrome = Chrome.OWN) {
      val id = args["id"].orEmpty()
      //  `onPicked` پیش از `onDismiss` می‌آید: عکس رسید ⇐ استودیو جای برگه، وگرنه برگشت
      var picked by remember { mutableStateOf<Uri?>(null) }
      DraftPhotoSourceSheet(
        open = true,
        title = StrProducts.photoTitle,
        onDismiss = { picked?.let { nav.replace(ProductsPaths.studio(id, it.toString())) } ?: back() },
        onPicked = { picked = it },
      )
    },
    V3Route("products/{id}/photo/studio", RouteKind.PAGE, { StrProducts.studioTitle }, parent = "products/{id}", chrome = Chrome.OWN) {
      val id = args["id"].orEmpty()
      val src = args.query("src")
      if (src.isNullOrBlank()) LandOn(ProductsPaths.item(id))
      else {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        PhotoStudioScreen(Uri.parse(src), onBack = { back() }) { bitmap ->
          ProductPhotos.save(context, store, scope, toasts, id, bitmap)
          back()
        }
      }
    },
    //  مشترک با فروشِ سریع (G5، از راهِ همین مسیر): `?pick=1` ⇐ نامِ دسته با `nav.finish`
    V3Route("products/categories", RouteKind.SHEET, { StrProducts.categoriesTitle }, parent = ProductsPaths.LIST, chrome = Chrome.OWN) {
      var picked by remember { mutableStateOf<String?>(null) }
      val use = if (args.flag("pick")) CategoryUse.Pick(args.query("selected")) { picked = it } else CategoryUse.Manage
      CategorySheet(store, data, toasts, use, onDismiss = { picked?.let { nav.finish(it) } ?: back() })
    },
    V3Route("products/bulk/category", RouteKind.SHEET, { StrProducts.bulkCategory }, parent = ProductsPaths.LIST, chrome = Chrome.OWN) {
      val ids = args.query("ids").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
      if (ids.isEmpty()) LandOn(ProductsPaths.LIST)
      else {
        var done by remember { mutableStateOf(false) }
        CategorySheet(store, data, toasts, CategoryUse.Bulk(ids) { done = true }, onDismiss = { if (done) nav.finish(BULK_DONE) else back() })
      }
    },
  ),
)

private const val BULK_DONE = "1"

private fun RouteScope.listNav() = ProductsListNav(
  open = { nav.open(ProductsPaths.item(it)) },
  edit = { nav.open(ProductsPaths.edit(it)) },
  entry = { nav.open(StockPaths.entry(product = it)) },
  new = { nav.open(ProductsPaths.new()) },
  scan = {
    nav.openForResult(SalesPaths.scan("product")) { code ->
      code?.let { nav.open(ProductsListModel.scanTarget(ShopStore.barcodeIndex(store.data.value), it)) }
    }
  },
  bulkCategory = { ids, done -> nav.openForResult(ProductsPaths.bulkCategory(ids)) { r -> if (r != null) done() } },
)

private fun RouteScope.formNav() = ProductFormNav(
  back = { back() },
  scan = { onCode -> nav.openForResult(SalesPaths.scan("product")) { code -> code?.let(onCode) } },
  pickCategory = { current, onPicked ->
    nav.openForResult(ProductsPaths.categories(pick = true, selected = current.takeIf { it.isNotBlank() })) { name -> name?.let(onPicked) }
  },
)
