package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.GiftCardPref
import ir.vil3ntec.tohid.data.LayawayPref
import ir.vil3ntec.tohid.data.LineSellerPref
import ir.vil3ntec.tohid.data.QuotePref
import ir.vil3ntec.tohid.data.QuoteStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.SpecialOrderPref
import ir.vil3ntec.tohid.data.TillReceivePref
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.components.pos.DeliverLaterPrefs
import ir.vil3ntec.tohid.ui.components.pos.HeldInvoicePrefs
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.components.pos.PosNotesPrefs
import ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.LocalV3NavSearch
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteArgs
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.ShellFrame
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleRoute
import java.io.File

/**
 *  صندوقِ گوشی درونِ همان قراردادِ پوستهٔ `AppRoot` (`ShellFrame`: نوارِ شناور فقط جایی که پوسته می‌کشد، پایینِ صفحه، جست‌وجوی نوار) —
 *  ولی خودِ `SaleRoute` بی سدِ اشتراک (`VipGate` در آزمون صندوق را قفل نشان می‌دهد). برای آزمون‌ها و عکس‌های پیش/پسِ صندوق.
 */
internal object TillShellHarness {
  val navRoutes: Set<String> = NavLayouts.DEFAULT.ids.mapNotNull { NavCatalog.of(it)?.route?.substringBefore('?') }.toSet() + "home"

  /** پشتهٔ برنامه: خانه ⇐ صندوق (همان باز کردن از خانه) */
  fun nav(): V3Nav = V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n -> n.isTab = { it in navRoutes }; n.openDeep("sale") }

  @Composable
  fun Shell(nav: V3Nav, store: ShopStore, cart: CartStore, dark: Boolean) {
    val data by store.data.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val toasts = rememberV3Toasts(snackbar)
    val shell = ShellContext(
      store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
      theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, onTheme = {},
      bottomReserve = PhoneNav.RESERVE, wide = false,
      access = Access.all(AppMode.SHOP, emptySet()),
      navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
    )
    val page = RoutePaths.normalize(nav.page.substringBefore('?'))
    val cartOpen by PhoneCartUi.sheetOpen
    val bar = ShellFrame.bar(
      phone = true, page = page, barTabs = navRoutes, sections = V3Modules.sections, gate = false,
      cartOpen = cartOpen, sheetOnTop = nav.current != nav.page, keyboard = false,
    )
    val navSearch = ShellFrame.navSearch(true, page, navRoutes, V3Modules.sections, false, NavCatalog.SEARCH in NavLayouts.DEFAULT.ids)
    CompositionLocalProvider(
      LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false, LocalV3NavSearch provides navSearch,
    ) {
      Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        Box(Modifier.fillMaxSize().padding(bottom = ShellFrame.pageBottom(true, bar, page, PhoneNav.RESERVE, 0.dp))) {
          val scope = remember(nav, shell) { RouteScope(RouteArgs("sale", emptyMap()), nav, shell) }
          scope.SaleRoute()
        }
        if (ShellFrame.navVisible(bar)) {
          PhoneNavBar(NavLayouts.DEFAULT, page, onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter))
        }
        V3SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
      }
    }
  }

  /** همهٔ ابزارهای سبد روشن (همان تنظیم‌های دستگاه) — [off] برمی‌گرداند */
  fun allTools(on: Boolean = true) {
    val ctx = DesktopContext
    QuotePref.set(ctx, on)
    SpecialOrderPref.set(ctx, on)
    GiftCardPref.set(ctx, on)
    LayawayPref.set(ctx, on)
    PriceListPrefs.set(ctx, on)
    TillReceivePref.set(ctx, on)
    LineSellerPref.set(ctx, on)
    DeliverLaterPrefs.set(ctx, on)
    HeldInvoicePrefs.set(ctx, on)
    OrdersLive.config.value = if (on) OrdersConfigDto(flag = true, entitled = true, enabled = true, permissions = OrderPermsDto(create = true)) else null
  }

  /** همه به پیش‌فرض: ابزارها خاموش، یادداشت روشن (پیش‌فرضِ خودش)، بی پیش‌فاکتور و بی سبد */
  fun reset() {
    allTools(false)
    PosNotesPrefs.set(DesktopContext, true)
    File(DesktopContext.filesDir, QuoteStore.FILE).delete()
    File(DesktopContext.filesDir, CartStore.FILE).delete()
    PhoneCartUi.sheetOpen.value = false
  }
}
