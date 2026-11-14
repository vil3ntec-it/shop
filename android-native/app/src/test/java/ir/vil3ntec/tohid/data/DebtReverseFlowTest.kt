package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.debt.DebtReversal
import ir.vil3ntec.tohid.core.promo.Loyalty
import ir.vil3ntec.tohid.isoMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S89 (تصمیمِ ۱۴۰۵/۰۸/۲۳، پرسشِ ۲۳) — «حذف» ِ قرض/رسیدِ ثبت‌شده با `shop.doc_chain` سندِ معکوس است
 *  (`DebtorEngine.removeTransaction`، منطق در `core/debt/DebtReversal`). کلیدِ خاموش دقیقاً حذفِ امروز؛ روشن: اصل می‌ماند،
 *  مانده/کهنگی/سررسید/امتیاز/گزارش‌ها همان حذفِ امروزند و رسیدِ نقدیِ معکوس از دخلِ **روزِ معکوس** کم می‌شود.
 *
 *  دفتر: برد ۴۰۰ (۰۸/۰۱، سررسید ۰۸/۰۵)، برد ۶۰۰ (۰۸/۰۳)، رسیدِ نقدِ ۴۰۰ (۰۸/۱۰، صندوقِ A)؛ معکوس در ۰۸/۲۰ (صندوقِ B).
 *  اگر جفت در نمای مؤثر نبود، رسید بردِ کهنه را می‌بست و معکوس بردِ تازه می‌شد ⇒ کهنگی، سررسید و سنِ بدهی عوض می‌شد.
 */
class DebtReverseFlowTest {

  private var seq = 0
  private val newId: () -> String = { "n${++seq}" }
  private val today = "2026-08-20"
  private val now = isoMillis(today) + 60_000

  @After fun reset() { LoyaltyRules.override = null }

