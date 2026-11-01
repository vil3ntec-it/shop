package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ORD-07: دکمهٔ «عقب انداختن» و بدنه‌اش همان قاعده‌های `lib/order-delay.js` */
class OrderDelayTest {

  private val statuses = listOf("accepted", "picking", "packed")

  @Test
  fun buttonOnlyWithFlagStatusAndManage() {
    assertTrue(OrderDelay.can("accepted", statuses, manage = true))
    assertTrue(OrderDelay.can("packed", statuses, manage = true))
    assertFalse(OrderDelay.can("new", statuses, manage = true), "تازه هنوز زمانی ندارد")
    assertFalse(OrderDelay.can("ready_for_pickup", statuses, manage = true))
    assertFalse(OrderDelay.can("accepted", statuses, manage = false), "بی orders.manage")
    assertFalse(OrderDelay.can("accepted", null, manage = true), "پرچمِ خاموش ⇒ delay ِ تهی")
    assertFalse(OrderDelay.can("accepted", emptyList(), manage = true))
  }

  @Test
  fun bodyFollowsServerParse() {
    assertTrue(OrderDelay.valid(15, "", "", 1440), "بی دلیل پذیرفته است")
    assertTrue(OrderDelay.valid(1, "courier", "", 1440))
    assertTrue(OrderDelay.valid(1440, "other", "برق رفت", 1440))
    assertFalse(OrderDelay.valid(0, "", "", 1440))
    assertFalse(OrderDelay.valid(1441, "", "", 1440))
    assertFalse(OrderDelay.valid(15, "other", "  ", 1440), "«دیگر» توضیح می‌خواهد")
    assertFalse(OrderDelay.valid(15, "lunch", "", 1440), "دلیلِ بیرون از فهرست")
    assertFalse(OrderDelay.valid(15, "busy", "", 0), "سقفِ صفر ⇒ هیچ")
  }

  @Test
  fun choicesWithinServerLimits() {
    assertEquals(listOf(10, 15, 20, 30, 45, 60), OrderDelay.minutes(1440))
    assertEquals(listOf(10, 15, 20), OrderDelay.minutes(20))
    assertEquals(emptyList(), OrderDelay.minutes(0))
    assertEquals(listOf("busy", "courier", "other"), OrderDelay.reasons(listOf("other", "courier", "busy", "new_code")))
    assertEquals(listOf("busy", "out_of_stock", "courier", "customer_request", "other"), OrderDelay.REASONS)
  }
}
