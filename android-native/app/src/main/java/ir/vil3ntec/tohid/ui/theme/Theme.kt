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
 *  ⚠️ از ۱۴۰۵/۰۷/۲۵ جایش را «Professional Retail OS» گرفته
 *  (`docs/VISUAL-DIRECTION-fa.md` §۱): صفحه‌های کار سطحِ توپر و تمیز دارند
 *  و شیشه/آرورا فقط برای صفحهٔ آغاز و ورود می‌ماند (`decorative = true`).
 *  شرحِ پایین تاریخچهٔ همان تصمیمِ پیشین است.
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
  /** نسخهٔ ۲: رنگِ اطلاع (آبی) برای نشانِ «متن + رنگ + نقطه» — کنارِ موفق/هشدار/خطر */
  val info: Color,
  val infoTint: Color,
  /*
   *  ناحیه‌ها — نسخهٔ ۲ (VISUAL-DIRECTION «نسخهٔ ۲»).
   *
   *  صاحب محصول: «همهٔ جعبه‌ها یک رنگ‌اند، هیچ چیز از هم جدا نیست». پس هر
   *  ناحیه رنگِ زمینهٔ خودش را دارد تا با یک نگاه شناخته شود: منو (`zoneNav`،
   *  خاکستری-آبیِ سرد)، کاتالوگ (همان `bg`، خنثی) و سبد (`zoneCart`، تهِ رنگِ
   *  اصلی). کارت‌ها روی همهٔ این‌ها سفید (`surface`) با سایهٔ نرم‌اند.
   */
  val zoneNav: Color,
  val zoneCart: Color,
  /** رنگِ سایهٔ نرمِ کارت‌ها (`softCard`) — به‌جای خطِ دورِ تیره */
  val shadow: Color,
)

/**
 *  روز — «Professional Retail OS» (VISUAL-DIRECTION §۱، ۱۴۰۵/۰۷/۲۵).
 *
 *  صفحه‌های کار سطحِ تمیز و روشن دارند: زمینهٔ خنثیِ ملایم، کارتِ سفیدِ
 *  توپر، خطِ دورِ نازکِ تیره (همان `--border`ِ وب) و سایهٔ بسیار کم. شیشه و
 *  آرورا دیگر پشتِ صفحه‌های کار نیستند (`AuroraBackground`/`glassSurface`).
 *  پیش از این: زمینهٔ بنفشِ کم‌رنگ #F2F0FA، سطحِ نیمه‌شفافِ ۹۵٪ و لبهٔ
 *  سفیدِ ۹۰٪ِ شیشه.
 */
val LightColors = ShopColors(
  /*
   *  نسخهٔ ۲ (۱۴۰۵/۰۷/۲۷): زمینهٔ خنثیِ آرام، کارتِ سفید با سایهٔ نرم، یک رنگِ
   *  اصلیِ نیلی-بنفش. خطِ دورِ کارت فقط مویی است (۷٪) — نسخهٔ ۱ با خطِ ۱۰٪ و
   *  گوشهٔ ۸، «همه‌جا خطِ تیره» دیده می‌شد. همهٔ جفت‌ها ≥ ۴٫۵:۱
   *  (`ThemeContrastTest`، `BackOfficeContrastTest`). وب همین عددها (`WebPaletteParityTest`).
   */
  bg = Color(0xFFF4F5F8),
  surface = Color(0xFFFFFFFF),
  //  پرکنِ ملایمِ درونِ کارت: ردیفِ یک‌درمیان، جای عکس، کادرِ جست‌وجو
  surface2 = Color(0xFFF6F7F9),
  border = Color(0x12101828),
  text = Color(0xFF171A26),
  muted = Color(0xFF5A6072),
  muted2 = Color(0xFF575D6F),
  primary = Color(0xFF5B45E0),
  primaryDark = Color(0xFF4532C2),
  primaryTint = Color(0x1A5B45E0),
  success = Color(0xFF076A4F),
  successTint = Color(0x1F10B981),
  warning = Color(0xFF845200),
  warningTint = Color(0x24F59E0B),
  danger = Color(0xFFB3203A),
  dangerTint = Color(0x1AE11D48),
  accent = Color(0xFF0FA98A),
  glow = Color(0x1F5B45E0),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4D7C5CFF),
  auroraTwo = Color(0x3D00C39A),
  auroraThree = Color(0x33FF5FA2),
  surfaceSolid = Color(0xFFFFFFFF),
  fieldBg = Color(0xFFFFFFFF),
  //  کادرِ ورودی باید پیدا باشد، ولی نرم: ۱۴٪ (نه خطِ تیره)
  fieldBorder = Color(0x24101828),
  fieldFocus = Color(0xFF5B45E0),
  info = Color(0xFF1A55C4),
  infoTint = Color(0x1A2563EB),
  zoneNav = Color(0xFFE9ECF3),
  zoneCart = Color(0xFFEFECFD),
  shadow = Color(0xB3101828),
)

