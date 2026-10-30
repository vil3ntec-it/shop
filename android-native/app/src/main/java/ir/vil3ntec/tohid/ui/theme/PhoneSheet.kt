package ir.vil3ntec.tohid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 *  توکن‌های برگهٔ تیرهٔ گوشی — نوار و برگهٔ سبد و قرصِ تبِ فعالِ نوارِ پایین
 *  (phone-ui، تصویرهای مرجعِ ۳، ۵ و ۶؛ VISUAL-DIRECTION §۴).
 *
 *  جدا از `ShopColors` نگه داشته شده تا پالتِ اصلی (`Theme.kt`، مالِ design-v2)
 *  دست نخورد. فقط برنامهٔ بومی (گوشیِ اندروید؛ پیش‌نمایشِ گوشیِ `DevShot`) — نسخهٔ
 *  وب به سفارشِ صاحب محصول دست نمی‌خورد. متن روی برگه ≥ ۴٫۵:۱ و شکلِ غیرمتنی
 *  ≥ ۳:۱ (`PhoneSheetContrastTest`).
 *
 *   • `sheet` زمینهٔ تیرهٔ نوار و برگهٔ سبد؛ `onSheet`/`onSheetMuted` متن رویش.
 *   • `accent`/`onAccent` دایرهٔ شمار و دکمهٔ اصلیِ برگه — همان بنفشِ برنامه در
 *     پلهٔ روشن، تا روی تیره خوانا بماند (یک رنگِ اصلی، نه رنگِ دوم).
 *   • `line` خطِ مویی و ریلِ نوارِ پیشرفت؛ `raised` زمینهٔ گردِ آیکن روی برگه.
 *   • `navPill`/`onNavPill` قرصِ تبِ فعال: روز تیره روی نوارِ روشن، شب روشن روی
 *     نوارِ تیره — تا در هر دو تم دیده شود.
 */
data class SheetColors(
  val sheet: Color,
  val onSheet: Color,
  val onSheetMuted: Color,
  val accent: Color,
  val onAccent: Color,
  val line: Color,
  val raised: Color,
  val navPill: Color,
  val onNavPill: Color,
)

val LightSheet = SheetColors(
  sheet = Color(0xFF17151F),
  onSheet = Color(0xFFFFFFFF),
  onSheetMuted = Color(0xFFB8B3CC),
  accent = Color(0xFFB9A6FF),
  onAccent = Color(0xFF17151F),
  line = Color(0x24FFFFFF),
  raised = Color(0x14FFFFFF),
  navPill = Color(0xFF17151F),
  onNavPill = Color(0xFFFFFFFF),
)

//  شب: برگه یک پله روشن‌تر از زمینه، تا «بالاتر» دیده شود نه سوراخ
val DarkSheet = SheetColors(
  sheet = Color(0xFF221F33),
  onSheet = Color(0xFFF0EEF8),
  onSheetMuted = Color(0xFFB3AEC8),
  accent = Color(0xFFA78BFF),
  onAccent = Color(0xFF14102A),
  line = Color(0x24FFFFFF),
  raised = Color(0x14FFFFFF),
  navPill = Color(0xFFF0EEF8),
  onNavPill = Color(0xFF17151F),
)

object PhoneSheet {
  /** تمِ جاری از روشنیِ زمینهٔ همان `Shop.colors` — بی نیاز به دست‌زدن به `TohidTheme` */
  val colors: SheetColors
    @Composable get() = of(Shop.colors.bg)

  fun of(bg: Color): SheetColors = if (bg.luminance() < 0.5f) DarkSheet else LightSheet
}
