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
    //  متنِ دکمهٔ اصلی و دکمهٔ حذف — همان onPrimary/onError ِ TohidTheme
    check(bad, "روز", "onPrimary روی primary", Color.White, LightColors.primary)
    check(bad, "شب", "onPrimary روی primary", Color(0xFF04121F), DarkColors.primary)
    check(bad, "روز", "onError روی danger", Color.White, LightColors.danger)
    check(bad, "شب", "onError روی danger", Color(0xFF2A0B09), DarkColors.danger)
    assertTrue("زیرِ ۴٫۵:۱ — $bad", bad.isEmpty())
  }

  @Test
  fun `سطح‌های کارِ روز توپرند — شیشه نیست`() {
    //  VISUAL-DIRECTION §۱ بند ۱: کارتِ سفیدِ توپر روی زمینهٔ خنثی
    assertEquals(1f, LightColors.surface.alpha)
    assertEquals(1f, LightColors.surfaceSolid.alpha)
    assertTrue("خطِ دورِ کارت باید تیره و نازک باشد، نه لبهٔ سفیدِ شیشه", LightColors.border.luminance() < 0.1f && LightColors.border.alpha < 0.2f)
  }

  @Test
  fun `حرفِ اولِ کالا روی تهِ رنگِ دسته در روز و شب خواناست`() {
    val bad = mutableListOf<String>()
    for ((theme, list) in listOf("روز" to AvatarLight, "شب" to AvatarDark)) {
      list.forEachIndexed { i, (bg, fg) -> check(bad, theme, "تهِ دستهٔ $i", fg, bg) }
    }
    assertTrue("زیرِ ۴٫۵:۱ — $bad", bad.isEmpty())
    //  همان دسته ⇐ همان رنگ؛ روز و شب از پالتِ خودشان
    assertEquals(avatarColors(LightColors, "خوراکی"), avatarColors(LightColors, " خوراکی "))
    assertTrue(avatarColors(DarkColors, "خوراکی") in AvatarDark)
    assertTrue(avatarColors(LightColors, "") in AvatarLight)
  }

  @Test
  fun `نسخهٔ ۲ — خطِ دور مویی است و سه ناحیه از هم جدا دیده می‌شوند`() {
    for ((theme, c) in listOf("روز" to LightColors, "شب" to DarkColors)) {
      //  «همه‌جا خطِ تیره» (نقدِ ۱۴۰۵/۰۷/۲۷): خطِ دورِ کارت حداکثر ۸٪
      assertTrue("$theme: خطِ دورِ کارت ${c.border.alpha} مویی نیست", c.border.alpha <= 0.08f)
      //  کارت، منو، کاتالوگ (bg) و سبد هر کدام رنگِ خودشان را دارند — «همهٔ جعبه‌ها یک رنگ‌اند»
      val zones = listOf(c.surface, c.zoneNav, c.bg, c.zoneCart)
      assertEquals("$theme: دو ناحیه هم‌رنگ‌اند", zones.size, zones.toSet().size)
      for (z in listOf(c.zoneNav, c.zoneCart, c.surface, c.bg)) assertEquals("$theme: ناحیه توپر است", 1f, z.alpha)
    }
  }
}
