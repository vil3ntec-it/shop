package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos

/*
 *  زمانِ تصویرها (دستهٔ ۶۵): هر صحنه چرخهٔ ۳/۴/۶ ثانیه‌ای دارد که ۲۴ بر آن بخش‌پذیر است؛ ساعت ثانیه‌های حرکت را تا
 *  [ART_LOOP] می‌شمارد و دوباره از صفر — پس پایانِ هر چرخه بی پرش به آغازش می‌چسبد.
 *
 *  قاعدهٔ صاحب‌کار: هیچ حرکتی جز وقتی کاربر واقعاً همان صفحه را می‌بیند. ساعت فقط وقتی قاب می‌خواهد که «کاهشِ حرکت»
 *  خاموش، تصویر در دید، برنامه RESUMED و صفحه زیرِ برگه‌ای نباشد؛ وگرنه هیچ قابی خواسته نمی‌شود و تصویر در همان
 *  ژستِ آخر می‌ایستد و از همان‌جا ادامه می‌دهد (بی پرش). عدد فقط در مرحلهٔ رسم خوانده می‌شود (بی بازترکیب در هر قاب).
 */

/** درازای شمارشِ ساعت (ثانیه) — مضربِ مشترکِ چرخه‌های همهٔ صحنه‌ها */
internal const val ART_LOOP = 24f

/**
 *  ثانیه‌های حرکت (۰ … [ART_LOOP]). [running] نادرست ⇒ هیچ قابی خواسته نمی‌شود و عدد همان می‌ماند.
 *  نخستین قاب پس از ادامه فقط زمانش را برمی‌دارد (فاصلهٔ ایستادن به حساب نمی‌آید).
 */
@Composable
internal fun rememberArtElapsed(running: Boolean): MutableFloatState {
  val elapsed = remember { mutableFloatStateOf(0f) }
  LaunchedEffect(running) {
    if (!running) return@LaunchedEffect
    //  «قابِ حرکتِ بی‌پایان»: سیاستِ حرکتِ بی‌پایان (مثلاً آزمونِ رابط با ساعتِ خودکار) آن را می‌ایستاند
    var last = withInfiniteAnimationFrameNanos { it }
    while (true) {
      withInfiniteAnimationFrameNanos { now ->
        val dt = (now - last) / 1_000_000_000f
        last = now
        val next = elapsed.floatValue + dt
        elapsed.floatValue = if (next >= ART_LOOP) next - ART_LOOP * kotlin.math.floor(next / ART_LOOP) else next
      }
    }
  }
  return elapsed
}
