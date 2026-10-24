package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.shell.ShellPaths
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.HomePager
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ گزارش و آمار (G6) */
object AnalyticsPaths {
  const val REPORTS = "reports"
  fun reports(tab: String? = null, range: String? = null) = RoutePaths.withQuery(REPORTS, "tab" to tab, "range" to range)
  fun stats(period: String? = null) = RoutePaths.withQuery("stats", "period" to period)
  fun audit(kind: String? = null) = RoutePaths.withQuery("audit", "kind" to kind)
  const val ADMIN = "admin"
}

/** پولِ دکان (سود، قیمتِ خرید) برای همین کاربر باز است؟ — همان `ShopRole.canSeeMoney` ِ امروز */
@Composable
private fun canSeeMoney(): Boolean {
  val context = LocalContext.current
  return remember { ShopRole.canSeeMoney(context) }
}

private fun RouteScope.backOrNull(): (() -> Unit)? = if (nav.canBack) ({ back() }) else null

/** گروهِ گزارش‌ها (G6، `analytics.md`): گزارش‌ها، آمار، سابقهٔ عملیات و مرکزِ مدیریت؛ آمار را در ورقِ خانه هم می‌گذارد */
val AnalyticsModule = AppModule(
  id = "analytics",
  title = { Str.reports },
  icon = Icons.Outlined.InsertChart,
  entries = listOf(
    ModuleEntry("reports", { Str.reports }, AnalyticsPaths.REPORTS, Icons.Outlined.InsertChart, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.MONEY, V3Hue.TEAL, order = 20),
    ModuleEntry("admin", { Str.adminCenter }, AnalyticsPaths.ADMIN, Icons.Outlined.AdminPanelSettings, setOf(Place.MORE), MoreGroup.SHOP, V3Hue.BRAND, order = 10),
    ModuleEntry("audit", { Str.auditLog }, "audit", Icons.AutoMirrored.Outlined.ManageSearch, setOf(Place.MORE), MoreGroup.SHOP, V3Hue.PURPLE, needs = setOf(Need.MANAGE), order = 30),
  ),
  routes = listOf(
    V3Route(AnalyticsPaths.REPORTS, RouteKind.TAB, { Str.reports }, chrome = Chrome.OWN) {
      if (Platform.DESKTOP) ReportsDesk(data, snackbar)
      else ReportsScreen(
        d = data, canSeeMoney = canSeeMoney(), tabArg = args.query("tab"), rangeArg = args.query("range"),
        onBack = backOrNull(), bottomReserve = bottomReserve, onOpen = { nav.open(it) },
      )
    },
    //  گوشی: صفحهٔ دومِ ورقِ خانه (`HomePager.page = 1`) با همان دوره؛ کامپیوتر ورقِ خانه ندارد ⇒ صفحهٔ خودش
    V3Route("stats", RouteKind.PAGE, { StrReports.statsTitle }, chrome = Chrome.OWN) {
      if (Platform.DESKTOP) StatsPage(data, canSeeMoney(), args.query("period"), { nav.open(it) }, bottomReserve = 0.dp, embedded = false, onBack = { back() })
      else {
        LaunchedEffect(args.path) {
          StatsRequest.period.value = args.query("period")
          HomePager.page.value = 1
        }
        LandOn(ShellPaths.HOME)
      }
    },
    V3Route("audit", RouteKind.PAGE, { StrReports.auditTitle }, needs = setOf(Need.AUDIT), chrome = Chrome.OWN, deniedBody = { StrReports.auditDenied }) {
      AuditScreen(data, args.query("kind"), onBack = { back() }, onOpen = { nav.open(it) })
    },
    V3Route(AnalyticsPaths.ADMIN, RouteKind.PAGE, { Str.adminCenter }, chrome = Chrome.OWN) {
      AdminScreen(
        d = data, canSeeMoney = canSeeMoney(), canManage = Need.MANAGE in access.granted, canAudit = Need.AUDIT in access.granted,
        members = rememberAdminMembers(), onBack = { back() }, onOpen = { nav.open(it) }, wide = wide,
      )
    },
    V3Route("reports/export", RouteKind.SHEET, { Str.reports }, parent = AnalyticsPaths.REPORTS) { LandOn(AnalyticsPaths.REPORTS) },
    V3Route("reports/print", RouteKind.SHEET, { Str.reports }, parent = AnalyticsPaths.REPORTS) { LandOn(AnalyticsPaths.REPORTS) },
  ),
).also {
  HomePager.stats = { d, bottomReserve, _, _ -> HomeStats(d, bottomReserve) }
}
