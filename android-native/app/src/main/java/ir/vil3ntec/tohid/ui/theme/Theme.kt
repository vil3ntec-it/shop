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
 *  رنگ‌ها — سیستمِ رنگِ VILL3N (دستورِ صاحب محصول، ۱۴۰۵/۰۷/۲۸).
 *
 *  «رنگ‌بندیِ برنامه رو این جوری کن همه جاشو». سبزآبیِ برند (#108B81) جای بنفشِ
 *  پیشین را گرفت؛ زمینه، کارت و سطحِ درونی سه رنگِ جدا با خطِ دورِ دیدنی در روز و
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
 *   • **متنِ کوچک ≥ ۴٫۵:۱ (WCAG AA).** سبزآبیِ برند روی سفید ۴٫۱۷:۱ است؛ پس برای
 *     پُرکردن، آیکن، نشانگر و عددِ درشت می‌ماند و متنِ کوچک و دکمهٔ پُر از
 *     `brandDeep` (#08665F، ۶٫۸:۱) می‌آیند. جدولِ کاملِ کنتراست در `Vill3nPaletteTest`.
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
 *  هفت وضعیتِ سفارش — **یک معنا در همه‌جا** (مشتری، دکان، پیک، مدیر؛ VILL3N §۸).
 *  نگاشتِ وضعیت به این هفت در `ui/components/common/OrderStatusTone.kt` است.
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
) {
  val all: List<Pair<String, StatusTone>>
    get() = listOf(
      "delivered" to delivered, "inDelivery" to inDelivery, "awaitingApproval" to awaitingApproval,
      "cancelled" to cancelled, "needsReview" to needsReview, "preparing" to preparing, "inactive" to inactive,
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
) {
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
  val warning: Color get() = status.awaitingApproval.fg
  val warningTint: Color get() = status.awaitingApproval.bg
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

/** سبزآبیِ برند و رنگ‌های پشتیبان (VILL3N §۳) — همان عدد در روز و شب */
object Brand {
  val primary = Color(0xFF108B81)
  val deep = Color(0xFF08665F)
  val successSurface = Color(0xFFDDF5EF)
  val blue = Color(0xFF2563EB)
  val blueSurface = Color(0xFFDBEAFE)
  val teal = Color(0xFF14B8A6)
  val orange = Color(0xFFF59E0B)
  val purple = Color(0xFF8B5CF6)
  val red = Color(0xFFEF4444)
  val neutral = Color(0xFF94A3B8)
}

val LightColors = ShopColors(
  background = Color(0xFFF3F6FA),
  backgroundSubtle = Color(0xFFEAF0F6),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFF8FAFC),
  surfaceSelected = Color(0xFFE0F2EF),
  textPrimary = Color(0xFF17212B),
  textSecondary = Color(0xFF5B687A),
  textMuted = Color(0xFF7B8798),
  icon = Color(0xFF64748B),
  iconActive = Brand.primary,
  borderCard = Color(0xFFD5DEE9),
  borderInput = Color(0xFFC5D0DE),
  divider = Color(0xFFE2E8F0),
  brandPrimary = Brand.primary,
  brandDeep = Brand.deep,
  //  #108B81 روی سفید ۴٫۱۷:۱ ⇒ متنِ کوچک با سبزآبیِ پررنگ (۶٫۸:۱)
  brandText = Brand.deep,
  link = Brand.blue,
  focus = Brand.primary,
  inverse = Color(0xFF0B1220),
  inverseRaised = Color(0xFF162235),
  onInverse = Color(0xFFF1F5F9),
  onInverseMuted = Color(0xFFB6C2D2),
  button = ButtonColorSet(
    /*
     *  ⚠️ انحراف از فایل: صاحب محصول زمینهٔ #108B81 با متنِ سفید خواسته؛ آن جفت
     *  ۴٫۱۷:۱ است و برچسبِ ۱۳–۱۴ نقطه‌ایِ دکمه زیرِ ۴٫۵:۱ِ AA می‌ماند. پس دکمهٔ پُر
     *  `brandDeep` است (۶٫۸:۱). برگرداندن = همین یک خط.
     */
    primary = ButtonTone(bg = Brand.deep, text = Color(0xFFFFFFFF), border = Brand.deep),
    secondary = ButtonTone(bg = Color(0xFFFFFFFF), text = Brand.deep, border = Color(0xFFB8DCD5)),
    neutral = ButtonTone(bg = Color(0xFFE8EDF3), text = Color(0xFF344256), border = Color(0xFFE8EDF3)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFFE2E8F0), text = Color(0xFF94A3B8), border = Color(0xFFE2E8F0)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF15803D), bg = Color(0xFFDCFCE7)),
    inDelivery = StatusTone(fg = Color(0xFF1D4ED8), bg = Color(0xFFDBEAFE)),
    awaitingApproval = StatusTone(fg = Color(0xFF92400E), bg = Color(0xFFFEF3C7)),
    cancelled = StatusTone(fg = Color(0xFFB91C1C), bg = Color(0xFFFEE2E2)),
    needsReview = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    preparing = StatusTone(fg = Color(0xFF0E7490), bg = Color(0xFFCFFAFE)),
    inactive = StatusTone(fg = Color(0xFF475569), bg = Color(0xFFE2E8F0)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF166534), bg = Color(0xFFF0FDF4), icon = Color(0xFF16A34A), border = Color(0xFFBBF7D0)),
    info = MessageTone(text = Color(0xFF1E40AF), bg = Color(0xFFEFF6FF), icon = Brand.blue, border = Color(0xFFBFDBFE)),
    warning = MessageTone(text = Color(0xFF92400E), bg = Color(0xFFFFFBEB), icon = Color(0xFFB45309), border = Color(0xFFFDE68A)),
    error = MessageTone(text = Color(0xFFB91C1C), bg = Color(0xFFFEF2F2), icon = Color(0xFFDC2626), border = Color(0xFFFECACA)),
  ),
  chart = ChartColors(
    sales = Brand.primary, comparison = Brand.blue, costs = Brand.purple, discount = Brand.orange,
    drop = Brand.red, stock = Brand.teal, neutral = Brand.neutral, grid = Color(0xFFE2E8F0),
  ),
  //  0 3px 12px rgba(15,23,42,.06): آلفای ۳۲٪ × آلفای سایهٔ سامانه (~۱۹٪) ≈ ۶٪
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F108B81),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4D108B81),
  auroraTwo = Color(0x3D2563EB),
  auroraThree = Color(0x3314B8A6),
)

