package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.InsertChart
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.AuditLogScreen
import ir.vil3ntec.tohid.ui.screens.DeskReportsScreen
import ir.vil3ntec.tohid.ui.screens.ManagementScreen
import ir.vil3ntec.tohid.ui.screens.ReportsScreen
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
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
}

/** گروهِ گزارش‌ها (G6، `analytics.md`): گزارش‌ها، آمار، سابقهٔ عملیات و مرکزِ مدیریت */
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
    V3Route(AnalyticsPaths.REPORTS, RouteKind.TAB, { Str.reportsAlt }) {
      if (desk) DeskReportsScreen(data, snackbar) else ReportsScreen(data)
    },
    //  آمارِ r8 صفحهٔ دومِ خانه است
    V3Route("stats", RouteKind.PAGE, { Str.reports }) { LandOn("home") },
    V3Route("audit", RouteKind.PAGE, { Str.auditLog }, needs = setOf(Need.AUDIT)) { AuditLogScreen(data) },
    V3Route(AnalyticsPaths.ADMIN, RouteKind.PAGE, { Str.adminCenter }) { ManagementScreen(data) { nav.open(it) } },
    V3Route("reports/export", RouteKind.SHEET, { Str.reports }, parent = AnalyticsPaths.REPORTS) { LandOn(AnalyticsPaths.REPORTS) },
    V3Route("reports/print", RouteKind.SHEET, { Str.reports }, parent = AnalyticsPaths.REPORTS) { LandOn(AnalyticsPaths.REPORTS) },
  ),
)
