package ir.vil3ntec.tohid.ui.v3.sales

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.runtime.LaunchedEffect
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.screens.DeskInvoicesScreen
import ir.vil3ntec.tohid.ui.screens.DeskSaleScreen
import ir.vil3ntec.tohid.ui.screens.VipGate
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.cart.FreeLineRoute
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadRoute
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineRoute
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleRoute
import ir.vil3ntec.tohid.ui.v3.sales.checkout.ApprovalRoute
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CreditApprovalRoute
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutRoute
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerRoute
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerCloseRoute
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerMoveRoute
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerOpenRoute
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerRoute
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerShiftRoute
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerXRoute
import ir.vil3ntec.tohid.ui.v3.sales.history.CancelRoute
import ir.vil3ntec.tohid.ui.v3.sales.history.FixRoute
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryRoute
import ir.vil3ntec.tohid.ui.v3.sales.history.InvoiceRoute
import ir.vil3ntec.tohid.ui.v3.sales.history.SalePrintRoute
import ir.vil3ntec.tohid.ui.v3.sales.quick.QuickSaleRoute
import ir.vil3ntec.tohid.ui.v3.sales.returns.NoReceiptReturnRoute
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnPrintRoute
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnReceiptRoute
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnRoute
import ir.vil3ntec.tohid.ui.v3.sales.scan.SaleScanRoute
import ir.vil3ntec.tohid.ui.v3.sales.scan.ScanScreen

/** مسیرهای گروهِ فروش (G5) */
object SalesPaths {
  const val SALE = "sale"
  fun sale(scan: Boolean = false) = RoutePaths.withQuery(SALE, "scan" to if (scan) "1" else null)
  const val QUICK = "sale/quick"
  const val CHECKOUT = "sale/checkout"
  /** تسویهٔ سبدِ جاری؛ از فروشِ سریع ⇒ `?from=quick` (خبرِ فروش به اعضای دکان، همان امروز) */
  fun checkout(quick: Boolean = false) = RoutePaths.withQuery(CHECKOUT, "from" to if (quick) FROM_QUICK else null)
  const val FROM_QUICK = "quick"
  /** انتخابِ مشتری برای نتیجه: شناسه، یا `""` = «بدون قرض‌دار» */
  fun customer(selected: String? = null) = RoutePaths.withQuery("sale/checkout/customer", "selected" to selected)
  /** برگهٔ «درخواستِ تأیید» ِ تخفیف (STF-02) */
  const val APPROVE = "sale/checkout/approve"
  /** برگهٔ «فروشِ نسیه بیش از سقف» (CRM-03، پشتِ `ShopPolicy.creditGate`) */
  const val CREDIT = "sale/checkout/credit"
  /** «تعداد و قیمت»ِ ردیفِ `i` ِ سبد */
  fun line(i: Int) = "sale/line/$i"
  const val FREE_LINE = "sale/free-line"
  fun keypad(productId: String) = "sale/keypad/" + RoutePaths.encode(productId)
  /** اسکن برای نتیجه: `nav.openForResult(SalesPaths.scan("stock")) { code -> … }` */
  fun scan(forWhat: String) = RoutePaths.withQuery("scan", "for" to forWhat)
  const val SCAN_QUICK = "quick"
  /** اسکنِ پیوستهٔ صندوق (برگه باز می‌ماند) */
  const val SCAN_SALE = "sale"
  /** QRِ رسید برای برگشت (RET-01) */
  const val SCAN_RECEIPT = "receipt"
  const val HISTORY = "sales"
  fun history(filter: String? = null, q: String? = null) = RoutePaths.withQuery(HISTORY, "filter" to filter, "q" to q)
  fun invoice(saleId: String) = "sales/" + RoutePaths.encode(saleId)
  fun print(saleId: String) = invoice(saleId) + "/print"
  fun returnOf(saleId: String) = invoice(saleId) + "/return"
  fun cancelOf(saleId: String) = invoice(saleId) + "/cancel"
  fun fixOf(saleId: String) = invoice(saleId) + "/fix"
  const val RETURN_NONE = "sales/return/none"
  fun returnReceipt(docId: String) = "sales/returns/" + RoutePaths.encode(docId)
  fun returnPrint(docId: String) = returnReceipt(docId) + "/print"
  const val DRAWER = "sales/drawer"
  const val DRAWER_OPEN = "sales/drawer/open"
  const val DRAWER_CLOSE = "sales/drawer/close"
  const val MOVE_NO_SALE = "no_sale"
  fun drawerMove(noSale: Boolean) = RoutePaths.withQuery("sales/drawer/move", "mode" to if (noSale) MOVE_NO_SALE else "cash")
  const val DRAWER_X = "sales/drawer/report"
  /** گزارشِ Z ِ همین بستن */
  val DRAWER_Z: String get() = RoutePaths.withQuery(DRAWER_X, "kind" to "z")
  fun drawerShift(openId: String) = "sales/drawer/shift/" + RoutePaths.encode(openId)
}

