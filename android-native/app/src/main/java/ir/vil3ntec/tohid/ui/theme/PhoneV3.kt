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
  val waiting: Color,
) {
  companion object {
    /**
     *  تنها منبعِ رنگ `ShopColors` است (design-v2، سیستمِ رنگِ VILL3N): این‌جا هیچ HEX ِ تازه‌ای
     *  نیست، فقط نام‌گذاریِ گوشیِ نسخهٔ ۳ روی همان توکن‌ها (`PhoneV3TokensTest`).
     *  • `accent`/`onAccent`: سرِ خانه و کارت‌های آمار متنِ سفید دارند ⇒ برندِ روز در هر دو تم.
     *  • `cart`/`onCart`: سبزِ روشنِ برند (دکمهٔ اصلیِ شب) در هر دو تم — سبد روی سطحِ تیره است.
     */
    fun from(s: ShopColors): PhoneV3Colors = PhoneV3Colors(
      bg = s.background,
      card = s.surface,
      cardGlass = s.surface.copy(alpha = 0.95f),
      ink = s.textPrimary,
      inkMuted = s.textSecondary,
      hairline = s.borderCard,
      icon = s.icon,
      chevron = s.textMuted,
      black = s.inverse,
      blackRaised = s.inverseRaised,
      onBlack = s.onInverse,
      onBlackMuted = s.onInverseMuted,
      accent = LightColors.brandPrimary,
      accentDeep = s.brandDeep,
      onAccent = LightColors.button.primary.text,
      cart = DarkColors.button.primary.bg,
      onCart = DarkColors.button.primary.text,
      toggleOn = s.brandPrimary,
      tile = s.surfaceElevated,
      positive = s.status.delivered.fg,
      negative = s.status.cancelled.fg,
      waiting = s.status.awaitingApproval.fg,
    )
  }
}

val PhoneV3Light: PhoneV3Colors = PhoneV3Colors.from(LightColors)

//  شب: کارت هرگز در زمینه گم نمی‌شود (#0B1220 ⇐ #162235 ⇐ #203149) و سبد روی تیره‌ترین سطح است
val PhoneV3Dark: PhoneV3Colors = PhoneV3Colors.from(DarkColors)

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
  /** «نیازِ بررسی» (بنفش) — مثلاً ارسالِ ناموفق */
  val review: PhoneV3Tone,
) {
  companion object {
    /** همان `ShopColors` (دکمه‌ها §۷، وضعیت‌ها §۸) — بی HEX ِ خودش */
    fun from(s: ShopColors): PhoneV3Ui {
      fun t(x: StatusTone) = PhoneV3Tone(x.fg, x.bg)
      return PhoneV3Ui(
        primary = s.button.primary.bg, onPrimary = s.button.primary.text,
        primaryOnDark = DarkColors.button.primary.bg, onPrimaryOnDark = DarkColors.button.primary.text,
        secondaryBg = s.button.secondary.bg, secondaryFg = s.button.secondary.text, secondaryBorder = s.button.secondary.border,
        neutralBg = s.button.neutral.bg, neutralFg = s.button.neutral.text,
        danger = s.button.danger.bg, onDanger = s.button.danger.text,
        disabledBg = s.button.disabled.bg, disabledFg = s.button.disabled.text,
        iconActive = s.iconActive,
        success = t(s.status.delivered), delivery = t(s.status.inDelivery), awaiting = t(s.status.awaitingApproval),
        cancelled = t(s.status.cancelled), preparing = t(s.status.preparing), inactive = t(s.status.inactive),
        review = t(s.status.needsReview),
      )
    }
  }
}

val PhoneV3UiLight: PhoneV3Ui = PhoneV3Ui.from(LightColors)

val PhoneV3UiDark: PhoneV3Ui = PhoneV3Ui.from(DarkColors)

object PhoneV3Palette {
  enum class Hue { BRAND, TEAL, BLUE, ORANGE, PURPLE }

