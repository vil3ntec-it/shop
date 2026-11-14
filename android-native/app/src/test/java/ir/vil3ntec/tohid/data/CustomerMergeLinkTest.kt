package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-10 روی همگام‌سازیِ v3 — «ادغامِ بی ویرایشِ سند» (`CustomerMerge.merge(docs = false)` + `CustomerAlias`):
 *  هیچ قرض و فاکتوری عوض نمی‌شود، فقط پروندهٔ مشتری‌ها و ردِ دفترچه؛ و هر خوانندهٔ مانده، صورت‌حساب، سنِ بدهی و
 *  گزارش همان را نشان می‌دهد که ادغامِ v1 (جابه‌جاییِ سند) نشان می‌داد.
 */
class CustomerMergeLinkTest {

  private var seq = 0
  private fun newId() = "m${seq++}"

  private fun give(id: String, who: String, amount: Double, date: String, notes: String = "", at: Long = 0) =
    DebtTransaction(id = id, debtorId = who, type = "give", amount = amount, date = date, notes = notes, createdAt = at)
  private fun receive(id: String, who: String, amount: Double, date: String, at: Long = 0) =
    DebtTransaction(id = id, debtorId = who, type = "receive", amount = amount, date = date, createdAt = at)

  private val d0 = ShopData(
    debtors = listOf(
      Debtor(id = "c1", name = "احمد", phone = "+93700123456", notes = "دکانِ کنار", createdAt = 10),
      Debtor(id = "c2", name = "احمد کابلی", phone = "+93799555444", phone2 = "0700123456", createdAt = 20, creditLimit = 5000.0),
      Debtor(id = "c5", name = "تنها", phone = "+93788000000", createdAt = 50),
    ),
    transactions = listOf(
      give("t1", "c1", 300.0, "2026-09-01", "فروش نسیه — فاکتور #7", at = 3),
      give("t2", "c2", 1000.0, "2026-06-01", "فروش نسیه — فاکتور #3", at = 1),
      receive("t3", "c2", 400.0, "2026-07-01", at = 2),
      receive("t4", "c1", 50.0, "2026-09-10", at = 4),
      give("t5", "c5", 50.0, "2026-10-01", at = 5),
    ),
    sales = listOf(
      Sale(id = "s3", invoiceNumber = 3, finalTotal = 1000.0, paymentMethod = "credit", debtorId = "c2", date = "2026-06-01", createdAt = 1),
      Sale(id = "s7", invoiceNumber = 7, finalTotal = 300.0, paymentMethod = "credit", debtorId = "c1", date = "2026-09-01", createdAt = 3),
      Sale(id = "s8", invoiceNumber = 8, finalTotal = 90.0, debtorId = "c1", date = "2026-09-05", createdAt = 4),
    ),
  )

  private fun ok(r: CustomerMerge.Result) = (r as CustomerMerge.Result.Ok)
  private fun link(d: ShopData) = ok(CustomerMerge.merge(d, "c2", "c1", CreditLimitPerm.Rules.OFF, "2026-10-19", 99, ::newId, docs = false))
  private fun moved(d: ShopData) = ok(CustomerMerge.merge(d, "c2", "c1", CreditLimitPerm.Rules.OFF, "2026-10-19", 99, ::newId, docs = true))

