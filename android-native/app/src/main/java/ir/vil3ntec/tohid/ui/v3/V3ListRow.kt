package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.iconTile
import ir.vil3ntec.tohid.ui.screens.pressed
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** رنگِ نشانهٔ کاشی (VILL3N r7: برند، سبزآبی، آبی، نارنجی، بنفش) — `null` یعنی خاکستریِ r5 */
typealias V3Hue = PhoneV3Palette.Hue

/** کارِ کشیدنی روی ردیف (ویرایش، حذف، بیشتر) */
data class V3SwipeAction(
  val icon: ImageVector,
  val label: String,
  val kind: V3ButtonKind = V3ButtonKind.NEUTRAL,
  val onClick: () -> Unit,
)

/** کاشیِ آیکنِ ردیف: سطحِ درونی (surfaceElevated) با نشانهٔ رنگی یا خاکستری */
@Composable
fun V3IconTile(icon: ImageVector, hue: V3Hue? = null, danger: Boolean = false, size: Dp = 40.dp) {
  val c = PhoneV3.colors
  val tint = when {
    danger -> c.negative
    hue != null -> PhoneV3Palette.glyph(c, hue)
    else -> c.icon
  }
  //  THEME-V2: آیکنِ رنگی روی پس‌زمینهٔ نرمِ همان رنگ
  val bg = if (hue != null && !danger) PhoneV3Palette.soft(c, hue) else c.iconTile
  Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.53f))
  }
}

/**
 *  ردیفِ فهرستِ نسخهٔ ۳ — یک پیاده‌سازی برای «بیشتر»، «تنظیمات» و هر فهرستِ تازه:
 *  `leading`ِ دلخواه (عکسِ کالا `V3ProductThumb`، آواتار) یا کاشیِ آیکن، عنوان و
 *  زیرعنوان، و در پایان مقدار، `trailing`ِ دلخواه (قرص، کلید، مبلغ) و فلشِ «جلو»
 *  (آینه در راست‌به‌چپ).
 *
 *  • `endActions`/`startActions`: کشیدنِ ردیف به سمتِ آغاز کارهای پایان را نشان
 *    می‌دهد و برعکس (مثلِ iOS، آینه‌شده)؛ گذشتن از آستانه یک تیکِ لرزشی می‌دهد و
 *    کشیدنِ تا بیش از نیمه کارِ اولِ همان سو را اجرا می‌کند. همه برای صفحه‌خوان
 *    «کارِ سفارشی»اند.
 *  • `selected`: حالتِ انتخابِ گروهی — دایرهٔ تیک در آغاز (null = بی انتخاب).
 *  • `onLongClick`: نگه‌داشتن (معمولاً شروعِ انتخاب یا برگهٔ کارها).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun V3ListRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  hue: V3Hue? = null,
  /** false ⇒ آیکنِ بی‌کاشی (فهرستِ r4) */
  tile: Boolean = true,
  leading: (@Composable () -> Unit)? = null,
  value: String? = null,
  valueColor: Color? = null,
  trailing: (@Composable () -> Unit)? = null,
  danger: Boolean = false,
  chevron: Boolean = true,
  minHeight: Dp = PhoneV3.rowHeight,
  subtitleLines: Int = 2,
  startActions: List<V3SwipeAction> = emptyList(),
  endActions: List<V3SwipeAction> = emptyList(),
  selected: Boolean? = null,
  onLongClick: (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val haptic = LocalHapticFeedback.current
  val body: @Composable (Modifier) -> Unit = { m ->
    Row(
      m
        .fillMaxWidth()
        .heightIn(min = minHeight)
        .background(if (selected == true) PhoneV3Palette.selected(c) else c.card)
        .then(
          if (onClick != null || onLongClick != null) Modifier.rowClick(c.pressed, title, onClick ?: {}, onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } })
          else Modifier,
        )
        .semantics { if (selected != null) this.selected = selected }
        .padding(start = if (tile || leading != null || selected != null) 12.dp else 22.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AnimatedVisibility(selected != null, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
        Row { SelectionDot(selected == true); Spacer(Modifier.width(12.dp)) }
      }
      when {
        leading != null -> { leading(); Spacer(Modifier.width(12.dp)) }
        icon != null && tile -> { V3IconTile(icon, hue, danger); Spacer(Modifier.width(12.dp)) }
        icon != null -> {
          Icon(icon, contentDescription = null, tint = if (danger) c.negative else c.icon, modifier = Modifier.size(24.dp))
          Spacer(Modifier.width(14.dp))
        }
      }
      Column(Modifier.weight(1f)) {
        Text(title, style = V3Type.body, color = if (danger) c.negative else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
          Text(subtitle, style = V3Type.footnote, color = c.inkMuted, maxLines = subtitleLines, overflow = TextOverflow.Ellipsis)
        }
      }
      if (value != null) {
        Spacer(Modifier.width(8.dp))
        Text(value, style = V3Type.subhead, color = valueColor ?: c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
      }
      if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
      if (onClick != null && chevron && trailing == null && selected == null) {
        Spacer(Modifier.width(4.dp))
        V3Chevron(if (tile) c.chevron else c.icon)
      }
    }
  }
  if (startActions.isEmpty() && endActions.isEmpty()) body(modifier)
  else V3Swipeable(startActions, endActions, modifier) { body(Modifier) }
}

