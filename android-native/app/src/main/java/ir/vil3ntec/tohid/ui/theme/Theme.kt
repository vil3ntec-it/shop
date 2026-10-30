package ir.vil3ntec.tohid.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.screens.Springs
import androidx.compose.ui.unit.dp

/*
 *  رنگ‌ها — THEME-V2 (دستورِ صاحب‌کار، ۱۴۰۵/۰۸/۰۲؛ جای سبزِ VILL3N ِ ۱۴۰۵/۰۷/۲۸).
 *
 *  پایه آبی و سفید، سه تم («آسمان» پیش‌فرض، «اقیانوس»، «غروب») × روز و شب در `ShopThemes.kt`
 *  (`shopColors(theme, dark)`، `ThemeStore`). زمینه، کارت و سطحِ درونی سه رنگِ جدا با خطِ دورِ دیدنی در روز و
 *  شب دارند. هر رنگ یک **نامِ معنایی** دارد (`background`، `surface`، `textPrimary`،
 *  `brandPrimary`، `status.delivered`…) و صفحه‌ها فقط همین نام‌ها را می‌خوانند —
 *  عوض‌شدنِ روز و شب بی بازنویسیِ صفحه (`HardcodedColorsTest`).
 *
 *  ── سه قاعده ──────────────────────────────────────────────────────
 *   • **زمینه ≠ کارت ≠ سطحِ درونی.** سطحِ ۰ زمینهٔ صفحه، ۱ کارت و پنل، ۲ بخشِ
 *     درونی. کارتِ سفید روی زمینهٔ روشن همیشه خطِ دورِ `borderCard` دارد؛ سایه
 *     کم است و جای خط را نمی‌گیرد.
 *   • **رنگ معنا دارد.** سبز: تحویل و موفق · آبی: در راه و اطلاع · کهربایی: در
 *     انتظار و هشدار · قرمز: لغو و خطا (و فقط هشدارِ واقعی) · بنفش: نیازِ بررسی ·
 *     فیروزه‌ای: در آماده‌سازی · خاکستری: غیرفعال. هرگز رنگِ تنها؛ همیشه با متن.
 *   • **متنِ کوچک ≥ ۴٫۵:۱ (WCAG AA).** primaryِ تم برای پُرکردن، آیکن، نشانگر و عددِ درشت
 *     است؛ متنِ کوچک و دکمهٔ پُر از `brandDeep` (دکمهٔ اصلیِ تم، مثلاً «آسمان» #1D4ED8) می‌آیند.
 *     جدولِ کاملِ کنتراستِ هر شش پالت در `Vill3nPaletteTest`، عددهای تم در `ThemeV2PaletteTest`.
 *
 *  نام‌های قدیمی (`bg`، `text`، `muted`، `primary`…) هنوز هستند تا صدها صفحهٔ
 *  موجود بی دست‌خوردن رنگِ تازه بگیرند؛ هر کدام فقط **نامِ دیگرِ** یک توکنِ
 *  معنایی است (پایینِ `ShopColors`) و مقدارِ جدا ندارد.
 */

/** جفتِ دکمه: زمینه، متن و خطِ دور (VILL3N §۷) */
@Immutable
data class ButtonTone(val bg: Color, val text: Color, val border: Color)

/** دکمه‌ها — اصلی، دوم، خنثی، خطر و غیرفعال */
@Immutable
data class ButtonColorSet(
  val primary: ButtonTone,
  val secondary: ButtonTone,
  val neutral: ButtonTone,
  val danger: ButtonTone,
  val disabled: ButtonTone,
)

/** نشانِ وضعیت: متن/نقطه روی زمینهٔ کم‌رنگِ همان رنگ */
@Immutable
data class StatusTone(val fg: Color, val bg: Color)

/**
 *  وضعیت‌ها — **یک معنا در همه‌جا** (مشتری، دکان، پیک، مدیر؛ VILL3N §۸، THEME-V2 «رنگ‌های معنایی»).
 *  نگاشتِ وضعیتِ سفارش در `ui/components/common/OrderStatusTone.kt` است. «در انتظارِ تأیید» و
 *  «نیازِ بررسی» هر دو بنفش‌اند؛ هشدار (کم‌موجود، سقف، اختلاف، در صف) کهربایی.
 */
