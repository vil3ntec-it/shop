package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** پایینِ صفحه در پوسته (AUDIT-NAV S1، S2): نوار فقط روی ریشهٔ تب‌ها و جای نوار و نوارِ سیستم همیشه نگه‌داشته */
class ShellFrameTest {
  private val tabs = setOf("home", "sale", "debtors", "products", "more")
  private val reserve = 80.dp
  private val system = 24.dp

  @Test
  fun `نوار فقط روی ریشهٔ تب‌های نوار — صفحهٔ درونی، صندوق و دروازه بی نوار`() {
    assertTrue(ShellFrame.navVisible(true, "products", tabs, gate = false, cartOpen = false))
    assertTrue(ShellFrame.navVisible(true, "more", tabs, gate = false, cartOpen = false))
    assertFalse("جزئیاتِ کالا", ShellFrame.navVisible(true, "products/42", tabs, gate = false, cartOpen = false))
    assertFalse("بخشی که از «بیشتر» باز شد", ShellFrame.navVisible(true, "stock", tabs, gate = false, cartOpen = false))
    assertFalse("صندوق نوارِ سبدِ خودش را دارد", ShellFrame.navVisible(true, "sale", tabs, gate = false, cartOpen = false))
    assertFalse(ShellFrame.navVisible(true, "settings/general/navbar", tabs + "settings/general/navbar", gate = false, cartOpen = false))
    assertFalse(ShellFrame.navVisible(true, "home", tabs, gate = true, cartOpen = false))
    assertFalse(ShellFrame.navVisible(true, "home", tabs, gate = false, cartOpen = true))
    assertFalse("کامپیوتر/پهن", ShellFrame.navVisible(false, "home", tabs, gate = false, cartOpen = false))
  }

  @Test
  fun `پایینِ هر صفحه نگه‌داشته — خانه خودش زیرِ نوار می‌کشد`() {
    assertEquals(reserve + system, ShellFrame.pageBottom(true, navVisible = true, page = "products", navReserve = reserve, systemBottom = system))
    assertEquals(0.dp, ShellFrame.pageBottom(true, navVisible = true, page = "home", navReserve = reserve, systemBottom = system))
    assertEquals("صفحهٔ درونی: فقط نوارِ سیستم", system, ShellFrame.pageBottom(true, navVisible = false, page = "products/42", navReserve = reserve, systemBottom = system))
    assertEquals(system, ShellFrame.pageBottom(true, navVisible = false, page = "sale", navReserve = reserve, systemBottom = system))
    assertEquals("پهن: نوارِ سیستم هم", system, ShellFrame.pageBottom(false, navVisible = false, page = "stock", navReserve = reserve, systemBottom = system))
  }
}
