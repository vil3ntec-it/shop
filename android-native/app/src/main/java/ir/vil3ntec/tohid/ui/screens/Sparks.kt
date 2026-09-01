package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 *  نورهای نقطه‌ای — از کنج‌ها و کناره‌ها، فقط وقتی دست می‌خورد.
 *
 *  ── جای چه چیزی را گرفت ───────────────────────────────────────────
 *  تا دیروز روی کارت‌های اشتراک یک «خطِ نور» بی‌وقفه این‌طرف و آن‌طرف
 *  می‌رفت. دو عیب داشت: یکی اینکه هیچ‌وقت تمام نمی‌شد و چشم را روی
 *  چیزی می‌کشید که خبری نداشت، و دیگری اینکه گوشیِ کاربر برای یک
 *  تزئین، بی‌دلیل بیدار می‌ماند و باتری می‌سوزاند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  حالا هیچ حرکتی نیست تا کاربر خودش کارت را بزند: همان لحظه یک مشت
 *  نقطهٔ نور از کنج‌ها و از میانهٔ هر ضلع بیرون می‌پاشد، دور می‌شود و
 *  محو می‌شود — کمتر از یک ثانیه. بعد از آن صفحه دوباره ساکن است.
 *
 *  ── دو نکتهٔ فنی که اگر رعایت نشوند کار نمی‌کند ────────────────────
 *   • این تغییردهنده باید **پیش از** `clip` بیاید. نقطه‌ها بیرونِ کادر
 *     کشیده می‌شوند و اگر بعد از `clip` بنشیند، همان‌جا بریده می‌شوند و
 *     چیزی دیده نمی‌شود.
 *   • فقط وقتی چیزی کشیده می‌شود که پخش در جریان باشد؛ در حالِ سکون،
 *     نه محاسبه‌ای هست و نه کشیدنی.
 *  ──────────────────────────────────────────────────────────────────
 */
class Sparks internal constructor() {
  /** با هر بار زدن یکی بالا می‌رود؛ پخشِ تازه از همین‌جا شروع می‌شود */
  internal var shot by mutableStateOf(0)
    private set

  fun fire() {
    shot++
  }
}

@Composable
fun rememberSparks(): Sparks = remember { Sparks() }

/**
 *  زمانِ یک پخش. کوتاه است عمداً: چیزی که با انگشتِ کاربر شروع می‌شود
 *  باید پیش از برداشتنِ انگشت تمام شده باشد، وگرنه به کارِ بعدی‌اش
 *  می‌چسبد.
 */
private const val BURST_MS = 620

/** نقطه‌ها از این نقطه‌های لنگر بیرون می‌زنند: چهار کنج و میانهٔ ضلع‌ها */
private val ANCHORS: List<Pair<Offset, Offset>> = listOf(
  //  کنج‌ها — جهتِ بیرون‌رفتن، مورب
  Offset(0f, 0f) to Offset(-0.7f, -0.7f),
  Offset(1f, 0f) to Offset(0.7f, -0.7f),
  Offset(0f, 1f) to Offset(-0.7f, 0.7f),
  Offset(1f, 1f) to Offset(0.7f, 0.7f),
  //  ضلعِ بالا و پایین
  Offset(0.3f, 0f) to Offset(-0.15f, -1f),
  Offset(0.7f, 0f) to Offset(0.15f, -1f),
  Offset(0.3f, 1f) to Offset(-0.15f, 1f),
  Offset(0.7f, 1f) to Offset(0.15f, 1f),
  //  ضلعِ چپ و راست
  Offset(0f, 0.32f) to Offset(-1f, -0.15f),
  Offset(0f, 0.68f) to Offset(-1f, 0.15f),
  Offset(1f, 0.32f) to Offset(1f, -0.15f),
  Offset(1f, 0.68f) to Offset(1f, 0.15f),
)

/**
 *  @param sparks همان چیزی که با `rememberSparks()` گرفته می‌شود و در
 *    `onClick` صدا زده می‌شود: `sparks.fire()`.
 *  @param tint رنگِ نقطه‌ها — طلایی برای کارت‌های اشتراک، آبی برای بقیه.
 */
@Composable
fun Modifier.edgeSparks(sparks: Sparks, tint: Color): Modifier {
  //  از یک تمام شده شروع می‌شود، پس در اولین کشیدنِ صفحه چیزی پیدا نیست
  val run = remember { Animatable(1f) }

  LaunchedEffect(sparks.shot) {
    if (sparks.shot == 0 || !Motion.enabled) return@LaunchedEffect
    run.snapTo(0f)
    run.animateTo(1f, tween(BURST_MS, easing = LinearOutSlowInEasing))
  }

  return drawWithContent {
    drawContent()
    val progress = run.value
    if (progress >= 1f || !Motion.enabled) return@drawWithContent

    val reach = 15.dp.toPx()
    ANCHORS.forEachIndexed { index, (at, dir) ->
      /*
       *  هر نقطه با تأخیرِ کوچکِ خودش راه می‌افتد.
       *
       *  با شروعِ هم‌زمان، دوازده نقطه یک حلقهٔ یکدست می‌شدند — مثل موجِ
       *  انفجار در بازی‌ها، نه مثل جرقه. تأخیرِ پله‌ای از خودِ شماره
       *  حساب می‌شود، پس تصادفی نیست و هر بار همان است.
       */
      val lag = (index % 4) * 0.09f
      val local = ((progress - lag) / (1f - lag)).coerceIn(0f, 1f)
      if (local <= 0f || local >= 1f) return@forEachIndexed

      //  دورترها کمی بیشتر می‌روند تا حلقه یکدست نباشد
      val travel = reach * (0.75f + (index % 3) * 0.2f) * local
      val center = Offset(
        x = size.width * at.x + dir.x * travel,
        y = size.height * at.y + dir.y * travel,
      )
      //  تندِ روشن شدن، آرامِ محو شدن — جرقه این‌طور دیده می‌شود
      val alpha = min(local / 0.12f, 1f) * (1f - local)
      val radius = (2.6f - 1.4f * local).dp.toPx()

      //  هالهٔ کم‌رنگ زیرِ نقطه: بدونش نقطه یک دانهٔ رنگ است، نه نور
      drawCircle(color = tint.copy(alpha = alpha * 0.22f), radius = radius * 2.6f, center = center)
      drawCircle(color = tint.copy(alpha = alpha), radius = radius, center = center)
    }
  }
}