@Immutable
data class StatusColors(
  /** تحویل‌شده / موفق */
  val delivered: StatusTone,
  /** در مسیر */
  val inDelivery: StatusTone,
  /** در انتظارِ تأیید */
  val awaitingApproval: StatusTone,
  /** لغو و رد */
  val cancelled: StatusTone,
  /** نیاز به بررسی */
  val needsReview: StatusTone,
  /** در آماده‌سازی */
  val preparing: StatusTone,
  /** غیرفعال / بسته‌شده */
  val inactive: StatusTone,
  /** هشدار: کم‌موجود، نزدیکِ انقضا، سقف، اختلاف، در صف (کهربایی) */
  val warning: StatusTone,
) {
  val all: List<Pair<String, StatusTone>>
    get() = listOf(
      "delivered" to delivered, "inDelivery" to inDelivery, "awaitingApproval" to awaitingApproval,
      "cancelled" to cancelled, "needsReview" to needsReview, "preparing" to preparing, "inactive" to inactive,
      "warning" to warning,
    )
}

/** پیام (نوار، کادرِ هشدار، اعلان): متن، زمینه، آیکن و خطِ دور */
@Immutable
data class MessageTone(val text: Color, val bg: Color, val icon: Color, val border: Color)

@Immutable
data class MessageColors(
  val success: MessageTone,
  val info: MessageTone,
  val warning: MessageTone,
  val error: MessageTone,
) {
  val all: List<Pair<String, MessageTone>>
    get() = listOf("success" to success, "info" to info, "warning" to warning, "error" to error)
}

/**
 *  نمودار — ترتیبِ سری‌ها ثابت است (VILL3N §۱۰): ۱ فروش، ۲ مقایسه/روند، ۳ هزینه،
 *  ۴ تخفیف/هدف، ۵ افتِ مهم/خطا، ۶ موجودی/تکمیلی، ۷ خنثی. قرمز برای دادهٔ عادی نه.
 *  همیشه با نامِ سری یا برچسبِ عدد (نارنجی، فیروزه‌ای و خاکستری روی سفید زیرِ ۳:۱اند).
 */
@Immutable
data class ChartColors(
  val sales: Color,
  val comparison: Color,
  val costs: Color,
  val discount: Color,
  val drop: Color,
  val stock: Color,
  val neutral: Color,
  val grid: Color,
) {
  /** سری‌ها به ترتیبِ ۱ تا ۷ — برای دسته‌های نمودارِ دایره‌ای و ستونیِ چندتایی */
  val series: List<Color> get() = listOf(sales, comparison, costs, discount, drop, stock, neutral)
}

