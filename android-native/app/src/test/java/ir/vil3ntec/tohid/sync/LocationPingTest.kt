package ir.vil3ntec.tohid.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  دروازهٔ رضایتِ لوکیشن (F0-07، D-04).
 *
 *  پیش از این، هر بار باز شدنِ برنامه — اگر اجازهٔ اندروید بود — هر شش
 *  ساعت موقعیت می‌رفت، حتی بی حساب. حالا تا کاربر صریحاً «بله» نگفته،
 *  هیچ موقعیتی نمی‌رود و اجازهٔ اندروید هم پرسیده نمی‌شود.
 */
class LocationPingTest {

  private val now = 100L * LocationPing.EVERY_MS

  @Test
  fun `بی رضایت هیچ‌وقت نمی‌فرستد — حتی با اجازهٔ اندروید و سرور`() {
    assertFalse(LocationPing.maySend(consented = false, configured = true, granted = true, lastSentAt = 0L, now = now, force = false))
    assertFalse(LocationPing.maySend(consented = false, configured = true, granted = true, lastSentAt = 0L, now = now, force = true))
  }

  @Test
  fun `پیش‌فرض بی رضایت است`() {
    assertFalse(LocationPing.DEFAULT_CONSENT)
  }

  @Test
  fun `با رضایت همان مسیرِ قبلی کار می‌کند`() {
    assertTrue(LocationPing.maySend(consented = true, configured = true, granted = true, lastSentAt = 0L, now = now, force = false))
  }

  @Test
  fun `با رضایت هم بی سرور یا بی اجازهٔ اندروید نمی‌فرستد`() {
    assertFalse(LocationPing.maySend(consented = true, configured = false, granted = true, lastSentAt = 0L, now = now, force = false))
    assertFalse(LocationPing.maySend(consented = true, configured = true, granted = false, lastSentAt = 0L, now = now, force = false))
  }

  @Test
  fun `با رضایت هم پیش از شش ساعت دوباره نمی‌فرستد مگر به زور`() {
    val last = now - LocationPing.EVERY_MS + 1
    assertFalse(LocationPing.maySend(consented = true, configured = true, granted = true, lastSentAt = last, now = now, force = false))
    assertTrue(LocationPing.maySend(consented = true, configured = true, granted = true, lastSentAt = last, now = now, force = true))
    assertTrue(LocationPing.maySend(consented = true, configured = true, granted = true, lastSentAt = now - LocationPing.EVERY_MS, now = now, force = false))
  }

  @Test
  fun `بی رضایت اجازهٔ اندروید هم پرسیده نمی‌شود`() {
    assertFalse(LocationPing.mayAsk(consented = false, askedBefore = false))
  }

  @Test
  fun `با رضایت فقط یک بار پرسیده می‌شود`() {
    assertTrue(LocationPing.mayAsk(consented = true, askedBefore = false))
    assertFalse(LocationPing.mayAsk(consented = true, askedBefore = true))
  }
}
