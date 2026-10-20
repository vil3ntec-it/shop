package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** یک ستونِ دوتاییِ نمودار: فروش (سیاه) و مصارف + خرید (بنفش) */
data class StatsBar(val label: String, val sales: Double, val spend: Double)

/** عددهای صفحهٔ آمار — همه از همان موتورِ گزارشِ برنامه (`ReportEngine`) */
data class StatsView(val total: Double, val bars: List<StatsBar>) {
  val periodSales: Double get() = bars.sumOf { it.sales }
  val periodSpend: Double get() = bars.sumOf { it.spend }

  companion object {
    fun of(d: ShopData, period: StatsBuckets.Period, today: String, monthNames: List<String>): StatsView {
      val buckets = StatsBuckets.of(period, today, monthNames)
      val bars = buckets.map { b ->
        val s = ReportEngine.sales(d, b.from, b.to)
        StatsBar(b.label, s.net, s.expenses + ReportEngine.purchases(d, b.from, b.to).total)
      }
      return StatsView(ReportEngine.sales(d, "0000-01-01", "9999-12-31").net, bars)
    }
  }
}

/**
 *  «آمار» — r8، صفحهٔ دومِ خانه (انگشت به چپ). عنوانِ وسط، «فروشِ کل» و عددِ درشت،
 *  «نمای کلی» با بازهٔ هفته/ماه/سال، نمودارِ ستونیِ جفتی با خط‌های راهنما و برچسب‌ها،
 *  راهنما، و دو کارتِ بنفش (فروش و مصارف). ستون‌ها بارِ اول از صفر بالا می‌آیند.
 */
@Composable
fun PhoneStatsPage(d: ShopData, bottomReserve: Dp, pages: Int, currentPage: Int, big: Boolean = false) {
  val p = PhoneV3.colors
  var period by rememberSaveable { mutableStateOf(StatsBuckets.Period.MONTH) }
  val today = ir.vil3ntec.tohid.todayIso()
  val months = ir.vil3ntec.tohid.JALALI_MONTHS
  val live = remember(d, period, today, months) { StatsView.of(d, period, today, months) }
  val view = if (big) StatsView(live.total * 1000 + 1_234_567_890.0, live.bars.map { it.copy(sales = it.sales * 1000, spend = it.spend * 1000) }) else live
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Column(
    Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(p.bg, p.card), endY = 900f))
      .verticalScroll(rememberScrollState())
      .padding(top = top + 14.dp, start = 24.dp, end = 24.dp),
  ) {
    Text(
      "آمار",
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      color = p.ink,
      style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    )
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PagerDots(pages, currentPage, p.ink, p.chevron) }
    Spacer(Modifier.height(22.dp))
    Text("فروشِ کل", color = p.inkMuted, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp))
    Spacer(Modifier.height(2.dp))
    val total = "${money(view.total)} ؋"
    Text(
      total,
      color = p.ink,
      maxLines = 1,
      style = MaterialTheme.typography.displaySmall.copy(fontSize = if (total.length > 14) 28.sp else 34.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    )
    Spacer(Modifier.height(26.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "نمای کلی", color = p.ink, modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
      )
      PeriodPicker(period) { period = it }
    }
    Spacer(Modifier.height(18.dp))
    PairedBars(view.bars, Modifier.fillMaxWidth().height(236.dp), key = period)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
      StatsLegend(PhoneV3Palette.chartSales(p), "فروش")
      StatsLegend(PhoneV3Palette.chartCost(p), "مصارف و خرید")
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      MoneyCard("فروش", view.periodSales, p.accentDeep, Icons.Rounded.ArrowDownward, Modifier.weight(1f))
      MoneyCard("مصارف و خرید", view.periodSpend, PhoneV3Palette.chartCost(p), Icons.Rounded.ArrowUpward, Modifier.weight(1f))
    }
    Spacer(Modifier.height(bottomReserve + navBottom + 20.dp))
  }
}

@Composable
private fun PeriodPicker(period: StatsBuckets.Period, onPick: (StatsBuckets.Period) -> Unit) {
  val p = PhoneV3.colors
  var open by remember { mutableStateOf(false) }
  Box {
    PressBox(scaleTo = 0.95f, label = "بازه", onClick = { open = true }) {
      Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(p.card)
          .border(1.dp, p.hairline, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(period.label, color = p.ink, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = p.ink, modifier = Modifier.size(18.dp))
      }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      StatsBuckets.Period.entries.forEach { it ->
        DropdownMenuItem(text = { Text(it.label) }, onClick = { open = false; onPick(it) })
      }
    }
  }
}

@Composable
private fun StatsLegend(color: Color, label: String) {
  val p = PhoneV3.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
    Spacer(Modifier.width(8.dp))
    Text(label, color = p.inkMuted, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
  }
}

/**
 *  نمودارِ ستونیِ جفتی (r8): محورِ عدد در سمتِ شروع، خط‌های نقطه‌چینِ راهنما، هر
 *  بازه دو ستونِ گردسر — بنفش (مصارف و خرید) و سیاه (فروش) — و نامِ بازه زیرش.
 *  ستون‌ها با `key` تازه (عوض‌شدنِ بازه یا بارِ اول) پله‌پله از صفر بالا می‌آیند.
 */