/** پُرشدنِ نرمِ هنگامِ فشار (بی موجِ رنگی) با زدن و نگه‌داشتن */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.rowClick(highlight: Color, label: String, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val bg by animateColorAsState(if (down) highlight else highlight.copy(alpha = 0f), MotionSpec.tweenOf(120), label = "rowPress")
  this
    .combinedClickable(interactionSource = src, indication = null, role = Role.Button, onClickLabel = label, onLongClick = onLongClick, onClick = onClick)
    .background(bg)
}

@Composable
private fun SelectionDot(on: Boolean) {
  val c = PhoneV3.colors
  Box(
    Modifier
      .size(24.dp)
      .clip(CircleShape)
      .background(if (on) PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).bg else c.card)
      .border(1.5.dp, if (on) PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).bg else PhoneV3Roles.inputBorder(c), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).fg, modifier = Modifier.size(16.dp))
  }
}

/** فلشِ «جلو»: در راست‌به‌چپ رو به چپ */
@Composable
fun V3Chevron(tint: Color = PhoneV3.colors.chevron) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Icon(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

private val ACTION_WIDTH = 76.dp

@Composable
private fun V3Swipeable(
  start: List<V3SwipeAction>,
  end: List<V3SwipeAction>,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  //  جابه‌جاییِ فیزیکی به «سمتِ آغاز» در راست‌به‌چپ مثبت (به راست) است
  val toStart = if (rtl) 1f else -1f
  val slot = with(density) { ACTION_WIDTH.toPx() }
  val offset = remember { Animatable(0f) }
  val width = remember { mutableFloatStateOf(1f) }
  val ticked = remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  fun settle(target: Float) = scope.launch { offset.animateTo(target, MotionSpec.sheet()) }

  Box(
    modifier
      .fillMaxWidth()
      .semantics {
        customActions = (end + start).map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
      },
  ) {
    //  کارهای پایان (پیدا با کشیدن به آغاز) و کارهای آغاز (با کشیدن به پایان)
    Row(Modifier.matchParentSize().background(c.tile)) {
      ActionCells(start) { a -> settle(0f); a.onClick() }
      Spacer(Modifier.weight(1f))
      ActionCells(end) { a -> settle(0f); a.onClick() }
    }
    Box(
      Modifier
        .offset { IntOffset(offset.value.roundToInt(), 0) }
        .draggable(
          orientation = Orientation.Horizontal,
          state = rememberDraggableState { delta ->
            //  مثبت = کارهای پایان پیدا می‌شوند؛ منفی = کارهای آغاز
            val revealed = (offset.value + delta) * toStart
            val max = if (end.isEmpty()) 0f else width.floatValue * 0.85f
            val min = if (start.isEmpty()) 0f else -width.floatValue * 0.85f
            val clamped = revealed.coerceIn(min, max)
            val past = kotlin.math.abs(clamped) > width.floatValue * 0.5f
            if (past != ticked.value) {
              ticked.value = past
              if (past) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            scope.launch { offset.snapTo(clamped * toStart) }
          },
          onDragStopped = { velocity ->
            val revealed = offset.value * toStart
            val v = velocity * toStart
            ticked.value = false
            when {
              revealed > width.floatValue * 0.5f && end.isNotEmpty() -> { settle(0f); end.first().onClick() }
              revealed < -width.floatValue * 0.5f && start.isNotEmpty() -> { settle(0f); start.first().onClick() }
              (revealed > slot * end.size / 2 || v > 1200f) && end.isNotEmpty() -> settle(slot * end.size * toStart)
              (revealed < -slot * start.size / 2 || v < -1200f) && start.isNotEmpty() -> settle(-slot * start.size * toStart)
              else -> settle(0f)
            }
          },
        )
        .onSizeChanged { width.floatValue = it.width.toFloat() },
    ) { content() }
  }
}

@Composable
private fun ActionCells(actions: List<V3SwipeAction>, onPick: (V3SwipeAction) -> Unit) {
  val c = PhoneV3.colors
  actions.forEach { a ->
    val pair = PhoneV3Roles.button(c, a.kind)
    Column(
      Modifier
        .width(ACTION_WIDTH)
        .fillMaxHeight()
        .background(pair.bg)
        .v3Pressable(label = a.label, haptic = true) { onPick(a) },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(a.icon, contentDescription = null, tint = pair.fg, modifier = Modifier.size(20.dp))
      Text(a.label, style = V3Type.caption, color = pair.fg, maxLines = 1)
    }
  }
}
