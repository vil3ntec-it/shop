package ir.vil3ntec.tohid.ui.v3.sales.scan

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  کادرِ دوربینِ اسکن: پردهٔ تیرهٔ دورِ یک قابِ گردِ ۲۴ (نسبتِ بارکد)، مرزِ روشن و خطِ اسکنِ
 *  نرم که بالا و پایین می‌رود (هاله + مغزِ روشن، دو سرِ محو). `read` ⇒ قاب رنگِ برند می‌گیرد
 *  و خط می‌رود. همه در `drawWithCache` ساخته و فقط رسم می‌شوند — بی چیدمانِ دوباره در هر
 *  فریم. «کاهشِ حرکت» ⇒ خط سرِ جایش (میانه) می‌ماند.
 */
@Composable
fun ScanViewfinder(read: Boolean, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val brand = c.accent
  val edge = c.onBlack
  val scrim = c.black.copy(alpha = SCRIM_ALPHA)
  val fill by animateFloatAsState(if (read) 1f else 0f, MotionSpec.tweenOf(MotionTokens.SHORT), label = "scanRead")
  val sweep: State<Float>? = if (Motion.enabled && !read) {
    rememberInfiniteTransition(label = "scanLine").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = MotionTokens.Standard), RepeatMode.Reverse),
      label = "scanSweep",
    )
  } else null

  Box(
    modifier.drawWithCache {
      val fw = minOf(size.width * 0.78f, 300.dp.toPx())
      val fh = fw * 0.62f
      val left = (size.width - fw) / 2f
      val top = size.height * 0.44f - fh / 2f
      val radius = CornerRadius(24.dp.toPx())
      val frame = RoundRect(left, top, left + fw, top + fh, radius)
      val cutout = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRoundRect(frame)
      }
      val stroke = Stroke(width = 3.dp.toPx())
      val inset = 16.dp.toPx()
      val line = 2.dp.toPx()
      val halo = 22.dp.toPx()
      val lineBrush = Brush.horizontalGradient(
        0f to Color.Transparent, 0.18f to brand, 0.5f to edge, 0.82f to brand, 1f to Color.Transparent,
        startX = left + inset, endX = left + fw - inset,
      )
      val haloBrush = Brush.verticalGradient(
        0f to Color.Transparent, 0.5f to brand.copy(alpha = 0.24f), 1f to Color.Transparent,
        startY = -halo, endY = halo,
      )
      onDrawBehind {
        drawPath(cutout, scrim)
        val k = fill
        if (k > 0f) drawRoundRect(brand, Offset(left, top), Size(fw, fh), radius, alpha = READ_FILL * k)
        drawRoundRect(lerp(edge, brand, k), Offset(left, top), Size(fw, fh), radius, style = stroke)
        if (k < 1f) {
          val p = sweep?.value ?: 0.5f
          translate(top = top + fh * (0.12f + 0.76f * p)) {
            drawRect(haloBrush, Offset(left + inset, -halo), Size(fw - 2 * inset, 2 * halo), alpha = 1f - k)
            drawRect(lineBrush, Offset(left + inset, -line / 2f), Size(fw - 2 * inset, line), alpha = 1f - k)
          }
        }
      }
    },
  )
}

/** یک رفت یا یک برگشتِ خط */
private const val SWEEP_MS = 1900

/** پردهٔ دورِ قاب */
private const val SCRIM_ALPHA = 0.5f

/** پُریِ رنگِ برندِ قاب پس از خواندن */
private const val READ_FILL = 0.32f
