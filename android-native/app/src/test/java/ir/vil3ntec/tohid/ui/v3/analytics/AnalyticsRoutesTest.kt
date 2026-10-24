package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** هر مسیرِ گروهِ گزارش با پیوندِ سرد باز می‌شود و برگشت به خانه می‌رسد */
class AnalyticsRoutesTest {
  private val table = V3Modules.table

  @Test
  fun `پیوندها با پرسش به همان مسیر می‌رسند`() {
    listOf(
      AnalyticsPaths.reports("purchases", "jmonth") to "reports",
      AnalyticsPaths.stats("week") to "stats",
      AnalyticsPaths.audit("sale") to "audit",
      AnalyticsPaths.ADMIN to "admin",
      AdminModel.route(AdminKpi.MONTH_PROFIT) to "reports",
    ).forEach { (path, pattern) ->
      val r = table.resolve(path)
      assertNotNull(path, r)
      assertEquals(pattern, r!!.route.pattern)
    }
    assertEquals("purchases", table.resolve(AnalyticsPaths.reports("purchases"))!!.args.query("tab"))
  }

  @Test
  fun `پیوندِ سرد پشته را از خانه می‌سازد و برگشت به خانه است`() {
    listOf(AnalyticsPaths.stats("month"), AnalyticsPaths.audit(), AnalyticsPaths.ADMIN).forEach { path ->
      val nav = V3Nav(table, "home", NavStack.of("home"))
      nav.openDeep(path)
      assertEquals(path.substringBefore('?'), nav.current.substringBefore('?'))
      assertTrue(nav.back())
      assertEquals("home", nav.current)
    }
  }

  @Test
  fun `سابقهٔ عملیات فقط با دسترسیِ سابقه باز است`() {
    assertFalse(table.resolve("audit")!!.allowed(Access(granted = setOf(Need.MANAGE))))
    assertTrue(table.resolve("audit")!!.allowed(Access(granted = setOf(Need.AUDIT))))
    assertTrue(table.resolve(AnalyticsPaths.ADMIN)!!.allowed(Access(granted = emptySet())))
  }
}
