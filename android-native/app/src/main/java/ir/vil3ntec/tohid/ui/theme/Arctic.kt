package ir.vil3ntec.tohid.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 *  زمینهٔ قطبی.
 *
 *  یک سرمه‌ایِ تیره با دو لکهٔ نورِ آبی و فیروزه‌ای که خیلی آرام نفس
 *  می‌کشند. عمداً بی‌جزئیات است: هر الگو یا تصویری پشتِ متنِ فارسی،
 *  خواندن را سخت می‌کند. کارش فقط این است که کارت‌ها روی چیزی شناور به
 *  نظر برسند، نه روی یک سطحِ تختِ مرده.
 *
 *  حرکتش آن‌قدر کند است که چشم نمی‌گیردش؛ و اگر کلیدِ انیمیشن خاموش
 *  باشد، اصلاً حرکت نمی‌کند.
 */
@Composable
fun ArcticBackground(animated: Boolean = true, content: @Composable () -> Unit) {
  val colors = Shop.colors
  val transition = rememberInfiniteTransition(label = "aurora")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
    label = "drift",
  )
  val phase = if (animated) t else 0.25f

  Box(
    Modifier
      .fillMaxSize()
      .background(colors.bg)
      .drawBehind {
        // لکهٔ اول، بالا و سمتِ راست
        val driftX = sin(phase * 2f * Math.PI.toFloat()) * size.width * 0.06f
        val driftY = sin(phase * 2f * Math.PI.toFloat() + 1.2f) * size.height * 0.03f
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(colors.auroraOne, Color.Transparent),
            center = Offset(size.width * 0.82f + driftX, size.height * 0.08f + driftY),
            radius = size.minDimension * 0.95f,
          ),
          radius = size.minDimension * 0.95f,
          center = Offset(size.width * 0.82f + driftX, size.height * 0.08f + driftY),
        )
        // لکهٔ دوم، پایین و سمتِ چپ
        val dx = sin(phase * 2f * Math.PI.toFloat() + 2.4f) * size.width * 0.05f
        val dy = sin(phase * 2f * Math.PI.toFloat() + 0.6f) * size.height * 0.04f
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(colors.auroraTwo, Color.Transparent),
            center = Offset(size.width * 0.1f + dx, size.height * 0.78f + dy),
            radius = size.minDimension * 1.05f,
          ),
          radius = size.minDimension * 1.05f,
          center = Offset(size.width * 0.1f + dx, size.height * 0.78f + dy),
        )
        // تیرگیِ ملایمِ پایین، تا نوارِ ناوبری روی زمینهٔ آرام بنشیند
        drawRect(
          brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to colors.bg.copy(alpha = 0.75f),
            startY = size.height * 0.55f,
            endY = size.height,
          ),
          topLeft = Offset(0f, size.height * 0.55f),
          size = Size(size.width, size.height * 0.45f),
        )
      }
  ) {
    content()
  }
}

/**
 *  سطحِ کارت.
 *
 *  اسمش «شیشه‌ای» بود ولی کارِ اصلی‌اش این است: کارت را از زمینه جدا کند
 *  **بدونِ کشیدنِ خطِ دور**. خطِ دور، کارت را جعبه نشان می‌دهد؛ و بدتر از
 *  آن، اگر رنگِ کارت و زمینه نزدیک باشند، همان خط تنها چیزی می‌شود که
 *  دیده می‌شود — دقیقاً همان چیزی که در نسخهٔ قبل اتفاق افتاد.
 *
 *  پس جدایی از دو چیز می‌آید:
 *   ۱) اختلافِ رنگِ کارت با زمینه (که در پالت درست شده)
 *   ۲) یک سایهٔ آبیِ نرم زیرِ کارت
 *
 *  حاشیه فقط یک رگهٔ بسیار کم‌رنگ است تا لبه در حالتِ تاریک کاملاً محو
 *  نشود؛ در روشنایی عملاً دیده نمی‌شود.
 */
fun Modifier.glassSurface(
  shape: Shape,
  tint: Color,
  sheen: Color,
  border: Color,
  strong: Boolean = false,
  glow: Color = Color(0x1F1B7FD4),
): Modifier = this
  .shadow(
    elevation = if (strong) 10.dp else 6.dp,
    shape = shape,
    ambientColor = glow,
    spotColor = glow,
  )
  .clip(shape)
  .background(color = tint, shape = shape)
  .drawBehind {
    // لبهٔ روشنِ بالا — فقط در حالتِ تاریک معنی دارد و آنجا هم بسیار کم
    if (sheen.alpha > 0f) {
      drawRect(
        brush = Brush.verticalGradient(
          0f to sheen,
          1f to Color.Transparent,
          endY = size.height * 0.3f,
        ),
        size = Size(size.width, size.height * 0.3f),
      )
    }
  }
  .border(width = 0.6.dp, color = border, shape = shape)