/**
 *  گروهِ فروش (G5، `sales.md`): صندوق (r2/r3)، تسویه و رسید، فروشِ سریع، اسکن، تاریخچه،
 *  فاکتور، چاپ، برگشت، لغو و اصلاح، و صندوقِ نقد — هر برگه مسیرِ خودش. صندوق و فاکتورهای
 *  کامپیوتر (`DeskSale`/`DeskInvoices`، design-v2) تا اعلامِ هماهنگ‌کننده دست نمی‌خورند.
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
      VipGate(Str.saleRegister) {
        if (desk) {
          if (args.flag("scan")) LaunchedEffect(args.path) { PhoneNav.scanRequest.value = true }
          DeskSaleScreen(store = store, cartStore = cart, d = data, snackbar = snackbar) { code -> nav.open(ProductsPaths.new(barcode = code)) }
        } else SaleRoute()
      }
    },
    V3Route(SalesPaths.QUICK, RouteKind.PAGE, { StrSale.quickSale }, parent = SalesPaths.SALE, aliases = listOf("quick"), chrome = Chrome.OWN) {
      VipGate(Str.saleRegister) { QuickSaleRoute() }
    },
    V3Route(SalesPaths.CHECKOUT, RouteKind.SHEET, { StrSale.checkoutTitle }, parent = SalesPaths.SALE, chrome = Chrome.OWN) { CheckoutRoute() },
    V3Route("sale/checkout/customer", RouteKind.SHEET, { StrSale.pickCustomer }, parent = SalesPaths.CHECKOUT, chrome = Chrome.OWN) { CustomerRoute() },
    V3Route(SalesPaths.APPROVE, RouteKind.SHEET, { StrSale.discountApproval }, parent = SalesPaths.CHECKOUT, chrome = Chrome.OWN) { ApprovalRoute() },
    V3Route(SalesPaths.CREDIT, RouteKind.SHEET, { StrPos.creditOverTitle }, parent = SalesPaths.CHECKOUT, chrome = Chrome.OWN) { CreditApprovalRoute() },
    V3Route("sale/line/{i}", RouteKind.SHEET, { StrSale.lineDetails }, parent = SalesPaths.SALE, chrome = Chrome.OWN) { LineRoute() },
    V3Route(SalesPaths.FREE_LINE, RouteKind.SHEET, { StrSale.freeItem }, parent = SalesPaths.SALE, chrome = Chrome.OWN) { FreeLineRoute() },
    V3Route("sale/keypad/{productId}", RouteKind.SHEET, { StrSale.typeQty }, parent = SalesPaths.SALE, chrome = Chrome.OWN) { KeypadRoute() },
    //  اسکن: صندوق ⇒ پیوسته زیرِ همان برگه (`SaleScan`)؛ رسید ⇒ QR هم؛ بقیه کد را با `finish`
    //  به صدازننده برمی‌گردانند (بستن ⇒ null). کامپیوتر بی کادرِ دوربین.
    V3Route("scan", RouteKind.SHEET, { StrKit.scanBarcode }, chrome = Chrome.OWN) {
      when (args.query("for")) {
        SalesPaths.SCAN_SALE -> SaleScanRoute()
        SalesPaths.SCAN_RECEIPT -> ScanScreen(onCode = { code -> nav.finish(code) }, onClose = { back() }, title = StrSale.scanReceiptQr, overlay = !desk, qr = true)
        else -> ScanScreen(onCode = { code -> nav.finish(code) }, onClose = { back() }, overlay = !desk)
      }
    },
    V3Route(SalesPaths.HISTORY, RouteKind.TAB, { Str.salesHistory }, chrome = Chrome.OWN) {
      if (desk) DeskInvoicesScreen(store, cart, data, snackbar) { nav.switchTab(SalesPaths.SALE) }
      else HistoryRoute()
    },
    V3Route(SalesPaths.DRAWER, RouteKind.PAGE, { StrSale.drawerWord }, parent = SalesPaths.HISTORY, chrome = Chrome.OWN) { DrawerRoute() },
    V3Route(SalesPaths.DRAWER_OPEN, RouteKind.SHEET, { StrSale.openDrawer }, parent = SalesPaths.DRAWER, chrome = Chrome.OWN) { DrawerOpenRoute() },
    V3Route(SalesPaths.DRAWER_CLOSE, RouteKind.SHEET, { StrSale.closeDrawer }, parent = SalesPaths.DRAWER, chrome = Chrome.OWN) { DrawerCloseRoute() },
    V3Route("sales/drawer/move", RouteKind.SHEET, { StrSale.cashMove }, parent = SalesPaths.DRAWER, chrome = Chrome.OWN) { DrawerMoveRoute() },
    V3Route(SalesPaths.DRAWER_X, RouteKind.SHEET, { StrSale.xReportTitle }, parent = SalesPaths.DRAWER, chrome = Chrome.OWN) { DrawerXRoute() },
    V3Route("sales/drawer/shift/{id}", RouteKind.SHEET, { StrSale.drawerHistory }, parent = SalesPaths.DRAWER, chrome = Chrome.OWN) { DrawerShiftRoute() },
    V3Route(SalesPaths.RETURN_NONE, RouteKind.SHEET, { StrSale.noReceiptReturn }, parent = SalesPaths.HISTORY, chrome = Chrome.OWN) { NoReceiptReturnRoute() },
    V3Route("sales/returns/{docId}", RouteKind.SHEET, { StrSale.returnReceipts }, parent = SalesPaths.HISTORY, chrome = Chrome.OWN) { ReturnReceiptRoute() },
    V3Route("sales/returns/{docId}/print", RouteKind.SHEET, { StrSale.printReturnReceipt }, parent = "sales/returns/{docId}", chrome = Chrome.OWN) { ReturnPrintRoute() },
    V3Route("sales/{saleId}", RouteKind.PAGE, { Str.salesHistory }, parent = SalesPaths.HISTORY, chrome = Chrome.OWN) { InvoiceRoute() },
    V3Route("sales/{saleId}/print", RouteKind.SHEET, { StrSale.printInvoice }, parent = "sales/{saleId}", chrome = Chrome.OWN) { SalePrintRoute() },
    V3Route("sales/{saleId}/return", RouteKind.SHEET, { StrSale.returnWord }, parent = "sales/{saleId}", chrome = Chrome.OWN) { ReturnRoute() },
    V3Route("sales/{saleId}/cancel", RouteKind.SHEET, { StrSale.cancelSale }, parent = "sales/{saleId}", chrome = Chrome.OWN) { CancelRoute() },
    V3Route("sales/{saleId}/fix", RouteKind.SHEET, { StrSale.fixInvoice }, parent = "sales/{saleId}", chrome = Chrome.OWN) { FixRoute() },
    //  برگهٔ فاکتورِ نیمه‌کاره را پوسته خودش سرِ باز شدن نشان می‌دهد (`DraftPrompt`)؛ پیوندش به صندوق
    V3Route("draft", RouteKind.SHEET, { StrSale.draftTitle }) { LandOn(SalesPaths.SALE) },
  ),
)
