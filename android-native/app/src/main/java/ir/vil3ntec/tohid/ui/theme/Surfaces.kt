package ir.vil3ntec.tohid.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 *  سطح‌ها — سیستمِ رنگِ VILL3N (§۶).
 *
 *  سطحِ ۰ زمینهٔ صفحه، ۱ کارت و پنل، ۲ بخشِ درونی؛ هر کدام رنگِ خودش. کارت =
 *  `surface` + خطِ دورِ یک‌پیکسلیِ `borderCard` + سایهٔ سبک. «کارتِ سفید روی زمینهٔ
 *  سفید بی خطِ دورِ دیدنی نه؛ سایه هرگز جای خط را نمی‌گیرد.» در شب هم خطِ دورِ
 *  خاکستری و سطح‌های جدا (کارت هرگز با زمینه یکی نمی‌شود).
 *  صفحه‌ها رنگ و گوشه را از این‌جا می‌گیرند، نه عدد در صفحه (`HardcodedColorsTest`).
 */

/**
 *  کارت: سطحِ ۱، گوشهٔ گرد، خطِ دورِ `borderCard` و سایهٔ سبک.
 *
 *  رنگِ سایه ضریب است، نه رنگِ نهایی: Compose آن را در آلفای سایهٔ سامانه
 *  ضرب می‌کند (`ShopColors.shadow`). `hairline = false` فقط برای چیزی که خطِ
 *  دورِ خودش را جدا می‌کشد.
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
  .then(if (hairline) Modifier.border(1.dp, colors.borderCard, shape) else Modifier)

/**
 *  ناحیه (منو، کاتالوگ، سبد): زمینهٔ رنگیِ گرد، بی سایه. با `border` مرزِ ناحیه
 *  دیده می‌شود («میزِ کامپیوتر: پنلِ کناری جدا از محتوا»).
 */
fun Modifier.zone(
  fill: Color,
  shape: androidx.compose.ui.graphics.Shape = Shape.cardLarge,
  border: Color? = null,
): Modifier =
  this.clip(shape).background(fill, shape).then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)

/** گوشهٔ گزینهٔ درونِ شیار — ۳ کمتر از کنترل تا با فاصلهٔ ۳ هم‌مرکز بنشیند */
val SegmentInnerShape = RoundedCornerShape(Corner.CONTROL - 3.dp)

/**
 *  شیارِ کنترلِ قطعه‌ای (زبانه‌ها، کلیدِ نما، روشِ پرداخت): زمینهٔ فرعیِ فرورفته
 *  (`backgroundSubtle`) و گزینهٔ انتخاب‌شدهٔ برآمده روی کارت — نه خطِ دور دورِ هر گزینه.
 */
fun Modifier.segmentTrack(colors: ShopColors): Modifier =
  this.clip(Shape.button).background(colors.backgroundSubtle, Shape.button).padding(3.dp)

/*
 *  تهِ «حرفِ اول»ِ کالای بی‌عکس — شش رنگِ ملایم، **یکی برای هر دسته**.
 *
 *  همهٔ کارت‌های بی‌عکس با یک رنگ، کاتالوگ را دوباره «همه یک رنگ» می‌کرد (نقدِ
 *  ۱۴۰۵/۰۷/۲۷). با رنگِ دسته، کالاهای یک دسته با یک نگاه کنارِ هم شناخته می‌شوند.
 *  VILL3N: شش رنگ از رنگ‌های پشتیبانِ همان سیستم‌اند (برند، آبی، کهربایی، بنفش،
 *  فیروزه‌ای، خاکستری؛ قرمز نه — قرمز فقط هشدارِ واقعی است) و جفت‌ها همان جفت‌های
 *  توکن‌اند؛ هر جفت (زمینه، حرف) ≥ ۴٫۵:۱ (`BackOfficeContrastTest`).
 */
fun avatarPalette(c: ShopColors): List<Pair<Color, Color>> = listOf(
  c.surfaceSelected to c.brandText,
  c.status.inDelivery.bg to c.status.inDelivery.fg,
  c.status.awaitingApproval.bg to c.status.awaitingApproval.fg,
  c.status.needsReview.bg to c.status.needsReview.fg,
  c.status.preparing.bg to c.status.preparing.fg,
  c.status.inactive.bg to c.status.inactive.fg,
)
val AvatarLight: List<Pair<Color, Color>> = avatarPalette(LightColors)
val AvatarDark: List<Pair<Color, Color>> = avatarPalette(DarkColors)

/** (زمینه، حرف) برای کلیدِ `key` (معمولاً نامِ دسته) — پایدار، همان رنگ در هر اجرا */
fun avatarColors(colors: ShopColors, key: String): Pair<Color, Color> {
  val list = if (colors.isDark) AvatarDark else AvatarLight
  val h = key.trim().fold(7) { a, ch -> a * 31 + ch.code }
  return list[Math.floorMod(h, list.size)]
}

/** گزینهٔ انتخاب‌شده درونِ `segmentTrack`: سطحِ کارت با خطِ دور */
fun Modifier.segmentSelected(colors: ShopColors): Modifier =
  this.softCard(colors, SegmentInnerShape, fill = colors.surface, elevation = Elevation.raised)
