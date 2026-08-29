package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space

/**
 *  حلقهٔ آماری.
 *
 *  یک عدد وسط و کمانی دورش که می‌گوید نسبت به چه چیزی چقدر است. عمداً
 *  «درصد» ننوشته‌ایم: فروشنده «۶۳٪» را معنی نمی‌کند، ولی کمانِ نیمه‌پر را
 *  یک نگاه می‌فهمد.
 *
 *  کمان از حالتِ خالی پر می‌شود، نه اینکه یک‌آن ظاهر شود؛ همان چیزی که
 *  نگاه را روی عدد نگه می‌دارد.
 */
@Composable
fun StatRing(
  label: String,
  value: String,
  fraction: Float,
  tint: Color,
  modifier: Modifier = Modifier,
  caption: String? = null,
  size: androidx.compose.ui.unit.Dp = 108.dp,
) {
  val target = fraction.coerceIn(0f, 1f)
  val animated by animateFloatAsState(
    targetValue = if (Motion.enabled) target else target,
    animationSpec = tween(900, easing = FastOutSlowInEasing),
    label = "ring",
  )
  val track = Shop.colors.surface2
  val glow = tint.copy(alpha = 0.28f)

  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
      Canvas(Modifier.fillMaxSize()) {
        val stroke = this.size.minDimension * 0.085f
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)

        // مسیرِ خالی
        drawArc(
          color = track,
          startAngle = 135f,
          sweepAngle = 270f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // هالهٔ پشتِ کمان، تا حلقه انگار نور دارد
        if (animated > 0f) {
          drawArc(
            color = glow,
            startAngle = 135f,
            sweepAngle = 270f * animated,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
          )
          drawArc(
            brush = Brush.sweepGradient(listOf(tint.copy(alpha = 0.55f), tint, tint.copy(alpha = 0.55f))),
            startAngle = 135f,
            sweepAngle = 270f * animated,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          value,
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          textAlign = TextAlign.Center,
        )
        if (caption != null) {
          Text(caption, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2, maxLines = 1)
        }
      }
    }
    Spacer(Modifier.height(Space.xs))
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = Shop.colors.muted,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
  }
}
