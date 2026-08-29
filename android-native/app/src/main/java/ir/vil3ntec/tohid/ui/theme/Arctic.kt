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
 *  سطحِ شیشه‌ای.
 *
 *  به‌جای «کادر با حاشیهٔ ضخیم»، یک سطحِ نیمه‌شفاف با لبهٔ روشنِ بالا و
 *  هالهٔ آبیِ زیرش. Compose تارکردنِ واقعیِ پس‌زمینه را ارزان نمی‌دهد، پس
 *  همان حس با سه لایه ساخته می‌شود: تیرگیِ ملایم، شیبِ روشنی از بالا به
 *  پایین، و یک حاشیهٔ بسیار کم‌رنگ.
 */
fun Modifier.glassSurface(
  shape: Shape,
  tint: Color,
  sheen: Color,
  border: Color,
  strong: Boolean = false,
): Modifier = this
  .clip(shape)
  .background(
    brush = Brush.verticalGradient(
      listOf(
        tint.copy(alpha = if (strong) 0.98f else 0.92f),
        tint.copy(alpha = if (strong) 0.90f else 0.78f),
      )
    ),
    shape = shape,
  )
  .drawBehind {
    // لبهٔ روشنِ بالا — همان بازتابی که شیشه دارد
    drawRect(
      brush = Brush.verticalGradient(
        0f to sheen,
        1f to Color.Transparent,
        endY = size.height * 0.35f,
      ),
      size = Size(size.width, size.height * 0.35f),
    )
  }
  .border(width = if (strong) 1.dp else 0.7.dp, color = border, shape = shape)
