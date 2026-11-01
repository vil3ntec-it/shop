package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  SEC-02 — `core/test-vectors/doc-chain-v1.json` (ساختهٔ `server/scripts/doc-chain-vectors.js`، مستقل از
 *  این کد): Kotlin همان خط‌ها را بایت‌به‌بایت می‌سازد که سرور (`lib/doc-chain.js`، `doc-chain.test.js`)
 *  می‌پذیرد. یک بایت فرق یعنی هر فاکتورِ گوشی روی سرور «hash_mismatch» می‌شد.
 */
class DocChainVectorsTest {

  @Suppress("UNCHECKED_CAST")
  private val v: Map<String, Any?> by lazy { TestJson.read("core/test-vectors/doc-chain-v1.json") as Map<String, Any?> }

  @Suppress("UNCHECKED_CAST")
  private fun facts(o: Any?): SaleFacts {
    val m = o as Map<String, Any?>
    return SaleFacts(
      id = m["id"] as String, series = m["series"] as String, no = m["no"] as Long, postedAt = m["postedAt"] as Long,
      customerId = m["customerId"] as String?, currency = m["currency"] as String,
      lines = (m["lines"] as List<Map<String, Any?>>).map {
        SaleFacts.Line(it["productId"] as String, it["qty"] as Long, it["unitPrice"] as Long, it["amount"] as Long)
      },
      subtotal = m["subtotal"] as Long, discount = m["discount"] as Long, total = m["total"] as Long,
      paid = m["paid"] as Long, method = m["method"] as String,
    )
  }

  @Test
  fun kotlinBuildsTheSameLinesTheServerAccepts() {
    assertEquals(HashChain.FORMAT, v["format"])
    val disk = MemoryDocDisk()
    val book = DocChainBook(disk)
    val s0 = book.start(v["shopId"] as String, v["series"] as String, v["epoch"] as Long, v["startNo"] as Long)!!
    assertEquals(v["chain"], s0.chain)
    val st = book.catchUp(s0, v["by"] as String, (v["sales"] as List<*>).map(::facts))

    @Suppress("UNCHECKED_CAST")
    val want = (v["links"] as List<Map<String, Any?>>)
    assertEquals(want.map { it["line"] }, disk.lines(st.epoch))
    for ((i, w) in want.withIndex()) {
      val l = DocLink.parse(disk.lines(st.epoch)[i])!!
      assertEquals(w["seq"], l.seq)
      assertEquals(w["prevHash"], l.prevHash)
      assertEquals(w["hash"], l.hash)
      assertEquals(w["body"], l.body)
      assertEquals(w["ref"], l.ref)
      assertEquals(w["no"], l.no)
    }
    assertEquals(v["head"], st.head)
    assertEquals(v["bytes"], st.bytes)
    assertEquals(v["bytes"], disk.size(st.epoch))
    assertTrue(book.verify(st.epoch).ok)
  }
}
