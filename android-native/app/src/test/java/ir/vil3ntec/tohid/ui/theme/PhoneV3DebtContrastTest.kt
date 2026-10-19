package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  رنگ‌های قرض‌دارانِ گوشی (r9، `PhoneV3Debt`): متن ≥ ۴٫۵:۱ (WCAG 1.4.3) و
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
      val pill = c.page.copy(alpha = 0.7f).compositeOver(owe)
      val text = listOf(
        "ink روی کارتِ بالا" to contrast(c.ink, surface),
        "muted2 (برچسبِ عدد) روی کارتِ بالا" to contrast(c.muted2, surface),
        "owe (جمعِ قرض) روی کارتِ بالا" to contrast(c.owe, surface),
        "settled (رسیدها) روی کارتِ بالا" to contrast(c.settled, surface),
        "muted (تراشه) روی سطح" to contrast(c.muted, surface),
        "onAccent روی تراشهٔ انتخاب‌شده" to contrast(c.onAccent, c.accent),
        "muted2 («N مورد») روی زمینه" to contrast(c.muted2, c.page),
        "muted (برچسبِ جستجو) روی زمینه" to contrast(c.muted, c.page),
        "accentInk (جستجوی فعال) روی زمینه" to contrast(c.accentInk, c.page),
        "ink روی کارتِ بدهکار" to contrast(c.ink, owe),
        "owe روی کارتِ بدهکار" to contrast(c.owe, owe),
        "muted روی کارتِ بدهکار" to contrast(c.muted, owe),
        "muted روی قرصِ روز" to contrast(c.muted, pill),
        "ink روی کارتِ صاف" to contrast(c.ink, settled),
        "settled روی کارتِ صاف" to contrast(c.settled, settled),
        "muted روی کارتِ صاف" to contrast(c.muted, settled),
      )
      val shape = listOf(
        "خطِ قرمزِ کارت روی زمینه" to contrast(c.owe, c.page),
        "خطِ سبزِ کارت روی زمینه" to contrast(c.settled, c.page),
        "میکروفون روی دایره‌اش" to contrast(c.accentInk, c.accentSoft.compositeOver(c.page)),
      )
      text.filter { it.second < 4.5f }.forEach { bad += "$theme: ${it.first} = ${it.second}" }
      shape.filter { it.second < 3f }.forEach { bad += "$theme: ${it.first} = ${it.second}" }
    }
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }
}
