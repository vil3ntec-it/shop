package ir.vil3ntec.tohid.desktop.shots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.internal.AssumptionViolatedException
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch

/**
 *  خودِ نگهبانِ مهلت (`RenderWatchdog`): آزمونِ گیرکرده در مهلت شکست می‌خورد با رونوشتِ نخ‌ها (جای دقیقِ گیر)، آزمونِ بعدیِ همان
 *  کلاس کنار می‌رود، و کلاسِ دیگر دست نمی‌خورد. بی پنجره و بی `TOHID_RENDER_OUT` (همان آزمونِ سدِ کامپیوتر).
 */
class RenderWatchdogTest {

  private fun desc(cls: String, name: String) = Description.createTestDescription(cls, name)

  @Test fun stuckTestFailsFastWithThreadDumpAndSkipsTheRestOfItsClass() {
    val gate = CountDownLatch(1)
    val cls = "watchdog.SelfTest" + System.nanoTime()
    val stuck = object : Statement() { override fun evaluate() { gate.await() } }
    val t0 = System.nanoTime()
    try {
      RenderWatchdog(300, { null }).apply(stuck, desc(cls, "hangs")).evaluate()
      fail("گیر باید شکست شود")
    } catch (e: AssertionError) {
      val ms = (System.nanoTime() - t0) / 1_000_000
      assertTrue("در مهلت: ${ms}ms", ms < 5_000)
      val msg = e.message.orEmpty()
      assertTrue(msg, msg.contains("hangs"))
      assertTrue("رونوشتِ نخِ آزمون با جای گیر", msg.contains("\"render-test\"") && msg.contains("CountDownLatch.await"))
      assertTrue("بن‌بست‌یاب اجرا شد", msg.contains("بن‌بست:"))
    } finally {
      gate.countDown()
    }
    try {
      RenderWatchdog(300, { null }).apply(object : Statement() { override fun evaluate() = Unit }, desc(cls, "next")).evaluate()
      fail("آزمونِ بعدیِ همان کلاس باید کنار برود")
    } catch (_: AssumptionViolatedException) { /* درست */ }
    var ran = false
    RenderWatchdog(300, { null }).apply(object : Statement() { override fun evaluate() { ran = true } }, desc("$cls.other", "ok")).evaluate()
    assertTrue("کلاسِ دیگر دست نمی‌خورد", ran)
  }

  @Test fun failureAndSuccessPassThroughUnchanged() {
    val boom = IllegalStateException("boom")
    try {
      RenderWatchdog(5_000, { null }).apply(object : Statement() { override fun evaluate() = throw boom }, desc("watchdog.Pass", "fails")).evaluate()
      fail()
    } catch (e: IllegalStateException) { assertEquals(boom, e) }
    var n = 0
    RenderWatchdog(5_000, { null }).apply(object : Statement() { override fun evaluate() { n++ } }, desc("watchdog.Pass", "passes")).evaluate()
    assertEquals(1, n)
  }
}
