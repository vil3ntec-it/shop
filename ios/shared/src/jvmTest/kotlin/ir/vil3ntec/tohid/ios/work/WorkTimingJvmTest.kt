package ir.vil3ntec.tohid.ios.work

import kotlin.test.Test
import kotlin.test.assertEquals

/** زمان‌بندیِ کارهای دوره‌ایِ آیفون (همگام‌سازی، پشتیبان، دیدبان، یادآور) — همان قاعده‌های WorkManager ِ اندروید */
class WorkTimingJvmTest {
  private val q = 15 * 60_000L

  @Test
  fun neverRunIsDueNow() = assertEquals(0L, WorkTiming.dueIn(last = 0L, period = q, now = 1_000_000L))

  @Test
  fun recentRunWaitsTheRestOfThePeriod() = assertEquals(q - 60_000L, WorkTiming.dueIn(last = 10_000_000L, period = q, now = 10_060_000L))

  @Test
  fun overdueRunIsDueNow() = assertEquals(0L, WorkTiming.dueIn(last = 10_000_000L, period = q, now = 10_000_000L + q + 1))

  @Test
  fun clockMovedBackIsDueNow() = assertEquals(0L, WorkTiming.dueIn(last = 10_000_000L, period = q, now = 9_000_000L))

  @Test
  fun periodHasAndroidFloor() {
    assertEquals(q, WorkTiming.period(60_000L))
    assertEquals(12 * 60 * 60_000L, WorkTiming.period(12 * 60 * 60_000L))
  }

  @Test
  fun exponentialBackoffDoublesUpToTheCap() {
    var b = 10_000L
    val seen = ArrayList<Long>()
    repeat(8) { b = WorkTiming.nextBackoff(b, 10_000L, linear = false); seen += b }
    assertEquals(listOf(20_000L, 40_000L, 80_000L, 160_000L, 320_000L, 640_000L, q, q), seen)
  }

  @Test
  fun linearBackoffAddsTheBase() = assertEquals(60_000L, WorkTiming.nextBackoff(30_000L, 30_000L, linear = true))

  @Test
  fun backoffNeverBelowAndroidMinimum() = assertEquals(WorkTiming.MIN_BACKOFF_MS, WorkTiming.nextBackoff(1_000L, 1_000L, linear = true))
}
