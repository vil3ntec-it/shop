package ir.vil3ntec.tohid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette.Hue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 *  رنگِ هر بخش (۶۵s، صاحب‌کار: «تمام سربرگ‌ها باید وسط باشن و رنگِ خودشون داشته باشن؛ تمام رنگ‌ها باید با دقت باشن، نه
 *  همین‌جوری یک رنگ بدی» — بیشترِ مردم با رنگ می‌فهمند). **یک** منبعِ رنگ: هر بخش همان خانوادهٔ رنگِ کاشیِ خودش در «بیشتر» را
 *  دارد (`V3Modules.HUE_BY_ID`؛ همان که نقشهٔ رنگِ تنظیمات — `SettingsHues` — هم می‌خواند) و این‌جا برای هر خانواده تهِ‌رنگ، مرز،
 *  عددِ دقیق و رنگِ نشانهٔ کاشی (روز و شب) نوشته شده است: کارتِ خلاصهٔ هر بخش (`V3Summary`) تهِ‌رنگِ همان بخش (نه گرادیان)، مرزِ
 *  کمی پررنگ‌تر و عددِ درشت به رنگِ پررنگِ همان خانواده؛ رنگِ پولِ تاریخچه (`historyTone`) هم از همین خانواده‌ها ([ofHue])؛
 *  نشانهٔ کاشی‌ها و سری‌های نمودار (`ThemeAccents`) هم از همین ([glyph]). صفحهٔ تازه از همین جدول برمی‌دارد و رنگِ تازه نمی‌سازد
 *  (`docs/RULES-fa.md` ← «ظاهر و طراحی»).
 *
 *  ۶۸c (صاحب‌کار: «رنگ صورتی کار نگیر، توی برنامه خیلی مسخره میشه. رنگ‌های برند بزار، لوکس»): **یک** پالتِ آرام و گران از برند
 *  — هفت خانواده، هیچ صورتی/ارغوانی، لیمویی/زیتونی یا رنگِ تند (`LuxPaletteGuardTest`):
 *
 *    نام در کد (`Hue`)        خانواده          بخش‌ها
 *    SKY / BLUE              لاجوردیِ سرمه‌ای   گزارش
 *    VIOLET / PURPLE         نیلیِ برند        فروش و تاریخچه
 *    TEAL                    نفتی             خرید و تأمین‌کننده، سفارشِ آنلاین
 *    MINT                    زمردی            رسیدها، گاوصندوق
 *    AMBER / ORANGE          زرِ گرم           مصارف، تخفیف
 *    CORAL                   عقیقِ سرخ (فقط خطر و بدهی) قرض
 *    SLATE                   گرافیت           انبار و کالا
 *
 *  هر خانه برای روز و شب: [SectionTone.tint] زمینهٔ کارت (روز بسیار روشن، شب تیره و پُر)، [SectionTone.edge] مرز (تزئینی)،
 *  [SectionTone.strong] عدد و پیکان، [SectionTone.label] برچسب و خطِ کم‌رنگ، [SectionTone.ink] نوشتهٔ اصلی، [SectionTone.glyph]
 *  نشانهٔ کاشیِ همان خانواده روی زمینهٔ صفحه. نوشته روی تهِ‌رنگ دست‌کم ۴٫۵:۱ (WCAG AA) و جفت‌های کنارِ هم ([PAIRS]،
 *  [MONEY_PAIRS]) در کوررنگیِ قرمز-سبز هم جدا (`SectionColorsTest`). رنگ هرگز تنها نیست: برچسب (و در تاریخچه آیکن) همیشه کنارش است.
 */
enum class V3Section(val hue: Hue) {
  /** فروش و تاریخچهٔ فروش — نیلیِ برند (کاشیِ «تاریخچه»، «فاکتور» ِ تنظیمات، میان‌برِ تاریخچهٔ خانه) */
  SALES(Hue.VIOLET),

  /** مصارف — زرِ گرم (کاشیِ «مصارف»)؛ دیگر صورتی نیست */
  EXPENSES(Hue.AMBER),

