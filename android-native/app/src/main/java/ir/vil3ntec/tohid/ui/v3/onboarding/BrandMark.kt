package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.R
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import kotlin.math.cos
import kotlin.math.sin

/**
 *  نشانِ برنامه روی کاشیِ سفید (شب: سطحِ یک) با مرزِ مویی — بالای هر دروازه. خودِ تصویر همان
 *  فایلِ آیکنِ لانچر است (`logo_mark`) و فقط **رنگش** به primaryِ تمِ جاری می‌چرخد: شکل، برجستگی و
 *  سایه دست نمی‌خورند (چرخشِ فام، نه رنگِ یک‌دست).
 */
@Composable
internal fun BrandMark(size: Dp = 56.dp) {
  val c = PhoneV3.colors
  val brand = PhoneV3Palette.glyph(c, PhoneV3Palette.Hue.BRAND)
  val filter = remember(brand) { ColorFilter.colorMatrix(ColorMatrix(BrandHue.rotation(BrandHue.shiftTo(brand)))) }
  val shape = RoundedCornerShape(size * 0.3f)
  Box(
    Modifier
      .size(size)
      .shadow(6.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.10f))
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(R.drawable.logo_mark),
      contentDescription = null,
      colorFilter = filter,
      contentScale = ContentScale.Fit,
      modifier = Modifier.size(size * 0.64f),
    )
  }
}

/** چرخشِ فامِ نشان تا رنگِ برندِ تم — ریاضیِ خالص (آزمون: `OnboardingModelsTest`) */
object BrandHue {
  /** فامِ غالبِ `logo_mark.png` (میانهٔ پیکسل‌های رنگی، درجه) */
  const val LOGO_HUE = 218f

  /** فامِ یک رنگ، ۰ تا ۳۶۰ */
  fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
      r -> ((g - b) / d).mod(6f)
      g -> (b - r) / d + 2f
      else -> (r - g) / d + 4f
    } * 60f
    return h.mod(360f)
  }

  /** کوتاه‌ترین چرخش از فامِ نشان تا فامِ برند (−۱۸۰ تا ۱۸۰) */
  fun shiftTo(brand: Color): Float {
    val d = (hueOf(brand) - LOGO_HUE).mod(360f)
    return if (d > 180f) d - 360f else d
  }

  /** ماتریسِ ۴×۵ ِ چرخشِ فام (feColorMatrix hueRotate)؛ روشنی و آلفا می‌مانند */
  fun rotation(degrees: Float): FloatArray {
    val a = Math.toRadians(degrees.toDouble())
    val cs = cos(a).toFloat()
    val sn = sin(a).toFloat()
    return floatArrayOf(
      0.213f + cs * 0.787f - sn * 0.213f, 0.715f - cs * 0.715f - sn * 0.715f, 0.072f - cs * 0.072f + sn * 0.928f, 0f, 0f,
      0.213f - cs * 0.213f + sn * 0.143f, 0.715f + cs * 0.285f + sn * 0.140f, 0.072f - cs * 0.072f - sn * 0.283f, 0f, 0f,
      0.213f - cs * 0.213f - sn * 0.787f, 0.715f - cs * 0.715f + sn * 0.715f, 0.072f + cs * 0.928f + sn * 0.072f, 0f, 0f,
      0f, 0f, 0f, 1f, 0f,
    )
  }
}
