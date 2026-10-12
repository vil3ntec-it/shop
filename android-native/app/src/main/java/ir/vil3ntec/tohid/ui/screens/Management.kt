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
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop

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
  val sellers = remember(d, today) { ReportEngine.bySeller(d, monthFrom, today) }
  val recent = remember(d) { d.auditLog.sortedByDescending { it.createdAt }.take(8) }
  val canAdmin = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  val canMoney = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }

  //  از سرور — اگر وارد نشده یا اینترنت نیست، کارت همین را می‌گوید
  val signedIn = remember { Backend.isReady(context) }
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
    Text("همهٔ دکان در یک نگاه — هر کارت به صفحهٔ کاملش می‌رود", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)

    /* ------------------------------ عددهای اصلی ------------------------------ */
    val kpis = buildList {
      add(Triple("فروشِ امروز", "${money(todayR.net)} ؋", "${plain(todayR.count)} فاکتور"))
      add(Triple("فروشِ این ماه", "${money(monthR.net)} ؋", "${plain(monthR.count)} فاکتور"))
      if (canMoney) add(Triple(if (monthR.netProfit < 0) "ضررِ خالصِ ماه" else "سودِ خالصِ ماه", "${money(kotlin.math.abs(monthR.netProfit))} ؋", "پس از مصارف"))
      if (canMoney) add(Triple("ارزشِ انبار", "${money(stock.value)} ؋", "${plain(stock.products)} قلم کالا"))
      add(Triple("طلب از قرض‌داران", "${money(owed)} ؋", "${plain(d.debtors.count { ShopStore.debt(d, it.id) > 0 })} نفر"))
      if (canMoney) add(Triple("بدهی به تأمین‌کننده", "${money(weOwe)} ؋", "${plain(d.suppliers.size)} تأمین‌کننده"))
      add(Triple("نقدِ مورد انتظار در دخل", shift?.let { "${money(CashShift.totalsOf(d, it, System.currentTimeMillis()).expected)} ؋" } ?: "صندوق بسته", shift?.let { "باز از ${formatMillis(it.open.createdAt)}" } ?: "از «تاریخچهٔ فروش» باز کنید"))
      add(Triple("هشدارِ انبار", "${plain(stock.out + stock.low)}", "${plain(stock.out)} تمام‌شده · ${plain(stock.low)} کم"))
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
        HubCard("هشدارها", Icons.Filled.WarningAmber, "انبار", { onOpen("warehouse") }) {
          HubLine("تمام‌شده", plain(stock.out), Shop.colors.danger)
          HubLine("موجودیِ کم", plain(stock.low), Shop.colors.warning)
          HubLine("منقضی", plain(expiry.count { it.expired }), Shop.colors.danger)
          HubLine("نزدیک به انقضا", plain(expiry.count { !it.expired }), Shop.colors.warning)
          val late = d.debtors.count { ShopStore.debt(d, it.id) > 0 }
          HubLine("قرض‌دارِ بدهکار", plain(late), Shop.colors.text)
        }
      }
      add {
        HubCard("صندوق", Icons.Filled.PointOfSale, "صندوق و فاکتورها", { onOpen("sales") }) {
          if (shift == null) {
            Text("صندوق بسته است.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          } else {
            val t = CashShift.totalsOf(d, shift, System.currentTimeMillis())
            HubLine("اول صندوق", money(t.opening))
            HubLine("فروشِ نقدی", money(t.sales))
            HubLine("رسیدِ قرض‌داران", money(t.receipts))
            HubLine("مرجوعی و مصارف", "−" + money(t.returns + t.expenses + t.suppliers))
            HubLine("مورد انتظار", money(t.expected), Shop.colors.primary)
          }
        }
      }
      if (canMoney) add {
        HubCard("فروشنده‌ها (این ماه)", Icons.Filled.Leaderboard, "گزارش‌ها", { onOpen("reports") }) {
          if (sellers.isEmpty()) Text("فروشی ثبت نشده.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          sellers.take(6).forEach { HubLine("${it.seller} · ${plain(it.count)}", money(it.net)) }
        }
      }
      add {
        HubCard("دستگاه‌های حساب", Icons.Filled.Devices, if (canAdmin) "تنظیمات" else null, { onOpen("settings") }) {
          when {
            !signedIn -> Text("بی حساب کار می‌کنید — دفتر فقط روی همین دستگاه است.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            devices == null -> Text(if (offline) "سرور در دسترس نیست." else "…", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            else -> devices!!.take(6).forEach { dv ->
              HubLine(dv.label + if (dv.status == "revoked") " (بسته)" else "", dv.lastSeenAt?.let { formatMillis(it) } ?: "—")
            }
          }
        }
      }
      add {
        HubCard("کارمندان", Icons.Filled.Badge, "کارمندان", { onOpen("team") }) {
          when {
            !signedIn -> Text("برای کارمندان، وارد حساب شوید.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            members == null -> Text("…", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            members!!.isEmpty() -> Text("هنوز کارمندی نیست.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            else -> members!!.take(6).forEach { m ->
              HubLine(m.name.ifBlank { m.email.ifBlank { m.phone } }, when (m.role) { "owner" -> "صاحب"; "manager" -> "مدیر"; else -> "فروشنده" })
            }
          }
        }
      }
      add {
        HubCard("اشتراک", Icons.Filled.WorkspacePremium, "اشتراکِ من", { onOpen("myplan") }) {
          val s = sub
          when {
            !signedIn -> Text("بی حساب.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            s == null -> Text("…", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            else -> {
              HubLine("پلن", s.plan ?: "—")
              HubLine("وضعیت", when (s.status) { "active" -> "فعال"; "expired" -> "منقضی"; "suspended" -> "معلق"; else -> s.status },
                if (s.status == "active") Shop.colors.success else Shop.colors.danger)
              HubLine("روزِ مانده", plain(s.daysLeft), if (s.daysLeft <= 7) Shop.colors.warning else Shop.colors.text)
            }
          }
        }
      }
      if (canAdmin) add {
        HubCard("سابقهٔ اخیر", Icons.Filled.History, "سابقهٔ عملیات", { onOpen("audit") }) {
          if (recent.isEmpty()) Text("هنوز چیزی ثبت نشده.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          recent.forEach { a ->
            Text("${formatDate(a.date)} — ${a.notes}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.text,
              maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
          }
        }
      }
      add {
        HubCard("میان‌بُرها", Icons.Filled.Apps, null, {}) {
          val links = listOf(
            "sale" to "فروش", "warehouse" to "انبار", "products" to "محصولات", "purchasing" to "خرید",
            "reports" to "گزارش‌ها", "receipts" to "رسیدها", "expenses" to "مصارف", "debtors" to "قرض‌داران",
          ) + if (canAdmin) listOf("settings" to "تنظیمات و پشتیبان") else emptyList()
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
