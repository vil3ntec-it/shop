package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.invoiceLabel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.DashboardEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  داشبوردِ کامپیوتر — نمای کلیِ دکان در یک نگاه، نه کاشی‌های گوشی.
 *
 *  خواستهٔ صاحب مخزن (با نمونهٔ «Seller Central»ِ آمازون): «هشدارها،
 *  کم‌بودی‌ها، تاریخ‌هایی که می‌روند، کدام کالا فروش داشته یا نداشته،
 *  قرض‌دارهایی که زیاد شده‌اند…». چیدمان:
 *
 *   ۱. سلام و تاریخ، بازه (امروز / ۷ / ۳۰ / ۹۰ روز)
 *   ۲. پنج کارتِ عدد با درصدِ تغییر نسبت به بازهٔ قبل
 *   ۳. نمودارِ فروش و سود + کارهای سریع
 *   ۴. هشدارها: ناموجود، کم‌موجود، نزدیکِ انقضا، قرضِ دیرکرده
 *   ۵. پرفروش‌ها، بی‌فروش‌ها (موجود ولی نفروخته)، قرض‌هایی که بالا رفته‌اند،
 *      سهمِ دسته‌ها، فاکتورهای تازه
 *
 *  هر ردیف قابلِ کلیک است و به صفحهٔ خودش می‌رود. عددها از
 *  `DashboardEngine` می‌آیند (آزمون: `DashboardEngineTest`).
 */
