package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  SEC-03 — لغو و اصلاحِ فاکتورِ ثبت‌شده: یک قاعدهٔ کنترل برای هر صفحه، و سندِ معکوس (نه ویرایش) در
 *  زنجیرهٔ اسنادِ دستگاه، پس از حلقهٔ «ثبت» ِ همان فاکتور.
 */
class InvoiceVoidTest {

  private val live = InvoiceVoid.Facts(status = "completed", online = false)
  private fun denied(d: InvoiceVoid.Decision) = (d as? InvoiceVoid.Decision.Denied)?.code

  @Test
  fun voidNeedsInvoicePermissionAndReason() {
    assertEquals("not_found", denied(InvoiceVoid.check(null, true, InvoiceVoid.Kind.VOID, "x")))
    assertEquals("already_cancelled", denied(InvoiceVoid.check(live.copy(status = "cancelled"), true, InvoiceVoid.Kind.VOID, "x")))
    assertEquals("online_order", denied(InvoiceVoid.check(live.copy(online = true), true, InvoiceVoid.Kind.VOID, "x")))
    assertEquals("no_permission", denied(InvoiceVoid.check(live, false, InvoiceVoid.Kind.VOID, "x")))
    assertEquals("reason_required", denied(InvoiceVoid.check(live, true, InvoiceVoid.Kind.VOID, "   ")))
    assertEquals("reason_too_long", denied(InvoiceVoid.check(live, true, InvoiceVoid.Kind.VOID, "ب".repeat(121))))
    assertEquals(InvoiceVoid.Decision.Allowed("مشتری پشیمان شد"), InvoiceVoid.check(live, true, InvoiceVoid.Kind.VOID, "  مشتری پشیمان شد "))
    assertEquals(InvoiceVoid.Decision.Allowed("ب".repeat(120)), InvoiceVoid.check(live, true, InvoiceVoid.Kind.VOID, "ب".repeat(120)))
  }

  @Test
  fun correctionHasTheFixedReasonAndTheSameGuards() {
    assertEquals(InvoiceVoid.Decision.Allowed(InvoiceVoid.CORRECTION_REASON), InvoiceVoid.check(live, true, InvoiceVoid.Kind.CORRECTION))
    assertEquals(InvoiceVoid.Decision.Allowed(InvoiceVoid.CORRECTION_REASON), InvoiceVoid.check(live, true, InvoiceVoid.Kind.CORRECTION, "هر چه"))
    assertEquals("online_order", denied(InvoiceVoid.check(live.copy(online = true), true, InvoiceVoid.Kind.CORRECTION)))
    assertEquals("no_permission", denied(InvoiceVoid.check(live, false, InvoiceVoid.Kind.CORRECTION)))
    assertEquals("already_cancelled", denied(InvoiceVoid.check(live.copy(status = "cancelled"), true, InvoiceVoid.Kind.CORRECTION)))
  }

  @Test
  fun reversalNegatesAmountsKeepsUnitPriceAndPointsToTheOriginal() {
    val f = sale("s7", 7, total = 32000, customer = "deb_1")
    val doc = InvoiceVoid.reversalDoc("doc/shp_t/C1/2", 5, "usr_9", f, 1_761_900_000_000, InvoiceVoid.Kind.VOID, "اشتباه در مقدار")
    assertEquals("void", doc["type"])
    assertEquals("void", doc["voidKind"])
    assertEquals("s7~r", doc["id"])
    assertEquals("s7", doc["reverses"])
    assertEquals("posted", doc["status"])
    assertEquals("اشتباه در مقدار", doc["reason"])
    assertEquals("usr_9", doc["by"])
    assertEquals(1_761_900_000_000L, doc["postedAt"])
    assertEquals("doc/shp_t/C1/2", doc["chain"])
    assertEquals(5L, doc["seq"])
    assertEquals(7L, doc["no"])
    assertEquals("B1", doc["series"], "سریِ فاکتورِ اصلی می‌ماند")
    assertEquals("deb_1", doc["customerId"])
    assertEquals(-32000L, doc["total"])
    assertEquals(-32000L, doc["paid"])
    @Suppress("UNCHECKED_CAST")
    val line = (doc["lines"] as List<Map<String, Any?>>)[0]
    assertEquals(-1500L, line["qty"])
    assertEquals(32000L, line["unitPrice"])
    assertEquals(-32000L, line["amount"])
    //  همان قاعدهٔ PostedDocument (بردارهای مشترک با JS): معکوسِ سندِ معکوس رد است
    val again = runCatching { PostedDocument.reverse(doc, ReversalRequest("x", 1, 1, "y", "z")) }.exceptionOrNull() as HashChainException
    assertEquals("is_reversal", again.code)
    assertEquals("correction", InvoiceVoid.reversalDoc("c", 1, "u", f, 1, InvoiceVoid.Kind.CORRECTION, InvoiceVoid.CORRECTION_REASON)["voidKind"])
  }