  private fun ok(r: DebtorEngine.Result): ShopData {
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is DebtorEngine.Result.Ok)
    return (r as DebtorEngine.Result.Ok).data
  }

  private fun failed(r: DebtorEngine.Result): String {
    assertTrue("انتظار می‌رفت رد شود: $r", r is DebtorEngine.Result.Failed)
    return (r as DebtorEngine.Result.Failed).message
  }

  private fun tx(d: ShopData, id: String, kind: DebtorEngine.Kind, amount: Double, date: String, due: String? = null): ShopData =
    ok(DebtorEngine.addTransaction(d, "d1", kind, amount, date, "", date, isoMillis(date) + 60_000, newId, dueDate = due, txId = id))

  /** دفترِ پایه با صندوقِ A ِ بسته (رسید درونش) و صندوقِ B ِ باز */
  private fun ledger(): ShopData {
    var d = ShopData(debtors = listOf(Debtor(id = "d1", name = "احمد")))
    d = tx(d, "g1", DebtorEngine.Kind.GIVE, 400.0, "2026-08-01", due = "2026-08-05")
    d = tx(d, "g2", DebtorEngine.Kind.GIVE, 600.0, "2026-08-03", due = "2026-09-30")
    val day10 = isoMillis("2026-08-10")
    d = (CashShift.open(d, 1000.0, "", "2026-08-10", day10, newId) as CashShift.Result.Ok).data
    d = tx(d, "r1", DebtorEngine.Kind.RECEIVE, 400.0, "2026-08-10")
    d = (CashShift.close(d, 1400.0, "", "", "2026-08-10", day10 + 120_000, newId) as CashShift.Result.Ok).data
    d = (CashShift.open(d, 500.0, "", today, now - 30_000, newId) as CashShift.Result.Ok).data
    return d
  }

  private fun reversed(d: ShopData, id: String = "r1") = ok(DebtorEngine.removeTransaction(d, id, chainOn = true, today = today, now = now, newId = newId))
  private fun deleted(d: ShopData, id: String = "r1") = ok(DebtorEngine.deleteTransaction(d, id, today, now, newId))

  @Test fun `کلیدِ خاموش دقیقاً همان حذفِ امروز است`() {
    val d = ledger()
    assertEquals(
      DebtorEngine.deleteTransaction(d, "r1", today, now) { "a1" },
      DebtorEngine.removeTransaction(d, "r1", chainOn = false, today = today, now = now) { "a1" },
    )
    //  ردیفی که پیش‌تر معکوس شده هم با کلیدِ خاموش همان حذفِ ساده است
    val withRev = reversed(d)
    assertEquals(
      DebtorEngine.deleteTransaction(withRev, "r1-rv", today, now) { "a2" },
      DebtorEngine.removeTransaction(withRev, "r1-rv", chainOn = false, today = today, now = now) { "a2" },
    )
  }

  @Test fun `کلیدِ روشن - اصل می‌ماند و ردیفِ معکوس با نوعِ مخالف و همان مبلغ می‌نشیند`() {
    val d = ledger()
    val r = reversed(d)
    assertTrue(r.transactions.any { it.id == "r1" })
    val rev = r.transactions.single { it.id == "r1-rv" }
    assertEquals(listOf("d1", "give", "r1", DebtReversal.KIND, today, now), listOf(rev.debtorId, rev.type, rev.refId, rev.kind, rev.date, rev.createdAt))
    assertEquals(400.0, rev.amount, 0.0)
    assertEquals("debt_tx_reverse", r.auditLog.last().type)
    assertEquals(ShopStore.debt(deleted(d), "d1"), ShopStore.debt(r, "d1"), 0.0)
    //  معکوسِ بردِ دستی: رسیدِ معکوس (نقد نیست)
    val g = reversed(d, "g2").transactions.single { it.id == "g2-rv" }
    assertEquals("receive", g.type)
    assertTrue(!CashShift.isCashReceipt(g))
  }

  @Test fun `کلیدِ روشن - معکوسِ دوباره، معکوسِ معکوس و ردیفِ ساختهٔ سند رد می‌شوند`() {
    val r = reversed(ledger())
    failed(DebtorEngine.removeTransaction(r, "r1", chainOn = true, today = today, now = now, newId = newId))
    failed(DebtorEngine.removeTransaction(r, "r1-rv", chainOn = true, today = today, now = now, newId = newId))
    val sys = r.copy(transactions = r.transactions + DebtTransaction("s1", "d1", "give", 90.0, today, kind = SalesEngine.SALE_CREDIT, refId = "sale-1"))
    failed(DebtorEngine.removeTransaction(sys, "s1", chainOn = true, today = today, now = now, newId = newId))
    failed(DebtorEngine.removeTransaction(r, "nope", chainOn = true, today = today, now = now, newId = newId))
  }

  @Test fun `دخلِ روزِ معکوس کم می‌شود و روزِ اصل دست نمی‌خورد`() {
    val r = reversed(ledger())
    val (a, b) = CashShift.list(r).let { it.last() to it.first() }
    assertEquals(400.0, CashShift.totalsOf(r, a, now).receipts, 0.0)
    val tb = CashShift.totalsOf(r, b, now + 1_000)
    assertEquals(-400.0, tb.receipts, 0.0)
    assertEquals(100.0, tb.expected, 0.0)
    //  گزارشِ X ِ همان صندوق (هستهٔ پرداخت) همان نقد را انتظار دارد
    val x = (PayFlow.report(r, b.open, null, now + 1_000) as PayFlow.Outcome.Ok).value
    assertEquals(10_000L, x.report.expected!![PayFlow.CASH])
    assertTrue(x.skipped.isEmpty())
    //  معکوسِ بردِ دستی هیچ پولی به دخل نمی‌آورد
    val g = reversed(ledger(), "g2")
    assertEquals(0.0, CashShift.totalsOf(g, CashShift.list(g).first(), now + 1_000).receipts, 0.0)
  }

  @Test fun `کهنگیِ قرض، سررسید و گزارش‌ها همان حذفِ امروزند`() {
    val d = ledger()
    val r = reversed(d)
    val x = deleted(d)
    assertEquals(isoMillis("2026-08-01") + 60_000, DebtorEngine.debtSince(r, "d1"))
    assertEquals(DebtorEngine.debtSince(x, "d1"), DebtorEngine.debtSince(r, "d1"))
    val br = DebtBook.book(r, "d1", today)!!
    val bx = DebtBook.book(x, "d1", today)!!
    assertEquals(listOf("g1"), br.overdue.map { it.tx.id })
    assertEquals(bx.overdueAmount, br.overdueAmount)
    assertEquals(bx.balance, br.balance)
    //  سنِ بدهی (هستهٔ گزارش) و گزارشِ قرض‌داران
    assertEquals(CustomerInsight.agingOf(x, "d1", today), CustomerInsight.agingOf(r, "d1", today))
    assertNotNull(CustomerInsight.agingOf(r, "d1", today))
    val sr = ReportEngine.debtors(r).single()
    val sx = ReportEngine.debtors(x).single()
    assertEquals(listOf(sx.given, sx.received, sx.remaining), listOf(sr.given, sr.received, sr.remaining))
    //  حساب: «داده/گرفته» همان، ولی فهرست هر دو ردیف را دارد
    val acc = DebtorEngine.account(r, "d1")!!
    assertEquals(listOf(1000.0, 0.0), listOf(acc.given, acc.received))
    assertTrue(acc.transactions.map { it.id }.containsAll(listOf("r1", "r1-rv")))
  }

  @Test fun `امتیاز - معکوسِ رسید همان پس‌گرفتنِ حذف است و رسیدِ معکوس‌شده دیگر شمرده نمی‌شود`() {
    val rule = Loyalty.Rule(everyAfn = 100, pointAfn = 1, maxSharePct = 20, silverAfn = 50_000, goldAfn = 200_000)
    LoyaltyRules.override = LoyaltyRules.Config(true, rule, false)
    val base = ShopData(
      products = listOf(Product(id = "p1", name = "روغن", salePrice = 1000.0, purchasePrice = 700.0, unit = "عدد")),
      warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0)),
      debtors = listOf(Debtor(id = "d1", name = "نوشا")),
    )
    val sale = SalesEngine.record(
      base, listOf(SalesEngine.CartLine("p1", 2.0)),
      SalesEngine.Checkout(debtorId = "d1", payment = SalesEngine.Payment.CREDIT, paidAmount = 500.0), today, now, newId, saleId = "s1",
    )
    fun pay(d: ShopData, key: String, at: Long) =
      DebtEntry.change(DebtEntry.Entry("d1", DebtorEngine.Kind.RECEIVE, 1000.0, today, ""), today, at, newId) { error(it) }(d, key)!!
    var d = (LoyaltyPoints.post(sale, true, rule, null, "") as SalesEngine.Result.Ok).data
    d = pay(d, "t1", now + 1)
    assertEquals(15L, LoyaltyPoints.balance(d, "d1"))
    val r = ok(DebtorEngine.removeTransaction(d, "t1", chainOn = true, today = today, now = now + 2, newId = newId))
    val x = ok(DebtorEngine.deleteTransaction(d, "t1", today, now + 2, newId))
    assertEquals(5L, LoyaltyPoints.balance(r, "d1"))
    assertEquals(LoyaltyPoints.balance(x, "d1"), LoyaltyPoints.balance(r, "d1"))
    //  رسیدِ تازه: همان ۱۰ امتیاز که پس از حذف می‌داد — نه ۱۵ (رسیدِ معکوس‌شده در استخر نیست)
    val r2 = pay(r, "t2", now + 3)
    val x2 = pay(x, "t2", now + 3)
    assertEquals(10L, r2.auditLog.single { it.id == "t2-lp-s1" }.points)
    assertEquals(LoyaltyPoints.balance(x2, "d1"), LoyaltyPoints.balance(r2, "d1"))
  }
}