  private fun shop(c: PhoneV3Colors): ShopColors = if (c === PhoneV3Dark) DarkColors else LightColors

  /** آیکنِ رنگیِ کاشی: روز رنگِ برند/نمودار، شب همان خانواده در روشن‌ترش (از پیام‌ها و وضعیت‌ها) */
  fun glyph(c: PhoneV3Colors, hue: Hue): Color {
    val s = shop(c)
    val dark = c === PhoneV3Dark
    return when (hue) {
      Hue.BRAND -> s.brandPrimary
      Hue.TEAL -> s.chart.stock
      Hue.BLUE -> if (dark) s.message.info.icon else s.chart.comparison
      Hue.ORANGE -> if (dark) s.message.warning.icon else s.chart.discount
      Hue.PURPLE -> if (dark) s.status.needsReview.fg else s.chart.costs
    }
  }

  /** سری‌های نمودار §۱۰ (ShopColors.chart): ۱ فروش، ۲ مقایسه، ۳ هزینه، ۴ تخفیف، ۵ افت، ۶ موجودی، ۷ خنثی */
  fun chartSeries(c: PhoneV3Colors): List<Color> = shop(c).chart.series
  fun chartSeries(c: PhoneV3Colors, n: Int): Color = chartSeries(c).let { it[n.mod(it.size)] }
  fun chartSales(c: PhoneV3Colors): Color = shop(c).chart.sales
  fun chartCost(c: PhoneV3Colors): Color = shop(c).chart.costs
  fun chartGrid(c: PhoneV3Colors): Color = shop(c).chart.grid

  /** iconActive و surfaceSelected */
  fun iconActive(c: PhoneV3Colors): Color = shop(c).iconActive
  fun selected(c: PhoneV3Colors): Color = shop(c).surfaceSelected
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
  /** وضعیتِ «در انتظار» (کهربایی): بردِ باز در حسابِ یک نفر */
  val wait: Color,
  val waitTint: Color,
  /** وضعیتِ «در راه» (آبی): سررسیدِ پیشِ رو */
  val info: Color,
  val infoTint: Color,
)

/** رنگ‌های قرض‌داران از همان `ShopColors` و `PhoneV3Colors` — بی HEX ِ خودش */
private fun debtColors(s: ShopColors, p: PhoneV3Colors, dark: Boolean) = PhoneV3DebtColors(
  page = p.bg,
  surface = p.card,
  edge = p.hairline,
  ink = p.ink,
  muted = p.inkMuted,
  icon = p.icon,
  fieldEdge = s.borderInput,
  //  روز: برند (سفید روی #108B81 برای «+» بس است، متنِ تراشه با برندِ پررنگ)؛ شب: جفتِ دکمهٔ اصلیِ شب
  accent = if (dark) p.cart else p.accent,
  onAccent = if (dark) p.onCart else p.onAccent,
  accentActive = if (dark) p.cart else p.accentDeep,
  accentInk = if (dark) s.brandText else p.accent,
  accentText = s.brandText,
  accentSoft = s.surfaceSelected,
  owe = p.negative,
  oweTint = s.status.cancelled.bg,
  settled = p.positive,
  settledTint = s.status.delivered.bg,
  pill = s.status.inactive.bg,
  onPill = s.status.inactive.fg,
  wait = s.status.awaitingApproval.fg,
  waitTint = s.status.awaitingApproval.bg,
  info = s.status.inDelivery.fg,
  infoTint = s.status.inDelivery.bg,
)

val PhoneV3DebtLight: PhoneV3DebtColors = debtColors(LightColors, PhoneV3Light, dark = false)

//  شب: دکمهٔ اصلی جفتِ شبِ سیستم (#19A985 با متنِ #041B15)
val PhoneV3DebtDark: PhoneV3DebtColors = debtColors(DarkColors, PhoneV3Dark, dark = true)

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
