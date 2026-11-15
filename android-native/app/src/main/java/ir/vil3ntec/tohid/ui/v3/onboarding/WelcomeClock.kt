package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.v3.V3Depth

/*
 *  ساعتِ صفحهٔ ورودِ قدیمی (`WelcomeDecor.kt`) — خواستِ صاحب‌کار: «هیچ انیمیشنی اجرا نشود مگر کاربر روی همان صفحه باشد».
 *  `V3Live` (همین قاعده برای همهٔ برنامه) هنوز روی این پایه نیست؛ تا برسد، همین چهار شرط با هم ⇒ «زنده»:
 *   ۱. حرکت روشن است (`Motion.enabled`: کلیدِ تنظیمات یا «کاهشِ حرکت» ِ سیستم) — خاموش ⇒ هیچ ساعتی ساخته نمی‌شود؛
 *   ۲. برنامه پیش‌رو و دست‌کم RESUMED است ([LocalWelcomeForeground] از `ProvideWelcomeForeground` ِ `AuthGate`/`ShopSetupGate`)؛
 *   ۳. هیچ برگه‌ای (`V3Sheet`) رویش باز نیست (`V3Depth.sheets`)؛
 *   ۴. برای نشان: خودش در قاب است ([WelcomeClock.area]؛ با پیمایش بیرون رفت ⇒ می‌ایستد).
 *  زنده نیست ⇒ هیچ قابی خواسته نمی‌شود و همان قابِ ایستا می‌ماند؛ دوباره زنده شد ⇒ از همان لحظه ادامه، بی پرش به آغاز.
 *  صفحه که برود (دروازه رد شد) ساعت با ترکیب از میان می‌رود.
 */

/** برنامه پیش‌رو است — [ProvideWelcomeForeground] می‌دهد؛ بی آن (عکس‌ها، پیش‌نمایش، آزمون) روشن */
val LocalWelcomeForeground = compositionLocalOf { true }

/** پس‌زمینهٔ ورود همین حالا پشتِ این صفحه کشیده شده ([WelcomeBackdrop]) — گامِ درونی دوباره نمی‌کشد */
internal val LocalWelcomeBackdrop = compositionLocalOf { false }

/** شمارِ قاب‌هایی که ساعت‌های صفحهٔ ورود خواسته‌اند — فقط برای آزمون (`WelcomeLookUiTest`)؛ روی رشتهٔ صفحه */
internal object WelcomeProbe {
  @Volatile var frames: Long = 0
}

/** پس‌زمینهٔ ورود یک بار پشتِ همهٔ گام‌ها: گام‌ها جابه‌جا می‌شوند و موج‌ها و خط‌ها سرِ جایشان می‌مانند */
@Composable
internal fun WelcomeBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  Box(modifier.fillMaxSize()) {
    WelcomeBackground()
    CompositionLocalProvider(LocalWelcomeBackdrop provides true) { content() }
  }
}

/** زمانِ ساعت (میلی‌ثانیه، فقط زمانِ «زنده») — در مرحلهٔ رسم بخوان */
@Stable
class WelcomeClock internal constructor() {
  private val time = mutableLongStateOf(0L)
  internal var onScreen by mutableStateOf(true)
    private set

  val ms: Long get() = time.longValue

  /** روی خودِ تکه: بیرون از قاب (پیمایش) ⇒ نه زنده */
  val area: Modifier = Modifier.onGloballyPositioned { c ->
    val b = c.boundsInWindow()
    val seen = c.isAttached && b.width > 0f && b.height > 0f
    if (onScreen != seen) onScreen = seen
  }

  internal suspend fun run() {
    var last = -1L
    while (true) {
      withInfiniteAnimationFrameMillis { now ->
        //  قابِ نخست فقط نقطهٔ آغاز است؛ مکثِ درازِ یک قاب (کندیِ دستگاه) پرشِ بزرگ نمی‌دهد
        if (last >= 0) time.longValue += (now - last).coerceIn(0L, MAX_STEP_MS)
        last = now
        WelcomeProbe.frames++
      }
    }
  }

  private companion object {
    const val MAX_STEP_MS = 100L
  }
}

/** ساعتِ پس‌زمینه یا نشان — فقط وقتی کسی روی همین صفحه است قاب می‌خواهد (بالا) */
@Composable
internal fun rememberWelcomeClock(onScreenOnly: Boolean = false): WelcomeClock {
  val clock = remember { WelcomeClock() }
  val live = Motion.enabled && LocalWelcomeForeground.current && V3Depth.sheets.intValue == 0 && (!onScreenOnly || clock.onScreen)
  if (live) LaunchedEffect(clock) { clock.run() }
  return clock
}
