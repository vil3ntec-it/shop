package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ShopColors
import ir.vil3ntec.tohid.ui.theme.tabular
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.SettingsHues

/*
 *  ═══════════════════════════════════════════════════════════════════════════
 *  سیستمِ طراحیِ **کامپیوتر** (ویندوز/مک/لینوکس) — یک منبع، فقط `Platform.DESKTOP`
 *  (`claude/e-desk-d1`، خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۴: «موبایل با کامپیوتر فرق داره؛ باید جوری درست کنی که رو کامپیوتر
 *  خونده بشه … خیلی حرفه‌ای و شیک، با بهترین رنگ‌بندیِ لازمِ هر بخش»).
 *
 *  • فاصله، گِردی، اندازه و قلم برای فاصلهٔ چشمِ پشتِ میز و ماوس — نه اندازهٔ انگشت.
 *  • رنگ **هیچ HEX ِ تازه‌ای ندارد**: سطح و متن از `ShopColors` ِ تمِ جاری (روز/شب، سه تم)، و رنگِ هر بخش از همان
 *    نقشه‌های یکتای گوشی (`PhoneV3Palette.Hue` ⇐ `V3Modules.hueOf` ⇐ `SettingsHues`) — [DeskHues]. فقط «پله»های
 *    خوانا (متن ≥ ۴٫۵:۱، آیکن ≥ ۳:۱) از همان رنگ ساخته می‌شوند.
 *  • گوشی و آیفون این‌ها را نمی‌خوانند؛ صفحهٔ کامپیوتری (`LocalDeskLayout`) فقط از همین‌ها می‌خواند — عدد در صفحه نه.
 *  ⚠️ API پایدار است (عاملِ صفحه‌های داده رویش می‌سازند): نام عوض نمی‌شود، فقط اضافه می‌شود.
 *  ═══════════════════════════════════════════════════════════════════════════
 */

/** فاصله‌ها — پلهٔ ۴: ۴، ۸، ۱۲، ۱۶، ۲۴، ۳۲ و بس */
object DeskSpace {
  val x1 = 4.dp
  val x2 = 8.dp
  val x3 = 12.dp
  val x4 = 16.dp
  val x6 = 24.dp
  val x8 = 32.dp

  /** کنارهٔ صفحه (محتوای میانِ منو و لبهٔ پنجره) */
  val page = 24.dp
  /** درونِ کارت */
  val card = 20.dp
  /** فاصلهٔ میانِ کارت‌های یک ردیف و میانِ ردیف‌های کارت */
  val gap = 16.dp
}

/** گِردی — کارت ۱۲، کنترل ۸، کاشیِ آیکن ۸، قرص کامل */
object DeskRadius {
  val card = 12.dp
  val control = 8.dp
  val tile = 8.dp

  val cardShape = RoundedCornerShape(card)
  val controlShape = RoundedCornerShape(control)
  val tileShape = RoundedCornerShape(tile)
  val pillShape = RoundedCornerShape(999.dp)
}

/** اندازه‌های پوسته و اجزا */
object DeskSize {
  /** منوی کناریِ باز (همیشه با نوشته) */
  val sidebar = 240.dp
  /** منوی جمع‌شده: فقط آیکن */
  val sidebarCompact = 72.dp
  /** پنجرهٔ باریک‌تر از این ⇒ منو خودبه‌خود جمع می‌شود (انتخابِ کاربر دست نمی‌خورد) */
  val sidebarAutoCompactBelow = 1100.dp
  val header = 56.dp
  val statusBar = 28.dp
  /** ردیفِ منو */
  val navItem = 36.dp
  /** کاشیِ آیکنِ منو */
  val navTile = 26.dp
  val navIcon = 16.dp
  /** دکمه و کادرِ معمولی */
  val control = 36.dp
  val controlSmall = 30.dp
  /** کاشیِ آیکنِ کارت (KPI، کارتِ بخش) */
  val tile = 36.dp
  val tileLarge = 40.dp
  val tileIcon = 20.dp
  /** ردیفِ جدول و سرِ جدول */
  val tableRow = 44.dp
  val tableHead = 36.dp
  /** کادرِ جست‌وجوی سربرگ */
  val searchMax = 520.dp
  /** پهنای بیشترِ محتوای داشبورد روی مانیتورِ خیلی پهن */
  val contentMax = 1600.dp
  /** ستونِ دسته‌های تنظیمات و پهنای بیشترِ صفحهٔ بخش */
  val settingsList = 300.dp
  val settingsContentMax = 760.dp
  val hairline = 1.dp
  val focusRing = 2.dp
  /** نوارِ رنگیِ کنارِ ردیفِ فعالِ منو و لبهٔ بالای کارتِ KPI */
  val accentBar = 3.dp
}

