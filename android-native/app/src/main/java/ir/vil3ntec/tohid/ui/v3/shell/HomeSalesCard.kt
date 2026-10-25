package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.LightColors
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  کادرِ اصلیِ خانه، **یکی** (HOME-V2 §۲٫۲): «فروشِ امروز» (`amount`، همان عددِ `DashboardNumbers`)
 *  با عددِ درشتِ شمارنده، یک خطِ
 *  مقایسه با همین ساعتِ دیروز (پیکانِ سبز/سرخ — رنگ تنها نیست) و نمودارِ خطیِ بسیار کوچکِ ۷ روز
 *  در پایینِ کادر. بی تراشه، بی دکمه، بی ورق‌خور؛ زدن ⇐ آمار (`onStats`، برای کسی که آمار دارد).
 *  قالب همان `V3HeroCard` ِ پایه است تا با کادرِ پنهانِ «صندوق» یک خانواده بماند.
 */
@Composable
internal fun HomeSalesCard(amount: Double, trend: HomeTrend.View, onStats: (() -> Unit)?, modifier: Modifier = Modifier) {
  val shown = rememberCountUp(amount)
  Box(
    modifier
      .padding(horizontal = 16.dp)
      .then(if (onStats != null) Modifier.clip(RoundedCornerShape(ir.vil3ntec.tohid.ui.v3.HERO_RADIUS)).v3Pressable(label = StrHome.openStats, onClick = onStats) else Modifier),
  ) {
    V3HeroCard(title = StrHome.todaySales, value = "${money(shown)} ؋")
    //  پایینِ کادر (جای تراشه‌ها که این کادر ندارد): مقایسه و خطِ کوچک
    Column(
      Modifier.matchParentSize().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.Bottom,
    ) {
      val pct = trend.pct
      val dir = trend.direction
      if (pct != null && dir != null) ComparePill(pct, dir)
      if (trend.hasSeries) {
        Spacer(Modifier.height(10.dp))
        Sparkline(trend.series, Modifier.fillMaxWidth().height(44.dp))
      }
    }
  }
}

/** «+۱۲٪ از دیروز» روی شیشهٔ سفید؛ دایرهٔ کوچکِ پیکان سبز (بالا) یا سرخ (پایین) با رنگِ معناییِ ثابت */
@Composable
private fun ComparePill(pct: Int, dir: HomeTrend.Direction) {
  val white = Color.White
  val sem = LightColors.message
  val (dot, icon) = when (dir) {
    HomeTrend.Direction.UP -> sem.success.icon to Icons.Rounded.ArrowUpward
    HomeTrend.Direction.DOWN -> sem.error.icon to Icons.Rounded.ArrowDownward
    HomeTrend.Direction.SAME -> white.copy(alpha = 0.28f) to Icons.Rounded.Remove
  }
  Row(
    Modifier.clip(RoundedCornerShape(percent = 50)).background(white.copy(alpha = 0.16f)).padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(18.dp).clip(CircleShape).background(dot), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = white, modifier = Modifier.size(12.dp))
    }
    Spacer(Modifier.width(6.dp))
    Text(StrHome.vsYesterday(pct), color = white, style = v3Text(12, FontWeight.SemiBold, 16), maxLines = 1)
  }
}

/**
 *  خطِ بسیار کوچکِ فروشِ ۷ روز: خطِ سفید با پرکنِ نرمِ رو به پایین و نقطهٔ امروز. فقط رسم
 *  (`drawWithCache`)، بی محور و برچسب. راست‌به‌چپ آینه است (کهنه‌ترین راست، امروز چپ) — همان
 *  جهتِ نمودارِ آمار.
 */
@Composable
internal fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val white = Color.White
  Box(
    modifier.drawWithCache {
      val n = values.size
      val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
      val stroke = 2.dp.toPx()
      val dotR = 3.5.dp.toPx()
      val top = dotR + stroke
      val h = size.height - top - stroke
      val step = if (n > 1) (size.width - 2 * dotR) / (n - 1) else 0f
      fun pt(i: Int): Offset {
        val x0 = dotR + step * i
        val x = if (rtl) size.width - x0 else x0
        return Offset(x, top + h * (1f - (values[i] / max).toFloat()))
      }
      val line = Path()
      val fill = Path()
      if (n > 0) {
        val first = pt(0)
        line.moveTo(first.x, first.y)
        fill.moveTo(first.x, size.height)
        fill.lineTo(first.x, first.y)
        for (i in 1 until n) {
          //  منحنیِ نرم میانِ دو نقطه (نیم‌راهِ افقی)
          val a = pt(i - 1)
          val b = pt(i)
          val mx = (a.x + b.x) / 2f
          line.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
          fill.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
        }
        val last = pt(n - 1)
        fill.lineTo(last.x, size.height)
        fill.close()
      }
      val shade = Brush.verticalGradient(listOf(white.copy(alpha = 0.22f), white.copy(alpha = 0f)))
      val end = if (n > 0) pt(n - 1) else Offset.Zero
      onDrawBehind {
        if (n == 0) return@onDrawBehind
        drawPath(fill, shade)
        drawPath(line, white.copy(alpha = 0.9f), style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(white, dotR, end)
      }
    },
  )
}
