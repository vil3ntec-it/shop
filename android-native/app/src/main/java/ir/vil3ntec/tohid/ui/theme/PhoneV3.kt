package ir.vil3ntec.tohid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 *  توکن‌های ظاهرِ گوشی، نسخهٔ ۳. شکل از تصویرهای مرجعِ صاحب محصول (سبدِ r2/r3،
 *  حسابِ r4، تنظیماتِ r5، کارتِ کششیِ r6، خانهٔ r7، آمارِ r8) و رنگ از سیستمِ
 *  رنگِ VILL3N (دستورِ صاحب محصول، ۱۴۰۵/۰۷/۲۸): برندِ سبزآبیِ #108B81 به‌جای بنفش،
 *  زمینه/کارت/سطحِ درونی با مرزِ دیدنی در هر دو تم. رنگِ تازه فقط از همین‌جا.
 */
data class PhoneV3Colors(
  /** زمینهٔ صفحه (سطحِ صفر) */
  val bg: Color,
  /** کارت‌ها و سطحِ کالاها در صندوق (سطحِ یک) */
  val card: Color,
  /** کارتِ حساب (r4) */
  val cardGlass: Color,
  val ink: Color,
  val inkMuted: Color,
  /** مرزِ کارت و خطِ میانِ ردیف‌های یک گروه */
  val hairline: Color,
  /** آیکنِ خطیِ تک‌رنگ */
  val icon: Color,
  val chevron: Color,
  /** سطحِ تیرهٔ وارونه: زمینهٔ سبد، قرصِ فعالِ نوار، کارتِ کششی، دکمهٔ اسکن */
  val black: Color,
  /** دایرهٔ آیکن روی سطحِ تیره (ردیفِ «مشتری» در سبد) */
  val blackRaised: Color,
  val onBlack: Color,
  val onBlackMuted: Color,
  /** رنگِ برند: سرِ خانه، آمار، کارِ اصلی */
  val accent: Color,
  val accentDeep: Color,
  val onAccent: Color,
  /** سبد روی سطحِ تیره: دایرهٔ شمار و دکمهٔ «پرداخت» */
  val cart: Color,
  val onCart: Color,
  /** کلیدِ روشن */
  val toggleOn: Color,
  /** کاشیِ بخش‌ها و سطحِ درونی (سطحِ دو) */
  val tile: Color,
  val positive: Color,
  val negative: Color,
  /**
   *  «در انتظار» (حالتِ پیک: کارِ هنوز به دکان نرسیده، مأموریتِ تازه) — همان
   *  رنگِ متنِ وضعیتِ «در انتظارِ تأیید»ِ سیستمِ رنگ (§۸)؛ شب روشن‌تر.
   */
  val waiting: Color = Color(0xFF92400E),
)

val PhoneV3Light = PhoneV3Colors(
  bg = Color(0xFFF3F6FA),
  card = Color(0xFFFFFFFF),
  cardGlass = Color(0xF2FFFFFF),
  ink = Color(0xFF17212B),
  inkMuted = Color(0xFF5B687A),
  hairline = Color(0xFFD5DEE9),
  icon = Color(0xFF64748B),
  chevron = Color(0xFF7B8798),
  black = Color(0xFF0B1220),
  blackRaised = Color(0xFF162235),
  onBlack = Color(0xFFF1F5F9),
  onBlackMuted = Color(0xFFB6C2D2),
  accent = Color(0xFF108B81),
  accentDeep = Color(0xFF08665F),
  onAccent = Color(0xFFFFFFFF),
  cart = Color(0xFF19A985),
  onCart = Color(0xFF041B15),
  toggleOn = Color(0xFF108B81),
  tile = Color(0xFFF8FAFC),
  positive = Color(0xFF15803D),
  negative = Color(0xFFB91C1C),
)

