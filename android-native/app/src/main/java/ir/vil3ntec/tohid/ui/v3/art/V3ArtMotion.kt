package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/*
 *  زمانِ تصویرها: یک ساعتِ بی‌پایانِ خطی به درازای [ART_LOOP] ثانیه که فقط در **مرحلهٔ رسم** خوانده می‌شود
 *  (`graphicsLayer {}` و `drawWithCache {}`) — هیچ بازترکیبی در هر فریم نیست. همهٔ موج‌ها چند بارِ درست در همان چرخه
 *  می‌زنند ([wave]: `k` دور در هر چرخه) تا پایانِ چرخه بی پرش به آغازش بچسبد.
 *
 *  ساعت فقط در همان ترکیب زنده است (`rememberInfiniteTransition`): صفحه که برود یا تصویر از دید بیرون برود، می‌ایستد؛
 *  برنامه که به پس‌زمینه برود، ساعتِ قاب‌های Compose خودش می‌ایستد. «کاهشِ حرکت» (`Motion.enabled = false`) ⇒ هیچ
 *  ساعتی ساخته نمی‌شود و تصویر در ژستِ آرامِ [ART_REST] ثابت می‌ماند.
 */

/** درازای یک چرخهٔ کامل (ثانیه) */
internal const val ART_LOOP = 24f

/** ژستِ ثابت (کاهشِ حرکت، بیرون از دید): شاخه‌ها کمی خمیده، نه صاف و بی‌جان */
internal const val ART_REST = 1.7f

private const val TAU = (2.0 * PI).toFloat()

/** موجِ سینوسی با `k` دور در هر چرخه */
internal fun wave(t: Float, k: Int, phase: Float = 0f): Float = sin(TAU * k * t / ART_LOOP + phase)

/** جای `t` در یک رفت از `k` رفتِ هر چرخه (۰ تا ۱) — برگِ رها، قطره، جرقه */
internal fun frac(t: Float, k: Int, phase: Float = 0f): Float {
  val x = t * k / ART_LOOP + phase
  return x - floor(x)
}

/**
 *  باد: تابِ فنرگونه با تندباد — موجِ اصلی (۴ ثانیه) با بازگشتِ تند‌ترِ کوچک (فنری که پس از هر هل کمی پس می‌زند)،
 *  که شدتش با تندبادِ کندِ ۲۴ ثانیه‌ای کم و زیاد می‌شود. خروجی حدودِ −۱ تا ۱؛ [phase] شاخه‌ها را از هم جدا می‌کند.
 */
internal fun wind(t: Float, phase: Float = 0f): Float {
  val gust = 0.62f + 0.38f * wave(t, 1, phase * 0.35f)
  return gust * (0.78f * wave(t, 6, phase) + 0.22f * wave(t, 12, phase * 1.9f + 0.6f))
}

/** ساعتِ تصویر یا null (بی حرکت) — `animated` نادرست ⇒ هیچ گذرِ بی‌پایانی ساخته نمی‌شود */
@Composable
internal fun rememberV3ArtClock(animated: Boolean): State<Float>? {
  if (!animated) return null
  return rememberInfiniteTransition(label = "v3Art").animateFloat(
    //  از همان ژستِ ثابت آغاز می‌شود: روشن شدنِ حرکت پرش ندارد
    initialValue = ART_REST,
    targetValue = ART_REST + ART_LOOP,
    animationSpec = infiniteRepeatable(tween((ART_LOOP * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
    label = "v3ArtTime",
  )
}