/**
 *  قلم‌ها برای فاصلهٔ میز — اندازهٔ مطلق (نه ضریبِ ۰٫۹۳ ِ `TohidTheme`)، همان وزیرمتن و همان رقمِ انتخابیِ دکان
 *  (`LocaleChoice`، از `MaterialTheme.typography`). عدد همیشه با رقمِ هم‌پهنا ([kpi]، [table]، [number]).
 */
object DeskType {
  private val base: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium

  private fun TextStyle.at(size: Int, weight: FontWeight, line: Int) = copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

  /** عنوانِ صفحه (سربرگ) */
  val pageTitle: TextStyle @Composable get() = base.at(22, FontWeight.Bold, 30)
  /** عنوانِ کارت/بخش */
  val sectionTitle: TextStyle @Composable get() = base.at(16, FontWeight.SemiBold, 24)
  val body: TextStyle @Composable get() = base.at(14, FontWeight.Medium, 22)
  val bodyStrong: TextStyle @Composable get() = base.at(14, FontWeight.SemiBold, 22)
  /** متنِ دوم: زیرعنوان، توضیحِ یک‌خطی، نوارِ وضعیت */
  val secondary: TextStyle @Composable get() = base.at(13, FontWeight.Medium, 20)
  /** برچسبِ کوچک: عنوانِ گروهِ منو، محورِ نمودار */
  val caption: TextStyle @Composable get() = base.at(12, FontWeight.SemiBold, 16)
  /** برچسبِ کنترل و ستون */
  val label: TextStyle @Composable get() = base.at(13, FontWeight.SemiBold, 18)
  /** ردیفِ منو */
  val navItem: TextStyle @Composable get() = base.at(14, FontWeight.Medium, 20)
  /** خانهٔ جدول (رقمِ هم‌پهنا) */
  val table: TextStyle @Composable get() = base.at(14, FontWeight.Medium, 20).tabular()
  val tableHead: TextStyle @Composable get() = base.at(13, FontWeight.SemiBold, 18)
  /** عددِ معمولی در متن (رقمِ هم‌پهنا) */
  val number: TextStyle @Composable get() = base.at(14, FontWeight.SemiBold, 20).tabular()
  /** عددِ درشتِ کارتِ KPI */
  val kpi: TextStyle @Composable get() = base.at(28, FontWeight.Bold, 36).tabular()
  /** عددِ درشتِ دوم (کارتِ باریک، جمعِ جدول) */
  val kpiSmall: TextStyle @Composable get() = base.at(20, FontWeight.Bold, 28).tabular()
  /** شمارِ کوچکِ نشان */
  val badge: TextStyle @Composable get() = base.at(11, FontWeight.Bold, 14).tabular()
  /** میان‌برِ صفحه‌کلید («Ctrl+2») */
  val shortcut: TextStyle @Composable get() = base.at(12, FontWeight.SemiBold, 16)
}

/**
 *  رنگ‌های سطح و متنِ کامپیوتر — همه از `ShopColors` ِ همان تم و همان روز/شب ([from])؛ هیچ HEX ِ تازه نیست.
 *  پنجره: زمینهٔ کمی رنگی ([app])، پوسته (منو، سربرگ، نوارِ وضعیت) و کارت‌ها روی سطحِ ۱ با خطِ مویی و سایهٔ خیلی نرم.
 */