@Composable
fun DeskDashboardScreen(d: ShopData, onOpen: (String) -> Unit, onProduct: (String) -> Unit) {
  val context = LocalContext.current
  var days by remember { mutableStateOf(7) }
  val today = ir.vil3ntec.tohid.todayIso()
  val b = remember(d, days, today) { DashboardEngine.board(d, today, days) }
  val name = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    /* ۱ ─ سلام و بازه */
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          if (name.isNotBlank()) "سلام، $name" else "سلام",
          style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text,
        )
        Text(
          "${formatDate(today)} · ${if (b.alertCount > 0) "${b.alertCount} هشدار نیاز به توجه دارد" else "همه‌چیز رو به راه است"}",
          style = MaterialTheme.typography.bodyMedium, color = if (b.alertCount > 0) Shop.colors.danger else Shop.colors.muted,
        )
      }
      Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Shop.colors.surface2).padding(3.dp),
      ) {
        listOf(1 to "امروز", 7 to "۷ روز", 30 to "۳۰ روز", 90 to "۹۰ روز").forEach { (n, label) ->
          val on = n == days
          Text(
            label,
            Modifier.clip(RoundedCornerShape(18.dp))
              .background(if (on) Shop.colors.primary else Color.Transparent)
              .clickable { days = n }
              .padding(horizontal = 14.dp, vertical = 6.dp),
            color = if (on) Color.White else Shop.colors.text,
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }

    /* ۲ ─ کارت‌های عدد — «گزارشِ تازه» روشن: کارت‌های امروز و نشان‌های وضعیت از هستهٔ گزارش، با کلیک به فهرستِ همان عدد */
    ReportFresh.load(context)
    if (ReportFresh.on.value) {
      val canSeeMoney = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
      HomeKpis(d, onOpen, canSeeMoney, columns = 5)
    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      KpiCard(Modifier.weight(1f), Icons.Filled.PointOfSale, "فروش", money(b.sales.value), b.sales.changePct, Shop.colors.primary)
      KpiCard(Modifier.weight(1f), Icons.Filled.TrendingUp, "سودِ ناخالص", money(b.profit.value), b.profit.changePct, Shop.colors.success)
      KpiCard(Modifier.weight(1f), Icons.Filled.Receipt, "تعدادِ فاکتور", b.invoices.value.toInt().toString(), b.invoices.changePct, Color(0xFF7C4DFF))
      KpiCard(Modifier.weight(1f), Icons.Filled.ShoppingBasket, "میانگینِ فاکتور", money(b.avgTicket.value), b.avgTicket.changePct, Shop.colors.warning)
      KpiCard(Modifier.weight(1f), Icons.Filled.AccountBalanceWallet, "طلب از مشتریان", money(b.receivables), null, Shop.colors.danger) { onOpen("debtors") }
    }

    /* ۳ ─ نمودار و کارهای سریع */
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(300.dp)) {
      Panel2(Modifier.weight(2.2f).fillMaxHeight(), "فروش و سود", "${if (days == 1) "امروز" else "$days روزِ گذشته"} · امروز ${money(b.todaySales)} ؋") {
        SalesChart(b.series, Modifier.fillMaxSize())
      }
      Panel2(Modifier.weight(1f).fillMaxHeight(), "کارهای سریع") {
        QuickAction(Icons.Filled.PointOfSale, "فروشِ تازه", "صندوق — Ctrl+2") { onOpen("sale") }
        QuickAction(Icons.Filled.AddBox, "کالای تازه", "جدولِ کالاها") { onOpen("products") }
        QuickAction(Icons.Filled.Payments, "دریافت از مشتری", "صورت‌حساب و F7") { onOpen("debtors") }
        QuickAction(Icons.Filled.LocalShipping, "ورودِ انبار و خرید", "${b.activeCount} کالای فعال") { onOpen("warehouse") }
        QuickAction(Icons.Filled.Assessment, "گزارش‌ها", "اکسل و چاپ") { onOpen("reports") }
      }
    }

    /* ۴ ─ هشدارها */
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(270.dp)) {
      AlertPanel(Modifier.weight(1f), "ناموجود", b.outOfStock.size, Shop.colors.danger,
        b.outOfStock.map { it.product.id to (it.product.name to "۰ ${it.product.unit}") }, onProduct)
      AlertPanel(Modifier.weight(1f), "کم‌موجود", b.lowStock.size, Shop.colors.warning,
        b.lowStock.map { it.product.id to (it.product.name to "${qty(it.stock)} از حدِ ${qty(it.product.minStock)}") }, onProduct)
      AlertPanel(Modifier.weight(1f), "انقضا (۳۰ روزِ آینده)", b.expiring.size, Shop.colors.warning,
        b.expiring.map { it.product.id to (it.product.name to if (it.expired) "گذشته · ${qty(it.remaining)}" else "${it.daysLeft} روز · ${qty(it.remaining)}") }, onProduct)
      AlertPanel(Modifier.weight(1f), "قرضِ بیش از ۶۰ روز", b.debtOverdue.size, Shop.colors.danger,
        b.debtOverdue.map { it.debtor.id to (it.debtor.name to "${money(it.balance)} · ${it.days} روز") }) { onOpen("debtors") }
    }

    /* ۵ ─ فروش‌ها و قرض‌ها */
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(330.dp)) {
      Panel2(Modifier.weight(1.3f).fillMaxHeight(), "پرفروش‌ها", "در همین بازه") {
        if (b.top.isEmpty()) Empty("فروشی در این بازه نیست")
        b.top.forEachIndexed { i, p ->
          ListRow("${i + 1}. ${p.product.name}", "${qty(p.quantity)} ${p.product.unit}", "${money(p.revenue)} ؋") { onProduct(p.product.id) }
        }
      }
      Panel2(Modifier.weight(1.3f).fillMaxHeight(), "بی‌فروش (موجود ولی نفروخته)", "${b.unsold.size} کالا — سرمایهٔ خوابیده") {
        if (b.unsold.isEmpty()) Empty("همهٔ کالاهای موجود فروش داشته‌اند")
        b.unsold.take(8).forEach { a ->
          ListRow(a.product.name, "${qty(a.stock)} ${a.product.unit}", "${money(a.stock * a.product.purchasePrice)} ؋") { onProduct(a.product.id) }
        }
      }
      Panel2(Modifier.weight(1.1f).fillMaxHeight(), "قرض‌هایی که بالا رفته", "افزایشِ بدهی در همین بازه") {
        if (b.debtRising.isEmpty()) Empty("بدهیِ هیچ مشتری‌ای بالا نرفته")
        b.debtRising.forEach { l ->
          ListRow(l.debtor.name, "+${money(l.change)}", "مانده ${money(l.balance)}", Shop.colors.danger) { onOpen("debtors") }
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(300.dp)) {
      Panel2(Modifier.weight(1f).fillMaxHeight(), "سهمِ دسته‌ها از فروش") {
        CategoryDonut(b.categories, Modifier.fillMaxSize())
      }
      Panel2(Modifier.weight(1.6f).fillMaxHeight(), "فاکتورهای تازه", "ارزشِ انبار ${money(b.stockValue)} ؋ · ${b.productCount} کالا") {
        if (b.recent.isEmpty()) Empty("هنوز فاکتوری نیست")
        b.recent.forEach { s ->
          val who = s.debtorId?.let { id -> d.debtors.find { it.id == id }?.name } ?: "مشتریِ گذری"
          ListRow(
            "#${s.invoiceLabel() ?: "-"} · $who",
            "${formatDate(s.date)} · ${ir.vil3ntec.tohid.data.PayFlow.payLabel(s)}",
            "${money(s.finalTotal)} ؋",
          ) { onOpen("sales") }
        }
      }
    }
  }
}

