package ir.vil3ntec.tohid.ui.v3.online

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Storefront
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.courier.CourierHome
import ir.vil3ntec.tohid.ui.screens.DeliveryScreen
import ir.vil3ntec.tohid.ui.screens.OrdersScreen
import ir.vil3ntec.tohid.ui.screens.ShopChatScreen
import ir.vil3ntec.tohid.ui.screens.StorefrontScreen
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ فروشِ آنلاین (G8) */
object OnlinePaths {
  const val ORDERS = "orders"
  fun orders(tab: String? = null) = RoutePaths.withQuery(ORDERS, "tab" to tab)
  fun order(id: String) = "orders/" + RoutePaths.encode(id)
  const val NEW_ORDER = "orders/new"
  const val STOREFRONT = "storefront"
  fun storefront(tab: String? = null) = RoutePaths.withQuery(STOREFRONT, "tab" to tab)
  const val CHAT = "chat"
  fun conversation(id: String) = "chat/" + RoutePaths.encode(id)
  const val DELIVERY = "delivery"
  fun delivery(tab: String? = null) = RoutePaths.withQuery(DELIVERY, "tab" to tab)
  const val COURIER = "courier"
}

/**
 *  گروهِ فروشِ آنلاین (G8، `online.md`): سفارش‌ها، ویترین (+بازارچه)، گفت‌وگو، میزِ
 *  تحویل — هر کدام فقط وقتی سرور برای این دکان روشن گفته (`Need.*`). همان‌جا
 *  مسیرهای حالتِ پیک (`CourierModule`).
 */
val OnlineModule = AppModule(
  id = "online",
  title = { StrNav.onlineOrders },
  icon = Icons.Outlined.ShoppingBasket,
  entries = listOf(
    ModuleEntry("orders", { Str.orders }, OnlinePaths.ORDERS, Icons.Outlined.ShoppingBasket, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.SALES, V3Hue.ORANGE, needs = setOf(Need.ORDERS_LIVE), order = 10),
    ModuleEntry("storefront", { Str.storefront }, OnlinePaths.STOREFRONT, Icons.Outlined.Storefront, setOf(Place.MORE), MoreGroup.SALES, V3Hue.BRAND, needs = setOf(Need.STOREFRONT), order = 15),
    ModuleEntry("delivery", { StrNav.deliveryDesk }, OnlinePaths.DELIVERY, Icons.Outlined.DeliveryDining, setOf(Place.MORE), MoreGroup.SALES, V3Hue.BLUE, needs = setOf(Need.DELIVERY), order = 16),
    ModuleEntry("chat", { Str.customerChat }, OnlinePaths.CHAT, Icons.Outlined.Forum, setOf(Place.MORE), MoreGroup.SALES, V3Hue.PURPLE, needs = setOf(Need.CHAT), order = 50),
  ),
  routes = listOf(
    V3Route(OnlinePaths.ORDERS, RouteKind.TAB, { Str.orders }, needs = setOf(Need.ORDERS_LIVE)) { OrdersScreen(store, snackbar) { back() } },
    V3Route(OnlinePaths.NEW_ORDER, RouteKind.PAGE, { Str.orders }, parent = OnlinePaths.ORDERS, needs = setOf(Need.ORDERS_LIVE)) { LandOn(OnlinePaths.ORDERS) },
    V3Route("orders/{id}", RouteKind.PAGE, { Str.orders }, parent = OnlinePaths.ORDERS, needs = setOf(Need.ORDERS_LIVE)) { LandOn(OnlinePaths.ORDERS) },
    V3Route("orders/{id}/action", RouteKind.SHEET, { Str.orders }, parent = "orders/{id}", needs = setOf(Need.ORDERS_LIVE)) { LandOn(OnlinePaths.ORDERS) },
    V3Route(OnlinePaths.STOREFRONT, RouteKind.PAGE, { Str.storefront }, needs = setOf(Need.STOREFRONT)) { StorefrontScreen(snackbar) },
    V3Route("storefront/products/{id}", RouteKind.SHEET, { Str.storefront }, parent = OnlinePaths.STOREFRONT, needs = setOf(Need.STOREFRONT)) { LandOn(OnlinePaths.STOREFRONT) },
    V3Route(OnlinePaths.CHAT, RouteKind.PAGE, { Str.customerChat }, needs = setOf(Need.CHAT)) { ShopChatScreen { back() } },
    V3Route("chat/{conversationId}", RouteKind.PAGE, { Str.customerChat }, parent = OnlinePaths.CHAT, needs = setOf(Need.CHAT)) { LandOn(OnlinePaths.CHAT) },
    V3Route(OnlinePaths.DELIVERY, RouteKind.PAGE, { StrNav.deliveryDesk }, needs = setOf(Need.DELIVERY)) { DeliveryScreen(snackbar) { back() } },
    V3Route("delivery/assign/{orderId}", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/auto", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/missions/{id}/action", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/code", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/pickup/{orderId}", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/couriers/{id}/settle", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/couriers/{id}/ledger", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/couriers/{id}/cap", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
    V3Route("delivery/invoice/{id}", RouteKind.SHEET, { StrNav.deliveryDesk }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY)) { LandOn(OnlinePaths.DELIVERY) },
  ),
)

/**
 *  حالتِ پیک (G8): خانهٔ پیک همان `CourierHome`ِ امروز است؛ خروج از حسابِ پیک ⇐
 *  دروازهٔ ورود (`ModeState.signedOutOfCourier`). مأموریت‌ها و برگه‌هایشان امروز
 *  درونِ همان خانه‌اند.
 */
val CourierModule = AppModule(
  id = "courier",
  title = { AppMode.COURIER.title.text },
  icon = Icons.Outlined.DeliveryDining,
  modes = setOf(AppMode.COURIER),
  routes = listOf(
    V3Route(OnlinePaths.COURIER, RouteKind.TAB, { AppMode.COURIER.title.text }, chrome = Chrome.OWN) {
      CourierHome(onSignedOut = { ModeState.signedOutOfCourier() })
    },
    V3Route("courier/missions/{id}", RouteKind.PAGE, { AppMode.COURIER.title.text }, parent = OnlinePaths.COURIER) { LandOn(OnlinePaths.COURIER) },
    V3Route("courier/missions/{id}/reason", RouteKind.SHEET, { AppMode.COURIER.title.text }, parent = "courier/missions/{id}") { LandOn(OnlinePaths.COURIER) },
    V3Route("courier/missions/{id}/deliver", RouteKind.SHEET, { AppMode.COURIER.title.text }, parent = "courier/missions/{id}") { LandOn(OnlinePaths.COURIER) },
    V3Route("courier/missions/{id}/photo", RouteKind.SHEET, { AppMode.COURIER.title.text }, parent = "courier/missions/{id}") { LandOn(OnlinePaths.COURIER) },
  ),
)
