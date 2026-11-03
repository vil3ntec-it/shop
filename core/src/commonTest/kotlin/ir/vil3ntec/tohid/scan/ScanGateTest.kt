package ir.vil3ntec.tohid.scan

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `ScanGate` ِ مشترک (اندروید، کامپیوتر و آیفون) — همان پنجرهٔ ۱۲۰۰ میلی‌ثانیه‌ای ِ وب */
class ScanGateTest {

  @Test fun same_code_inside_the_window_is_dropped() {
    val gate = ScanGate()
    assertTrue(gate.accept("6260000000017", now = 10_000))
    assertFalse(gate.accept("6260000000017", now = 10_000 + 1_199))
    //  پس از پنجره همان کد دوباره پذیرفته می‌شود
    assertTrue(gate.accept("6260000000017", now = 10_000 + 1_200))
  }

  @Test fun another_code_passes_at_once_and_reset_forgets() {
    val gate = ScanGate()
    assertTrue(gate.accept("A", now = 5_000))
    assertTrue(gate.accept("B", now = 5_001))
    assertFalse(gate.accept("B", now = 5_002))
    gate.reset()
    assertTrue(gate.accept("B", now = 5_003))
  }

  @Test fun custom_window_and_default_clock() {
    val gate = ScanGate(windowMs = 50)
    assertTrue(gate.accept("X", now = 0))
    assertTrue(gate.accept("X", now = 50))
    //  بی `now`: ساعتِ دیواریِ دستگاه (همان System.currentTimeMillis ِ پیشین)
    assertTrue(ScanGate().accept("Y"))
  }
}
