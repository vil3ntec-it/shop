package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun todayIso(): String =
  SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/**
 *  داشبورد — پنل‌به‌پنل همان چیزی که نسخهٔ وب نشان می‌دهد.
 *
 *  ترتیب و عنوان‌ها عمداً یکی است: چهار کاشیِ بالا، روند معاملات، قرض‌داران،
 *  مصارف اخیر، مصارف بر اساس دسته، وضعیت انبار و خلاصهٔ امروز. کسی که با
 *  نسخهٔ وب کار کرده، اینجا دنبال چیزی نمی‌گردد.
 *
 *  عددها هم با همان فرمول‌ها حساب می‌شوند و از موتورهای مشترک می‌آیند، نه
 *  از حسابِ جداگانهٔ این صفحه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  d: ShopData,
  theme: ThemeChoice = ThemeChoice.SYSTEM,
  onTheme: (ThemeChoice) -> Unit = {},
  onOpen: (String) -> Unit = {},
) {
  val today = todayIso()
  val monthPrefix = today.take(7)

  val todaySales = d.sales.filter { it.date == today && it.status != "cancelled" }
  val todayTotal = todaySales.sumOf { it.finalTotal }
  val todayExpense = d.expenses.filter { it.date == today }.sumOf { it.amount }
  val todayProfit = ReportEngine.sales(d, today, today).netProfit

  val debtorBalances = d.debtors.map { it to ShopStore.debt(d, it.id) }
  val owing = debtorBalances.filter { it.second > 0 }.sortedByDescending { it.second }
  val totalDebt = owing.sumOf { it.second }
  val expenseMonth = d.expenses.filter { it.date.startsWith(monthPrefix) }.sumOf { it.amount }
  val supplierDebt = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id) }.coerceAtLeast(0.0)

  val lowStock = d.products.filter { ShopStore.stockStatus(d, it) == "low" }
  val outOfStock = d.products.filter { ShopStore.stockStatus(d, it) == "out" }
  val warehouse = WarehouseEngine.summary(d)

  var vipOpen by remember { mutableStateOf(false) }
  var alertsOpen by remember { mutableStateOf(false) }

  // همان هشدارهایی که زنگِ نسخهٔ وب نشان می‌دهد
  val alerts = buildList {
    outOfStock.forEach {
      add(Alert("تمام شد", it.name, "کالا موجود نیست", Shop.colors.danger))
    }
    lowStock.forEach {
      add(Alert("موجودی کم", it.name, "${qty(ShopStore.stock(d, it.id))} مانده", Shop.colors.warning))
    }
    if (supplierDebt > 0) {
      add(Alert("بدهی به تأمین‌کننده", "${money(supplierDebt)} افغانی", "پرداخت‌نشده", Shop.colors.warning))
    }
    owing.take(3).forEach { (debtor, amount) ->
      add(Alert("قرض‌دار", debtor.name, "${money(amount)} افغانی", Shop.colors.danger))
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
  ) {
    /* ---------------------------- سربرگ ---------------------------- */
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text("داشبورد", style = MaterialTheme.typography.headlineMedium, color = Shop.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(
          "خلاصهٔ امروزِ دکان",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        VipBadge(onClick = { vipOpen = true })
        HeaderButton(Icons.Filled.Notifications, "هشدارها", badge = alerts.size) {
          alertsOpen = true
        }
        HeaderButton(
          if (theme == ThemeChoice.DARK) Icons.Filled.LightMode else Icons.Filled.DarkMode,
          "روشن یا تاریک",
        ) {
          onTheme(if (theme == ThemeChoice.DARK) ThemeChoice.LIGHT else ThemeChoice.DARK)
        }
        HeaderButton(Icons.Filled.Person, "حساب") { onOpen("settings") }
      }
    }
    if (vipOpen) VipSheet { vipOpen = false }
    if (alertsOpen) {
      ModalBottomSheet(onDismissRequest = { alertsOpen = false }, containerColor = Shop.colors.bg) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
          Text(
            "هشدارهای فروشگاه",
            style = MaterialTheme.typography.titleMedium,
            color = Shop.colors.text,
          )
          Spacer(Modifier.height(10.dp))
          if (alerts.isEmpty()) {
            EmptyNote("همه‌چیز مرتب است — هشداری نیست.")
          } else {
            alerts.forEach { alert ->
              LineRow(alert.title, alert.value, alert.tint, detail = alert.detail)
            }
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))

    /* ------------------------ چهار کاشیِ بالا ------------------------ */
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatTile(
        "وضعیت انبار", d.products.size.fa(),
        hint = "قلم کالا",
        modifier = Modifier.weight(1f).clickable { onOpen("products") },
      )
      StatTile(
        "قرض‌داران", owing.size.fa(),
        tint = Shop.colors.warning,
        hint = "نفر",
        modifier = Modifier.weight(1f).clickable { onOpen("debtors") },
      )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatTile(
        "مجموع بدهی", money(totalDebt),
        tint = Shop.colors.danger,
        hint = "افغانی",
        modifier = Modifier.weight(1f).clickable { onOpen("debtors") },
      )
      StatTile(
        "مصارف این ماه", money(expenseMonth),
        tint = Shop.colors.success,
        hint = "افغانی",
        modifier = Modifier.weight(1f).clickable { onOpen("expenses") },
      )
    }

    /* ------------------------ روند معاملات ------------------------ */
    Spacer(Modifier.height(16.dp))
    Panel {
      PanelHead("روند معاملات", "این ماه")
      Spacer(Modifier.height(12.dp))
      val monthSales = d.sales.filter { it.status != "cancelled" && it.date.startsWith(monthPrefix) }
      if (monthSales.isEmpty()) {
        EmptyNote("هنوز فروشی ثبت نشده")
      } else {
        val byDay = monthSales.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.finalTotal } }
        TrendChart(byDay.toSortedMap().values.toList())
        Spacer(Modifier.height(8.dp))
        Text(
          "جمع ماه: ${money(byDay.values.sum())} افغانی",
          style = MaterialTheme.typography.labelMedium,
          color = Shop.colors.muted,
        )
      }
    }

    /* -------------------------- قرض‌داران -------------------------- */
    Spacer(Modifier.height(14.dp))
    Panel {
      PanelHead("قرض‌داران", "مشاهده همه") { onOpen("debtors") }
      Spacer(Modifier.height(8.dp))
      if (owing.isEmpty()) {
        EmptyNote("هنوز اطلاعاتی ثبت نشده")
      } else {
        owing.take(5).forEach { (debtor, amount) ->
          LineRow(debtor.name, "${money(amount)} افغانی", Shop.colors.danger)
        }
      }
    }

    /* ------------------------- مصارف اخیر ------------------------- */
    Spacer(Modifier.height(14.dp))
    Panel {
      PanelHead("مصارف اخیر", "مشاهده همه") { onOpen("expenses") }
      Spacer(Modifier.height(8.dp))
      val recent = d.expenses.sortedByDescending { it.createdAt }.take(5)
      if (recent.isEmpty()) {
        EmptyNote("هنوز اطلاعاتی ثبت نشده")
      } else {
        recent.forEach { e ->
          LineRow(
            e.title.ifBlank { e.category.ifBlank { "مصرف" } },
            "${money(e.amount)} افغانی",
            Shop.colors.warning,
            detail = formatDate(e.date),
          )
        }
      }
    }

    /* --------------------- مصارف بر اساس دسته --------------------- */
    Spacer(Modifier.height(14.dp))
    Panel {
      PanelHead("مصارف بر اساس دسته", "این ماه")
      Spacer(Modifier.height(8.dp))
      val byCategory = d.expenses
        .filter { it.date.startsWith(monthPrefix) }
        .groupBy { it.category.ifBlank { "بدون دسته" } }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
      if (byCategory.isEmpty()) {
        EmptyNote("این ماه مصرفی ثبت نشده")
      } else {
        val max = byCategory.first().second
        byCategory.take(6).forEach { (name, amount) ->
          CategoryBar(name, amount, if (max > 0) (amount / max).toFloat() else 0f)
        }
      }
    }

    /* ------------------------- وضعیت انبار ------------------------- */
    Spacer(Modifier.height(14.dp))
    Panel {
      PanelHead("وضعیت انبار", "مشاهده همه") { onOpen("products") }
      Spacer(Modifier.height(10.dp))
      ChipRow(
        listOf(
          "تعداد محصولات" to warehouse.products.fa(),
          "تعداد کارتن" to qty(warehouse.cartons),
          "تعداد واحد" to qty(warehouse.units),
          "ارزش تقریبی موجودی" to "${money(warehouse.value)} افغانی",
        )
      )
      if (lowStock.isNotEmpty() || outOfStock.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        (outOfStock + lowStock).take(5).forEach { p ->
          val out = ShopStore.stockStatus(d, p) == "out"
          LineRow(
            p.name,
            if (out) "تمام‌شده" else "موجودی کم",
            if (out) Shop.colors.danger else Shop.colors.warning,
            detail = "${qty(ShopStore.stock(d, p.id))}${if (p.unit.isNotBlank()) " ${p.unit}" else ""}",
          )
        }
      }
    }

    /* ------------------------- خلاصهٔ امروز ------------------------- */
    Spacer(Modifier.height(14.dp))
    Panel {
      PanelHead("خلاصه امروز", formatDate(today))
      Spacer(Modifier.height(10.dp))
      ChipRow(
        listOf(
          "فروش امروز" to "${money(todayTotal)} افغانی",
          "تعداد فروش امروز" to todaySales.size.fa(),
          "سود امروز" to "${money(todayProfit)} افغانی",
          "مصارف امروز" to "${money(todayExpense)} افغانی",
        )
      )
      Spacer(Modifier.height(8.dp))
      ChipRow(
        listOf(
          "بدهی تأمین‌کنندگان" to "${money(supplierDebt)} افغانی",
          "کالاهای کم‌موجودی" to lowStock.size.fa(),
          "کالاهای تمام‌شده" to outOfStock.size.fa(),
        )
      )
    }

    Spacer(Modifier.height(24.dp))
  }
}