@Immutable
data class ShopColors(
  //  ── سطح‌ها (VILL3N §۴–۶) ──
  /** سطحِ ۰ — زمینهٔ صفحه */
  val background: Color,
  /** زمینهٔ فرعی: منوی کناری، شیارِ کنترلِ قطعه‌ای، نوارِ وضعیت */
  val backgroundSubtle: Color,
  /** سطحِ ۱ — کارت و پنل */
  val surface: Color,
  /** سطحِ ۲ — بخشِ درونیِ کارت، ردیفِ یک‌درمیان، جای عکس */
  val surfaceElevated: Color,
  /** ردیف/گزینه/منوی انتخاب‌شده */
  val surfaceSelected: Color,
  //  ── متن و آیکن ──
  val textPrimary: Color,
  val textSecondary: Color,
  /** کم‌رنگ‌ترین: راهنمای داخلِ کادر، غیرفعال، متنِ درشت. روی سفید ۳٫۶:۱ ⇒ نه برای متنِ کوچکِ مهم */
  val textMuted: Color,
  val icon: Color,
  val iconActive: Color,
  //  ── خط‌ها ──
  val borderCard: Color,
  val borderInput: Color,
  val divider: Color,
  //  ── برند ──
  /** سبزآبیِ برند — پُرکردن، نشانگر، آیکنِ فعال، سریِ ۱ِ نمودار، عددِ درشت */
  val brandPrimary: Color,
  /** سبزآبیِ پررنگ — تأکید، کارتِ پُرِ فروش، فشرده */
  val brandDeep: Color,
  /** برند برای **متنِ کوچک** و پیوندِ کار روی سطح‌ها (≥ ۴٫۵:۱) */
  val brandText: Color,
  /** پیوند و اطلاع (آبی) */
  val link: Color,
  /** حلقهٔ تمرکز و خطِ گزینهٔ فعال */
  val focus: Color,
  //  ── سطحِ وارونه (نوارِ سبد، شیتِ تیره) ──
  val inverse: Color,
  val inverseRaised: Color,
  val onInverse: Color,
  val onInverseMuted: Color,
  //  ── گروه‌ها ──
  val button: ButtonColorSet,
  val status: StatusColors,
  val message: MessageColors,
  val chart: ChartColors,
  //  ── سایه و تزیین ──
  /** رنگِ سایهٔ سبکِ کارت (`softCard`) — Compose آن را در آلفای سایهٔ سامانه ضرب می‌کند */
  val shadow: Color,
  val scrim: Color,
  /** هالهٔ ملایمِ زیرِ دکمهٔ اصلی */
  val glow: Color,
  /** لبهٔ روشنِ بالای سطحِ شیشه‌ای — فقط صفحهٔ آغاز و ورود */
  val sheen: Color,
  /** لکه‌های نورِ زمینه — فقط صفحهٔ آغاز و ورود (`decorative = true`) */
  val auroraOne: Color,
  val auroraTwo: Color,
  val auroraThree: Color,
  //  ── THEME-V2 ──
  /** گرادیانِ کادرِ بزرگ (۱۳۵°، سه رنگ) — متن رویش همیشه سفید */
  val heroGradient: List<Color> = listOf(brandPrimary, brandDeep),
  /** هفت رنگِ همراه (آیکونِ بخش‌ها، کاشی‌ها، سری‌های نمودار) — در هر سه تم یکی */
  val accents: List<Color> = ThemeAccents.ALL,
  /** پردهٔ سیاهِ خوانایی روی کادرِ بزرگ (۰ تا این عدد در جهتِ گرادیان) تا متنِ سفید AA بماند */
  val heroScrim: Float = 0f,
  /** رنگِ برند روی سطحِ وارونه (کارِ اسنک‌بار، `inversePrimary`ِ متریالِ روز): primaryِ تیرهٔ همان تم */
  val brandOnInverse: Color = brandPrimary,
  /** سایهٔ سیاهِ زیرِ شیشهٔ سفیدِ تراشه‌های کادرِ بزرگ — برچسبِ ۱۲sp همه‌جای گرادیان ≥ ۴٫۵:۱ */
  val heroChipShade: Float = 0f,
) {
  /** رنگِ همراهِ i (چرخشی) */
  fun accent(i: Int): Color = accents[i.mod(accents.size)]

  /** پس‌زمینهٔ نرمِ همان رنگ: ۱۲٪ در روشن، ۲۰٪ در تیره */
  fun accentSoft(i: Int): Color = accent(i).copy(alpha = if (isDark) 0.20f else 0.12f)

  /** شب؟ — برای پالت‌هایی که به روز/شب جدا نیاز دارند */
  val isDark: Boolean get() = background.red + background.green + background.blue < 1.5f

  /*
   *  ── نام‌های قدیمی ⇐ توکنِ معنایی ──────────────────────────────────
   *  صفحه‌های موجود این نام‌ها را می‌خوانند. هیچ‌کدام مقدارِ خودش را ندارد.
   */
  val bg: Color get() = background
  val surface2: Color get() = surfaceElevated
  /** پنجره، منو، کشو — توپر (`surface` هم دیگر شیشه نیست) */
  val surfaceSolid: Color get() = surface
  val border: Color get() = borderCard
  val text: Color get() = textPrimary
  val muted: Color get() = textSecondary
  /**
   *  متنِ فرعیِ سوم. `textMuted`ِ VILL3N در روز روی سفید ۳٫۶:۱ است و این نام در
   *  صفحه‌ها برای متنِ کوچک (تاریخ، واحد، زیرنویس) به کار رفته ⇒ همان `textSecondary`.
   */
  val muted2: Color get() = textSecondary
  /** رنگِ «اصلیِ» صفحه‌ها بیشتر متن و پیوند است ⇒ برندِ خوانا، نه `brandPrimary` */
  val primary: Color get() = brandText
  val primaryDark: Color get() = brandText
  val primaryTint: Color get() = surfaceSelected
  val accent: Color get() = iconActive
  val success: Color get() = status.delivered.fg
  val successTint: Color get() = status.delivered.bg
  val warning: Color get() = status.warning.fg
  val warningTint: Color get() = status.warning.bg
  val danger: Color get() = status.cancelled.fg
  val dangerTint: Color get() = status.cancelled.bg
  val info: Color get() = status.inDelivery.fg
  val infoTint: Color get() = status.inDelivery.bg
  val fieldBg: Color get() = surface
  val fieldBorder: Color get() = borderInput
  val fieldFocus: Color get() = focus
  /** ناحیهٔ منو (کامپیوتر): زمینهٔ فرعی، جدا از محتوا */
  val zoneNav: Color get() = backgroundSubtle
  /** ناحیهٔ سبد: سطحِ درونی — جدا از کاتالوگ (زمینه) و از کارت‌ها */
  val zoneCart: Color get() = surfaceElevated
}

