package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.v3.nav.ShellFrame.Bar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پایینِ صفحه در پوسته (AUDIT-NAV S1، کارِ یک‌دستی ۱۴۰۵/۰۸/۲۳): نوار روی هر بخشِ اصلی — نه فقط ریشهٔ تب‌ها
 *  (پیش‌تر `stock` ِ بازشده از «بیشتر» و صندوق نوار نداشتند و این آزمون همان را می‌خواست؛ صاحب‌کار برعکسش را
 *  خواست) — و جای نوار و نوارِ سیستم همیشه نگه‌داشته.
 */
class ShellFrameTest {
  private val tabs = setOf("home", "sale", "debtors", "products", "more")
  private val sections = V3Modules.sections
  private val reserve = 80.dp
  private val system = 24.dp

  private fun bar(
    page: String,
    phone: Boolean = true,
    gate: Boolean = false,
    cartOpen: Boolean = false,
    sheetOnTop: Boolean = false,
    keyboard: Boolean = false,
  ) = ShellFrame.bar(phone, page, tabs, sections, gate, cartOpen, sheetOnTop, keyboard)

  @Test
  fun `بخش‌های اصلی همه از ماژول‌ها می‌آیند`() {
    val main = listOf("products", "stock", "debtors", "sales", "receipts", "expenses", "purchasing", "reports", "settings", "team", "more", "sale")
    main.forEach { assertTrue("بخشِ اصلی: $it", it in sections) }
    assertFalse("جزئیاتِ کالا بخش نیست", "products/{id}" in sections)
  }

  @Test
  fun `نوار روی هر بخشِ اصلی — تب‌ها، بخش‌های «بیشتر» و صندوق`() {
    listOf("products", "more", "home", "sale").forEach { assertEquals(it, Bar.SHOWN, bar(it)) }
    listOf("stock", "reports", "expenses", "receipts", "purchasing", "sales", "settings", "team").forEach {
      assertEquals("بخشی که از «بیشتر» باز شد: $it", Bar.SHOWN, bar(it))
    }
    assertTrue(ShellFrame.navVisible(bar("stock")))
  }

  @Test
  fun `صفحهٔ درونی، فروشِ سریع، ویرایشگرِ نوار، دروازه و پهن بی نوار`() {
    assertEquals("جزئیاتِ کالا", Bar.NONE, bar("products/42"))
    assertEquals("فرمِ کالای تازه", Bar.NONE, bar("products/new"))
    assertEquals("فروشِ سریع نوارِ سبدِ تمام‌قدِ خودش را دارد", Bar.NONE, bar("sale/quick"))
    assertEquals(Bar.NONE, bar(ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths.NAVBAR))
    assertEquals(Bar.NONE, bar("home", gate = true))
    assertEquals("سبدِ بازِ صندوق", Bar.NONE, bar("sale", cartOpen = true))
    assertEquals("کامپیوتر یا پهن", Bar.NONE, bar("home", phone = false))
  }

  @Test
  fun `صفحه‌کلیدِ باز نوار را برمی‌دارد و برگه رویش را می‌پوشاند`() {
    assertEquals(Bar.NONE, bar("products", keyboard = true))
    //  دوربینِ اسکن (`scan`) و تسویه (`sale/checkout`) برگه‌اند: نوار کشیده نمی‌شود ولی صفحهٔ زیر نمی‌پرد
    assertEquals(Bar.COVERED, bar("products", sheetOnTop = true))
    assertEquals(Bar.COVERED, bar("sale", sheetOnTop = true))
    assertFalse(ShellFrame.navVisible(Bar.COVERED))
    assertEquals("صفحهٔ درونی زیرِ برگه هم بی نوار", Bar.NONE, bar("products/42", sheetOnTop = true))
  }

  @Test
  fun `پایینِ هر صفحه نگه‌داشته — جای نوار روی بخش‌ها، خانه خودش زیرِ نوار می‌کشد`() {
    assertEquals(reserve + system, ShellFrame.pageBottom(true, Bar.SHOWN, "products", reserve, system))
    assertEquals(reserve + system, ShellFrame.pageBottom(true, Bar.SHOWN, "stock", reserve, system))
    assertEquals("برگه روی صفحه: جا می‌ماند", reserve + system, ShellFrame.pageBottom(true, Bar.COVERED, "sale", reserve, system))
    assertEquals(0.dp, ShellFrame.pageBottom(true, Bar.SHOWN, "home", reserve, system))
    assertEquals("صفحهٔ درونی: فقط نوارِ سیستم", system, ShellFrame.pageBottom(true, Bar.NONE, "products/42", reserve, system))
    assertEquals("صفحه‌کلیدِ باز: فقط نوارِ سیستم", system, ShellFrame.pageBottom(true, bar("products", keyboard = true), "products", reserve, system))
    assertEquals("پهن: نوارِ سیستم", system, ShellFrame.pageBottom(false, Bar.NONE, "stock", reserve, system))
  }
}
