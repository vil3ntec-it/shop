package ir.vil3ntec.tohid.ui.theme

import android.app.ActivityManager
import android.os.Build
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 *  حالِ رنگیِ صفحه.
 *
 *  رنگ‌ها یکی‌اند؛ فقط ترتیبِ غلبه فرق می‌کند تا هر بخشِ برنامه بویِ
 *  خودش را داشته باشد بی‌آنکه هویتِ برنامه عوض شود.
 */
enum class AuroraTone { Brand, Debt, Cash, Stock }

/** نارنجیِ کمکی — برای حال‌هایی که پرامپت طراحی نارنجی خواسته */
private val AuroraWarm = Color(0x59FF9A3D)

@Composable
private fun tonePalette(tone: AuroraTone): List<Color> {
  val c = Shop.colors
  return when (tone) {
    AuroraTone.Brand -> listOf(c.auroraOne, c.auroraTwo, c.auroraThree)
    AuroraTone.Debt -> listOf(c.auroraThree, c.auroraOne, AuroraWarm)
    AuroraTone.Cash -> listOf(c.auroraTwo, c.auroraOne, c.auroraThree)
    AuroraTone.Stock -> listOf(AuroraWarm, c.auroraOne, c.auroraTwo)
  }
}

/**
 *  زمینه‌ی آرورا.
 *
 *  سه لکه‌ی نور با مه‌ی سنگین، روی یک زمینه‌ی تخت. عمداً بی‌جزئیات است:
 *  هر الگویی پشتِ متنِ فارسی، خواندن را سخت می‌کند. کارش فقط این است که
 *  شیشه‌ها روی چیزی زنده شناور به نظر برسند.
 *
 *  روی گوشی‌های تازه (API ≥ ۳۱ و کم‌حافظه‌نبودن) لکه‌ها واقعاً مه‌آلود
 *  می‌شوند و خیلی آرام جابه‌جا می‌شوند؛ وگرنه همان لکه‌ها با گرادینتِ
 *  شعاعیِ ساکن کشیده می‌شوند تا گوشیِ ضعیف کُند نشود. این تنها انیمیشنِ
 *  بی‌پایانِ مجاز در کلِ برنامه است.
 */
@Composable
fun AuroraBackground(
  tone: AuroraTone = AuroraTone.Brand,
  animated: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colors = Shop.colors
  val blobs = tonePalette(tone)
  val rich = richGlass()
  val moving = animated && rich

  val transition = rememberInfiniteTransition(label = "aurora")
  val t by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
    label = "drift",
  )
  val phase = if (moving) t else 0.5f
  //  هر لکه ۲۰dp جابه‌جا می‌شود، با فازِ متفاوت تا با هم نروند و بیایند
  fun shift(seed: Float) = (sin((phase + seed) * 2f * Math.PI.toFloat()) * 20f).dp

  Box(Modifier.fillMaxSize().background(colors.bg)) {
    if (rich) {
      Box(Modifier.fillMaxSize().blur(120.dp, BlurredEdgeTreatment.Unbounded)) {
        Blob(230.dp, blobs[0], Alignment.TopEnd, 60.dp + shift(0f), (-70).dp + shift(0.3f))
        Blob(200.dp, blobs[1], Alignment.CenterStart, (-70).dp + shift(0.5f), 40.dp + shift(0.8f))
        Blob(200.dp, blobs[2], Alignment.BottomEnd, 40.dp + shift(0.65f), 30.dp + shift(0.15f))
      }
    } else {
      //  فالبکِ ساکن: همان سه لکه، ولی کشیده‌شده با گرادینتِ شعاعی
      Box(
        Modifier.fillMaxSize().drawBehind {
          fun spot(color: Color, cx: Float, cy: Float, r: Float) {
            val center = Offset(size.width * cx, size.height * cy)
            val radius = size.minDimension * r
            drawCircle(
              brush = Brush.radialGradient(listOf(color, Color.Transparent), center, radius),
              radius = radius,
              center = center,
            )
          }
          spot(blobs[0], 0.86f, 0.06f, 0.95f)
          spot(blobs[1], 0.06f, 0.46f, 0.85f)
          spot(blobs[2], 0.88f, 0.92f, 0.85f)
        }
      )
    }
    //  آرام‌کردنِ پایینِ صفحه تا نوارِ ناوبری روی نور ننشیند
    Box(
      Modifier.fillMaxSize().drawBehind {
        drawRect(
          brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to colors.bg.copy(alpha = 0.55f),
            startY = size.height * 0.62f,
            endY = size.height,
          ),
          topLeft = Offset(0f, size.height * 0.62f),
          size = Size(size.width, size.height * 0.38f),
        )
      }
    )
    content()
  }
}

@Composable
private fun Blob(
  diameter: androidx.compose.ui.unit.Dp,
  color: Color,
  align: Alignment,
  x: androidx.compose.ui.unit.Dp,
  y: androidx.compose.ui.unit.Dp,
) {
  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier
        .align(align)
        .offset(x = x, y = y)
        .size(diameter)
        .clip(CircleShape)
        .background(color.copy(alpha = (color.alpha * 1.6f).coerceAtMost(1f))),
    )
  }
}

/**
 *  آیا این گوشی توانِ شیشه و مه را دارد؟
 *
 *  روی اندرویدِ قدیمی یا گوشیِ کم‌حافظه، مه یا اصلاً کار نمی‌کند یا
 *  فریم می‌اندازد؛ آنجا سطحِ توپر می‌دهیم تا کاربر حسِ نسخه‌ی ناقص
 *  نگیرد.
 */
@Composable
fun richGlass(): Boolean {
  val context = LocalContext.current
  return remember(context) {
    val am = context.getSystemService(ActivityManager::class.java)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am?.isLowRamDevice != true
  }
}

/**
 *  سطحِ شیشه‌ای.
 *
 *  جدایی از سه چیز می‌آید: نیمه‌شفافیِ سطح، یک سایه‌ی نرمِ رنگی زیرِ
 *  کارت، و **لبه‌ی روشنِ گرادینتی** که بدونش شیشه فقط یک لکه‌ی تار است.
 */
fun Modifier.glassSurface(
  shape: Shape,
  tint: Color,
  sheen: Color,
  border: Color,
  strong: Boolean = false,
  glow: Color = Color(0x1F7C5CFF),
): Modifier = this
  .shadow(
    elevation = if (strong) 14.dp else 8.dp,
    shape = shape,
    ambientColor = glow,
    spotColor = glow,
  )
  .clip(shape)
  .background(color = tint, shape = shape)
  .drawBehind {
    if (sheen.alpha > 0f) {
      drawRect(
        brush = Brush.verticalGradient(
          0f to sheen,
          1f to Color.Transparent,
          endY = size.height * 0.35f,
        ),
        size = Size(size.width, size.height * 0.35f),
      )
    }
  }
  .border(
    width = 1.dp,
    //  لبه از بالا-چپ روشن شروع می‌شود و پایین محو می‌شود — همان چیزی
    //  که شیشه را «شیشه» نشان می‌دهد نه یک لکه‌ی تار
    brush = Brush.linearGradient(
      listOf(
        border.copy(alpha = (border.alpha * 1.9f).coerceAtMost(1f)),
        border.copy(alpha = border.alpha * 0.3f),
      ),
    ),
    shape = shape,
  )