//  شب: کارت هرگز در زمینه گم نمی‌شود (#0B1220 ⇐ #162235 ⇐ #203149) و سبد روی تیره‌ترین سطح است
val PhoneV3Dark = PhoneV3Colors(
  bg = Color(0xFF0B1220),
  card = Color(0xFF162235),
  cardGlass = Color(0xF2162235),
  ink = Color(0xFFF1F5F9),
  inkMuted = Color(0xFFB6C2D2),
  hairline = Color(0xFF34445A),
  icon = Color(0xFFA3B2C5),
  chevron = Color(0xFF94A3B8),
  black = Color(0xFF050A14),
  blackRaised = Color(0xFF162235),
  onBlack = Color(0xFFF1F5F9),
  onBlackMuted = Color(0xFFB6C2D2),
  //  سرِ خانه و کارت‌های آمار متنِ سفید دارند ⇒ همان برندِ روز (سفید روی #108B81 و #08665F خواناست)
  accent = Color(0xFF108B81),
  accentDeep = Color(0xFF08665F),
  onAccent = Color(0xFFFFFFFF),
  cart = Color(0xFF19A985),
  onCart = Color(0xFF041B15),
  toggleOn = Color(0xFF19A985),
  tile = Color(0xFF203149),
  positive = Color(0xFF86EFAC),
  negative = Color(0xFFFCA5A5),
  waiting = Color(0xFFFCD34D),
)

object PhoneV3 {
  /** تمِ جاری از روشنیِ زمینهٔ `Shop.colors` — مثلِ `PhoneSheet` */
  val colors: PhoneV3Colors
    @Composable get() = of(Shop.colors.bg)

  fun of(bg: Color): PhoneV3Colors = if (bg.luminance() < 0.5f) PhoneV3Dark else PhoneV3Light

  /** گوشهٔ پایینِ سطحِ سفیدِ کالاها و کارتِ کششی (r2/r3/r6) */
  val sheetRadius = 36.dp
  /** کارتِ اصلیِ حساب (r4) */
  val cardRadius = 28.dp
  /** کارتِ گروهِ تنظیمات و کادرهای آمار (r4/r5) */
  val groupRadius = 18.dp
  /** کاشیِ بخش‌ها (r7) */
  val tileRadius = 16.dp
  /** بلندیِ نوارِ قرصیِ پایین */
  val navHeight = 64.dp
  /** بلندیِ ردیفِ تنظیمات (r5) */
  val rowHeight = 52.dp

  /** دکمه‌ها، آیکنِ فعال و رنگِ وضعیت‌ها (سیستمِ رنگِ VILL3N §۷/§۸) برای تمِ جاری */
  val ui: PhoneV3Ui
    @Composable get() = if (Shop.colors.bg.luminance() < 0.5f) PhoneV3UiDark else PhoneV3UiLight
}

/** یک جفتِ رنگِ وضعیت: متن/آیکن روی زمینهٔ کم‌رنگ — هرگز رنگ بی برچسب */
data class PhoneV3Tone(val fg: Color, val bg: Color)

/**
 *  رنگ‌های کار و وضعیت از سیستمِ رنگِ VILL3N (۱۴۰۵/۰۷/۲۸) — جدا از `PhoneV3Colors`
 *  تا سازنده‌های آن دست نخورند. وضعیتِ سفارش برای مشتری، دکان، پیک و مدیر یک معنا
 *  دارد (§۸). `onDark*` برای سطحِ تیرهٔ سرمه‌ای در هر دو تم (کارتِ «مأموریتِ جاری»،
 *  برگهٔ پیگیری): دکمهٔ اصلی #19A985 با متنِ #041B15.
 */
data class PhoneV3Ui(
  val primary: Color,
  val onPrimary: Color,
  val primaryOnDark: Color,
  val onPrimaryOnDark: Color,
  val secondaryBg: Color,
  val secondaryFg: Color,
  val secondaryBorder: Color,
  val neutralBg: Color,
  val neutralFg: Color,
  val danger: Color,
  val onDanger: Color,
  val disabledBg: Color,
  val disabledFg: Color,
  /** آیکنِ فعال/برند */
  val iconActive: Color,
  val success: PhoneV3Tone,
  val delivery: PhoneV3Tone,
  val awaiting: PhoneV3Tone,
  val cancelled: PhoneV3Tone,
  val preparing: PhoneV3Tone,
  val inactive: PhoneV3Tone,
)

