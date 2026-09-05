package ir.vil3ntec.tohid.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.screens.Springs
import androidx.compose.ui.unit.dp

/**
 *  رنگ‌ها — «شیشه روی نورِ آرورا».
 *
 *  سه لایه، و ترتیبشان تمامِ ظاهرِ برنامه است:
 *
 *      نورِ آرورا (سه لکهٔ رنگ، پشتِ همه‌چیز)
 *          ↓
 *      پنل‌های شیشه‌ای (سفیدِ نیمه‌شفاف + لبهٔ روشن)
 *          ↓
 *      محتوا، بدونِ زمینهٔ خودش
 *
 *  ── چرا عوض شد ─────────────────────────────────────────────────────
 *  پالتِ پیشین «آبیِ یخ» بود: کارتِ خاکستری-آبیِ توپر روی زمینهٔ سفید.
 *  ظاهرش سالمِ متریالِ پیش‌فرض بود و همین ایرادش بود — از هر برنامهٔ
 *  دیگری قابلِ تشخیص نبود. قرارِ صاحب مخزن این شد که ظاهر در سطحِ
 *  برنامه‌های مالیِ امروز باشد، نه متریالِ خام.
 *
 *  سه قاعده که شکسته نمی‌شوند:
 *
 *   • **سطح‌ها شیشه‌اند، نه رنگِ توپر.** `surface` یک سفیدِ نیمه‌شفاف
 *     است؛ چیزی که پنل را جدا می‌کند، نورِ پشتش و لبهٔ روشنش است.
 *     پنجره‌ها ولی توپرند (`surfaceSolid`) — متنِ پنجره نباید روی
 *     محتوای پشتش بیفتد.
 *
 *   • **بنفش نشانِ برنامه است، نعنایی و صورتی و نارنجی معنا دارند.**
 *     نعنایی: دخل و موفقیت · قرمز: خرج و قرض · نارنجی: هشدار. رنگ
 *     هیچ‌جا تزیین نیست.
 *
 *   • **در هر صفحه فقط یک المانِ گرادینتی** — دکمهٔ اصلی یا FAB. دو
 *     گرادینت در یک صفحه یعنی هیچ‌کدام مهم نیست.
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
  /** لکه‌های نورِ زمینه — سه لکه، به ترتیبِ غلبه */
  val auroraOne: Color,
  val auroraTwo: Color,
  val auroraThree: Color,
  /**
   *  سطحِ **توپر** — برای پنجره‌ها، کشوها و منوها.
   *
   *  `surface` از این پس شیشه است: سفیدِ کم‌رنگی که زمینهٔ آرورا از پشتش
   *  پیداست. برای کارت درست است و برای پنجره فاجعه — متنِ پنجره روی
   *  محتوای پشتش می‌افتد و هیچ‌کدام خوانده نمی‌شود. پس هرجا که چیزی
   *  **روی** برنامه می‌نشیند، این یکی به کار می‌رود.
   */
  val surfaceSolid: Color,
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
 *  روز — نورِ آرورا روی زمینهٔ بسیار روشن، شیشهٔ سفید رویش.
 *
 *  زمینه عمداً سفیدِ خالص نیست: روی سفیدِ خالص، هم لکه‌های نور بی‌رمق
 *  می‌شوند و هم پنلِ سفیدِ نیمه‌شفاف اصلاً دیده نمی‌شود. یک بنفشِ خیلی
 *  کم‌رنگ، هر دو را حل می‌کند.
 */
val LightColors = ShopColors(
  //  زمینهٔ روز: سفیدِ خالص نه — یک بنفشِ بسیار کم‌رنگ، تا نورِ آرورا
  //  رویش دیده شود. سفیدِ خالص، شیشه را نامرئی می‌کند.
  bg = Color(0xFFF2F0FA),
  //  شیشه: سفیدِ نیمه‌شفاف. زیرش آروراست، پس پنل «شناور» دیده می‌شود
  //  شیشه‌ی مه‌آلود، نه شیشه‌ی ساده: آن‌قدر مات که محتوای پشتِ کارت از
  //  داخلش خوانده نشود، و آن‌قدر شفاف که رنگِ نورِ پشت‌زمینه از زیرش
  //  بزند. تارکردنِ واقعی روی این نسخه‌ی کامپوز نیست، پس ماتی جایش را
  //  می‌گیرد — کاری که چشم هم همان را «شیشه» می‌بیند.
  surface = Color(0xF2FFFFFF),
  surface2 = Color(0xFAFFFFFF),
  border = Color(0xE6FFFFFF),
  text = Color(0xFF191627),
  muted = Color(0xFF635C86),
  muted2 = Color(0xFF8A83AC),
  primary = Color(0xFF7C5CFF),
  primaryDark = Color(0xFF5B3FE0),
  primaryTint = Color(0x1F7C5CFF),
  success = Color(0xFF0FA98A),
  successTint = Color(0x1F0FA98A),
  warning = Color(0xFFB87708),
  warningTint = Color(0x1FF0A32B),
  danger = Color(0xFFD93B3B),
  dangerTint = Color(0x1FD93B3B),
  accent = Color(0xFF0FA98A),
  glow = Color(0x1F7C5CFF),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4D7C5CFF),
  auroraTwo = Color(0x3D00C39A),
  auroraThree = Color(0x33FF5FA2),
  surfaceSolid = Color(0xFFFBFAFF),
  fieldBg = Color(0xCCFFFFFF),
  fieldBorder = Color(0x1F191627),
  fieldFocus = Color(0xFF7C5CFF),
)