/* ============================ اجزای صفحه ============================ */

@Composable
private fun PanelHead(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    if (action != null) {
      Text(
        action,
        style = MaterialTheme.typography.labelMedium,
        color = if (onAction != null) Shop.colors.primary else Shop.colors.muted,
        modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier,
      )
    }
  }
}

@Composable
private fun LineRow(title: String, value: String, tint: Color, detail: String? = null) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      if (detail != null) {
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }
    }
    Text(
      value,
      style = MaterialTheme.typography.labelLarge,
      color = tint,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun ChipRow(items: List<Pair<String, String>>) {
  Row(
    Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items.forEach { (label, value) ->
      Column(
        Modifier
          .clip(RoundedCornerShape(Radius.sm))
          .background(Shop.colors.surface2)
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Text(
          value,
          style = MaterialTheme.typography.labelLarge,
          color = Shop.colors.text,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun CategoryBar(name: String, amount: Double, ratio: Float) {
  Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(name, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text)
      Text(
        "${money(amount)} افغانی",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted,
      )
    }
    Spacer(Modifier.height(4.dp))
    Box(
      Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(Shop.colors.surface2)
    ) {
      Box(
        Modifier
          .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Shop.colors.warning)
      )
    }
  }
}

/** نمودارِ ساده و بی‌کتابخانه — همان روندی که وب می‌کشد */
@Composable
private fun TrendChart(values: List<Double>) {
  val line = Shop.colors.primary
  val fill = Shop.colors.primaryTint
  Canvas(Modifier.fillMaxWidth().height(120.dp)) {
    if (values.isEmpty()) return@Canvas
    val max = values.maxOrNull() ?: 0.0
    if (max <= 0.0) return@Canvas
    val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
    val points = values.mapIndexed { i, v ->
      Offset(
        x = if (values.size > 1) stepX * i else size.width / 2,
        y = size.height - (v / max).toFloat() * (size.height - 8f) - 4f,
      )
    }
    // سطحِ زیر خط
    points.forEachIndexed { i, p ->
      if (i > 0) {
        val prev = points[i - 1]
        drawLine(color = line, start = prev, end = p, strokeWidth = 3f)
        drawRect(
          color = fill,
          topLeft = Offset(prev.x, minOf(prev.y, p.y)),
          size = androidx.compose.ui.geometry.Size(
            width = p.x - prev.x,
            height = size.height - minOf(prev.y, p.y),
          ),
        )
      }
    }
    points.forEach { p -> drawCircle(color = line, radius = 4f, center = p) }
  }
}


/** یک هشدار در زنگِ سربرگ */
private data class Alert(val value: String, val title: String, val detail: String, val tint: Color)

/** دکمه‌های کوچکِ سربرگ — همان آیکن‌های گردِ نسخهٔ وب */
@Composable
private fun HeaderButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  badge: Int = 0,
  onClick: () -> Unit,
) {
  Box(contentAlignment = Alignment.TopEnd) {
    Box(
      Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Shop.colors.surface)
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = description, tint = Shop.colors.muted, modifier = Modifier.size(18.dp))
    }
    if (badge > 0) {
      Box(
        Modifier
          .size(16.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Shop.colors.danger),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          badge.coerceAtMost(9).fa(),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
        )
      }
    }
  }
}