  private val json = Json { encodeDefaults = true }
  private fun docs(d: ShopData) =
    json.encodeToString(DebtTransaction.serializer().let { kotlinx.serialization.builtins.ListSerializer(it) }, d.transactions) +
      json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Sale.serializer()), d.sales)

  /** همهٔ خواننده‌های پروندهٔ [id] که مانده، صورت‌حساب، سنِ بدهی یا فاکتور می‌سازند — بی شناسهٔ «مشتریِ سند» */
  private fun view(d: ShopData, id: String): List<Any?> {
    val acc = DebtorEngine.account(d, id)!!
    return listOf(
      ShopStore.debt(d, id),
      acc.given, acc.received, acc.balance, acc.since, acc.transactions.map { it.id }, acc.sales.map { it.id },
      DebtorEngine.debtSince(d, id),
      CustomerEngine.ledger(d, id).map { Triple(it.tx.id, it.balance, it.saleId) },
      CustomerEngine.invoices(d, id).map { it.id },
      CustomerEngine.summary(d, d.debtors.first { it.id == id }, now = 1_800_000_000_000).let { it.copy(id = "") },
      DebtBook.book(d, id, "2026-10-19")!!.let { b -> Triple(b.entries.map { it.tx.id to it.sale?.id }, b.balance, b.overdueAmount) },
      DebtBook.creditCheck(d, id, ir.vil3ntec.tohid.core.money.Money.zero(DebtBook.CURRENCY))?.toString(),
      CustomerStatement.lines(d, id).map { it.copy() },
      CustomerInsight.agingOf(d, id, "2026-10-19"),
      GiftCards.balanceMinor(d, id),
      ReportCsv.statement(d, id, "2026-10-19").rows,
      ReportEngine.debtors(d).first { it.debtor.id == id }.let { Triple(it.given, it.received, it.remaining) },
      ReportCore.debts(ReportBridge.ledgerOf(d), "2026-10-19").customers.rows.filter { it.id == id }.toString(),
    )
  }

  @Test fun `بی ویرایشِ سند — قرض و فاکتورها بایت‌به‌بایت همان، فقط پروندهٔ مشتری و ردِ دفترچه`() {
    val r = link(d0)
    val d = r.data
    assertEquals("هیچ سندی عوض نشد", docs(d0), docs(d))
    assertEquals("c2", d.debtors.first { it.id == "c1" }.mergedInto)
    assertEquals("احمد", d.debtors.first { it.id == "c1" }.name)
    assertEquals(listOf("c2", "c5"), CustomerCrm.active(d).map { it.id })
    val a = d.auditLog.single()
    assertEquals("customer_merge", a.type)
    assertEquals("c1", a.refId)
    assertNull("ردیفی جابه‌جا نشد", a.refIds)
    assertEquals(
      "ادغامِ «احمد» (c1) در «احمد کابلی» (c2): 2 تراکنش و 2 فاکتور بی ویرایش زیرِ ماندگار خوانده می‌شود — ماندهٔ منتقل‌شده 250 افغانی",
      a.notes,
    )
    //  opهای همگام‌سازی (تفاضل با سایه، همان `engine.record`): فقط جدولِ پایهٔ مشتری و درجِ سابقه — هیچ سندی
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(d0), LedgerDiff.toJson(d), 7)
    assertEquals(setOf("debtors" to SyncOp.UPDATE, "auditLog" to SyncOp.INSERT), ops.map { it.table to it.type }.toSet())
    assertTrue(ops.none { it.table == "transactions" || it.table == "sales" })
  }

  @Test fun `هر خوانندهٔ پرونده همان را نشان می‌دهد که ادغامِ v1 با جابه‌جاییِ سند`() {
    val linked = link(d0).data
    val v1 = moved(d0).data
    assertEquals(view(v1, "c2"), view(linked, "c2"))
    assertEquals(view(v1, "c5"), view(linked, "c5"))
    assertEquals(1150.0 - 400.0 - 50.0, ShopStore.debt(linked, "c2"), 0.0)
    assertEquals("ادغام‌شده ماندهٔ جدا ندارد", 0.0, ShopStore.debt(linked, "c1"), 0.0)
    assertEquals(listOf("s3", "s7", "s8"), CustomerEngine.invoices(linked, "c2").map { it.id }.sorted())
    //  گزارشِ سنِ بدهیِ همه: یک ردیف برای ماندگار، هیچ ردیفی برای ادغام‌شده
    assertEquals(
      ReportCore.debts(ReportBridge.ledgerOf(v1), "2026-10-19").customers.toString(),
      ReportCore.debts(ReportBridge.ledgerOf(linked), "2026-10-19").customers.toString(),
    )
    assertEquals(CustomerInsight.aging(v1, "2026-10-19"), CustomerInsight.aging(linked, "2026-10-19"))
  }

  @Test fun `پولِ دکان همان است و برنامهٔ کهنه‌ای که پیوند را نمی‌شناسد هر سند را دارد`() {
    val d = link(d0).data
    //  خوانندهٔ کهنه: جمع به شناسهٔ خامِ سند
    fun raw(x: ShopData) = x.transactions.groupBy { it.debtorId }.mapValues { (_, l) -> l.sumOf { if (it.type == "give") it.amount else -it.amount } }
    assertEquals(raw(d0), raw(d))
    assertEquals(raw(d0).values.sum(), d.debtors.filter(CustomerCrm::isActive).sumOf { ShopStore.debt(d, it.id) }, 1e-9)
  }

  @Test fun `برگرداندنِ ادغام بی هیچ سندِ تازه — مانده‌ها همان پیش از ادغام`() {
    val d = link(d0).data
    val undone = d.copy(debtors = d.debtors.map { if (it.id == "c1") it.copy(mergedInto = null) else it })
    assertEquals(ShopStore.debt(d0, "c1"), ShopStore.debt(undone, "c1"), 0.0)
    assertEquals(ShopStore.debt(d0, "c2"), ShopStore.debt(undone, "c2"), 0.0)
    assertEquals(docs(d0), docs(undone))
  }

  @Test fun `زنجیره — ادغامِ ماندگاری که خودش پیوند دارد، همهٔ سندها زیرِ ماندگارِ آخر`() {
    val first = link(d0).data
    val again = ok(CustomerMerge.merge(first, "c5", "c2", CreditLimitPerm.Rules.OFF, "2026-10-20", 100, ::newId, docs = false))
    val d = again.data
    assertEquals(docs(d0), docs(d))
    assertEquals("زنجیره صاف", "c5", d.debtors.first { it.id == "c1" }.mergedInto)
    assertEquals(4, again.preview.transactions)
    assertEquals(3, again.preview.sales)
    assertEquals(d0.transactions.sumOf { if (it.type == "give") it.amount else -it.amount }, ShopStore.debt(d, "c5"), 1e-9)
    assertEquals(listOf("c5"), CustomerCrm.active(d).map { it.id })
  }

  @Test fun `حلقهٔ دو ادغامِ وارونه روی v3 — فقط پروندهٔ مشتری ترمیم می‌شود، هیچ سندی جابه‌جا نمی‌شود`() {
    val loop = d0.copy(debtors = d0.debtors.map {
      when (it.id) { "c1" -> it.copy(mergedInto = "c2"); "c2" -> it.copy(mergedInto = "c1"); else -> it }
    })
    val next = CustomerMerge.sweep(loop, "2026-10-20", 5, ::newId, docs = false)!!
    assertEquals(docs(d0), docs(next))
    assertNull("کوچک‌ترین شناسهٔ حلقه دوباره فعال", next.debtors.first { it.id == "c1" }.mergedInto)
    assertEquals("c1", next.debtors.first { it.id == "c2" }.mergedInto)
    val ops = CustomerMerge.sweepOps(loop, next)!!
    assertEquals(setOf("debtors" to SyncOp.UPDATE, "auditLog" to SyncOp.INSERT), ops.map { it.table to it.type }.toSet())
    assertEquals(d0.transactions.sumOf { if (it.type == "give") it.amount else -it.amount } - 50.0, ShopStore.debt(next, "c1"), 1e-9)
    assertNull("دورِ دوم کاری ندارد", CustomerMerge.sweep(next, "2026-10-20", 6, ::newId, docs = false))
    assertNull("بی حلقه هم کاری ندارد", CustomerMerge.sweep(link(d0).data, "2026-10-20", 6, ::newId, docs = false))
  }

  @Test fun `حذفِ ماندگار سندهای پیوندی را هم می‌برد — مثلِ دفترِ v1 پس از ادغام`() {
    val linked = link(d0).data
    val v1 = moved(d0).data
    val a = DebtorEngine.delete(linked, "c2", "2026-10-20", 1) { "x" } as DebtorEngine.Result.Ok
    val b = DebtorEngine.delete(v1, "c2", "2026-10-20", 1) { "x" } as DebtorEngine.Result.Ok
    assertEquals(b.data.transactions.map { it.id }, a.data.transactions.map { it.id })
    assertEquals(b.data.auditLog.last().notes, a.data.auditLog.last().notes)
    assertEquals(DebtorEngine.deleteWarning(v1, "c2"), DebtorEngine.deleteWarning(linked, "c2"))
  }
}
