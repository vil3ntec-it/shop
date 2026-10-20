package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import ir.vil3ntec.tohid.ui.components.common.NEUTRAL_TINT
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.pillColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  کنتراستِ جفت‌رنگ‌هایی که پنلِ فروشگاه (پوسته، کارتِ آماری، نشانِ وضعیت،
 *  زبانه، نوارِ کارِ گروهی، منوی کناری) به کار می‌برد — ≥ ۴٫۵:۱ در روز و شب
 *  (VISUAL-DIRECTION §۶، v3 §۵.۴).
 */
class BackOfficeContrastTest {

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  private fun check(bad: MutableList<String>, theme: String, what: String, fg: Color, bg: Color) {
    val r = contrast(fg, bg)
    if (r < 4.5f) bad += "$theme: $what = ${"%.2f".format(r)}"
  }

  @Test
  fun `متنِ پنل روی همهٔ سطح‌ها در روز و شب خواناست`() {
    val bad = mutableListOf<String>()
    for ((theme, c) in listOf("روز" to LightColors, "شب" to DarkColors)) {
      val grounds = listOf(
        "bg" to c.bg,
        "surface" to c.surface.compositeOver(c.bg),
        "surfaceSolid" to c.surfaceSolid,
        //  ردیف/منوی انتخاب‌شده: متنِ تیره روی تینتِ اصلی
        "primaryTint" to c.primaryTint.compositeOver(c.surfaceSolid),
        //  نسخهٔ ۲: ناحیهٔ منو و ناحیهٔ سبد، و قرصِ انتخاب‌شدهٔ منو روی ناحیهٔ خودش
        "zoneNav" to c.zoneNav,
        "zoneCart" to c.zoneCart,
        "primaryTint/zoneNav" to c.primaryTint.compositeOver(c.zoneNav),
        "surface2" to c.surface2,
      )
      for ((where, ground) in grounds) {
        check(bad, theme, "text روی $where", c.text, ground)
        check(bad, theme, "muted روی $where", c.muted, ground)
      }
      //  رنگ‌های معنایی روی سطح (فلشِ تغییرِ کارتِ آماری، دکمهٔ حذف، لینک)
      for ((name, fg) in listOf("success" to c.success, "warning" to c.warning, "danger" to c.danger, "info" to c.info, "primary" to c.primary)) {
        check(bad, theme, "$name روی surfaceSolid", fg, c.surfaceSolid)
        check(bad, theme, "$name روی surface", fg, c.surface.compositeOver(c.bg))
        check(bad, theme, "$name روی zoneCart", fg, c.zoneCart)
      }
      //  رنگِ اصلیِ پررنگ روی تینتِ خودش (قرصِ منو، تراشهٔ دستهٔ فعال، تعدادِ سبد)
      check(bad, theme, "primaryDark روی primaryTint/zoneNav", c.primaryDark, c.primaryTint.compositeOver(c.zoneNav))
      check(bad, theme, "primaryDark روی primaryTint", c.primaryDark, c.primaryTint.compositeOver(c.surfaceSolid))
      //  نشانِ وضعیت: متن/نقطه روی زمینهٔ کم‌رنگِ همان رنگ، روی کارت و روی ردیفِ جدول
      for (tone in PillTone.entries) {
        val (fg, tint) = pillColors(c, tone)
        check(bad, theme, "نشانِ $tone روی surfaceSolid", fg, tint.compositeOver(c.surfaceSolid))
        check(bad, theme, "نشانِ $tone روی surface", fg, tint.compositeOver(c.surface.compositeOver(c.bg)))
        check(bad, theme, "نشانِ $tone روی zoneCart", fg, tint.compositeOver(c.zoneCart))
      }
      //  آیکن/کلیدِ انتخاب‌شده و پیوندهای میان‌بُرِ وب (`.admin-links`): رنگِ اصلی روی تینتِ خودش
      check(bad, theme, "primary روی primaryTint", c.primary, c.primaryTint.compositeOver(c.surfaceSolid))
      //  شمارِ زبانه: متن روی تینتِ خنثی
      check(bad, theme, "text روی شمارِ زبانه", c.text, c.muted.copy(alpha = NEUTRAL_TINT).compositeOver(c.bg))
    }
    //  متنِ دکمهٔ اصلی و دکمهٔ حذف — همان onPrimary/onError ِ طرحِ متریالِ TohidTheme
    //  (`materialScheme`)، و رنگِ اصلی/خطای متریال به‌عنوانِ متن روی کارت و پنجره:
    //  `TextButton`، `OutlinedButton` و برچسبِ کادرِ فعال با `primary` نوشته می‌شوند.
    for ((theme, c) in listOf("روز" to LightColors, "شب" to DarkColors)) {
      val m = materialScheme(c)
      check(bad, theme, "onPrimary روی primary", m.onPrimary, m.primary)
      check(bad, theme, "onError روی error", m.onError, m.error)
      check(bad, theme, "primary (متنِ TextButton) روی surface", m.primary, m.surface)
      check(bad, theme, "error (متنِ خطا) روی surface", m.error, m.surface)
      check(bad, theme, "onSurfaceVariant روی surface", m.onSurfaceVariant, m.surface)
      check(bad, theme, "onPrimaryContainer روی primaryContainer", m.onPrimaryContainer, m.primaryContainer)
      check(bad, theme, "onSecondaryContainer روی secondaryContainer", m.onSecondaryContainer, m.secondaryContainer)
      check(bad, theme, "inverseOnSurface روی inverseSurface (اسنک‌بار)", m.inverseOnSurface, m.inverseSurface)
      check(bad, theme, "inversePrimary روی inverseSurface (کارِ اسنک‌بار)", m.inversePrimary, m.inverseSurface)
    }
    assertTrue("زیرِ ۴٫۵:۱ — $bad", bad.isEmpty())
  }

