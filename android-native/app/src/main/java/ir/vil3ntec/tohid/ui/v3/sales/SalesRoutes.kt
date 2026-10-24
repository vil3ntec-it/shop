package ir.vil3ntec.tohid.ui.v3.sales

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.screens.BarcodeScanSheet
import ir.vil3ntec.tohid.ui.screens.DeskInvoicesScreen
import ir.vil3ntec.tohid.ui.screens.DeskSaleScreen
import ir.vil3ntec.tohid.ui.screens.QuickSaleScreen
import ir.vil3ntec.tohid.ui.screens.SaleScreen
import ir.vil3ntec.tohid.ui.screens.SalesHistoryScreen
import ir.vil3ntec.tohid.ui.screens.VipGate
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths

/** مسیرهای گروهِ فروش (G5) */
object SalesPaths {
  const val SALE = "sale"
  fun sale(scan: Boolean = false) = RoutePaths.withQuery(SALE, "scan" to if (scan) "1" else null)
  const val QUICK = "sale/quick"
  const val CHECKOUT = "sale/checkout"
  /** اسکن برای نتیجه: `nav.openForResult(SalesPaths.scan("stock")) { code -> … }` */
  fun scan(forWhat: String) = RoutePaths.withQuery("scan", "for" to forWhat)
  const val HISTORY = "sales"
  fun history(filter: String? = null, q: String? = null) = RoutePaths.withQuery(HISTORY, "filter" to filter, "q" to q)
  fun invoice(saleId: String) = "sales/" + RoutePaths.encode(saleId)
  const val DRAWER = "sales/drawer"
}

/**
 *  گروهِ فروش (G5، `sales.md`): صندوق (r2/r3، نگه داشته می‌شود)، فروشِ سریع، اسکن،
 *  تاریخچه و فاکتور. برگه‌های صندوق امروز درونِ خودِ صندوق‌اند.
 */
val SalesModule = AppModule(
  id = "sales",
  title = { Str.sale },
  icon = Icons.Outlined.PointOfSale,
  entries = listOf(
    ModuleEntry("sale", { Str.sale }, SalesPaths.SALE, Icons.Outlined.PointOfSale, setOf(Place.NAV, Place.HOME), hue = V3Hue.BRAND, order = 10),
    ModuleEntry("scan", { StrKit.scanBarcode }, SalesPaths.sale(scan = true), Icons.Outlined.QrCodeScanner, setOf(Place.NAV), order = 70),
    ModuleEntry("history",{ Str.salesHistoryAlt }, SalesPaths.HISTORY, Icons.Outlined.History, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.SALES, V3Hue.BLUE, order = 30),
  ),
  routes = listOf(
    V3Route(SalesPaths.SALE, RouteKind.TAB, { Str.sale }, chrome = Chrome.OWN) {
      if (args.flag("scan")) LaunchedEffect(args.path) { PhoneNav.scanRequest.value = true }
      VipGate(Str.saleRegister) {
        if (Platform.DESKTOP) DeskSaleScreen(store = store, cartStore = cart, d = data, snackbar = snackbar) { code ->
          nav.open(ProductsPaths.new(barcode = code))
        } else SaleScreen(store = store, cartStore = cart, d = data, snackbar = snackbar, onQuickSale = { nav.open(SalesPaths.QUICK) }) { code ->
          nav.open(ProductsPaths.new(barcode = code))
        }
      }
    },
    V3Route(SalesPaths.QUICK, RouteKind.PAGE, { Str.pickProduct }, parent = SalesPaths.SALE, aliases = listOf("quick"), chrome = Chrome.OWN) {
      VipGate(Str.saleRegister) { QuickSaleScreen(store, cart, data, snackbar) { nav.switchTab(SalesPaths.SALE) } }
    },
    V3Route(SalesPaths.CHECKOUT, RouteKind.SHEET, { Str.sale }, parent = SalesPaths.SALE) { LandOn(SalesPaths.SALE) },
    V3Route("sale/checkout/customer", RouteKind.SHEET, { Str.sale }, parent = SalesPaths.CHECKOUT) { LandOn(SalesPaths.SALE) },
    V3Route("sale/line/{i}", RouteKind.SHEET, { Str.sale }, parent = SalesPaths.SALE) { LandOn(SalesPaths.SALE) },
    V3Route("sale/free-line", RouteKind.SHEET, { Str.sale }, parent = SalesPaths.SALE) { LandOn(SalesPaths.SALE) },
    V3Route("sale/keypad/{productId}", RouteKind.SHEET, { Str.sale }, parent = SalesPaths.SALE) { LandOn(SalesPaths.SALE) },
    //  اسکن به‌عنوانِ نتیجه: کد با `finish` به صدازننده برمی‌گردد
    V3Route("scan", RouteKind.SHEET, { Str.sale }, chrome = Chrome.OWN) {
      //  برگه پس از `onCode` خودش `onDismiss` هم می‌زند: پس از نتیجه، برگشتِ دوم نه
      var got by remember { mutableStateOf(false) }
      BarcodeScanSheet(onDismiss = { if (!got) back() }, onCode = { code -> got = true; nav.finish(code) })
    },
    V3Route(SalesPaths.HISTORY, RouteKind.TAB, { Str.salesHistory }) {
      if (Platform.DESKTOP) DeskInvoicesScreen(store, cart, data, snackbar) { nav.switchTab(SalesPaths.SALE) }
      else SalesHistoryScreen(store, cart, data, snackbar) { nav.switchTab(SalesPaths.SALE) }
    },
    //  فاکتور، چاپ، مرجوعی، لغو و صندوقِ نقد امروز درونِ تاریخچه‌اند
    V3Route(SalesPaths.DRAWER, RouteKind.PAGE, { Str.salesHistory }, parent = SalesPaths.HISTORY) { LandOn(SalesPaths.HISTORY) },
    V3Route("sales/drawer/open", RouteKind.SHEET, { Str.salesHistory }, parent = SalesPaths.DRAWER) { LandOn(SalesPaths.HISTORY) },
    V3Route("sales/drawer/close", RouteKind.SHEET, { Str.salesHistory }, parent = SalesPaths.DRAWER) { LandOn(SalesPaths.HISTORY) },
    V3Route("sales/{saleId}", RouteKind.PAGE, { Str.salesHistory }, parent = SalesPaths.HISTORY) { LandOn(SalesPaths.HISTORY) },
    V3Route("sales/{saleId}/print", RouteKind.SHEET, { Str.salesHistory }, parent = "sales/{saleId}") { LandOn(SalesPaths.HISTORY) },
    V3Route("sales/{saleId}/return", RouteKind.SHEET, { Str.salesHistory }, parent = "sales/{saleId}") { LandOn(SalesPaths.HISTORY) },
    V3Route("sales/{saleId}/cancel", RouteKind.SHEET, { Str.salesHistory }, parent = "sales/{saleId}") { LandOn(SalesPaths.HISTORY) },
    V3Route("sales/{saleId}/fix", RouteKind.SHEET, { Str.salesHistory }, parent = "sales/{saleId}") { LandOn(SalesPaths.HISTORY) },
    //  پیشنهادِ فاکتورِ نیمه‌کاره خودکار سرِ باز شدن است (`DraftRecoveryPrompt`)
    V3Route("draft", RouteKind.SHEET, { Str.sale }) { LandOn("home") },
  ),
)
