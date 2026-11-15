package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrReach
import androidx.compose.material.icons.outlined.Schedule
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.displayMillis
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chevron
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** کارمندانِ دکان از سرور — بی حساب ⇒ «وارد نشده»، بی پاسخ ⇒ «اتصال نیست» */
@Composable
fun rememberAdminMembers(): AdminMembers {
  val context = LocalContext.current
  var m by remember { mutableStateOf<AdminMembers>(AdminMembers.Loading) }
  LaunchedEffect(Unit) {
    m = if (!Backend.isReady(context)) AdminMembers.SignedOut
    else Backend.shop(context).members().valueOrNull()?.let { AdminMembers.Count(it.members.size) } ?: AdminMembers.Offline
  }
  return m
}

/**
 *  «مرکزِ مدیریت» (`admin`): همهٔ دکان در یک نگاه — شبکهٔ کارت‌های عدد (دو ستون روی گوشی،
 *  سه/چهار روی پهن) که هر کدام صفحهٔ دقیقِ خودش را باز می‌کند، و فهرستِ کارمندان، سابقهٔ
 *  عملیات، اشتراک و تنظیمات. کارتِ پولی برای شاگرد نیست (همان سدِ امروز).
 */
@Composable
fun AdminScreen(
  d: ShopData,
  canSeeMoney: Boolean,
  canManage: Boolean,
  canAudit: Boolean,
  members: AdminMembers,
  onBack: (() -> Unit)?,
  onOpen: (String) -> Unit,
  wide: Boolean = false,
  today: String = todayIso(),
  /** STF-07/08 روشن (پرچمِ سرور) ⇒ ردیفِ «ساعتِ کار و پورسانت» */
  staffTime: Boolean = false,
) {
  val n = remember(d, today) { AdminModel.numbers(d, today, System.currentTimeMillis()) }
  val kpis = AdminModel.kpis(canSeeMoney)
  val columns = if (wide) 4 else 2
  val stagger = rememberV3Stagger()
  V3Scaffold(title = Str.adminCenter, subtitle = StrReports.adminCaption, onBack = onBack) { pad ->
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      //  ۶۳w (صاحب‌کار: «کادرهای بزرگ نه»): فهرستِ فشرده — گوشی چهار عددِ اصلی و بقیه زیرِ «… عددِ دیگر»؛ پهن همه
      ir.vil3ntec.tohid.ui.components.common.MetricRows(
        kpis.map { k -> kpiMetric(k, n) { onOpen(AdminModel.route(k)) } },
        top = if (columns > 2) Int.MAX_VALUE else ADMIN_TOP,
      )
      V3Section(null) {
        val rows = buildList {
          //  ۶۵r: رنگ همان رنگِ بخشِ مقصد در «بیشتر» (`V3Modules.hueOf`)؛ «تنظیمات» `SettingsHues.ROOT` (پیش‌تر خاکستری)
          if (canManage) add(AdminLink(StrReports.staff, Icons.Outlined.Badge, V3Modules.hueOf("team"), AccountPaths.TEAM, membersValue(members)))
          if (staffTime) add(AdminLink(StrReach.staffTime, Icons.Outlined.Schedule, V3Hue.TEAL, AnalyticsPaths.STAFF_TIME, null))
          //  «سابقهٔ عملیات» از ۶۳w در برنامه نیست (صاحب‌کار) — `canAudit` فقط برای سازگاریِ صدازننده‌ها می‌ماند
          add(AdminLink(StrReports.subscription, Icons.Outlined.WorkspacePremium, V3Modules.hueOf("myplan"), AccountPaths.SUBSCRIPTION, null))
          if (canManage) add(AdminLink(Str.settings, Icons.Outlined.Settings, ir.vil3ntec.tohid.ui.v3.settings.SettingsHues.ROOT, SettingsPaths.ROOT, null))
        }
        rows.forEachIndexed { i, l ->
          if (i > 0) V3Divider(start = ir.vil3ntec.tohid.ui.v3.V3NavRowSpec.DIVIDER_START)
          ir.vil3ntec.tohid.ui.v3.V3NavRow(l.title, icon = l.icon, hue = l.hue, value = l.value, onClick = { onOpen(l.route) })
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

private class AdminLink(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val hue: V3Hue?, val route: String, val value: String?)

private fun membersValue(m: AdminMembers): String = when (m) {
  AdminMembers.Loading -> StrReports.loading
  AdminMembers.Offline -> "— · ${StrReports.noConnection}"
  AdminMembers.SignedOut -> "— · ${StrReports.signedOut}"
  is AdminMembers.Count -> StrReports.people(m.n)
}

/** گوشی: چند عددِ اصلیِ مرکزِ مدیریت پیدا؛ بقیه با «بیشتر» */
private const val ADMIN_TOP = 4

/** یک ردیفِ فهرستِ فشرده: برچسب، عدد و زیرنویس — همان عددها و همان مقصدِ کاشیِ پیشین */
private fun kpiMetric(k: AdminKpi, n: AdminNumbers, onClick: () -> Unit): ir.vil3ntec.tohid.ui.components.common.Metric {
  val loss = k == AdminKpi.MONTH_PROFIT && n.monthProfit < 0
  val label = when (k) {
    AdminKpi.TODAY_SALES -> StrReports.todaySales
    AdminKpi.MONTH_SALES -> StrReports.monthSales
    AdminKpi.MONTH_PROFIT -> if (loss) StrReports.monthLoss else StrReports.monthProfit
    AdminKpi.STOCK_VALUE -> StrReports.stockValue
    AdminKpi.RECEIVABLE -> StrReports.owedByDebtors
    AdminKpi.PAYABLE -> StrReports.owedToSuppliers
    AdminKpi.CASH -> StrReports.expectedCash
    AdminKpi.STOCK_ALERT -> StrReports.stockAlert
  }
  val value: Double? = when (k) {
    AdminKpi.TODAY_SALES -> n.todayNet
    AdminKpi.MONTH_SALES -> n.monthNet
    AdminKpi.MONTH_PROFIT -> kotlin.math.abs(n.monthProfit)
    AdminKpi.STOCK_VALUE -> n.stockValue
    AdminKpi.RECEIVABLE -> n.owed
    AdminKpi.PAYABLE -> n.weOwe
    AdminKpi.CASH -> n.cashExpected
    AdminKpi.STOCK_ALERT -> (n.out + n.low).toDouble()
  }
  val caption = when (k) {
    AdminKpi.TODAY_SALES -> Str.invoicesCount(n.todayCount)
    AdminKpi.MONTH_SALES -> Str.invoicesCount(n.monthCount)
    AdminKpi.MONTH_PROFIT -> StrReports.afterExpenses
    AdminKpi.STOCK_VALUE -> StrReports.itemCount(n.stockProducts)
    AdminKpi.RECEIVABLE -> StrReports.people(n.owingPeople)
    AdminKpi.PAYABLE -> StrReports.suppliers(n.suppliers)
    AdminKpi.CASH -> n.cashOpenedAt?.let { StrReports.openSince(displayMillis(it)) } ?: StrReports.openFromHistory
    AdminKpi.STOCK_ALERT -> StrReports.outAndLow(n.out, n.low)
  }
  val text = when {
    value == null -> StrReports.drawerClosed
    k == AdminKpi.STOCK_ALERT -> value.toLong().toString()
    else -> afn(value)
  }
  return ir.vil3ntec.tohid.ui.components.common.Metric(label, text, caption, onClick = onClick)
}
