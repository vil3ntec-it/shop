package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrDesk
import ir.vil3ntec.tohid.plain

/*
 *  اجزای کوچکِ کامپیوتر — فقط از [DeskStyle] (فاصله، گِردی، قلم، رنگ). هر صفحهٔ کامپیوتری این‌ها را می‌کشد، نه
 *  کارت/دکمهٔ گوشی را. حالت‌ها: زیرِ ماوس، فشرده، انتخاب‌شده و حلقهٔ تمرکز فقط وقتی با صفحه‌کلید آمده‌اند ([deskClickable]).
 *  بی انیمیشنِ پیوسته، بی تاری؛ سایه فقط روی کارت ([deskCard])، نه روی ردیف.
 */

/** کارت: سطحِ ۱، خطِ مویی و سایهٔ خیلی نرم */
fun Modifier.deskCard(c: DeskColors, shape: Shape = DeskRadius.cardShape, fill: Color = c.surface): Modifier {
  val soft = c.shadow.copy(alpha = c.shadow.alpha * 0.45f)
  return this
    .shadow(2.dp, shape, clip = false, ambientColor = soft, spotColor = soft)
    .clip(shape)
    .background(fill, shape)
    .border(DeskSize.hairline, c.hairline, shape)
}

/**
 *  زدنیِ کامپیوتری: زمینهٔ [rest] ⇐ زیرِ ماوس ⇐ فشرده ⇐ انتخاب‌شده، و حلقهٔ تمرکز فقط وقتی کاربر با صفحه‌کلید
 *  (Tab) آمده — نه پس از هر کلیک. Enter/Space همان کلیک است.
 */
fun Modifier.deskClickable(
  label: String? = null,
  role: Role = Role.Button,
  shape: Shape = DeskRadius.controlShape,
  enabled: Boolean = true,
  selected: Boolean = false,
  rest: Color = Color.Transparent,
  hover: Color? = null,
  selectedFill: Color? = null,
  onClick: () -> Unit,
): Modifier = composed {
  val c = Desk.colors
  val src = remember { MutableInteractionSource() }
  val hovered by src.collectIsHoveredAsState()
  val pressed by src.collectIsPressedAsState()
  val focused by src.collectIsFocusedAsState()
  val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
  val fill = when {
    selected -> selectedFill ?: c.selected
    !enabled -> rest
    pressed -> c.pressed
    hovered -> hover ?: c.hover
    else -> rest
  }
  this
    .clip(shape)
    .background(fill, shape)
    .then(if (focused && keyboard) Modifier.border(DeskSize.focusRing, c.focus, shape) else Modifier)
    .hoverable(src, enabled)
    .clickable(src, indication = null, enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
}

/** کاشیِ گردگوشهٔ آیکن به رنگِ بخش */
@Composable
fun DeskIconTile(icon: ImageVector, tone: DeskHue, modifier: Modifier = Modifier, size: Dp = DeskSize.tile, iconSize: Dp = DeskSize.tileIcon) {
  Box(modifier.size(size).clip(DeskRadius.tileShape).background(tone.tile), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = tone.glyph, modifier = Modifier.size(iconSize))
  }
}

/** کارت با سرخطِ اختیاری و کارِ کنارِ سرخط («همه»، کلیدِ بازه) */
@Composable
fun DeskCard(
  modifier: Modifier = Modifier,
  title: String? = null,
  icon: ImageVector? = null,
  tone: DeskHue? = null,
  padding: PaddingValues = PaddingValues(DeskSpace.card),
  action: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = Desk.colors
  Column(modifier.deskCard(c).padding(padding)) {
    if (title != null || action != null) {
      Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null && tone != null) {
          DeskIconTile(icon, tone, size = 28.dp, iconSize = 16.dp)
          Spacer(Modifier.width(DeskSpace.x3))
        }
        if (title != null) DeskSectionTitle(title, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        action?.invoke(this)
      }
      Spacer(Modifier.height(DeskSpace.x3))
    }
    content()
  }
}

