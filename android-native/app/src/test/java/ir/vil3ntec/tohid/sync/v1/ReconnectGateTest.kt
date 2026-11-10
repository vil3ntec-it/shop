package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.SyncReconnect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  SYN-12: گردشِ درِ هجومِ پس از وصلِ دوباره که `SyncV1Worker` پیش از هر دورِ خودکارِ v1 و v3 می‌پرسد — همان قاعدهٔ
 *  `SyncReconnect` (بردارهای `core/test-vectors/sync-outage-v1.json`) و همان رفتارِ `online` ِ وب.
 */
class ReconnectGateTest {
  private var clock = 1_000L
  private var rnd = 0.5
  private fun gate() = ReconnectGate(now = { clock }, random = { rnd })

  @Test
  fun `بی‌نتی دیده نشده ⇒ هیچ درنگی، همان رفتارِ پیشین`() {
    val g = gate()
    repeat(3) { assertEquals(0L, g.beforeRound()) }
  }

  @Test
  fun `نخستین دورِ خودکار پس از برگشتنِ شبکه پخش می‌شود و دورهای دیگرِ همان پنجره تا پایانِ همان درنگ صبر می‌کنند`() {
    val g = gate()
    g.offline()
    assertEquals(7_500L, g.beforeRound())   // ۰٫۵ × ۱۵ ثانیه
    clock += 3_000
    rnd = 0.99
    assertEquals("کارگرِ دوم (دوره‌ای/پس از تغییر) پایانِ همان درنگ", 4_500L, g.beforeRound())
    g.offline()   // شبکه دوباره رفت و آمد: درنگِ جاری دراز نمی‌شود
    assertEquals(4_500L, g.beforeRound())
    clock += 4_500
    assertEquals("پس از درنگ همان دم", 0L, g.beforeRound())
    assertEquals(0L, g.beforeRound())
  }

  @Test
  fun `«همگام‌سازی حالا» هرگز صبر نمی‌کند و درنگ را پایان می‌دهد`() {
    val g = gate()
    g.offline()
    assertTrue(g.beforeRound() > 0)
    g.userAsked()
    assertEquals(0L, g.beforeRound())
    assertEquals("درنگ تمام شد", 0L, g.beforeRound())
    //  کاربر پیش از هر دورِ پس از برگشتنِ شبکه
    g.offline()
    g.userAsked()
    assertEquals(0L, g.beforeRound())
    assertEquals(0L, g.beforeRound())
  }

  @Test
  fun `درنگ کران‌دار است و عددِ تصادفیِ نادرست بی درنگ`() {
    for (r in listOf(0.0, 0.3, 0.999, 1.0, 7.0, -2.0)) {
      clock += 60_000
      rnd = r
      val g = gate()
      g.offline()
      val ms = g.beforeRound()
      assertTrue("random=$r ⇒ $ms", ms in 0L..SyncReconnect.SPREAD_MS)
    }
    rnd = Double.NaN
    val g = gate()
    g.offline()
    assertEquals(0L, g.beforeRound())
  }
}
