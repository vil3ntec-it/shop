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
  fun `رابطِ تازه همیشه روشن است — هر انتخابِ کهنه و هر ساخت`() {
    //  تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۱۱: «شکلِ قدیم هرگز نیاید»
    for (stored in listOf(null, true, false)) for (preview in listOf(true, false)) {
      assertTrue(UiV3.resolve(stored = stored, preview = preview))
    }
    assertTrue(UiV3.on)
  }

  @Test
  fun `هیچ تنظیمی کلیدِ رابطِ تازه را ندارد`() {
    val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "android-native").isDirectory }
    val main = java.io.File(root, "android-native/app/src/main/java")
    val hits = main.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
      .filter { f -> f.readText().let { it.contains("UiV3.set(") || it.contains("item(key = \"ui-v3\")") } }
      .map { it.relativeTo(main).path }.toList()
    assertEquals(emptyList<String>(), hits)
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
