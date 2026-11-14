package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 *  SEC-02 — برگشت، قرض/رسید، خرید و پرداخت به تأمین‌کننده در زنجیرهٔ سریِ دستگاه، با نشانِ «تا کجا» برای سندِ
 *  بی‌شماره ([DocChainState.docFrom]/[DocChainState.docAt]/[DocChainState.docIds])، و «بررسیِ یکپارچگی» ([DocChainAudit]).
 */
class DocChainDocsTest {

  private val t0 = 1_761_800_000_000L
  private val day = 24 * 60 * 60_000L

  private fun ret(id: String, at: Long, amount: Long = 2500) =
    ReturnFacts(id, at, "AFN", saleId = "s7", saleItemId = "i1", productId = "p1", qty = 1000, amount = amount, docId = "rd1", refund = "cash")
  private fun debt(id: String, at: Long, amount: Long = 9000) = DebtFacts(id, at, "AFN", customerId = "deb_1", direction = "give", amount = amount)
  private fun buy(id: String, at: Long) =
    PurchaseFacts(id, at, "AFN", supplierId = "sup_1", productId = "p1", qty = 12_000, unitPrice = 400, total = 4800, paid = 4800, debt = 0)
  private fun pay(id: String, at: Long) = SupplierPayFacts(id, at, "AFN", supplierId = "sup_1", amount = 1500)

  private fun started(disk: MemoryDocDisk, docFrom: Long = t0): Pair<DocChainBook, DocChainState> {
    val book = DocChainBook(disk)
    return book to book.start("shp_t", "B1", 1, 7, docFrom = docFrom)!!
  }

  private fun refs(disk: MemoryDocDisk) = disk.lines(1).map { DocLink.parse(it)!!.let { l -> "${l.type}:${l.ref}" } }

  @Test
  fun stateWithoutPlainDocsIsByteIdenticalToTheOldFormat() {
    val disk = MemoryDocDisk()
    DocChainBook(disk).start("shp_t", "B1", 1, 7)!!
    val text = disk.state!!
    assertFalse("docFrom" in text, text)
    assertEquals(-1, DocChainState.decode(text)!!.docFrom)
    //  وضعیتِ کهنهٔ بی این کلیدها ⇐ همان پیش‌فرض‌ها
    val old = DocChainState.decode(text)!!
    assertEquals(0, old.docAt)
    assertTrue(old.docIds.isEmpty())
  }

