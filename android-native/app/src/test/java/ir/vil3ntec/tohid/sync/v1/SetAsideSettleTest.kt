package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.core.sync.RejectedSettle
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SetAside
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 *  دستگاهِ شاگرد پس از «کنار بگذار» و «اصلاح و ثبت» ِ صاحب با سرور یکی می‌شود (`SetAsideCommit` + `RejectedSettle` +
 *  ژورنالِ `SyncCommit`) — همان کدِ گوشیِ نسخهٔ ۳ و کامپیوتر (این آزمون روی هر دو اجرا می‌شود).
 *  «سرور» درختی است که فقط opهای پذیرفته را دارد؛ «دیسک» دفتر، سایه و صف. سنجه پس از هر سناریو: موجودی، قرض، شمار و
 *  جمعِ فروش، دریافتی و برگشتِ دستگاه **دقیقاً** همان سرور؛ سند در `setAside` کامل و دیدنی؛ هیچ op ای (نه تفاضلِ بعدی،
 *  نه بارگذاریِ کامل) آن را دوباره نمی‌فرستد؛ تصمیمِ تکراری، دیررس، پیش از دفترِ کنار و بسته شدنِ برنامه در هر گام
 *  همان دفتر را می‌دهد. پیش از این کار همین سنجه‌ها سرخ بودند: دفترِ شاگرد فروشِ ردشده را تا ابد می‌شمرد.
 */
class SetAsideSettleTest {

  private class Disk {
    var ledger = ShopData()
    var shadow = ""
    var cursor = 0L
    val queue = ArrayList<SyncOp>()
  }

  private class Crash : RuntimeException("برنامه بسته شد")
  private enum class At { NONE, BEFORE_LEDGER, AFTER_LEDGER, AFTER_COMMIT }

