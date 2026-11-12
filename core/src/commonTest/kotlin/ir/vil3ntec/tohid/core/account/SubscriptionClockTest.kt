package ir.vil3ntec.tohid.core.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionClockTest {
  private val day = SubscriptionClock.DAY
  private val t0 = 1_800_000_000_000L
  private fun sub(active: Boolean = true, ends: Long = t0 + 25 * day, grace: Long = 0, days: Int = 25, status: String = "active", permanent: Boolean = false) =
    SubscriptionClock.Cached(active, permanent, status, ends, grace, days)

  @Test fun daysCountDownWithoutInternet() {
    val s = sub()
    assertEquals(25, SubscriptionClock.daysLeft(s, t0))
    assertEquals(15, SubscriptionClock.daysLeft(s, t0 + 10 * day), "ده روز بی تپشِ تازه ⇒ ۱۵، نه همان ۲۵")
    assertEquals(1, SubscriptionClock.daysLeft(s, t0 + 24 * day + 1))
    assertEquals(0, SubscriptionClock.daysLeft(s, t0 + 26 * day))
  }

  @Test fun cachedActiveEndsAtItsOwnEnd() {
    val s = sub()
    assertTrue(SubscriptionClock.activeNow(s, t0 + 25 * day))
    assertFalse(SubscriptionClock.activeNow(s, t0 + 25 * day + 1), "پایان گذشت ⇒ دیگر فعال نیست، سرور چیزی نگفته باشد هم")
    assertTrue(SubscriptionClock.ended(s, t0 + 30 * day))
    assertFalse(SubscriptionClock.ended(s, t0 + day))
  }

  @Test fun graceCountsAndPermanentAndUnknown() {
    val g = sub(ends = t0, grace = t0 + 3 * day, days = 3)
    assertTrue(SubscriptionClock.activeNow(g, t0 + 2 * day), "در مهلت هنوز فعال")
    assertEquals(1, SubscriptionClock.daysLeft(g, t0 + 2 * day))
    assertTrue(SubscriptionClock.ended(g, t0 + 4 * day))
    val p = sub(permanent = true, ends = t0 + 100 * 365 * day)
    assertFalse(SubscriptionClock.ended(p, t0 + 200L * 365 * day))
    val trial = sub(ends = 0, days = 5, status = "trial")
    assertEquals(5, SubscriptionClock.daysLeft(trial, t0 + 30 * day), "پایانِ نامعلوم ⇒ همان عددِ تپش")
    assertFalse(SubscriptionClock.ended(trial, t0 + 30 * day))
    assertFalse(SubscriptionClock.ended(sub(active = false, status = "none"), t0), "هرگز اشتراکی نبوده ⇒ قفل نه")
    assertTrue(SubscriptionClock.ended(sub(active = false, status = "expired"), t0))
  }
}
