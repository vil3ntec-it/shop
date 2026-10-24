package ir.vil3ntec.tohid.ui.v3.shopper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.modes.ModeSwitcher
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/**
 *  مسیرهای حالتِ «خرید» — همان شکل‌هایی که `ShopperLinks.parse`ِ هستهٔ خرید
 *  (#355) می‌سازد. صفحه‌ها را گروهِ خرید روی همین کیت می‌سازد؛ امروز هر مسیر
 *  جای‌نگهدارِ آرامِ خودش را دارد تا پیوندها (QRِ دکان، پیگیریِ سفارش) کار کنند.
 */
object ShopperRoutePaths {
  const val HOME = "shopper/home"
  fun shop(slug: String, product: String? = null) = RoutePaths.withQuery("shopper/shop/" + RoutePaths.encode(slug), "product" to product)
  const val CART = "shopper/cart"
  fun cart(slug: String) = "shopper/cart/" + RoutePaths.encode(slug)
  const val CHECKOUT = "shopper/checkout"
  const val ORDERS = "shopper/orders"
  fun order(slug: String, token: String) = "shopper/order/" + RoutePaths.encode(slug) + "/" + RoutePaths.encode(token)
  const val ACCOUNT = "shopper/account"
}

val ShopperModule = AppModule(
  id = "shopper",
  title = { AppMode.SHOPPER.title.text },
  icon = Icons.Outlined.Storefront,
  modes = setOf(AppMode.SHOPPER),
  entries = listOf(
    ModuleEntry("shopper-home", { StrKit.shopperHome }, ShopperRoutePaths.HOME, Icons.Outlined.Storefront, setOf(Place.NAV), hue = V3Hue.BRAND, order = 0),
    ModuleEntry("shopper-cart", { StrKit.shopperCart }, ShopperRoutePaths.CART, Icons.Outlined.ShoppingCart, setOf(Place.NAV), order = 1),
    ModuleEntry("shopper-orders", { StrKit.shopperOrders }, ShopperRoutePaths.ORDERS, Icons.Outlined.ReceiptLong, setOf(Place.NAV), order = 2),
    ModuleEntry("shopper-account", { StrKit.shopperAccount }, ShopperRoutePaths.ACCOUNT, Icons.Outlined.AccountCircle, setOf(Place.NAV), order = 3),
  ),
  routes = listOf(
    V3Route(ShopperRoutePaths.HOME, RouteKind.TAB, { StrKit.shopperHome }, chrome = Chrome.OWN) { Placeholder(StrKit.shopperHome, Icons.Outlined.Storefront) },
    V3Route("shopper/shop/{slug}", RouteKind.PAGE, { StrKit.shopperShop }, parent = ShopperRoutePaths.HOME, chrome = Chrome.OWN) { ShopPlaceholder() },
    V3Route(ShopperRoutePaths.CART, RouteKind.TAB, { StrKit.shopperCart }, chrome = Chrome.OWN) { Placeholder(StrKit.shopperCart, Icons.Outlined.ShoppingCart) },
    V3Route("shopper/cart/{slug}", RouteKind.PAGE, { StrKit.shopperCart }, parent = "shopper/shop/{slug}", chrome = Chrome.OWN) { Placeholder(StrKit.shopperCart, Icons.Outlined.ShoppingCart) },
    V3Route(ShopperRoutePaths.CHECKOUT, RouteKind.PAGE, { StrKit.shopperCheckout }, parent = ShopperRoutePaths.CART, chrome = Chrome.OWN) { Placeholder(StrKit.shopperCheckout, Icons.Outlined.ShoppingCart) },
    V3Route(ShopperRoutePaths.ORDERS, RouteKind.TAB, { StrKit.shopperOrders }, chrome = Chrome.OWN) { Placeholder(StrKit.shopperOrders, Icons.Outlined.ReceiptLong) },
    V3Route("shopper/order/{id}", RouteKind.PAGE, { StrKit.shopperOrder }, parent = ShopperRoutePaths.ORDERS, chrome = Chrome.OWN) { Placeholder(StrKit.shopperOrder, Icons.Outlined.ReceiptLong) },
    V3Route("shopper/order/{slug}/{token}", RouteKind.PAGE, { StrKit.shopperOrder }, parent = ShopperRoutePaths.ORDERS, chrome = Chrome.OWN) { Placeholder(StrKit.shopperOrder, Icons.Outlined.ReceiptLong) },
    V3Route(ShopperRoutePaths.ACCOUNT, RouteKind.TAB, { StrKit.shopperAccount }, chrome = Chrome.OWN) {
      V3Scaffold(title = StrKit.shopperAccount, bottomReserve = bottomReserve) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
          ModeSwitcher()
        }
      }
    },
  ),
)

@Composable
private fun RouteScope.Placeholder(title: String, icon: ImageVector) {
  V3Scaffold(title = title, onBack = if (nav.stack.canPop) ({ back() }) else null, bottomReserve = bottomReserve) { pad ->
    Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
      V3EmptyState(icon, title, body = StrKit.comingSoonBody)
    }
  }
}

/** صفحهٔ دکان: نامِ دکان و بخش‌هایی که ماژول‌ها روی آن گذاشته‌اند (`ShopperSection`) */
@Composable
private fun RouteScope.ShopPlaceholder() {
  val slug = args["slug"].orEmpty()
  val sections = V3Modules.shopperSections(access)
  V3Scaffold(title = slug, subtitle = StrKit.shopperShop, onBack = { back() }, bottomReserve = bottomReserve) { pad ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
      V3EmptyState(Icons.Outlined.Storefront, slug, body = StrKit.comingSoonBody)
      if (sections.isNotEmpty()) V3Section(title = null) {
        sections.forEach { s -> V3ListRow(s.title(), icon = s.icon, onClick = { nav.open(s.route.replace("{slug}", RoutePaths.encode(slug))) }) }
      }
    }
  }
}
