package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import kotlin.math.abs

/*
 *  حرکت‌های «مثلِ اپل» که کیت به همه می‌دهد (MOTION-APPLE.md) — فقط APIهای خودِ
 *  Compose، فقط `graphicsLayer` (بی چیدمانِ دوباره در هر فریم)، همه پشتِ `Motion.enabled`
 *  (کاهشِ حرکت ⇐ همان حالتِ پایانی، بی‌درنگ). فنرها قطع‌شدنی‌اند: هدفِ تازه از همان
 *  جا و همان سرعت ادامه می‌دهد، هیچ‌وقت منتظرِ پایانِ حرکتِ قبلی نمی‌ماند.
 */

/** فنرهای کیت: `ui` برای جا و اندازه (بی جهشِ دیدنی)، `playful` فقط برای تأییدها (تیکِ موفقیت، افزودن به سبد) */
object V3Springs {
  fun <T> ui(): FiniteAnimationSpec<T> = if (Motion.enabled) spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) else snap()
  fun <T> playful(): FiniteAnimationSpec<T> = if (Motion.enabled) spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium) else snap()
}

/** شمارِ برگه‌های باز (`V3Sheet` خودش می‌شمارد) — صفحهٔ پشتِ برگه کوچک می‌شود */
object V3Depth {
  internal val sheets = mutableIntStateOf(0)
}

/** صفحهٔ پشتِ برگه تا ۰٫۹۶ (پوسته روی صفحهٔ زیرین گذاشته است؛ صفحه لازم نیست کاری کند) */
fun Modifier.v3BehindSheet(): Modifier = composed {
  val s by animateFloatAsState(if (V3Depth.sheets.intValue > 0 && Motion.enabled) 0.96f else 1f, MotionSpec.sheet(), label = "behindSheet")
  graphicsLayer { scaleX = s; scaleY = s }
}

/**
 *  کارتِ ورق (HorizontalPager) که پا‌به‌پای انگشت می‌آید: کارتِ کناری کمی کوچک‌تر و
 *  کم‌رنگ‌تر است و با کشیدن، درست به اندازهٔ جابه‌جایی بزرگ و پررنگ می‌شود.
 */
fun Modifier.v3PagerItem(state: PagerState, page: Int, minScale: Float = 0.92f, minAlpha: Float = 0.6f): Modifier =
  graphicsLayer {
    if (!Motion.enabled) return@graphicsLayer
    val d = abs((state.currentPage - page) + state.currentPageOffsetFraction).coerceIn(0f, 1f)
    val k = 1f - d
    scaleX = minScale + (1f - minScale) * k
    scaleY = scaleX
    alpha = minAlpha + (1f - minAlpha) * k
  }

/**
 *  رشدِ نمودار از خطِ پایه: `val p = rememberV3Grow(data)` و در رسم، بلندیِ ستون × `p`
 *  (یا `Modifier.v3GrowFromBaseline { p }` روی کلِ نمودار). با دادهٔ تازه دوباره رشد می‌کند.
 */
@Composable
fun rememberV3Grow(key: Any?): Float {
  val p = remember { Animatable(if (Motion.enabled) 0f else 1f) }
  LaunchedEffect(key) {
    if (!Motion.enabled) { p.snapTo(1f); return@LaunchedEffect }
    p.snapTo(0f)
    p.animateTo(1f, tween(MotionTokens.EMPHASIZED + 250, easing = MotionTokens.EmphasizedDecelerate))
  }
  return p.value
}

fun Modifier.v3GrowFromBaseline(progress: () -> Float): Modifier =
  graphicsLayer { scaleY = progress(); transformOrigin = TransformOrigin(0.5f, 1f) }

/**
 *  لرزشِ خطا: هر بار که `trigger` عوض شود (شمارنده، پیامِ خطا، …) سه رفت‌وبرگشتِ ۸dp
 *  افقی و یک لرزشِ «خطا». `null` یعنی هیچ. بی‌حرکت ⇐ فقط لرزش.
 */
fun Modifier.v3Shake(trigger: Any?): Modifier = composed {
  val x = remember { Animatable(0f) }
  val context = LocalContext.current
  val px = with(LocalDensity.current) { 8.dp.toPx() }
  LaunchedEffect(trigger) {
    if (trigger == null) return@LaunchedEffect
    Haptics.error(context)
    if (!Motion.enabled) return@LaunchedEffect
    x.snapTo(0f)
    x.animateTo(0f, keyframes {
      durationMillis = 360
      px at 45; -px at 105; px at 165; -px at 225; px at 285; 0f at 360
    })
  }
  graphicsLayer { translationX = x.value }
}

/**
 *  ردیفِ LazyColumn/LazyRow که آمدن، رفتن و جابه‌جا شدنش حرکت دارد —
 *  `items(list, key = { it.id }) { Row(Modifier.v3AnimateItem(this)) }` (کلید لازم است).
 */
fun Modifier.v3AnimateItem(scope: LazyItemScope): Modifier =
  if (!Motion.enabled) this
  else with(scope) {
    this@v3AnimateItem.animateItem(
      fadeInSpec = tween(MotionTokens.MEDIUM),
      placementSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1)),
      fadeOutSpec = tween(MotionTokens.SHORT),
    )
  }

/**
 *  آمدن/رفتنِ یک ردیف در فهرستِ غیرِ Lazy (و «کشیدن برای حذف»): بلندی نرم جمع و باز
 *  می‌شود، ردیف‌های زیرین بی‌پرش بالا می‌آیند. بی‌حرکت ⇐ بی‌درنگ.
 */
@Composable
fun V3Collapse(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  if (!Motion.enabled) { if (visible) content(); return }
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(MotionTokens.MEDIUM)),
    exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(MotionTokens.SHORT)),
  ) { content() }
}

/**
 *  «شیشه»ی نوارها (فقط نوارِ پایین و نوارِ تب): سطحِ نیمه‌شفاف روی گوشیِ توانا؛ گوشیِ کم‌حافظه،
 *  اندرویدِ پیش از ۱۲ یا «کاهشِ حرکت» ⇒ همان سطحِ پُر. بی blur — سبک می‌ماند.
 */
@Composable
fun v3GlassColor(solid: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
  val context = LocalContext.current
  val rich = remember(context) {
    Motion.enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
      runCatching { context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice != true }.getOrDefault(false)
  }
  return if (rich) solid.copy(alpha = 0.88f) else solid
}
