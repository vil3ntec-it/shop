package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope

/*
 *  PERF-SCREEN — «هیچ انیمیشن یا چیزِ زنده‌ای تا کسی در آن بخش نیست اجرا نشود» (خواستِ صاحب‌کار؛ قاعده در `docs/RULES-fa.md`).
 *
 *  هر چیزِ زنده (انیمیشنِ بی‌پایان، چشمک، شیمر، اسپینر، تیکِ ساعت، «… دقیقه پیش»، نظرسنجیِ صفحه، شمارنده) از همین‌جا می‌گذرد.
 *  «دیده می‌شود» = هر سه با هم:
 *   ۱. لایهٔ بالا: تکه در بالاترین لایه است — صفحه (لایهٔ ۰) زیرِ هیچ برگه‌ای نیست (`V3Depth.sheets`)، و هر `V3Sheet` لایهٔ خودش
 *      را می‌دهد ([LocalV3Layer])؛ صفحهٔ زیرِ دروازه (قفل) پوشیده است ([LocalV3Covered]). صفحه‌های زیرینِ پشته و تب‌های دیگر
 *      اصلاً ترکیب نمی‌شوند (`V3RouteHost`).
 *   ۲. برنامه دست‌کم RESUMED (پیش‌رو، صفحهٔ گوشی روشن) — [LocalV3Foreground] از `ProvideV3Foreground` ِ ریشه.
 *   ۳. در قاب: اگر [V3LiveState.area] روی خودِ تکه نشسته باشد، تکه‌ای که بیرون از قاب پیمایش شده زنده نیست.
 *  دیده نمی‌شود ⇒ همان قابِ ایستا (مقدارِ آغاز)، هیچ قابی درخواست نمی‌شود؛ دوباره دیده شد ⇒ از همان‌جا ادامه.
 *  کارِ داده‌ای پس‌زمینه (همگام‌سازی، کارگرها، زنگِ سفارش) زنده به این معنا نیست و از این‌جا نمی‌گذرد.
 */

/** لایهٔ این تکه: ۰ = صفحه؛ هر `V3Sheet` شمارهٔ خودش (۱، ۲…) */
val LocalV3Layer = compositionLocalOf { 0 }

/** برنامه پیش‌رو و دست‌کم RESUMED است — ریشه‌ها با `ProvideV3Foreground` می‌دهند؛ بی آن (پیش‌نمایش، آزمون) روشن */
val LocalV3Foreground = compositionLocalOf { true }

/** صفحه زیرِ دروازه (قفل/ورود) است — `V3RouteHost` می‌دهد */
val LocalV3Covered = compositionLocalOf { false }

/** وضعِ «زنده» ِ یک تکه — [active] را بخوان؛ [area] را روی همان تکه بگذار تا بیرون از قاب هم بایستد */
@Stable
class V3LiveState internal constructor(private val gate: State<Boolean>, private val onScreen: MutableState<Boolean>) {
  val active: Boolean get() = gate.value && onScreen.value

  val area: Modifier = Modifier.onGloballyPositioned { c ->
    val b = c.boundsInWindow()
    val seen = c.isAttached && b.width > 0f && b.height > 0f
    if (onScreen.value != seen) onScreen.value = seen
  }
}

/** شمارِ قاب‌هایی که چیزهای زنده خواسته‌اند — فقط برای آزمون (`V3LiveGateTest`)؛ روی رشتهٔ صفحه */
object V3LiveProbe {
  @Volatile var frames: Long = 0
}

@Composable
fun rememberV3Live(): V3LiveState {
  val top = LocalV3Layer.current == V3Depth.sheets.intValue
  val gate = rememberUpdatedState(!LocalV3Covered.current && top && LocalV3Foreground.current)
  val onScreen = remember { mutableStateOf(true) }
  return remember { V3LiveState(gate, onScreen) }
}

/**
 *  جایِ `rememberInfiniteTransition().animateFloat(…)`: فقط وقتی [live] زنده است قاب می‌خواهد؛ وگرنه همان [initial] ِ ایستا.
 */
@Composable
fun v3LiveFloat(initial: Float, target: Float, spec: InfiniteRepeatableSpec<Float>, label: String, live: V3LiveState = rememberV3Live()): State<Float> {
  if (!live.active) return remember(initial) { mutableFloatStateOf(initial) }
  val v = rememberInfiniteTransition(label).animateFloat(initial, target, spec, label)
  return remember(v) { ProbeState(v) }
}

/** خواندنِ هر قاب در آزمون شمرده می‌شود (فقط وقتی مقدار عوض شد) */
private class ProbeState(private val v: State<Float>) : State<Float> {
  private var last = Float.NaN
  override val value: Float get() = v.value.also { if (it != last) { last = it; V3LiveProbe.frames++ } }
}

/**
 *  جایِ `LaunchedEffect(…) { while (true) { …; delay(…) } }` ِ صفحه (تیک، «… پیش»، نظرسنجیِ همان صفحه): [block] فقط تا
 *  وقتی [live] زنده است می‌دود و با دوباره دیده شدن از نو آغاز می‌شود (یک بار همان دم).
 */
@Composable
fun V3LiveEffect(vararg keys: Any?, live: V3LiveState = rememberV3Live(), block: suspend CoroutineScope.() -> Unit) {
  val active = live.active
  LaunchedEffect(*keys, active) { if (active) block() }
}

/** ساعتِ صفحه هر [periodMs]، فقط وقتی زنده؛ دوباره دیده شد ⇒ همان دم تازه. [now] از سوی صفحه (ساعتِ دستگاه) */
@Composable
fun rememberV3LiveClock(periodMs: Long, live: V3LiveState = rememberV3Live(), now: () -> Long): Long {
  var t by remember { mutableLongStateOf(now()) }
  V3LiveEffect(periodMs, live = live) {
    while (true) {
      t = now()
      kotlinx.coroutines.delay(periodMs)
    }
  }
  return t
}

/** اسپینرِ «در حالِ کار»: زنده ⇒ چرخان؛ پوشیده یا پس‌زمینه ⇒ همان حلقهٔ ایستا، بی قاب */
@Composable
fun V3LiveSpinner(
  modifier: Modifier = Modifier,
  color: Color = ProgressIndicatorDefaults.circularColor,
  strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
  val live = rememberV3Live()
  val m = modifier.then(live.area)
  if (live.active) CircularProgressIndicator(m, color = color, strokeWidth = strokeWidth)
  else CircularProgressIndicator({ 0.75f }, m, color = color, strokeWidth = strokeWidth, trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor)
}
