package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 *  DLV-07/17: صفِ پیک برچسب‌های کیسه را فقط با «گرفتم» و موقعیت را فقط با «تحویل شد» می‌برد — همان کلیدهای
 *  `bags` و `position` ِ `server/src/lib/courier-checks.js` (`parseInput`).
 */
class CourierQueueChecksTest {

  private val pin = CourierChecks.Point(34.524356, 69.181234)

  @Test
  fun bagsOnlyWithPickedUp() {
    val (q, e) = CourierQueue.enqueue(QueueState(), "cev-bags-0001", "A", "picked_up", 1, bags = listOf(" VLNB-12-1-2 ", "VLNB-12-2-2"))
    assertEquals(listOf("VLNB-12-1-2", "VLNB-12-2-2"), e.bags)
    assertEquals(listOf(e), q.pending)
    val (_, m) = CourierQueue.enqueue(QueueState(), "cev-bags-0002", "A", "moving", 1, bags = listOf("VLNB-12-1-2"))
    assertEquals(emptyList<String>(), m.bags, "برچسب فقط با «گرفتم»")
  }

  @Test
  fun badBags() {
    val many = List(CourierQueue.MAX_BAGS + 1) { "VLNB-1-1-1" }
    assertEquals("bad_bags", assertFailsWith<CourierInputError> {
      CourierQueue.enqueue(QueueState(), "cev-bags-0003", "A", "picked_up", 1, bags = many)
    }.code)
    assertEquals("bad_bags", assertFailsWith<CourierInputError> {
      CourierQueue.enqueue(QueueState(), "cev-bags-0004", "A", "picked_up", 1, bags = listOf("x".repeat(CourierQueue.MAX_LABEL + 1)))
    }.code)
  }

  @Test
  fun positionOnlyWithDelivered() {
    val (_, d) = CourierQueue.enqueue(QueueState(), "cev-pos-00001", "A", "delivered", 1, cashCollectedMinor = 0, position = pin)
    assertEquals(pin, d.position)
    val (_, p) = CourierQueue.enqueue(QueueState(), "cev-pos-00002", "A", "picked_up", 1, position = pin)
    assertNull(p.position, "موقعیت فقط با «تحویل شد»")
    for (bad in listOf(CourierChecks.Point(91.0, 69.0), CourierChecks.Point(34.0, -181.0), CourierChecks.Point(Double.NaN, 69.0))) {
      assertEquals("bad_position", assertFailsWith<CourierInputError> {
        CourierQueue.enqueue(QueueState(), "cev-pos-00003", "A", "delivered", 1, position = bad)
      }.code)
    }
  }

  @Test
  fun cardDefaultsMeanNoCheck() {
    val c = MissionCard(id = "A", orderNo = 1, status = "assigned")
    assertNull(c.bagScanBags)
    assertNull(c.nearPinMaxM)
  }
}
