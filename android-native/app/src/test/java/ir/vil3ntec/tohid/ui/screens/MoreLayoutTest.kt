package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «بیشتر»ِ گوشی (نسخهٔ ۳، بندِ F): هیچ بخشی بی‌راه نمی‌ماند، دسترسیِ نقش‌ها همان
 *  است که بود، و «تنظیمات» آخرین گروه است.
 */
class MoreLayoutTest {

  private fun routes(g: List<MoreLayout.Group>) = g.flatMap { it.items }.map { it.route }.toSet()

  @Test
  fun `هر مسیری که «بیشترِ» پیشین داشت هنوز هست — و قرض‌داران که از نوار رفت`() {
    val all = MoreLayout.groups(canManage = true, ordersOn = true, newOrders = 0, chatOn = true, version = "2.14.3")
    val before = setOf(
      "orders", "admin", "devices", "warehouse", "expenses", "receipts", "sales",
      "purchasing", "reports", "audit", "team", "myplan", "chat", "support",
    )
    assertTrue((before - routes(all)).toString(), routes(all).containsAll(before))
    assertTrue("debtors" in routes(all))
    assertTrue(MoreLayout.APP in routes(all))
    val ids = all.flatMap { it.items }.map { it.id }
    assertEquals("شناسهٔ تکراری", ids.size, ids.toSet().size)
  }

  @Test
  fun `«تنظیمات» گروهِ آخر است و هر گروهِ تنظیمات یک ردیف`() {
    val all = MoreLayout.groups(canManage = true, ordersOn = false, newOrders = 0, chatOn = false, version = "1")
    val last = all.last()
    assertEquals(MoreLayout.SETTINGS, last.title)
    assertEquals(SettingsLayout.GROUPS.map { it.title }, last.items.map { it.settingsGroup })
    assertTrue(last.items.all { it.route == "settings" })
  }

  @Test
  fun `شاگرد — بی سابقه، بی کارمندان، بی تنظیمات`() {
    val staff = MoreLayout.groups(canManage = false, ordersOn = true, newOrders = 0, chatOn = true, version = "1")
    val r = routes(staff)
    assertFalse("audit" in r); assertFalse("team" in r); assertFalse("settings" in r)
    assertFalse(staff.any { it.title == MoreLayout.SETTINGS })
    assertTrue("support" in r && "myplan" in r)
  }

  @Test
  fun `سفارش و گفت‌وگو فقط وقتی سرور روشن گفته؛ شمارِ تازه‌ها روی ردیف`() {
    val off = routes(MoreLayout.groups(true, ordersOn = false, newOrders = 3, chatOn = false, version = "1"))
    assertFalse("orders" in off); assertFalse("chat" in off)
    val on = MoreLayout.groups(true, ordersOn = true, newOrders = 3, chatOn = true, version = "1")
    assertEquals("3 تازه", on.flatMap { it.items }.first { it.id == "orders" }.value)
    assertEquals(null, MoreLayout.groups(true, true, 0, true, "1").flatMap { it.items }.first { it.id == "orders" }.value)
    assertTrue(on.all { it.items.isNotEmpty() })
  }
}
