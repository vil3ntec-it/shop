package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** کدام موردِ نوار و منوی کناری فعال است (AUDIT-NAV N1، N2) — روی **همهٔ** مسیرهای ثبت‌شده */
class NavSelectionTest {
  private val table = V3Modules.table
  private val layout = NavLayouts.DEFAULT
  private val barRoutes = layout.ids.mapNotNull { NavCatalog.of(it)?.route?.substringBefore('?') }.toSet() + "home"

  private fun sample(pattern: String) = pattern.replace(Regex("\\{[^}]+}"), "x1")

  /** همان پشته‌ای که پیوندِ سرد در گوشی می‌سازد */
  private fun phoneStack(path: String): List<String> {
    val nav = V3Nav(table, "home", NavStack.of("home"))
    nav.isTab = { it in barRoutes }
    nav.sectionsRoot = NavCatalog.MORE
    nav.openDeep(path)
    return nav.stack.entries
  }

  private fun active(entries: List<String>) = NavCatalog.activeForStack(layout, entries, table::canonical)

  @Test
  fun `هر مسیرِ دکان یک موردِ فعال دارد — تبِ ریشهٔ زنجیره یا «بیشتر»`() {
    table.routes(AppMode.SHOP).forEach { r ->
      if (r.kind == RouteKind.GATE) return@forEach
      val path = sample(r.pattern)
      val entries = phoneStack(path)
      val got = active(entries)
      assertNotNull("بی موردِ فعال: ${r.pattern} $entries", got)
      val root = RoutePaths.normalize(table.canonical(table.chain(path).first()).substringBefore('?'))
      val want = layout.ids.firstOrNull { NavCatalog.of(it)?.route?.substringBefore('?') == root } ?: NavCatalog.MORE
      assertEquals(r.pattern, want, got)
    }
  }

  @Test
  fun `جزئیات زیرِ تبِ خودش، بخش‌های «بیشتر» زیرِ «بیشتر»، برگه همان صفحهٔ زیر`() {
    assertEquals(NavCatalog.PRODUCTS, active(listOf("products", "products/42")))
    assertEquals(NavCatalog.PRODUCTS, active(listOf("products", "products/42", "products/categories")))
    assertEquals(NavCatalog.MORE, active(listOf("more", "reports")))
    assertEquals(NavCatalog.MORE, active(listOf("more", "settings", "settings/security")))
    assertEquals(NavCatalog.HOME, active(listOf("dashboard")))
    //  پیوندِ سرد به بخشی بیرون از نوار ⇐ زیرِ «بیشتر»، برگشت به «بیشتر»
    assertEquals(listOf("more", "stock", "stock/42"), phoneStack("stock/42"))
    assertEquals(NavCatalog.MORE, active(phoneStack("stock/42")))
    assertEquals(listOf("products", "products/42", "products/42/edit"), phoneStack("products/42/edit"))
  }

  @Test
  fun `منوی کناری با نامِ قدیمیِ ریشهٔ زنجیره`() {
    assertEquals("products", V3Modules.railId("products/42"))
    assertEquals("products", V3Modules.railId("products/42/edit"))
    assertEquals("warehouse", V3Modules.railId("stock"))
    assertEquals("warehouse", V3Modules.railId("stock/42"))
    assertEquals("warehouse", V3Modules.railId("stock/entry"))
    assertEquals("sale", V3Modules.railId("sale/quick"))
    assertEquals("settings", V3Modules.railId("settings/security/app-lock"))
    //  هر مسیر همان شناسهٔ ریشهٔ زنجیرهٔ خودش را دارد
    table.routes(AppMode.SHOP).forEach { r ->
      val path = sample(r.pattern)
      assertEquals(r.pattern, V3Modules.railId(table.chain(path).first()), V3Modules.railId(path))
    }
  }
}
