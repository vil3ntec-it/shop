package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayout
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  کاشیِ «سفارش‌های آنلاین» ِ خانه (خواستِ صاحب‌کار): جای «فروشِ تازه» فقط با بخشِ سفارشِ روشن، نشانِ زنده (تازه‌ها با نقطهٔ
 *  تپنده، بی تازه شمارِ در-کارها)، و صندوق که هرگز بی‌راه نمی‌ماند (نوار یا دکمهٔ شناورِ خانه).
 */
class HomeTilesTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  @Test
  fun `بخشِ سفارشِ خاموش ⇒ کاشیِ «فروشِ تازه» می‌ماند و دکمهٔ شناوری نیست`() {
    assertNull(HomeTiles.orders(enabled = false, counts = mapOf("new" to 4)))
    assertFalse(HomeTiles.saleFab(ordersTile = false, navIds = emptyList()))
  }

  @Test
  fun `نشان — تازه‌ها با نقطهٔ تپنده، بی تازه در-کارها، هیچ ⇒ بی نشان`() {
    val fresh = HomeTiles.orders(true, mapOf("new" to 3, "preparing" to 2, "ready" to 1, "done" to 40))!!
    assertEquals(3, fresh.fresh)
    assertEquals(3, fresh.active)
    assertTrue(fresh.pulse)
    assertEquals("3", fresh.badgeText)
    val busy = HomeTiles.orders(true, mapOf("preparing" to 2, "on_the_way" to 1, "scheduled" to 1, "done" to 9))!!
    assertFalse(busy.pulse)
    assertEquals(4, busy.badge)
    assertEquals("4", busy.badgeText)
    val idle = HomeTiles.orders(true, mapOf("done" to 12))!!
    assertNull("تمام‌شده‌ها نشان نمی‌گیرند", idle.badgeText)
    assertFalse(idle.pulse)
    assertEquals("99+", HomeTiles.orders(true, mapOf("new" to 250))!!.badgeText)
    assertEquals("شمارِ منفیِ خراب صفر است", null, HomeTiles.orders(true, mapOf("new" to -2))!!.badgeText)
  }

  @Test
  fun `نامِ دسترس‌پذیرِ کاشی شمار را می‌گوید`() {
    val a = HomeTiles.a11y(HomeTiles.Orders(fresh = 2, active = 5))
    assertTrue(a.contains("2") && a.contains("تازه"))
    assertTrue(HomeTiles.a11y(HomeTiles.Orders(0, 5)).contains("5"))
    assertEquals(StrOrdersHub.tile, HomeTiles.a11y(HomeTiles.Orders(0, 0)))
    I18n.source = { Lang.EN }
    assertEquals("Online orders, 2 new", HomeTiles.a11y(HomeTiles.Orders(2, 5)))
  }

  @Test
  fun `صندوق همیشه از خانه می‌رسد — نوار یا دکمهٔ شناور`() {
    //  هر چیدمانِ ممکنِ نوار (همهٔ موردها در هر جا): کاشیِ سفارش ⇒ یا نوار «فروش»/«اسکن» دارد یا خانه دکمهٔ شناور
    val all = NavCatalog.BUILT_IN.map { it.id }
    val layouts = listOf(NavLayouts.DEFAULT) + all.map { drop -> NavLayout(NavCatalog.SEARCH, (all - drop - NavCatalog.SEARCH).take(5), null) } +
      listOf(NavLayout(null, listOf(NavCatalog.HOME, NavCatalog.PRODUCTS, NavCatalog.HISTORY, NavCatalog.MORE), NavCatalog.ACCOUNT))
    for (l in layouts) {
      val tillInBar = NavCatalog.SALE in l.ids || NavCatalog.SCAN in l.ids
      assertTrue("${l.ids}", tillInBar || HomeTiles.saleFab(ordersTile = true, navIds = l.ids))
      assertFalse("نوارِ دارای صندوق دکمهٔ دوم نمی‌خواهد: ${l.ids}", tillInBar && HomeTiles.saleFab(true, l.ids))
    }
    //  چیدمانِ عکسِ صاحب‌کار (خانه، کالا، تاریخچه، فروش، بیشتر) ⇒ صندوق در نوار، بی دکمهٔ دوم
    assertFalse(HomeTiles.saleFab(true, listOf("search", "home", "products", "history", "sale", "more", "account")))
    assertTrue(HomeTiles.saleFab(true, listOf("search", "home", "products", "history", "more", "account")))
    //  مقصدِ دکمه همان زبانهٔ صندوقِ نسخهٔ ۳ است و کاشی همان صفِ سفارش‌ها
    val sale = V3Modules.table.resolve(ShellTargets.NEW_SALE)
    assertNotNull(sale)
    assertEquals(RouteKind.TAB, sale!!.route.kind)
    assertEquals("orders", V3Modules.table.resolve(ShellTargets.ORDERS)!!.route.pattern)
  }

  @Test
  fun `کاشی و دکمهٔ شناور در خانهٔ گوشی، تبلت و کامپیوتر به کار رفته‌اند`() {
    val repo = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val shell = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/shell")
    val home = File(shell, "HomeScreen.kt").readText()
    assertTrue("خانهٔ گوشی/تبلت کاشی را با همان شمار می‌گیرد", "HomeShortcuts(onOpen, Modifier.v3Stagger(stagger, \"shortcuts\", 2), orders)" in home)
    assertTrue("خانهٔ گوشی/تبلت دکمهٔ شناورِ صندوق دارد", "HomeSaleFab(" in home && "HomeTiles.saleFab(" in home)
    assertTrue("خانهٔ کامپیوتر همان ردیفِ میان‌برها", "HomeShortcuts(" in File(shell, "DeskHome.kt").readText())
    //  سفارش‌ها در «خانه» ِ ماژول‌ها هم هست
    assertTrue(V3Modules.ALL.flatMap { it.entries }.any { it.id == "orders" && Place.HOME in it.places })
  }
}
