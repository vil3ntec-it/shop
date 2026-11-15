package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 *  ۶۹ p3 — «اقیانوس» و «غروب» برداشته شدند (فیروزه‌ایِ تند و صورتی/نارنجی). کاربری که یکی از آن دو را برگزیده بود بی خطا به
 *  **نزدیک‌ترین** تمِ مانده می‌رود (کم‌ترین ΔE ِ رنگِ برند + دکمهٔ اصلیِ روزِ همان تمِ کهنه) و شناسهٔ ذخیره‌شده همان‌جا بازنویسی
 *  می‌شود (`ThemeStore.load`؛ آزمونِ ذخیره روی کامپیوتر: `ThemeStoreMigrationDesktopTest`).
 */
class ThemeMigrationTest {

  private fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

  private fun lab(c: Color): DoubleArray {
    val r = lin(c.red); val g = lin(c.green); val b = lin(c.blue)
    val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
    return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
  }

  private fun deltaE(a: Color, b: Color): Double = lab(a).zip(lab(b)).sumOf { (x, y) -> (x - y).pow(2) }.let(::sqrt)

  /** رنگِ برند و دکمهٔ اصلیِ روزِ دو تمِ برداشته‌شده (جدولِ THEME-V2 ِ پیشین) */
  private val removed = mapOf(
    "ocean" to (Color(0xFF0284C7) to Color(0xFF0369A1)),
    "dusk" to (Color(0xFF4F46E5) to Color(0xFF4338CA)),
  )

  @Test
  fun `تمِ کهنه به نزدیک‌ترین تمِ مانده می‌رود`() {
    assertEquals(removed.keys, ShopTheme.LEGACY.keys)
    for ((old, colors) in removed) {
      val (brand, button) = colors
      val nearest = ShopTheme.entries.minBy { t ->
        val c = shopColors(t, false)
        deltaE(brand, c.brandPrimary) + deltaE(button, c.button.primary.bg)
      }
      assertEquals("«$old»", nearest, ShopTheme.LEGACY.getValue(old))
      assertEquals(nearest, ShopTheme.of(old))
    }
    assertEquals(ShopTheme.NAVY, ShopTheme.of("ocean"))
    assertEquals(ShopTheme.SKY, ShopTheme.of("dusk"))
    //  شناسهٔ کهنه هرگز شناسهٔ تمِ زنده نیست
    assertTrue(ShopTheme.LEGACY.keys.none { k -> ShopTheme.entries.any { it.id == k } })
  }

  @Test
  fun `بازنویسیِ شناسهٔ ذخیره‌شده — فقط وقتی لازم است، هرگز خطا`() {
    assertEquals("navy", ShopTheme.migrated("ocean"))
    assertEquals("sky", ShopTheme.migrated("dusk"))
    assertEquals("sky", ShopTheme.migrated("green"))
    assertEquals("sky", ShopTheme.migrated(""))
    assertNull(ShopTheme.migrated(null))
    for (t in ShopTheme.entries) assertNull(t.id, ShopTheme.migrated(t.id))
    for (id in listOf(null, "", "OCEAN", "dusk ", "x".repeat(500))) ShopTheme.of(id)
  }
}
