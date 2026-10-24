package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.DeskDashboardScreen
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای پوسته (G10) */
object ShellPaths {
  const val HOME = "home"
  fun notifications(tab: String? = null) = RoutePaths.withQuery("notifications", "tab" to tab)
  fun search(q: String? = null, scan: Boolean = false) = RoutePaths.withQuery("search", "q" to q, "scan" to if (scan) "1" else null)
  const val PALETTE = "palette"
}

/** بخش‌های جست‌وجو که همین کاربر حق دارد باز کند (نقش، پرچمِ سرور، پرچمِ ماژول) */
private fun RouteScope.allowedSections(): List<SearchIndex.Section> =
  SearchIndex.sections().filter { s -> nav.resolve(s.route)?.allowed(access) ?: (s.route == "more") }

/** اسکنِ بارکد برای جست‌وجو ⇐ همان کالا، یا فرمِ کالای تازه با همین بارکد */
private fun RouteScope.scanToSearch() {
  nav.openForResult(ShellTargets.SCAN) { code -> code?.takeIf { it.isNotBlank() }?.let { nav.open(SearchIndex.scanTarget(data, it)) } }
}

@Composable
private fun RouteScope.Search() {
  val sections = remember(access) { allowedSections() }
  LaunchedEffect(args.path) { if (args.flag("scan")) scanToSearch() }
  SearchScreen(
    d = data,
    sections = sections,
    initialQuery = args.query("q").orEmpty(),
    onOpen = { nav.open(it) },
    onScan = { scanToSearch() },
    onBack = if (nav.canBack) ({ back() }) else null,
    bottomReserve = bottomReserve,
  )
}

/**
 *  پوسته (G10، `shell.md`): خانه (r7/r6/r8 + تراکنش‌ها)، مرکزِ هشدارها، جست‌وجو، فهرستِ
 *  تراکنش‌ها و فهرستِ فرمانِ کامپیوتر — همه روی کیتِ نسخهٔ ۳ و هر کار مستقیم سرِ کادرِ خودش.
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
      if (Platform.DESKTOP) DeskDashboardScreen(data, { nav.open(it) }) { id -> nav.open(ShellTargets.product(id)) }
      else HomeScreen(data, onOpen = { nav.open(it) }, bottomReserve = bottomReserve)
    },
    V3Route("notifications", RouteKind.PAGE, { StrKit.notifications }, parent = ShellPaths.HOME, chrome = Chrome.OWN) {
      NotificationsScreen(data, args.query("tab"), onOpen = { nav.open(it) }, onBack = if (nav.canBack) ({ back() }) else null)
    },
    V3Route("search", RouteKind.TAB, { Str.search }, chrome = Chrome.OWN) { Search() },
    V3Route(TransactionPaths.LIST, RouteKind.PAGE, { StrHome.transactions }, parent = ShellPaths.HOME, chrome = Chrome.OWN) {
      TransactionsScreen(
        data,
        TransactionPaths.kindOf(args.query("kind")),
        onOpen = { nav.open(it) },
        onBack = if (nav.canBack) ({ back() }) else null,
        bottomReserve = 0.dp,
      )
    },
    V3Route(ShellPaths.PALETTE, RouteKind.SHEET, { Str.search }, chrome = Chrome.OWN) {
      val sections = remember(access) { allowedSections() }
      V3Palette(data, sections, onDismiss = { back() }) { hit -> nav.replace(hit.route) }
    },
  ),
)
