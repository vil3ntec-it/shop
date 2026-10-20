package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.CommandPalette
import ir.vil3ntec.tohid.ui.screens.DashboardScreen
import ir.vil3ntec.tohid.ui.screens.DeskDashboardScreen
import ir.vil3ntec.tohid.ui.screens.PhoneSearchScreen
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths

/** مسیرهای پوسته (G10) */
object ShellPaths {
  const val HOME = "home"
  fun notifications(tab: String? = null) = RoutePaths.withQuery("notifications", "tab" to tab)
  fun search(q: String? = null, scan: Boolean = false) = RoutePaths.withQuery("search", "q" to q, "scan" to if (scan) "1" else null)
  const val PALETTE = "palette"
}

/**
 *  پوسته (G10، `shell.md`): خانه (r7/r6/r8)، هشدارها، جست‌وجو و فهرستِ فرمانِ
 *  کامپیوتر. `notifications` امروز صفحهٔ موقتِ `NotificationsStub` است.
 */
val ShellModule = AppModule(
  id = "shell",
  title = { Str.home },
  icon = Icons.Outlined.Home,
  entries = listOf(
    ModuleEntry("home", { Str.home }, ShellPaths.HOME, Icons.Outlined.Home, setOf(Place.NAV), order = 0),
    ModuleEntry("search", { Str.search }, "search", Icons.Outlined.Search, setOf(Place.NAV), order = 1),
  ),
  routes = listOf(
    V3Route(ShellPaths.HOME, RouteKind.TAB, { Str.home }, aliases = listOf("dashboard"), chrome = Chrome.OWN) {
      if (Platform.DESKTOP) DeskDashboardScreen(data, { nav.open(it) }) { id -> nav.open(ProductsPaths.item(id)) }
      else DashboardScreen(data, bottomReserve = bottomReserve) { nav.open(it) }
    },
    V3Route("notifications", RouteKind.PAGE, { StrKit.notifications }, chrome = Chrome.OWN) { NotificationsStub() },
    V3Route("search", RouteKind.TAB, { Str.search }, chrome = Chrome.OWN) {
      PhoneSearchScreen(
        d = data,
        onOpen = { nav.open(it) },
        onProduct = { id -> nav.open(ProductsPaths.item(id)) },
        onBack = { back() },
        initialQuery = args.query("q").orEmpty(),
        bottomReserve = 0.dp,
      )
    },
    V3Route(ShellPaths.PALETTE, RouteKind.SHEET, { Str.search }, chrome = Chrome.OWN) {
      CommandPalette(data, onDismiss = { back() }) { hit ->
        when (hit.kind) {
          "product" -> nav.replace(ProductsPaths.item(hit.id))
          "debtor" -> nav.replace(MoneyPaths.debtor(hit.id))
          else -> nav.replace(hit.id)
        }
      }
    },
  ),
)