  @Test
  fun voidIsChainedAfterThePostedSaleAndOriginalLinkIsUntouched() {
    val disk = MemoryDocDisk()
    val book = DocChainBook(disk)
    val st = book.start("shp_t", "B1", 1, 7)!!
    val on = DocChainCycle.Gate(true, 1)
    val s7 = sale("s7", 7)
    val s8 = sale("s8", 8)
    //  فاکتورِ ۷ هنوز حلقه نشده بود (دورِ همگام‌سازی نرسیده) — اول «ثبت»، بعد «معکوس»
    val after = DocChainCycle.recordVoid(on, book, "shp_t", "B1", "usr_1", listOf(s7, s8), s7, 99, InvoiceVoid.Kind.VOID, "اشتباه")!!
    val links = disk.lines(1).map { DocLink.parse(it)!! }
    assertEquals(listOf("sale" to "s7", "sale" to "s8", "void" to "s7"), links.map { it.type to it.ref })
    assertEquals(3, after.seq)
    assertEquals(8, after.lastNo, "حلقهٔ معکوس شمارهٔ آخرین فاکتور را جلو نمی‌برد")
    assertTrue(book.verify(1).ok)
    val expected = InvoiceVoid.reversalDoc(st.chain, 3, "usr_1", s7, 99, InvoiceVoid.Kind.VOID, "اشتباه")
    assertEquals(HashChain.canonicalJson(expected), links[2].body)
    assertEquals(HashChain.entryHash(links[1].hash, expected), links[2].hash)
    //  گزارشِ بعدی سه حلقه را می‌برد؛ حلقهٔ «ثبت» ِ ۷ دست نخورده
    assertEquals(listOf(1L, 2L, 3L), book.report(after).links.map { it.seq })
    assertEquals(HashChain.canonicalJson(DocChain.saleDoc(st.chain, 1, "usr_1", s7)), links[0].body)
    //  دورِ همگام‌سازی پس از لغو، فاکتورِ ۷ را دوباره «ثبت» نمی‌کند
    val again = book.catchUp(after, "usr_1", listOf(s7, s8))
    assertEquals(after, again)
  }

  @Test
  fun voidOfAnotherDevicesInvoiceGoesIntoThisChain() {
    val disk = MemoryDocDisk()
    val book = DocChainBook(disk)
    book.start("shp_t", "C1", 1, 1)!!
    val foreign = sale("a5", 5, series = "A1")
    val st = DocChainCycle.recordVoid(DocChainCycle.Gate(true, 1), book, "shp_t", "C1", "usr_2", emptyList(), foreign, 50, InvoiceVoid.Kind.CORRECTION, InvoiceVoid.CORRECTION_REASON)!!
    val l = DocLink.parse(disk.lines(1).single())!!
    assertEquals("void", l.type)
    assertEquals("a5", l.ref)
    assertEquals(1, st.seq)
    assertEquals(0, st.lastNo)
  }

  @Test
  fun nothingIsChainedWhenOffNotStartedHaltedOrUnnumbered() {
    val s7 = sale("s7", 7)
    //  پرچمِ خاموش
    val disk = MemoryDocDisk()
    val book = DocChainBook(disk)
    book.start("shp_t", "B1", 1, 1)
    assertNull(DocChainCycle.recordVoid(DocChainCycle.Gate(false, 1), book, "shp_t", "B1", "u", listOf(s7), s7, 1, InvoiceVoid.Kind.VOID, "x"))
    assertTrue(disk.lines(1).isEmpty())
    //  زنجیره هنوز ساخته نشده
    val empty = MemoryDocDisk()
    assertNull(DocChainCycle.recordVoid(DocChainCycle.Gate(true, 1), DocChainBook(empty), "shp_t", "B1", "u", listOf(s7), s7, 1, InvoiceVoid.Kind.VOID, "x"))
    assertNull(empty.state)
    //  ایستاده
    val st = book.halt(book.state()!!, "fork")
    assertNull(DocChainCycle.recordVoid(DocChainCycle.Gate(true, 1), book, "shp_t", "B1", "u", listOf(s7), s7, 1, InvoiceVoid.Kind.VOID, "x"))
    assertEquals(st, book.appendVoid(st, "u", s7, 1, InvoiceVoid.Kind.VOID, "x"))
    //  فاکتورِ بی شماره (پیش از سری) ⇐ سندِ معکوس ساخته نمی‌شود، زنجیره نمی‌ایستد
    val disk2 = MemoryDocDisk()
    val book2 = DocChainBook(disk2)
    val s0 = book2.start("shp_t", "B1", 1, 1)!!
    assertEquals(s0, book2.appendVoid(s0, "u", sale("old", 0), 1, InvoiceVoid.Kind.VOID, "x"))
    assertTrue(disk2.lines(1).isEmpty())
  }
}
