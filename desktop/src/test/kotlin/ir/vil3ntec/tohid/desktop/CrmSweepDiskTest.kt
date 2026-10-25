package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  CRM-10 — `ShopStore.sweepMerged` پس از Pull و در شروع، با همان دیسک و همان موتورِ Sync v1:
 *  دستگاهِ «ب» همین `ShopStore` است و دستگاهِ «الف» دفترِ خالص با opهایی که `LedgerDiff`
 *  برایش می‌سازد (همان opهایی که سرور به «ب» می‌دهد).
 */
class CrmSweepDiskTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val ledger get() = File(dir, "shop-data.json")
  private val kekFile get() = File(File(ctx.root, "keys"), "atrest-kek.key")
  private val engine get() = SyncV1Engine.of(ctx)

  private fun clean() {
    ShopPolicy.setCrmReports(ctx, false)
    HeartbeatCache(ctx).forget()
    SoftLock.refresh(ctx)
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("before-restore") }
      .forEach { it.deleteRecursively() }
    File(dir, "atrest").deleteRecursively()
    kekFile.delete()
    AtRest.keyring(ctx).forget()
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private fun give(id: String, who: String, amount: Double, date: String) =
    DebtTransaction(id = id, debtorId = who, type = "give", amount = amount, date = date)

  /** «احمد» دو بار ثبت شده؛ ردیف‌ها با پیشوندِ آزمون تا با آزمون‌های دیگرِ همین موتور قاطی نشوند */
  private val base = ShopData(
    debtors = listOf(
      Debtor(id = "crm9-c1", name = "احمد", phone = "+93700123456", createdAt = 10),
      Debtor(id = "crm9-c2", name = "احمد کابلی", phone = "+93799555444", phone2 = "0700123456", createdAt = 20),
    ),
    transactions = listOf(give("crm9-t1", "crm9-c1", 300.0, "2026-09-01"), give("crm9-t2", "crm9-c2", 1000.0, "2026-06-01")),
    sales = listOf(Sale(id = "crm9-s7", invoiceNumber = 7, finalTotal = 300.0, paymentMethod = "credit", debtorId = "crm9-c1", date = "2026-09-01")),
  )
  private val late = give("crm9-late", "crm9-c1", 70.0, "2026-10-18")

  private var seq = 0
  private fun newId() = "crm9-a${seq++}"

  /** opهایی که دستگاهی با دفترِ [before] برای رسیدن به [after] می‌سازد */
  private fun opsOf(before: ShopData, after: ShopData): List<SyncOp> =
    LedgerDiff.diff(LedgerDiff.shadowOf(before), LedgerDiff.toJson(after))

  private fun applied(d: ShopData, ops: List<SyncOp>): ShopData =
    LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(d), ops))

  /** opهایی که از [from] به بعد به صف رفتند — صف از آزمون‌های دیگرِ همین موتور هم پر است */
  private fun added(from: Int): List<SyncOp> = engine.queuedOps().drop(from)

  /** UPDATE ِ `debtorId`/`mergedInto` و INSERT ِ ردِ دفترچه — هیچ چیزِ دیگر، هرگز DELETE */
  private fun assertTargeted(ops: List<SyncOp>) {
    assertFalse("sweep هرگز DELETE نمی‌سازد: $ops", ops.any { it.type == SyncOp.DELETE })
    for (op in ops) {
      val keys = op.fields?.keys.orEmpty()
      val ok = when (op.table) {
        "transactions", "sales" -> op.type == SyncOp.UPDATE && keys == setOf("debtorId")
        "debtors" -> op.type == SyncOp.UPDATE && keys == setOf("mergedInto")
        "auditLog" -> op.type == SyncOp.INSERT
        else -> false
      }
      assertTrue("op ِ بیرون از sweep: $op", ok)
    }
  }

  private fun merged(): ShopData =
    (CustomerMerge.merge(base, "crm9-c2", "crm9-c1", "2026-10-19", 1, ::newId) as CustomerMerge.Result.Ok).data

  /** همان Pull ِ `SyncV1Worker`: `absorbRemote` با `applyFromServerNow` */
  private fun ShopStore.pull(ops: List<SyncOp>) =
    engine.absorbRemote(ops, read = { data.value }, write = { applyFromServerNow(it) })

  @Test fun `ادغام روی یک دستگاه، sweep روی دیگری پس از Pull؛ sweep ِ دوم و شروعِ دوباره هیچ op نمی‌سازند`() {
    ShopPolicy.setCrmReports(ctx, true)
    val b = open()
    assertTrue(runBlocking { b.save(base) })
    //  «ب» آفلاین، پیش از رسیدنِ ادغام، روی «احمد» ِ کهنه قرض داد
    val bLocal = base.copy(transactions = base.transactions + late)
    assertTrue(runBlocking { b.save(bLocal) })

    //  «الف» c1 را در c2 ادغام کرد؛ سرور opهایش را به «ب» می‌دهد
    val a = (CustomerMerge.merge(base, "crm9-c2", "crm9-c1", "2026-10-19", 1, ::newId) as CustomerMerge.Result.Ok).data
    val q0 = engine.queueSize()
    b.pull(opsOf(base, a))
    assertEquals("Pull خودش op نمی‌سازد", q0, engine.queueSize())
    assertEquals("crm9-c2", b.data.value.debtors.first { it.id == "crm9-c1" }.mergedInto)
    assertEquals("crm9-c1", b.data.value.transactions.first { it.id == "crm9-late" }.debtorId)

    //  sweep ِ پس از Pull: فقط ردیفِ دیررسیده و یک ردِ دفترچه ⇒ دقیقاً دو op
    val beforeSweep = b.data.value
    assertEquals(true, runBlocking { b.sweepMerged() })
    assertEquals(q0 + 2, engine.queueSize())
    assertTargeted(added(q0))
    assertEquals(setOf("transactions" to SyncOp.UPDATE, "auditLog" to SyncOp.INSERT), added(q0).map { it.table to it.type }.toSet())
    val swept = b.data.value
    assertEquals("crm9-c2", swept.transactions.first { it.id == "crm9-late" }.debtorId)
    assertEquals(1370.0, ShopStore.debt(swept, "crm9-c2"), 0.0)
    assertEquals("crm9-c2", swept.auditLog.last().refId)
    assertEquals(swept, open().data.value)                       // روی دیسک نشست

    //  خودتوان: دومین sweep و شروعِ دوبارهٔ برنامه هیچ چیزی نمی‌نویسند و هیچ op نمی‌سازند
    assertNull(runBlocking { b.sweepMerged() })
    val restarted = ShopStore(ctx)
    runBlocking { restarted.loadAndSummarize() }
    assertEquals(q0 + 2, engine.queueSize())
    assertEquals(swept, restarted.data.value)

    //  بی پینگ‌پنگ: «الف» هم پیش از دیدنِ sweep ِ «ب»، همان ردیفِ دیررسیده را خودش جابه‌جا کرد
    val aGotLate = applied(a, opsOf(base, bLocal))
    val aSwept = CustomerMerge.sweep(aGotLate, "2026-10-20", 2, ::newId)!!
    //  تبادل: هر کدام opهای sweep ِ دیگری را می‌گیرد ⇒ هیچ‌کدام دیگر کاری ندارند
    val aFinal = applied(aSwept, opsOf(beforeSweep, swept))
    assertNull(CustomerMerge.sweep(aFinal))
    b.pull(opsOf(aGotLate, aSwept))
    assertNull(runBlocking { b.sweepMerged() })
    assertEquals(q0 + 2, engine.queueSize())
    assertEquals(aFinal.transactions.associate { it.id to it.debtorId }, b.data.value.transactions.associate { it.id to it.debtorId })
  }

  @Test fun `تنظیمِ خاموش (پیش‌فرض) — نه پس از Pull، نه در شروع، چیزی جابه‌جا نمی‌شود`() {
    val b = open()
    val merged = (CustomerMerge.merge(base, "crm9-c2", "crm9-c1", "2026-10-19", 1, ::newId) as CustomerMerge.Result.Ok).data
    val stray = merged.copy(transactions = merged.transactions + late)
    assertTrue(runBlocking { b.save(stray) })
    val q0 = engine.queueSize()
    val bytes = ledger.readBytes()

    assertNull(runBlocking { b.sweepMerged() })
    runBlocking { b.applyFromServer(stray) }
    runBlocking { ShopStore(ctx).loadAndSummarize() }
    assertEquals(q0, engine.queueSize())
    assertArrayEquals(bytes, ledger.readBytes())
    assertEquals("crm9-c1", open().data.value.transactions.first { it.id == "crm9-late" }.debtorId)

    //  همان دفتر با تنظیمِ روشن ⇒ جابه‌جا می‌شود (پس خاموش بودن بود که جلویش را گرفت)
    ShopPolicy.setCrmReports(ctx, true)
    assertEquals(true, runBlocking { b.sweepMerged() })
    assertEquals("crm9-c2", open().data.value.transactions.first { it.id == "crm9-late" }.debtorId)
  }

  @Test fun `دفترِ خوانده‌نشده (SEC-01، #331) — نوشتن بسته، sweep هیچ چیزی نمی‌نویسد و هیچ op نمی‌سازد`() {
    ShopPolicy.setCrmReports(ctx, true)
    AtRest.setWanted(ctx, true)
    val merged = (CustomerMerge.merge(base, "crm9-c2", "crm9-c1", "2026-10-19", 1, ::newId) as CustomerMerge.Result.Ok).data
    assertTrue(runBlocking { open().save(merged.copy(transactions = merged.transactions + late)) })
    val bytes = ledger.readBytes()

    //  کلید نیامد (نصبِ دوباره / انتقال) — همان دو «اجرا» ِ `AtRestDiskTest`
    assertTrue(kekFile.delete())
    AtRest.keyring(ctx).forget()
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, open().unreadable.value)
    AtRest.keyring(ctx).forget()
    val q0 = engine.queueSize()

    val store = ShopStore(ctx)
    runBlocking { store.loadAndSummarize() }                     // sweep ِ شروع
    assertEquals(AtRestException.Kind.KEY_MISSING, store.unreadable.value)
    assertNull(runBlocking { store.sweepMerged() })
    assertEquals(q0, engine.queueSize())
    assertArrayEquals("دفترِ واقعی دست نخورد", bytes, ledger.readBytes())
    assertEquals(ShopData(), store.data.value)
  }

  @Test fun `دو نمونه روی یک دیسک — فروشِ صفحه پس از بارِ کارگر نه گم می‌شود نه DELETE می‌گیرد`() {
    ShopPolicy.setCrmReports(ctx, true)
    val m = merged()
    val ui = open()
    assertTrue(runBlocking { ui.save(m.copy(transactions = m.transactions + late)) })
    //  کارگرِ همگام‌سازی دفتر را خواند…
    val worker = open()
    //  …و صندوق‌دار همان وقت فروخت (دفترِ حافظهٔ کارگر این را ندارد)
    val sale = Sale(id = "crm9-new", invoiceNumber = 9, finalTotal = 55.0, date = "2026-10-20")
    assertTrue(runBlocking { ui.save(ui.data.value.copy(sales = ui.data.value.sales + sale)) })
    val q0 = engine.queueSize()

    assertEquals(true, runBlocking { worker.sweepMerged() })
    assertTargeted(added(q0))
    assertEquals(2, added(q0).size)
    val disk = open().data.value
    assertTrue("فروشِ صفحه روی دیسک ماند", disk.sales.any { it.id == "crm9-new" })
    assertEquals("crm9-c2", disk.transactions.first { it.id == "crm9-late" }.debtorId)
    assertEquals(disk, worker.data.value)
  }

  @Test fun `شروعِ برنامه پس از Pull ِ کارگر — sweep ِ نمونهٔ صفحه ردیف‌های رسیده را حذف نمی‌کند`() {
    ShopPolicy.setCrmReports(ctx, true)
    val ui = open()
    val local = base.copy(transactions = base.transactions + late)
    assertTrue(runBlocking { ui.save(base) })
    assertTrue(runBlocking { ui.save(local) })
    //  کارگر ادغامِ دستگاهِ دیگر را Pull کرد. دفترِ حافظهٔ صفحه پیش‌تر کهنه می‌ماند؛ حالا همان را
    //  می‌گیرد (`LedgerFollow`) — sweep ِ صفحه در هر دو حال از دیسک می‌خواند
    val worker = open()
    val m = merged()
    worker.pull(opsOf(base, m))
    assertTrue(ui.data.value.auditLog.any { it.type == "customer_merge" })
    val q0 = engine.queueSize()

    assertEquals(true, runBlocking { ui.sweepMerged() })
    assertTargeted(added(q0))
    val disk = open().data.value
    assertTrue("ردِ ادغامِ رسیده ماند", disk.auditLog.any { it.type == "customer_merge" && it.refId == "crm9-c1" })
    assertEquals("crm9-c2", disk.debtors.first { it.id == "crm9-c1" }.mergedInto)
    assertEquals(setOf("crm9-c2"), disk.transactions.map { it.debtorId }.toSet())
    assertEquals(disk, ui.data.value)
  }

  @Test fun `قفلِ نرم — sweep چیزی نمی‌نویسد و op نمی‌سازد؛ با باز شدنِ قفل می‌نشیند`() {
    ShopPolicy.setCrmReports(ctx, true)
    val m = merged()
    val b = open()
    assertTrue(runBlocking { b.save(m.copy(transactions = m.transactions + late)) })
    val bytes = ledger.readBytes()
    val q0 = engine.queueSize()

    HeartbeatCache(ctx).write(buildJsonObject {
      put("subscription", buildJsonObject { put("active", JsonPrimitive(false)); put("status", JsonPrimitive("expired")) })
    })
    assertTrue("پیش‌شرطِ آزمون: قفلِ نرم بسته است", SoftLock.isLocked(ctx))
    assertNull(runBlocking { b.sweepMerged() })
    assertEquals(q0, engine.queueSize())
    assertArrayEquals(bytes, ledger.readBytes())

    HeartbeatCache(ctx).forget()
    assertFalse(SoftLock.isLocked(ctx))
    assertEquals(true, runBlocking { b.sweepMerged() })
    assertTargeted(added(q0))
  }

  @Test fun `همگام‌سازیِ v3 — sweep نمی‌نشیند، چون جابه‌جاییِ سندِ مالی آن‌جا به صندوقِ تعارض می‌رود`() {
    ShopPolicy.setCrmReports(ctx, true)
    val m = merged()
    val b = open()
    assertTrue(runBlocking { b.save(m.copy(transactions = m.transactions + late)) })
    val bytes = ledger.readBytes()
    val q0 = engine.queueSize()
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    try {
      assertNull(runBlocking { b.sweepMerged() })
      runBlocking { ShopStore(ctx).loadAndSummarize() }
    } finally {
      assertEquals("", SyncChoice.set(ctx, SyncChoice.V1, 0))
    }
    assertEquals(q0, engine.queueSize())
    assertArrayEquals(bytes, ledger.readBytes())
  }

  @Test fun `شروع — ذخیره‌ای که با بارِ دفتر خوانده و پس از sweep نوشته شد، sweep را برنمی‌گرداند و DELETE نمی‌سازد`() {
    ShopPolicy.setCrmReports(ctx, true)
    val m = merged()
    assertTrue(runBlocking { open().save(m.copy(transactions = m.transactions + late)) })
    val q0 = engine.queueSize()

    //  صفحه همان لحظه‌ای که `loaded` روشن می‌شود دفتر را برمی‌دارد و بعد فروشی رویش ثبت می‌کند
    val ui = ShopStore(ctx)
    val seen = runBlocking {
      val cur = async(start = CoroutineStart.UNDISPATCHED) { ui.loaded.first { it }; ui.data.value }
      ui.loadAndSummarize()
      cur.await()
    }
    assertEquals("صفحه دفترِ پس از sweep را دید", "crm9-c2", seen.transactions.first { it.id == "crm9-late" }.debtorId)
    val swept = engine.queuedOps().drop(q0)
    assertTargeted(swept)
    assertEquals(2, swept.size)

    val sale = Sale(id = "crm9-race", invoiceNumber = 11, finalTotal = 40.0, date = "2026-10-21")
    assertTrue(runBlocking { ui.save(seen.copy(sales = seen.sales + sale)) })
    val after = engine.queuedOps().drop(q0 + swept.size)
    assertEquals("فقط درجِ همان فروش", listOf("sales" to SyncOp.INSERT), after.map { it.table to it.type })
    val disk = open().data.value
    assertEquals("crm9-c2", disk.transactions.first { it.id == "crm9-late" }.debtorId)
    assertTrue(disk.auditLog.any { it.type == "customer_merge" && it.refId == "crm9-c2" })
    assertTrue(disk.sales.any { it.id == "crm9-race" })
  }
}