@Immutable
data class DeskColors(
  /** زمینهٔ پنجره، زیرِ کارت‌ها */
  val app: Color,
  /** منوی کناری، سربرگ و نوارِ وضعیت */
  val chrome: Color,
  /** کارت */
  val surface: Color,
  /** سرِ جدول، درونِ کارت، شیارِ کنترلِ قطعه‌ای */
  val surfaceAlt: Color,
  /** خطِ دورِ کارت و مرزِ پوسته */
  val hairline: Color,
  /** خطِ میانِ ردیف‌ها */
  val divider: Color,
  val ink: Color,
  /** متنِ دوم — روی [surface] و [app] ≥ ۴٫۵:۱ */
  val inkSecondary: Color,
  /** کم‌رنگ‌ترین — فقط راهنمای درونِ کادر و نوشتهٔ درشت */
  val inkMuted: Color,
  val icon: Color,
  /** زیرِ ماوس */
  val hover: Color,
  /** فشرده */
  val pressed: Color,
  /** انتخاب‌شده (بی رنگِ بخش) */
  val selected: Color,
  /** حلقهٔ تمرکزِ صفحه‌کلید */
  val focus: Color,
  /** رنگِ سایهٔ کارت (Compose در آلفای سایه ضرب می‌کند) */
  val shadow: Color,
  /** برند: آیکنِ فعال، نشانگر */
  val brand: Color,
  /** برند برای متنِ کوچک و پیوند (≥ ۴٫۵:۱) */
  val brandInk: Color,
  /** دکمهٔ اصلی */
  val primary: Color,
  val onPrimary: Color,
  /** سبز/سرخ/کهربایی/آبی برای متن (≥ ۴٫۵:۱) و زمینهٔ نرمِ همان */
  val positive: Color,
  val positiveSoft: Color,
  val negative: Color,
  val negativeSoft: Color,
  val warning: Color,
  val warningSoft: Color,
  val info: Color,
  val infoSoft: Color,
  /** نشانِ هشدار (شمارِ منو): زمینه و متن */
  val alert: Color,
  val onAlert: Color,
  /** خطِ شبکهٔ نمودار */
  val grid: Color,
  val isDark: Boolean,
  val source: ShopColors,
  /** نشانِ کوچکِ «کاری مانده» (نقطهٔ نسیه، قرضِ دیرکرده) — غیرِ متنی، ≥ ۳:۱ روی کارت */
  val attention: Color = source.message.warning.icon,
) {
  companion object {
    fun from(s: ShopColors): DeskColors = DeskColors(
      app = s.background,
      chrome = s.surface,
      surface = s.surface,
      surfaceAlt = s.surfaceElevated,
      hairline = s.borderCard,
      divider = s.divider,
      ink = s.textPrimary,
      inkSecondary = s.textSecondary,
      inkMuted = s.textMuted,
      icon = s.icon,
      hover = lerp(s.surface, s.surfaceElevated, if (s.isDark) 0.75f else 0.9f),
      pressed = lerp(s.surfaceElevated, s.borderCard, 0.35f),
      selected = s.surfaceSelected,
      focus = s.focus,
      shadow = s.shadow,
      brand = s.brandPrimary,
      brandInk = s.brandText,
      primary = s.button.primary.bg,
      onPrimary = s.button.primary.text,
      positive = s.status.delivered.fg,
      positiveSoft = s.status.delivered.bg,
      negative = s.status.cancelled.fg,
      negativeSoft = s.status.cancelled.bg,
      warning = s.status.warning.fg,
      warningSoft = s.status.warning.bg,
      info = s.status.inDelivery.fg,
      infoSoft = s.status.inDelivery.bg,
      alert = s.button.danger.bg,
      onAlert = s.button.danger.text,
      grid = s.chart.grid,
      isDark = s.isDark,
      source = s,
    )
  }
}

/** دسترسی به رنگ‌ها و پله‌های رنگِ بخش در صفحه‌های کامپیوتری */
object Desk {
  private var lastShop: ShopColors? = null
  private var lastColors: DeskColors? = null

  /** رنگ‌های تمِ جاری — یک خانه حافظه (در گذرِ ۲۵۰ms ِ تم هر قاب تازه است، بعدش همان) */
  val colors: DeskColors
    @Composable get() = forShop(Shop.colors)

  fun forShop(s: ShopColors): DeskColors {
    val hit = lastColors
    if (hit != null && lastShop === s) return hit
    return DeskColors.from(s).also { lastColors = it; lastShop = s }
  }
}

/**
 *  یک رنگِ بخش در پله‌های خوانا — همه از **یک** رنگِ همراه (`ShopColors.accent` با شمارهٔ `PhoneV3Palette.accentIndex`)
 *  یا برند (`BRAND`):
 *  @property solid خودِ رنگ: نوارِ کنارِ ردیفِ فعال، لبهٔ کارت، خطِ نمودار
 *  @property tile زمینهٔ نرمِ کاشیِ آیکن و ردیفِ فعال (روی [DeskColors.surface] نشانده شده، نه شفاف)
 *  @property glyph آیکن روی [tile] — ≥ ۳:۱ (WCAG 1.4.11)
 *  @property ink متن روی [DeskColors.surface] **و** روی [tile] — ≥ ۴٫۵:۱
 *  @property onSolid متن/آیکن روی [solid]
 */
@Immutable
data class DeskHue(
  val hue: V3Hue,
  val solid: Color,
  val tile: Color,
  val glyph: Color,
  val ink: Color,
  val onSolid: Color,
)