private val StatusDark = listOf(
  PhoneV3Tone(Color(0xFF86EFAC), Color(0xFF123C2B)),
  PhoneV3Tone(Color(0xFF93C5FD), Color(0xFF172A4D)),
  PhoneV3Tone(Color(0xFFFCD34D), Color(0xFF3A2C0B)),
  PhoneV3Tone(Color(0xFFFCA5A5), Color(0xFF3F1A1A)),
  PhoneV3Tone(Color(0xFF67E8F9), Color(0xFF0F3640)),
  PhoneV3Tone(Color(0xFFB6C2D2), Color(0xFF203149)),
)

val PhoneV3UiLight = PhoneV3Ui(
  primary = Color(0xFF108B81), onPrimary = Color(0xFFFFFFFF),
  primaryOnDark = Color(0xFF19A985), onPrimaryOnDark = Color(0xFF041B15),
  secondaryBg = Color(0xFFFFFFFF), secondaryFg = Color(0xFF08665F), secondaryBorder = Color(0xFFB8DCD5),
  neutralBg = Color(0xFFE8EDF3), neutralFg = Color(0xFF344256),
  danger = Color(0xFFDC2626), onDanger = Color(0xFFFFFFFF),
  disabledBg = Color(0xFFE2E8F0), disabledFg = Color(0xFF94A3B8),
  iconActive = Color(0xFF108B81),
  success = PhoneV3Tone(Color(0xFF15803D), Color(0xFFDCFCE7)),
  delivery = PhoneV3Tone(Color(0xFF1D4ED8), Color(0xFFDBEAFE)),
  awaiting = PhoneV3Tone(Color(0xFF92400E), Color(0xFFFEF3C7)),
  cancelled = PhoneV3Tone(Color(0xFFB91C1C), Color(0xFFFEE2E2)),
  preparing = PhoneV3Tone(Color(0xFF0E7490), Color(0xFFCFFAFE)),
  inactive = PhoneV3Tone(Color(0xFF475569), Color(0xFFE2E8F0)),
)

val PhoneV3UiDark = PhoneV3Ui(
  primary = Color(0xFF19A985), onPrimary = Color(0xFF041B15),
  primaryOnDark = Color(0xFF19A985), onPrimaryOnDark = Color(0xFF041B15),
  secondaryBg = Color(0xFF162235), secondaryFg = Color(0xFF6EE7D1), secondaryBorder = Color(0xFF40516A),
  neutralBg = Color(0xFF203149), neutralFg = Color(0xFFF1F5F9),
  danger = Color(0xFFDC2626), onDanger = Color(0xFFFFFFFF),
  disabledBg = Color(0xFF203149), disabledFg = Color(0xFF64748B),
  iconActive = Color(0xFF6EE7D1),
  success = StatusDark[0], delivery = StatusDark[1], awaiting = StatusDark[2],
  cancelled = StatusDark[3], preparing = StatusDark[4], inactive = StatusDark[5],
)

/**
 *  رنگ‌های پشتیبانِ سیستمِ VILL3N برای خانه و آمارِ گوشی (phone-v3-home) — §۳، §۴، §۵، §۱۰.
 *
 *  • نشانه‌های پُر و رنگیِ شبکهٔ «بخش‌ها» (شکلِ r7): برند، سبزآبی، آبی، نارنجی، بنفش؛
 *    سرخ فقط برای هشدارِ واقعی. شب کمی روشن‌تر تا روی کاشیِ #203149 خوانا بماند.
 *  • نمودارِ آمار (شکلِ r8): فروش = برند، هزینه‌ها = بنفش، خطِ راهنما.
 *  • نوارِ پایین: آیکنِ فعال = iconActive، دایرهٔ فعال = surfaceSelected.
 */
object PhoneV3Palette {
  enum class Hue { BRAND, TEAL, BLUE, ORANGE, PURPLE }