/**
 *  شب — بنفشِ تیره، نه سیاه.
 *
 *  سیاهِ خالص با لکه‌های نور، لکه‌دار و کثیف دیده می‌شود؛ یک بنفشِ بسیار
 *  تیره همان عمق را می‌دهد و نور رویش تمیز می‌نشیند.
 *
 *  درسی که از پالتِ قبلی نگه داشته شد: متن‌های فرعی نباید تیره باشند.
 *  هر سه پلهٔ متن آن‌قدر روشن‌اند که روی شیشهٔ کم‌رنگ هم خوانده شوند.
 */
val DarkColors = ShopColors(
  //  شبِ بنفش‌فام، نه سیاهِ خالص: سیاهِ خالص با نورِ آرورا لکه‌دار
  //  می‌شود و شیشه رویش کثیف به نظر می‌رسد
  bg = Color(0xFF0B0A14),
  //  در شب، سفیدِ کم‌رنگ روی زمینه‌ی تیره یعنی «هرچه پشتش هست پیداست».
  //  جایش یک بنفشِ شبِ مات می‌نشیند که هنوز از زمینه روشن‌تر است.
  surface = Color(0xF01A1730),
  surface2 = Color(0xF7231F3D),
  border = Color(0x2EFFFFFF),
  text = Color(0xFFF0EEF8),
  muted = Color(0xFFA9A3C4),
  muted2 = Color(0xFF8B84AB),
  primary = Color(0xFFA78BFF),
  primaryDark = Color(0xFF7C5CFF),
  primaryTint = Color(0x33A78BFF),
  success = Color(0xFF5CE0BC),
  successTint = Color(0x335CE0BC),
  warning = Color(0xFFF5B54A),
  warningTint = Color(0x33F5B54A),
  danger = Color(0xFFFF7A7A),
  dangerTint = Color(0x33FF7A7A),
  accent = Color(0xFF5CE0BC),
  glow = Color(0x4D7C5CFF),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C7C5CFF),
  auroraTwo = Color(0x6600C39A),
  auroraThree = Color(0x59FF5FA2),
  surfaceSolid = Color(0xFF15132A),
  fieldBg = Color(0x0FFFFFFF),
  fieldBorder = Color(0x38FFFFFF),
  fieldFocus = Color(0xFFA78BFF),
)

/**
 *  گِردیِ گوشه‌ها — بزرگ و نرم.
 *
 *  گوشهٔ تیز، عنصر را «جعبه» نشان می‌دهد. گِردیِ زیاد همان عنصر را روی
 *  زمینه شناور می‌کند، که همان حسی است که می‌خواهیم.
 */
/**
 *  گِردیِ اجزای آماده‌ی متریال.
 *
 *  تا حالا این را نمی‌دادیم و متریال پیش‌فرضِ خودش را می‌گذاشت: کادرهای
 *  متن از `extraSmall` رنگ می‌گیرند که **چهار نقطه** است — یعنی عملاً
 *  کنجِ تیز. همهٔ کارت‌ها و دکمه‌های خودمان گِرد بودند و وسطشان کادرهای
 *  متن چهارگوش می‌نشستند؛ در صفحهٔ «ثبت برد» و «محصول جدید» همین یک
 *  تفاوت، فرم را ناجور نشان می‌داد.
 *
 *  حالا یک جا داده می‌شود و همهٔ کادرهای متن، کشوها، منوها و کارت‌های
 *  متریال در تمامِ برنامه گِرد می‌شوند.
 */
val ShopShapes = Shapes(
  extraSmall = RoundedCornerShape(12.dp),
  small = RoundedCornerShape(16.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

/**
 *  گِردی، به تفکیکِ نقش.
 *
 *  پنل از دکمه گِردتر است و دکمه از کادرِ ورودی: هرچه چیزی بزرگ‌تر
 *  باشد، برای اینکه «شناور» به نظر برسد گِردیِ بیشتری لازم دارد. کادرِ
 *  ورودیِ خیلی گِرد، بی‌جا شلوغ می‌شود.
 */
object Radius {
  val sm = 16.dp
  val md = 20.dp
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
  val target = if (dark) DarkColors else LightColors
  //  عوض‌شدنِ روز و شب نباید مثل خاموش‌وروشنِ چراغ باشد: سه رنگِ اصلی
  //  نرم جابه‌جا می‌شوند و بقیه دنبالشان می‌آیند
  val bg by animateColorAsState(target.bg, Springs.effect, label = "themeBg")
  val surface by animateColorAsState(target.surface, Springs.effect, label = "themeSurface")
  val text by animateColorAsState(target.text, Springs.effect, label = "themeText")
  val colors = target.copy(bg = bg, surface = surface, text = text)
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
      //  پنجره و کشو و منو، سطحِ **توپر** می‌گیرند نه شیشه: متنِ پنجره
      //  نباید روی محتوای پشتش بیفتد
      surface = colors.surfaceSolid,
      onSurface = colors.text,
      surfaceVariant = colors.surfaceSolid,
      onSurfaceVariant = colors.muted,
      surfaceContainer = colors.surfaceSolid,
      surfaceContainerHigh = colors.surfaceSolid,
      surfaceContainerHighest = colors.surfaceSolid,
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
      //  پنجره و کشو و منو، سطحِ **توپر** می‌گیرند نه شیشه: متنِ پنجره
      //  نباید روی محتوای پشتش بیفتد
      surface = colors.surfaceSolid,
      onSurface = colors.text,
      surfaceVariant = colors.surfaceSolid,
      onSurfaceVariant = colors.muted,
      surfaceContainer = colors.surfaceSolid,
      surfaceContainerHigh = colors.surfaceSolid,
      surfaceContainerHighest = colors.surfaceSolid,
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
    MaterialTheme(colorScheme = scheme, typography = type, shapes = ShopShapes, content = content)
  }
}
