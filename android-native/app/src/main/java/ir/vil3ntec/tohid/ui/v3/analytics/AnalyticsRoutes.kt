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
import ir.vil3ntec.tohid.core.i18n.StrBranches
import ir.vil3ntec.tohid.core.i18n.StrOnlineReport
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.ui.screens.rememberStaffTimeConfig
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.shell.ShellPaths
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.HomePager
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ گزارش و آمار (G6) */
object AnalyticsPaths {
  const val REPORTS = "reports"
  fun reports(tab: String? = null, range: String? = null) = RoutePaths.withQuery(REPORTS, "tab" to tab, "range" to range)
  fun stats(period: String? = null) = RoutePaths.withQuery("stats", "period" to period)
  fun audit(kind: String? = null) = RoutePaths.withQuery("audit", "kind" to kind)
  const val ADMIN = "admin"
  /** STF-07/08: ساعتِ کار و پورسانت — فقط با پرچمِ `shop.staff_time` (`StaffTimeHub`) */
  const val STAFF_TIME = "admin/staff-time"
  /** RPT-14: گزارشِ فروشِ اینترنتی — راهش فقط با پرچمِ `shop.orders.online_report` ِ سرور (`OnlineReportLive`) */
  const val ONLINE_REPORT = "reports/online"
  /** RPT-18: گزارشِ همهٔ شعبه‌ها — راهش فقط با پرچمِ `shop.branches_report` و فقط برای صاحب (`BranchesReportLive`) */
  const val BRANCHES_REPORT = "reports/branches"
}

/** پولِ دکان (سود، قیمتِ خرید) برای همین کاربر باز است؟ — همان `ShopRole.canSeeMoney` ِ امروز */
@Composable
private fun canSeeMoney(): Boolean {
  val context = LocalContext.current
  return remember { ShopRole.canSeeMoney(context) }
}

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
      //  CRM-15: خروجی/چاپِ برگهٔ مشتریان ⇐ «خروجیِ همهٔ مشتریان» در «چه کسی پرونده را دید» ِ هر پرونده
      val viewed = ir.vil3ntec.tohid.ui.screens.rememberCustomerAccessNote(store)
      val exported: (String) -> Unit = { key ->
        if (key in ir.vil3ntec.tohid.core.crm.CustomerAccess.CUSTOMER_SHEETS) {
          viewed(ir.vil3ntec.tohid.core.crm.CustomerAccess.ALL, ir.vil3ntec.tohid.core.crm.CustomerAccess.Kind.EXPORT)
        }
      }
      androidx.compose.runtime.CompositionLocalProvider(ir.vil3ntec.tohid.ui.screens.LocalReportExport provides exported) {
        if (desk) ReportsDesk(data, snackbar) { nav.open(it) }
        else ReportsScreen(
          d = data, canSeeMoney = canSeeMoney(), tabArg = args.query("tab"), rangeArg = args.query("range"),
          onBack = backOrNull, bottomReserve = bottomReserve, onOpen = { nav.open(it) },
        )
      }
    },
    //  گوشی: صفحهٔ دومِ ورقِ خانه (`HomePager.page = 1`) با همان دوره؛ کامپیوتر ورقِ خانه ندارد ⇒ صفحهٔ خودش
    V3Route("stats", RouteKind.PAGE, { StrReports.statsTitle }, chrome = Chrome.OWN) {
      if (desk) StatsPage(data, canSeeMoney(), args.query("period"), { nav.open(it) }, bottomReserve = 0.dp, embedded = false, onBack = backOrNull)
      else {
        LaunchedEffect(args.path) {
          StatsRequest.period.value = args.query("period")
          HomePager.page.value = 1
        }
        LandOn(ShellPaths.HOME)
      }
    },
    V3Route("audit", RouteKind.PAGE, { StrReports.auditTitle }, needs = setOf(Need.AUDIT), chrome = Chrome.OWN, deniedBody = { StrReports.auditDenied }) {
      AuditScreen(data, args.query("kind"), onBack = backOrNull, onOpen = { nav.open(it) })
    },
    V3Route(AnalyticsPaths.ADMIN, RouteKind.PAGE, { Str.adminCenter }, chrome = Chrome.OWN) {
      AdminScreen(
        d = data, canSeeMoney = canSeeMoney(), canManage = Need.MANAGE in access.granted, canAudit = Need.AUDIT in access.granted,
        members = rememberAdminMembers(), onBack = backOrNull, onOpen = { nav.open(it) }, wide = desk,
        staffTime = rememberStaffTimeConfig()?.enabled == true,
      )
    },
    V3Route(AnalyticsPaths.STAFF_TIME, RouteKind.PAGE, { StrReach.staffTime }, parent = AnalyticsPaths.ADMIN) { StaffTimePage(data) },
    V3Route(AnalyticsPaths.ONLINE_REPORT, RouteKind.PAGE, { StrOnlineReport.title }, parent = AnalyticsPaths.REPORTS) { OnlineReportPage() },
    V3Route(AnalyticsPaths.BRANCHES_REPORT, RouteKind.PAGE, { StrBranches.title }, parent = AnalyticsPaths.REPORTS) { BranchesReportPage() },
    V3Route("reports/export", RouteKind.SHEET, { Str.reports }, parent = AnalyticsPaths.REPORTS) { LandOn(AnalyticsPaths.REPORTS) },
    V3Route("reports/print", RouteKind.SHEET, { Str.reports }, parent = AnalyticsPaths.REPORTS) { LandOn(AnalyticsPaths.REPORTS) },
  ),
).also {
  HomePager.stats = { d, bottomReserve, _, _ -> HomeStats(d, bottomReserve) }
}