  fun glyph(c: PhoneV3Colors, hue: Hue): Color {
    val dark = c === PhoneV3Dark
    return when (hue) {
      Hue.BRAND -> if (dark) Color(0xFF19A985) else Color(0xFF108B81)
      Hue.TEAL -> if (dark) Color(0xFF2DD4BF) else Color(0xFF14B8A6)
      Hue.BLUE -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
      Hue.ORANGE -> if (dark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
      Hue.PURPLE -> if (dark) Color(0xFFA78BFA) else Color(0xFF8B5CF6)
    }
  }

  /** سری‌های نمودار §۱۰: ۱ فروش = برند، ۳ هزینه = بنفش */
  fun chartSales(c: PhoneV3Colors): Color = c.accent
  fun chartCost(@Suppress("UNUSED_PARAMETER") c: PhoneV3Colors): Color = Color(0xFF8B5CF6)
  fun chartGrid(c: PhoneV3Colors): Color = if (c === PhoneV3Dark) Color(0xFF29374A) else Color(0xFFE2E8F0)

  /** iconActive و surfaceSelected */
  fun iconActive(c: PhoneV3Colors): Color = if (c === PhoneV3Dark) Color(0xFF6EE7D1) else Color(0xFF108B81)
  fun selected(c: PhoneV3Colors): Color = if (c === PhoneV3Dark) Color(0xFF123C3A) else Color(0xFFE0F2EF)
}

/* ======================= قرض‌داران (r9-debtors.jpg) ======================= */

/**
 *  رنگ‌های صفحهٔ قرض‌داران — سیستمِ رنگِ VILL3N (۱۴۰۵/۰۷/۲۸)؛ r9 فقط شکل است.
 *
 *  زمینه/کارت/مرز همان `PhoneV3Colors` است (کارتِ سفید با مرزِ دیدنی روی زمینهٔ
 *  #F3F6FA، و در شب #162235 روی #0B1220). مبلغ با `negative`/`positive`؛ کارتِ هر
 *  نفر با جفتِ رنگِ وضعیت (بدهکار = «لغوشده»ِ قرمز، صاف = «تحویل‌شده»ِ سبز)، و قرصِ
 *  روز با جفتِ خنثیِ «غیرفعال» و آیکنِ ساعت — رنگ هرگز تنها نیست، برچسب کنارش است.
 *  رنگِ تازه فقط جفت‌های وضعیتِ شب است که فایلِ سیستم تعریفشان را به ما سپرده.
 */
data class PhoneV3DebtColors(
  /** زمینهٔ صفحه (سطحِ صفر) */
  val page: Color,
  /** کارتِ سه‌عددی، تراشهٔ انتخاب‌نشده، دکمه‌های × و مداد (سطحِ یک) */
  val surface: Color,
  /** مرزِ کارت‌ها — در هر دو تم دیدنی */
  val edge: Color,
  val ink: Color,
  /** متنِ دوم: برچسبِ عددها، «N مورد»، زیرِ مبلغ، تراشهٔ انتخاب‌نشده */
  val muted: Color,
  /** آیکنِ خطی: ذره‌بین، × و مداد */
  val icon: Color,
  /** خطِ دورِ کادرِ جستجو */
  val fieldEdge: Color,
  /** کارِ اصلی: دکمهٔ گردِ «+» */
  val accent: Color,
  /** متن/آیکن روی `accent` و روی `accentActive` */
  val onAccent: Color,
  /** فعال/انتخاب‌شده (primaryDeep): تراشهٔ ترتیبِ انتخاب‌شده — متنِ سفید رویش ≥ ۴٫۵:۱ */
  val accentActive: Color,
  /** آیکنِ فعال (iconActive): میکروفون */
  val accentInk: Color,
  /** متنِ برند روی زمینه: برچسب، مرز و نشانگرِ کادرِ جستجوی فعال */
  val accentText: Color,
  /** سطحِ انتخاب‌شده: دایرهٔ میکروفون و دایرهٔ آیکنِ حالتِ خالی */
  val accentSoft: Color,
  /** بدهکار: مبلغ و مرزِ کارت (`negative`) */
  val owe: Color,
  /** بدهکار: زمینهٔ کارت (وضعیتِ قرمز) */
  val oweTint: Color,
  /** صاف یا موجودی‌دار: مبلغ و مرزِ کارت (`positive`) */
  val settled: Color,
  val settledTint: Color,
  /** قرصِ «N روز»: جفتِ خنثیِ «غیرفعال» */
  val pill: Color,
  val onPill: Color,
)

val PhoneV3DebtLight = PhoneV3DebtColors(
  page = PhoneV3Light.bg,
  surface = PhoneV3Light.card,
  edge = PhoneV3Light.hairline,
  ink = PhoneV3Light.ink,
  muted = PhoneV3Light.inkMuted,
  icon = PhoneV3Light.icon,
  fieldEdge = Color(0xFFC5D0DE),
  accent = PhoneV3Light.accent,
  onAccent = PhoneV3Light.onAccent,
  //  سفید روی #108B81 فقط ۴٫۱۷:۱ است (برای آیکنِ «+» بس، برای متنِ تراشه نه)
  accentActive = PhoneV3Light.accentDeep,
  accentInk = PhoneV3Light.accent,
  accentText = PhoneV3Light.accentDeep,
  accentSoft = Color(0xFFE0F2EF),
  owe = PhoneV3Light.negative,
  oweTint = Color(0xFFFEE2E2),
  settled = PhoneV3Light.positive,
  settledTint = Color(0xFFDCFCE7),
  pill = Color(0xFFE2E8F0),
  onPill = Color(0xFF475569),
)

//  شب: دکمهٔ اصلی جفتِ شبِ سیستم (#19A985 با متنِ #041B15)؛ قرمز و خنثیِ شب را
//  سیستم به ما سپرده — هم‌خانوادهٔ سبزِ شبِ خودش (#123C2B / #86EFAC)
val PhoneV3DebtDark = PhoneV3DebtColors(
  page = PhoneV3Dark.bg,
  surface = PhoneV3Dark.card,
  edge = PhoneV3Dark.hairline,
  ink = PhoneV3Dark.ink,
  muted = PhoneV3Dark.inkMuted,
  icon = PhoneV3Dark.icon,
  fieldEdge = Color(0xFF40516A),
  accent = PhoneV3Dark.cart,
  onAccent = PhoneV3Dark.onCart,
  accentActive = PhoneV3Dark.cart,
  accentInk = Color(0xFF6EE7D1),
  accentText = Color(0xFF6EE7D1),
  accentSoft = Color(0xFF123C3A),
  owe = PhoneV3Dark.negative,
  oweTint = Color(0xFF3F1A1F),
  settled = PhoneV3Dark.positive,
  settledTint = Color(0xFF123C2B),
  pill = Color(0xFF29374A),
  onPill = Color(0xFFCBD5E1),
)

/**
 *  اندازه‌های r9 به dp، از همان عکس سنجیده (۲٫۲۵ پیکسل = ۱dp: لبهٔ ۱۶dp همان
 *  ۳۶ پیکسلِ عکس است، دکمهٔ ۲۲dp همان ۴۸ پیکسل).
 */
object PhoneV3Debt {
  /** تمِ جاری از روشنیِ زمینهٔ `Shop.colors` — مثلِ `PhoneV3.colors` */
  val colors: PhoneV3DebtColors
    @Composable get() = of(Shop.colors.bg)

