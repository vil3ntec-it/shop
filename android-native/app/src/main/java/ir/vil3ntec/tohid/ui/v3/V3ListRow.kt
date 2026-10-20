package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.iconTile
import ir.vil3ntec.tohid.ui.screens.pressed
import ir.vil3ntec.tohid.ui.screens.v3Press
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** رنگِ نشانهٔ کاشی (VILL3N r7) — `null` یعنی خاکستریِ تک‌رنگِ r5 */
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
  Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(c.iconTile), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.53f))
  }
}

/**
 *  ردیفِ فهرستِ نسخهٔ ۳ — یک پیاده‌سازی برای «بیشتر»، «تنظیمات»، انبار، و هر
 *  فهرستِ تازه: کاشیِ آیکن (یا `leading`ِ دلخواه مثلِ عکسِ کالا)، عنوان و
 *  زیرعنوان، و در پایان مقدار/قرص/کلید و فلشِ «جلو» (آینه‌شده در راست‌به‌چپ).
 *
 *  `swipeActions`: کشیدنِ ردیف به سمتِ آغاز کارها را از سمتِ پایان نشان می‌دهد
 *  (مثلِ iOS، آینه‌شده)؛ کشیدنِ تا بیش از نیمه کارِ اول را بی‌درنگ اجرا می‌کند،
 *  هر دو با لرزشِ کوتاه. کارها برای صفحه‌خوان هم «کارِ سفارشی»اند.
 */
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
  swipeActions: List<V3SwipeAction> = emptyList(),
  subtitleLines: Int = 2,
  onClick: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val body: @Composable (Modifier) -> Unit = { m ->
    Row(
      m
        .fillMaxWidth()
        .heightIn(min = minHeight)
        .background(c.card)
        .v3Press(c.pressed, label = title, onClick = onClick)
        .padding(start = if (tile || leading != null) 12.dp else 22.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      when {
        leading != null -> { leading(); Spacer(Modifier.width(12.dp)) }
        icon != null && tile -> { V3IconTile(icon, hue, danger); Spacer(Modifier.width(12.dp)) }
        icon != null -> {
          Icon(icon, contentDescription = null, tint = if (danger) c.negative else c.icon, modifier = Modifier.size(24.dp))
          Spacer(Modifier.width(14.dp))
        }
      }
      Column(Modifier.weight(1f)) {
        Text(title, style = v3Text(15, FontWeight.Medium, 22), color = if (danger) c.negative else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
          Text(subtitle, style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted, maxLines = subtitleLines, overflow = TextOverflow.Ellipsis)
        }
      }
      if (value != null) {
        Spacer(Modifier.width(8.dp))
        Text(
          value,
          style = v3Text(13, FontWeight.Medium, 18),
          color = valueColor ?: c.inkMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 160.dp),
        )
      }
      if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
      if (onClick != null && chevron && trailing == null) {
        Spacer(Modifier.width(4.dp))
        V3Chevron(if (tile) c.chevron else c.icon)
      }
    }
  }
  if (swipeActions.isEmpty()) body(modifier) else V3Swipeable(swipeActions, modifier) { body(Modifier) }
}

/** فلشِ «جلو»: در راست‌به‌چپ رو به چپ */
@Composable
fun V3Chevron(tint: Color = PhoneV3.colors.chevron) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Icon(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

private val ACTION_WIDTH = 76.dp

@Composable
private fun V3Swipeable(actions: List<V3SwipeAction>, modifier: Modifier, content: @Composable () -> Unit) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  //  «پایان» در راست‌به‌چپ چپ است؛ ردیف به سمتِ آغاز می‌رود تا کارها از پایان پیدا شوند
  val toStart = if (rtl) 1f else -1f
  val open = with(density) { ACTION_WIDTH.toPx() } * actions.size
  val offset = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  val width = remember { mutableFloatStateOf(1f) }
  fun settle(target: Float) = scope.launch { offset.animateTo(target, MotionSpec.sheet()) }
  fun close() = settle(0f)

  Box(
    modifier
      .fillMaxWidth()
      .semantics {
        customActions = actions.map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
      },
  ) {
    //  کارها زیرِ ردیف، در سمتِ پایان
    Row(
      Modifier.matchParentSize().background(c.tile),
      horizontalArrangement = Arrangement.End,
    ) {
      actions.forEach { a ->
        val pair = PhoneV3Roles.button(c, a.kind)
        Column(
          Modifier
            .width(ACTION_WIDTH)
            .fillMaxHeight()
            .background(pair.bg)
            .v3Pressable(label = a.label, haptic = true) { close(); a.onClick() },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(a.icon, contentDescription = null, tint = pair.fg, modifier = Modifier.size(20.dp))
          Text(a.label, style = v3Text(11, FontWeight.SemiBold, 15), color = pair.fg, maxLines = 1)
        }
      }
    }
    Box(
      Modifier
        .offset { IntOffset(offset.value.roundToInt(), 0) }
        .draggable(
          orientation = Orientation.Horizontal,
          state = rememberDraggableState { delta ->
            val revealed = (offset.value + delta) * toStart
            //  فقط به سمتِ آغاز؛ کمی کشِ بیش از اندازه تا «اجرای کامل»
            val clamped = revealed.coerceIn(0f, width.floatValue * 0.85f)
            scope.launch { offset.snapTo(clamped * toStart) }
          },
          onDragStopped = { velocity ->
            val revealed = offset.value * toStart
            when {
              revealed > width.floatValue * 0.55f -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                close()
                actions.first().onClick()
              }
              revealed > open / 2 || velocity * toStart > 1200f -> settle(open * toStart)
              else -> close()
            }
          },
        )
        .onSizeChanged { width.floatValue = it.width.toFloat() },
    ) { content() }
  }
}
