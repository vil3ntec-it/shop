package ir.vil3ntec.tohid.ui.courier

import ir.vil3ntec.tohid.ui.theme.V3Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** قرصِ وضعیتِ پیک همان نگاشتِ یگانهٔ `OrderStatusTone.courier` را به کیت می‌دهد و هر وضعیت برچسبِ پیک دارد */
class CourierStatusTest {
  private val all = listOf("requested", "assigned", "picked_up", "moving", "arrived", "delivered", "failed", "rejected", "withdrawn", "returned")

  @Test
  fun `هر وضعیتِ پیک یک معنای کیت`() {
    assertEquals(V3Status.AWAITING, courierStatus("requested"))
    assertEquals(V3Status.PREPARING, courierStatus("assigned"))
    listOf("picked_up", "moving", "arrived").forEach { assertEquals(it, V3Status.IN_DELIVERY, courierStatus(it)) }
    assertEquals(V3Status.DELIVERED, courierStatus("delivered"))
    assertEquals(V3Status.CANCELLED, courierStatus("rejected"))
    assertEquals(V3Status.REVIEW, courierStatus("failed"))
    assertEquals(V3Status.REVIEW, courierStatus("returned"))
    assertEquals(V3Status.INACTIVE, courierStatus("withdrawn"))
    assertEquals(V3Status.INACTIVE, courierStatus("unknown_future_status"))
  }

  @Test
  fun `هر وضعیت برچسبِ فارسیِ پیک دارد نه کد`() {
    all.forEach { assertTrue(it, STATUS_LABEL[it].orEmpty().isNotBlank() && STATUS_LABEL[it] != it) }
  }

  @Test
  fun `برچسبِ دکمهٔ پله و کارِ منفی`() {
    assertEquals("قبول", actionLabel("requested", "assigned"))
    assertEquals("تلاشِ دوباره", actionLabel("failed", "moving"))
    assertEquals("راه افتادم", actionLabel("picked_up", "moving"))
    assertTrue(isNegative("rejected") && isNegative("failed"))
    assertEquals(-1, reached("requested"))
    assertEquals(4, reached("delivered"))
  }
}
