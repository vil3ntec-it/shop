package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** گزارشِ X/Z ِ صندوق (PAY-15) کارِ جدای صفِ چاپ است: نه رسید، نه چاپِ دوبارهٔ فاکتور */
class PrintQueueReportTest {

  private fun job(id: String, attempts: Int = 0) =
    PrintQueue.Job(id, PrintQueue.Kind.REPORT, "bt:00:11", "گزارش Z", 1_000L, attempts = attempts)

  @Test
  fun reportKeyRoundTrips() {
    assertEquals(PrintQueue.Kind.REPORT, PrintQueue.Kind.of("report"))
    assertEquals("report", PrintQueue.Kind.REPORT.key)
    //  کلیدِ ناشناخته همان رفتارِ پیشین
    assertEquals(PrintQueue.Kind.RECEIPT, PrintQueue.Kind.of("nope"))
  }

  @Test
  fun everyReportIsQueuedAndNeverCopyMarked() {
    val q = PrintQueue()
    assertTrue(q.enqueue(job("a")))
    assertTrue(q.enqueue(job("b")))
    assertEquals(2, q.size)
    assertFalse(job("c", attempts = 3).needsCopyMark)
    q.restore(q.all().map { it.copy(started = true) })
    assertEquals(listOf("a", "b"), q.all().map { it.id })
    assertFalse(q.all().first().needsCopyMark)
  }
}
