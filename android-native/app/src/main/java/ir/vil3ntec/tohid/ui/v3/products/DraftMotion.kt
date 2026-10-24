package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  سه تکهٔ حرکتی که کیت (`V3Motion`/`V3MotionFx`) هنوز ندارد — با همان فنرها و
 *  واژه‌های کیت، پشتِ `Motion.enabled`، فقط `graphicsLayer`/رسم. وقتی پایه دادشان، این
 *  فایل کنار می‌رود (فهرستِ «Draft» در PR):
 *   • `draftPressable` = `v3Pressable` + نگه‌داشتن (کاشیِ شبکه: انتخابِ گروهی)
 *   • `draftAnimateGridItem` = `v3AnimateItem` برای `LazyVerticalGrid`
 *   • `draftFlash` = درخششِ کوتاهِ ردیفِ تازه‌ثبت؛ `DraftBusyVeil` = پوششِ «در حالِ ساختن…»
 */

/** `v3Pressable` با نگه‌داشتن: کوچک‌شدنِ فنریِ ۰٫۹۷ و لرزشِ نگه‌داشتن */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.draftPressable(label: String, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
  val haptic = LocalHapticFeedback.current
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val s by animateFloatAsState(if (down && Motion.enabled) 0.97f else 1f, MotionSpec.press(), label = "draftPress")
  this
    .graphicsLayer { scaleX = s; scaleY = s }
    .combinedClickable(
      interactionSource = src, indication = null, role = Role.Button, onClickLabel = label,
      onLongClick = onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
      onClick = onClick,
    )
}

/** `v3AnimateItem` برای خانه‌های `LazyVerticalGrid` — همان فنر و محو (کلید لازم است) */
internal fun Modifier.draftAnimateGridItem(scope: LazyGridItemScope): Modifier =
  if (!Motion.enabled) this
  else with(scope) {
    this@draftAnimateGridItem.animateItem(
      fadeInSpec = tween(MotionTokens.MEDIUM),
      placementSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1)),
      fadeOutSpec = tween(MotionTokens.SHORT),
    )
  }

/** درخششِ کوتاهِ «تازه»: surfaceSelected که در ۱٫۲ ثانیه محو می‌شود (فقط رسم) */
internal fun Modifier.draftFlash(on: Boolean, shape: Shape = RectangleShape): Modifier = composed {
  val c = PhoneV3.colors
  val tint = PhoneV3Palette.selected(c)
  val a = remember { Animatable(0f) }
  LaunchedEffect(on) {
    if (on && Motion.enabled) {
      a.snapTo(FLASH_ALPHA)
      a.animateTo(0f, MotionSpec.tweenOf(FLASH_MS))
    }
  }
  drawWithContent {
    drawContent()
    val v = a.value
    if (v > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), tint, alpha = v)
  }
}

private const val FLASH_MS = 1200
private const val FLASH_ALPHA = 0.7f

/** پوششِ «در حالِ ساختن…» روی پیش‌نمایش — با محو می‌آید و می‌رود */
@Composable
internal fun DraftBusyVeil(visible: Boolean, label: String) {
  val c = PhoneV3.colors
  AnimatedVisibility(
    visible = visible,
    enter = if (Motion.enabled) fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) else EnterTransition.None,
    exit = if (Motion.enabled) fadeOut(MotionSpec.tweenOf(MotionTokens.MEDIUM)) else ExitTransition.None,
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Box(Modifier.fillMaxSize().graphicsLayer { alpha = VEIL_ALPHA }.background(c.card))
      Text(label, style = V3Type.callout, color = c.ink)
    }
  }
}

private const val VEIL_ALPHA = 0.55f
