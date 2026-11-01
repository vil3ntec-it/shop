package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ORD-03/04/08/09: نشانگر و دکمه‌های میزِ سفارش همان قاعده‌های `lib/order-desk.js` */
class OrderDeskTest {

  private val now = 1_800_000_000_000L

  @Test
  fun pauseWinsOverBusyAndPastMeansOpen() {
    assertEquals("open", OrderDesk.status(null, null, now))
    assertEquals("busy", OrderDesk.status(now + 60_000, null, now))
    assertEquals("paused", OrderDesk.status(now + 60_000, now + 1, now))
    assertEquals("open", OrderDesk.status(now - 1, now, now), "گذشته ⇒ باز")
  }

  @Test
  fun minutesLeftRoundsUp() {
    assertEquals(0, OrderDesk.minutesLeft(null, now))
    assertEquals(0, OrderDesk.minutesLeft(now, now))
    assertEquals(1, OrderDesk.minutesLeft(now + 1, now))
    assertEquals(20, OrderDesk.minutesLeft(now + 20 * 60_000, now))
    assertEquals(21, OrderDesk.minutesLeft(now + 20 * 60_000 + 1, now))
  }

  @Test
  fun pauseNeedsListedReasonAndNoteForOther() {
    assertTrue(OrderDesk.canPause(30, "staff", ""))
    assertFalse(OrderDesk.canPause(30, "other", " "))
    assertTrue(OrderDesk.canPause(30, "other", "برق نیست"))
    assertFalse(OrderDesk.canPause(4, "staff", ""))
    assertFalse(OrderDesk.canPause(1441, "staff", ""))
    assertFalse(OrderDesk.canPause(30, "lunch", ""))
    assertEquals(listOf("busy", "out_of_stock", "staff", "technical", "other"), OrderDesk.PAUSE_REASONS)
  }

  @Test
  fun boundedSettings() {
    assertEquals(5, OrderDesk.bounded(" 5 ", 1, 500))
    assertNull(OrderDesk.bounded("0", 1, 500))
    assertNull(OrderDesk.bounded("x", 1, 500))
    assertNull(OrderDesk.bounded("501", 1, 500))
  }
}
