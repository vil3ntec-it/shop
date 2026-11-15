package ir.vil3ntec.tohid.ui.desk

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.screens.DeskMenu
import ir.vil3ntec.tohid.ui.screens.MoreLayout
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  منوی کناریِ کامپیوتر (e-desk-d1، `DeskMenu.sidebar`): هیچ بخشی گم نمی‌شود — هر ردیفِ منوی پیشینِ کامپیوتر
 *  (`DeskMenu.groups`) و هر ردیفِ «بیشتر» ِ گوشی (`MoreLayout`) در منو هست، میان‌برها همان، هر ردیفِ زنده مسیر دارد،
 *  «خاموش» همان چهار بخش، و «همهٔ بخش‌ها» هم کارت‌های همان‌ها را دارد (`DeskMoreModel`).
 */
class DeskSidebarModelTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private val ui: File = listOf(
    "src/main/java/ir/vil3ntec/tohid/ui",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/ui",
  ).map { File(it) }.first { it.isDirectory }

  /** از متنِ AppNav.kt — همان روشِ `DeskMenuTest` (AppNav وضعیتِ Compose دارد) */
  private fun shortcuts(): List<Pair<String, String>> {
    val nav = File(ui, "AppNav.kt").readText().substringAfter("val SHORTCUTS").substringAfter("listOf(").substringBefore(")\n")
    return Regex(""""([a-z]+)" to ([^,\n]+)""").findAll(nav).map { it.groupValues[1] to it.groupValues[2] }.toList()
  }

  private class Case(val admin: Boolean, val on: Boolean)

  private val cases = listOf(true, false).flatMap { a -> listOf(false, true).map { Case(a, it) } }

  private fun extras(c: Case): List<DeskMenu.Extra> {
    val access = MoreLayout.access(c.admin, c.on, c.on, c.on, c.on)
    return V3Modules.entries(Place.MORE, access).mapNotNull { e -> e.group?.let { DeskMenu.Extra(e.id, e.label(), e.route, it) } }
  }

  private fun sidebar(c: Case): List<DeskMenu.Item> =
    DeskMenu.sidebar(c.admin, shortcuts(), ordersLive = c.on, storefrontLive = c.on, deliveryLive = c.on, chatLive = c.on, extras = extras(c))
      .flatMap { it.items } + DeskMenu.sidebarFooter(c.admin)

  @Test fun `هر ردیفِ منوی پیشینِ کامپیوتر در منوی تازه هست — همان مسیر و همان کلید`() {
    for (c in cases) {
      val old = DeskMenu.groups(c.admin, shortcuts(), ordersLive = c.on, storefrontLive = c.on, deliveryLive = c.on).flatMap { it.items }
      val now = sidebar(c).associateBy { it.id }
      for (o in old) {
        val n = now[o.id] ?: error("«${o.label}» (${o.id}) در منوی کامپیوتر نیست — admin=${c.admin} on=${c.on}")
        assertEquals(o.id, o.target, n.target)
        assertEquals(o.id, o.key, n.key)
        assertEquals(o.id, o.state, n.state)
      }
    }
  }

  @Test fun `هر بخشِ «بیشتر» ِ گوشی در منوی کامپیوتر هست`() {
    for (c in cases) {
      val phone = MoreLayout.groups(canManage = c.admin, ordersOn = c.on, newOrders = 0, chatOn = c.on, version = "1", storefrontOn = c.on, deliveryOn = c.on)
      val ids = sidebar(c).map { it.id }.toSet()
      val targets = sidebar(c).filter { it.state == DeskMenu.State.LIVE }.map { V3Modules.table.canonical(it.target) }.toSet()
      for (g in phone) for (item in g.items) {
        val found = when {
          //  شش دستهٔ تنظیمات ⇐ ردیفِ «تنظیمات» ِ پایینِ منو (صفحهٔ دوستونه، `DeskSettingsUiTest`)
          item.settingsGroup != null -> "settings" in ids
          //  «تاریخچه» همان Ctrl+6 ِ «تاریخچهٔ فروش»
          item.id == "history" -> "sales" in ids
          else -> item.id in ids || V3Modules.table.canonical(item.route) in targets
        }
        assertTrue("«${item.label}» (${item.id}) از «بیشتر» در منوی کامپیوتر نیست — admin=${c.admin} on=${c.on}", found)
      }
    }
  }

  @Test fun `میان‌برها یک بار، هر ردیفِ زنده مسیر دارد و شناسه تکراری نیست`() {
    val sc = shortcuts()
    assertEquals(9, sc.size)
    for (c in cases) {
      val items = sidebar(c)
      sc.forEachIndexed { i, (id, _) ->
        val hits = items.filter { it.id == id }
        assertEquals("$id یک بار", 1, hits.size)
        assertEquals("$id کلید", "Ctrl+${i + 1}", hits[0].key)
      }
      assertEquals("Ctrl+M", items.single { it.id == "admin" }.key)
      assertEquals(items.size, items.map { it.id }.toSet().size)
      val dead = items.filter { it.state == DeskMenu.State.LIVE }.filterNot { it.target.isNotEmpty() && V3Modules.table.resolve(it.target) != null }
      assertEquals("پیوندِ مرده", emptyList<String>(), dead.map { it.id })
      assertTrue("در دست ساخت", items.none { it.state == DeskMenu.State.SOON })
      val off = items.filter { it.state == DeskMenu.State.OFF }.map { it.id }
      assertEquals(if (c.on) emptyList() else listOf("off:orders", "off:fulfil", "off:storefront", "off:delivery"), off)
    }
  }

  @Test fun `گروه‌ها همان گروه‌های «بیشتر» اند، به ترتیب، و ماژولِ تازه تهِ گروهِ خودش می‌نشیند`() {
    I18n.source = { Lang.FA }
    val g = DeskMenu.sidebar(true, shortcuts(), extras = listOf(DeskMenu.Extra("x-new", "بخشِ تازه", "example", MoreGroup.SHOP)))
    assertEquals(
      listOf("", MoreGroup.SALES.title.text, ir.vil3ntec.tohid.core.i18n.StrDesk.groupOnline, MoreGroup.STOCK.title.text, MoreGroup.MONEY.title.text, MoreGroup.SHOP.title.text, MoreGroup.ACCOUNT.title.text),
      g.map { it.title },
    )
    assertEquals("x-new", g.single { it.title == MoreGroup.SHOP.title.text }.items.last().id)
    assertEquals(listOf("dashboard"), g.first().items.map { it.id })
  }

  @Test fun `ردیفِ روشن — زده‌شده، مسیرِ دقیق، سپس ریشه`() {
    val items = sidebar(Case(admin = true, on = true))
    assertEquals("dashboard", DeskSidebarModel.selected(items, "dashboard", "home", null))
    assertEquals("debtors", DeskSidebarModel.selected(items, V3Modules.railId("debtors"), "debtors", null))
    //  «آماده‌سازی و تحویل» و «سفارش‌های آنلاین» یک ریشه دارند: مسیرِ دقیق برنده است، و ردیفِ زده‌شده هم
    val fulfil = items.single { it.id == "fulfil" }.target
    assertEquals("fulfil", DeskSidebarModel.selected(items, V3Modules.railId(fulfil), fulfil, null))
    assertEquals("orders", DeskSidebarModel.selected(items, V3Modules.railId("orders"), "orders", "orders"))
  }

  @Test fun `«همهٔ بخش‌ها» ِ کامپیوتر همهٔ بخش‌های «بیشتر» ِ گوشی را دارد`() {
    for (c in cases) {
      val desk = DeskMoreModel.sections(c.admin, c.on, 0, c.on, c.on, c.on, emptySet())
      val cards = desk.flatMap { it.cards }
      val ids = cards.map { it.id }.toSet()
      val routes = cards.filter { !it.off }.map { V3Modules.table.canonical(it.route) }.toSet()
      val phone = MoreLayout.groups(canManage = c.admin, ordersOn = c.on, newOrders = 0, chatOn = c.on, version = "1", storefrontOn = c.on, deliveryOn = c.on)
      for (g in phone) for (item in g.items) {
        val found = item.id in ids || (item.id == "history" && "sales" in ids) || V3Modules.table.canonical(item.route) in routes
        assertTrue("«${item.label}» (${item.id}) در «همهٔ بخش‌ها» ِ کامپیوتر نیست — admin=${c.admin} on=${c.on}", found)
      }
      //  هر کارتِ روشن یک خطِ «چه می‌کند» دارد و هیچ کارتی بی مسیر نیست
      assertTrue(cards.filter { !it.off }.all { !it.line.isNullOrBlank() && V3Modules.table.resolve(it.route) != null })
    }
  }
}
