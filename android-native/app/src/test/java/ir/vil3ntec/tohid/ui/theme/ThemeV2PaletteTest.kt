package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  THEME-V2 (۱۴۰۵/۰۸/۰۲) ⇐ ۶۹ p3: چهار تمِ گزیده × روز و شب عینِ جدول، تمِ پیش‌فرض «آسمان»، هفت رنگِ همراه،
 *  متنِ سفید روی کادرِ بزرگ، و این‌که لایه‌های گوشی (`PhoneV3Colors`، `PhoneV3Ui`، `PhoneV3Palette`) از
 *  همان `ShopColors` ِ تمِ جاری ساخته می‌شوند. قاعده‌های کنتراستِ همهٔ شش پالت در `Vill3nPaletteTest`.
 */
class ThemeV2PaletteTest {

  private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  private val all: List<ShopColors> = ShopTheme.entries.flatMap { listOf(shopColors(it, false), shopColors(it, true)) }

  /**
   *  ستون‌ها: primary روشن، دکمهٔ اصلیِ روشن، primary تیره، گرادیانِ روشن، گرادیانِ تیره، زمینه/سطحِ روشن و تیره، متنِ روز، متن و
   *  متنِ کم‌رنگِ شب. ۶۹ p3 (عمدی، صاحب‌کار ۱۴۰۵/۰۸/۲۴: «از رنگ‌های مزخرف توی تم و رنگ و بک‌گراندِ دارک‌مود … همه چی افتضاحه»):
   *  چهار تمِ گزیده از پالتِ برند — «آسمان» (روزش همان THEME-V3 ِ پسندیده؛ شبِ تازهٔ سه‌پله‌ای)، «سرمه‌ای»، «عاجی» و «گرافیت»؛
   *  «اقیانوس» و «غروب» (صورتی) برداشته شدند (`ThemeMigrationTest`). عددها از `scratchpad/p3/design.py`.
   */
  private val owner = mapOf(
    ShopTheme.SKY to listOf(
      "#2563EB", "#1D4ED8", "#6EA3FF", "#2563EB #4F46E5 #7C3AED", "#1E3A8A #312E81 #4C1D95", "#F4F7FC/#FFFFFF", "#0A0E15/#161C27",
      "#0B1324", "#EDF1F7", "#A9B3C2",
    ),
    ShopTheme.NAVY to listOf(
      "#1E3A8A", "#1E3A8A", "#8CA9F0", "#172554 #1E3A8A #25407F", "#0F1A3D #172554 #1E3A8A", "#F3F5F9/#FFFFFF", "#090D18/#131B2D",
      "#0B1426", "#ECF0F8", "#A6B0C3",
    ),
    ShopTheme.IVORY to listOf(
      "#8C5E1A", "#8C5E1A", "#D9B260", "#3A2C1E #5C4322 #8C5E1A", "#2A2016 #3D2F1C #5E4518", "#F7F5F1/#FFFFFF", "#100E0B/#1F1B17",
      "#1C1814", "#F4EFE7", "#B7AEA2",
    ),
    ShopTheme.GRAPHITE to listOf(
      "#334155", "#1F2937", "#CBD2DC", "#111827 #1F2937 #374151", "#23272E #30353E #454C58", "#F5F6F8/#FFFFFF", "#0D0E11/#191C20",
      "#111318", "#F1F2F4", "#AEB2BA",
    ),
  )

