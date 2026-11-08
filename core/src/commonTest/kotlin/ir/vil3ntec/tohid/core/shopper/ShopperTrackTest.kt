package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.order
import ir.vil3ntec.tohid.core.shopper.TrackRules.StepState.DONE
import ir.vil3ntec.tohid.core.shopper.TrackRules.StepState.NOW
import ir.vil3ntec.tohid.core.shopper.TrackRules.StepState.STOP
import ir.vil3ntec.tohid.core.shopper.TrackRules.StepState.TODO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** پله‌های پیگیری = `K.orderSteps` ِ وب (`server/public/kit/order-kit.js`). */
class ShopperTrackTest {

  private fun ids(o: ir.vil3ntec.tohid.core.market.PublicOrder) = TrackRules.steps(o).map { it.id to it.state }

  @Test fun newDeliveryOrderIsOnTheFirstStep() {
    assertEquals(
      listOf("placed" to NOW, "accepted" to TODO, "prep" to TODO, "way" to TODO, "delivered" to TODO),
      ids(order("new", timeline = listOf("new" to 1L))),
    )
  }

  @Test fun pickupHasReadyAndPickedStepsWithTimes() {
    val o = order("ready_for_pickup", fulfillment = "pickup", timeline = listOf("new" to 1L, "accepted" to 2L, "picking" to 3L, "packed" to 4L, "ready_for_pickup" to 5L))
    val s = TrackRules.steps(o)
    assertEquals(listOf("placed", "accepted", "prep", "ready", "picked"), s.map { it.id })
    assertEquals(listOf(DONE, DONE, DONE, NOW, TODO), s.map { it.state })
    assertEquals(4L, s[2].at, "آماده‌سازی: زمانِ آخرین کلیدِ همان پله (packed)")
    assertNull(s[4].at)
  }

  @Test fun deliveredIsAllDoneAndStopsRefreshing() {
    val o = order("delivered", timeline = listOf("new" to 1L, "accepted" to 2L, "out_for_delivery" to 3L, "delivered" to 4L))
    assertTrue(TrackRules.steps(o).all { it.state == DONE })
    assertTrue(TrackRules.finished(o))
    assertNull(TrackRules.refreshEveryMs(o))
    assertEquals("ok", TrackRules.kind("delivered"))
  }

  @Test fun rejectedOrderEndsWithAStopStepAndNoLaterSteps() {
    val o = order("rejected", timeline = listOf("new" to 1L, "rejected" to 6L))
    assertEquals(listOf("placed" to DONE, "rejected" to STOP), ids(o))
    assertEquals(6L, TrackRules.steps(o).last().at)
    val c = order("cancelled", timeline = listOf("new" to 1L, "accepted" to 2L))
    assertEquals(listOf("placed" to DONE, "accepted" to DONE, "cancelled" to STOP), ids(c))
    assertEquals(9000L, TrackRules.steps(c).last().at, "بی زمانِ لغو ⇒ updatedAt")
    assertEquals("err", TrackRules.kind("auto_rejected"))
  }

  @Test fun failedDeliveryStaysOnTheWayAndKeepsRefreshing() {
    val o = order("delivery_failed", timeline = listOf("new" to 1L, "accepted" to 2L, "out_for_delivery" to 3L, "delivery_failed" to 4L))
    assertEquals(NOW, TrackRules.steps(o)[3].state)
    assertEquals("review", TrackRules.kind(o.status))
    assertFalse(TrackRules.finished(o))
    assertEquals(30_000L, TrackRules.refreshEveryMs(o))
  }

  @Test fun onlyServerPaidCountsAsPaid() {
    assertEquals(0, TrackRules.paidMinor(order("delivered", paymentStatus = "pending")))
    assertEquals(25000, TrackRules.dueMinor(order("delivered", paymentStatus = "unpaid")))
    assertEquals(0, TrackRules.dueMinor(order("delivered", paymentStatus = "paid")))
  }
}
