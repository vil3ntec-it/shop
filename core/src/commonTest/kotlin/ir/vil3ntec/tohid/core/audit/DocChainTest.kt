package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  SEC-02 — زنجیرهٔ اسنادِ سریِ دستگاه روی دیسکِ حافظه: از همین لحظه، فقط سریِ خود و به ترتیب؛ همان
 *  قالبِ v1 ِ [HashChain]؛ قطعِ برق میانِ نوشتن؛ دست‌کاری و کوتاه شدنِ فایل؛ فقط حلقه‌های تأییدنشده
 *  به سرور؛ تأییدِ جلو/عقب‌رونده.
 */
class DocChainTest {

  private fun started(disk: MemoryDocDisk, startNo: Long = 7): Pair<DocChainBook, DocChainState> {
    val book = DocChainBook(disk)
    return book to book.start("shp_t", "B1", 1, startNo)!!
  }

  @Test
  fun chainsOnlyOwnSeriesFromStartNoInNumberOrder() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val st = book.catchUp(s0, "usr_1", listOf(sale("s9", 9), sale("old", 6), sale("a1", 8, series = "A1"), sale("s7", 7), sale("s8", 8)))
    assertEquals(listOf("s7", "s8", "s9"), disk.lines(1).map { DocLink.parse(it)!!.ref })
    assertEquals(3, st.seq)
    assertEquals(9, st.lastNo)
    //  همان فاکتورها دوباره ⇐ هیچ حلقهٔ تازه
    val again = book.catchUp(st, "usr_1", listOf(sale("s7", 7), sale("s8", 8), sale("s9", 9)))
    assertEquals(st, again)
    assertEquals(3, disk.lines(1).size)
  }

  @Test
  fun linkIsTheV1HashOfTheSaleDocument() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val st = book.catchUp(s0, "usr_1", listOf(sale("s7", 7, customer = "deb_1"), sale("s8", 8)))
    val links = disk.lines(1).map { DocLink.parse(it)!! }
    var prev = HashChain.GENESIS
    links.forEachIndexed { i, l ->
      val doc = DocChain.saleDoc("doc/shp_t/B1/1", i + 1L, "usr_1", if (i == 0) sale("s7", 7, customer = "deb_1") else sale("s8", 8))
      assertEquals(HashChain.canonicalJson(doc), l.body)
      assertEquals(HashChain.entryHash(prev, doc), l.hash)
      assertEquals(prev, l.prevHash)
      prev = l.hash
    }
    assertEquals(prev, st.head)
    assertTrue(book.verify(1).ok)
    assertEquals(st.bytes, disk.size(1))
  }

  @Test
  fun saleDocumentCarriesNoPersonalData() {
    val doc = DocChain.saleDoc("doc/shp_t/B1/1", 1, "usr_1", sale("s1", 1, customer = "deb_1"))
    assertEquals(
      setOf("chain", "seq", "kind", "type", "v", "id", "series", "no", "status", "postedAt", "by", "customerId",
        "currency", "qtyScale", "lines", "subtotal", "discount", "total", "paid", "method"),
      doc.keys,
    )
    @Suppress("UNCHECKED_CAST")
    assertEquals(setOf("productId", "qty", "unitPrice", "amount"), (doc["lines"] as List<Map<String, Any?>>)[0].keys)
    //  بی کاربر و بی مشتری ⇐ کلید نیست (نه null)
    assertFalse("by" in DocChain.saleDoc("c", 1, "", sale("s1", 1)))
    assertFalse("customerId" in DocChain.saleDoc("c", 1, "", sale("s1", 1)))
  }

  @Test
  fun crashBetweenAppendAndStateIsAdoptedOnOpen() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val before = disk.state
    book.catchUp(s0, "usr_1", listOf(sale("s7", 7), sale("s8", 8)))
    disk.state = before // وضعیتِ تازه هرگز نرسید
    val st = DocChainBook(disk).state()!!
    assertEquals(2, st.seq)
    assertEquals(8, st.lastNo)
    assertEquals("", st.halted)
    assertEquals(disk.size(1), st.bytes)
  }

  @Test
  fun tornLastLineIsIgnoredThenReplaced() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val st = book.catchUp(s0, "usr_1", listOf(sale("s7", 7)))
    disk.files[1]!!.append("L1\t2\t") // قطعِ برق وسطِ خط
    val writes = disk.stateWrites
    val opened = DocChainBook(disk).state()!!
    assertEquals(st, opened)
    assertEquals(writes, disk.stateWrites, "دُمِ نیمه‌کاره وضعیت را نمی‌نویسد")
    val next = book.catchUp(opened, "usr_1", listOf(sale("s8", 8)))
    assertEquals(2, next.seq)
    assertEquals(2, disk.lines(1).size)
    assertTrue(book.verify(1).ok)
  }

  @Test
  fun truncatedOrTamperedFileHaltsTheChain() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    val st = book.catchUp(s0, "usr_1", listOf(sale("s7", 7), sale("s8", 8)))
    val full = disk.files[1]!!.toString()
    //  کوتاه شد
    disk.files[1] = StringBuilder(full.substring(0, full.indexOf('\n') + 1))
    assertEquals("truncated", DocChainBook(disk).state()!!.halted)

    //  حلقهٔ افزوده‌ای که پیوند نمی‌خورد (کسی خط نوشت)
    val disk2 = MemoryDocDisk()
    val (book2, t0) = started(disk2)
    val t1 = book2.catchUp(t0, "usr_1", listOf(sale("s7", 7)))
    val forged = DocLink(2, HashChain.GENESIS, HashChain.GENESIS, "sale", "x", 8, "{}")
    disk2.files[1]!!.append(forged.line()).append('\n')
    assertEquals("local_broken", DocChainBook(disk2).state()!!.halted)
    //  ایستاده ⇐ نه حلقهٔ تازه، نه حلقه در گزارش؛ دلیل در گزارش
    val halted = DocChainBook(disk2).state()!!
    assertEquals(halted, book2.catchUp(halted, "usr_1", listOf(sale("s9", 9))))
    val r = book2.report(halted)
    assertEquals("local_broken", r.broken)
    assertTrue(r.links.isEmpty())
    assertEquals(1, t1.seq)
    assertEquals(2, st.seq)
  }

  @Test
  fun reportSendsUnackedLinksWithinCaps() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk, startNo = 1)
    val st = book.catchUp(s0, "usr_1", (1L..5L).map { sale("s$it", it) })
    val all = book.report(st)
    assertEquals(listOf(1L, 2L, 3L, 4L, 5L), all.links.map { it.seq })
    assertEquals(st.head, all.head)
    assertNull(all.broken)
    assertEquals(listOf(1L, 2L), book.report(st, maxLinks = 2).links.map { it.seq })
    //  بدنهٔ بزرگ‌تر از سقف ⇐ دست‌کم یک حلقه
    assertEquals(listOf(1L), book.report(st, maxBytes = 1).links.map { it.seq })
  }

  @Test
  fun ackMovesForwardAndBackAndCatchesDivergence() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk, startNo = 1)
    var st = book.catchUp(s0, "usr_1", (1L..5L).map { sale("s$it", it) })
    val links = disk.lines(1).map { DocLink.parse(it)!! }

    st = book.acked(st, 3, links[2].hash)
    assertEquals(3, st.ackSeq)
    assertEquals(links.take(3).sumOf { DocChain.lineBytes(it.line()) }, st.ackBytes)
    assertEquals(listOf(4L, 5L), book.report(st).links.map { it.seq })
    //  سرور از پشتیبان برگشت و فقط تا ۱ دارد ⇐ از ۲ دوباره
    st = book.acked(st, 1, links[0].hash)
    assertEquals(listOf(2L, 3L, 4L, 5L), book.report(st).links.map { it.seq })
    st = book.acked(st, 5, links[4].hash)
    assertTrue(book.report(st).links.isEmpty())
    //  سرور هیچ ندارد
    st = book.acked(st, 0, HashChain.GENESIS)
    assertEquals(5, book.report(st).links.size)

    //  سرور حلقه‌ای دارد که این دستگاه ندارد ⇐ دفترِ محلی عقب رفته
    assertEquals("local_broken", book.acked(st, 6, links[4].hash).halted)
    val disk2 = MemoryDocDisk()
    val (book2, u0) = started(disk2, startNo = 1)
    val u = book2.catchUp(u0, "usr_1", (1L..2L).map { sale("s$it", it) })
    assertEquals("local_broken", book2.acked(u, 2, HashChain.GENESIS).halted)
  }

  @Test
  fun stateRoundTripsAndRejectsGarbage() {
    val st = DocChainState(chain = "doc/shp_t/B1/3", series = "B1", epoch = 3, startNo = 9, seq = 4,
      head = "a".repeat(64), bytes = 1200, lastNo = 12, ackSeq = 2, ackHash = "b".repeat(64), ackBytes = 600, halted = "fork")
    assertEquals(st, DocChainState.decode(st.encode()))
    assertNull(DocChainState.decode(""))
    assertNull(DocChainState.decode("v=2\nchain=doc/shp_t/B1/3"))
    assertNull(DocChainState.decode(st.encode().replace("series=B1", "series=A1")))
    assertNull(DocChainState.decode(st.encode().replace("head=" + "a".repeat(64), "head=xyz")))
  }

  @Test
  fun lineFormatIsStrict() {
    val l = DocLink(3, "a".repeat(64), "b".repeat(64), "sale", "sal_1", 12, "{\"a\":1}")
    assertEquals(l, DocLink.parse(l.line()))
    assertNull(DocLink.parse(l.line().replace("L1", "L2")))
    assertNull(DocLink.parse(l.line().replace("\t3\t", "\t0\t")))
    assertNull(DocLink.parse(l.line().replace("sale", "Sale")))
    assertNull(DocLink.parse("L1\t1\tx"))
    //  canonicalJson هیچ TAB یا پایانِ خطِ خامی ندارد
    val body = HashChain.canonicalJson(mapOf("t" to "a\tb\nc\r"))
    assertFalse(body.any { it == '\t' || it == '\n' || it == '\r' })
  }

  @Test
  fun newEpochNeverWritesOverAnUnreportedEpochFile() {
    val disk = MemoryDocDisk()
    val (book, s0) = started(disk)
    book.catchUp(s0, "usr_1", listOf(sale("s7", 7)))
    disk.state = null // وضعیت پاک شد؛ دورهٔ ۱ هرگز به سرور نرسیده بود
    val fresh = DocChainBook(disk).start("shp_t", "B1", 1, 8)!!
    assertEquals(2, fresh.epoch)
    assertEquals("doc/shp_t/B1/2", fresh.chain)
    assertNull(DocChainBook(MemoryDocDisk()).start("bad shop", "B1", 1, 1))
    assertNull(DocChainBook(MemoryDocDisk()).start("shp_t", "W", 1, 1))
  }

  @Test
  fun chainIdIsValidated() {
    assertEquals("doc/shp_t/B1/1", DocChain.chainId("shp_t", "B1", 1))
    assertNull(DocChain.chainId("shp/t", "B1", 1))
    assertNull(DocChain.chainId("shp_t", "b1", 1))
    assertNull(DocChain.chainId("shp_t", "B1", 0))
    assertEquals(Triple("shp_t", "B12", 4L), DocChain.chainId("doc/shp_t/B12/4"))
    assertNull(DocChain.chainId("doc/shp_t/B12/0"))
    assertNotNull(DocChain.chainId("doc/a.b:c-d_e/Z999/999999999"))
  }
}