  private class Parts(private val disk: Disk, private val at: At, private val dead: BooleanArray) : SyncCommit.Parts {
    override fun shadow(): String = disk.shadow
    override fun commit(shadow: String, cursor: Long?) {
      if (dead[0]) throw Crash()
      disk.shadow = shadow
      cursor?.let { disk.cursor = it }
      if (at == At.AFTER_COMMIT) { dead[0] = true; throw Crash() }
    }
    override fun queued(): Set<String> = disk.queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) { disk.queue += ops }
  }

  private val journal = Files.createTempDirectory("set-aside").toFile().resolve("sync-journal-t.json")
  private fun session(disk: Disk, at: At = At.NONE, dead: BooleanArray = BooleanArray(1)) = SyncCommit(SyncCommit.Journal(journal), Parts(disk, at, dead))

  private var seq = 0
  private fun newId() = "t${seq++}"
  private val today = "2026-11-08"
  private var clock = 1_793_900_000_000L

  private val d0 = ShopData(
    products = listOf(Product(id = "sugar", name = "شکر", unit = "عدد", salePrice = 400.0), Product(id = "rice", name = "برنج", unit = "عدد", salePrice = 300.0)),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "sugar", units = 20.0), WarehouseEntry(id = "w2", productId = "rice", units = 40.0)),
    debtors = listOf(Debtor(id = "ahmad", name = "احمد")),
  )

  /** سرور: فقط آنچه پذیرفت */
  private var server = LedgerDiff.toJson(d0)
  /** دفترِ کنارِ صفِ همین دستگاه (`OpQueue.dropped` ⇒ op) */
  private val book = ArrayList<JsonObject>()
  private var nextBox = 0

  private fun synced(d: ShopData): Disk = Disk().apply { ledger = d; shadow = LedgerDiff.shadowOf(LedgerDiff.toJson(d)).toString() }

  /** `ShopStore.writeInternal` ⇒ `record` */
  private fun save(disk: Disk, next: ShopData) { disk.ledger = next; session(disk).record(next) }

  /** push: همه پذیرفته (سرور جلو می‌رود) یا همه رد (دفترِ کنار) */
  private fun push(disk: Disk, accept: Boolean): List<SyncOp> {
    val ops = disk.queue.toList()
    disk.queue.clear()
    if (accept) server = LedgerDiff.applyRemote(server, ops) else book += ops.map { it.toJson() }
    return ops
  }

  /** pull ِ opهای دستگاهِ دیگر */
  private fun pull(disk: Disk, ops: List<SyncOp>) = session(disk).absorb(ops, disk.ledger, disk.cursor + 1) { disk.ledger = it }

  /** صاحب «کنار گذاشت»/«اصلاح کرد» ⇒ ردیفِ `/mine` با بستهٔ همان opها (و مقدارِ امروزِ سرور برای ویرایش‌ها) */
  private fun decision(ops: List<SyncOp>, status: String, base: Map<String, Map<String, Any?>?> = emptyMap(), note: String = "مشتری نخواست") =
    ConflictInbox.Mine(
      id = "cf_${nextBox++}", table = ops.first().table, rowId = ops.first().rowId, opId = ops.first().opId, device = "pos-s",
      reason = "approval_required", status = status, at = 1, resolvedAt = 2, note = note, fixedDoc = if (status == ConflictInbox.FIXED) "sales/new" else "",
      settle = ConflictInbox.Settle("v1", ops.map { ConflictInbox.SettleOp(it.opId, it.table, it.rowId, it.type) }, base, false),
    )

  private fun settle(disk: Disk, vararg m: ConflictInbox.Mine, at: At = At.NONE, marks: SetAsideMarks? = null): Int {
    val dead = BooleanArray(1)
    return SetAsideCommit.run(
      session(disk, at, dead), { disk.queue.mapTo(HashSet()) { "${it.table}/${it.rowId}" } }, m.toList(), book,
      read = { if (dead[0]) throw Crash() else disk.ledger },
      write = {
        if (at == At.BEFORE_LEDGER) { dead[0] = true; throw Crash() }
        disk.ledger = it
        if (at == At.AFTER_LEDGER) { dead[0] = true; throw Crash() }
      },
      now = clock,
      marks = marks,
    )
  }

  private data class Figures(val stock: Map<String, Double>, val debt: Map<String, Double>, val sales: Int, val total: Double, val paid: Double, val returns: Double)

  private fun figures(d: ShopData): Figures {
    val live = d.sales.filter { it.status != "cancelled" }
    return Figures(
      d.products.associate { it.id to ShopStore.stock(d, it.id) }, d.debtors.associate { it.id to ShopStore.debt(d, it.id) },
      live.size, live.sumOf { it.finalTotal }, live.sumOf { it.paidAmount }, d.saleReturns.sumOf { it.amount },
    )
  }

  private fun serverLedger(): ShopData = LedgerDiff.fromJson(server)

  private fun assertSameAsServer(disk: Disk) {
    assertEquals(figures(serverLedger()), figures(disk.ledger))
    //  هیچ چیزی دوباره نمی‌رود: نه تفاضلِ بعدی، نه بارگذاریِ کاملِ دفتر
    assertEquals(0, session(disk).record(disk.ledger))
    assertTrue(disk.queue.isEmpty())
    val upload = LedgerDiff.diff(null, LedgerDiff.toJson(disk.ledger), 0).map { "${it.table}/${it.rowId}" }.toSet()
    val aside = disk.ledger.setAside.flatMap { doc -> doc.rows.flatMap { (t, rows) -> (rows as JsonArray).map { "$t/${((it as JsonObject)["id"] as JsonPrimitive).content}" } } }
    assertTrue("ردیفِ کنارگذاشته در بارگذاریِ کامل: ${aside.filter { it in upload }}", aside.none { it in upload })
  }

  private fun credit(d: ShopData, qty: Double = 3.0): ShopData {
    val r = SalesEngine.record(
      d, listOf(SalesEngine.CartLine("sugar", qty)),
      SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = "ahmad", seller = "علی"), today, clock++, ::newId,
    )
    return (r as SalesEngine.Result.Ok).data
  }

  /** شاگرد فروشِ قرضی می‌زند و سرور با همهٔ همراه‌ها رد می‌کند */
  private fun rejectedCredit(disk: Disk): List<SyncOp> {
    save(disk, credit(disk.ledger))
    val ops = push(disk, accept = false)
    assertTrue(ops.any { it.table == "sales" } && ops.any { it.table == "transactions" })
    //  پیش از تصمیم: دستگاه با سرور فرق دارد (همان باگِ #1003)
    assertFalse(figures(serverLedger()) == figures(disk.ledger))
    return ops
  }

  @Test fun `کنار بگذار ⇒ موجودی و قرض و جمع‌ها همان سرور؛ سند کامل و دیدنی؛ هیچ چیزی دوباره نمی‌رود`() {
    val s = synced(d0)
    val ops = rejectedCredit(s)
    val saleId = ops.first { it.table == "sales" }.rowId
    assertEquals(1, settle(s, decision(ops, ConflictInbox.DISMISSED)))
    assertSameAsServer(s)
    assertEquals(20.0, ShopStore.stock(s.ledger, "sugar"), 0.0)
    assertEquals(0.0, ShopStore.debt(s.ledger, "ahmad"), 0.0)
    val doc = s.ledger.setAside.single()
    assertEquals(listOf(ConflictInbox.DISMISSED, "مشتری نخواست", saleId), listOf(doc.status, doc.note, doc.saleId))
    //  ⛔ هرگز پاک نمی‌شود: همهٔ ردیف‌های سند و همهٔ opها برای سابقه
    assertEquals(ops.map { it.opId }, doc.ops.map { (it["op_id"] as JsonPrimitive).content })
    assertTrue(doc.rows.keys.containsAll(listOf("sales", "saleItems", "transactions", "stockMovements", "auditLog")))
    assertTrue(s.ledger.sales.none { it.id == saleId })
    assertEquals(ops.map { it.opId }.toSet(), SetAside.settledOps(s.ledger))
  }

  @Test fun `اصلاح و ثبت ⇒ فاکتورِ تازهٔ صاحب یک بار، کهنه هیچ — هر ترتیبی`() {
    for (pullFirst in listOf(true, false)) {
      server = LedgerDiff.toJson(d0); book.clear()
      val s = synced(d0)
      val ops = rejectedCredit(s)
      //  صاحب روی دستگاهِ خودش همان سبد را ثبت می‌کند (با پرداختِ نقد) و سرور می‌پذیرد
      val owner = synced(serverLedger())
      val fixed = SalesEngine.record(owner.ledger, listOf(SalesEngine.CartLine("sugar", 3.0)), SalesEngine.Checkout(seller = "صاحب"), today, clock++, ::newId)
      save(owner, (fixed as SalesEngine.Result.Ok).data)
      val placed = push(owner, accept = true)
      val m = decision(ops, ConflictInbox.FIXED, note = "اصلاح و دوباره ثبت شد")
      if (pullFirst) { pull(s, placed); settle(s, m) } else { settle(s, m); pull(s, placed) }
      assertSameAsServer(s)
      assertEquals("ترتیب: $pullFirst", 17.0, ShopStore.stock(s.ledger, "sugar"), 0.0)
      assertEquals(0.0, ShopStore.debt(s.ledger, "ahmad"), 0.0)
      assertEquals(1, s.ledger.sales.size)
      assertEquals(ConflictInbox.FIXED, s.ledger.setAside.single().status)
    }
  }

  @Test fun `دوباره ثبت ⇒ همان #1003 — دستگاه دست نمی‌خورد و همان سرور است`() {
    val s = synced(d0)
    val ops = rejectedCredit(s)
    //  صاحب دوباره ثبت کرد: همان opها با شناسهٔ تازه به نامِ همین دستگاه (دستگاه آن‌ها را pull نمی‌کند)
    server = LedgerDiff.applyRemote(server, ops.mapIndexed { i, o -> o.copy(opId = "rs_cf_$i") })
    val m = decision(ops, ConflictInbox.RESUBMITTED)
    val before = s.ledger
    assertEquals(0, settle(s, m))
    assertTrue(before === s.ledger)
    assertSameAsServer(s)
    assertEquals(17.0, ShopStore.stock(s.ledger, "sugar"), 0.0)
    assertEquals(1200.0, ShopStore.debt(s.ledger, "ahmad"), 0.0)
    assertTrue(s.ledger.setAside.isEmpty())
  }

  @Test fun `تصمیمِ تکراری، دیررس، باز، پیش از دفترِ کنار و دستگاهِ دیگر همان دفتر را می‌دهد`() {
    val s = synced(d0)
    save(s, credit(s.ledger))
    val ops = s.queue.toList()
    val m = decision(ops, ConflictInbox.DISMISSED)
    //  تصمیم پیش از آنکه ردِ سرور به دفترِ کنار برسد (پاسخِ push گم شد) ⇒ هیچ؛ op در صف است
    assertEquals(0, settle(s, m))
    push(s, accept = false)
    //  پاسخِ کهنهٔ «باز» و همان تصمیم دو بار در یک فهرست
    val open = m.copy(status = ConflictInbox.OPEN, settle = null)
    assertEquals(1, settle(s, open, m, m))
    val once = s.ledger
    assertEquals(0, settle(s, m))
    assertEquals(0, settle(s, open))
    assertTrue(once === s.ledger)
    assertEquals(1, s.ledger.setAside.size)
    assertSameAsServer(s)
    //  دستگاهِ دیگرِ همان کاربر (دفترِ کنارش این opها را ندارد) دست نمی‌خورد
    val other = synced(d0)
    val keep = ArrayList(book); book.clear()
    assertEquals(0, settle(other, m))
    book += keep
    assertTrue(other.ledger.setAside.isEmpty())
  }

  @Test fun `بسته شدنِ برنامه در هر گام ⇒ پس از باز شدن همان دفتر، یک بار`() {
    for (at in listOf(At.BEFORE_LEDGER, At.AFTER_LEDGER, At.AFTER_COMMIT)) {
      server = LedgerDiff.toJson(d0); book.clear()
      journal.delete()
      val s = synced(d0)
      val ops = rejectedCredit(s)
      val m = decision(ops, ConflictInbox.DISMISSED)
      assertTrue(runCatching { settle(s, m, at = at) }.exceptionOrNull() is Crash)
      //  باز شدن (`ShopStore.load` ⇒ `recover` با دفترِ دیسک)، بعد دورِ بعد همان تصمیم دوباره
      session(s).recover(s.ledger)
      settle(s, m)
      settle(s, m)
      assertEquals("گام: $at", 1, s.ledger.setAside.size)
      assertSameAsServer(s)
      assertEquals(20.0, ShopStore.stock(s.ledger, "sugar"), 0.0)
      assertEquals(0.0, ShopStore.debt(s.ledger, "ahmad"), 0.0)
    }
  }

  @Test fun `لغوِ ردشدهٔ شاگرد کنار گذاشته ⇒ فاکتور همان «ثبت‌شده» ِ سرور`() {
    //  صاحب فروشِ نسیه زد و همه دارند
    val owner = synced(d0)
    save(owner, credit(owner.ledger, 2.0))
    val placed = push(owner, accept = true)
    val s = synced(d0)
    pull(s, placed)
    val saleId = placed.first { it.table == "sales" }.rowId
    assertSameAsServer(s)
    //  شاگرد لغو می‌کند ⇒ سرور رد (لغو در حدِ نقشش نیست)
    val cancelled = SalesEngine.cancel(s.ledger, saleId, today, clock++, ::newId, reason = "اشتباه")
    save(s, (cancelled as SalesEngine.Result.Ok).data)
    val ops = push(s, accept = false)
    assertEquals("cancelled", s.ledger.sales.single().status)
    val row = server["sales"]!!.let { (it as JsonArray).map { r -> r as JsonObject }.single { r -> (r["id"] as JsonPrimitive).content == saleId } }
    val base = mapOf("sales/$saleId" to ir.vil3ntec.tohid.data.repo.ApprovalRequestsRepository.plain(row)!!.mapKeys { it.key as String })
    settle(s, decision(ops, ConflictInbox.DISMISSED, base, note = "فاکتور درست بود"))
    assertSameAsServer(s)
    assertEquals("completed", s.ledger.sales.single().status)
    assertEquals(18.0, ShopStore.stock(s.ledger, "sugar"), 0.0)
    assertEquals(800.0, ShopStore.debt(s.ledger, "ahmad"), 0.0)
  }

  @Test fun `برگشتِ ردشدهٔ شاگرد کنار گذاشته ⇒ مقدارِ برگشته و پولِ برگشت همان سرور`() {
    val owner = synced(d0)
    save(owner, credit(owner.ledger, 4.0))
    val placed = push(owner, accept = true)
    val s = synced(d0)
    pull(s, placed)
    val sale = s.ledger.sales.single()
    val item = s.ledger.saleItems.single { it.saleId == sale.id }
    val req = ReturnFlow.Request(docId = "rd-1", saleId = sale.id, picks = mapOf(item.id to ReturnFlow.Pick(2.0)), reason = "خراب")
    val posted = ReturnFlow.post(s.ledger, req, today, clock++, ::newId)
    save(s, (posted as ReturnFlow.Outcome.Ok).value.data)
    val ops = push(s, accept = false)
    assertTrue(ops.any { it.table == "returns" } && ops.any { it.table == "saleItems" && it.type == SyncOp.UPDATE })
    settle(s, decision(ops, ConflictInbox.DISMISSED))
    assertSameAsServer(s)
    assertEquals(0.0, s.ledger.saleItems.single().returnedQty, 0.0)
    assertEquals(16.0, ShopStore.stock(s.ledger, "sugar"), 0.0)
    assertTrue(s.ledger.saleReturns.isEmpty())
  }
  @Test fun `برگشت به نسخهٔ کهنه‌تر — تصمیم دو بار نمی‌نشیند`() {
    val owner = synced(d0)
    save(owner, credit(owner.ledger, 4.0))
    val placed = push(owner, accept = true)
    val s = synced(d0)
    pull(s, placed)
    val sale = s.ledger.sales.single()
    val item = s.ledger.saleItems.single { it.saleId == sale.id }
    val req = ReturnFlow.Request(docId = "rd-2", saleId = sale.id, picks = mapOf(item.id to ReturnFlow.Pick(2.0)), reason = "خراب")
    save(s, (ReturnFlow.post(s.ledger, req, today, clock++, ::newId) as ReturnFlow.Outcome.Ok).value.data)
    val ops = push(s, accept = false)
    val m = decision(ops, ConflictInbox.DISMISSED)
    val mf = journal.resolveSibling(SetAsideMarks.v1Name("t"))
    val marks = SetAsideMarks({ mf.takeIf { it.isFile }?.readText() }, { mf.writeText(it) })
    assertEquals(1, settle(s, m, marks = marks))
    assertSameAsServer(s)
    //  دستهٔ ۳۰ (یا «بازسازی از سرور») دفتر را بی `setAside` نوشت (`LedgerCompatDiskTest`)؛ دفترِ کنارِ صف ولی ماند
    s.ledger = s.ledger.copy(setAside = emptyList())
    //  نشانِ «یک بار» اگر فقط دفتر بود: همان تصمیم دوباره برنامه می‌گرفت — `$inc` ِ وارونهٔ دوم
    assertTrue(RejectedSettle.plan(m, book, LedgerDiff.toJson(s.ledger), SetAside.doneIds(s.ledger)) is RejectedSettle.Result.Ok)
    assertEquals(0, settle(s, m, marks = marks))
    assertEquals(0.0, s.ledger.saleItems.single().returnedQty, 0.0)
    assertEquals(16.0, ShopStore.stock(s.ledger, "sugar"), 0.0)
    assertEquals(figures(serverLedger()), figures(s.ledger))
    //  و کارگر دیگر هر ۵ دقیقه نمی‌پرسد
    assertFalse(SetAside.openRejected(book, s.ledger, marks.read().ops))
    assertEquals(setOf(m.id), marks.read().ids)
  }
}
