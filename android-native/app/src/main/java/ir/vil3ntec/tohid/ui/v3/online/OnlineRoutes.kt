package ir.vil3ntec.tohid.ui.v3.online

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Storefront
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.courier.CourierHome
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.online.chat.ChatRoute
import ir.vil3ntec.tohid.ui.v3.online.chat.ChatThreadRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.CourierSheetRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryAssignRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryAutoRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryCodeRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryInvoiceRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryPickupRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryRoute
import ir.vil3ntec.tohid.ui.v3.online.delivery.MissionActionRoute
import ir.vil3ntec.tohid.ui.v3.online.orders.NewOrderRoute
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderActionRoute
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderDetailRoute
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersRoute
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontModel
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontProductRoute
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontRoute

/** مسیرهای گروهِ فروشِ آنلاین (G8) */
object OnlinePaths {
  const val ORDERS = "orders"
  fun orders(tab: String? = null) = RoutePaths.withQuery(ORDERS, "tab" to tab)
  fun order(id: String) = "orders/" + RoutePaths.encode(id)
  fun action(id: String, to: String) = RoutePaths.withQuery(order(id) + "/action", "to" to to)
  const val NEW_ORDER = "orders/new"
  const val STOREFRONT = "storefront"
  fun storefront(tab: String? = null) = RoutePaths.withQuery(STOREFRONT, "tab" to tab)
  fun storefrontProduct(id: String) = "storefront/products/" + RoutePaths.encode(id)
  const val CHAT = "chat"
  fun conversation(id: String) = "chat/" + RoutePaths.encode(id)
  const val DELIVERY = "delivery"
  fun delivery(tab: String? = null) = RoutePaths.withQuery(DELIVERY, "tab" to tab)
  fun deliveryAssign(orderId: String) = "delivery/assign/" + RoutePaths.encode(orderId)
  const val DELIVERY_AUTO = "delivery/auto"
  fun missionAction(id: String, act: String) = RoutePaths.withQuery("delivery/missions/" + RoutePaths.encode(id) + "/action", "act" to act)
  const val DELIVERY_CODE = "delivery/code"
  fun deliveryPickup(orderId: String) = "delivery/pickup/" + RoutePaths.encode(orderId)
  /** `kind`: settle · ledger · cap */
  fun courierSheet(id: String, kind: String) = "delivery/couriers/" + RoutePaths.encode(id) + "/" + kind
  fun deliveryInvoice(company: String) = "delivery/invoice/" + RoutePaths.encode(company)
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
    V3Route(OnlinePaths.ORDERS, RouteKind.TAB, { StrOnline.ordersTitle }, needs = setOf(Need.ORDERS_LIVE), chrome = Chrome.OWN) { OrdersRoute() },
    V3Route(OnlinePaths.NEW_ORDER, RouteKind.PAGE, { StrOnline.manualOrder }, parent = OnlinePaths.ORDERS, needs = setOf(Need.ORDERS_LIVE), chrome = Chrome.OWN) { NewOrderRoute() },
    V3Route("orders/{id}", RouteKind.PAGE, { StrOnline.ordersTitle }, parent = OnlinePaths.ORDERS, needs = setOf(Need.ORDERS_LIVE), chrome = Chrome.OWN) {
      OrderDetailRoute(args["id"].orEmpty())
    },
    V3Route("orders/{id}/action", RouteKind.SHEET, { StrOnline.ordersTitle }, parent = "orders/{id}", needs = setOf(Need.ORDERS_LIVE), chrome = Chrome.OWN) { OrderActionRoute() },
    V3Route(OnlinePaths.STOREFRONT, RouteKind.PAGE, { StrOnline.sfTitle }, needs = setOf(Need.STOREFRONT), chrome = Chrome.OWN) { StorefrontRoute() },
    V3Route("storefront/products/{id}", RouteKind.SHEET, { StrOnline.sfTitle }, parent = OnlinePaths.storefront(StorefrontModel.PRODUCTS), needs = setOf(Need.STOREFRONT), chrome = Chrome.OWN) {
      StorefrontProductRoute()
    },
    V3Route(OnlinePaths.CHAT, RouteKind.PAGE, { StrOnline.ctTitle }, needs = setOf(Need.CHAT), chrome = Chrome.OWN) { ChatRoute() },
    V3Route("chat/{conversationId}", RouteKind.PAGE, { StrOnline.ctTitle }, parent = OnlinePaths.CHAT, needs = setOf(Need.CHAT), chrome = Chrome.OWN) {
      ChatThreadRoute(args["conversationId"].orEmpty())
    },
    V3Route(OnlinePaths.DELIVERY, RouteKind.PAGE, { StrOnline.dlTitle }, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { DeliveryRoute() },
    V3Route("delivery/assign/{orderId}", RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { DeliveryAssignRoute() },
    V3Route(OnlinePaths.DELIVERY_AUTO, RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { DeliveryAutoRoute() },
    V3Route("delivery/missions/{id}/action", RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { MissionActionRoute() },
    V3Route(OnlinePaths.DELIVERY_CODE, RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { DeliveryCodeRoute() },
    V3Route("delivery/pickup/{orderId}", RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { DeliveryPickupRoute() },
    V3Route("delivery/couriers/{id}/settle", RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { CourierSheetRoute("settle") },
    V3Route("delivery/couriers/{id}/ledger", RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { CourierSheetRoute("ledger") },
    V3Route("delivery/couriers/{id}/cap", RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { CourierSheetRoute("cap") },
    V3Route("delivery/invoice/{id}", RouteKind.SHEET, { StrOnline.dlTitle }, parent = OnlinePaths.DELIVERY, needs = setOf(Need.DELIVERY), chrome = Chrome.OWN) { DeliveryInvoiceRoute() },
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