/* ───────────────────────────── اجزا ───────────────────────────── */

private val CardShape = RoundedCornerShape(16.dp)

@Composable
private fun Panel2(modifier: Modifier, title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
  Surface(color = Shop.colors.surfaceSolid, shape = CardShape, modifier = modifier.border(1.dp, Shop.colors.border, CardShape)) {
    Column(Modifier.padding(16.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      Spacer(Modifier.height(10.dp))
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
    }
  }
}

@Composable
private fun KpiCard(modifier: Modifier, icon: ImageVector, title: String, value: String, change: Double?, tint: Color, onClick: (() -> Unit)? = null) {
  Surface(
    color = Shop.colors.surfaceSolid, shape = CardShape,
    modifier = modifier.border(1.dp, Shop.colors.border, CardShape).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
          Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
      }
      Spacer(Modifier.height(10.dp))
      Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1)
      if (change != null) {
        val up = change >= 0
        Text(
          "${if (up) "▲" else "▼"} ${"%.0f".format(kotlin.math.abs(change))}٪ نسبت به بازهٔ قبل",
          style = MaterialTheme.typography.labelMedium, color = if (up) Shop.colors.success else Shop.colors.danger,
        )
      } else Text(" ", style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 7.dp, horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Shop.colors.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
      Icon(icon, null, tint = Shop.colors.primary, modifier = Modifier.size(19.dp))
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
      Text(hint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Icon(Icons.Filled.ChevronLeft, null, tint = Shop.colors.muted)
  }
}

@Composable
private fun ListRow(title: String, middle: String, end: String, endColor: Color = Shop.colors.text, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 6.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
    Text(middle, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1)
    Text(end, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = endColor, maxLines = 1)
  }
}

@Composable
private fun Empty(text: String) {
  Text(text, Modifier.fillMaxWidth().padding(vertical = 18.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
}

@Composable
private fun AlertPanel(
  modifier: Modifier,
  title: String,
  count: Int,
  tint: Color,
  rows: List<Pair<String, Pair<String, String>>>,
  onClick: (String) -> Unit,
) {
  Surface(color = Shop.colors.surfaceSolid, shape = CardShape, modifier = modifier.fillMaxHeight().border(1.dp, if (count > 0) tint.copy(alpha = 0.45f) else Shop.colors.border, CardShape)) {
    Column(Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (count > 0) tint else Shop.colors.success))
        Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
        Text(
          count.toString(),
          Modifier.clip(RoundedCornerShape(10.dp)).background((if (count > 0) tint else Shop.colors.success).copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
          color = if (count > 0) tint else Shop.colors.success, style = MaterialTheme.typography.labelLarge,
        )
      }
      Spacer(Modifier.height(8.dp))
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        if (rows.isEmpty()) Empty("چیزی نیست ✓")
        rows.take(30).forEach { (id, pair) ->
          Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onClick(id) }.padding(vertical = 5.dp, horizontal = 2.dp),
          ) {
            Text(pair.first, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
            Text(pair.second, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
          }
        }
      }
    }
  }
}

