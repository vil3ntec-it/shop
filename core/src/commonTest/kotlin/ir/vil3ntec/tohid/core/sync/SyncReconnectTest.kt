package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** گردشِ `SyncReconnect` روی هر هدف (JVM و شبیه‌سازِ iOS)؛ بردارهای مشترک با وب: `jvmTest/…/SyncOutageVectorsTest.kt` */
class SyncReconnectTest {

  /** online ⇒ درنگ؛ تیک صبر می‌کند؛ online ِ دوباره دراز نمی‌کند؛ کاربر درنگ را پایان می‌دهد */
  @Test
  fun firstRoundAfterReconnectIsSpreadAndUserNeverWaits() {
    var until = 0L
    fun ask(cause: String, now: Long, random: Double = 0.4): Long {
      val h = SyncReconnect.hold(until, cause, now, random)
      until = h.until
      return h.waitMs
    }
    assertEquals(6_000L, ask(SyncReconnect.RECONNECT, 10_000))
    assertEquals(4_000L, ask(SyncReconnect.TIMER, 12_000), "تیک تا پایانِ همان درنگ صبر می‌کند")
    assertEquals(3_000L, ask(SyncReconnect.RECONNECT, 13_000, random = 1.0), "online ِ دوباره همان پایان")
    assertEquals(0L, ask(SyncReconnect.USER, 13_500), "«همگام‌سازی حالا» هرگز صبر نمی‌کند")
    assertEquals(0L, until, "دورِ کاربر درنگ را پایان داد")
    assertEquals(0L, ask(SyncReconnect.TIMER, 13_600), "پس از دورِ کاربر تیک همان دم")
  }

  /** تلاشِ پس از شکست و تغییرِ تازه در میانهٔ درنگ همان دم می‌روند (فاصلهٔ تلاش همان) */
  @Test
  fun retryAndChangeRunAtOnceAndEndTheHold() {
    for (cause in listOf(SyncReconnect.RETRY, SyncReconnect.CHANGE, "boot")) {
      val held = SyncReconnect.hold(0L, SyncReconnect.RECONNECT, 0L, 0.9)
      assertTrue(held.waitMs > 0)
      assertEquals(SyncReconnect.Hold(0L, 0L), SyncReconnect.hold(held.until, cause, 1_000L, 0.9), cause)
    }
  }

  /** کران‌دار: هرگز بیش از سقف، و سقف زیرِ آستانهٔ اعلانِ قطعی */
  @Test
  fun delayIsBoundedBelowTheOutageNotice() {
    assertTrue(SyncReconnect.SPREAD_MS < SyncOutage.SUSTAIN_MS)
    for (r in listOf(-1.0, 0.0, 0.25, 0.5, 0.75, 0.999, 1.0, 2.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
      val ms = SyncReconnect.delayMs(r)
      assertTrue(ms in 0L..SyncReconnect.SPREAD_MS, "random=$r ⇒ $ms")
    }
    assertEquals(0L, SyncReconnect.delayMs(Double.NaN))
    assertEquals(SyncReconnect.SPREAD_MS, SyncReconnect.delayMs(1.0))
  }
}