/**
 *  شب — نسخهٔ ۲: خاکستریِ تیرهٔ سرد، نه سیاه و نه بنفش.
 *
 *  کارت فقط یک پله از زمینه روشن‌تر است و یک لبهٔ مویی (۷٪ سفید) دارد؛ سایه
 *  در شب دیده نمی‌شود، پس جدایی از همین اختلافِ کم می‌آید. منو تیره‌تر از
 *  زمینه است و سبد تهِ بنفش دارد — همان سه ناحیهٔ روز.
 *
 *  درسی که از پالتِ قبلی نگه داشته شد: متن‌های فرعی نباید تیره باشند.
 */
val DarkColors = ShopColors(
  bg = Color(0xFF0F1117),
  surface = Color(0xFF181B23),
  surface2 = Color(0xFF1F232D),
  border = Color(0x12FFFFFF),
  text = Color(0xFFECEEF4),
  muted = Color(0xFFA3A9B9),
  muted2 = Color(0xFF959CAD),
  primary = Color(0xFFA594FF),
  //  «پررنگ‌ترِ» رنگِ اصلی در شب روشن‌تر است: متن روی تینت
  primaryDark = Color(0xFFC3B8FF),
  primaryTint = Color(0x29A594FF),
  success = Color(0xFF4FD1A5),
  successTint = Color(0x244FD1A5),
  warning = Color(0xFFF2B45A),
  warningTint = Color(0x24F2B45A),
  danger = Color(0xFFFF8F8F),
  dangerTint = Color(0x24FF8F8F),
  accent = Color(0xFF4FD1A5),
  glow = Color(0x4D7C5CFF),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C7C5CFF),
  auroraTwo = Color(0x6100C39A),
  auroraThree = Color(0x57FF5FA2),
  surfaceSolid = Color(0xFF1C1F28),
  fieldBg = Color(0xFF1F232D),
  fieldBorder = Color(0x24FFFFFF),
  fieldFocus = Color(0xFFA594FF),
  info = Color(0xFF86B4FF),
  infoTint = Color(0x2486B4FF),
  zoneNav = Color(0xFF0A0C11),
  zoneCart = Color(0xFF1A1830),
  shadow = Color(0xFF000000),
)

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
  //  نسخهٔ ۲: یک پلهٔ گِردی برای همهٔ سکوها — کادرِ ورودی ۱۲، کارت و منو ۱۶،
  //  پنجره ۲۰. عددها فقط در `Corner` (Tokens.kt).
  extraSmall = RoundedCornerShape(Corner.extraSmall.current),
  //  تراشه‌های متریال (FilterChip، AssistChip…) از `small` می‌خوانند ⇒ قرصیِ کامل
  small = Shape.chip,
  medium = RoundedCornerShape(Corner.medium.current),
  large = RoundedCornerShape(Corner.large.current),
  extraLarge = RoundedCornerShape(Corner.extraLarge.current),
)

/**
 *  گِردی، به تفکیکِ نقش.
 *
 *  پنل از دکمه گِردتر است و دکمه از کادرِ ورودی: هرچه چیزی بزرگ‌تر
 *  باشد، برای اینکه «شناور» به نظر برسد گِردیِ بیشتری لازم دارد. کادرِ
 *  ورودیِ خیلی گِرد، بی‌جا شلوغ می‌شود.
 */
object Radius {
  val sm = Corner.panelSm.current
  val md = Corner.panelMd.current
  val lg = Corner.panelLg.current
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
    /*
     *  کامپیوتر بزرگ‌نمایی ندارد، کوچک‌نمایی دارد.
     *
     *  منطقِ تبلت (صفحهٔ بزرگ‌تر ⇒ متنِ بزرگ‌تر) روی مانیتور برعکس کار
     *  می‌کرد: پهنای ۹۰۰ به بالا متن را ۲۲٪ درشت می‌کرد و هر صفحه شبیهِ
     *  گوشیِ بزرگ‌شده می‌شد. روی کامپیوتر چشم نزدیک‌تر و ماوس دقیق است؛
     *  متن اندازهٔ نرم‌افزارهای رومیزی است و بزرگ‌نماییِ Ctrl+/− دستِ کاربر.
     */
    val scale = when {
      ir.vil3ntec.tohid.ui.Platform.DESKTOP -> 0.93f
      width >= 900 -> 1.22f
      width >= 600 -> 1.12f
      else -> 1f
    }
    //  رقمِ صفحه، انتخابِ دکان (LOC-05) — با زدنِ کلید کلِ برنامه عوض می‌شود
    val digits = ir.vil3ntec.tohid.LocaleChoice.digits
    val type = remember(scale, digits) { shopTypography(scale, digits) }
    MaterialTheme(colorScheme = scheme, typography = type, shapes = ShopShapes) {
      if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
        //  دکمه و کادرِ ماوسی لازم نیست ۴۸ پیکسل باشد (اندازهٔ انگشت)
        CompositionLocalProvider(
          androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 34.dp,
          content = content,
        )
      } else content()
    }
  }
}
