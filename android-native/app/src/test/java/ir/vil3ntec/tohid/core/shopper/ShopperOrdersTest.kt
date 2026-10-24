package ir.vil3ntec.tohid.core.shopper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** پله‌های پیگیری — همان خروجیِ `K.orderSteps` ِ وب برای همین سفارش‌ها */
class ShopperOrdersTest {

  private fun order(status: String, fulfillment: String = "delivery", vararg at: Pair<String, Long>) = PublicOrder(
    status = status, fulfillment = fulfillment, updatedAt = 999,
    timeline = at.map { TimelineEntry(it.first, it.second) },
  )

  private fun states(o: PublicOrder) = ShopperOrders.steps(o).map { it.stage to it.state }

  @Test
  fun `تازه — پلهٔ اول «الان»`() {
    assertEquals(
      listOf(
        OrderStage.PLACED to StepState.NOW, OrderStage.ACCEPTED to StepState.TODO, OrderStage.PREPARING to StepState.TODO,
        OrderStage.ON_THE_WAY to StepState.TODO, OrderStage.DELIVERED to StepState.TODO,
      ),
      states(order("new", "delivery", "new" to 1)),
    )
  }

  @Test
  fun `در راه با زمانِ هر پله`() {
    val o = order("out_for_delivery", "delivery", "new" to 1, "accepted" to 2, "picking" to 3, "packed" to 4, "out_for_delivery" to 5)
    val steps = ShopperOrders.steps(o)
    assertEquals(listOf(StepState.DONE, StepState.DONE, StepState.DONE, StepState.NOW, StepState.TODO), steps.map { it.state })
    //  آماده‌سازی = آخرین زمانِ «جمع‌آوری/بسته‌بندی»
    assertEquals(4L, steps[2].at)
    assertEquals(5L, steps[3].at)
  }

  @Test
  fun `دریافت از دکان — «آمادهٔ دریافت» و «تحویل گرفتید» تمام`() {
    val o = order("picked_up", "pickup", "new" to 1, "accepted" to 2, "ready_for_pickup" to 3, "picked_up" to 4)
    assertEquals(
      listOf(
        OrderStage.PLACED to StepState.DONE, OrderStage.ACCEPTED to StepState.DONE, OrderStage.PREPARING to StepState.DONE,
        OrderStage.READY_FOR_PICKUP to StepState.DONE, OrderStage.PICKED_UP to StepState.DONE,
      ),
      states(o),
    )
    assertTrue(ShopperOrders.isFinal(o.status))
  }

  @Test
  fun `ردشده پس از قبول — پله‌های بعدی نیست، فقط پایان`() {
    val o = order("cancelled", "delivery", "new" to 1, "accepted" to 2, "cancelled" to 7)
    val steps = ShopperOrders.steps(o)
    assertEquals(
      listOf(OrderStage.PLACED to StepState.DONE, OrderStage.ACCEPTED to StepState.DONE, OrderStage.STOPPED to StepState.STOP),
      steps.map { it.stage to it.state },
    )
    assertEquals("cancelled", steps.last().status)
    assertEquals(7L, steps.last().at)
  }

  @Test
  fun `لغوِ خریدار بی زمان ⇒ زمانِ آخرین تغییر`() {
    val steps = ShopperOrders.steps(order("cancelled_by_customer", "delivery", "new" to 1))
    assertEquals(OrderStage.STOPPED, steps.last().stage)
    assertEquals(999L, steps.last().at)
  }

  @Test
  fun `تحویل نشد هنوز «در راه» است و پیگیری ادامه دارد`() {
    val o = order("delivery_failed", "delivery", "new" to 1, "accepted" to 2, "out_for_delivery" to 3, "delivery_failed" to 4)
    assertEquals(StepState.NOW, ShopperOrders.steps(o)[3].state)
    assertFalse(ShopperOrders.isFinal(o.status))
  }

  @Test
  fun `دفترِ سفارش‌های دستگاه — تازه‌ترین اول، بی تکرار با سرور`() {
    val storage = MemoryStorage()
    val book = ShopperOrderBook(storage)
    book.remember("nan", "نانوایی", PublicOrder(token = "t1", orderNo = 1, createdAt = 10, status = "new"))
    book.remember("gul", "گل", PublicOrder(token = "t2", orderNo = 2, createdAt = 20, status = "new"))
    book.touch("t1", "accepted")
    val merged = ShopperOrderBook(storage).merge(
      listOf(
        MarketOrder(orderNo = 2, status = "picking", token = "t2", createdAt = 20, store = StoreRef("gul", "گل‌فروشی")),
        MarketOrder(orderNo = 9, status = "delivered", token = "t9", createdAt = 30, store = StoreRef("ab", "آب")),
      ),
    )
    assertEquals(listOf("t9", "t2", "t1"), merged.map { it.token })
    assertEquals("picking", merged[1].status)
    assertEquals("accepted", merged[2].status)
  }
}
