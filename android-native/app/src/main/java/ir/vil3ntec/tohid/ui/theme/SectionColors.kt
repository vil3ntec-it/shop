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
 *  دارد (`V3Modules.HUE_BY_ID`؛ همان که نقشهٔ رنگِ تنظیمات — `SettingsHues` — هم می‌خواند) و این‌جا برای هر خانواده تهِ‌رنگ، مرز
 *  و عددِ دقیق (روز و شب) نوشته شده است: کارتِ خلاصهٔ هر بخش (`V3Summary`) تهِ‌رنگِ همان بخش (نه گرادیان)، مرزِ کمی پررنگ‌تر و
 *  عددِ درشت به رنگِ پررنگِ همان خانواده؛ رنگِ پولِ تاریخچه (`historyTone`) هم از همین خانواده‌ها ([ofHue]). صفحهٔ تازه از همین
 *  جدول برمی‌دارد و رنگِ تازه نمی‌سازد (`docs/RULES-fa.md` ← «ظاهر و طراحی»).
 *
 *  هر خانه برای روز و شب: [SectionTone.tint] زمینهٔ کارت، [SectionTone.edge] مرز (تزئینی)، [SectionTone.strong] عدد و
 *  پیکان، [SectionTone.label] برچسب و خطِ کم‌رنگ، [SectionTone.ink] نوشتهٔ اصلی. نوشته روی تهِ‌رنگ دست‌کم ۴٫۵:۱ (WCAG AA)
 *  و جفت‌های کنارِ هم ([PAIRS]، [MONEY_PAIRS]) در کوررنگیِ قرمز-سبز هم جدا (`SectionColorsTest`). رنگ هرگز تنها نیست: برچسب
 *  (و در تاریخچه آیکن) همیشه کنارش است.
 */
enum class V3Section(val hue: Hue) {
  /** فروش و تاریخچهٔ فروش — بنفش (کاشیِ «تاریخچه»، «فاکتور» ِ تنظیمات) */
  SALES(Hue.VIOLET),

  /** مصارف — صورتی (کاشیِ «مصارف») */
  EXPENSES(Hue.PINK),

  /** خرید و تأمین‌کننده — لیمویی (کاشیِ «خرید») */
  PURCHASES(Hue.LIME),

  /** قرض (بدهکار) — مرجانیِ سرخ، سرخِ قرضِ امروز (کاشیِ «قرض‌داران») */
  DEBT(Hue.CORAL),

  /** دریافتِ قرض — نعنایی (کاشیِ «رسیدها») */
  RECEIPTS(Hue.MINT),

  /** گاوصندوق — فیروزه‌ای (همان خانوادهٔ نعنایی) */
  SAFE(Hue.TEAL),

  /** گزارش — آسمانی (کاشیِ «گزارش‌ها»، «گزارش» ِ تنظیمات) */
  REPORTS(Hue.SKY),

  /** تخفیف — فیروزه‌ای (کاشیِ «تخفیف‌ها») */
  PROMOS(Hue.TEAL),

  /** انبار و کالا — کهربایی (کاشیِ «انبار»، «کالا» ِ تنظیمات) */
  STOCK(Hue.AMBER),

  /** سفارش و فروشگاهِ اینترنتی — مرجانی («آنلاین» ِ تنظیمات) */
  ORDERS(Hue.CORAL),
}

/** رنگ‌های یک خانواده در یک تم */
@Immutable
data class SectionTone(val tint: Color, val edge: Color, val strong: Color, val label: Color, val ink: Color)

object SectionColors {

  private val LABEL_DAY = Color(0xFF475569)
  private val INK_DAY = Color(0xFF0F172A)
  private val LABEL_NIGHT = Color(0xFFCBD5E1)
  private val INK_NIGHT = Color(0xFFF1F5F9)

