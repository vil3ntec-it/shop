package ir.vil3ntec.tohid.ui.components.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VISUAL-DIRECTION §۱.۵ — نمای کارتی/جدولی، برای هر کاربر و هر صفحه، روی همین دستگاه */
class ViewModePrefTest {

  private class MapStore : ViewModePref.Store {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
  }

  @Test
  fun `پیش‌فرض‌ها — مدیریت جدول، فروش کارت، گوشی همیشه کارت`() {
    assertEquals(ViewMode.TABLE, ViewModePref.default(ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.CARD, ViewModePref.default(ScreenKind.SALE, FormFactor.LARGE))
    assertEquals(ViewMode.CARD, ViewModePref.default(ScreenKind.MANAGEMENT, FormFactor.PHONE))
    assertEquals(ViewMode.CARD, ViewModePref.default(ScreenKind.SALE, FormFactor.PHONE))
  }

  @Test
  fun `انتخابِ ذخیره‌شده بر پیش‌فرض می‌نشیند ولی گوشی همیشه کارت است`() {
    assertEquals(ViewMode.CARD, ViewModePref.resolve("card", ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.TABLE, ViewModePref.resolve("table", ScreenKind.SALE, FormFactor.LARGE))
    assertEquals(ViewMode.CARD, ViewModePref.resolve("table", ScreenKind.MANAGEMENT, FormFactor.PHONE))
    //  مقدارِ خراب یا خالی ⇐ پیش‌فرض، نه خطا
    assertEquals(ViewMode.TABLE, ViewModePref.resolve("grid??", ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.TABLE, ViewModePref.resolve(null, ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.TABLE, ViewModePref.resolve(" TABLE ", ScreenKind.SALE, FormFactor.LARGE))
    assertNull(ViewMode.parse(""))
  }

  @Test
  fun `کلید برای هر کاربر و هر صفحه جداست`() {
    val a = ViewModePref.key("u1", "products")
    assertEquals("view_mode:u1:products", a)
    assertNotEquals(a, ViewModePref.key("u2", "products"))
    assertNotEquals(a, ViewModePref.key("u1", "sale"))
    //  بی‌حساب ⇐ local
    assertEquals("view_mode:local:products", ViewModePref.key("", "products"))
    assertEquals("view_mode:local:products", ViewModePref.key(null, "products"))
    //  «:» درونِ شناسه دو کلید را یکی نمی‌کند
    assertNotEquals(ViewModePref.key("a:b", "c"), ViewModePref.key("a", "b:c"))
  }

  @Test
  fun `ذخیره و خواندن — کاربر و صفحهٔ دیگر اثر نمی‌گیرند`() {
    val s = MapStore()
    ViewModePref.save(s, "u1", "products", ViewMode.CARD, FormFactor.LARGE)
    assertEquals(ViewMode.CARD, ViewModePref.load(s, "u1", "products", ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.TABLE, ViewModePref.load(s, "u2", "products", ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.TABLE, ViewModePref.load(s, "u1", "customers", ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.CARD, ViewModePref.load(s, "u1", "sale", ScreenKind.SALE, FormFactor.LARGE))
  }

  @Test
  fun `گوشی چیزی نمی‌نویسد و انتخابِ پنجرهٔ پهن را پاک نمی‌کند`() {
    val s = MapStore()
    ViewModePref.save(s, "u1", "products", ViewMode.TABLE, FormFactor.LARGE)
    ViewModePref.save(s, "u1", "products", ViewMode.CARD, FormFactor.PHONE)
    assertEquals(ViewMode.TABLE, ViewModePref.load(s, "u1", "products", ScreenKind.MANAGEMENT, FormFactor.LARGE))
    assertEquals(ViewMode.CARD, ViewModePref.load(s, "u1", "products", ScreenKind.MANAGEMENT, FormFactor.PHONE))
    assertFalse(ViewModePref.toggleVisible(FormFactor.PHONE))
    assertTrue(ViewModePref.toggleVisible(FormFactor.LARGE))
  }

  @Test
  fun `انبارِ خراب صفحه را نمی‌اندازد`() {
    val broken = object : ViewModePref.Store {
      override fun get(key: String): String? = error("disk")
      override fun put(key: String, value: String) = error("disk")
    }
    assertEquals(ViewMode.TABLE, ViewModePref.load(broken, "u1", "products", ScreenKind.MANAGEMENT, FormFactor.LARGE))
    ViewModePref.save(broken, "u1", "products", ViewMode.CARD, FormFactor.LARGE)
  }

  @Test
  fun `نمای دیگر`() {
    assertEquals(ViewMode.TABLE, ViewModePref.other(ViewMode.CARD))
    assertEquals(ViewMode.CARD, ViewModePref.other(ViewMode.TABLE))
  }
}
