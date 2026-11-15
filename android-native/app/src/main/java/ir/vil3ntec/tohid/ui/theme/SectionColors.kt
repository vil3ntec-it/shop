package ir.vil3ntec.tohid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 *  رنگِ هر بخش (۶۵s، صاحب‌کار: «تمام سربرگ‌ها باید وسط باشن و رنگِ خودشون داشته باشن؛ تمام رنگ‌ها باید با دقت باشن، نه
 *  همین‌جوری یک رنگ بدی» — بیشترِ مردم با رنگ می‌فهمند). **تنها** جدولِ رنگِ بخش‌ها: کارتِ خلاصهٔ هر بخش (`V3Summary`)
 *  تهِ‌رنگِ همان بخش را دارد (نه گرادیان)، مرزِ کمی پررنگ‌تر و عددِ درشت به رنگِ پررنگِ همان بخش. صفحهٔ تازه از همین
 *  جدول برمی‌دارد و رنگِ تازه نمی‌سازد (`docs/RULES-fa.md` ← «ظاهر و طراحی»).
 *
 *  هر خانه برای روز و شب: [SectionTone.tint] زمینهٔ کارت، [SectionTone.edge] مرز (تزئینی)، [SectionTone.strong] عدد و
 *  پیکان، [SectionTone.label] برچسب و خطِ کم‌رنگ، [SectionTone.ink] نوشتهٔ اصلی. نوشته روی تهِ‌رنگ دست‌کم ۴٫۵:۱ (WCAG AA)
 *  و جفت‌های کنارِ هم (قرض/دریافت، فروش/مصارف، خرید/فروش) در کوررنگیِ قرمز-سبز هم جدا (`SectionColorsTest`). رنگ هرگز
 *  تنها نیست: برچسبِ بخش همیشه کنارِ عدد است.
 */
enum class V3Section {
  /** فروش و تاریخچهٔ فروش — آبی */
  SALES,

  /** مصارف — سرخ‌گلی (رُز) */
  EXPENSES,

  /** خرید و تأمین‌کننده — نارنجیِ کهربایی */
  PURCHASES,

  /** قرض (بدهکار) — سرخِ قرض، همان رنگِ امروزِ قرض‌داران */
  DEBT,

  /** دریافتِ قرض — سبزِ دریافت */
  RECEIPTS,

  /** گاوصندوق — فیروزه‌ای */
  SAFE,

  /** گزارش — نیلی */
  REPORTS,

  /** تخفیف — بنفش */
  PROMOS,

  /** انبار و کالا — سبزِ زیتونی */
  STOCK,

  /** سفارش و فروشگاهِ اینترنتی — فیروزه‌ایِ آبی (سیان) */
  ORDERS,
}

/** رنگ‌های یک بخش در یک تم */
@Immutable
data class SectionTone(val tint: Color, val edge: Color, val strong: Color, val label: Color, val ink: Color)

object SectionColors {

  private val LABEL_DAY = Color(0xFF475569)
  private val INK_DAY = Color(0xFF0F172A)
  private val LABEL_NIGHT = Color(0xFFCBD5E1)
  private val INK_NIGHT = Color(0xFFF1F5F9)

  private fun day(tint: Long, edge: Long, strong: Long) = SectionTone(Color(tint), Color(edge), Color(strong), LABEL_DAY, INK_DAY)
  private fun night(tint: Long, edge: Long, strong: Long) = SectionTone(Color(tint), Color(edge), Color(strong), LABEL_NIGHT, INK_NIGHT)

  /** روز: تهِ‌رنگِ ۵۰، مرزِ ۲۰۰ و عددِ ۷۰۰/۸۰۰ ِ همان رنگ */
  val DAY: Map<V3Section, SectionTone> = mapOf(
    V3Section.SALES to day(0xFFEFF6FF, 0xFFBFDBFE, 0xFF1D4ED8),
    V3Section.EXPENSES to day(0xFFFFF1F2, 0xFFFECDD3, 0xFFBE123C),
    V3Section.PURCHASES to day(0xFFFFF7ED, 0xFFFED7AA, 0xFF9A3412),
    V3Section.DEBT to day(0xFFFEF2F2, 0xFFFECACA, 0xFFB91C1C),
    V3Section.RECEIPTS to day(0xFFECFDF5, 0xFFA7F3D0, 0xFF047857),
    V3Section.SAFE to day(0xFFF0FDFA, 0xFF99F6E4, 0xFF0F766E),
    V3Section.REPORTS to day(0xFFEEF2FF, 0xFFC7D2FE, 0xFF4338CA),
    V3Section.PROMOS to day(0xFFFAF5FF, 0xFFE9D5FF, 0xFF7E22CE),
    V3Section.STOCK to day(0xFFF7FEE7, 0xFFD9F99D, 0xFF3F6212),
    V3Section.ORDERS to day(0xFFECFEFF, 0xFFA5F3FC, 0xFF0E7490),
  )

  /** شب: عددِ ۳۰۰/۴۰۰ ِ همان رنگ؛ تهِ‌رنگ ۱۴٪ و مرز ۳۸٪ ِ همان روی کارتِ شب (#11151E) */
  val NIGHT: Map<V3Section, SectionTone> = mapOf(
    V3Section.SALES to night(0xFF232E3D, 0xFF425873, 0xFF93C5FD),
    V3Section.EXPENSES to night(0xFF322932, 0xFF6B4B55, 0xFFFDA4AF),
    V3Section.PURCHASES to night(0xFF322C2A, 0xFF6B543F, 0xFFFDBA74),
    V3Section.DEBT to night(0xFF31222A, 0xFF69383E, 0xFFF87171),
    V3Section.RECEIPTS to night(0xFF1E3233, 0xFF346558, 0xFF6EE7B7),
    V3Section.SAFE to night(0xFF1C3337, 0xFF2E6663, 0xFF5EEAD4),
    V3Section.REPORTS to night(0xFF262B3D, 0xFF495172, 0xFFA5B4FC),
    V3Section.PROMOS to night(0xFF2D2B3D, 0xFF5D5173, 0xFFD8B4FE),
    V3Section.STOCK to night(0xFF293428, 0xFF536939, 0xFFBEF264),
    V3Section.ORDERS to night(0xFF1D333D, 0xFF326571, 0xFF67E8F9),
  )

  /** جفت‌هایی که کنارِ هم دیده می‌شوند — در کوررنگی هم باید جدا بمانند */
  val PAIRS: List<Pair<V3Section, V3Section>> = listOf(
    V3Section.DEBT to V3Section.RECEIPTS,
    V3Section.SALES to V3Section.EXPENSES,
    V3Section.PURCHASES to V3Section.SALES,
  )

  fun of(section: V3Section, dark: Boolean): SectionTone = (if (dark) NIGHT else DAY).getValue(section)

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