/**
 *  شب — کارت (#162235) همیشه از زمینه (#0B1220) جداست و خطِ دورِ خاکستریِ
 *  روشن (#34445A) دارد: «کارت در شب هرگز با زمینه یکی نشود».
 *  رنگ‌هایی که فایل برای شب نداده (زمینهٔ فرعی، شش وضعیت، پیام‌ها، غیرفعال) این‌جا
 *  ساخته شده‌اند: همان رنگ، تیره برای زمینه و روشن برای متن، همه ≥ ۴٫۵:۱.
 */
val DarkColors = ShopColors(
  background = Color(0xFF0B1220),
  //  فایل برای شب ندارد ⇒ همان سطحِ وارونهٔ شب (#050A14): یک پله تیره‌تر از زمینه،
  //  تا منوی کناری و شیارِ کنترل از کارت (۱٫۲۴:۱) و از محتوا جدا دیده شوند
  backgroundSubtle = Color(0xFF050A14),
  surface = Color(0xFF162235),
  surfaceElevated = Color(0xFF203149),
  surfaceSelected = Color(0xFF123C3A),
  textPrimary = Color(0xFFF1F5F9),
  textSecondary = Color(0xFFB6C2D2),
  textMuted = Color(0xFF94A3B8),
  icon = Color(0xFFA3B2C5),
  iconActive = Color(0xFF6EE7D1),
  borderCard = Color(0xFF34445A),
  borderInput = Color(0xFF40516A),
  divider = Color(0xFF29374A),
  //  دکمهٔ اصلیِ شب (#19A985) — همان رنگِ پُرِ برند در شب
  brandPrimary = Color(0xFF19A985),
  brandDeep = Brand.deep,
  brandText = Color(0xFF6EE7D1),
  link = Color(0xFF93C5FD),
  focus = Color(0xFF6EE7D1),
  inverse = Color(0xFF050A14),
  inverseRaised = Color(0xFF162235),
  onInverse = Color(0xFFF1F5F9),
  onInverseMuted = Color(0xFFB6C2D2),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF19A985), text = Color(0xFF041B15), border = Color(0xFF19A985)),
    secondary = ButtonTone(bg = Color(0xFF162235), text = Color(0xFF6EE7D1), border = Color(0xFF40516A)),
    neutral = ButtonTone(bg = Color(0xFF203149), text = Color(0xFFF1F5F9), border = Color(0xFF203149)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    //  ساخته‌شده: غیرفعالِ شب — کم‌رنگ ولی روی کارت پیدا
    disabled = ButtonTone(bg = Color(0xFF1B2839), text = Color(0xFF64748B), border = Color(0xFF29374A)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF86EFAC), bg = Color(0xFF123C2B)),
    inDelivery = StatusTone(fg = Color(0xFF93C5FD), bg = Color(0xFF172F57)),
    awaitingApproval = StatusTone(fg = Color(0xFFFCD34D), bg = Color(0xFF3D2E0E)),
    cancelled = StatusTone(fg = Color(0xFFFCA5A5), bg = Color(0xFF451A1E)),
    needsReview = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    preparing = StatusTone(fg = Color(0xFF67E8F9), bg = Color(0xFF0E3A45)),
    inactive = StatusTone(fg = Color(0xFFCBD5E1), bg = Color(0xFF273548)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF86EFAC), bg = Color(0xFF0F2A1E), icon = Color(0xFF4ADE80), border = Color(0xFF1F5135)),
    info = MessageTone(text = Color(0xFF93C5FD), bg = Color(0xFF0F1E3A), icon = Color(0xFF60A5FA), border = Color(0xFF1E3A8A)),
    warning = MessageTone(text = Color(0xFFFCD34D), bg = Color(0xFF2B1F0B), icon = Color(0xFFFBBF24), border = Color(0xFF78350F)),
    error = MessageTone(text = Color(0xFFFCA5A5), bg = Color(0xFF2D1215), icon = Color(0xFFF87171), border = Color(0xFF7F1D1D)),
  ),
  chart = ChartColors(
    sales = Brand.primary, comparison = Brand.blue, costs = Brand.purple, discount = Brand.orange,
    drop = Brand.red, stock = Brand.teal, neutral = Brand.neutral, grid = Color(0xFF29374A),
  ),
  shadow = Color(0x66000000),
  scrim = Color(0xCC020509),
  glow = Color(0x3319A985),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C108B81),
  auroraTwo = Color(0x612563EB),
  auroraThree = Color(0x5714B8A6),
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
 *  متن روی سطح ≥ ۴٫۵:۱ باشد ⇒ همان دکمهٔ اصلی (روز #08665F، شب #19A985).
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
    inversePrimary = Color(0xFF6EE7D1),
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
  val target = if (dark) DarkColors else LightColors
  //  عوض‌شدنِ روز و شب نباید مثل خاموش‌وروشنِ چراغ باشد: سه رنگِ اصلی
  //  نرم جابه‌جا می‌شوند و بقیه دنبالشان می‌آیند
  val bg by animateColorAsState(target.background, Springs.effect, label = "themeBg")
  val surface by animateColorAsState(target.surface, Springs.effect, label = "themeSurface")
  val text by animateColorAsState(target.textPrimary, Springs.effect, label = "themeText")
  val colors = target.copy(background = bg, surface = surface, textPrimary = text)
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