/** سرخطِ کارت/بخش — ۱۶ نیم‌پررنگ، یک خط */
@Composable
fun DeskSectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Desk.colors.ink) {
  Text(text, modifier, style = DeskType.sectionTitle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** میان‌برِ صفحه‌کلید («Ctrl+2») — همیشه چپ‌به‌راست، روی دکمهٔ اصلی با [onPrimary] */
@Composable
fun DeskShortcut(keys: String, modifier: Modifier = Modifier, onPrimary: Boolean = false) {
  val c = Desk.colors
  val fg = if (onPrimary) c.onPrimary else c.inkSecondary
  val bg = if (onPrimary) c.onPrimary.copy(alpha = 0.16f) else c.surfaceAlt
  androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Text(
      keys,
      modifier
        .clip(RoundedShape4)
        .background(bg)
        .then(if (onPrimary) Modifier else Modifier.border(DeskSize.hairline, c.hairline, RoundedShape4))
        .padding(horizontal = 6.dp, vertical = 1.dp),
      style = DeskType.shortcut,
      color = fg,
      maxLines = 1,
    )
  }
}

private val RoundedShape4 = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)

enum class DeskButtonKind { PRIMARY, SECONDARY, GHOST }

/** دکمهٔ کامپیوتر (۳۶dp): اصلی (پُر)، دوم (خطِ دور) یا بی‌زمینه — با آیکن و میان‌برِ اختیاری */
@Composable
fun DeskButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  kind: DeskButtonKind = DeskButtonKind.SECONDARY,
  shortcut: String? = null,
  enabled: Boolean = true,
) {
  val c = Desk.colors
  val primary = kind == DeskButtonKind.PRIMARY
  val fg = when {
    !enabled -> c.inkMuted
    primary -> c.onPrimary
    else -> c.ink
  }
  val rest = when (kind) {
    DeskButtonKind.PRIMARY -> if (enabled) c.primary else c.surfaceAlt
    DeskButtonKind.SECONDARY -> c.surface
    DeskButtonKind.GHOST -> Color.Transparent
  }
  val hover = if (primary) androidx.compose.ui.graphics.lerp(c.primary, c.ink, if (c.isDark) 0.12f else 0.18f) else c.hover
  Row(
    modifier
      .heightIn(min = DeskSize.control)
      .then(if (kind == DeskButtonKind.SECONDARY) Modifier.border(DeskSize.hairline, c.hairline, DeskRadius.controlShape) else Modifier)
      .deskClickable(label = text, enabled = enabled, rest = rest, hover = hover, onClick = onClick)
      .padding(horizontal = DeskSpace.x3 + 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(DeskSpace.x2))
    }
    Text(text, style = DeskType.bodyStrong, color = fg, maxLines = 1)
    if (shortcut != null) {
      Spacer(Modifier.width(DeskSpace.x2))
      DeskShortcut(shortcut, onPrimary = primary && enabled)
    }
  }
}

/** «همه ›» و پیوندهای کوچکِ کنارِ سرخط */
@Composable
fun DeskLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val c = Desk.colors
  Text(
    text,
    modifier.deskClickable(label = text, role = Role.Button, onClick = onClick).padding(horizontal = DeskSpace.x2, vertical = DeskSpace.x1),
    style = DeskType.label,
    color = c.brandInk,
    maxLines = 1,
  )
}

/** نوعِ نشانِ شمار: هشدار (سرخِ پُر)، رنگِ بخش (نرم)، خنثی */
enum class DeskBadgeKind { ALERT, HUE, NEUTRAL }

/** شمارِ کوچکِ کنارِ ردیفِ منو یا سرخط — بیش از ۹۹ ⇒ «۹۹+» */
@Composable
fun DeskBadge(count: Int, modifier: Modifier = Modifier, kind: DeskBadgeKind = DeskBadgeKind.ALERT, tone: DeskHue? = null) {
  if (count <= 0) return
  val c = Desk.colors
  val (bg, fg) = when (kind) {
    DeskBadgeKind.ALERT -> c.alert to c.onAlert
    DeskBadgeKind.HUE -> (tone?.tile ?: c.selected) to (tone?.ink ?: c.brandInk)
    DeskBadgeKind.NEUTRAL -> c.surfaceAlt to c.inkSecondary
  }
  val text = if (count > 99) plain(99) + "+" else plain(count)
  Text(
    text,
    modifier.clip(DeskRadius.pillShape).background(bg).widthIn(min = 20.dp).padding(horizontal = 6.dp, vertical = 1.dp),
    style = DeskType.badge,
    color = fg,
    maxLines = 1,
    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
  )
}

