package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.analytics.AdminMembers
import ir.vil3ntec.tohid.ui.v3.analytics.AdminScreen
import ir.vil3ntec.tohid.ui.v3.analytics.AuditScreen
import ir.vil3ntec.tohid.ui.v3.analytics.ReportTab
import ir.vil3ntec.tohid.ui.v3.analytics.ReportsScreen
import ir.vil3ntec.tohid.ui.v3.analytics.StatsPage

/**
 *  عکس‌های گروهِ G6 گزارش: هر زبانهٔ گزارش با داده و خالی، ضرر، برگهٔ ماه، شاگرد (بی پول)؛
 *  آمار (روز، ماه، خالی)؛ سابقهٔ عملیات (همه، فیلتر، خالی)؛ مرکزِ مدیریت (صاحب، شاگرد، بی اتصال).
 *  دادهٔ خالی و «ضرر» فقط برای عکس ساخته می‌شوند (دفترِ نمایشیِ CI + یک مصرفِ بزرگ).
 */
object AnalyticsShots : GroupShots {
  override val group = "analytics"

  @Composable
  private fun data(store: ShopStore): ShopData {
    val d by store.data.collectAsState()
    return d
  }

  override val shots = buildList {
    ReportTab.entries.forEach { t ->
      add(Shot("reports-${t.id}", route = "reports?tab=${t.id}"))
      add(Shot("reports-${t.id}-empty", content = { ReportsScreen(ShopData(), true, t.id, null, null, 0.dp, {}) }))
    }
    add(Shot("reports-loss", content = { store ->
      val d = data(store)
      val loss = d.copy(expenses = d.expenses + Expense("shot-loss", title = "کرایه", amount = 250_000.0, date = todayIso()))
      ReportsScreen(loss, true, "profit", "month", null, 0.dp, {})
    }))
    add(Shot("reports-month-picker", content = { store -> ReportsScreen(data(store), true, "profit", "jmonth", null, 0.dp, {}, startPicker = true) }))
    add(Shot("reports-staff", content = { store -> ReportsScreen(data(store), false, "products", null, null, 0.dp, {}) }))
    add(Shot("stats", content = { store -> StatsPage(data(store), true, "day", {}, 0.dp, embedded = true) }))
    add(Shot("stats-month", content = { store -> StatsPage(data(store), true, "month", {}, 0.dp, embedded = true) }))
    add(Shot("stats-empty", content = { StatsPage(ShopData(), true, "day", {}, 0.dp, embedded = false, onBack = {}) }))
    add(Shot("audit", route = "audit"))
    add(Shot("audit-filtered", route = "audit?kind=sale"))
    add(Shot("audit-empty", content = { AuditScreen(ShopData(), null, onBack = {}, onOpen = {}) }))
    add(Shot("admin", route = "admin"))
    add(Shot("admin-staff", content = { store ->
      AdminScreen(data(store), canSeeMoney = false, canManage = false, canAudit = false, members = AdminMembers.SignedOut, onBack = {}, onOpen = {})
    }))
    add(Shot("admin-offline", content = { store ->
      AdminScreen(data(store), canSeeMoney = true, canManage = true, canAudit = true, members = AdminMembers.Offline, onBack = {}, onOpen = {})
    }))
  }
}
