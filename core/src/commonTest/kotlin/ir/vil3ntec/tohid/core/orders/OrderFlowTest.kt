package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «به فاکتور» ِ یک‌ضرب و «چه کسی درستش می‌کند» — همان قاعده‌های `server/src/lib/order-flow.js` و `order-arrival.js` */
class OrderFlowTest {

  private val pack = listOf("picking", "packed")

  @Test
  fun noButtonBeforeAcceptOrAfterInvoiceOrForTests() {
    assertNull(OrderFlow.plan("new", "delivery", "unpaid", isTest = false, invoiced = false, pack = pack), "پیش از قبول نه")
    assertNull(OrderFlow.plan("packed", "delivery", "unpaid", isTest = false, invoiced = true, pack = null), "یک بار")
    assertNull(OrderFlow.plan("packed", "delivery", "unpaid", isTest = true, invoiced = false, pack = null), "آزمایشی نه")
    assertNull(OrderFlow.plan("packed", "delivery", "pending", isTest = false, invoiced = false, pack = null), "پرداختِ در جریان نه")
    assertNull(OrderFlow.plan("cancelled", "delivery", "unpaid", isTest = false, invoiced = false, pack = null))
    assertNull(OrderFlow.plan("returned_to_store", "delivery", "unpaid", isTest = false, invoiced = false, pack = null))
    assertNull(OrderFlow.plan("accepted", "delivery", "unpaid", isTest = false, invoiced = false, pack = null), "بی اجازهٔ بسته‌بندی نه")
  }

  @Test
  fun deliveryPacksThenInvoicesNow() {
    val p = OrderFlow.plan("accepted", "delivery", "unpaid", isTest = false, invoiced = false, pack = pack)!!
    assertEquals(listOf("picking", "packed"), p.before)
    assertTrue(p.recordNow)
    assertEquals(emptyList(), p.after)
    assertTrue(p.packs)
    assertFalse(p.handsOver)
    val packed = OrderFlow.plan("packed", "delivery", "unpaid", isTest = false, invoiced = false, pack = null)!!
    assertEquals(emptyList(), packed.before)
    assertTrue(packed.recordNow)
    assertTrue(OrderFlow.plan("picking", "delivery", "unpaid", isTest = false, invoiced = false, pack = listOf("packed"))!!.recordNow)
  }

  @Test
  fun unpaidPickupIsInvoicedAtTheCounter() {
    val p = OrderFlow.plan("accepted", "pickup", "unpaid", isTest = false, invoiced = false, pack = pack)!!
    assertEquals(listOf("picking", "packed", "ready_for_pickup"), p.before)
    assertFalse(p.recordNow, "فروش را تحویل با نقدِ پیشخوان می‌نویسد")
    assertEquals(listOf("picked_up"), p.after)
    assertTrue(p.handsOver)
    val ready = OrderFlow.plan("ready_for_pickup", "pickup", "unpaid", isTest = false, invoiced = false, pack = null)!!
    assertEquals(emptyList(), ready.before)
    assertEquals(listOf("picked_up"), ready.after)
  }

  @Test
  fun paidPickupIsInvoicedNow() {
    val p = OrderFlow.plan("packed", "pickup", "paid", isTest = false, invoiced = false, pack = null)!!
    assertTrue(p.recordNow)
    assertEquals(emptyList(), p.after)
  }

  @Test
  fun handedOverOrdersOnlyMarkTheInvoice() {
    for (s in listOf("out_for_delivery", "delivered", "picked_up")) {
      val p = OrderFlow.plan(s, "delivery", "unpaid", isTest = false, invoiced = false, pack = null)!!
      assertTrue(p.recordNow, s)
      assertEquals(emptyList(), p.before)
    }
  }

  @Test
  fun fixCodesFromServer() {
    assertEquals(OrderFlow.Fix.SUPPORT, OrderFlow.fixOf("support"))
    assertEquals(OrderFlow.Fix.SUBSCRIBE, OrderFlow.fixOf("subscribe"))
    assertEquals(OrderFlow.Fix.STOREFRONT, OrderFlow.fixOf("storefront"))
    assertEquals(OrderFlow.Fix.PRODUCTS, OrderFlow.fixOf("products"))
    assertEquals(OrderFlow.Fix.SERVER, OrderFlow.fixOf("server"))
    assertEquals(OrderFlow.Fix.NONE, OrderFlow.fixOf("sometext_from_newer_server"))
    assertTrue(OrderFlow.waitsForCustomer("awaiting_choice"))
    assertFalse(OrderFlow.waitsForCustomer("cash"))
  }
}
