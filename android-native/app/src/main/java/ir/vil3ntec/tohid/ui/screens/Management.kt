package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.model.DeviceDto
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.SubscriptionDto
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAdmin
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings

/**
 *  مرکزِ مدیریت — همه‌چیزِ دکان در یک صفحه.
 *
 *  ── چرا ───────────────────────────────────────────────────────────
 *  صاحبِ دکان برای دیدنِ «امروز چه شد» باید شش صفحه را می‌گشت: فروش،
 *  گزارش، انبار، قرض‌داران، صندوق، تنظیمات (دستگاه‌ها) و کارمندان. این‌جا
 *  عددهای اصلی، هشدارها، صندوق، فروشنده‌ها، دستگاه‌ها، کارمندان، اشتراک
 *  و سابقهٔ اخیر کنارِ هم‌اند و هر کارت یک‌راست به صفحهٔ کاملش می‌رود.
 *
 *  همهٔ عددها از همان موتورهایی می‌آیند که صفحه‌های خودشان (`ReportEngine`،
 *  `WarehouseEngine`، `CashShift`، `ExpiryEngine`) — عددِ این‌جا با عددِ
 *  آن صفحه هیچ‌وقت فرق نمی‌کند. دستگاه‌ها، کارمندان و اشتراک از سرور
 *  خوانده می‌شوند (بی اینترنت فقط «در دسترس نیست» می‌گوید).
 *
 *  روی کامپیوتر چهار ستونِ عدد و سه ستونِ کارت؛ روی گوشی یک‌ستونه.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrAdmin` (LOC-06)؛ نامِ دستگاه، کارمند، پلن و یادداشتِ سابقه داده‌اند.
 */