  @Test
  fun `چهار تم در روز و شب عینِ جدولِ ۶۹ p3‌اند`() {
    for ((t, w) in owner) {
      val l = shopColors(t, false)
      val d = shopColors(t, true)
      assertEquals("$t primary", w[0], hex(l.brandPrimary))
      assertEquals("$t دکمه", w[1], hex(l.button.primary.bg))
      assertEquals("$t primary تیره", w[2], hex(d.brandPrimary))
      assertEquals("$t گرادیان", w[3], l.heroGradient.joinToString(" ") { hex(it) })
      assertEquals("$t گرادیانِ تیره", w[4], d.heroGradient.joinToString(" ") { hex(it) })
      assertEquals("$t زمینه", w[5], "${hex(l.background)}/${hex(l.surface)}")
      assertEquals("$t زمینهٔ تیره", w[6], "${hex(d.background)}/${hex(d.surface)}")
      assertEquals("$t متنِ روز", w[7], hex(l.textPrimary))
      assertEquals("$t متنِ شب", w[8], hex(d.textPrimary))
      assertEquals("$t متنِ کم‌رنگِ شب", w[9], hex(d.textSecondary))
      assertFalse(l.isDark)
      assertTrue(d.isDark)
      //  «نوارِ پایین و انتخاب‌ها: primaryِ تم»
      assertEquals(l.brandPrimary, l.iconActive)
      assertEquals(d.brandPrimary, d.iconActive)
    }
    assertEquals("هر تم یک ردیف", ShopTheme.entries.toSet(), owner.keys)
    assertEquals("هشت پالتِ جدا", 8, all.toSet().size)
    //  روزِ «آسمان» همان THEME-V3 ِ پسندیده (سطحِ ۲ از فایل)؛ شبِ «آسمان» سه پلهٔ تازه
    assertEquals("#F0F4FC", hex(shopColors(ShopTheme.SKY, false).surfaceElevated))
    assertEquals("#202835", hex(shopColors(ShopTheme.SKY, true).surfaceElevated))
  }

  @Test
  fun `تمِ پیش‌فرض «آسمان» است و شناسهٔ ناشناخته هم «آسمان»`() {
    assertEquals(listOf("sky", "navy", "ivory", "graphite"), ShopTheme.entries.map { it.id })
    assertEquals(ShopTheme.SKY, ShopTheme.of(null))
    assertEquals(ShopTheme.SKY, ShopTheme.of("green"))
    for (t in ShopTheme.entries) assertEquals(t, ShopTheme.of(t.id))
    assertEquals(ShopTheme.SKY, ThemeStore.theme)
    ThemeStore.preview(ShopTheme.IVORY)
    assertEquals(ShopTheme.IVORY, ThemeStore.theme)
    ThemeStore.preview(ShopTheme.SKY)
    assertEquals(ShopTheme.SKY, ThemeStore.theme)
  }

  /**
   *  ۶۸c (عمدی، صاحب‌کار: «رنگ صورتی کار نگیر … رنگ‌های برند بزار، لوکس»): هفت رنگِ همراه همان نشانهٔ هفت خانوادهٔ پالتِ لوکسِ
   *  `SectionColors` (لاجوردی، نیلی، عقیق، زر، زمرد، گرافیت، نفتی) — روز پررنگ‌تر و شب روشن‌تر؛ پیش‌تر یک فهرستِ آسمانی/بنفش/
   *  مرجانی/کهربایی/نعنایی/صورتی/لیمویی برای هر دو.
   */
  @Test
  fun `هفت رنگِ همراه و پس‌زمینهٔ نرمشان`() {
    //  جای ۲ (میان‌برِ «سفارش‌های آنلاین» ِ خانه) نفتی — همان رنگِ بخشِ سفارش؛ عقیقِ خطر جای ۶؛ جاهای ۰ و ۵ دو درخششِ کادرِ
    //  گرادیانیِ خانه: آسمانیِ پیشین و بنفشِ نرم به‌جای صورتی (جدا از جدولِ بخش‌ها، روز و شب یکی)
    val day = listOf("#0EA5E9", "#4F46E5", "#0E7490", "#A87A12", "#05875F", "#8B5CF6", "#B4232F")
    val night = listOf("#0EA5E9", "#9696F0", "#4FB6CC", "#D9B260", "#4DC79A", "#8B5CF6", "#E57B76")
    assertEquals(day, ThemeAccents.ALL.map { hex(it) })
    assertEquals(day, ThemeAccents.DAY.map { hex(it) })
    assertEquals(night, ThemeAccents.NIGHT.map { hex(it) })
    //  «آسمان» همان دو درخششِ پیشین؛ ۶۹ p3: هر تمِ دیگر دو درخششِ کادرِ خانهٔ خودش (جاهای ۰ و ۵)، بقیه در هر چهار تم یکی
    assertEquals(day, shopColors(ShopTheme.SKY, false).accents.map { hex(it) })
    assertEquals(night, shopColors(ShopTheme.SKY, true).accents.map { hex(it) })
    assertEquals(listOf(1, 2, 3, 4, 6), ThemeAccents.SHARED_SLOTS)
    for (c in all) {
      val want = if (c.isDark) night else day
      assertEquals(ThemeAccents.SHARED_SLOTS.map { want[it] }, ThemeAccents.SHARED_SLOTS.map { hex(c.accents[it]) })
      assertEquals(7, c.accents.size)
      assertEquals("چرخشی", c.accent(0), c.accent(7))
      assertEquals("چرخشی", c.accent(6), c.accent(-1))
      for (i in want.indices) {
        val soft = c.accentSoft(i)
        assertEquals(hex(c.accent(i)), hex(soft))
        assertEquals(if (c.isDark) 0.20f else 0.12f, soft.alpha, 0.01f)
      }
    }
  }

