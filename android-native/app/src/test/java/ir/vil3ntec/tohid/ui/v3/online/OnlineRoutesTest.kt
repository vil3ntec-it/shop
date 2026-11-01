package ir.vil3ntec.tohid.ui.v3.online

import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** هر پیوندِ سفارش، ویترین، گفت‌وگو و میزِ تحویل به همان مسیر و همان آرگومان‌ها می‌رسد */
class OnlineRoutesTest {
  private val table = V3Modules.table

  @Test
  fun `پیوندها به الگوی درست می‌رسند`() {
    listOf(
      OnlinePaths.orders("ready") to "orders",
      OnlinePaths.order("o-1") to "orders/{id}",
      OnlinePaths.action("o-1", "rejected") to "orders/{id}/action",
      OnlinePaths.NEW_ORDER to "orders/new",
      OnlinePaths.storefront(StorefrontModel.PRODUCTS) to "storefront",
      OnlinePaths.storefrontProduct("p-1") to "storefront/products/{id}",
      OnlinePaths.conversation("c-1") to "chat/{conversationId}",
      OnlinePaths.delivery("couriers") to "delivery",
      OnlinePaths.deliveryAssign("o-1") to "delivery/assign/{orderId}",
      OnlinePaths.DELIVERY_AUTO to "delivery/auto",
      OnlinePaths.missionAction("m-1", "DELIVER") to "delivery/missions/{id}/action",
      OnlinePaths.DELIVERY_CODE to "delivery/code",
      OnlinePaths.deliveryPickup("o-2") to "delivery/pickup/{orderId}",
      OnlinePaths.courierSheet("cr-1", "settle") to "delivery/couriers/{id}/settle",
      OnlinePaths.courierSheet("cr-1", "ledger") to "delivery/couriers/{id}/ledger",
      OnlinePaths.courierSheet("cr-1", "cap") to "delivery/couriers/{id}/cap",
      OnlinePaths.deliveryInvoice("Kabul Express") to "delivery/invoice/{id}",
    ).forEach { (path, pattern) ->
      val r = table.resolve(path)
      assertNotNull(path, r)
      assertEquals(path, pattern, r!!.route.pattern)
    }
  }

  @Test
  fun `آرگومان‌ها و پرسش‌ها سالم برمی‌گردند`() {
    val act = table.resolve(OnlinePaths.missionAction("m-1", "DELIVER"))!!.args
    assertEquals("m-1", act["id"])
    assertEquals("DELIVER", act.query("act"))
    assertEquals("rejected", table.resolve(OnlinePaths.action("o-9", "rejected"))!!.args.query("to"))
    //  نامِ شرکت با فاصله و حرفِ فارسی هم سالم می‌ماند
    val name = "پیک سریع کابل"
    assertEquals(name, table.resolve(OnlinePaths.deliveryInvoice(name))!!.args["id"])
  }

  @Test
  fun `برگه‌های میز روی میز باز می‌شوند و برگشت به میز است`() {
    listOf(
      OnlinePaths.deliveryAssign("o-1"), OnlinePaths.DELIVERY_AUTO, OnlinePaths.DELIVERY_CODE,
      OnlinePaths.courierSheet("cr-1", "settle"), OnlinePaths.deliveryInvoice("x"),
    ).forEach { path ->
      assertEquals(path, RouteKind.SHEET, table.resolve(path)!!.route.kind)
      val nav = V3Nav(table, "home", NavStack.of("home"))
      nav.openDeep(path)
      assertEquals(path, OnlinePaths.DELIVERY, nav.page.substringBefore('?'))
      assertTrue(nav.back())
      assertEquals(path, OnlinePaths.DELIVERY, nav.current.substringBefore('?'))
    }
  }

  @Test
  fun `جزئیاتِ سفارش با پیوندِ سرد به صف برمی‌گردد`() {
    val nav = V3Nav(table, "home", NavStack.of("home"))
    nav.openDeep(OnlinePaths.order("o-1"))
    assertEquals("orders/o-1", nav.current)
    assertTrue(nav.back())
    assertEquals(OnlinePaths.ORDERS, nav.current.substringBefore('?'))
  }
}