/**
 *  رنگِ هر بخش روی کامپیوتر — **فقط نگاشت** به نقشه‌های یکتای گوشی، نه پالتِ دوم:
 *  • بخش‌ها (ورودیِ ماژول: `debtors`، `warehouse`، …، و شناسه‌های منوی کناری) ⇐ `V3Modules.hueOf`
 *  • دسته و بخشِ تنظیمات (`SettingsLayout.SALES`، `pay`، …) ⇐ `SettingsHues`
 *  • گروه‌های «بیشتر» ⇐ رنگِ نخستین بخشِ همان گروه.
 *  شناسهٔ ناشناخته ⇒ برند.
 */
object DeskHues {
  /** شناسه‌های منوی کامپیوتر که ورودیِ ماژول نیستند ⇒ ورودیِ هم‌معنا */
  private val ALIAS = mapOf(
    "dashboard" to "home",
    "fulfil" to "orders",
  )

  /** رنگِ یک بخش (شناسهٔ ماژول/منو) یا یک دسته/بخشِ تنظیمات */
  fun of(section: String): V3Hue {
    val id = section.removePrefix("off:").removePrefix("settings:")
    if (id == "settings") return SettingsHues.ROOT
    if (id == "app") return ir.vil3ntec.tohid.ui.screens.MoreLayout.APP_HUE // همان ردیفِ «نسخه و به‌روزرسانی» ِ «بیشتر»
    V3Modules.hueOf(ALIAS[id] ?: id)?.let { return it }
    if (SettingsLayout.groupOf(id) != null || SettingsHues.GROUP.containsKey(id) || SettingsHues.PART.containsKey(id)) return SettingsHues.of(id)
    return V3Hue.BRAND
  }

  /** رنگِ یک گروهِ «بیشتر» — همان رنگِ نخستین بخشِ ثابتِ گروه */
  fun group(g: MoreGroup): V3Hue = when (g) {
    MoreGroup.SALES -> of("history")
    MoreGroup.STOCK -> of("warehouse")
    MoreGroup.MONEY -> of("reports")
    MoreGroup.SHOP -> of("admin")
    MoreGroup.ACCOUNT -> of("myplan")
  }

  /** پله‌های خوانای یک رنگ برای تمِ جاری */
  @Composable
  fun tone(hue: V3Hue): DeskHue {
    val s = Shop.colors
    return remember(s, hue) { tone(s, hue) }
  }

  @Composable
  fun tone(section: String): DeskHue = tone(of(section))

  /** همان، خالص (آزمونِ تضاد در همهٔ تم‌ها) */
  fun tone(s: ShopColors, hue: V3Hue): DeskHue {
    val solid = PhoneV3Palette.accentIndex(hue)?.let { s.accent(it) } ?: s.brandPrimary
    val surface = s.surface
    val tile = solid.copy(alpha = if (s.isDark) 0.22f else 0.12f).compositeOver(surface)
    //  به سوی رنگِ متنِ اصلی (روز تیره، شب روشن) تا آستانه — هر دو زمینه
    val toward = s.textPrimary
    val glyph = step(solid, toward) { minOf(DeskContrast.ratio(it, tile), DeskContrast.ratio(it, surface)) >= 3.0 }
    val ink = step(solid, toward) { minOf(DeskContrast.ratio(it, tile), DeskContrast.ratio(it, surface)) >= 4.5 }
    //  روی خودِ رنگ: سفید یا تیره‌ترین متنِ تم — هر کدام تضادِ بیشتری دارد
    val light = Color.White
    val dark = if (s.isDark) s.inverse else s.textPrimary
    val onSolid = if (DeskContrast.ratio(light, solid) >= DeskContrast.ratio(dark, solid)) light else dark
    return DeskHue(hue, solid, tile, glyph, ink, onSolid)
  }

  /** کمترین آمیزهٔ [from] به سوی [to] (گامِ ۵٪) که [ok] بگوید؛ هیچ ⇒ خودِ [to] */
  private inline fun step(from: Color, to: Color, ok: (Color) -> Boolean): Color {
    var t = 0f
    while (t <= 1f) {
      val c = lerp(from, to, t)
      if (ok(c)) return c
      t += 0.05f
    }
    return to
  }
}

/** نسبتِ تضادِ WCAG 2.1 — همان فرمولِ آزمون‌های تم (`ThemeContrastTest`) */
object DeskContrast {
  fun ratio(a: Color, b: Color): Double {
    val la = a.compositeOver(Color.Black).luminance().toDouble()
    val lb = b.compositeOver(Color.Black).luminance().toDouble()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
  }
}
