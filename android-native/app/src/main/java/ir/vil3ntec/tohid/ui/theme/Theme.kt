package ir.vil3ntec.tohid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 *  رنگ‌ها — «آبیِ یخ».
 *
 *  زمینه سرمه‌ای تیره است و نشانِ برنامه یک آبیِ روشنِ یخی؛ همین یک رنگ
 *  در تمامِ برنامه تکرار می‌شود تا هرجای اپ که باشی، بدانی توحید است.
 *
 *  دو تصمیم که عمدی‌اند:
 *
 *   • **سطح‌ها شفاف‌اند، نه تخت.** کارت‌ها کمی از زمینه روشن‌ترند و
 *     حاشیه‌شان آن‌قدر کم‌رنگ است که دیده نشود؛ چیزی که کارت را جدا
 *     می‌کند، اختلافِ ملایمِ روشنی است نه خطِ دورش. جعبهٔ خط‌کشی‌شده،
 *     ارزان به نظر می‌رسد.
 *
 *   • **بنفش و صورتی هیچ‌جا نیست.** فقط سرمه‌ای، آبیِ یخ و فیروزه‌ای؛
 *     و سبز و نارنجی و قرمز فقط جایی که معنیِ مالی دارند (سود، هشدار،
 *     بدهی) — نه برای تزیین.
 */
data class ShopColors(
  val bg: Color,
  val surface: Color,
  val surface2: Color,
  val border: Color,
  val text: Color,
  val muted: Color,
  val muted2: Color,
  val primary: Color,
  val primaryDark: Color,
  val primaryTint: Color,
  val success: Color,
  val successTint: Color,
  val warning: Color,
  val warningTint: Color,
  val danger: Color,
  val dangerTint: Color,
  /** فیروزه‌ای — تأکیدِ دوم، کنارِ آبیِ اصلی */
  val accent: Color,
  /** هالهٔ آبیِ ملایمی که زیرِ کارت‌ها و دکمه‌ها می‌نشیند */
  val glow: Color,
  /** لبهٔ روشنِ بالای کارت‌های شیشه‌ای */
  val sheen: Color,
  /** لکه‌های نورِ زمینه */
  val auroraOne: Color,
  val auroraTwo: Color,
)

/**
 *  روزِ قطبی — همان هویتِ آبی، روی زمینهٔ روشن.
 *
 *  کلیدِ روشن/تاریک قابلیتِ موجودِ برنامه است و برداشته نمی‌شود؛ ولی
 *  حالتِ روشن هم دیگر سفیدِ خالی نیست: یک سفیدِ آبی‌فام که همان خانواده
 *  را نگه می‌دارد.
 */
val LightColors = ShopColors(
  bg = Color(0xFFF2F7FD),
  surface = Color(0xFFFFFFFF),
  surface2 = Color(0xFFE9F1FB),
  border = Color(0xFFDCE8F6),
  text = Color(0xFF0C1626),
  muted = Color(0xFF5C6E86),
  muted2 = Color(0xFF93A4BA),
  primary = Color(0xFF1B7FD4),
  primaryDark = Color(0xFF115EA3),
  primaryTint = Color(0xFFDCEEFF),
  success = Color(0xFF12876B),
  successTint = Color(0xFFDDF4EE),
  warning = Color(0xFFC77B18),
  warningTint = Color(0xFFFBEEDA),
  danger = Color(0xFFD5453F),
  dangerTint = Color(0xFFFBE4E3),
  accent = Color(0xFF12A5B8),
  glow = Color(0x141B7FD4),
  sheen = Color(0xB3FFFFFF),
  auroraOne = Color(0x2263B8F0),
  auroraTwo = Color(0x1A3FD0DE),
)

/**
 *  شبِ قطبی — حالتِ اصلیِ برنامه.
 *
 *  زمینه سرمه‌ایِ تقریباً سیاه است تا نورِ آبی روی آن بنشیند. سطح‌ها سه
 *  پله دارند (زمینه، کارت، کارتِ روی کارت) و اختلافشان کم است؛ عمق از
 *  همین پله‌ها می‌آید، نه از سایهٔ سنگین.
 */
val DarkColors = ShopColors(
  bg = Color(0xFF060B14),
  surface = Color(0xFF0E1725),
  surface2 = Color(0xFF152134),
  border = Color(0x1F7FC4F5),
  text = Color(0xFFEAF3FC),
  muted = Color(0xFF93A9C4),
  muted2 = Color(0xFF5D7391),
  primary = Color(0xFF67C6F5),
  primaryDark = Color(0xFF9BDCFF),
  primaryTint = Color(0x2667C6F5),
  success = Color(0xFF3ED6A8),
  successTint = Color(0x263ED6A8),
  warning = Color(0xFFF3BE63),
  warningTint = Color(0x26F3BE63),
  danger = Color(0xFFFF7A72),
  dangerTint = Color(0x26FF7A72),
  accent = Color(0xFF56E5DA),
  glow = Color(0x3367C6F5),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x3D2F7FD6),
  auroraTwo = Color(0x2E23B6C9),
)

/**
 *  گِردیِ گوشه‌ها — بزرگ و نرم.
 *
 *  گوشهٔ تیز، عنصر را «جعبه» نشان می‌دهد. گِردیِ زیاد همان عنصر را روی
 *  زمینه شناور می‌کند، که همان حسی است که می‌خواهیم.
 */
object Radius {
  val sm = 16.dp
  val md = 22.dp
  val lg = 28.dp
}

val LocalShopColors = staticCompositionLocalOf { LightColors }

/** رنگ‌های برنامه از هرجای رابط کاربری: `Shop.colors.primary` */
object Shop {
  val colors: ShopColors
    @Composable get() = LocalShopColors.current
}

/** انتخابِ کاربر برای ظاهر — همان `theme` در تنظیماتِ نسخهٔ وب */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

@Composable
fun TohidTheme(
  choice: ThemeChoice = ThemeChoice.SYSTEM,
  content: @Composable () -> Unit,
) {
  val dark = when (choice) {
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
  }
  val colors = if (dark) DarkColors else LightColors
  val scheme = if (dark) {
    darkColorScheme(
      primary = colors.primary,
      background = colors.bg,
      surface = colors.surface,
      onPrimary = Color.White,
      onBackground = colors.text,
      onSurface = colors.text,
      error = colors.danger,
    )
  } else {
    lightColorScheme(
      primary = colors.primary,
      background = colors.bg,
      surface = colors.surface,
      onPrimary = Color.White,
      onBackground = colors.text,
      onSurface = colors.text,
      error = colors.danger,
    )
  }

  CompositionLocalProvider(LocalShopColors provides colors) {
    MaterialTheme(colorScheme = scheme, typography = ShopTypography, content = content)
  }
}