@Composable
private fun PairedBars(bars: List<StatsBar>, modifier: Modifier, key: Any) {
  val p = PhoneV3.colors
  val measurer = rememberTextMeasurer()
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val grow = remember(key, bars.size) { List(bars.size) { Animatable(if (Motion.enabled) 0f else 1f) } }
  LaunchedEffect(grow) {
    if (!Motion.enabled) return@LaunchedEffect
    grow.forEachIndexed { i, a ->
      launch {
        delay(60L * i)
        a.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
      }
    }
  }
  val maxV = bars.maxOfOrNull { maxOf(it.sales, it.spend) } ?: 0.0
  val axis = StatsBuckets.axisMax(maxV)
  val axisStyle = MaterialTheme.typography.labelMedium.copy(color = p.inkMuted, fontSize = 12.sp)
  val labelStyle = MaterialTheme.typography.labelMedium.copy(color = p.inkMuted, fontSize = 12.sp)
  val dark = PhoneV3Palette.chartSales(p)
  val purple = PhoneV3Palette.chartCost(p)
  val grid = PhoneV3Palette.chartGrid(p)
  val desc = bars.joinToString("، ") { "${it.label}: فروش ${money(it.sales)}، مصارف ${money(it.spend)}" }
  Canvas(modifier.semantics { contentDescription = desc }) {
    val labelH = 26.dp.toPx()
    val axisW = 40.dp.toPx()
    val chartTop = 8.dp.toPx()
    val chartBottom = size.height - labelH
    val chartH = chartBottom - chartTop
    val plotStart = if (rtl) 0f else axisW
    val plotEnd = if (rtl) size.width - axisW else size.width
    //  خط‌های راهنما و عددِ محور
    for (k in 0..4) {
      val y = chartBottom - chartH * k / 4f
      drawLine(grid, Offset(plotStart, y), Offset(plotEnd, y), strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
      val t = measurer.measure(StatsBuckets.compact(axis * k / 4.0), axisStyle)
      val x = if (rtl) size.width - t.size.width else 0f
      drawText(t, topLeft = Offset(x, y - t.size.height / 2f))
    }
    if (bars.isEmpty()) return@Canvas
    val slot = (plotEnd - plotStart) / bars.size
    val barW = minOf(13.dp.toPx(), slot / 3.2f)
    val inner = 5.dp.toPx()
    bars.forEachIndexed { i, b ->
      //  راست‌به‌چپ: بازهٔ نخست در راست
      val center = if (rtl) plotEnd - slot * (i + 0.5f) else plotStart + slot * (i + 0.5f)
      val g = grow.getOrNull(i)?.value ?: 1f
      //  صفر (کمتر از نیم افغانی) ⇒ هیچ ستونی؛ مقدارِ کوچکِ ناصفر ⇒ دست‌کم یک ستونِ کوتاهِ خوانا، نه نقطه
      val minH = maxOf(barW * 1.8f, 6.dp.toPx())
      fun bar(v: Double, left: Float, color: Color) {
        if (v < 0.5 || axis <= 0) return
        val full = maxOf((v / axis).toFloat().coerceIn(0f, 1f) * chartH, minH)
        val h = full * g
        if (h <= 0.5f) return
        drawRoundRect(color, Offset(left, chartBottom - h), Size(barW, h), CornerRadius(barW / 2f, barW / 2f))
      }
      //  r8 (چپ‌به‌راست): بنفش سپس سیاه — آینه‌شده: بنفش در سمتِ شروع
      val first = if (rtl) center + inner / 2f else center - inner / 2f - barW
      val second = if (rtl) center - inner / 2f - barW else center + inner / 2f
      bar(b.spend, first, purple)
      bar(b.sales, second, dark)
      val t = measurer.measure(b.label, labelStyle, maxLines = 1)
      drawText(t, topLeft = Offset(center - t.size.width / 2f, size.height - t.size.height.toFloat()))
    }
  }
}

/** دو کارتِ بنفشِ پایینِ r8: آیکنِ گرد با پیکان، برچسب، مبلغِ سفید و نقشِ پیکانِ بزرگ */
@Composable
private fun MoneyCard(label: String, amount: Double, bg: Color, arrow: ImageVector, modifier: Modifier) {
  val p = PhoneV3.colors
  Box(modifier.height(168.dp).clip(RoundedCornerShape(22.dp)).background(bg)) {
    Box(
      Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = 30.dp).size(170.dp)
        .clip(CircleShape).background(p.onAccent.copy(alpha = 0.08f)),
    )
    Icon(
      arrow, contentDescription = null, tint = p.black.copy(alpha = 0.28f),
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 26.dp).size(84.dp),
    )
    Column(Modifier.fillMaxSize().padding(18.dp)) {
      Box(Modifier.size(30.dp).clip(CircleShape).background(p.onAccent), contentAlignment = Alignment.Center) {
        Icon(arrow, contentDescription = null, tint = bg, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.weight(1f))
      Text(label, color = p.onAccent.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
      Spacer(Modifier.height(4.dp))
      val text = "${money(amount)} ؋"
      Text(
        text, color = p.onAccent, maxLines = 1,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = if (text.length > 11) 15.sp else 19.sp, fontWeight = FontWeight.Bold),
      )
    }
  }
}
