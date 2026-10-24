package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.DeepLinks
import ir.vil3ntec.tohid.data.Reminders
import ir.vil3ntec.tohid.data.Watchman
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.HomeSections
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 *  هر مقصدی که پوسته، هشدارها و اعلان‌های سیستم باز می‌کنند یک مسیرِ ثبت‌شده است، و
 *  مقصدهای «اشتباه»ِ NAV-MAP درست شده‌اند (فرم به‌جای فهرست، همگام‌سازی به‌جای دستگاه‌ها).
 */
class ShellTargetsTest {
  private fun resolves(path: String) = assertNotNull("مسیرِ ثبت‌نشده: $path", V3Modules.table.resolve(path))

  @Test
  fun `کارهای سریع و کارتِ کششی فرمِ خودشان را باز می‌کنند`() {
    assertEquals("debtors/receive", ShellTargets.RECEIVE)
    assertEquals("stock/entry", ShellTargets.STOCK_ENTRY)
    assertEquals("expenses/new", ShellTargets.PAY)
    assertEquals("sale", ShellTargets.NEW_SALE)
    assertEquals("sales", ShellTargets.HISTORY)
    listOf(ShellTargets.RECEIVE, ShellTargets.STOCK_ENTRY, ShellTargets.PAY, ShellTargets.NEW_SALE, ShellTargets.HISTORY, ShellTargets.PROFILE)
      .forEach(::resolves)
  }

  @Test
  fun `هشدارِ همگام‌سازی صفحهٔ همگام‌سازی را باز می‌کند نه دستگاه‌ها`() {
    assertEquals("settings/data/sync", ShellTargets.SYNC)
    resolves(ShellTargets.SYNC)
    assertEquals("settings/data/sync", V3Modules.table.resolve(ShellTargets.SYNC)!!.match.path.substringBefore('?'))
  }

  @Test
  fun `همهٔ مقصدهای پوسته ثبت‌اند`() {
    listOf(
      ShellTargets.BACKUP, ShellTargets.ABOUT, ShellTargets.NEW_ORDERS, ShellTargets.SUBSCRIPTION, ShellTargets.PLANS,
      ShellTargets.stock(true), ShellTargets.stock(false), ShellTargets.OVERDUE_DEBTS, ShellTargets.MOST_DEBT, ShellTargets.EXPIRING,
      ShellTargets.PURCHASING, ShellTargets.product("p1"), ShellTargets.newProduct("123"), ShellTargets.debtor("d1"),
      ShellTargets.supplier("s1"), ShellTargets.invoice("sale-1"), ShellTargets.SCAN, ShellTargets.notifications("news"),
      ShellTargets.search("x"), TransactionPaths.LIST,
    ).forEach(::resolves)
    HomeSections.tiles(canManage = true, ordersLive = true).forEach { resolves(it.route) }
    HomeAlerts.build(
      HomeAlerts.Input(ordersLive = true, newOrders = 1, subscriptionLocked = true, sync = HomeAlerts.Sync.ERROR, outOfStock = 1, overdueDebtors = 1, expiring = 1),
    ).forEach { resolves(it.route); resolves(it.actionRoute) }
    SearchIndex.sections().forEach { resolves(it.route) }
  }

  @Test
  fun `اعلان‌های سیستم سرِ کادرِ خودشان باز می‌شوند`() {
    assertEquals("products?filter=out", Watchman.LINK_STOCK_OUT)
    assertEquals("debtors?sort=most", Watchman.LINK_DEBTS)
    assertEquals("subscription", Watchman.LINK_SUBSCRIPTION)
    assertEquals("notifications?tab=news", Watchman.LINK_NEWS)
    assertEquals("home", Reminders.LINK)
    listOf(Watchman.LINK_STOCK_OUT, Watchman.LINK_DEBTS, Watchman.LINK_SUBSCRIPTION, Watchman.LINK_NEWS, Watchman.linkDebtor("d 1"), Reminders.LINK)
      .forEach(::resolves)
  }

  @Test
  fun `پیوندِ اعلان همان مسیر را در حالتِ دکان باز می‌کند`() {
    //  همان رشته‌ای که PendingIntent در `data` می‌گذارد و MainActivity با `DeepLinks.parse` می‌خواند
    listOf(Watchman.LINK_STOCK_OUT, Watchman.LINK_DEBTS, Watchman.LINK_SUBSCRIPTION, Watchman.LINK_NEWS, Watchman.linkDebtor("d7"), Reminders.LINK)
      .forEach { path ->
        val link = DeepLinks.parse(DeepLinks.appLink(path))
        assertNotNull("پیوند خوانده نشد: $path", link)
        assertEquals(AppMode.SHOP, link!!.mode)
        assertEquals(path, link.path)
        resolves(link.path)
      }
    assertEquals("vill3n://app/products?filter=out", DeepLinks.appLink(Watchman.LINK_STOCK_OUT))
  }
}