/** نمودارِ ناحیه‌ای: فروش (رنگِ برند) و سود (سبز)، با خطِ پایه و برچسبِ اول/آخر */
@Composable
private fun SalesChart(series: List<DashboardEngine.Day>, modifier: Modifier) {
  if (series.isEmpty() || series.all { it.sales == 0.0 }) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("فروشی در این بازه ثبت نشده", color = Shop.colors.muted) }
    return
  }
  val brand = Shop.colors.primary
  val green = Shop.colors.success
  val grid = Shop.colors.border
  Column(modifier) {
    Row {
      Legend(brand, "فروش")
      Spacer(Modifier.width(14.dp))
      Legend(green, "سود")
      Spacer(Modifier.weight(1f))
      Text("بیشینه ${money(series.maxOf { it.sales })}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Spacer(Modifier.height(6.dp))
    Canvas(Modifier.weight(1f).fillMaxWidth()) {
      val maxV = series.maxOf { it.sales }.coerceAtLeast(1.0)
      val n = series.size
      fun x(i: Int) = if (n == 1) size.width / 2 else size.width * (1f - i.toFloat() / (n - 1))   //  راست‌به‌چپ: امروز سمتِ چپ
      fun y(v: Double) = size.height * (1f - (v / maxV).toFloat() * 0.92f)
      for (k in 0..3) {
        val gy = size.height * k / 4f
        drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
      }
      fun area(values: List<Double>, color: Color) {
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
        val fill = Path().apply { addPath(line); lineTo(x(n - 1), size.height); lineTo(x(0), size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 3f))
        values.forEachIndexed { i, v -> if (n <= 31) drawCircle(color, 3.5f, Offset(x(i), y(v))) }
      }
      area(series.map { it.sales }, brand)
      area(series.map { it.profit.coerceAtLeast(0.0) }, green)
    }
    Row {
      Text(formatDate(series.last().iso), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      Spacer(Modifier.weight(1f))
      Text(formatDate(series.first().iso), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
}

@Composable
private fun Legend(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(5.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text)
  }
}

private val DonutColors = listOf(
  Color(0xFF5B3DF5), Color(0xFF00BFA5), Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF8BC34A), Color(0xFF9E9E9E),
)

@Composable
private fun CategoryDonut(shares: List<DashboardEngine.Share>, modifier: Modifier) {
  val total = shares.sumOf { it.revenue }
  if (total <= 0) { Box(modifier, contentAlignment = Alignment.Center) { Text("فروشی در این بازه نیست", color = Shop.colors.muted) }; return }
  val top = shares.take(6).toMutableList()
  val rest = shares.drop(6).sumOf { it.revenue }
  if (rest > 0) top += DashboardEngine.Share("بقیه", rest)
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(150.dp)) {
      var start = -90f
      top.forEachIndexed { i, s ->
        val sweep = (s.revenue / total * 360f).toFloat()
        drawArc(DonutColors[i % DonutColors.size], start, sweep - 1.5f, false, style = Stroke(width = 34f), topLeft = Offset(17f, 17f), size = Size(size.width - 34f, size.height - 34f))
        start += sweep
      }
    }
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      top.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(10.dp).clip(CircleShape).background(DonutColors[i % DonutColors.size]))
          Spacer(Modifier.width(6.dp))
          Text(s.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
          Text("${"%.0f".format(s.revenue / total * 100)}٪", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        }
      }
    }
  }
}
