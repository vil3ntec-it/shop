package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  توکن‌های برگهٔ تیرهٔ گوشی (`PhoneSheet.kt`): کنتراستِ متن ≥ ۴٫۵:۱ (WCAG 1.4.3)
 *  و شکلِ غیرمتنی (دایرهٔ شمار، قرصِ تبِ فعال، نوارِ پیشرفت) ≥ ۳:۱ (1.4.11)،
 *  روز و شب؛ و انتخابِ تم از روشنیِ زمینهٔ `Shop.colors`.
 */
class PhoneSheetContrastTest {

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  @Test
  fun `تم از روشنیِ زمینه انتخاب می‌شود`() {
    assertEquals(LightSheet, PhoneSheet.of(LightColors.bg))
    assertEquals(DarkSheet, PhoneSheet.of(DarkColors.bg))
  }

  @Test
  fun `برگه و قرص در روز و شب خوانا هستند`() {
    val bad = mutableListOf<String>()
    for ((theme, s, c) in listOf(Triple("روز", LightSheet, LightColors), Triple("شب", DarkSheet, DarkColors))) {
      listOf(
        "onSheet روی sheet" to contrast(s.onSheet, s.sheet),
        "onSheetMuted روی sheet" to contrast(s.onSheetMuted, s.sheet),
        "onSheetMuted روی raised" to contrast(s.onSheetMuted, s.raised.compositeOver(s.sheet)),
        "accent (متن) روی sheet" to contrast(s.accent, s.sheet),
        "onAccent روی accent" to contrast(s.onAccent, s.accent),
        "onNavPill روی navPill" to contrast(s.onNavPill, s.navPill),
        "sheet (حرفِ اولِ عکس) روی onSheet" to contrast(s.sheet, s.onSheet),
      ).filter { it.second < 4.5f }.forEach { bad += "$theme: ${it.first} = ${"%.2f".format(it.second)}" }
      listOf(
        "navPill روی نوار" to contrast(s.navPill, c.surfaceSolid),
        "accent روی sheet" to contrast(s.accent, s.sheet),
      ).filter { it.second < 3f }.forEach { bad += "$theme: ${it.first} = ${"%.2f".format(it.second)}" }
    }
    assertTrue("کنتراستِ کم — $bad", bad.isEmpty())
  }
}
