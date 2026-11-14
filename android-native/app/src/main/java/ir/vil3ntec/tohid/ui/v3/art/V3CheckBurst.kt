package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import ir.vil3ntec.tohid.ui.screens.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 *  جشنِ کوتاهِ موفقیت (فروش، پرداخت، رسید، خرید): دایره با فنرِ کوچکی باز می‌شود، تیک کشیده می‌شود، حلقه‌ای نرم
 *  بیرون می‌رود و چند ذرهٔ رنگی پخش و محو می‌شوند — همه در **یک** بوم و با **یک** عدد ([V3Burst.MS] میلی‌ثانیه، ۰ تا ۱)
 *  که فقط در رسم خوانده می‌شود. هیچ لمسی را نمی‌گیرد (بی `pointerInput`)؛ صندوق‌دار همان لحظه اسکنِ بعدی را می‌زند.
 *  «کاهشِ حرکت» ⇒ [V3Burst.spec] همان حالتِ پایانی است: دایره و تیک، بی ذره.
 */
object V3Burst {
  /** درازای جشن */
  const val MS = 700

  fun spec(): FiniteAnimationSpec<Float> = if (Motion.enabled) tween(MS, easing = LinearEasing) else snap()

  internal fun clamp01(x: Float) = x.coerceIn(0f, 1f)
  internal fun easeOutCubic(x: Float): Float { val y = 1f - x; return 1f - y * y * y }
  internal fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val y = x - 1f
    return 1f + (c1 + 1f) * y * y * y + c1 * y * y
  }
}

/**
 *  @param progress ۰ تا ۱ (فقط در رسم خوانده می‌شود)
 *  @param disc رنگِ دایره؛ [tick] رنگِ تیک؛ [sparks] رنگِ ذره‌ها (دست‌کم یکی)
 *  @param burst ذره‌ها و حلقه؛ نشانِ خیلی کوچک ⇒ کم‌تر
 */
@Composable
fun V3CheckBurst(
  progress: () -> Float,
  disc: Color,
  tick: Color,
  sparks: List<Color>,
  modifier: Modifier = Modifier,
  burst: Boolean = true,
) {
  Spacer(
    modifier.drawWithCache {
      val r = size.minDimension / 2f
      val c = center
      val check = Path().apply {
        moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
        lineTo(c.x - r * 0.12f, c.y + r * 0.32f)
        lineTo(c.x + r * 0.45f, c.y - r * 0.30f)
      }
      val measure = PathMeasure().apply { setPath(check, false) }
      val part = Path()
      val stroke = Stroke(width = r * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
      val n = if (r < 18f) 6 else 10
      onDrawBehind {
        burstFrame(progress(), c, r, disc, tick, sparks, if (burst) n else 0, measure, part, stroke)
      }
    },
  )
}

private fun DrawScope.burstFrame(
  p: Float, c: Offset, r: Float, disc: Color, tick: Color, sparks: List<Color>, n: Int,
  measure: PathMeasure, part: Path, stroke: Stroke,
) {
  //  حلقهٔ بیرونی (از ۰٫۳۰)
  if (n > 0) {
    val rp = V3Burst.clamp01((p - 0.30f) / 0.60f)
    if (rp > 0f && rp < 1f) {
      drawCircle(sparks[0].copy(alpha = 0.45f * (1f - rp)), r * (1f + 0.7f * V3Burst.easeOutCubic(rp)), c, style = Stroke(width = r * 0.07f * (1f - rp) + 0.5f))
    }
  }
  //  دایره با فنر (۰ تا ۰٫۴۲)
  val ds = V3Burst.easeOutBack(V3Burst.clamp01(p / 0.42f))
  if (ds > 0f) drawCircle(disc, r * ds, c)
  //  تیک (۰٫۲۲ تا ۰٫۶۴)
  val cp = V3Burst.easeOutCubic(V3Burst.clamp01((p - 0.22f) / 0.42f))
  if (cp > 0f) {
    part.reset()
    measure.getSegment(0f, measure.length * cp, part, true)
    drawPath(part, tick, style = stroke)
  }
  //  ذره‌ها (۰٫۲۸ تا ۱): به بیرون، کوچک و محو؛ یکی‌درمیان دایره و نوارِ کاغذیِ چرخان
  if (n == 0) return
  val pp = V3Burst.clamp01((p - 0.28f) / 0.72f)
  if (pp <= 0f || pp >= 1f) return
  val dist = r * (1.05f + 1.0f * V3Burst.easeOutCubic(pp))
  val alpha = 1f - pp * pp
  val s = r * 0.13f * (1f - 0.65f * pp)
  for (i in 0 until n) {
    val deg = i * 360f / n + 14f * sin(i * 1.7f)
    val rad = deg * (PI.toFloat() / 180f)
    val d = dist * (0.86f + 0.14f * ((i * 7) % 5) / 4f)
    val at = Offset(c.x + cos(rad) * d, c.y + sin(rad) * d)
    val color = sparks[i % sparks.size].copy(alpha = alpha)
    if (i % 2 == 0) {
      drawCircle(color, s, at)
    } else {
      rotate(deg + pp * 220f, at) {
        drawRoundRect(color, Offset(at.x - s * 1.3f, at.y - s * 0.55f), Size(s * 2.6f, s * 1.1f), CornerRadius(s * 0.4f))
      }
    }
  }
}
