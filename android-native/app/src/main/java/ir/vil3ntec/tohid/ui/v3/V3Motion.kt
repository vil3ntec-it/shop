package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.nav.Move
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import kotlinx.coroutines.delay

/**
 *  حرکت‌های کیتِ نسخهٔ ۳ — از واژه‌نامهٔ `MotionTokens`/`MotionSpec` (`Motion.kt`).
 *
 *  • `V3Transitions.page`: جابه‌جاییِ صفحه‌ها روی یک محور (shared-axis). رفتن به
 *    عمق: صفحهٔ تازه از **سمتِ پایان** می‌آید (راست‌به‌چپ: از چپ — آینهٔ iOS) و
 *    صفحهٔ رفته کمی به سمتِ آغاز می‌رود و محو می‌شود؛ برگشت درست برعکس. تبِ تازه
 *    (نوارِ پایین): محو و کمی بزرگ‌شدن (fade-through).
 *  • `V3ContainerTransform`: کارتِ فهرست تا صفحهٔ جزئیات بزرگ می‌شود و برمی‌گردد.
 *  • `rememberV3Stagger` + `Modifier.v3Stagger`: ورودِ پلکانیِ ردیف‌ها فقط بارِ اول.
 *  • `Modifier.v3Pressable`: کوچک‌شدنِ فنری + لرزشِ اختیاری + زدن.
 *  • `rememberCountUp`: شمارندهٔ عدد (پول/آمار) — عددِ پایانی همیشه دقیق.
 *  با «کاهشِ حرکت» (`Motion.enabled = false`) همه بی‌درنگ‌اند.
 */
object V3Transitions {
  /** جهتِ «پایان» در راست‌به‌چپ منفی است (چپ) */
  private fun endSign(dir: LayoutDirection) = if (dir == LayoutDirection.Rtl) -1 else 1

