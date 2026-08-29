package ir.vil3ntec.tohid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
  /*
   *  خطِ دورِ کارت‌ها.
   *
   *  یک بار کاملاً بی‌رنگ شد تا کارت «فقط با رنگش» دیده شود. نتیجه‌اش
   *  این بود که چند کارتِ پشتِ سرِ هم یک تودهٔ یکدست می‌شدند و معلوم
   *  نبود هرکدام تا کجاست. حالا هست، ولی نازک و کم‌رنگ: به‌اندازه‌ای که
   *  لبه فهمیده شود، نه آن‌قدر که خط به چشم بیاید.
   */
  /** لکه‌های نورِ زمینه */
  val auroraOne: Color,
  val auroraTwo: Color,
  /*
   *  کادرهای ورودی، جدا از کارت‌ها.
   *
   *  این سه رنگ عمداً از `border` جدا شده‌اند. `border` حاشیهٔ کارت است و
   *  نامرئی است — کارت باید با رنگش دیده شود نه با خطش. ولی کادرِ ورودی
   *  دقیقاً برعکس: کاربر باید ببیند کجا می‌شود نوشت.
   *
   *  یک بار این دو را یکی کردم و نتیجه‌اش این شد که حاشیهٔ همهٔ کادرهای
   *  متن در تمام برنامه نامرئی شد. دیگر یکی نمی‌شوند.
   */
  val fieldBg: Color,
  val fieldBorder: Color,
  val fieldFocus: Color,
)

/**
 *  روز — زمینهٔ سفید، کارت‌های آبیِ کم‌رنگ.
 *
 *  دو بار جایشان عوض شد تا درست شود. اولش زمینه سفید بود و کارت هم سفید:
 *  کارت دیده نمی‌شد. بعد زمینه آبی شد که کارت پیدا شود، ولی زمینهٔ آبی
 *  خودش تو‌ذوق می‌زد.
 *
 *  جوابِ درست وسط این دوتاست: **زمینه سفیدِ خالص، کارت آبیِ خیلی کم‌رنگ.**
 *  صفحه تمیز می‌ماند و کارت هم رنگِ خودش را دارد، نه خطِ دورش را.
 */
val LightColors = ShopColors(
  bg = Color(0xFFFFFFFF),
  surface = Color(0xFFEFF5FD),
  surface2 = Color(0xFFDDE9F8),
  border = Color(0x14101C2B),
  text = Color(0xFF0B1420),
  muted = Color(0xFF4E627A),
  muted2 = Color(0xFF7C8FA6),
  primary = Color(0xFF1268BE),
  primaryDark = Color(0xFF0B4C90),
  primaryTint = Color(0xFFD5E7FB),
  success = Color(0xFF0B6E55),
  successTint = Color(0xFFD3EFE6),
  warning = Color(0xFFA25E0B),
  warningTint = Color(0xFFF9E6C7),
  danger = Color(0xFFBE322D),
  dangerTint = Color(0xFFF9D8D6),
  accent = Color(0xFF0A7F8E),
  glow = Color(0x1A1268BE),
  sheen = Color(0x00FFFFFF),
  auroraOne = Color(0x140F7FD6),
  auroraTwo = Color(0x0F23B6C9),
  fieldBg = Color(0xFFFFFFFF),
  fieldBorder = Color(0xFFC6D5E6),
  fieldFocus = Color(0xFF1268BE),
)

/**
 *  شب — سیاهِ سرمه‌ای، با متنی که واقعاً خوانده می‌شود.
 *
 *  ایرادِ نسخهٔ قبل: رنگِ متن‌های فرعی (`muted` و `muted2`) آن‌قدر تیره
 *  بودند که روی زمینهٔ تقریباً سیاه محو می‌شدند. برچسبِ زیرِ هر عدد،
 *  توضیحِ زیرِ هر ردیف، نامِ ماه‌ها — هیچ‌کدام خوانده نمی‌شد.
 *
 *  حالا هر سه پلهٔ متن روشن‌تر شده‌اند تا نسبتِ کنتراستشان با زمینه از
 *  حدِ خوانایی بگذرد: متنِ اصلی تقریباً سفید، متنِ فرعی خاکستریِ روشن، و
 *  کم‌رنگ‌ترین هم آن‌قدر روشن که دیده شود.
 */
val DarkColors = ShopColors(
  bg = Color(0xFF05090F),
  surface = Color(0xFF141F31),
  surface2 = Color(0xFF1F3049),
  border = Color(0x24FFFFFF),
  text = Color(0xFFF2F7FD),
  muted = Color(0xFFB9CADF),
  muted2 = Color(0xFF92A6C0),
  primary = Color(0xFF7CD0F7),
  primaryDark = Color(0xFFB2E4FF),
  primaryTint = Color(0x3D7CD0F7),
  success = Color(0xFF56E0B6),
  successTint = Color(0x3356E0B6),
  warning = Color(0xFFFFC978),
  warningTint = Color(0x33FFC978),
  danger = Color(0xFFFF908A),
  dangerTint = Color(0x33FF908A),
  accent = Color(0xFF6EEDE2),
  glow = Color(0x4D7CD0F7),
  sheen = Color(0x0FFFFFFF),
  auroraOne = Color(0x332F7FD6),
  auroraTwo = Color(0x2623B6C9),
  fieldBg = Color(0xFF16233A),
  fieldBorder = Color(0xFF3C5474),
  fieldFocus = Color(0xFF7CD0F7),
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
      // خطِ دورِ اجزای آمادهٔ متریال (کادر متن، تراشه، دکمهٔ خطی).
      // این را نباید با حاشیهٔ کارت یکی کرد: حاشیهٔ کارت نامرئی است و
      // اگر اینجا هم بنشیند، کادرِ متن در کلِ برنامه بی‌خط می‌شود.
      outline = colors.fieldBorder,
      outlineVariant = colors.fieldBorder,
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
      // خطِ دورِ اجزای آمادهٔ متریال (کادر متن، تراشه، دکمهٔ خطی).
      // این را نباید با حاشیهٔ کارت یکی کرد: حاشیهٔ کارت نامرئی است و
      // اگر اینجا هم بنشیند، کادرِ متن در کلِ برنامه بی‌خط می‌شود.
      outline = colors.fieldBorder,
      outlineVariant = colors.fieldBorder,
      error = colors.danger,
      onError = Color.White,
      errorContainer = colors.dangerTint,
      onErrorContainer = colors.danger,
      scrim = Color(0x990C1626),
    )
  }

  CompositionLocalProvider(LocalShopColors provides colors) {
    // متن روی تبلت بزرگ‌تر می‌شود. یک جا حساب می‌شود و همهٔ صفحه‌ها را
    // می‌گیرد؛ وگرنه باید سربرگِ هر صفحه را جدا بزرگ می‌کردیم و یکی‌شان
    // جا می‌ماند — همان که جا مانده بود.
    val width = LocalConfiguration.current.screenWidthDp
    val scale = when {
      width >= 900 -> 1.22f
      width >= 600 -> 1.12f
      else -> 1f
    }
    val type = remember(scale) { shopTypography(scale) }
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
  }
}
