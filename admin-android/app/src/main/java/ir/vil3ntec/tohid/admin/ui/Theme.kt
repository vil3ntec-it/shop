package ir.vil3ntec.tohid.admin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.admin.R

/**
 *  ظاهرِ برنامهٔ مدیریت.
 *
 *  عمداً تیره و ساده است و رنگِ برنامهٔ مشتری را ندارد: کسی که هر دو را
 *  روی گوشی دارد، نباید یک لحظه فکر کند در کدام است — اینجا با یک زدن
 *  اشتراکِ کسی عوض می‌شود.
 */
data class AdminColors(
  val bg: Color,
  val surface: Color,
  val surface2: Color,
  val border: Color,
  val text: Color,
  val muted: Color,
  val primary: Color,
  val success: Color,
  val warn: Color,
  val danger: Color,
)

private val Dark = AdminColors(
  bg = Color(0xFF0B1020),
  surface = Color(0xFF141B30),
  surface2 = Color(0xFF1D2740),
  border = Color(0xFF283350),
  text = Color(0xFFEDF1FA),
  muted = Color(0xFF93A0BF),
  primary = Color(0xFF4C8DFF),
  success = Color(0xFF35C48E),
  warn = Color(0xFFF5B93B),
  danger = Color(0xFFF3616B),
)

private val Light = AdminColors(
  bg = Color(0xFFF3F5FB),
  surface = Color(0xFFFFFFFF),
  surface2 = Color(0xFFEBEFF8),
  border = Color(0xFFDBE2EF),
  text = Color(0xFF141B30),
  muted = Color(0xFF64708C),
  primary = Color(0xFF2563C9),
  success = Color(0xFF14926A),
  warn = Color(0xFFC98A0B),
  danger = Color(0xFFD03B45),
)

private val LocalAdminColors = staticCompositionLocalOf { Dark }

object Admin {
  val colors: AdminColors
    @Composable get() = LocalAdminColors.current
}

private val vazir = FontFamily(
  Font(R.font.vazirmatn_regular, FontWeight.Normal),
  Font(R.font.vazirmatn_medium, FontWeight.Medium),
  Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
  Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

@Composable
fun AdminTheme(content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val colors = if (dark) Dark else Light

  val scheme = if (dark) {
    darkColorScheme(
      primary = colors.primary, background = colors.bg, surface = colors.surface,
      onPrimary = Color.White, onBackground = colors.text, onSurface = colors.text,
      error = colors.danger,
    )
  } else {
    lightColorScheme(
      primary = colors.primary, background = colors.bg, surface = colors.surface,
      onPrimary = Color.White, onBackground = colors.text, onSurface = colors.text,
      error = colors.danger,
    )
  }

  //  یک قلم برای همه‌ی اندازه‌ها؛ فارسی با قلمِ پیش‌فرضِ اندروید بد می‌نشیند
  val base = Typography()
  fun TextStyle.fa() = copy(fontFamily = vazir)
  val typography = Typography(
    displayLarge = base.displayLarge.fa(), displayMedium = base.displayMedium.fa(),
    displaySmall = base.displaySmall.fa(), headlineLarge = base.headlineLarge.fa(),
    headlineMedium = base.headlineMedium.fa(), headlineSmall = base.headlineSmall.fa(),
    titleLarge = base.titleLarge.fa(), titleMedium = base.titleMedium.fa(),
    titleSmall = base.titleSmall.fa(), bodyLarge = base.bodyLarge.fa(),
    bodyMedium = base.bodyMedium.fa(), bodySmall = base.bodySmall.fa().copy(fontSize = 13.sp),
    labelLarge = base.labelLarge.fa(), labelMedium = base.labelMedium.fa(),
    labelSmall = base.labelSmall.fa(),
  )

  CompositionLocalProvider(
    LocalAdminColors provides colors,
    //  کلِ برنامه راست‌به‌چپ است
    LocalLayoutDirection provides LayoutDirection.Rtl,
  ) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
  }
}