  fun page(move: Move, dir: LayoutDirection): ContentTransform {
    if (!Motion.enabled) return EnterTransition.None togetherWith ExitTransition.None
    val end = endSign(dir)
    return when (move) {
      Move.PUSH ->
        (slideInHorizontally(MotionSpec.pageOffset) { w -> end * w / 3 } + fadeIn(tween(MotionTokens.MEDIUM, easing = MotionTokens.EmphasizedDecelerate)))
          .togetherWith(slideOutHorizontally(MotionSpec.pageOffset) { w -> -end * w / 8 } + fadeOut(tween(MotionTokens.SHORT, easing = MotionTokens.EmphasizedAccelerate)))
      Move.POP ->
        (slideInHorizontally(MotionSpec.pageOffset) { w -> -end * w / 8 } + fadeIn(tween(MotionTokens.MEDIUM, easing = MotionTokens.EmphasizedDecelerate)))
          .togetherWith(slideOutHorizontally(MotionSpec.pageOffset) { w -> end * w / 3 } + fadeOut(tween(MotionTokens.SHORT, easing = MotionTokens.EmphasizedAccelerate)))
      Move.SWITCH, Move.NONE ->
        (fadeIn(tween(MotionTokens.MEDIUM, delayMillis = 60, easing = MotionTokens.EmphasizedDecelerate)) +
          scaleIn(tween(MotionTokens.MEDIUM, delayMillis = 60, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.98f))
          .togetherWith(fadeOut(tween(90)))
    }
  }
}

/**
 *  جابه‌جاییِ محتوا با همان حرکتِ صفحه — برای زیرصفحه‌های درونِ یک صفحه
 *  (`depth` بیشتر ⇐ رفتن به عمق).
 */
@Composable
fun <T> V3PageSwitch(target: T, depth: (T) -> Int, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
  val dir = LocalLayoutDirection.current
  AnimatedContent(
    targetState = target,
    modifier = modifier,
    transitionSpec = {
      val d0 = depth(initialState)
      val d1 = depth(targetState)
      V3Transitions.page(if (d1 > d0) Move.PUSH else if (d1 < d0) Move.POP else Move.SWITCH, dir)
    },
    label = "v3page",
  ) { content(it) }
}

/**
 *  زدنی با کوچک‌شدنِ فنری (۰٫۹۷) و لرزشِ کوتاهِ اختیاری — همهٔ کاشی‌ها،
 *  ردیف‌ها، کارت‌ها و دکمه‌های کیت همین را دارند.
 */
fun Modifier.v3Pressable(
  enabled: Boolean = true,
  label: String? = null,
  role: Role = Role.Button,
  scale: Float = 0.97f,
  haptic: Boolean = false,
  onClick: () -> Unit,
): Modifier = composed {
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val s by animateFloatAsState(if (down && enabled && Motion.enabled) scale else 1f, MotionSpec.press(), label = "v3press")
  val feedback = LocalHapticFeedback.current
  this
    .graphicsLayer { scaleX = s; scaleY = s }
    .clickable(src, indication = null, enabled = enabled, onClickLabel = label, role = role) {
      if (haptic) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
      onClick()
    }
}

/**
 *  ورودِ پلکانی: هر کلید فقط **یک بار** و فقط تا `STAGGER_MAX` ردیفِ اول با
 *  تأخیر می‌آید؛ ردیفی که با پیمایش دوباره دیده شود بی‌درنگ است (برخلافِ
 *  `StaggeredItem`ِ قدیم که در فهرستِ بلند ردیفِ دور را دیر می‌آورد).
 */
@Stable
class V3StaggerState internal constructor() {
  internal val seen = mutableStateMapOf<Any, Boolean>()
}

@Composable
fun rememberV3Stagger(vararg keys: Any?): V3StaggerState = remember(*keys) { V3StaggerState() }

fun Modifier.v3Stagger(state: V3StaggerState, key: Any, index: Int): Modifier = composed {
  val first = remember(key) { state.seen.put(key, true) == null }
  val animate = first && Motion.enabled && index < MotionTokens.STAGGER_MAX
  val p = remember(key) { Animatable(if (animate) 0f else 1f) }
  LaunchedEffect(key) {
    if (p.value < 1f) {
      delay((index * MotionTokens.STAGGER_STEP).toLong())
      p.animateTo(1f, tween(MotionTokens.LONG, easing = MotionTokens.EmphasizedDecelerate))
    }
  }
  graphicsLayer {
    alpha = p.value
    translationY = (1f - p.value) * 14.dp.toPx()
  }
}

/**
 *  شمارندهٔ عدد: از عددِ پیشین تا عددِ تازه در `COUNT_UP` میلی‌ثانیه. محاسبه
 *  `Double` است (پولِ میلیاردی دقیق می‌ماند) و در پایان دقیقاً خودِ `target`.
 */
@Composable
fun rememberCountUp(target: Double): Double {
  val from = remember { doubleArrayOf(if (Motion.enabled) 0.0 else target) }
  val progress = remember { Animatable(if (Motion.enabled) 0f else 1f) }
  val start = from[0]
  LaunchedEffect(target) {
    if (!Motion.enabled) { progress.snapTo(1f); from[0] = target; return@LaunchedEffect }
    progress.snapTo(0f)
    progress.animateTo(1f, tween(MotionTokens.COUNT_UP, easing = MotionTokens.EmphasizedDecelerate))
    from[0] = target
  }
  val p = progress.value
  return if (p >= 1f) target else start + (target - start) * p
}

/**
 *  بزرگ‌شدنِ کارت تا صفحه (container transform): فهرست با `Modifier.v3Container(key)`
 *  روی هر کارت، و جزئیات با همان کلید روی ظرفِ خودش؛ انتخاب که عوض شود، مرزِ
 *  کارت تا مرزِ جزئیات می‌کشد. بی‌حرکت ⇐ جابه‌جاییِ ساده.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class V3ContainerScope internal constructor(
  internal val shared: SharedTransitionScope,
  internal val visibility: AnimatedVisibilityScope,
) {
  @Composable
  fun Modifier.v3Container(key: Any): Modifier =
    if (!Motion.enabled) this else with(shared) {
      this@v3Container.sharedBounds(
        rememberSharedContentState(key),
        visibility,
        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
      )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <K : Any> V3ContainerTransform(
  selected: K?,
  modifier: Modifier = Modifier,
  list: @Composable V3ContainerScope.() -> Unit,
  detail: @Composable V3ContainerScope.(K) -> Unit,
) {
  SharedTransitionLayout(modifier) {
    AnimatedContent(
      targetState = selected,
      transitionSpec = {
        if (!Motion.enabled) EnterTransition.None togetherWith ExitTransition.None
        else fadeIn(tween(MotionTokens.MEDIUM)) togetherWith fadeOut(tween(MotionTokens.SHORT))
      },
      label = "v3container",
    ) { key ->
      val scope = V3ContainerScope(this@SharedTransitionLayout, this)
      if (key == null) scope.list() else scope.detail(key)
    }
  }
}

