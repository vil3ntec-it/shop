package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** چاپِ چندتکهٔ «تاریخچه» از صفِ چاپ: ترتیب، سقفِ تکه‌های منتظر و «لغو» در میانه */
class PrintBatchTest {

  private fun job(id: String) = PrintQueue.Job(id, PrintQueue.Kind.REPORT, "bt:00:11", "فاکتورهای امروز", 1_000L)

  @Test
  fun partsGoOneAfterAnotherAndNeverPileUpInTheQueue() {
    val b = PrintBatch(total = 4, ahead = 2)
    assertEquals(0, b.take())
    assertTrue(b.queued("j0"))
    assertEquals(1, b.take())
    assertTrue(b.queued("j1"))
    //  دو تکه منتظرند ⇒ تکهٔ سوم ساخته نمی‌شود تا یکی چاپ شود
    assertNull(b.take())
    assertEquals(emptyList(), b.finished("j0", printed = true))
    assertEquals(2, b.take())
    assertTrue(b.queued("j2"))
    b.finished("j1", true)
    assertEquals(3, b.take())
    assertTrue(b.queued("j3"))
    assertNull(b.take())
    b.finished("j2", true)
    b.finished("j3", true)
    assertEquals(4, b.printed)
    assertEquals(PrintBatch.Phase.DONE, b.phase)
    assertNull(b.take())
  }

  @Test
  fun cancelMidwayDiscardsWaitingPartsAndStopsTheRest() {
    val q = PrintQueue()
    val b = PrintBatch(total = 10, ahead = 2)
    //  تکهٔ ۱ چاپ شد؛ ۲ و ۳ در صف‌اند
    b.take(); q.enqueue(job("j0")); b.queued("j0")
    b.take(); q.enqueue(job("j1")); b.queued("j1")
    q.succeeded("j0"); b.finished("j0", true)
    b.take(); q.enqueue(job("j2")); b.queued("j2")
    assertEquals(listOf("j1", "j2"), q.all().map { it.id })

    val drop = b.cancel()
    assertEquals(listOf("j1", "j2"), drop)
    drop.forEach(q::discard)
    assertEquals(0, q.size)
    assertEquals(PrintBatch.Phase.CANCELLED, b.phase)
    assertEquals(1, b.printed)
    //  هیچ تکهٔ تازه‌ای پس از لغو
    assertNull(b.take())
    //  تکه‌ای که همان لحظه از ساخت رسید پذیرفته نمی‌شود و صدازننده کنارش می‌گذارد
    assertFalse(b.queued("late"))
    //  خبرِ دیررسِ کارِ کنارگذاشته چیزی را عوض نمی‌کند
    assertEquals(emptyList(), b.finished("j1", false))
    assertEquals(1, b.printed)
    //  لغوِ دوباره بی‌اثر
    assertEquals(emptyList(), b.cancel())
  }

  @Test
  fun partDiscardedFromTheDevicesPageCancelsTheRest() {
    val b = PrintBatch(total = 3, ahead = 2)
    b.take(); b.queued("j0")
    b.take(); b.queued("j1")
    //  «کنار گذاشتن» در صفحهٔ دستگاه‌ها ⇒ بقیه هم نه، نه چاپِ ناقصِ بی‌صدا
    assertEquals(listOf("j1"), b.finished("j0", printed = false))
    assertEquals(PrintBatch.Phase.CANCELLED, b.phase)
    assertNull(b.take())
  }

  @Test
  fun renderFailureStopsTheBatch() {
    val b = PrintBatch(total = 3)
    b.take(); b.queued("j0")
    assertEquals(listOf("j0"), b.fail())
    assertEquals(PrintBatch.Phase.FAILED, b.phase)
    assertNull(b.take())
  }

  @Test
  fun emptyBatchIsDoneAtOnce() {
    val b = PrintBatch(total = 0)
    assertEquals(PrintBatch.Phase.DONE, b.phase)
    assertNull(b.take())
  }

  @Test
  fun planSplitsByLengthAndCountKeepingOrder() {
    assertEquals(listOf(0..1, 2..2, 3..4), PrintBatch.plan(listOf(300, 400, 600, 300, 100), maxDots = 800))
    //  تکهٔ بلندتر از سقف کارِ خودش را می‌گیرد
    assertEquals(listOf(0..0, 1..1, 2..2), PrintBatch.plan(listOf(100, 5_000, 100), maxDots = 800))
    assertEquals(listOf(0..1, 2..3, 4..4), PrintBatch.plan(List(5) { 10 }, maxDots = 800, maxItems = 2))
    assertEquals(emptyList(), PrintBatch.plan(emptyList(), maxDots = 800))
  }
}
