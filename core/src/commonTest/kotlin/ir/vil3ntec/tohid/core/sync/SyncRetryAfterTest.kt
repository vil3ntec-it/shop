package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ۳-۱۳ (SYN-12، پرسشِ ۱ الف): `Retry-After` ِ سرور با سقفِ ۵ دقیقه — `SyncRetryAfter` روی هر هدف (JVM و شبیه‌سازِ iOS) */
class SyncRetryAfterTest {

  /** فقط عددِ ثانیه؛ بقیه «نگفت» */
  @Test
  fun parsesSecondsOnlyAndCapsAtFiveMinutes() {
    assertEquals(5_000L, SyncRetryAfter.parse("5"))
    assertEquals(42_000L, SyncRetryAfter.parse(" 42 "))
    assertEquals(0L, SyncRetryAfter.parse("0"))
    assertEquals(SyncRetryAfter.MAX_MS, SyncRetryAfter.parse("301"), "بیش از ۵ دقیقه ⇒ سقف")
    assertEquals(SyncRetryAfter.MAX_MS, SyncRetryAfter.parse("999999999"))
    for (bad in listOf(null, "", " ", "-5", "1.5", "5s", "Wed, 21 Oct 2026 07:28:00 GMT", "9999999999", "٥")) {
      assertNull(SyncRetryAfter.parse(bad), "نادرست ⇒ null: $bad")
    }
  }

  /** پایانِ «زودتر نه»: دیرترین، هرگز بیش از سقفِ همین لحظه؛ «نگفت» همان پایانِ پیشین */
  @Test
  fun untilKeepsTheLatestWithinTheCap() {
    assertEquals(15_000L, SyncRetryAfter.until(0L, 10_000L, 5_000L))
    assertEquals(15_000L, SyncRetryAfter.until(15_000L, 11_000L, 1_000L), "پاسخِ کوتاه‌تر پایان را جلو نمی‌کشد")
    assertEquals(41_000L, SyncRetryAfter.until(15_000L, 11_000L, 30_000L))
    assertEquals(15_000L, SyncRetryAfter.until(15_000L, 11_000L, null))
    assertEquals(15_000L, SyncRetryAfter.until(15_000L, 11_000L, 0L))
    assertEquals(10_000L + SyncRetryAfter.MAX_MS, SyncRetryAfter.until(0L, 10_000L, 60 * 60_000L), "سقف")
    //  پایانِ جاری که از سقفِ همین لحظه گذشته (ساعتِ به‌عقب‌رفته) کوتاه می‌شود
    assertEquals(SyncRetryAfter.MAX_MS + 1_000L, SyncRetryAfter.until(10 * 60 * 60_000L, 1_000L, 1_000L))
  }

  /** دورِ خودکار صبر می‌کند؛ «همگام‌سازی حالا» هرگز؛ گذشته ⇒ همین حالا */
  @Test
  fun automaticRoundsWaitUserNever() {
    assertEquals(4_000L, SyncRetryAfter.waitMs(15_000L, 11_000L, user = false))
    assertEquals(0L, SyncRetryAfter.waitMs(15_000L, 11_000L, user = true))
    assertEquals(0L, SyncRetryAfter.waitMs(15_000L, 15_000L, user = false))
    assertEquals(0L, SyncRetryAfter.waitMs(0L, 15_000L, user = false))
    assertEquals(SyncRetryAfter.MAX_MS, SyncRetryAfter.waitMs(Long.MAX_VALUE, 0L, user = false))
  }

  /** سقفِ درنگ همان سقفِ فاصلهٔ تلاشِ موتورها و بزرگ‌تر از پخشِ پس از وصلِ دوباره */
  @Test
  fun capMatchesEngineBackoffCap() {
    assertEquals(5 * 60_000L, SyncRetryAfter.MAX_MS)
    assertEquals(true, SyncRetryAfter.MAX_MS > SyncReconnect.SPREAD_MS)
  }
}
