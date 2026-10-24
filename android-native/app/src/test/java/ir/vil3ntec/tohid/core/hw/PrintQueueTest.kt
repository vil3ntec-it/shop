package ir.vil3ntec.tohid.core.hw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  صفِ چاپ (HW-02، سناریوی S16): به ترتیب، تلاشِ دوباره با فاصلهٔ فزاینده،
 *  ایستادن و پرسیدن وقتی «چاپِ معوق» خاموش است، چاپِ دوباره فقط با دلیل،
 *  و رسیدِ خودکارِ یک فروش هرگز دو بار.
 */
class PrintQueueTest {

  private fun job(id: String, kind: PrintQueue.Kind = PrintQueue.Kind.RECEIPT, saleId: String? = id, reason: String = "") =
    PrintQueue.Job(id = id, kind = kind, link = "net:192.168.1.50:9100", title = id, createdAt = 0, saleId = saleId, reason = reason)

  @Test fun `به ترتیب — تا سرِ صف چاپ نشده، بعدی منتظر است`() {
    val q = PrintQueue()
    q.enqueue(job("a")); q.enqueue(job("b"))
    assertEquals("a", q.next(0)?.id)
    q.failed("a", "چاپگر خاموش", now = 0)
    assertNull("پیش از رسیدنِ زمانِ تلاشِ بعدی هیچ کاری — و «b» جلو نمی‌زند", q.next(1_000))
    assertEquals("a", q.next(2_000)?.id)
    q.succeeded("a")
    assertEquals("b", q.next(2_000)?.id)
  }

  @Test fun `پله‌های تلاش و ایستادن وقتی چاپِ معوق خاموش است`() {
    val q = PrintQueue(backoffMs = listOf(1_000, 2_000))
    q.enqueue(job("a"))
    q.failed("a", "x", 0)
    assertEquals(1_000, q.all().first().nextAt)
    assertTrue(q.status() is PrintQueue.Status.Retrying)
    q.failed("a", "x", 1_000)
    assertEquals(3_000, q.all().first().nextAt)
    q.failed("a", "کاغذ تمام", 3_000)
    val s = q.status()
    assertTrue(s is PrintQueue.Status.NeedsUser)
    assertEquals("کاغذ تمام", (s as PrintQueue.Status.NeedsUser).job.lastError)
    assertNull("ایستاده — خودش دوباره نمی‌زند", q.next(1_000_000))
    q.retryNow(5_000)
    assertEquals("a", q.next(5_000)?.id)
  }

  @Test fun `چاپِ معوق روشن — بی‌پایان با آخرین فاصله`() {
    val q = PrintQueue(backoffMs = listOf(1_000, 2_000))
    q.deferred = true
    q.enqueue(job("a"))
    repeat(10) { q.failed("a", "x", 0) }
    assertFalse(q.all().first().held)
    assertEquals(2_000, q.all().first().nextAt)
  }

  @Test fun `رسیدِ خودکارِ یک فروش دو بار در صف نمی‌نشیند؛ چاپِ دوباره دلیل می‌خواهد`() {
    val q = PrintQueue()
    assertTrue(q.enqueue(job("r1", saleId = "s1")))
    assertFalse(q.enqueue(job("r2", saleId = "s1")))
    try {
      q.enqueue(job("p1", kind = PrintQueue.Kind.REPRINT, saleId = "s1", reason = " "))
      throw AssertionError("بی‌دلیل پذیرفته شد")
    } catch (e: IllegalArgumentException) { /* درست */ }
    assertTrue(q.enqueue(job("p2", kind = PrintQueue.Kind.REPRINT, saleId = "s1", reason = "کاغذ گیر کرد")))
    assertEquals(2, q.size)
  }

  @Test fun `کنار گذاشتن و بازگرداندن به همان ترتیب`() {
    val q = PrintQueue()
    q.enqueue(job("a")); q.enqueue(job("b")); q.enqueue(job("c"))
    q.discard("a")
    val saved = q.all()
    val q2 = PrintQueue()
    q2.restore(saved)
    assertEquals(listOf("b", "c"), q2.all().map { it.id })
    //  برگشته از فایل ⇒ `restored` (رسید نشانِ «چاپِ دوباره» می‌گیرد)
    assertEquals(PrintQueue.Status.Busy(saved[0].copy(restored = true), 1), q2.status())
    q2.succeeded("b"); q2.succeeded("c")
    assertEquals(PrintQueue.Status.Idle, q2.status())
  }
}