  /** خرید و تأمین‌کننده — نفتی (کاشیِ «خرید»)؛ دیگر لیمویی/زیتونی نیست */
  PURCHASES(Hue.TEAL),

  /** قرض (بدهکار) — عقیقِ سرخ، تنها بخشی که رنگِ خطر دارد (کاشیِ «قرض‌داران»)؛ نه مرجانیِ صورتی */
  DEBT(Hue.CORAL),

  /** دریافتِ قرض — زمردی (کاشیِ «رسیدها»، میان‌برِ دریافتِ خانه) */
  RECEIPTS(Hue.MINT),

  /** گاوصندوق — زمردی (پولِ دخل؛ همان خانوادهٔ رسیدها) */
  SAFE(Hue.MINT),

  /** گزارش — لاجوردیِ سرمه‌ای (کاشیِ «گزارش‌ها»، «گزارش» ِ تنظیمات) */
  REPORTS(Hue.SKY),

  /** تخفیف — زرِ گرم (کاشیِ «تخفیف‌ها») */
  PROMOS(Hue.AMBER),

  /** انبار و کالا — گرافیت (کاشیِ «انبار»، «کالا» ِ تنظیمات) */
  STOCK(Hue.SLATE),

  /** سفارش و فروشگاهِ اینترنتی — نفتی (کاشیِ «سفارش‌ها»، «آنلاین» ِ تنظیمات)؛ نه مرجانی */
  ORDERS(Hue.TEAL),
}

/** رنگ‌های یک خانواده در یک تم */
@Immutable
data class SectionTone(val tint: Color, val edge: Color, val strong: Color, val label: Color, val ink: Color, val glyph: Color)

object SectionColors {

  private val LABEL_DAY = Color(0xFF475569)
  private val INK_DAY = Color(0xFF0F172A)
  private val LABEL_NIGHT = Color(0xFFCBD5E1)
  private val INK_NIGHT = Color(0xFFF1F5F9)

  private fun day(tint: Long, edge: Long, strong: Long, glyph: Long) =
    SectionTone(Color(tint), Color(edge), Color(strong), LABEL_DAY, INK_DAY, Color(glyph))

  private fun night(tint: Long, edge: Long, strong: Long, glyph: Long) =
    SectionTone(Color(tint), Color(edge), Color(strong), LABEL_NIGHT, INK_NIGHT, Color(glyph))

  /** نامِ یکتای هر خانواده (هم‌معنی‌های پالت یکی‌اند: BLUE = SKY، ORANGE = AMBER، PURPLE = VIOLET؛ برند ⇒ لاجوردی) */
  fun family(h: Hue): Hue = when (h) {
    Hue.BLUE, Hue.BRAND -> Hue.SKY
    Hue.ORANGE -> Hue.AMBER
    Hue.PURPLE -> Hue.VIOLET
    else -> h
  }

  /**
   *  ترتیبِ رنگ‌های همراهِ تم (`ShopColors.accents`، `ThemeAccents`) و سری‌های نمودار: ۰ لاجوردی، ۱ نیلی، ۲ عقیق، ۳ زر،
   *  ۴ زمرد، ۵ گرافیت، ۶ نفتی — همان جاهای پیشینِ «آسمانی، بنفش، مرجانی، کهربایی، نعنایی، صورتی، لیمویی» تا میان‌برهای خانه
   *  (`HomeAccents`) بی دست خوردن رنگِ تازه بگیرند.
   */
  val ORDER: List<Hue> = listOf(Hue.SKY, Hue.VIOLET, Hue.CORAL, Hue.AMBER, Hue.MINT, Hue.SLATE, Hue.TEAL)