  @Test
  fun chainsPlainDocsFromStartInTimeOrderOnlyOnce() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val docs = listOf(
      debt("d2", t0 + 30), ret("r1", t0 + 10), buy("b1", t0 + 20), pay("p1", t0 + 20),
      debt("old", t0 - 1), // پیش از آغاز — در زنجیره نمی‌آید
      debt("bad,id", t0 + 40), // شناسه‌ای که در کلید جا نمی‌شود — کنار
    )
    val st = book.catchUpDocs(s0, "usr_1", docs, now = t0 + 100)
    assertEquals(listOf("return:r1", "purchase:b1", "supplier_pay:p1", "debt:d2"), refs(disk))
    assertEquals(4, st.seq)
    assertEquals(6, st.lastNo, "سندِ بی‌شماره شمارهٔ فاکتور را جلو نمی‌برد")
    assertEquals(t0 + 30, st.docAt)
    assertEquals(setOf("return:r1", "purchase:b1", "supplier_pay:p1", "debt:d2"), st.docIds)
    //  همان دفتر دوباره ⇐ هیچ حلقهٔ تازه، هیچ نوشتن
    val writes = disk.stateWrites
    assertEquals(st, book.catchUpDocs(st, "usr_1", docs, now = t0 + 200))
    assertEquals(writes, disk.stateWrites)
    assertEquals(4, disk.lines(1).size)
    //  وضعیت از دیسک همان است (نشان در فایلِ وضعیت می‌ماند)
    assertEquals(st, book.state())
    assertTrue(book.verify(1).ok)
  }

  @Test
  fun lateArrivalInsideTheWindowIsChainedOutsideIsNot() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val first = listOf(debt("d1", t0 + 5 * day))
    var st = book.catchUpDocs(s0, "u", first, now = t0 + 5 * day)
    assertEquals(t0 + 5 * day, st.docAt)
    //  از دستگاهِ دیگر، دو روز «عقب‌تر» ولی تازه رسید ⇐ زنجیره می‌شود؛ چهار روز عقب‌تر ⇐ نه
    val more = first + debt("late2", t0 + 3 * day) + debt("late4", t0 + day - 1)
    st = book.catchUpDocs(st, "u", more, now = t0 + 5 * day + 1)
    assertEquals(listOf("debt:d1", "debt:late2"), refs(disk))
    assertEquals(t0 + 5 * day, st.docAt, "سندِ دیررسیده «تا کجا» را عقب نمی‌برد")
    //  پنجره جلو رفت ⇐ کلیدهای بیرون از آن کنار (فایلِ وضعیت بزرگ نمی‌شود)
    st = book.catchUpDocs(st, "u", more + debt("d9", t0 + 9 * day), now = t0 + 9 * day)
    assertEquals(setOf("debt:d9"), st.docIds)
    assertEquals(3, st.seq)
  }

  @Test
  fun futureDatedDocDoesNotPushTheMarkerPastThisDevicesClock() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val now = t0 + 1000
    //  دستگاهی که ساعتش ده روز جلوست
    var st = book.catchUpDocs(s0, "u", listOf(debt("future", now + 10 * day)), now = now)
    assertEquals(now, st.docAt)
    //  سندِ خودِ این دستگاه یک دقیقه بعد ⇐ هنوز زنجیره می‌شود؛ سندِ آینده دوباره نه
    st = book.catchUpDocs(st, "u", listOf(debt("future", now + 10 * day), ret("mine", now + 60_000)), now = now + 60_000)
    assertEquals(listOf("debt:future", "return:mine"), refs(disk))
  }

  @Test
  fun chainFromAnOlderVersionStartsTheMarkerAtTheFirstCycle() {
    val disk = MemoryDocDisk()
    val book = DocChainBook(disk)
    val legacy = book.start("shp_t", "B1", 1, 7)!!
    val history = listOf(debt("d_old", t0 - day), ret("r_old", t0 - 5))
    val st = book.catchUpDocs(legacy, "u", history, now = t0)
    assertEquals(t0, st.docFrom)
    assertTrue(disk.lines(1).isEmpty(), "تاریخچهٔ پیش از این نسخه دوباره ساخته نمی‌شود")
    val st2 = book.catchUpDocs(st, "u", history + debt("d_new", t0 + 1), now = t0 + 2)
    assertEquals(listOf("debt:d_new"), refs(disk))
    assertEquals(t0, st2.docFrom)
  }

  @Test
  fun haltedChainChainsNothing() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val halted = book.halt(s0, "fork")
    assertEquals(halted, book.catchUpDocs(halted, "u", listOf(debt("d1", t0 + 1)), now = t0 + 2))
    assertTrue(disk.lines(1).isEmpty())
  }

  @Test
  fun bodyIsTheV1DocWithOnlyImmutableFieldsAndNoPersonalData() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    book.catchUpDocs(s0, "usr_1", listOf(ret("r1", t0 + 1), debt("d1", t0 + 2)), now = t0 + 3)
    val (r, d) = disk.lines(1).map { DocLink.parse(it)!! }
    assertEquals(
      """{"amount":2500,"by":"usr_1","chain":"doc/shp_t/B1/1","currency":"AFN","docId":"rd1","id":"r1","kind":"doc",""" +
        """"postedAt":1761800000001,"productId":"p1","qty":1000,"qtyScale":1000,"refund":"cash","saleId":"s7","saleItemId":"i1",""" +
        """"seq":1,"status":"posted","type":"return","v":1}""",
      r.body,
    )
    assertEquals("return", r.type)
    assertEquals(0, r.no)
    assertEquals(HashChain.sha256Hex(HashChain.GENESIS + r.body), r.hash)
    assertEquals(r.hash, d.prevHash)
    assertTrue(""""direction":"give"""" in d.body && """"customerId":"deb_1"""" in d.body)
  }

  @Test
  fun cycleChainsSalesThenPlainDocsAndReportsBoth() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer()
    val book = DocChainBook(disk)
    val history = listOf(sale("s1", 1))
    val off = DocChainCycle.Gate(false, 0)
    val (g, o) = runSync { DocChainCycle.tick(off, t0, book, server, "shp_t", "B1", "u", history, listOf(debt("d_old", t0 - 1))) }
    assertEquals(t0, assertIs<DocChainCycle.Outcome.Reported>(o).state.docFrom)
    val (_, o2) = runSync {
      DocChainCycle.tick(g, t0 + 10, book, server, "shp_t", "B1", "u", history + sale("s2", 2), listOf(debt("d_old", t0 - 1), debt("d1", t0 + 5)))
    }
    val st = assertIs<DocChainCycle.Outcome.Reported>(o2).state
    assertEquals(listOf("sale", "debt"), server.reports.last().links.map { it.type })
    assertEquals(2, st.ackSeq)
  }

  @Test
  fun flagOffCycleWritesNothingEvenWithPlainDocs() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer().apply { status = DocChainStatus(enabled = false) }
    runSync {
      DocChainCycle.tick(DocChainCycle.Gate(false, 0), t0, DocChainBook(disk), server, "shp_t", "B1", "u", emptyList(), listOf(debt("d1", t0)))
    }
    assertTrue(disk.files.isEmpty())
    assertEquals(null, disk.state)
    assertTrue(server.reports.isEmpty())
  }

  /* ---------------- «بررسیِ یکپارچگی» ---------------- */

  private class Fixture {
    val disk = MemoryDocDisk()
    val book = DocChainBook(disk)
    val sales = mutableListOf(sale("s7", 7), sale("s8", 8, customer = "deb_1"))
    val docs = mutableListOf<PlainFacts>()
    var state: DocChainState = book.start("shp_t", "B1", 1, 7, docFrom = 1_761_800_000_000L)!!
    fun audit(cancelled: Set<String> = emptySet()) = DocChainAudit.check(state, book.links(state.epoch), sales, docs, cancelled)
  }

  private fun fixture(): Fixture = Fixture().apply {
    docs += listOf(ret("r1", t0 + 1), debt("d1", t0 + 2), buy("b1", t0 + 3))
    state = book.catchUpDocs(book.catchUp(state, "usr_1", sales), "usr_1", docs, now = t0 + 10)
    state = book.acked(state, 3, DocLink.parse(disk.lines(1)[2])!!.hash)
  }

  @Test
  fun intactChainAndUnchangedBookAreOk() {
    val r = fixture().audit()
    assertTrue(r.ok, r.toString())
    assertEquals(5, r.total)
    assertEquals(mapOf("sale" to 2, "return" to 1, "debt" to 1, "purchase" to 1), r.byType)
    assertEquals(3, r.acked)
    assertEquals(0, r.waiting)
    assertEquals(0, r.unanchored)
  }

  @Test
  fun editedOrDeletedLedgerRowAfterAnchoringIsFound() {
    val f = fixture()
    //  مبلغِ قرض پس از لنگر عوض شد، خرید پاک شد، فاکتور مبلغِ دیگری گرفت
    f.docs[1] = debt("d1", t0 + 2, amount = 1)
    f.docs.removeAt(2)
    f.sales[0] = sale("s7", 7, total = 999)
    val r = f.audit()
    assertFalse(r.ok)
    assertTrue(r.links.ok, "خودِ زنجیره سالم است؛ دفتر عوض شد")
    assertEquals(listOf("sale:s7", "debt:d1"), r.changed.map { "${it.type}:${it.ref}" })
    assertEquals(listOf("purchase:b1"), r.missing.map { "${it.type}:${it.ref}" })
  }

  @Test
  fun tamperedChainFileIsFound() {
    val f = fixture()
    val sb = f.disk.files.getValue(1)
    val text = sb.toString().replace("\"amount\":9000", "\"amount\":9001")
    sb.setLength(0); sb.append(text)
    val r = f.audit()
    assertFalse(r.links.ok)
    assertEquals(3, r.links.index, "حلقهٔ قرض (چهارم)")
  }

  @Test
  fun waitingAndUnanchoredAreCountedSeparately() {
    val f = fixture()
    f.sales += sale("s9", 9) // پس از آخرین حلقه ⇐ دورِ بعد
    f.docs += debt("d2", t0 + 20) // تازه ⇐ دورِ بعد
    f.docs += debt("d_skipped", t0 + 5) // پس از آغاز ولی هرگز زنجیره نشد (پیش از پنجره)
    f.state = f.state.copy(docAt = t0 + 5 + DocChainDocs.SETTLE_MS + 1)
    val r = f.audit()
    assertEquals(2, r.waiting)
    assertEquals(1, r.unanchored)
    assertTrue(r.ok, "در انتظار و بی لنگر خرابی نیست؛ جدا گفته می‌شود")
  }

  @Test
  fun voidLinkNeedsTheSaleStillCancelled() {
    val f = fixture()
    f.state = f.book.appendVoid(f.state, "usr_1", f.sales[0], t0 + 50, InvoiceVoid.Kind.VOID, "اشتباه")
    assertTrue(f.audit(cancelled = setOf("s7")).ok)
    val r = f.audit(cancelled = emptySet())
    assertEquals(listOf("void:s7"), r.changed.map { "${it.type}:${it.ref}" })
  }
}
