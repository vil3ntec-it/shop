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
  fun `نوار روی هر بخشِ اصلی — تب‌ها و بخش‌های «بیشتر»`() {
    listOf("products", "more", "home").forEach { assertEquals(it, Bar.SHOWN, bar(it)) }
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

  /**
   *  حالتِ تمرکزِ فروش (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «بخش فروش اون نوارها نباشن») — تغییرِ عمدیِ همین آزمون: پیش‌تر `sale` نوار
   *  داشت (کارِ یک‌دستی ۱۴۰۵/۰۸/۲۳). حالا صندوقِ گوشی، تب باشد یا بخش، با هر حالتی بی نوار است و نوارِ سبد تنها چیزِ پایین.
   */
  @Test
  fun `صندوقِ گوشی حالتِ تمرکز است — هرگز نوارِ شناور ندارد`() {
    assertTrue("sale" in ShellFrame.FOCUS)
    assertTrue("صندوق تبِ نوار است ولی باز هم بی نوار", "sale" in tabs)
    assertEquals(Bar.NONE, bar("sale"))
    assertEquals(Bar.NONE, bar("sale", sheetOnTop = true))
    assertEquals(Bar.NONE, bar("sale", keyboard = true))
    assertFalse(ShellFrame.navVisible(bar("sale")))
    //  فقط خودِ صندوق: بخش‌های همسایه (تاریخچهٔ فروش، کالاها) نوار را نگه می‌دارند
    assertEquals(Bar.SHOWN, bar("sales"))
    assertEquals(Bar.SHOWN, bar("products"))
  }

  @Test
  fun `صفحه‌کلیدِ باز نوار را برمی‌دارد و برگه رویش را می‌پوشاند`() {
    assertEquals(Bar.NONE, bar("products", keyboard = true))
    //  دوربینِ اسکن (`scan`) و تسویه (`sale/checkout`) برگه‌اند: نوار کشیده نمی‌شود ولی صفحهٔ زیر نمی‌پرد
    assertEquals(Bar.COVERED, bar("products", sheetOnTop = true))
    assertEquals("صندوق (حالتِ تمرکز) زیرِ برگه هم بی نوار", Bar.NONE, bar("sale", sheetOnTop = true))
    assertFalse(ShellFrame.navVisible(Bar.COVERED))
    assertEquals("صفحهٔ درونی زیرِ برگه هم بی نوار", Bar.NONE, bar("products/42", sheetOnTop = true))
  }

  @Test
  fun `پایینِ هر صفحه نگه‌داشته — جای نوار روی بخش‌ها، خانه خودش زیرِ نوار می‌کشد`() {
    assertEquals(reserve + system, ShellFrame.pageBottom(true, Bar.SHOWN, "products", reserve, system))
    assertEquals(reserve + system, ShellFrame.pageBottom(true, Bar.SHOWN, "stock", reserve, system))
    assertEquals("برگه روی صفحه: جا می‌ماند", reserve + system, ShellFrame.pageBottom(true, Bar.COVERED, "products", reserve, system))
    assertEquals("صندوق: فقط نوارِ سیستم", system, ShellFrame.pageBottom(true, bar("sale"), "sale", reserve, system))
    assertEquals(0.dp, ShellFrame.pageBottom(true, Bar.SHOWN, "home", reserve, system))
    assertEquals("صفحهٔ درونی: فقط نوارِ سیستم", system, ShellFrame.pageBottom(true, Bar.NONE, "products/42", reserve, system))
    assertEquals("صفحه‌کلیدِ باز: فقط نوارِ سیستم", system, ShellFrame.pageBottom(true, bar("products", keyboard = true), "products", reserve, system))
    assertEquals("پهن: نوارِ سیستم", system, ShellFrame.pageBottom(false, Bar.NONE, "stock", reserve, system))
  }

  /** ۶۳w: دکمهٔ جست‌وجوی نوار روی بخش ⇒ جست‌وجوی همان بخش؛ وگرنه سربرگ ذره‌بینِ خودش را می‌گذارد */
  private fun navSearch(page: String, phone: Boolean = true, gate: Boolean = false, hasSearch: Boolean = true) =
    ShellFrame.navSearch(phone, page, tabs, sections, gate, hasSearch)

  @Test
  fun `دکمهٔ جست‌وجوی نوار روی هر بخشِ اصلی هست — صفحه‌کلید و برگه حسابش نمی‌کنند`() {
    listOf("products", "stock", "debtors", "sales", "expenses", "receipts", "settings").forEach { assertTrue(it, navSearch(it)) }
    //  صفحه‌کلیدِ باز و برگهٔ روی صفحه نوار را موقت می‌پوشانند، ولی ذره‌بینِ سربرگ نباید بپرد
    assertEquals(Bar.NONE, bar("products", keyboard = true))
    assertTrue(navSearch("products"))
  }

  @Test
  fun `بی دکمهٔ جست‌وجو در نوار، صفحهٔ درونی، کامپیوتر یا دروازه ⇒ سربرگ ذره‌بین دارد`() {
    assertFalse("نوارِ ویرایش‌شده بی جست‌وجو", navSearch("products", hasSearch = false))
    assertFalse("جزئیاتِ کالا نوار ندارد", navSearch("products/{id}"))
    assertFalse("فروشِ سریع", navSearch("sale/quick"))
    assertFalse("صندوق (حالتِ تمرکز): ذره‌بینِ سربرگ", navSearch("sale"))
    assertFalse("کامپیوتر", navSearch("products", phone = false))
    assertFalse("دروازه", navSearch("products", gate = true))
  }
}
