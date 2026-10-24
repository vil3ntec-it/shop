package ir.vil3ntec.tohid.ui.v3.sales.quick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.PromoLive
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartFeedback

/**
 *  چسبِ مسیرِ `sale/quick`: سبدِ زنده، جمع با قیمتِ پس از تخفیف‌های خودکار (`PromoLive` +
 *  `PromoEngine.priced`، خاموش ⇒ همان سبد) و هر کار به مسیرِ خودش — صفحه‌کلید، اسکن،
 *  دسته‌ها (`products/categories`)، کالای تازه، پرداخت و سبدِ کامل.
 */
@Composable
fun RouteScope.QuickSaleRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val lines by cart.lines.collectAsState()
  val promo = PromoLive.rememberOutcome(data, lines)
  val total = SalesEngine.cartTotal(data, PromoEngine.priced(lines, promo))
  //  کالای حذف‌شده در سبد نمی‌ماند (همان پاک‌سازیِ صندوق)
  LaunchedEffect(data.products.size, data.debtors.size) { cart.prune(data) }
  val index = remember(data.products) { ShopStore.barcodeIndex(data) }
  val feedback = remember(cart, toasts, snackbar, scope) {
    CartFeedback(context, cart, toasts, snackbar, scope) { code -> nav.open(ProductsPaths.new(barcode = code)) }
  }
  QuickSaleScreen(
    d = data,
    cart = lines,
    total = total,
    onBack = backOrNull,
    actions = QuickSaleActions(
      tap = { id, weighed -> if (weighed) nav.open(SalesPaths.keypad(id)) else feedback.addOne(data, id) },
      minus = { id -> feedback.minusOne(data, id) },
      hold = { id -> nav.open(SalesPaths.keypad(id)) },
      scan = { nav.openForResult(SalesPaths.scan(SalesPaths.SCAN_QUICK)) { code -> code?.let { feedback.scan(data, index, it) } } },
      categories = { nav.open(ProductsPaths.categories()) },
      newProduct = { nav.open(ProductsPaths.new()) },
      pay = { nav.open(SalesPaths.CHECKOUT) },
      openCart = { nav.switchTab(SalesPaths.SALE) },
    ),
  )
}