  @Test
  fun `متنِ سفید روی کادرِ بزرگ — برچسب‌ها سرِ گرادیان ≥ ۴٫۵، عددِ درشت همه‌جا ≥ ۳`() {
    val bad = mutableListOf<String>()
    for (t in ShopTheme.entries) for (dark in listOf(false, true)) {
      val c = shopColors(t, dark)
      assertTrue("$t پرده ≤ ۰٫۳", c.heroScrim in 0f..0.3f)
      if (dark) assertEquals("$t شب پرده ندارد", 0f, c.heroScrim)
      val stops = c.heroGradient
      stops.forEachIndexed { i, stop ->
        //  همان پردهٔ `V3HeroCard`: از شفاف تا سیاه با آلفای `heroScrim`، هم‌جهتِ گرادیان
        val at = i.toFloat() / (stops.size - 1)
        val ground = Color.Black.copy(alpha = c.heroScrim * at).compositeOver(stop)
        val r = contrast(Color.White, ground)
        val min = if (i == 0) 4.5f else 3f
        if (r < min) bad += "$t${if (dark) "-شب" else ""}: سفید روی ${hex(stop)} = ${"%.2f".format(r)} < $min"
      }
    }
    assertEquals(emptyList<String>(), bad)
  }

  /** رنگِ گرادیانِ کادرِ بزرگ در جای `t` (۰ = سرِ آغاز) با پردهٔ خوانایی، همان‌طور که `V3HeroCard` می‌کشد */
  private fun heroAt(c: ShopColors, t: Float): Color {
    val stops = c.heroGradient
    val span = t * (stops.size - 1)
    val i = span.toInt().coerceAtMost(stops.size - 2)
    val f = span - i
    val a = stops[i]
    val b = stops[i + 1]
    val base = Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f)
    return Color.Black.copy(alpha = c.heroScrim * t).compositeOver(base)
  }

  @Test
  fun `برچسبِ تراشه‌های کادرِ بزرگ همه‌جا AA است — شیشهٔ سفید روی سایهٔ تم`() {
    val bad = mutableListOf<String>()
    for (t in ShopTheme.entries) for (dark in listOf(false, true)) {
      val c = shopColors(t, dark)
      //  تراشه‌ها پایینِ کادر از سرِ آغاز تا حدودِ ۷۵٪ِ پهنا ⇒ t تا ~۰٫۸۲ روی خطِ ۱۳۵°
      var worst = 99f
      var x = 0f
      while (x <= 0.84f) {
        val under = Color.Black.copy(alpha = c.heroChipShade).compositeOver(heroAt(c, x))
        val chip = Color.White.copy(alpha = 0.16f).compositeOver(under)
        worst = minOf(worst, contrast(Color.White, chip))
        x += 0.02f
      }
      if (worst < 4.5f) bad += "${t.id}${if (dark) "-شب" else ""}: ${"%.2f".format(worst)}"
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `برچسب و زیرنویسِ سفیدِ کادرِ بزرگ — AA در هر هشت پالت`() {
    //  ۶۹ p3: انحرافِ «اقیانوس» ِ روز (کفِ ۳٫۵) با برداشتنِ آن تم رفت ⇒ هر هشت پالت ≥ ۴٫۵
    val bad = mutableListOf<String>()
    for (t in ShopTheme.entries) for (dark in listOf(false, true)) {
      val c = shopColors(t, dark)
      val min = 4.5f
      var worst = 99f
      var x = 0f
      while (x <= 0.66f) { worst = minOf(worst, contrast(Color.White, heroAt(c, x))); x += 0.02f }
      if (worst < min) bad += "${t.id}${if (dark) "-شب" else ""}: ${"%.2f".format(worst)} < $min"
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `«در انتظارِ تأیید» بنفش و هشدار کهربایی — در هر هشت پالت`() {
    for (c in all) {
      assertEquals(c.status.needsReview, c.status.awaitingApproval)
      assertTrue(c.status.warning != c.status.awaitingApproval)
      assertEquals(c.status.warning.fg, c.warning)
      assertTrue("سایهٔ تراشه ۰ تا ۰٫۳", c.heroChipShade in 0f..0.3f)
    }
    assertEquals("#6D28D9", hex(LightColors.status.awaitingApproval.fg))
    assertEquals("#92400E", hex(LightColors.status.warning.fg))
    assertEquals("#C4B5FD", hex(DarkColors.status.awaitingApproval.fg))
    assertEquals("#FCD34D", hex(DarkColors.status.warning.fg))
  }

  @Test
  fun `لایه‌های گوشی از ShopColors ِ همان تم ساخته می‌شوند`() {
    for (s in all) {
      val p = PhoneV3Colors.from(s)
      assertSame(s, p.source)
      assertEquals(s.background, p.bg)
      assertEquals(s.surface, p.card)
      assertEquals(s.textPrimary, p.ink)
      //  ۶۹ p3 (عمدی): شب پُرِ دکمهٔ اصلی — سرِ گرادیانِ شب روی کارتِ شب گم می‌شد؛ روز همان سرِ گرادیان
      assertEquals(if (s.isDark) s.button.primary.bg else s.heroGradient.first(), p.accent)
      assertTrue("accent روی کارت ≥ ۳", contrast(p.accent, p.card) >= 3f)
      assertEquals(Color.White, p.onAccent)
      assertEquals(s.button.primary.bg, p.cart)
      assertEquals(s.button.primary.text, p.onCart)
      assertEquals(s.isDark, p.source.isDark)
      val ui = PhoneV3Ui.from(s)
      assertEquals(s.button.primary.bg, ui.primary)
      assertEquals(s.button.primary.bg, ui.primaryOnDark)
      assertEquals(s.brandPrimary, PhoneV3Palette.glyph(p, V3Hue.BRAND))
      assertEquals(s.accent(4), PhoneV3Palette.glyph(p, V3Hue.MINT))
      assertEquals(s.accentSoft(4), PhoneV3Palette.soft(p, V3Hue.MINT))
      assertEquals(s.chart.series, PhoneV3Palette.chartSeries(p))
      assertEquals(s.surfaceSelected, PhoneV3Palette.selected(p))
    }
    assertSame(PhoneV3Light, PhoneV3.forShop(LightColors))
    assertSame(PhoneV3Dark, PhoneV3.forShop(DarkColors))
    val navy = shopColors(ShopTheme.NAVY, false)
    assertSame("یک خانه حافظه", PhoneV3.forShop(navy), PhoneV3.forShop(navy))
    assertSame(navy, PhoneV3.forShop(navy).source)
  }

  @Test
  fun `هر بخش یک رنگِ همراهِ ثابت دارد`() {
    //  ۶۸c (عمدی): «فروش برند، دریافت زمردی، انبار گرافیت، تاریخچه نیلی، قرض‌داران عقیق، مصارف زر، گزارش لاجوردی» — بی صورتی
    val want = mapOf(
      "sale" to V3Hue.BRAND, "receipts" to V3Hue.MINT, "warehouse" to V3Hue.SLATE, "history" to V3Hue.VIOLET,
      "debtors" to V3Hue.CORAL, "expenses" to V3Hue.AMBER, "reports" to V3Hue.SKY,
    )
    for ((id, hue) in want) assertEquals(id, hue, V3Modules.hueOf(id))
    //  هر شناسهٔ این جدول در ثبتِ ماژول‌ها هم ورودی دارد (آیکنِ واقعیِ همان بخش)
    val ids = V3Modules.ALL.flatMap { m -> m.entries.map { it.id } }.toSet()
    assertEquals(emptySet<String>(), want.keys - ids)
    //  میان‌برهای اصلی رنگِ تکراری ندارند
    assertEquals(want.size, want.values.toSet().size)
  }
}
