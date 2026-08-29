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
 *  یک نکته که در نسخهٔ قبل اشتباه بود: زمینه تقریباً سفید بود و کارت‌ها
 *  هم سفید، پس کارت از زمینه جدا نمی‌شد و کاربر فقط یک خطِ نازک می‌دید.
 *  حالا زمینه یک آبیِ یخیِ روشن است و کارت سفیدِ خالص؛ جدایی از **اختلافِ
 *  رنگ** می‌آید، نه از خطِ دورِ کارت.
 */
val LightColors = ShopColors(
  bg = Color(0xFFDFEAF7),
  surface = Color(0xFFFFFFFF),
  surface2 = Color(0xFFECF3FC),
  // حاشیه تقریباً نامرئی است؛ فقط برای جاهایی که واقعاً خط لازم است
  border = Color(0x0F1B4F80),
  text = Color(0xFF0C1626),
  muted = Color(0xFF56687F),
  muted2 = Color(0xFF8698AF),
  primary = Color(0xFF1B7FD4),
  primaryDark = Color(0xFF115EA3),
  primaryTint = Color(0xFFD5E9FD),
  success = Color(0xFF0F7A60),
  successTint = Color(0xFFD5F1E9),
  warning = Color(0xFFB86F10),
  warningTint = Color(0xFFFBE9CE),
  danger = Color(0xFFCC3B35),
  dangerTint = Color(0xFFFBDCDA),
  accent = Color(0xFF0E93A4),
  glow = Color(0x1F1B7FD4),
  sheen = Color(0x00FFFFFF),
  auroraOne = Color(0x2E7FBDF0),
  auroraTwo = Color(0x2340D3E0),
)

/**
 *  شبِ قطبی — حالتِ اصلیِ برنامه.
 *
 *  اینجا هم همان قاعده: فاصلهٔ روشنیِ زمینه تا کارت باید آن‌قدر باشد که
 *  بدونِ خطِ دور هم دیده شود. سه پله داریم — زمینه، کارت، کارتِ روی کارت.
 */
val DarkColors = ShopColors(
  bg = Color(0xFF050A12),
  surface = Color(0xFF121E30),
  surface2 = Color(0xFF1C2C44),
  border = Color(0x14A7D6F7),
  text = Color(0xFFEAF3FC),
  muted = Color(0xFF9CB0CA),
  muted2 = Color(0xFF6A80A0),
  primary = Color(0xFF67C6F5),
  primaryDark = Color(0xFF9BDCFF),
  primaryTint = Color(0x2E67C6F5),
  success = Color(0xFF3ED6A8),
  successTint = Color(0x2E3ED6A8),
  warning = Color(0xFFF3BE63),
  warningTint = Color(0x2EF3BE63),
  danger = Color(0xFFFF7A72),
  dangerTint = Color(0x2EFF7A72),
  accent = Color(0xFF56E5DA),
  glow = Color(0x3D67C6F5),
  sheen = Color(0x14FFFFFF),
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
  /*
   *  اجزای آمادهٔ متریال — تراشه، دکمه، کادرِ متن — رنگشان را از همین
   *  طرح می‌گیرند. تا وقتی این‌ها را ننویسیم، متریال رنگ‌های پیش‌فرضِ
   *  بنفشِ خودش را می‌گذارد و خطِ خاکستری دورِ تراشه‌ها می‌کشد؛ همان
   *  چیزی که در صفحهٔ گزارش‌ها دیده می‌شد.
   */
  val scheme = if (dark) {
    darkColorScheme(
      primary = colors.primary,
      onPrimary = Color(0xFF04121F),
      primaryContainer = colors.primaryTint,
      onPrimaryContainer = colors.primaryDark,
      secondary = colors.accent,
      onSecondary = Color(0xFF04121F),
      secondaryContainer = colors.primaryTint,
      onSecondaryContainer = colors.primary,
      background = colors.bg,
      onBackground = colors.text,
      surface = colors.surface,
      onSurface = colors.text,
      surfaceVariant = colors.surface2,
      onSurfaceVariant = colors.muted,
      surfaceContainer = colors.surface,
      surfaceContainerHigh = colors.surface2,
      surfaceContainerHighest = colors.surface2,
      outline = colors.border,
      outlineVariant = colors.border,
      error = colors.danger,
      onError = Color(0xFF2A0B09),
      errorContainer = colors.dangerTint,
      onErrorContainer = colors.danger,
      scrim = Color(0xCC020509),
    )
  } else {
    lightColorScheme(
      primary = colors.primary,
      onPrimary = Color.White,
      primaryContainer = colors.primaryTint,
      onPrimaryContainer = colors.primaryDark,
      secondary = colors.accent,
      onSecondary = Color.White,
      secondaryContainer = colors.primaryTint,
      onSecondaryContainer = colors.primaryDark,
      background = colors.bg,
      onBackground = colors.text,
      surface = colors.surface,
      onSurface = colors.text,
      surfaceVariant = colors.surface2,
      onSurfaceVariant = colors.muted,
      surfaceContainer = colors.surface,
      surfaceContainerHigh = colors.surface2,
      surfaceContainerHighest = colors.surface2,
      outline = colors.border,
      outlineVariant = colors.border,
      error = colors.danger,
      onError = Color.White,
      errorContainer = colors.dangerTint,
      onErrorContainer = colors.danger,
      scrim = Color(0x990C1626),
    )
  }

  CompositionLocalProvider(LocalShopColors provides colors) {
    MaterialTheme(colorScheme = scheme, typography = ShopTypography, content = content)
  }
}