/*
 *  مقدارهای VILL3N — عینِ فایلِ صاحب محصول (`COLOR-SYSTEM.md`). هر جا عدد عوض شده،
 *  دلیلش کنارش نوشته و در `Vill3nPaletteTest` ثبت است.
 */

/**
 *  رنگ‌های ثابتِ برند برای چند صفحهٔ قدیمی که هنوز از `Shop.colors` نمی‌خوانند (خوش‌آمد، نوارِ بالا، VIP) —
 *  از THEME-V2 همان «آسمان» (پیش‌فرض)؛ جای سبزآبیِ VILL3N. صفحه‌های تازه از `Shop.colors` می‌خوانند.
 */
object Brand {
  val primary = Color(0xFF2563EB)
  val deep = Color(0xFF1D4ED8)
  val successSurface = Color(0xFFDDF5EF)
  val blue = Color(0xFF2563EB)
  val blueSurface = Color(0xFFDBEAFE)
  val teal = Color(0xFF14B8A6)
  val orange = Color(0xFFF59E0B)
  val purple = Color(0xFF8B5CF6)
  val red = Color(0xFFEF4444)
  val neutral = Color(0xFF94A3B8)
}

/** روزِ پیش‌فرض = «آسمان»ِ روشن (THEME-V2، ۱۴۰۵/۰۸/۰۲؛ جای سبزِ VILL3N). سه تم در `ShopThemes.kt`. */
val LightColors: ShopColors = SkyLight

/** شبِ پیش‌فرض = «آسمان»ِ تیره */
val DarkColors: ShopColors = SkyDark

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
  //  VILL3N: کادرِ ورودی و دکمه ۱۰، کارت ۱۲، کارتِ ویژه و پنجره ۱۶.
  //  عددها فقط در `Corner` (Tokens.kt).
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

/**
 *  طرحِ رنگِ متریال از توکن‌های VILL3N.
 *
 *  `primary`ِ متریال هم پُرِ `Button` است و هم متنِ `TextButton`/`OutlinedButton`
 *  و برچسبِ کادرِ متنِ فعال (بیش از ۲۰۰ جا). پس باید هم با متنِ رویش و هم به‌عنوانِ
 *  متن روی سطح ≥ ۴٫۵:۱ باشد ⇒ همان دکمهٔ اصلیِ تم (مثلاً «آسمان»: روز #1D4ED8، شب #60A5FA).
 *  کلید، نشانگر و حلقهٔ تمرکز از همین می‌خوانند.
 */
