package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  پیش‌نویس‌های حرکتِ گروهِ کالاها برای کیت (MOTION-APPLE.md) — همه پشتِ
 *  `Motion.enabled` (بی‌حرکت ⇐ همان حالتِ پایانی، بی‌درنگ)، فقط APIهای خودِ Compose، و
 *  جابه‌جایی با `graphicsLayer` (نه چیدمانِ دوباره در هر فریم). وقتی پایه همین‌ها را
 *  در `V3Motion` داد، این فایل کنار می‌رود (فهرستِ «Draft» در PR).
 */

/** فنرِ اندازهٔ محتوا (باز و بسته شدنِ کارت، پیدا شدنِ بخش) — بی‌جهش */
private fun <T> sizeSpring() = spring<T>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

/** باز/بسته شدنِ نرمِ اندازه (کارتِ ردیف) */
internal fun Modifier.draftSize(): Modifier = composed { if (Motion.enabled) animateContentSize(sizeSpring()) else this }

/**
 *  لرزشِ خطا: سه رفت‌وبرگشتِ ۸dp در ۳۶۰ms با هر بالا رفتنِ `trigger` + یک ضربِ لرزشی
 *  (`Haptics.confirm`). بی‌حرکت ⇐ فقط لرزشِ گوشی.
 */
internal fun Modifier.draftShake(trigger: Int): Modifier = composed {
  val context = LocalContext.current
  val dx = remember { Animatable(0f) }
  LaunchedEffect(trigger) {
    if (trigger <= 0) return@LaunchedEffect
    Haptics.confirm(context)
    if (!Motion.enabled) return@LaunchedEffect
    dx.snapTo(0f)
    dx.animateTo(0f, keyframes { durationMillis = SHAKE_MS; 8f at 30; -8f at 90; 8f at 150; -8f at 210; 6f at 270; -3f at 320 })
  }
  graphicsLayer { translationX = dx.value * density }
}

private const val SHAKE_MS = 360

/** زدنی که نگه‌داشتن هم دارد (کاشیِ شبکه): کوچک‌شدنِ فنریِ ۰٫۹۷ و لرزشِ نگه‌داشتن */
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

/** درخششِ کوتاهِ «تازه»: surfaceSelected که در ۱٫۲ ثانیه محو می‌شود */
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

/** چرخشِ فنریِ فلشِ «باز/بسته» (۰ ⇐ ۱۸۰ درجه) */
internal fun Modifier.draftFlip(open: Boolean): Modifier = composed {
  val turn by animateFloatAsState(if (open) 180f else 0f, MotionSpec.indicator(), label = "draftFlip")
  graphicsLayer { rotationZ = turn }
}

/** پیدا/پنهان شدنِ یک تکه از صفحه: باز شدنِ فنری + محو (خطا، «بیشتر»، خطِ سود) */
@Composable
internal fun DraftReveal(visible: Boolean, content: @Composable () -> Unit) {
  AnimatedVisibility(
    visible = visible,
    enter = if (Motion.enabled) expandVertically(sizeSpring()) + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) else EnterTransition.None,
    exit = if (Motion.enabled) shrinkVertically(sizeSpring()) + fadeOut(MotionSpec.tweenOf(120)) else ExitTransition.None,
  ) { content() }
}

/** آمدن/رفتنِ یک نشانهٔ کوچک (دایرهٔ تیکِ انتخاب) با بزرگ‌شدنِ فنری */
@Composable
internal fun DraftPop(visible: Boolean, content: @Composable () -> Unit) {
  AnimatedVisibility(
    visible = visible,
    enter = if (Motion.enabled) scaleIn(MotionSpec.morph(), initialScale = 0.6f) + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) else EnterTransition.None,
    exit = if (Motion.enabled) scaleOut(MotionSpec.morph(), targetScale = 0.6f) + fadeOut(MotionSpec.tweenOf(120)) else ExitTransition.None,
  ) { content() }
}

/** پوششِ «در حالِ ساختن…» روی پیش‌نمایش: درخششِ آرامِ رفت‌وبرگشتی (بی‌حرکت ⇐ ثابت) */
@Composable
internal fun DraftBusyVeil(visible: Boolean, label: String) {
  if (!visible) return
  val c = PhoneV3.colors
  val a = if (Motion.enabled) {
    rememberInfiniteTransition(label = "busyVeil").animateFloat(0.18f, 0.5f, remember { infiniteRepeatable(tween<Float>(VEIL_MS, easing = MotionTokens.Standard), RepeatMode.Reverse) }, label = "busy").value
  } else 0.35f
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a }.background(c.card))
    Text(label, style = V3Type.callout, color = c.ink)
  }
}

private const val VEIL_MS = 700