  /** روز: تهِ‌رنگِ بسیار روشن، مرزِ نرم، عددِ پررنگِ همان خانواده و نشانهٔ کاشی (روی سفید ≥ ۳:۱) */
  val DAY: Map<Hue, SectionTone> = mapOf(
    Hue.SKY to day(0xFFEEF2FA, 0xFFC3CFE8, 0xFF1E3A8A, 0xFF1E40AF),
    Hue.VIOLET to day(0xFFF2F2FD, 0xFFD2D2F4, 0xFF3F3AB0, 0xFF4F46E5),
    Hue.TEAL to day(0xFFECF6F8, 0xFFBBDCE3, 0xFF0B5E6E, 0xFF0E7490),
    Hue.MINT to day(0xFFECF7F2, 0xFFB5E1CC, 0xFF0B6B4A, 0xFF05875F),
    Hue.AMBER to day(0xFFFAF5EA, 0xFFE9DAB5, 0xFF7E5A10, 0xFFA87A12),
    Hue.CORAL to day(0xFFFBF1F1, 0xFFEDC5C5, 0xFF8F1D24, 0xFFB4232F),
    Hue.SLATE to day(0xFFF2F4F7, 0xFFD2D8E1, 0xFF334155, 0xFF526177),
  )

  /** شب: تهِ‌رنگِ تیره و پُرِ همان خانواده روی کارتِ شب (#11151E)، مرزِ روشن‌تر، عدد و نشانهٔ روشن (روی کارت ≥ ۶:۱) */
  val NIGHT: Map<Hue, SectionTone> = mapOf(
    Hue.SKY to night(0xFF131D33, 0xFF27406B, 0xFFA9BEEC, 0xFF86A6E8),
    Hue.VIOLET to night(0xFF1C1D40, 0xFF34336F, 0xFFB3B3F3, 0xFF9696F0),
    Hue.TEAL to night(0xFF0F2731, 0xFF1A4A5B, 0xFF8ACCDA, 0xFF4FB6CC),
    Hue.MINT to night(0xFF0F2A24, 0xFF1B4D3F, 0xFF94E6C4, 0xFF4DC79A),
    Hue.AMBER to night(0xFF2B2416, 0xFF5A4722, 0xFFE4C682, 0xFFD9B260),
    Hue.CORAL to night(0xFF2C191B, 0xFF5C2B2E, 0xFFEE8E86, 0xFFE57B76),
    Hue.SLATE to night(0xFF1D232E, 0xFF384354, 0xFFC4CDD9, 0xFF9AA8BB),
  )

  /** بخش‌هایی که کنارِ هم دیده می‌شوند — در کوررنگی هم باید جدا بمانند */
  val PAIRS: List<Pair<V3Section, V3Section>> = listOf(
    V3Section.DEBT to V3Section.RECEIPTS,
    V3Section.SALES to V3Section.EXPENSES,
    V3Section.PURCHASES to V3Section.SALES,
  )

  /**
   *  جفت‌های رنگِ پولِ تاریخچه که در یک فهرست کنارِ هم‌اند (نقد/نسیه/کارت در ردیف‌ها، برگشت در ردیف و نتیجه، قرضِ وصول‌شده
   *  کنارِ برگشت و نقد) — همان خانواده‌های `historyTone`.
   */
  val MONEY_PAIRS: List<Pair<Hue, Hue>> = listOf(
    Hue.MINT to Hue.AMBER, Hue.MINT to Hue.SKY, Hue.AMBER to Hue.SKY,
    Hue.AMBER to Hue.CORAL, Hue.MINT to Hue.CORAL, Hue.CORAL to Hue.TEAL, Hue.MINT to Hue.TEAL,
  )

  /** نشانهٔ کاشیِ خانوادهٔ [h] (رنگِ همراهِ تم) در روز یا شب */
  fun glyph(h: Hue, dark: Boolean): Color = ofHue(h, dark).glyph

  fun ofHue(h: Hue, dark: Boolean): SectionTone = (if (dark) NIGHT else DAY).getValue(family(h))

  fun of(section: V3Section, dark: Boolean): SectionTone = ofHue(section.hue, dark)

  /** روشناییِ نسبیِ WCAG 2.1 */
  fun luminance(c: Color): Double {
    fun ch(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
  }

  /** نسبتِ کنتراستِ WCAG ِ دو رنگ (۱ تا ۲۱) */
  fun contrast(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
  }
}

/** رنگ‌های [this] در تمِ جاری (روز/شب) */
@Composable
fun V3Section.tone(): SectionTone = SectionColors.of(this, PhoneV3.colors.source.isDark)