/**
 *  تغییر نسبت به دیروز (یا بازهٔ پیشین): پیکان + درصد، سبز برای بهتر و سرخ برای بدتر — رنگ هرگز تنها نیست (پیکان و
 *  نوشتهٔ «نسبت به …» کنارش). [pct] `null` ⇒ مقایسه بی‌معناست (پایهٔ صفر) ⇒ فقط [fallback].
 */
@Composable
fun DeskDelta(pct: Int?, versus: String, modifier: Modifier = Modifier, higherIsBetter: Boolean = true, fallback: String? = null) {
  val c = Desk.colors
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    if (pct == null) {
      if (fallback != null) Text(fallback, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      return@Row
    }
    val good = if (pct == 0) null else (pct > 0) == higherIsBetter
    val color = when (good) { true -> c.positive; false -> c.negative; null -> c.inkSecondary }
    val soft = when (good) { true -> c.positiveSoft; false -> c.negativeSoft; null -> c.surfaceAlt }
    val icon = when {
      pct > 0 -> Icons.AutoMirrored.Filled.TrendingUp
      pct < 0 -> Icons.AutoMirrored.Filled.TrendingDown
      else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    Row(
      Modifier.clip(DeskRadius.pillShape).background(soft).padding(horizontal = 6.dp, vertical = 1.dp)
        .semantics(mergeDescendants = true) { contentDescription = StrDesk.deltaA11y(pct, versus) },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      //  پیکانِ روند همیشه چپ‌به‌راست (بالا رفتن = راست‌بالا)، مثلِ نمودار
      androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
      }
      Spacer(Modifier.width(3.dp))
      Text(StrDesk.percent(kotlin.math.abs(pct)), style = DeskType.badge.copy(fontSize = DeskType.caption.fontSize), color = color, maxLines = 1)
    }
    Spacer(Modifier.width(DeskSpace.x2))
    Text(versus, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/**
 *  خطِ کوچکِ روند (بی محور، بی انیمیشن): [values] کهنه‌ترین اول. در راست‌به‌چپ هم زمان از چپ به راست می‌رود (مثلِ هر
 *  نمودارِ زمانی) تا با پیکانِ [DeskDelta] یکی باشد. همه صفر ⇒ خطِ صافِ کم‌رنگ.
 */
@Composable
fun DeskSparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
  val c = Desk.colors
  val flat = values.none { it > 0.0 }
  Canvas(modifier) {
    if (values.size < 2) return@Canvas
    val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val min = minOf(0.0, values.minOrNull() ?: 0.0)
    val stroke = 2.dp.toPx()
    val h = size.height - stroke
    val stepX = size.width / (values.size - 1)
    val path = Path()
    values.forEachIndexed { i, v ->
      val x = i * stepX
      val y = stroke / 2 + h - ((v - min) / (max - min) * h).toFloat()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (flat) {
      drawLine(c.grid, Offset(0f, size.height - stroke), Offset(size.width, size.height - stroke), strokeWidth = stroke)
    } else {
      drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
      val lastY = stroke / 2 + h - ((values.last() - min) / (max - min) * h).toFloat()
      drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width, lastY))
    }
  }
}

/** راهنمای هاور (نام + میان‌بر) — برای منوی جمع‌شده، دکمه‌های تک‌آیکن و کارهای سریع */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeskTooltip(text: String, shortcut: String? = null, content: @Composable () -> Unit) {
  TooltipBox(
    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
    tooltip = {
      PlainTooltip {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(text, style = DeskType.secondary)
          if (shortcut != null) {
            Spacer(Modifier.width(DeskSpace.x2))
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
              Text(shortcut, style = DeskType.shortcut)
            }
          }
        }
      }
    },
    state = rememberTooltipState(),
  ) { content() }
}

/** خطِ مویی میانِ ردیف‌ها */
@Composable
fun DeskDivider(modifier: Modifier = Modifier, color: Color = Desk.colors.divider) {
  Box(modifier.fillMaxWidth().height(DeskSize.hairline).background(color))
}

