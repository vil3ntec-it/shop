package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  F0-18 — رنگ‌هایی که جای رنگ‌های ثابت نشستند، در روز و شب خوانا باشند.
 *
 *  نارنجیِ ثابتِ `0xFFFF9800` روی کارتِ روشن ۲٫۱:۱ بود (زیرِ ۴٫۵:۱ِ WCAG AA).
 *  جایش `Shop.colors.warning` نشست؛ پنجرهٔ میان‌برها هم عنوان را با
 *  `Shop.colors.primary` می‌نویسد. این آزمون همان دو توکن را روی هر سه
 *  زمینهٔ برنامه (زمینه، کارتِ شیشه‌ای، پنجرهٔ توپر) در هر دو تم می‌سنجد.
 */
class ThemeContrastTest {

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  @Test
  fun `هشدار و رنگِ اصلی در روز و شب روی همهٔ زمینه‌ها خوانا هستند`() {
    val bad = mutableListOf<String>()
    for ((theme, c) in listOf("روز" to LightColors, "شب" to DarkColors)) {
      val grounds = listOf(
        "bg" to c.bg,
        "surface" to c.surface.compositeOver(c.bg),   //  کارتِ شیشه‌ای روی زمینه
        "surfaceSolid" to c.surfaceSolid,              //  پنجره و منو
      )
      for ((token, fg) in listOf("warning" to c.warning, "primary" to c.primary)) {
        for ((where, ground) in grounds) {
          val r = contrast(fg, ground)
          if (r < 4.5f) bad += "$theme: $token روی $where = ${"%.2f".format(r)}"
        }
      }
    }
    assertTrue("زیرِ ۴٫۵:۱ — $bad", bad.isEmpty())
  }
}
