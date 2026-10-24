package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  نمودارِ ستونیِ آمار (شکلِ stats-chart-ref): محورِ عدد در سمتِ آغاز با خط‌های نقطه‌چین،
 *  ستونِ انتخاب‌شده به رنگِ برند و بقیه خنثی، نامِ بازه زیرِ هر ستون، و بالای ستونِ انتخاب‌شده
 *  برچسبِ عددِ دقیق. ستون‌ها از خطِ پایه با فنر بالا می‌آیند (`growth`) و رنگ و برچسبِ
 *  انتخاب با فنرِ نشانگر (`MotionSpec.indicator`) به ستونِ تازه می‌لغزد. همهٔ متن‌ها پیش از
 *  کشیدن اندازه گرفته می‌شوند؛ کشیدن فقط می‌خواند (بی ساختنِ چیزِ تازه در هر فریم).
 */
@Composable
internal fun StatsBarChart(
  values: List<Double>,
  labels: List<String>,
  selected: Int,
  growth: Growth,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val measurer = rememberTextMeasurer()
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val axis = StatsModel.axisMax(values.maxOrNull() ?: 0.0)
  val axisStyle = V3Type.caption.copy(color = c.inkMuted, fontWeight = FontWeight.Medium)
  val selStyle = V3Type.caption.copy(color = c.ink, fontWeight = FontWeight.Bold)
  val tipStyle = V3Type.caption.copy(color = c.onBlack, fontWeight = FontWeight.Bold)
  val axisText = remember(axis, axisStyle) { (0..4).map { measurer.measure(StatsModel.compact(axis * it / 4.0), axisStyle) } }
  val labelText = remember(labels, axisStyle) { labels.map { measurer.measure(it, axisStyle, maxLines = 1) } }
  val labelSel = remember(labels, selStyle) { labels.map { measurer.measure(it, selStyle, maxLines = 1) } }
  val tips = remember(values, tipStyle) { values.map { measurer.measure(afn(it), tipStyle, maxLines = 1) } }
  val brand = chartColor(c, ChartSeries.SALES)
  val neutral = chartColor(c, ChartSeries.NEUTRAL)
  val grid = chartGrid(c)
  val tipBg = c.black
  val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 14f)) }
  val pos = remember { Animatable(selected.toFloat()) }
  LaunchedEffect(selected) { pos.animateTo(selected.toFloat(), MotionSpec.indicator()) }
  val desc = labels.indices.joinToString("، ") { "${labels[it]}: ${afn(values.getOrElse(it) { 0.0 })}" }
  Canvas(
    modifier
      .semantics { contentDescription = desc }
      .pointerInput(values.size, rtl) {
        detectTapGestures { p ->
          val axisW = AXIS_W.toPx()
          val start = if (rtl) 0f else axisW
          val end = if (rtl) size.width - axisW else size.width.toFloat()
          if (values.isEmpty() || p.x < start || p.x > end) return@detectTapGestures
          val i = ((p.x - start) / ((end - start) / values.size)).toInt().coerceIn(0, values.lastIndex)
          onSelect(if (rtl) values.lastIndex - i else i)
        }
      },
  ) {
    val labelH = 24.dp.toPx()
    val tipH = 24.dp.toPx()
    val top = tipH + 12.dp.toPx()
    val bottom = size.height - labelH
    val h = bottom - top
    val axisW = AXIS_W.toPx()
    val start = if (rtl) 0f else axisW
    val end = if (rtl) size.width - axisW else size.width
    axisText.forEachIndexed { k, t ->
      val y = bottom - h * k / 4f
      drawLine(grid, Offset(start, y), Offset(end, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
      drawText(t, topLeft = Offset(if (rtl) size.width - t.size.width else 0f, y - t.size.height / 2f))
    }
    if (values.isEmpty()) return@Canvas
    val slot = (end - start) / values.size
    val barW = minOf(36.dp.toPx(), slot * 0.62f)
    val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    fun centerOf(i: Float) = if (rtl) end - slot * (i + 0.5f) else start + slot * (i + 0.5f)
    fun heightOf(i: Int): Float {
      val v = values[i]
      val full = if (v <= 0.0 || axis <= 0.0) 0f else maxOf((v / axis).toFloat().coerceIn(0f, 1f) * h, 4.dp.toPx())
      return full * growth.at(i)
    }
    values.indices.forEach { i ->
      val center = centerOf(i.toFloat())
      val bh = heightOf(i)
      //  رنگِ انتخاب با جای فنریِ `pos` می‌لغزد
      val w = (1f - kotlin.math.abs(i - pos.value)).coerceIn(0f, 1f)
      if (bh > 0.5f) drawRoundRect(lerp(neutral, brand, w), Offset(center - barW / 2f, bottom - bh), Size(barW, bh), radius)
      val lt = if (i == selected) labelSel[i] else labelText[i]
      drawText(lt, topLeft = Offset(center - lt.size.width / 2f, size.height - lt.size.height.toFloat()))
    }
    //  برچسبِ عددِ دقیق: بالای ستونِ انتخاب‌شده، با همان فنر جابه‌جا
    val tt = tips[selected.coerceIn(0, tips.lastIndex)]
    val center = centerOf(pos.value)
    val bw = tt.size.width + 16.dp.toPx()
    val x = (center - bw / 2f).coerceIn(0f, size.width - bw)
    val y = (bottom - heightOf(selected.coerceIn(0, values.lastIndex)) - tipH - 6.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(tipBg, Offset(x, y), Size(bw, tipH), radius)
    drawText(tt, topLeft = Offset(x + 8.dp.toPx(), y + (tipH - tt.size.height) / 2f))
  }
}

private val AXIS_W = 44.dp

/**
 *  پخشِ ساعتی: میله‌های کوچک (بلندی = شمارِ فاکتور)، پُرفروش‌ترین خانه به رنگِ برند با
 *  نشانگرِ مثلثی بالایش، و ساعتِ آغازِ هر خانه زیرش.
 */
@Composable
internal fun HourStrip(bins: List<HourBin>, peak: HourBin?, growth: Growth, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val max = bins.maxOfOrNull { it.count } ?: 0
  val brand = chartColor(c, ChartSeries.SALES)
  val neutral = chartColor(c, ChartSeries.NEUTRAL)
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
    bins.forEachIndexed { i, b ->
      val on = b == peak
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(10.dp), contentAlignment = Alignment.Center) {
          if (on) Canvas(Modifier.width(10.dp).height(7.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close() }, c.ink)
          }
        }
        Spacer(Modifier.height(4.dp))
        val f = if (max == 0) 0f else b.count.toFloat() / max
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.BottomCenter) {
          //  بلندیِ پایانی ثابت؛ رشد از خطِ پایه با `scaleY` (بی چیدمانِ دوباره)
          Box(
            Modifier.fillMaxWidth().height((8 + 36 * f).dp)
              .graphicsLayer { scaleY = growth.at(i); transformOrigin = TransformOrigin(0.5f, 1f) }
              .clip(RoundedCornerShape(6.dp)).background(if (on) brand else neutral),
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(b.hour.toString().padStart(2, '0'), style = V3Type.caption, color = if (on) c.ink else c.inkMuted, maxLines = 1)
      }
    }
  }
}

/** «۱۶:۰۰ تا ۱۷:۰۰» ِ یک خانه */
internal fun hourSpan(b: HourBin): String =
  StrReports.rangeSpan("${b.hour.toString().padStart(2, '0')}:00", "${(b.hour + b.span).coerceAtMost(24).toString().padStart(2, '0')}:00")
