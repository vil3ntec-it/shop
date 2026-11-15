package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.audit.DocChainAudit
import ir.vil3ntec.tohid.core.audit.DocChainBook
import ir.vil3ntec.tohid.core.audit.DocChainDisk
import ir.vil3ntec.tohid.core.audit.DocLink
import ir.vil3ntec.tohid.ui.v3.analytics.DocChainCheckModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  SEC-02 — دفترِ واقعیِ برنامه (`ShopData`) ⇐ `PlainChainFacts` ⇐ زنجیره ⇐ «بررسیِ یکپارچگی» (`DocChainAudit`،
 *  `DocChainCheckModel`): برگشت، قرض/رسید، خرید و پرداخت به تأمین‌کننده لنگر می‌خورند، بی یادداشت و دلیل؛ دست‌کاریِ مبلغ
 *  در دفتر پس از لنگر و پاک شدنِ ردیف دیده می‌شود؛ ویرایشِ بخش‌های ویرایش‌پذیر (سررسید، شمارهٔ فاکتورِ خرید، شیفت) نه.
 */
class DocChainLedgerTest {

  private class Disk : DocChainDisk {
    var state: String? = null
    val files = HashMap<Long, StringBuilder>()
    override fun readState(): String? = state
    override fun writeState(text: String) { state = text }
    override fun size(epoch: Long): Long = (files[epoch]?.toString() ?: "").toByteArray().size.toLong()
    override fun readFrom(epoch: Long, from: Long): List<String> {
      val b = (files[epoch]?.toString() ?: "").toByteArray()
      if (from >= b.size) return emptyList()
      return String(b.copyOfRange(from.toInt(), b.size)).split('\n').dropLast(1)
    }
    override fun append(epoch: Long, lines: List<String>) {
      val sb = files.getOrPut(epoch) { StringBuilder() }
      lines.forEach { sb.append(it).append('\n') }
    }
  }

  private val t0 = 1_761_800_000_000L

  private fun ledger() = ShopData(
    sales = listOf(Sale(id = "s1", series = "B1", invoiceNumber = 7, createdAt = t0 + 1, total = 100.0, finalTotal = 100.0, paidAmount = 100.0, paymentMethod = "cash")),
    saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 2.0, unitPrice = 50.0, totalPrice = 100.0)),
    saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", saleItemId = "i1", productId = "p1", quantity = 0.5, amount = 25.0, reason = "خراب بود", createdAt = t0 + 2, docId = "rd1", refund = "cash")),
    transactions = listOf(DebtTransaction(id = "d1", debtorId = "deb_1", type = "give", amount = 750.5, notes = "یادداشتِ شخصی", createdAt = t0 + 3, dueDate = "2026-12-01")),
    purchases = listOf(Purchase(id = "b1", productId = "p1", supplierId = "sup_1", quantity = 12.0, purchasePrice = 4.25, totalAmount = 51.0, paidAmount = 51.0, notes = "x", createdAt = t0 + 4, invoiceNo = 0)),
    supplierPayments = listOf(SupplierPayment(id = "sp1", supplierId = "sup_1", amount = 15.0, notes = "y", createdAt = t0 + 5)),
  )

  private fun chained(d: ShopData): Triple<Disk, DocChainBook, ir.vil3ntec.tohid.core.audit.DocChainState> {
    val disk = Disk()
    val book = DocChainBook(disk)
    var st = book.start("shp_t", "B1", 1, 7, docFrom = t0)!!
    st = book.catchUpDocs(book.catchUp(st, "usr_1", SaleChainFacts.of(d, "B1")), "usr_1", PlainChainFacts.of(d), now = t0 + 100)
    return Triple(disk, book, st)
  }

  private fun audit(d: ShopData, book: DocChainBook, st: ir.vil3ntec.tohid.core.audit.DocChainState) = DocChainAudit.check(
    st, book.links(st.epoch), SaleChainFacts.ofAll(d), PlainChainFacts.of(d), d.sales.filter { it.status == "cancelled" }.mapTo(HashSet()) { it.id },
  )

  @Test fun `برگشت، قرض، خرید و پرداخت به تأمین‌کننده پسِ فاکتور لنگر می‌خورند، بی یادداشت و دلیل`() {
    val d = ledger()
    val (disk, book, st) = chained(d)
    val links = disk.readFrom(1, 0).map { DocLink.parse(it)!! }
    assertEquals(listOf("sale", "return", "debt", "purchase", "supplier_pay"), links.map { it.type })
    assertEquals(5L, st.seq)
    val debt = links[2].body
    assertTrue(debt, "\"amount\":75050" in debt && "\"customerId\":\"deb_1\"" in debt)
    for (l in links) for (k in listOf("notes", "reason", "dueDate", "یادداشت", "خراب")) assertFalse("${l.ref}: $k", k in l.body)
    assertTrue(audit(d, book, st).ok)
  }

  @Test fun `مبلغِ دست‌خورده و ردیفِ پاک‌شده دیده می‌شود؛ بخشِ ویرایش‌پذیر نه`() {
    val d = ledger()
    val (_, book, st) = chained(d)
    //  ویرایش‌های درست: سررسید، شمارهٔ فاکتورِ خرید، مُهرِ شیفت ⇐ هنوز سالم
    val edited = d.copy(
      transactions = d.transactions.map { it.copy(dueDate = "2027-01-01", shiftId = "sh1") },
      purchases = d.purchases.map { it.copy(invoiceNo = 4, landedCost = 1.0) },
    )
    assertTrue(audit(edited, book, st).ok)
    //  دست‌کاری: مبلغِ قرض و برگشت عوض، پرداخت به تأمین‌کننده پاک
    val forged = d.copy(
      transactions = d.transactions.map { it.copy(amount = 75.0) },
      saleReturns = d.saleReturns.map { it.copy(amount = 2.5) },
      supplierPayments = emptyList(),
    )
    val r = audit(forged, book, st)
    assertFalse(r.ok)
    assertEquals(listOf("return:r1", "debt:d1"), r.changed.map { "${it.type}:${it.ref}" })
    assertEquals(listOf("supplier_pay:sp1"), r.missing.map { "${it.type}:${it.ref}" })
    val v = DocChainCheckModel.view(r) { "t$it" }
    assertFalse(v.ok)
    assertEquals(2, v.changed.size)
    assertEquals(1, v.missing.size)
  }

  @Test fun `نمای صفحه — سالم با شمارِ هر نوع و «تا کجا»؛ فایلِ دست‌خورده با شمارهٔ حلقه`() {
    val d = ledger()
    val (disk, book, st) = chained(d)
    val ok = DocChainCheckModel.view(audit(d, book, st)) { "T$it" }
    assertTrue(ok.ok)
    assertEquals(5 + 3, ok.facts.size)
    assertTrue(ok.facts.joinToString(), ok.facts.any { "T${t0 + 5}" in it })
    assertTrue(ok.notes.isEmpty())
    val text = disk.files.getValue(1).toString().replace("\"amount\":1500", "\"amount\":1501")
    disk.files.getValue(1).setLength(0); disk.files.getValue(1).append(text)
    val bad = DocChainCheckModel.view(audit(d, book, st)) { "T$it" }
    assertFalse(bad.ok)
    assertTrue(bad.body, "5" in bad.body)
  }
}
