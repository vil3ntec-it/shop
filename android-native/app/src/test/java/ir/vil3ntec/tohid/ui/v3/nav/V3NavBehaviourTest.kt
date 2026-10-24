package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.NavStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** رفتارِ پشته (AUDIT-NAV N3، N6، N7) */
class V3NavBehaviourTest {
  private val tabs = setOf("home", "sale", "debtors", "products", "more")

  private fun nav(wide: Boolean = false) = V3Nav(V3Modules.table, "home", NavStack.of("home")).also {
    it.isTab = { base -> base in tabs }
    it.wide = wide
  }

  @Test
  fun `پهن — رفتن به عمق روی پشته، برگشت به جزئیات؛ منوی کناری هم‌سطح`() {
    val n = nav(wide = true)
    n.open("products")
    n.open("products/42")
    n.open("products/42/edit")
    assertEquals(listOf("products", "products/42", "products/42/edit"), n.stack.entries)
    n.back()
    assertEquals("products/42", n.current)
    n.openFromRail("reports")
    assertEquals(listOf("products", "reports"), n.stack.entries)
    n.openFromRail("debtors")
    assertEquals(listOf("debtors"), n.stack.entries)
  }

  @Test
  fun `هر تب پشتهٔ خودش را نگه می‌دارد؛ زدنِ دوباره ⇐ ریشه`() {
    val n = nav()
    n.open("products")
    n.open("products/42")
    n.open("home")
    assertEquals(listOf("home"), n.stack.entries)
    n.open("products")
    assertEquals(listOf("products", "products/42"), n.stack.entries)
    n.open("products")
    assertEquals(listOf("products"), n.stack.entries)
  }

  @Test
  fun `حالتِ صفحه فقط تا وقتی در پشته‌ای هست نگه داشته می‌شود`() {
    val n = nav()
    n.open("products")
    n.open("products/42")
    n.open("home")
    assertTrue("پشتهٔ تبِ دیگر زنده است", "products/42" in n.alive)
    n.open("products")
    n.back()
    assertFalse("برداشته شد ⇒ فراموش", "products/42" in n.alive)
  }

  @Test
  fun `دروازهٔ باز روی خانه — «برگشت» می‌بنددش و برنامه بسته نمی‌شود`() {
    val n = nav()
    assertFalse(n.canBack)
    n.open("lock")
    assertTrue(n.gate != null)
    assertTrue(n.canBack)
    n.back()
    assertEquals(null, n.gate)
    assertEquals(listOf("home"), n.stack.entries)
  }
}
