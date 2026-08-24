package af.tohid.shop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// همان پالت نسخه وب، تا دو نسخه یک حس داشته باشند
private val Primary = Color(0xFF2C5CE6)
private val PrimaryDark = Color(0xFF1F3F9E)
private val Success = Color(0xFF18A06B)
private val Warning = Color(0xFFE8A13A)
private val Danger = Color(0xFFE54B4B)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF0FF),
    onPrimaryContainer = PrimaryDark,
    secondary = Success,
    tertiary = Warning,
    error = Danger,
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF1A2233),
    surface = Color.White,
    onSurface = Color(0xFF1A2233),
    surfaceVariant = Color(0xFFEEF2FA),
    onSurfaceVariant = Color(0xFF7C8698),
    outline = Color(0xFFE5EAF3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6C8BFF),
    onPrimary = Color(0xFF0F1420),
    primaryContainer = Color(0xFF1B2748),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Success,
    tertiary = Warning,
    error = Color(0xFFFF7B7B),
    background = Color(0xFF0F1420),
    onBackground = Color(0xFFE8EDF7),
    surface = Color(0xFF161D2C),
    onSurface = Color(0xFFE8EDF7),
    surfaceVariant = Color(0xFF1C2436),
    onSurfaceVariant = Color(0xFF94A0B8),
    outline = Color(0xFF28324A),
)

@Composable
fun TohidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
