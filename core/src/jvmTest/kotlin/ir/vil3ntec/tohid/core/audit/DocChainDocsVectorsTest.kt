package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  SEC-02 — `core/test-vectors/doc-chain-docs-v1.json` (ساختهٔ `server/scripts/doc-chain-docs-vectors.js`، مستقل از این کد):
 *  برگشت، قرض/رسید، خرید و پرداخت به تأمین‌کننده پسِ فاکتورهای همان سری. Kotlin همان خط‌ها و همان نشانِ «تا کجا» را
 *  بایت‌به‌بایت می‌سازد که سرور (`lib/doc-chain.js`، `doc-chain-docs.test.js`) می‌پذیرد.
 */
class DocChainDocsVectorsTest {

  @Suppress("UNCHECKED_CAST")
  private val v: Map<String, Any?> by lazy { TestJson.read("core/test-vectors/doc-chain-docs-v1.json") as Map<String, Any?> }

  @Suppress("UNCHECKED_CAST")
  private fun sale(o: Any?): SaleFacts {
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

  @Suppress("UNCHECKED_CAST")
  private fun doc(o: Any?): PlainFacts {
    val m = o as Map<String, Any?>
    fun s(k: String) = m[k] as String
    fun l(k: String) = m[k] as Long
    fun so(k: String) = m[k] as String?
    fun lo(k: String) = m[k] as Long?
    val id = s("id"); val at = l("postedAt"); val cur = s("currency")
    return when (s("type")) {
      "return" -> ReturnFacts(
        id, at, cur, saleId = s("saleId"), saleItemId = s("saleItemId"), productId = s("productId"), qty = l("qty"), amount = l("amount"),
        docId = so("docId"), disposition = so("disposition"), refund = so("refund"), toAccount = lo("toAccount"), toCredit = lo("toCredit"),
        creditTo = so("creditTo"), unitPrice = lo("unitPrice"),
      )
      "debt" -> DebtFacts(id, at, cur, customerId = s("customerId"), direction = s("direction"), amount = l("amount"), refId = so("refId"), kind = so("kind"))
      "purchase" -> PurchaseFacts(
        id, at, cur, supplierId = s("supplierId"), productId = s("productId"), qty = l("qty"), unitPrice = l("unitPrice"),
        total = l("total"), paid = l("paid"), debt = l("debt"),
      )
      else -> SupplierPayFacts(id, at, cur, supplierId = s("supplierId"), amount = l("amount"))
    }
  }

  @Test
  fun kotlinBuildsTheSameLinesAndMarkerTheServerAccepts() {
    assertEquals(HashChain.FORMAT, v["format"])
    val disk = MemoryDocDisk()
    val book = DocChainBook(disk)
    val s0 = book.start(v["shopId"] as String, v["series"] as String, v["epoch"] as Long, v["startNo"] as Long, docFrom = v["docFrom"] as Long)!!
    assertEquals(v["chain"], s0.chain)
    val by = v["by"] as String
    val sales = book.catchUp(s0, by, (v["sales"] as List<*>).map(::sale))
    val st = book.catchUpDocs(sales, by, (v["docs"] as List<*>).map(::doc), now = v["now"] as Long)

    @Suppress("UNCHECKED_CAST")
    val want = v["links"] as List<Map<String, Any?>>
    assertEquals(want.map { it["line"] }, disk.lines(st.epoch))
    for ((i, w) in want.withIndex()) {
      val l = DocLink.parse(disk.lines(st.epoch)[i])!!
      assertEquals(w["hash"], l.hash)
      assertEquals(w["type"], l.type)
      assertEquals(w["ref"], l.ref)
      assertEquals(w["no"], l.no)
    }
    assertEquals(v["head"], st.head)
    assertEquals(v["bytes"], st.bytes)
    assertEquals(v["docAt"], st.docAt)
    assertEquals((v["docIds"] as List<*>).toSet(), st.docIds)
    assertTrue(book.verify(st.epoch).ok)
  }
}
