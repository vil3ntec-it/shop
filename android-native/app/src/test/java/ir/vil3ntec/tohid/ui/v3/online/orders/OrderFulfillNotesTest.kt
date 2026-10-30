package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderFulfillNotes.Act
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  هشدارهای «فروشِ تحویل» روی صف و جزئیاتِ سفارشِ نسخهٔ ۳ — همان شرط‌های صفحهٔ پیشینِ
 *  `ui/screens/Orders.kt` (پس از ادغامِ #340 در شاخهٔ آنلاین): کدام هشدار، با کدام دکمه، برای چه کسی.
 */
class OrderFulfillNotesTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", salePrice = 120.0, purchasePrice = 90.0)
  private val ledger = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "we1", productId = "p1", units = 10.0)))

  private fun order(status: String = "picked_up", at: Long = 5_000L) = OrderDto(
    id = "01ORDER", orderNo = 12, status = status, fulfillment = "pickup", paymentMethod = "cod", paymentStatus = "unpaid",
    customer = OrderCustomerDto(name = "احمد"),
    subtotalMinor = 12000, totalMinor = 12000,
    lines = listOf(OrderLineDto(lineNo = 1, productId = "p1", name = "برنج", unit = "کیلو", qtyMilli = 1000, unitPriceMinor = 12000, lineTotalMinor = 12000)),
    events = listOf(OrderEventDto(kind = "status", fromStatus = "ready_for_pickup", toStatus = "picked_up", at = at)),
  )

  private fun row(id: String = "01ORDER", msg: String = "پیام") = OrdersLive.Unposted(orderId = id, orderNo = 12, message = msg)

  private fun detail(
    d: ShopData = ledger, o: OrderDto = order(), review: List<OrdersLive.Unposted> = emptyList(), since: Long = 0L,
    manage: Boolean = true, refund: Boolean = true, record: Boolean = true, reverse: Boolean = true, waiting: Boolean = false,
  ) = OrderFulfillNotes.detail(d, o, review, emptyList(), since, manage, refund, record, reverse, waiting)

  @Test
  fun `صف: هر فهرستِ پُر یک هشدار؛ بررسی فقط برای صاحبِ بازپرداخت و نخستین سفارش را باز می‌کند`() {
    assertTrue(OrderFulfillNotes.queue(false, emptyList(), emptyList(), emptyList(), emptyList(), canRefund = true).isEmpty())
    val all = OrderFulfillNotes.queue(true, listOf(row("a"), row("b")), listOf(row()), listOf(row()), listOf(row()), canRefund = true)
    assertEquals(listOf("rollout", "review", "unposted", "waiting", "short"), all.map { it.key })
    assertEquals("a", all[1].orderId)
    assertEquals(Act.OPEN_REVIEW, all[1].act)
    assertEquals(Act.RETRY_ALL, all[2].act)
    assertNull("منتظر و کمبود دکمه ندارند", all[3].act ?: all[4].act)
    val clerk = OrderFulfillNotes.queue(false, listOf(row()), emptyList(), emptyList(), emptyList(), canRefund = false)
    assertTrue(clerk.isEmpty())
  }

  @Test
  fun `جزئیات: تحویلِ بی‌فروش ⇒ «ثبتِ فروش» فقط با اجازه؛ پیش از کف یا آزمایشی ⇒ هیچ`() {
    val n = detail().single()
    assertEquals("unposted", n.key)
    assertEquals(Act.POST, n.act)
    assertNull("بی اجازهٔ ثبت", detail(record = false).single().act)
    assertEquals(OrdersLive.SKIPPED_NOTE, detail(waiting = true).single().body)
    assertTrue("تحویلِ پیش از کف", detail(since = 5_001L).isEmpty())
    assertTrue("آزمایشی", detail(o = order().copy(isTest = true)).isEmpty())
  }

  @Test
  fun `جزئیات: فروش ثبت شده ⇒ برچسبِ فاکتور؛ برگشت به دکان ⇒ «لغوِ فروشِ تحویل»`() {
    val sold = (OrderFulfillment.fulfill(ledger, order(), "2026-10-19", 5_000L) as OrderFulfillment.Result.Ok).data
    val n = detail(d = sold).single()
    assertEquals("sold", n.key)
    assertFalse(n.warn)
    val back = detail(d = sold, o = order(status = "returned_to_store"))
    assertEquals(listOf("sold", "reverse"), back.map { it.key })
    assertEquals(Act.POST, back[1].act)
    assertNull("بی اجازهٔ لغو", detail(d = sold, o = order(status = "returned_to_store"), reverse = false)[1].act)
  }

  @Test
  fun `جزئیات: ردیفِ بررسی ⇒ «بررسی شد» فقط برای صاحبِ بازپرداخت`() {
    val n = detail(review = listOf(row(msg = "پیش از راه‌اندازی"))).single()
    assertEquals("review", n.key)
    assertEquals("پیش از راه‌اندازی", n.body)
    assertEquals(Act.ACK, n.act)
    assertNull(detail(review = listOf(row()), refund = false).single().act)
  }
}