  @Test
  fun `سطح‌های کار توپرند و خطِ دورِ کارت دیدنی است — شیشه نیست`() {
    //  VISUAL-DIRECTION §۱ بند ۱: کارتِ سفیدِ توپر روی زمینهٔ خنثی.
    //  VILL3N §۶ (جای قاعدهٔ «خطِ مویی ۷٪»ِ نسخهٔ ۲): «کارتِ سفید روی زمینهٔ روشن بی خطِ
    //  دورِ دیدنی نه؛ سایه جای خط را نمی‌گیرد» ⇒ خطِ دور توپر و از کارت و زمینه تیره‌تر.
    for ((theme, c) in listOf("روز" to LightColors, "شب" to DarkColors)) {
      for ((name, v) in listOf("surface" to c.surface, "surfaceSolid" to c.surfaceSolid, "border" to c.border, "fieldBorder" to c.fieldBorder)) {
        assertEquals("$theme: $name توپر است", 1f, v.alpha)
      }
      if (theme == "روز") {
        assertTrue("روز: خطِ دورِ کارت تیره‌تر از کارت و زمینه", c.border.luminance() < c.surface.luminance() && c.border.luminance() < c.bg.luminance())
        assertTrue("روز: خطِ کادرِ ورودی پررنگ‌تر از خطِ کارت", c.fieldBorder.luminance() < c.border.luminance())
      } else {
        assertTrue("شب: خطِ دورِ کارت روشن‌تر از کارت و زمینه", c.border.luminance() > c.surface.luminance() && c.border.luminance() > c.bg.luminance())
        assertTrue("شب: خطِ کادرِ ورودی پررنگ‌تر از خطِ کارت", c.fieldBorder.luminance() > c.border.luminance())
      }
      //  «دیدنی»: دستِ‌کم ۱٫۲۵:۱ با کارت و با زمینه (روز: ۱٫۳۶ و ۱٫۲۵؛ شب: ۱٫۶۱ و ۱٫۸۹)
      assertTrue("$theme: خطِ دور روی کارت ${contrast(c.border, c.surface)}", contrast(c.border, c.surface) >= 1.25f)
      assertTrue("$theme: خطِ دور روی زمینه ${contrast(c.border, c.bg)}", contrast(c.border, c.bg) >= 1.24f)
    }
  }

  @Test
  fun `زمینه، کارت و سطحِ درونی جدا دیده می‌شوند و ناحیه‌ها هم‌رنگ نیستند`() {
    for ((theme, c) in listOf("روز" to LightColors, "شب" to DarkColors)) {
      //  VILL3N §۶: سطحِ ۰ ≠ ۱ ≠ ۲، و «کارت در شب هرگز با زمینه یکی نشود»
      val levels = listOf(c.background, c.backgroundSubtle, c.surface, c.surfaceElevated, c.surfaceSelected)
      assertEquals("$theme: دو سطح هم‌رنگ‌اند", levels.size, levels.toSet().size)
      assertTrue("$theme: کارت باید روشن‌تر از زمینه باشد", c.surface.luminance() > c.bg.luminance())
      //  کارت، منو، کاتالوگ (bg) و سبد هر کدام رنگِ خودشان را دارند — «همهٔ جعبه‌ها یک رنگ‌اند» (نقدِ نسخهٔ ۲)
      val zones = listOf(c.surface, c.zoneNav, c.bg, c.zoneCart)
      assertEquals("$theme: دو ناحیه هم‌رنگ‌اند", zones.size, zones.toSet().size)
      for (z in listOf(c.zoneNav, c.zoneCart, c.surface, c.bg)) assertEquals("$theme: ناحیه توپر است", 1f, z.alpha)
    }
  }
}
