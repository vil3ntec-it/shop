package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  رنگ‌های قرض‌دارانِ گوشی (شکلِ r9، رنگِ VILL3N — `PhoneV3Debt`): متن ≥ ۴٫۵:۱ (WCAG 1.4.3) و
 *  خطِ دورِ کارت و آیکنِ میکروفون ≥ ۳:۱ (1.4.11)، روز و شب — روی همان سطحی
 *  که واقعاً کشیده می‌شود (رنگِ نیم‌شفاف روی زمینهٔ صفحه).
 */
class PhoneV3DebtContrastTest {

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  @Test
  fun `تم از روشنیِ زمینه انتخاب می‌شود`() {
    assertEquals(PhoneV3DebtLight, PhoneV3Debt.of(LightColors.bg))
    assertEquals(PhoneV3DebtDark, PhoneV3Debt.of(DarkColors.bg))
  }

  @Test
  fun `کارت‌ها، تراشه‌ها و کادرِ جستجو در روز و شب خوانا هستند`() {
    val bad = mutableListOf<String>()
    for ((theme, c) in listOf("روز" to PhoneV3DebtLight, "شب" to PhoneV3DebtDark)) {
      val surface = c.surface.compositeOver(c.page)
      val owe = c.oweTint.compositeOver(c.page)
      val settled = c.settledTint.compositeOver(c.page)
      val text = listOf(
        "ink روی کارتِ بالا" to contrast(c.ink, surface),
        "muted (برچسبِ عدد، تراشه) روی کارت" to contrast(c.muted, surface),
        "owe (جمعِ قرض) روی کارتِ بالا" to contrast(c.owe, surface),
        "settled (رسیدها) روی کارتِ بالا" to contrast(c.settled, surface),
        "onAccent روی تراشهٔ انتخاب‌شده" to contrast(c.onAccent, c.accentActive),
        "muted («N مورد»، برچسبِ جستجو) روی زمینه" to contrast(c.muted, c.page),
        "accentText (جستجوی فعال) روی زمینه" to contrast(c.accentText, c.page),
        "ink روی کارتِ بدهکار" to contrast(c.ink, owe),
        "owe روی کارتِ بدهکار" to contrast(c.owe, owe),
        "muted روی کارتِ بدهکار" to contrast(c.muted, owe),
        "onPill روی قرصِ روز" to contrast(c.onPill, c.pill),
        "ink روی کارتِ صاف" to contrast(c.ink, settled),
        "settled روی کارتِ صاف" to contrast(c.settled, settled),
        "muted روی کارتِ صاف" to contrast(c.muted, settled),
      )
      val shape = listOf(
        "خطِ قرمزِ کارت روی زمینه" to contrast(c.owe, c.page),
        "خطِ سبزِ کارت روی زمینه" to contrast(c.settled, c.page),
        "میکروفون روی دایره‌اش" to contrast(c.accentInk, c.accentSoft.compositeOver(c.page)),
        "آیکنِ × و مداد روی دکمه" to contrast(c.icon, surface),
        "دکمهٔ «+» روی زمینه" to contrast(c.accent, c.page),
        "«+» روی دکمه" to contrast(c.onAccent, c.accent),
      )
      text.filter { it.second < 4.5f }.forEach { bad += "$theme: ${it.first} = ${it.second}" }
      shape.filter { it.second < 3f }.forEach { bad += "$theme: ${it.first} = ${it.second}" }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }
}