  private fun day(tint: Long, edge: Long, strong: Long) = SectionTone(Color(tint), Color(edge), Color(strong), LABEL_DAY, INK_DAY)
  private fun night(tint: Long, edge: Long, strong: Long) = SectionTone(Color(tint), Color(edge), Color(strong), LABEL_NIGHT, INK_NIGHT)

  /** نامِ یکتای هر خانواده (هم‌معنی‌های پالت یکی‌اند: TEAL = MINT، BLUE = SKY، ORANGE = AMBER، PURPLE = VIOLET؛ برند ⇒ آسمانی) */
  fun family(h: Hue): Hue = when (h) {
    Hue.TEAL -> Hue.MINT
    Hue.BLUE, Hue.BRAND -> Hue.SKY
    Hue.ORANGE -> Hue.AMBER
    Hue.PURPLE -> Hue.VIOLET
    else -> h
  }

  /** روز: تهِ‌رنگِ ۵۰، مرزِ ۲۰۰ و عددِ ۷۰۰/۸۰۰ ِ همان خانواده */
  val DAY: Map<Hue, SectionTone> = mapOf(
    Hue.SKY to day(0xFFF0F9FF, 0xFFBAE6FD, 0xFF0369A1),
    Hue.VIOLET to day(0xFFF5F3FF, 0xFFDDD6FE, 0xFF6D28D9),
    Hue.CORAL to day(0xFFFFF4F1, 0xFFFECCC2, 0xFF991B1B),
    Hue.AMBER to day(0xFFFFFBEB, 0xFFFDE68A, 0xFFB45309),
    Hue.MINT to day(0xFFF0FDFA, 0xFF99F6E4, 0xFF0F766E),
    Hue.PINK to day(0xFFFDF2F8, 0xFFFBCFE8, 0xFFBE185D),
    Hue.LIME to day(0xFFF7FEE7, 0xFFD9F99D, 0xFF3F6212),
  )

  /** شب: عددِ ۳۰۰ ِ همان خانواده؛ تهِ‌رنگ ۱۴٪ و مرز ۳۸٪ ِ همان روی کارتِ شب (#11151E) */
  val NIGHT: Map<Hue, SectionTone> = mapOf(
    Hue.SKY to night(0xFF20303D, 0xFF3A5D72, 0xFF7DD3FC),
    Hue.VIOLET to night(0xFF2A2B3D, 0xFF555273, 0xFFC4B5FD),
    Hue.CORAL to night(0xFF32282D, 0xFF6B4847, 0xFFFF9B8A),
    Hue.AMBER to night(0xFF323025, 0xFF6A5D30, 0xFFFCD34D),
    Hue.MINT to night(0xFF1C3337, 0xFF2E6663, 0xFF5EEAD4),
    Hue.PINK to night(0xFF312A37, 0xFF694D63, 0xFFF9A8D4),
    Hue.LIME to night(0xFF293428, 0xFF536939, 0xFFBEF264),
  )

  /** بخش‌هایی که کنارِ هم دیده می‌شوند — در کوررنگی هم باید جدا بمانند */
  val PAIRS: List<Pair<V3Section, V3Section>> = listOf(
    V3Section.DEBT to V3Section.RECEIPTS,
    V3Section.SALES to V3Section.EXPENSES,
    V3Section.PURCHASES to V3Section.SALES,
  )

  /**
   *  جفت‌های رنگِ پولِ تاریخچه که در یک فهرست کنارِ هم‌اند (نقد/نسیه/کارت در ردیف‌ها، برگشت در ردیف و نتیجه، قرضِ وصول‌شده
   *  کنارِ برگشت) — همان خانواده‌های `historyTone`.
   */
  val MONEY_PAIRS: List<Pair<Hue, Hue>> = listOf(
    Hue.MINT to Hue.AMBER, Hue.MINT to Hue.SKY, Hue.AMBER to Hue.SKY,
    Hue.AMBER to Hue.CORAL, Hue.MINT to Hue.CORAL, Hue.CORAL to Hue.PINK,
  )

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
