package ir.vil3ntec.tohid.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 *  سطح‌های نسخهٔ ۲ — VISUAL-DIRECTION «نسخهٔ ۲».
 *
 *  صاحب محصول (۱۴۰۵/۰۷/۲۷): «همه‌جا خطِ تیره، همهٔ جعبه‌ها یک رنگ، هیچ چیز
 *  شناخته نمی‌شود». پس:
 *   • کارت = سطحِ سفید + گوشهٔ ۱۶ + سایهٔ نرمِ پهن + لبهٔ مویی (۷٪). خطِ تیره نه.
 *   • ناحیه = زمینهٔ رنگیِ خودش (منو، کاتالوگ، سبد)، نه یک کارتِ دیگر با همان رنگ.
 *  صفحه‌ها رنگ و گوشه را از این‌جا می‌گیرند، نه عدد در صفحه (`HardcodedColorsTest`).
 */

/**
 *  کارتِ نرم: سایهٔ پهن و کم‌رنگ، گوشهٔ گرد و لبهٔ مویی.
 *
 *  رنگِ سایه ضریب است، نه رنگِ نهایی: Compose آن را در آلفای سایهٔ سامانه
 *  (حدودِ ۱۹٪) ضرب می‌کند؛ پس `ShopColors.shadow` با آلفای بالا سایه‌ای کم‌رنگ می‌دهد.
 */
fun Modifier.softCard(
  colors: ShopColors,
  shape: androidx.compose.ui.graphics.Shape = Shape.card,
  fill: Color = colors.surface,
  elevation: Dp = Elevation.card,
  hairline: Boolean = true,
): Modifier = this
  .shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
  .clip(shape)
  .background(fill, shape)
  .then(if (hairline) Modifier.border(1.dp, colors.border, shape) else Modifier)

/** ناحیه (منو، کاتالوگ، سبد): زمینهٔ رنگیِ گرد، بی سایه و بی خط */
fun Modifier.zone(fill: Color, shape: androidx.compose.ui.graphics.Shape = Shape.cardLarge): Modifier =
  this.clip(shape).background(fill, shape)

/** گوشهٔ گزینهٔ درونِ شیار — ۳ کمتر از کنترل تا با فاصلهٔ ۳ هم‌مرکز بنشیند */
val SegmentInnerShape = RoundedCornerShape(Corner.CONTROL - 3.dp)

/**
 *  شیارِ کنترلِ قطعه‌ای (زبانه‌ها، کلیدِ نما، روشِ پرداخت): زمینهٔ فرورفته و گزینهٔ
 *  انتخاب‌شدهٔ سفیدِ برآمده — نه خطِ دور دورِ هر گزینه. شیار همان رنگِ سردِ
 *  `zoneNav` است: در روز کمی تیره‌تر از زمینه و در شب تیره‌تر از کارت.
 */
fun Modifier.segmentTrack(colors: ShopColors): Modifier =
  this.clip(Shape.button).background(colors.zoneNav, Shape.button).padding(3.dp)

/*
 *  تهِ «حرفِ اول»ِ کالای بی‌عکس — شش رنگِ ملایم، **یکی برای هر دسته**.
 *
 *  همهٔ کارت‌های بی‌عکس با یک رنگ، کاتالوگ را دوباره «همه یک رنگ» می‌کرد (نقدِ
 *  ۱۴۰۵/۰۷/۲۷). با رنگِ دسته، کالاهای یک دسته با یک نگاه کنارِ هم شناخته می‌شوند.
 *  هر جفت (زمینه، حرف) ≥ ۴٫۵:۱ (`BackOfficeContrastTest`).
 */
val AvatarLight: List<Pair<Color, Color>> = listOf(
  Color(0xFFECE9FD) to Color(0xFF4532C2),  //  نیلی
  Color(0xFFE2F4EE) to Color(0xFF0B6B55),  //  سبزآبی
  Color(0xFFFCEFD8) to Color(0xFF7A4D00),  //  کهربایی
  Color(0xFFFCE7EC) to Color(0xFF9F1D3A),  //  گلی
  Color(0xFFE4EDFD) to Color(0xFF1A55C4),  //  آبی
  Color(0xFFEAEDF3) to Color(0xFF3D4557),  //  خاکستری
)
val AvatarDark: List<Pair<Color, Color>> = listOf(
  Color(0xFF2A2550) to Color(0xFFC3B8FF),
  Color(0xFF163A33) to Color(0xFF6FDCB8),
  Color(0xFF3A2E17) to Color(0xFFF2C27A),
  Color(0xFF3D1E27) to Color(0xFFFF9DB0),
  Color(0xFF1C2D4A) to Color(0xFF9CC2FF),
  Color(0xFF262B36) to Color(0xFFC2C8D6),
)

/** (زمینه، حرف) برای کلیدِ `key` (معمولاً نامِ دسته) — پایدار، همان رنگ در هر اجرا */
fun avatarColors(colors: ShopColors, key: String): Pair<Color, Color> {
  val list = if (colors.bg.luminance() < 0.5f) AvatarDark else AvatarLight
  val h = key.trim().fold(7) { a, ch -> a * 31 + ch.code }
  return list[Math.floorMod(h, list.size)]
}

/** گزینهٔ انتخاب‌شده درونِ `segmentTrack` */
fun Modifier.segmentSelected(colors: ShopColors): Modifier =
  this.softCard(colors, SegmentInnerShape, fill = colors.surface, elevation = Elevation.raised)
