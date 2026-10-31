package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.DebtEntry
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  رسیدِ قرض در پروندهٔ مشتریِ کامپیوتر (F7) — دو نمونهٔ `ShopStore` روی یک پوشه، همان که برنامه دارد: صفحه
 *  (`desktop/Main.kt`) و کارگرِ همگام‌سازی (`SyncV1Worker` هر بار نمونهٔ تازه می‌سازد و Pull را روی دیسک می‌نشاند).
 *
 *  پیش‌تر صفحه `store.save(دفترِ خودش + رسید)` می‌کرد: رسیدِ گوشیِ دیگر که کارگر آورده بود از دیسک پاک می‌شد و
 *  `record` برایش DELETE به سرور می‌فرستاد. حالا `DebtEntry.record` ⇐ `ShopStore.update` روی دفترِ همین
 *  لحظهٔ دیسک. ردیف‌ها با پیشوندِ هر آزمون، چون سایه و صفِ همین موتور میانِ آزمون‌ها می‌ماند.
 */
class DebtReceiptStaleTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val engine get() = SyncV1Engine.of(ctx)

  private fun clean() {
    HeartbeatCache(ctx).forget()
    SoftLock.refresh(ctx)
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    File(dir, "atrest").deleteRecursively()
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private fun seed(p: String) = ShopData(
    debtors = listOf(Debtor(id = "$p-c1", name = "احمد")),
    transactions = listOf(DebtTransaction(id = "$p-g1", debtorId = "$p-c1", type = "give", amount = 1000.0, date = "2026-10-01", createdAt = 1)),
  )

  private fun phoneReceipt(p: String) =
    DebtTransaction(id = "$p-phone", debtorId = "$p-c1", type = "receive", amount = 300.0, date = "2026-10-30", createdAt = 2)

  private fun opsOf(before: ShopData, after: ShopData): List<SyncOp> =
    LedgerDiff.diff(LedgerDiff.shadowOf(before), LedgerDiff.toJson(after))

  /** همان دورِ کارگر (`SyncV1Worker.doWork`): نمونهٔ تازه، `load`، `absorbRemote` با `applyFromServerNow` */
  private fun workerPulls(ops: List<SyncOp>) {
    val w = open()
    engine.absorbRemote(ops, read = { w.data.value }, write = { w.applyFromServerNow(it) })
  }

  private fun receive(store: ShopStore, p: String, key: String) = runBlocking {
    DebtEntry.record(
      store, key,
      DebtEntry.Entry(debtorId = "$p-c1", kind = DebtorEngine.Kind.RECEIVE, amount = 200.0, date = "2026-10-30", notes = ""),
      today = "2026-10-30", now = 3,
    )
  }

  @Test fun `رسیدِ صفحه پس از Pull ِ کارگر رسیدِ گوشی را نگه می‌دارد و DELETE نمی‌سازد`() {
    val p = "dr7a"
    val ui = open()
    assertTrue(runBlocking { ui.save(seed(p)) })
    val q0 = engine.queueSize()

    workerPulls(opsOf(seed(p), seed(p).let { it.copy(transactions = it.transactions + phoneReceipt(p)) }))
    assertEquals("Pull خودش op نمی‌سازد", q0, engine.queueSize())
    assertEquals("صفحه نوشتنِ کارگر را نمی‌بیند (نمونهٔ جدا)", listOf("$p-g1"), ui.data.value.transactions.map { it.id })

    assertEquals(DebtEntry.Result.Ok("$p-k1", again = false), receive(ui, p, "$p-k1"))

    val disk = open().data.value
    assertEquals(listOf("$p-g1", "$p-phone", "$p-k1"), disk.transactions.map { it.id })
    assertEquals("مانده = ۱۰۰۰ − ۳۰۰ − ۲۰۰", 500.0, ShopStore.debt(disk, "$p-c1"), 0.0)
    assertEquals("صفحه همان دفترِ دیسک را دارد", disk, ui.data.value)
    val ops = engine.queuedOps().drop(q0)
    assertFalse("هیچ DELETE ای برای رسیدِ گوشی: $ops", ops.any { it.type == SyncOp.DELETE })
    assertEquals(setOf("transactions" to SyncOp.INSERT, "auditLog" to SyncOp.INSERT), ops.map { it.table to it.type }.toSet())
    assertEquals(listOf("$p-k1"), ops.filter { it.table == "transactions" }.map { it.rowId })
  }

  @Test fun `دو ضربه با یک کلید یک ردیف و یک بار op`() {
    val p = "dr7b"
    val ui = open()
    assertTrue(runBlocking { ui.save(seed(p)) })
    val q0 = engine.queueSize()

    assertEquals(DebtEntry.Result.Ok("$p-k1", again = false), receive(ui, p, "$p-k1"))
    assertEquals(DebtEntry.Result.Ok("$p-k1", again = true), receive(ui, p, "$p-k1"))

    val disk = open().data.value
    assertEquals(1, disk.transactions.count { it.type == "receive" })
    assertEquals(1, disk.auditLog.count { it.type == "customer_payment" })
    assertEquals("یک INSERT برای رسید و یکی برای سابقه", q0 + 2, engine.queueSize())
  }
}
