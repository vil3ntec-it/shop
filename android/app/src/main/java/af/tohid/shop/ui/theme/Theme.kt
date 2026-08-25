package af.tohid.shop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.R

/* ------------------------------------------------------------------ */
/*  فونت — همان Vazirmatn نسخه‌ی وب                                    */
/* ------------------------------------------------------------------ */

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
)

/* ------------------------------------------------------------------ */
/*  توکن‌های رنگی — دقیقاً معادل متغیرهای CSS نسخه‌ی وب                 */
/* ------------------------------------------------------------------ */

@Immutable
data class TohidColors(
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
    val isDark: Boolean,
)

private val LightTokens = TohidColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8FC),
    surface2 = Color(0xFFEEF2FA),
    border = Color(0xFFE5EAF3),
    text = Color(0xFF1A2233),
    muted = Color(0xFF7C8698),
    muted2 = Color(0xFFA4ADBD),
    primary = Color(0xFF2C5CE6),
    primaryDark = Color(0xFF1F3F9E),
    primaryTint = Color(0xFFEAF0FF),
    success = Color(0xFF18A06B),
    successTint = Color(0xFFE8F8F1),
    warning = Color(0xFFE8A13A),
    warningTint = Color(0xFFFDF3E4),
    danger = Color(0xFFE54B4B),
    dangerTint = Color(0xFFFDECEC),
    isDark = false,
)

private val DarkTokens = TohidColors(
    bg = Color(0xFF0F1420),
    surface = Color(0xFF161D2C),
    surface2 = Color(0xFF1C2436),
    border = Color(0xFF2A3448),
    text = Color(0xFFE8ECF5),
    muted = Color(0xFF8B95AB),
    muted2 = Color(0xFF5F6A82),
    primary = Color(0xFF5B82F0),
    primaryDark = Color(0xFF8FABF7),
    primaryTint = Color(0xFF1B2942),
    success = Color(0xFF3ECF94),
    successTint = Color(0xFF12291F),
    warning = Color(0xFFF0B955),
    warningTint = Color(0xFF2E2412),
    danger = Color(0xFFF0685F),
    dangerTint = Color(0xFF2E1616),
    isDark = true,
)

val LocalTohidColors = staticCompositionLocalOf { LightTokens }

/** میان‌بر: `T.primary` به‌جای `MaterialTheme.colorScheme.primary` */
val T: TohidColors
    @Composable get() = LocalTohidColors.current

/* ------------------------------------------------------------------ */
/*  اندازه‌ها                                                          */
/* ------------------------------------------------------------------ */

object Radius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
}

object Dims {
    val header = 64.dp
    val bottomNav = 66.dp
    val screenPadding = 16.dp
}

/* ------------------------------------------------------------------ */
/*  تایپوگرافی                                                         */
/* ------------------------------------------------------------------ */

private fun fa(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Int = (size * 1.7).toInt(),
) = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    textDirection = TextDirection.Content,
)

private val TohidTypography = Typography(
    displayLarge = fa(28, FontWeight.ExtraBold),
    displayMedium = fa(24, FontWeight.ExtraBold),
    displaySmall = fa(22, FontWeight.Bold),
    headlineLarge = fa(20, FontWeight.ExtraBold),
    headlineMedium = fa(18, FontWeight.ExtraBold),
    headlineSmall = fa(17, FontWeight.Bold),
    titleLarge = fa(17, FontWeight.Bold),
    titleMedium = fa(15, FontWeight.Bold),
    titleSmall = fa(14, FontWeight.SemiBold),
    bodyLarge = fa(14, FontWeight.Normal),
    bodyMedium = fa(13, FontWeight.Normal),
    bodySmall = fa(12, FontWeight.Normal),
    labelLarge = fa(13, FontWeight.SemiBold),
    labelMedium = fa(12, FontWeight.SemiBold),
    labelSmall = fa(11, FontWeight.SemiBold),
)

/* ------------------------------------------------------------------ */

@Composable
fun TohidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = Color.White,
            primaryContainer = tokens.primaryTint,
            onPrimaryContainer = tokens.primaryDark,
            secondary = tokens.success,
            tertiary = tokens.warning,
            error = tokens.danger,
            onError = Color.White,
            background = tokens.bg,
            onBackground = tokens.text,
            surface = tokens.bg,
            onSurface = tokens.text,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.muted,
            outline = tokens.border,
            outlineVariant = tokens.border,
        )
    } else {
        lightColorScheme(
            primary = tokens.primary,
            onPrimary = Color.White,
            primaryContainer = tokens.primaryTint,
            onPrimaryContainer = tokens.primaryDark,
            secondary = tokens.success,
            tertiary = tokens.warning,
            error = tokens.danger,
            onError = Color.White,
            background = tokens.bg,
            onBackground = tokens.text,
            surface = tokens.bg,
            onSurface = tokens.text,
            surfaceVariant = tokens.surface,
            onSurfaceVariant = tokens.muted,
            outline = tokens.border,
            outlineVariant = tokens.border,
        )
    }

    CompositionLocalProvider(
        LocalTohidColors provides tokens,
        // کل برنامه راست‌چین است — دقیقاً مثل dir="rtl" در نسخه‌ی وب
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TohidTypography,
            content = content,
        )
    }
}
