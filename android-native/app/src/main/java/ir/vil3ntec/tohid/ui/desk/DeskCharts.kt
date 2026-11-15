package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 *  نمودارهای سادهٔ صفحه‌های داده‌ایِ کامپیوتر (`claude/e-desk-d3b`، روشِ `dataviz`): یک رنگ برای یک سری (بی راهنما، سرخطِ کارت نامش است)،
 *  ستونِ نازک با سرِ گرد و پایهٔ صاف، شبکهٔ کم‌رنگ، برچسب و مقدار با رنگِ متن (نه رنگِ سری)، و هاورِ هر ستون. بی انیمیشن.
 */

/* ───────────────────────────── نمودار ───────────────────────────── */

/**
 *  نمودارِ ستونیِ ساده (بی انیمیشن): چهار خطِ شبکه، ستون‌های گوشه‌گردِ یک رنگ، برچسبِ زیر (هر چند تا یکی وقتی جا تنگ است)،
 *  و زیرِ ماوس ستونِ همان نقطه پررنگ با مقدارش. زمان همیشه چپ‌به‌راست (مثلِ هر نمودارِ زمانی)، در راست‌به‌چپ هم.
 */
@Composable
fun DeskBarChart(
  values: List<Double>,
  labels: List<String>,
  color: Color,
  modifier: Modifier = Modifier,
  format: (Double) -> String = { it.toLong().toString() },
  description: String = "",
) {
  val c = Desk.colors
  val measurer = rememberTextMeasurer()
  val axisStyle = DeskType.caption.copy(color = c.inkSecondary)
  val tipStyle = DeskType.caption.copy(color = c.surface)
  var hover by remember(values) { mutableStateOf(-1) }
  val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
  Canvas(
    modifier
      .semantics { contentDescription = description }
      .pointerInput(values) {
        awaitPointerEventScope {
          while (true) {
            val e = awaitPointerEvent()
            val p = e.changes.firstOrNull()?.position ?: continue
            hover = if (e.type == PointerEventType.Exit || values.isEmpty()) -1
            else ((p.x / size.width) * values.size).toInt().coerceIn(0, values.size - 1)
          }
        }
      },
  ) {
    if (values.isEmpty()) return@Canvas
    val labelH = 20.dp.toPx()
    val top = 8.dp.toPx()
    val h = size.height - labelH - top
    val slot = size.width / values.size
    val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
    for (g in 0..3) {
      val y = top + h * g / 3f
      drawLine(c.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
    val step = maxOf(1, Math.ceil(values.size / (size.width / 56.dp.toPx()).toDouble()).toInt())
    values.forEachIndexed { i, v ->
      val bh = (v / max * h).toFloat().coerceAtLeast(if (v > 0) 2f else 0f)
      val x = i * slot + (slot - barW) / 2
      val on = i == hover
      //  سرِ ستون گرد (۴px)، پایه صاف روی خطِ صفر
      val r = minOf(4.dp.toPx(), bh / 2, barW / 2)
      val bar = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
          androidx.compose.ui.geometry.RoundRect(
            left = x, top = top + h - bh, right = x + barW, bottom = top + h,
            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
            bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero,
          ),
        )
      }
      drawPath(bar, if (hover < 0 || on) color else color.copy(alpha = 0.45f))
      if (i % step == 0 || on) {
        val t = measurer.measure(labels.getOrElse(i) { "" }, axisStyle, maxLines = 1)
        drawText(t, topLeft = Offset((i * slot + slot / 2 - t.size.width / 2).coerceIn(0f, size.width - t.size.width), size.height - labelH + 4.dp.toPx()))
      }
    }
    if (hover in values.indices) {
      val t = measurer.measure(labels.getOrElse(hover) { "" } + "  " + format(values[hover]), tipStyle, maxLines = 1)
      val pad = 6.dp.toPx()
      val w = t.size.width + pad * 2
      val x = (hover * slot + slot / 2 - w / 2).coerceIn(0f, size.width - w)
      drawRoundRect(c.ink, topLeft = Offset(x, 0f), size = Size(w, t.size.height + pad), cornerRadius = CornerRadius(6.dp.toPx()))
      drawText(t, topLeft = Offset(x + pad, pad / 2))
    }
  }
}

/** سهمِ چند ردیف (روشِ پرداخت، پرفروش‌ها): برچسب و مقدار در یک خط، نوارِ نازکِ سهم زیرش — یک رنگ */
@Composable
fun DeskShareBars(
  rows: List<Pair<String, Double>>,
  color: Color,
  modifier: Modifier = Modifier,
  format: (Double) -> String,
  /** ردیفِ برگزیده (پالایهٔ جاری) — نوشتهٔ پررنگ */
  selected: Int? = null,
  /** زدنِ ردیف (اختیاری) — مثلاً پالایهٔ همان دسته */
  onPick: ((Int) -> Unit)? = null,
) {
  val c = Desk.colors
  val max = (rows.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
  Column(modifier, verticalArrangement = Arrangement.spacedBy(if (onPick != null) DeskSpace.x1 else DeskSpace.x3)) {
    rows.forEachIndexed { i, (label, v) ->
      Column(
        if (onPick != null) Modifier.deskClickable(label = label, selected = i == selected) { onPick(i) }.padding(horizontal = DeskSpace.x2, vertical = DeskSpace.x2)
        else Modifier,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(label, Modifier.weight(1f), style = if (i == selected) DeskType.bodyStrong else DeskType.secondary, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.width(DeskSpace.x2))
          Text(format(v), style = DeskType.table, color = c.inkSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(DeskSpace.x1 + 2.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(DeskRadius.pillShape).background(c.surfaceAlt)) {
          Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(DeskRadius.pillShape).background(color))
        }
      }
    }
  }
}

