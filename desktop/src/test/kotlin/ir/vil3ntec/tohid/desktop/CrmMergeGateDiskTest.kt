package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  CRM-10 — «ادغامِ مشتری» روی همگام‌سازیِ v3 بسته است (`ShopStore.mergeBlockedReason`،
 *  `mergeCustomers`): دکمه دلیلِ یک‌خطی می‌گیرد و هیچ نوشتن یا opی ساخته نمی‌شود. روی v1 همان
 *  درِ ادغام باز است.
 */
class CrmMergeGateDiskTest {

  private val ctx = DesktopContext
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val engine get() = SyncV1Engine.of(ctx)

  private fun clean() {
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private val twins = ShopData(
    debtors = listOf(
      Debtor(id = "gate1-c1", name = "احمد", phone = "+93700123456", createdAt = 10),
      Debtor(id = "gate1-c2", name = "احمد کابلی", phone = "+93799555444", phone2 = "0700123456", createdAt = 20),
    ),
    transactions = listOf(
      DebtTransaction(id = "gate1-t1", debtorId = "gate1-c1", type = "give", amount = 300.0, date = "2026-09-01"),
      DebtTransaction(id = "gate1-t2", debtorId = "gate1-c2", type = "give", amount = 1000.0, date = "2026-06-01"),
    ),
  )

  @Test fun `همگام‌سازیِ v3 — دکمه بسته با دلیلِ یک‌خطی، و درِ ادغام نه می‌نویسد نه op می‌سازد`() {
    val store = open()
    assertTrue(runBlocking { store.save(twins) })
    val bytes = ledger.readBytes()
    val v1Queue = engine.queueSize()
    val v3Queue = SyncV3Runner.of(ctx).queueSize()

    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    assertEquals(StrShell.mergeNotOnV3, store.mergeBlockedReason())
    val r = runBlocking { store.mergeCustomers("gate1-c2", "gate1-c1") }
    assertEquals(StrShell.mergeNotOnV3, (r as CustomerMerge.Result.Failed).message)

    assertArrayEquals("دفتر دست نخورد", bytes, ledger.readBytes())
    assertEquals(v1Queue, engine.queueSize())
    assertEquals(v3Queue, SyncV3Runner.of(ctx).queueSize())
    assertNull(open().data.value.debtors.first { it.id == "gate1-c1" }.mergedInto)
  }

  @Test fun `همگام‌سازیِ v1 — دکمه باز است و همان در ادغام را می‌نشاند`() {
    val store = open()
    assertTrue(runBlocking { store.save(twins) })
    val q0 = engine.queueSize()

    assertNull(store.mergeBlockedReason())
    val r = runBlocking { store.mergeCustomers("gate1-c2", "gate1-c1") }
    assertTrue(r is CustomerMerge.Result.Ok)
    val disk = open().data.value
    assertEquals("gate1-c2", disk.debtors.first { it.id == "gate1-c1" }.mergedInto)
    assertEquals(setOf("gate1-c2"), disk.transactions.map { it.debtorId }.toSet())
    assertTrue("ادغام op ساخت", engine.queueSize() > q0)

    //  ادغامِ دوباره رد می‌شود و چیزی نمی‌نویسد
    val bytes = ledger.readBytes()
    assertTrue(runBlocking { store.mergeCustomers("gate1-c2", "gate1-c1") } is CustomerMerge.Result.Failed)
    assertArrayEquals(bytes, ledger.readBytes())
  }
}
