package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 *  `ShopStore.appendAudit` (از راهِ `LedgerWriter.edit`): ردیفِ سابقهٔ صفِ چاپ
 *  (`no_sale`، `reprint`) روی دفترِ همان لحظه و زیرِ همان قفلِ نوشتن ساخته
 *  می‌شود. فروشی که هم‌زمان ثبت شد نه گم می‌شود و نه opِ حذف می‌سازد.
 */
class LedgerWriterTest {

  /** دیسکِ ساختگی: نوشتن کند است تا دو نوشتن حتماً روی هم بیفتند */
  private class Disk {
    @Volatile var ledger = ShopData()
    val writes = ArrayList<ShopData>()
    val inWrite = CountDownLatch(1)
    fun write(next: ShopData): Boolean {
      inWrite.countDown()
      Thread.sleep(80)
      synchronized(writes) { writes += next }
      ledger = next
      return true
    }
  }

  private val sale = Sale(id = "s1", finalTotal = 500.0, createdAt = 10)
  private fun noSale(d: ShopData) =
    AuditEntry(id = "a1", type = "no_sale", createdAt = 20, by = "علی", reason = "پولِ خرد", approvedBy = "علی",
      notes = "فروش‌های دفترِ همان لحظه: ${d.sales.size}")

  @Test fun `فروشِ هم‌زمان و ردیفِ سابقه هر دو می‌مانند`() {
    val lock = Any()
    val disk = Disk()
    val w = LedgerWriter(lock, { disk.ledger }, { disk.write(it) })
    //  صندوق فروش را ثبت می‌کند (نوشتنش قفل را گرفته)…
    val cashier = Thread { w.save(disk.ledger.copy(sales = listOf(sale))) }
    cashier.start()
    assertTrue(disk.inWrite.await(2, TimeUnit.SECONDS))
    //  …و همان وقت تلاشِ دوبارهٔ رسید تمام می‌شود و ردیفِ سابقه می‌خواهد
    val spooler = Thread { w.edit(LedgerWriter.appendAudit { noSale(it) }) }
    spooler.start()
    cashier.join(); spooler.join()
    assertEquals(listOf("s1"), disk.ledger.sales.map { it.id })
    assertEquals(listOf("a1"), disk.ledger.auditLog.map { it.id })
    assertEquals("ردیف از دفترِ پس از فروش ساخته شد", "فروش‌های دفترِ همان لحظه: 1", disk.ledger.auditLog.single().notes)
    //  هیچ نوشتنی دفتری بی فروش پس از فروش ننوشت (وگرنه record opِ حذف می‌ساخت)
    assertTrue(disk.writes.last().sales.isNotEmpty())
  }

  @Test fun `راهِ کهنه — خواندن بیرونِ قفل فروش را گم می‌کرد`() {
    val lock = Any()
    val disk = Disk()
    val w = LedgerWriter(lock, { disk.ledger }, { disk.write(it) })
    val stale = disk.ledger                       // `store.data.value` پیش از رسیدنِ فروش
    val cashier = Thread { w.save(disk.ledger.copy(sales = listOf(sale))) }
    cashier.start()
    assertTrue(disk.inWrite.await(2, TimeUnit.SECONDS))
    val spooler = Thread { w.save(stale.copy(auditLog = stale.auditLog + noSale(stale))) }
    spooler.start()
    cashier.join(); spooler.join()
    assertTrue("همان باگی که `edit` می‌بندد", disk.ledger.sales.isEmpty())
  }

  @Test fun `appendAudit ردیف را ته سابقه می‌گذارد و بقیهٔ دفتر را دست نمی‌زند`() {
    val d = ShopData(sales = listOf(sale), auditLog = listOf(AuditEntry(id = "a0", type = "shift_open")))
    val out = LedgerWriter.appendAudit { noSale(it) }(d)
    assertEquals(listOf("a0", "a1"), out.auditLog.map { it.id })
    assertEquals(d.copy(auditLog = d.auditLog), out.copy(auditLog = d.auditLog))
  }
}
