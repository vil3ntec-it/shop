package ir.vil3ntec.tohid.ui

import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.paletteFor
import ir.vil3ntec.tohid.ui.theme.shopColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پرچمِ رابطِ نسخهٔ ۳ (`UiV3`) — تصمیمِ صاحب محصول: پیش‌فرض خاموش تا خودش روشن کند؛
 *  خاموش ⇒ همان رابط و همان پالتِ سبزآبیِ امروز.
 */
class UiV3Test {

  @Test
  fun `پیش‌فرض خاموش است — فقط نسخهٔ پیش‌نمایش بی انتخاب روشن است`() {
    assertFalse(UiV3.resolve(stored = null, preview = false))
    assertTrue(UiV3.resolve(stored = null, preview = true))
  }

  @Test
  fun `انتخابِ ذخیره‌شدهٔ دستگاه بر پیش‌فرض جلو می‌افتد`() {
    assertTrue(UiV3.resolve(stored = true, preview = false))
    assertFalse(UiV3.resolve(stored = false, preview = true))
  }

  @Test
  fun `ساختِ معمولی (نه پیش‌نمایش) با پرچمِ خاموش بالا می‌آید`() {
    //  BuildConfig.PREVIEW ِ آزمون‌ها false است ⇒ مقدارِ آغازینِ پیش از `load` خاموش
    assertFalse(UiV3.on)
  }

  @Test
  fun `پرچمِ خاموش ⇒ پالتِ سبزآبیِ امروز، هر تمی که انتخاب شده باشد`() {
    for (t in ShopTheme.entries) {
      val day = paletteFor(v3 = false, theme = t, dark = false)
      val night = paletteFor(v3 = false, theme = t, dark = true)
      //  عددهای main (VILL3N، ۱۴۰۵/۰۷/۲۸)
      assertEquals(0xFF108B81.toInt(), day.brandPrimary.toArgbInt())
      assertEquals(0xFF08665F.toInt(), day.button.primary.bg.toArgbInt())
      assertEquals(0xFF19A985.toInt(), night.brandPrimary.toArgbInt())
      //  «هشدار» و «در انتظارِ تأیید» در رابطِ امروز یک رنگ (کهربایی) بودند
      assertEquals(day.status.awaitingApproval, day.status.warning)
      assertEquals(night.status.awaitingApproval, night.status.warning)
      assertEquals(0xFF92400E.toInt(), day.status.awaitingApproval.fg.toArgbInt())
    }
  }

  @Test
  fun `پرچمِ روشن ⇒ همان تمِ انتخاب‌شدهٔ نسخهٔ ۳`() {
    for (t in ShopTheme.entries) for (dark in listOf(false, true)) {
      assertSame(shopColors(t, dark), paletteFor(v3 = true, theme = t, dark = dark))
    }
  }

  private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or
      ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()
}
