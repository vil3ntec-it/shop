package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.ThemeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  ۶۹ p3 — شناسهٔ ذخیره‌شدهٔ تمِ برداشته‌شده («اقیانوس»، «غروب») در `ThemeStore.load` بی خطا به نزدیک‌ترین تمِ مانده می‌رود و
 *  همان‌جا بازنویسی می‌شود؛ تمِ زنده دست نمی‌خورد. (قاعدهٔ «نزدیک‌ترین»: `ThemeMigrationTest`.)
 */
class ThemeStoreMigrationDesktopTest {

  private val prefs get() = DesktopContext.getSharedPreferences("tohid-theme", 0)

  @After
  fun reset() {
    prefs.edit().remove("palette").commit()
    ThemeStore.preview(ShopTheme.SKY)
  }

  private fun loadWith(stored: String?): Pair<ShopTheme, String?> {
    prefs.edit().apply { if (stored == null) remove("palette") else putString("palette", stored) }.commit()
    ThemeStore.load(DesktopContext)
    return ThemeStore.theme to prefs.getString("palette", null)
  }

  @Test
  fun `تمِ کهنه بی خطا جانشین می‌گیرد و ذخیره بازنویسی می‌شود`() {
    assertEquals(ShopTheme.NAVY to "navy", loadWith("ocean"))
    assertEquals(ShopTheme.SKY to "sky", loadWith("dusk"))
    assertEquals(ShopTheme.SKY to "sky", loadWith("whatever"))
    //  تمِ زنده و نبودِ انتخاب دست نمی‌خورند
    assertEquals(ShopTheme.GRAPHITE to "graphite", loadWith("graphite"))
    assertEquals(ShopTheme.SKY to null, loadWith(null))
    //  بارِ دوم چیزی عوض نمی‌شود
    loadWith("ocean")
    ThemeStore.load(DesktopContext)
    assertEquals(ShopTheme.NAVY, ThemeStore.theme)
    assertEquals("navy", prefs.getString("palette", null))
  }
}
