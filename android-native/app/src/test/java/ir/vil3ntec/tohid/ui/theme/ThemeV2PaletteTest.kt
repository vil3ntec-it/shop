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
 *  THEME-V2 (۱۴۰۵/۰۸/۰۲): سه تم × روز و شب عینِ جدولِ صاحب‌کار، تمِ پیش‌فرض «آسمان»، هفت رنگِ همراه،
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

  /** ستون‌های جدولِ فایل: primary روشن، دکمهٔ اصلیِ روشن، primary تیره، گرادیانِ روشن، گرادیانِ تیره، زمینه/سطحِ روشن و تیره */
  private val owner = mapOf(
    ShopTheme.SKY to listOf(
      "#2563EB", "#1D4ED8", "#60A5FA", "#2563EB #4F46E5 #7C3AED", "#1E3A8A #312E81 #4C1D95", "#F5F8FF/#FFFFFF", "#0B1020/#121A2E",
    ),
    ShopTheme.OCEAN to listOf(
      "#0284C7", "#0369A1", "#38BDF8", "#0369A1 #0EA5E9 #22D3EE", "#0C4A6E #075985 #155E75", "#F2F9FC/#FFFFFF", "#07141C/#0F2230",
    ),
    ShopTheme.DUSK to listOf(
      "#4F46E5", "#4338CA", "#818CF8", "#4F46E5 #DB2777 #F97316", "#312E81 #831843 #9A3412", "#F7F6FF/#FFFFFF", "#100E1F/#1B1830",
    ),
  )

  @Test
  fun `سه تم در روز و شب عینِ جدولِ THEME-V2‌اند`() {
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
      assertEquals("#0F172A", hex(l.textPrimary))
      assertEquals("#E6ECFF", hex(d.textPrimary))
      assertEquals("#94A3B8", hex(d.textSecondary))
      assertFalse(l.isDark)
      assertTrue(d.isDark)
      //  «نوارِ پایین و انتخاب‌ها: primaryِ تم»
      assertEquals(l.brandPrimary, l.iconActive)
      assertEquals(d.brandPrimary, d.iconActive)
    }
    assertEquals("شش پالتِ جدا", 6, all.toSet().size)
    //  سطحِ ۲ِ «آسمان» از فایل؛ دو تمِ دیگر مشتق‌اند
    assertEquals("#EEF3FF", hex(shopColors(ShopTheme.SKY, false).surfaceElevated))
    assertEquals("#1A2440", hex(shopColors(ShopTheme.SKY, true).surfaceElevated))
  }

  @Test
  fun `تمِ پیش‌فرض «آسمان» است و شناسهٔ ناشناخته هم «آسمان»`() {
    assertEquals(listOf("sky", "ocean", "dusk"), ShopTheme.entries.map { it.id })
    assertEquals(ShopTheme.SKY, ShopTheme.of(null))
    assertEquals(ShopTheme.SKY, ShopTheme.of("green"))
    assertEquals(ShopTheme.OCEAN, ShopTheme.of("ocean"))
    assertEquals(ShopTheme.DUSK, ShopTheme.of("dusk"))
    assertEquals(ShopTheme.SKY, ThemeStore.theme)
    ThemeStore.preview(ShopTheme.DUSK)
    assertEquals(ShopTheme.DUSK, ThemeStore.theme)
    ThemeStore.preview(ShopTheme.SKY)
    assertEquals(ShopTheme.SKY, ThemeStore.theme)
  }

  @Test
  fun `هفت رنگِ همراه و پس‌زمینهٔ نرمشان`() {
    val want = listOf("#0EA5E9", "#8B5CF6", "#F97362", "#F59E0B", "#14B8A6", "#EC4899", "#84CC16")
    assertEquals(want, ThemeAccents.ALL.map { hex(it) })
    for (c in all) {
      assertEquals(want, c.accents.map { hex(it) })
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
  fun `برچسب و زیرنویسِ سفیدِ کادرِ بزرگ — AA جز «اقیانوس» ِ روز (انحرافِ ثبت‌شده)`() {
    //  «اقیانوس» ِ روز: گرادیانِ فایل (#0EA5E9 ⇐ #22D3EE) برای متنِ سفیدِ کوچک روشن است؛ با پردهٔ ۰٫۳ حدودِ
    //  ۳٫۷:۱ می‌ماند (عددِ درشت و برچسبِ سرِ کادر AA‌اند). رساندنش به ۴٫۵ پردهٔ ~۰٫۵ می‌خواهد که فیروزه‌ایِ
    //  کادر را تیره می‌کند — تصمیمِ صاحب‌کار؛ این‌جا فقط کفِ ۳٫۵ سنجیده می‌شود تا بدتر نشود.
    val bad = mutableListOf<String>()
    for (t in ShopTheme.entries) for (dark in listOf(false, true)) {
      val c = shopColors(t, dark)
      val min = if (t == ShopTheme.OCEAN && !dark) 3.5f else 4.5f
      var worst = 99f
      var x = 0f
      while (x <= 0.66f) { worst = minOf(worst, contrast(Color.White, heroAt(c, x))); x += 0.02f }
      if (worst < min) bad += "${t.id}${if (dark) "-شب" else ""}: ${"%.2f".format(worst)} < $min"
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `«در انتظارِ تأیید» بنفش و هشدار کهربایی — در هر شش پالت`() {
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
      assertEquals(s.heroGradient.first(), p.accent)
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
    val ocean = shopColors(ShopTheme.OCEAN, false)
    assertSame("یک خانه حافظه", PhoneV3.forShop(ocean), PhoneV3.forShop(ocean))
    assertSame(ocean, PhoneV3.forShop(ocean).source)
  }

  @Test
  fun `هر بخش یک رنگِ همراهِ ثابت دارد`() {
    //  «فروش آبی، دریافت نعنایی، ورودِ کالا کهربایی، تاریخچه بنفش، مشتری‌ها مرجانی، مصارف صورتی، گزارش آسمانی»
    val want = mapOf(
      "sale" to V3Hue.BRAND, "receipts" to V3Hue.MINT, "warehouse" to V3Hue.AMBER, "history" to V3Hue.VIOLET,
      "debtors" to V3Hue.CORAL, "expenses" to V3Hue.PINK, "reports" to V3Hue.SKY,
    )
    for ((id, hue) in want) assertEquals(id, hue, V3Modules.hueOf(id))
    //  هر شناسهٔ این جدول در ثبتِ ماژول‌ها هم ورودی دارد (آیکنِ واقعیِ همان بخش)
    val ids = V3Modules.ALL.flatMap { m -> m.entries.map { it.id } }.toSet()
    assertEquals(emptySet<String>(), want.keys - ids)
    //  میان‌برهای اصلی رنگِ تکراری ندارند
    assertEquals(want.size, want.values.toSet().size)
  }
}
