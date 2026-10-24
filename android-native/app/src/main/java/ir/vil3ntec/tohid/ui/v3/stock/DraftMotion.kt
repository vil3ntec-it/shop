package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens

/**
 *  حرکت‌های کوچکِ صفحه‌های انبار — فقط از واژه‌نامهٔ کیت (`MotionSpec`/`MotionTokens`)، پس
 *  همه با «کاهشِ حرکت» بی‌درنگ‌اند و فنرها همان فنرهای کیت (۰٫۸۵/۳۸۰ برای جا و اندازه).
 *  پیشنهادِ کیت (`Draft`): بازشدن/بسته‌شدنِ بخش، تغییرِ اندازه، جابه‌جاییِ ردیفِ تنبل،
 *  نشستنِ نشان، و عوض‌شدنِ عدد.
 */
internal object DraftMotion {
  /** بخشِ تاشو باز می‌شود: ارتفاع با فنر، محتوا محو-به-دیده */
  fun reveal(): EnterTransition = expandVertically(MotionSpec.sheet()) + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT))

  fun hide(): ExitTransition = shrinkVertically(MotionSpec.sheet()) + fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT))

  /** نشانِ کوچک (تیکِ «شمرده») با فنرِ بازیگوشِ کیت می‌نشیند */
  fun pop(): EnterTransition = scaleIn(MotionSpec.morph(), initialScale = 0.4f) + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT))

  fun unpop(): ExitTransition = scaleOut(MotionSpec.morph(), targetScale = 0.4f) + fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT))

  /** عددِ تازه از پایین بالا می‌آید و پیشین بالا می‌رود (پیش‌نمایشِ اصلاح) */
  fun figure(): ContentTransform =
    (slideInVertically(MotionSpec.sheet()) { it / 2 } + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)))
      .togetherWith(slideOutVertically(MotionSpec.sheet()) { -it / 2 } + fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)))
}

/** تغییرِ اندازهٔ کارت/بخش با فنرِ کیت */
internal fun Modifier.draftResize(): Modifier = animateContentSize(MotionSpec.sheet())

/** ردیفِ فهرستِ تنبل: آمدن، رفتن و جابه‌جایی با فیلتر/جست‌وجو — بی‌حرکت ⇐ بی‌درنگ */
internal fun LazyItemScope.draftItem(): Modifier = Modifier.animateItem(
  fadeInSpec = if (Motion.enabled) MotionSpec.tweenOf(MotionTokens.MEDIUM) else null,
  placementSpec = if (Motion.enabled) MotionSpec.sheet() else null,
  fadeOutSpec = if (Motion.enabled) MotionSpec.tweenOf(MotionTokens.SHORT) else null,
)
