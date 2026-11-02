package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.core.sync.ClockSkew.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S28b — هشدارِ قرمزِ ساعتِ دستگاه با مرزِ ۵ دقیقهٔ سرور */
class ClockSkewTest {

  private val day = 86_400_000L

  @Test
  fun s28bDeviceOnYear2000IsBehindAndRed() {
    //  D2: ساعت روی سالِ ۲۰۰۰، سرور ~۲۰۲۶ ⇒ اختلاف + (سرور جلوتر)
    val offset = 26L * 365 * day
    assertEquals(Level.BEHIND, ClockSkew.level(offset))
    assertTrue(ClockSkew.red(ClockSkew.level(offset)))
  }

  @Test
  fun s28bDeviceOneDayAheadIsRedToo() {
    assertEquals(Level.AHEAD, ClockSkew.level(-day))
  }

  @Test
  fun withinFiveMinutesIsQuiet() {
    assertEquals(Level.OK, ClockSkew.level(ClockSkew.SKEW_MS))
    assertEquals(Level.OK, ClockSkew.level(-ClockSkew.SKEW_MS))
    assertEquals(Level.BEHIND, ClockSkew.level(ClockSkew.SKEW_MS + 1))
    assertEquals(Level.AHEAD, ClockSkew.level(-ClockSkew.SKEW_MS - 1))
    assertFalse(ClockSkew.red(ClockSkew.level(null)))
  }

  @Test
  fun behindHighestSeenWarnsWithoutServer() {
    val seen = 1_800_000_000_000L
    assertEquals(Level.BEHIND, ClockSkew.level(null, seen - ClockSkew.SKEW_MS - 1, seen))
    assertEquals(Level.OK, ClockSkew.level(null, seen - ClockSkew.SKEW_MS, seen))
    assertEquals(Level.OK, ClockSkew.level(null, seen + day, seen))
    //  نامعلوم ⇒ بی هشدار
    assertEquals(Level.OK, ClockSkew.level(null, 0, seen))
    assertEquals(Level.OK, ClockSkew.level(null, seen, 0))
  }

  @Test
  fun serverAnswerWinsOverAStaleHighestSeen() {
    //  ساعت دیروز یک روز جلو بود (بالاترین دیده‌شده = فردا) و حالا درست است: سرور می‌گوید اختلاف نیست
    val now = 1_800_000_000_000L
    assertEquals(Level.OK, ClockSkew.level(1_000L, now, now + day))
    assertEquals(Level.BEHIND, ClockSkew.level(null, now, now + day))
  }

  @Test
  fun correctedTimeAndGapText() {
    assertEquals(1_000L + 500L, ClockSkew.corrected(1_000L, 500L))
    assertEquals(Triple(1L, 2L, 3L), ClockSkew.gap(-(day + 2 * 3_600_000L + 3 * 60_000L + 59_000L)))
    assertEquals(Triple(0L, 0L, 0L), ClockSkew.gap(59_000L))
  }
}
