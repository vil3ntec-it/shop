package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.theme.V3Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopAlertsTest {
  private val home = HomeAlerts.build(
    HomeAlerts.Input(ordersLive = true, newOrders = 2, subscriptionDaysLeft = 6, sync = HomeAlerts.Sync.ERROR, lowStock = 2, expiring = 1),
  )

  @Test
  fun `فوری‌ها جدا و بقیه پس از آن`() {
    val g = ShopAlerts.build(home, ShopAlerts.Extras())
    assertEquals(listOf(ShopAlerts.Kind.ORDERS, ShopAlerts.Kind.SYNC), g.urgent.map { it.kind })
    assertEquals(listOf(ShopAlerts.Kind.SUBSCRIPTION, ShopAlerts.Kind.STOCK, ShopAlerts.Kind.EXPIRY), g.rest.map { it.kind })
    //  سفارشِ تازه آبیِ «اطلاع»، خطای همگام‌سازی سرخ، کم‌بودن زرد
    assertEquals(V3Tone.INFO, g.urgent[0].tone)
    assertEquals(V3Tone.ERROR, g.urgent[1].tone)
    assertEquals(V3Tone.WARNING, g.rest[1].tone)
    assertEquals("subscription/plans", g.rest[0].actionRoute)
    assertEquals("subscription", g.rest[0].route)
  }

  @Test
  fun `ردیف‌های زنگِ قدیم مستقیم سرِ همان سند`() {
    val g = ShopAlerts.build(
      emptyList(),
      ShopAlerts.Extras(
        out = listOf(ShopAlerts.Product("p1", "چای")),
        low = listOf(ShopAlerts.Low("p2", "قند", "2")),
        supplierDebt = "5,000", onlySupplierId = "s1",
        backupStale = true, backupText = "۳ روز پیش",
        topDebtors = listOf(ShopAlerts.Owing("d1", "احمد", "1,200")),
      ),
    )
    assertTrue(g.urgent.isEmpty())
    assertEquals(
      listOf("products/p1", "products/p2", "purchasing/suppliers/s1", "settings/data/backup", "debtors/d1"),
      g.rest.map { it.route },
    )
    assertEquals(V3Tone.ERROR, g.rest[0].tone)
    assertEquals(g.rest.map { it.key }.distinct(), g.rest.map { it.key })
  }

  @Test
  fun `ساده مثلِ خانه — نامِ کالاها زیرِ همان هشدارِ کالا، نه ردیفِ جدا`() {
    val withStock = HomeAlerts.build(HomeAlerts.Input(outOfStock = 2, lowStock = 2))
    val g = ShopAlerts.build(
      withStock,
      ShopAlerts.Extras(
        out = listOf(ShopAlerts.Product("p1", "چای"), ShopAlerts.Product("p2", "قند")),
        low = listOf(ShopAlerts.Low("p3", "برنج", "2"), ShopAlerts.Low("p4", "روغن", "1")),
      ),
    )
    assertTrue(g.all.none { it.kind == ShopAlerts.Kind.PRODUCT_OUT || it.kind == ShopAlerts.Kind.PRODUCT_LOW })
    val stock = g.all.single { it.kind == ShopAlerts.Kind.STOCK }
    assertTrue(stock.subtitle.startsWith("چای"))
    assertTrue(stock.subtitle.contains("برنج"))
    assertFalse(stock.subtitle.contains("روغن"))           // سه نام و «۱ دیگر»
    assertEquals("products?filter=out", stock.route)
  }

  @Test
  fun `چند قرض‌دارِ بیشترین قرض یک ردیف ⇐ فهرست به ترتیبِ بیشترین`() {
    val g = ShopAlerts.build(
      emptyList(),
      ShopAlerts.Extras(topDebtors = listOf(ShopAlerts.Owing("d1", "احمد", "1,200"), ShopAlerts.Owing("d2", "کریم", "900"))),
    )
    val row = g.rest.single()
    assertEquals("debtors?sort=most", row.route)
    assertTrue(row.subtitle.contains("احمد") && row.subtitle.contains("کریم"))
  }

  @Test
  fun `فهرستِ نام‌ها سه تا و باقی شمرده`() {
    assertEquals("الف", ShopAlerts.names(listOf("الف")))
    assertTrue(ShopAlerts.names(listOf("a", "b", "c", "d", "e")).endsWith(ir.vil3ntec.tohid.core.i18n.StrHome.andMore(2)))
  }

  @Test
  fun `چند تأمین‌کننده ⇐ فهرستِ خرید؛ بی بدهی ⇐ ردیفی نیست`() {
    val many = ShopAlerts.build(emptyList(), ShopAlerts.Extras(supplierDebt = "9,000", onlySupplierId = null))
    assertEquals("purchasing", many.rest.single().route)
    assertTrue(ShopAlerts.build(emptyList(), ShopAlerts.Extras()).isEmpty)
  }

  @Test
  fun `نقطهٔ زنگ فقط با فوری یا خبرِ تازه`() {
    val calm = ShopAlerts.build(emptyList(), ShopAlerts.Extras(backupStale = true))
    assertFalse(ShopAlerts.bellDot(calm, unseenNews = false))
    assertTrue(ShopAlerts.bellDot(calm, unseenNews = true))
    assertTrue(ShopAlerts.bellDot(ShopAlerts.build(home, ShopAlerts.Extras()), unseenNews = false))
  }
}