  fun of(bg: Color): PhoneV3DebtColors = if (bg.luminance() < 0.5f) PhoneV3DebtDark else PhoneV3DebtLight

  /**
   *  r9 روی صفحهٔ ۶۶۸dp گرفته شده که متنِ برنامه را ۱٫۱۲ برابر می‌کند
   *  (`TohidTheme`)؛ این صفحه همان ضریب را روی هر دستگاه ثابت نگه می‌دارد تا
   *  گوشی هم همان اندازه‌های r9 را ببیند.
   */
  const val typeScale = 1.12f
  /** کارتِ سه‌عددی و کارتِ هر قرض‌دار (r9: ۴۵ پیکسل) */
  val cardRadius = 20.dp
  /** کادرِ جستجو (r9: ۲۸ پیکسل) */
  val fieldRadius = 12.dp
  /** دایرهٔ میکروفون */
  val micSize = 34.dp
  /** دکمه‌های ریزِ × و مدادِ گوشهٔ کارت */
  val cornerButton = 22.dp
  val cornerButtonRadius = 7.dp
  val cardBorder = 1.2.dp
  /** لبهٔ صفحه */
  val gutter = 16.dp
  /** فاصلهٔ کارت‌ها */
  val gap = 8.dp
  /** کمینهٔ پهنای کارت: سه ستون روی گوشیِ ۳۶۰ تا ۴۷۰dp */
  val tileMin = 104.dp
  /** تبلت: همان چهار ستونِ r9 روی ۶۶۸dp */
  val tileMinTablet = 132.dp
  /** دکمهٔ گردِ «قرض‌دار تازه» */
  val fabSize = 56.dp
}