fun materialScheme(c: ShopColors) = if (c.isDark) {
  darkColorScheme(
    primary = c.button.primary.bg,
    onPrimary = c.button.primary.text,
    primaryContainer = c.surfaceSelected,
    onPrimaryContainer = c.brandText,
    inversePrimary = c.brandDeep,
    secondary = c.brandText,
    onSecondary = c.button.primary.text,
    secondaryContainer = c.surfaceSelected,
    onSecondaryContainer = c.brandText,
    tertiary = c.link,
    onTertiary = c.inverse,
    background = c.background,
    onBackground = c.textPrimary,
    //  پنجره، کشو و منو سطحِ توپرِ کارت را می‌گیرند
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceElevated,
    onSurfaceVariant = c.textSecondary,
    surfaceTint = c.surface,
    surfaceContainerLowest = c.surface,
    surfaceContainerLow = c.surface,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface,
    surfaceContainerHighest = c.surface,
    inverseSurface = c.textPrimary,
    inverseOnSurface = c.background,
    //  خطِ دورِ کادرِ متن، تراشه و دکمهٔ خطی؛ جداکننده از `outlineVariant`
    outline = c.borderInput,
    outlineVariant = c.divider,
    error = c.message.error.icon,
    onError = Color(0xFF2A0B09),
    errorContainer = c.message.error.bg,
    onErrorContainer = c.message.error.text,
    scrim = c.scrim,
  )
} else {
  lightColorScheme(
    primary = c.button.primary.bg,
    onPrimary = c.button.primary.text,
    primaryContainer = c.surfaceSelected,
    onPrimaryContainer = c.brandText,
    inversePrimary = c.brandOnInverse,
    secondary = c.brandText,
    onSecondary = c.button.primary.text,
    secondaryContainer = c.surfaceSelected,
    onSecondaryContainer = c.brandText,
    tertiary = c.link,
    onTertiary = Color.White,
    background = c.background,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceElevated,
    onSurfaceVariant = c.textSecondary,
    surfaceTint = c.surface,
    surfaceContainerLowest = c.surface,
    surfaceContainerLow = c.surface,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface,
    surfaceContainerHighest = c.surface,
    inverseSurface = c.inverse,
    inverseOnSurface = c.onInverse,
    outline = c.borderInput,
    outlineVariant = c.divider,
    //  دکمهٔ خطر و متنِ خطا: #DC2626 روی سفید ۴٫۸:۱ و سفید رویش ۴٫۸:۱
    error = c.button.danger.bg,
    onError = c.button.danger.text,
    errorContainer = c.message.error.bg,
    onErrorContainer = c.message.error.text,
    scrim = c.scrim,
  )
}

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
  //  تمِ همین دستگاه (`ThemeStore`، پیش‌فرض «آسمان») × روز/شب
  val target = shopColors(ThemeStore.theme, dark)
  //  عوض‌شدنِ روز/شب یا تم مثلِ خاموش‌وروشنِ چراغ نیست: رنگ‌های اصلی در ۲۵۰ms نرم جابه‌جا می‌شوند
  //  و بقیه دنبالشان می‌آیند (THEME-V2)
  val spec = remember { androidx.compose.animation.core.tween<Color>(250) }
  val bg by animateColorAsState(target.background, spec, label = "themeBg")
  val surface by animateColorAsState(target.surface, spec, label = "themeSurface")
  val surface2 by animateColorAsState(target.surfaceElevated, spec, label = "themeSurface2")
  val text by animateColorAsState(target.textPrimary, spec, label = "themeText")
  val brand by animateColorAsState(target.brandPrimary, spec, label = "themeBrand")
  val brandText by animateColorAsState(target.brandText, spec, label = "themeBrandText")
  val selected by animateColorAsState(target.surfaceSelected, spec, label = "themeSelected")
  val button by animateColorAsState(target.button.primary.bg, spec, label = "themeButton")
  val hero0 by animateColorAsState(target.heroGradient[0], spec, label = "themeHero0")
  val hero1 by animateColorAsState(target.heroGradient[1], spec, label = "themeHero1")
  val hero2 by animateColorAsState(target.heroGradient.last(), spec, label = "themeHero2")
  val colors = target.copy(
    background = bg, surface = surface, surfaceElevated = surface2, textPrimary = text,
    brandPrimary = brand, iconActive = brand, focus = brand, brandText = brandText, surfaceSelected = selected,
    button = target.button.copy(primary = target.button.primary.copy(bg = button, border = button)),
    heroGradient = listOf(hero0, hero1, hero2),
  )
  /*
   *  اجزای آمادهٔ متریال — تراشه، دکمه، کادرِ متن — رنگشان را از همین
   *  طرح می‌گیرند. تا وقتی این‌ها را ننویسیم، متریال رنگ‌های پیش‌فرضِ
   *  خودش را می‌گذارد و خطِ خاکستری دورِ تراشه‌ها می‌کشد. طرح از `target`
   *  ساخته می‌شود (نه رنگِ در حالِ جابه‌جایی) تا `isDark` وسطِ جابه‌جایی نلرزد.
   */
  val scheme = remember(target) { materialScheme(target) }
    .copy(background = colors.background, surface = colors.surface, onSurface = colors.textPrimary, onBackground = colors.textPrimary)

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
    //  انگلیسی همیشه رقمِ لاتین؛ فارسی و پشتو همان انتخابِ دکان
    val digits = ir.vil3ntec.tohid.LocaleChoice.screenDigits
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