@Composable
fun ManagementScreen(d: ShopData, onOpen: (String) -> Unit) {
  val context = LocalContext.current
  val size = screenSize()
  val today = todayIso()
  val monthFrom = today.take(7) + "-01"

  val todayR = remember(d, today) { ReportEngine.sales(d, today, today) }
  val monthR = remember(d, today) { ReportEngine.sales(d, monthFrom, today) }
  val stock = remember(d) { WarehouseEngine.summary(d) }
  val owed = remember(d) { d.debtors.sumOf { ShopStore.debt(d, it.id).coerceAtLeast(0.0) } }
  val weOwe = remember(d) { d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id).coerceAtLeast(0.0) } }
  val expiry = remember(d, today) { ExpiryEngine.alerts(d, today) }
  val shift = remember(d) { CashShift.current(d) }
  //  PAY (v3 A4): با پرچم نقدِ مورد انتظار از هستهٔ pay-core (`PayFlow`)؛ خاموش ⇒ همان `CashShift`
  val payV2 = remember { ir.vil3ntec.tohid.data.ShopPolicy.payV2(context) }
  //  شمارشِ کور (PAY-13): با پرچم، شاگرد مبلغِ مورد انتظار را این‌جا هم نمی‌بیند
  val blind = remember { payV2 && ir.vil3ntec.tohid.data.ShopRole.isStaff(context) }
  fun expectedCash(s: CashShift.Shift): Double =
    if (payV2) ir.vil3ntec.tohid.data.PayFlow.expectedCash(d, s.open, System.currentTimeMillis())
    else CashShift.totalsOf(d, s, System.currentTimeMillis()).expected
  val sellers = remember(d, today) { ReportEngine.bySeller(d, monthFrom, today) }
  val recent = remember(d) { d.auditLog.sortedByDescending { it.createdAt }.take(8) }
  val canAdmin = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  val canMoney = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }

  //  از سرور — اگر وارد نشده یا اینترنت نیست، کارت همین را می‌گوید
  val signedIn = remember { Backend.isReady(context) }
  val staffTime = rememberStaffTimeConfig()
  var devices by remember { mutableStateOf<List<DeviceDto>?>(null) }
  var members by remember { mutableStateOf<List<MemberDto>?>(null) }
  var sub by remember { mutableStateOf<SubscriptionDto?>(null) }
  var offline by remember { mutableStateOf(false) }
  LaunchedEffect(signedIn) {
    if (!signedIn) return@LaunchedEffect
    val acc = Backend.account(context)
    acc.devices().valueOrNull()?.let { devices = it } ?: run { offline = true }
    Backend.shop(context).members().valueOrNull()?.let { members = it.members }
    acc.subscription().valueOrNull()?.let { sub = it }
  }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(StrAdmin.subtitle, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)

    /* ------------------------------ عددهای اصلی ------------------------------ */
    val kpis = buildList {
      add(Triple(StrHome.todaySales, "${money(todayR.net)} ؋", Str.invoicesCount(plain(todayR.count))))
      add(Triple(StrAdmin.monthSales, "${money(monthR.net)} ؋", Str.invoicesCount(plain(monthR.count))))
      if (canMoney) add(Triple(if (monthR.netProfit < 0) StrAdmin.monthLoss else StrAdmin.monthProfit, "${money(kotlin.math.abs(monthR.netProfit))} ؋", StrAdmin.afterExpenses))
      if (canMoney) add(Triple(StrAdmin.stockValue, "${money(stock.value)} ؋", StrAdmin.itemLines(plain(stock.products))))
      add(Triple(StrAdmin.owedByDebtors, "${money(owed)} ؋", StrAdmin.people(plain(d.debtors.count { ShopStore.debt(d, it.id) > 0 }))))
      if (canMoney) add(Triple(StrNav.supplierDebt, "${money(weOwe)} ؋", StrAdmin.suppliers(plain(d.suppliers.size))))
      add(Triple(StrAdmin.expectedInDrawer, shift?.let { if (blind) StrAdmin.blindCount else "${money(expectedCash(it))} ؋" } ?: StrNav.registerClosed, shift?.let { StrAdmin.openSince(formatMillis(it.open.createdAt)) } ?: StrAdmin.openFromHistory))
      add(Triple(StrAdmin.stockAlerts, "${plain(stock.out + stock.low)}", StrAdmin.outAndLow(plain(stock.out), plain(stock.low))))
    }
    val perRow = when (size) { Screen.WIDE -> 4; Screen.TABLET -> 3; else -> 2 }
    kpis.chunked(perRow).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { (label, value, hint) ->
          StatTile(label = label, value = value, hint = hint, modifier = Modifier.weight(1f), compact = size == Screen.PHONE)
        }
        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }

    /* ------------------------------ کارت‌ها ------------------------------ */
    val cards = buildList<@Composable () -> Unit> {
      add {
        HubCard(Str.alerts, Icons.Filled.WarningAmber, Str.warehouse, { onOpen("warehouse") }) {
          HubLine(Str.finished, plain(stock.out), Shop.colors.danger)
          HubLine(StrAdmin.lowStock, plain(stock.low), Shop.colors.warning)
          HubLine(StrAdmin.expired, plain(expiry.count { it.expired }), Shop.colors.danger)
          HubLine(StrAdmin.nearExpiry, plain(expiry.count { !it.expired }), Shop.colors.warning)
          val late = d.debtors.count { ShopStore.debt(d, it.id) > 0 }
          HubLine(StrAdmin.debtorsOwing, plain(late), Shop.colors.text)
        }
      }
      add {
        HubCard(StrAdmin.register, Icons.Filled.PointOfSale, StrAdmin.registerAndInvoices, { onOpen("sales") }) {
          if (shift == null) {
            Text(StrAdmin.registerClosedNote, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          } else if (payV2) {
            //  PAY (v3 A4): همان عددِ هسته — کیف پول و کارت نقدِ دخل نیستند
            HubLine(StrAdmin.openingFloat, money(shift.open.amount))
            HubLine(StrAdmin.expectedCash, if (blind) StrAdmin.blindCount else money(expectedCash(shift)), Shop.colors.primary)
          } else {
            val t = CashShift.totalsOf(d, shift, System.currentTimeMillis())
            HubLine(StrAdmin.openingFloat, money(t.opening))
            HubLine(StrAdmin.cashSales, money(t.sales))
            HubLine(StrAdmin.debtorReceipts, money(t.receipts))
            HubLine(StrAdmin.returnsAndExpenses, "−" + money(t.returns + t.expenses + t.suppliers))
            if (t.cashIn > 0 || t.cashOut > 0) HubLine(StrAdmin.cashInAndOut, (if (t.cashIn - t.cashOut < 0) "−" else "") + money(kotlin.math.abs(t.cashIn - t.cashOut)))
            HubLine(StrAdmin.expected, money(t.expected), Shop.colors.primary)
          }
        }
      }
      if (canMoney) add {
        HubCard(StrAdmin.sellersThisMonth, Icons.Filled.Leaderboard, Str.reports, { onOpen("reports") }) {
          if (sellers.isEmpty()) Text(StrAdmin.noSales, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          sellers.take(6).forEach { HubLine("${it.seller} · ${plain(it.count)}", money(it.net)) }
        }
      }
      add {
        HubCard(StrAdmin.accountDevices, Icons.Filled.Devices, if (canAdmin) Str.settings else null, { onOpen("settings") }) {
          when {
            !signedIn -> Text(StrAdmin.noAccountLocal, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            devices == null -> Text(if (offline) StrAdmin.serverUnreachable else "…", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            else -> devices!!.take(6).forEach { dv ->
              HubLine(dv.label + if (dv.status == "revoked") StrAdmin.revokedSuffix else "", dv.lastSeenAt?.let { formatMillis(it) } ?: "—")
            }
          }
        }
      }
      add {
        HubCard(StrNav.staffShort, Icons.Filled.Badge, StrNav.staffShort, { onOpen("team") }) {
          when {
            !signedIn -> Text(StrAdmin.signInForStaff, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            members == null -> Text("…", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            members!!.isEmpty() -> Text(StrAdmin.noStaffYet, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            else -> members!!.take(6).forEach { m ->
              HubLine(m.name.ifBlank { m.email.ifBlank { m.phone } }, when (m.role) { "owner" -> StrAdmin.roleOwner; "manager" -> StrNav.roleManager; else -> StrAdmin.roleSeller })
            }
          }
        }
      }
      add {
        HubCard(StrNav.subscription, Icons.Filled.WorkspacePremium, Str.mySubscription, { onOpen("myplan") }) {
          val s = sub
          when {
            !signedIn -> Text(StrAdmin.noAccount, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            s == null -> Text("…", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            else -> {
              HubLine(StrAdmin.plan, s.plan ?: "—")
              HubLine(StrNav.status, when (s.status) { "active" -> StrSettings.active; "expired" -> StrAdmin.expired; "suspended" -> StrAdmin.suspended; else -> s.status },
                if (s.status == "active") Shop.colors.success else Shop.colors.danger)
              HubLine(StrAdmin.daysLeft, plain(s.daysLeft), if (s.daysLeft <= 7) Shop.colors.warning else Shop.colors.text)
            }
          }
        }
      }
      if (canAdmin) add {
        HubCard(StrAdmin.recentActivity, Icons.Filled.History, StrNav.auditLogAlt, { onOpen("audit") }) {
          if (recent.isEmpty()) Text(StrAdmin.nothingRecorded, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          recent.forEach { a ->
            Text("${displayDate(a.date)} — ${a.notes}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.text,
              maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
          }
        }
      }
      //  STF-07/STF-08: فقط با پرچمِ روشنِ `shop.staff_time` (`StaffTimeAndroid.kt`)؛ خاموش ⇒ فهرستِ کارت‌ها همان پیشین
      staffTime?.takeIf { it.enabled }?.let { cfg -> add { StaffTimeHub(d, monthFrom, today, cfg) } }
      add {
        HubCard(StrNav.shortcuts, Icons.Filled.Apps, null, {}) {
          val links = listOf(
            "sale" to Str.sale, "warehouse" to Str.warehouse, "products" to Str.products, "purchasing" to Str.purchases,
            "reports" to Str.reports, "receipts" to Str.receipts, "expenses" to Str.expenses, "debtors" to Str.debtors,
          ) + if (canAdmin) listOf("settings" to StrAdmin.settingsAndBackup) else emptyList()
          links.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              row.forEach { (id, label) ->
                Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.primary,
                  modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(Shop.colors.primary.copy(alpha = 0.08f)).clickable { onOpen(id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp))
              }
              repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
          }
        }
      }
    }
    val cols = when (size) { Screen.WIDE -> 3; Screen.TABLET -> 2; else -> 1 }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      for (c in 0 until cols) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          cards.filterIndexed { i, _ -> i % cols == c }.forEach { it() }
        }
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun HubCard(
  title: String,
  icon: ImageVector,
  action: String?,
  onAction: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Panel {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = Shop.colors.primary, modifier = Modifier.size(19.dp))
      Spacer(Modifier.width(8.dp))
      Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      if (action != null) TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelMedium) }
    }
    Spacer(Modifier.height(6.dp))
    content()
  }
}

@Composable
private fun HubLine(label: String, value: String, tint: Color = Shop.colors.text) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold)
  }
}
