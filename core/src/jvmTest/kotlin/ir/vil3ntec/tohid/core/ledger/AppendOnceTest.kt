package ir.vil3ntec.tohid.core.ledger

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *  رسیدِ قرض روی دفترِ همین لحظه، یک بار برای هر کلید — نوشتنِ هم‌زمانِ همگام‌سازی گم نمی‌شود.
 *
 *  «دفتر» این‌جا فهرستِ شناسهٔ ردیف‌هاست با همان قراردادِ `ShopStore.update` (خواندن ⇐ تبدیل ⇐ نوشتن زیرِ یک
 *  قفل)، و «همگام‌سازی» با همان قفل ردیف می‌افزاید — همان که `SyncV1Engine.absorbRemote` با قفلِ موتور می‌کند.
 *  نخ‌های واقعی، چون باگ مالِ هم‌زمانی است.
 */
class AppendOnceTest {

  private class Book(rows: List<String>) {
    private val lock = Any()
    @Volatile var disk: List<String> = rows
    @Volatile var unreadable = false
    @Volatile var refuse = false
    var writes = 0

    /** همان قراردادِ `ShopStore.update`: `null` هیچ، `true` نوشته شد، `false` قفلِ نرم یا دیسک */
    fun update(transform: (List<String>?) -> List<String>?): Boolean? = synchronized(lock) {
      val next = transform(if (unreadable) null else disk) ?: return@synchronized null
      if (refuse) return@synchronized false
      disk = next
      writes++
      true
    }

    /** Pull ِ همگام‌سازی: ردیفِ دستگاهِ دیگر، زیرِ همان قفل */
    fun syncWrite(row: String) = synchronized(lock) { disk = disk + row; writes++ }
  }

  private val has: (List<String>, String) -> Boolean = { d, k -> k in d }
  private val addRow: (List<String>, String) -> List<String>? = { d, k -> d + k }

  private fun Book.receipt(key: String, change: (List<String>, String) -> List<String>? = addRow) =
    AppendOnce.append(key, has, change) { t -> update(t) }

  @Test
  fun syncWriteThatLandsAfterTheScreenReadSurvivesTheReceipt() {
    val book = Book(listOf("a"))
    val inHand = book.disk
    book.syncWrite("remote")
    assertEquals(AppendOnce.Outcome.Wrote(listOf("a", "remote", "r1")), book.receipt("r1"))
    assertEquals(listOf("a", "remote", "r1"), book.disk)
    //  دفترِ در دستِ صفحه هیچ نقشی در نوشتن نداشت
    assertEquals(listOf("a"), inHand)
  }

  @Test
  fun doubleSubmitWithTheSameKeyRecordsOnce() {
    val book = Book(listOf("a"))
    assertEquals(AppendOnce.Outcome.Wrote(listOf("a", "r1")), book.receipt("r1"))
    assertEquals(AppendOnce.Outcome.Already, book.receipt("r1"))
    assertEquals(listOf("a", "r1"), book.disk)
    assertEquals(1, book.writes)
  }

  @Test
  fun concurrentSyncWritesAndDoubleClickedReceiptsAllSurviveOnce() {
    repeat(50) { round ->
      val book = Book(emptyList())
      val pool = Executors.newFixedThreadPool(8)
      val start = CountDownLatch(1)
      val remote = (1..200).map { "remote-$round-$it" }
      val receipts = (1..50).map { "receipt-$round-$it" }
      pool.submit { start.await(); remote.forEach { book.syncWrite(it); Thread.yield() } }
      //  هر رسید دو بار، از دو نخ — همان دو ضربهٔ پشتِ‌سرِهم
      for (k in receipts) repeat(2) { pool.submit { start.await(); book.receipt(k) } }
      start.countDown()
      pool.shutdown()
      assertEquals(true, pool.awaitTermination(30, TimeUnit.SECONDS))
      assertEquals((remote + receipts).toSet(), book.disk.toSet(), "دور $round")
      assertEquals(book.disk.size, book.disk.toSet().size, "ردیفِ دوتایی در دور $round")
    }
  }

  @Test
  fun unreadLedgerWritesNothing() {
    val book = Book(listOf("a")).apply { unreadable = true }
    assertEquals(AppendOnce.Outcome.Unread, book.receipt("r1"))
    assertEquals(0, book.writes)
  }

  @Test
  fun refusedChangeWritesNothing() {
    val book = Book(listOf("a"))
    assertEquals(AppendOnce.Outcome.Skipped, book.receipt("r1") { _, _ -> null })
    assertEquals(listOf("a"), book.disk)
    assertEquals(0, book.writes)
  }

  @Test
  fun failedWriteIsReportedAndTheSameKeyCanBeRetried() {
    val book = Book(listOf("a")).apply { refuse = true }
    assertEquals(AppendOnce.Outcome.Unsaved, book.receipt("r1"))
    assertEquals(listOf("a"), book.disk)
    book.refuse = false
    assertEquals(AppendOnce.Outcome.Wrote(listOf("a", "r1")), book.receipt("r1"))
  }

  @Test
  fun updateThatGaveUpBeforeTheChangeIsUnsaved() {
    assertEquals(AppendOnce.Outcome.Unsaved, AppendOnce.append("r1", has, addRow) { false })
  }

  @Test
  fun blankKeyIsRejected() {
    val book = Book(listOf("a"))
    assertFailsWith<IllegalArgumentException> { book.receipt(" ") }
    assertFailsWith<IllegalArgumentException> { AppendOnce.decide(listOf("a"), "", has, addRow) }
    assertEquals(0, book.writes)
  }
}
