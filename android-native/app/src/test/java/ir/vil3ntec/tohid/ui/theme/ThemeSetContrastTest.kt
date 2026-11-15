package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ۶۹ p3 — چهار تمِ گزیده × روز و شب (صاحب‌کار: «رنگ و بک‌گراندِ دارک‌مود … همه چی افتضاحه»). جدولِ پایه (متن ≥ ۴٫۵، آیکن ≥ ۳) در
 *  `Vill3nPaletteTest` است؛ این‌جا ساختارِ سطح‌ها و قاعده‌های تازه:
 *   • متنِ اصلی روی زمینه و کارت AAA (≥ ۷:۱)؛
 *   • شب: زمینهٔ نزدیک‌به‌سیاه و سه پلهٔ بلندی (زمینه ⇐ کارت/نوار/برگه ⇐ سطحِ درونی/کادر/دکمهٔ خنثی) که هر پله دست‌کم ۱٫۱۲:۱
 *     روشن‌تر است — نه سیاهِ تخت که کارت در آن گم شود؛ روز: کارتِ سفید روشن‌تر از زمینه؛
 *   • خطِ مویی دیدنی (روی کارت ≥ ۱٫۲۵:۱ و روی زمینه ≥ ۱٫۲۴:۱ — همان کفِ `BackOfficeContrastTest` برای هر هشت پالت؛ کادرِ ورودی روی پُرِ خودش ≥ ۱٫۵:۱)؛
 *   • پُرِ دکمهٔ اصلی و خطر و حلقهٔ تمرکز روی زمینه و کارت ≥ ۳:۱ (WCAG 1.4.11)؛
 *   • طرحِ متریال (صفحه‌های قدیمی): `primary` هم پُر است هم نوشته ⇒ نوشتهٔ رویش و خودش روی کارت ≥ ۴٫۵.
 */
class ThemeSetContrastTest {

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  private val palettes: List<Pair<String, ShopColors>> =
    ShopTheme.entries.flatMap { t -> listOf("${t.id}-روز" to shopColors(t, false), "${t.id}-شب" to shopColors(t, true)) }

  @Test
  fun `چهار تم، هشت پالت`() {
    assertEquals(4, ShopTheme.entries.size)
    assertEquals(8, palettes.map { it.second }.toSet().size)
  }

  @Test
  fun `متنِ اصلی AAA و سطح‌ها پله‌پله — روز و شب`() {
    val bad = mutableListOf<String>()
    fun need(name: String, what: String, r: Float, min: Float) { if (r < min) bad += "$name: $what = ${"%.3f".format(r)} < $min" }
    for ((name, c) in palettes) {
      need(name, "textPrimary/background", contrast(c.textPrimary, c.background), 7f)
      need(name, "textPrimary/surface", contrast(c.textPrimary, c.surface), 7f)
      need(name, "textPrimary/surfaceElevated", contrast(c.textPrimary, c.surfaceElevated), 7f)
      need(name, "hairline/surface", contrast(c.borderCard, c.surface), 1.25f)
      need(name, "hairline/background", contrast(c.borderCard, c.background), 1.24f)
      val inputFill = PhoneV3Roles.inputFill(PhoneV3Colors.from(c))
      need(name, "borderInput/inputFill", contrast(c.borderInput, inputFill), 1.5f)
      need(name, "textPrimary/inputFill", contrast(c.textPrimary, inputFill), 7f)
      if (c.isDark) {
        //  نزدیک‌به‌سیاه، نه سیاهِ تخت و نه سرمه‌ایِ روشن
        val l = c.background.luminance()
        if (l !in 0.002f..0.008f) bad += "$name: روشناییِ زمینه $l بیرون از نزدیک‌به‌سیاه"
        assertTrue("$name پله‌ها رو به بالا", c.background.luminance() < c.surface.luminance() && c.surface.luminance() < c.surfaceElevated.luminance())
        need(name, "پلهٔ زمینه⇐کارت", contrast(c.surface, c.background), 1.12f)
        need(name, "پلهٔ کارت⇐سطحِ درونی", contrast(c.surfaceElevated, c.surface), 1.12f)
        //  گودیِ نوارِ کنار/شیارِ قطعه‌ای تیره‌تر از زمینه؛ سطحِ وارونه (سبد، پرده) تیره‌ترین
        assertTrue("$name backgroundSubtle", c.backgroundSubtle.luminance() < c.background.luminance())
        assertTrue("$name inverse", c.inverse.luminance() <= c.background.luminance())
      } else {
        assertTrue("$name کارت روشن‌تر از زمینه", c.surface.luminance() > c.background.luminance())
        need(name, "پلهٔ زمینه⇐کارت", contrast(c.surface, c.background), 1.04f)
      }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }

  @Test
  fun `پُرِ دکمهٔ اصلی و خطر و حلقهٔ تمرکز روی زمینه و کارت ≥ ۳`() {
    val bad = mutableListOf<String>()
    for ((name, c) in palettes) for ((g, ground) in listOf("background" to c.background, "surface" to c.surface)) {
      for ((what, fg) in listOf("primary" to c.button.primary.bg, "danger" to c.button.danger.bg, "focus" to c.focus)) {
        val r = contrast(fg, ground)
        if (r < 3f) bad += "$name: $what/$g = ${"%.2f".format(r)}"
      }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }

  @Test
  fun `طرحِ متریال — primary هم پُر و هم نوشته، هر دو AA`() {
    val bad = mutableListOf<String>()
    for ((name, c) in palettes) {
      val m = materialScheme(c)
      if (contrast(m.onPrimary, m.primary) < 4.5f) bad += "$name: onPrimary/primary"
      if (contrast(m.primary, c.surface) < 4.5f) bad += "$name: primary به‌عنوانِ نوشته روی کارت"
      if (contrast(m.onSecondary, m.secondary) < 4.5f) bad += "$name: onSecondary/secondary"
      if (contrast(m.onSurface, m.surface) < 7f) bad += "$name: onSurface/surface"
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }
}