/** کنترلِ قطعه‌ایِ کوچک (۷ روز / ۳۰ روز) — شیارِ [DeskColors.surfaceAlt] و گزینهٔ انتخاب‌شدهٔ برآمده */
@Composable
fun <T> DeskSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
  val c = Desk.colors
  Row(modifier.clip(DeskRadius.controlShape).background(c.surfaceAlt).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    options.forEach { (value, label) ->
      val on = value == selected
      Text(
        label,
        Modifier
          .then(if (on) Modifier.shadow(1.dp, RoundedShape6, clip = false, ambientColor = c.shadow, spotColor = c.shadow) else Modifier)
          .deskClickable(label = label, role = Role.Tab, shape = RoundedShape6, selected = on, selectedFill = c.surface) { onSelect(value) }
          .padding(horizontal = DeskSpace.x3, vertical = 4.dp),
        style = DeskType.label,
        color = if (on) c.ink else c.inkSecondary,
        maxLines = 1,
      )
    }
  }
}

private val RoundedShape6 = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)

/**
 *  کارتِ عددِ کلیدی (KPI): کاشیِ رنگِ بخش، برچسب، عددِ درشتِ هم‌پهنا، تغییر و خطِ کوچکِ روند — تخت، با نوارِ رنگیِ بالا.
 *  زدن (اختیاری) صفحهٔ همان عدد را باز می‌کند.
 */
@Immutable
data class DeskKpi(
  val label: String,
  val value: String,
  val icon: ImageVector,
  val tone: DeskHue,
  val pct: Int? = null,
  val versus: String = "",
  val fallback: String? = null,
  val higherIsBetter: Boolean = true,
  val spark: List<Double> = emptyList(),
  val unit: String? = null,
)

/** [footer] (اختیاری): کارهای کوچکِ زیرِ عدد (مثلاً «دریافت»/«پرداخت» ِ کارتِ نقد) — جدا از زدنِ خودِ کارت */
@Composable
fun DeskKpiCard(kpi: DeskKpi, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, footer: (@Composable RowScope.() -> Unit)? = null) {
  val c = Desk.colors
  Column(
    modifier
      .deskCard(c)
      .then(if (onClick != null) Modifier.deskClickable(label = kpi.label, shape = DeskRadius.cardShape, onClick = onClick) else Modifier)
      .drawAccentTop(kpi.tone.solid)
      .padding(start = DeskSpace.card, end = DeskSpace.card, top = DeskSpace.card - 2.dp, bottom = DeskSpace.x4),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      DeskIconTile(kpi.icon, kpi.tone, size = 32.dp, iconSize = 18.dp)
      Spacer(Modifier.width(DeskSpace.x3))
      Text(kpi.label, Modifier.weight(1f), style = DeskType.label, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(DeskSpace.x3))
    Row(verticalAlignment = Alignment.Bottom) {
      Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
        Text(kpi.value, style = DeskType.kpi, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
        if (kpi.unit != null) {
          Spacer(Modifier.width(DeskSpace.x1))
          Text(kpi.unit, style = DeskType.bodyStrong, color = c.inkSecondary, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
      }
      if (kpi.spark.size >= 2) {
        Spacer(Modifier.width(DeskSpace.x3))
        DeskSparkline(kpi.spark, kpi.tone.solid, Modifier.width(72.dp).height(28.dp).padding(bottom = 4.dp))
      }
    }
    Spacer(Modifier.height(DeskSpace.x2))
    DeskDelta(kpi.pct, kpi.versus, higherIsBetter = kpi.higherIsBetter, fallback = kpi.fallback)
    if (footer != null) {
      Spacer(Modifier.weight(1f).heightIn(min = DeskSpace.x3))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2), content = footer)
    }
  }
}

/** نوارِ رنگیِ لبهٔ بالای کارت (درونِ گوشهٔ گرد؛ `clip` ِ کارت بیرونش را می‌بُرد) */
private fun Modifier.drawAccentTop(color: Color): Modifier = this.drawBehind {
  drawRect(color, size = androidx.compose.ui.geometry.Size(size.width, DeskSize.accentBar.toPx()))
}
