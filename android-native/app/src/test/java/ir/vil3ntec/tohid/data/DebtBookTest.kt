package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  دفترِ قرض‌داران (v3 §22.2-A5، §21.11، CRM-01/03/05/07).
 *
 *  دو گروه آزمون:
 *   • مرجع (characterisation): روی دفترِ امروز — هم ساخته‌شده با موتورهای واقعی،
 *     هم JSONِ قدیمی بی فیلدهای تازه — ماندهٔ آخر برابرِ `ShopStore.debt` و ماندهٔ
 *     هر ردیف برابرِ `CustomerEngine.ledger` است، و خواندن/نوشتنِ JSONِ قدیمی
 *     بایت‌به‌بایت همان می‌ماند. یعنی هیچ عددِ نشان‌داده‌شده‌ای عوض نشده.
 *   • رفتارِ تازه: اقلام، تخصیصِ پرداخت، سررسید، سقفِ اعتبار و رسید.
 */
class DebtBookTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private val oil = Product(id = "p2", name = "روغن", unit = "بوتل", purchasePrice = 80.0, salePrice = 120.0)
  private var seq = 0
  private fun newId() = "x${seq++}"
  private val d0 = ShopData(
    products = listOf(rice, oil),
    debtors = listOf(Debtor(id = "c1", name = "احمد", phone = "0790000000"), Debtor(id = "c2", name = "محمود")),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 100.0),
      WarehouseEntry(id = "w2", productId = "p2", units = 100.0),
    ),
  )
  private val m = DebtBook::minor

  private fun ok(r: SalesEngine.Result) = (r as SalesEngine.Result.Ok).data
  private fun ok(r: DebtorEngine.Result) = when (r) {
    is DebtorEngine.Result.Ok -> r.data
    is DebtorEngine.Result.Failed -> throw AssertionError(r.message)
  }

  private fun creditSale(
    d: ShopData, who: String, lines: List<SalesEngine.CartLine>, day: String,
    paid: Double = 0.0, discount: Double = 0.0, now: Long = 0,
  ) = ok(
    SalesEngine.record(
      d, lines,
      SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, debtorId = who, paidAmount = paid, discountValue = discount),
      day, now, ::newId,
    )
  )

  private fun tx(d: ShopData, who: String, kind: DebtorEngine.Kind, amount: Double, day: String, now: Long, notes: String = "", due: String? = null) =
    ok(DebtorEngine.addTransaction(d, who, kind, amount, day, notes, day, now, ::newId, dueDate = due))

  /** دفتری با همهٔ رویدادهای امروز: نسیه با پیش‌پرداخت و تخفیف، برد و رسیدِ دستی، مرجوعی، لغو */
  private fun busy(): ShopData {
    var d = creditSale(d0, "c1", listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("p2", 1.0)), "2026-10-01", paid = 100.0, discount = 20.0, now = 10)
    d = tx(d, "c1", DebtorEngine.Kind.GIVE, 250.5, "2026-10-02", 20)
    d = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 300.0, "2026-10-03", 30)
    d = creditSale(d, "c1", listOf(SalesEngine.CartLine("p1", 1.25)), "2026-10-04", now = 40)
    val second = d.sales.last()
    val item = d.saleItems.first { it.saleId == second.id }
    d = ok(SalesEngine.recordReturn(d, second.id, mapOf(item.id to 0.25), "خراب", "2026-10-05", 50, ::newId))
    d = creditSale(d, "c2", listOf(SalesEngine.CartLine("p2", 3.0)), "2026-10-05", now = 55)
    d = ok(SalesEngine.cancel(d, d.sales.last().id, "2026-10-06", 60, ::newId, "اشتباه"))
    d = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 0.1, "2026-10-07", 70)
    d = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 0.2, "2026-10-07", 71)
    return d
  }

  /* ───────────────────────────── مرجع ───────────────────────────── */

  @Test fun `مرجع — ماندهٔ آخر و ماندهٔ هر ردیف همان عددِ امروز است`() {
    val d = busy()
    for (c in d.debtors) {
      val book = DebtBook.book(d, c.id, "2026-10-17")!!
      assertEquals(m(ShopStore.debt(d, c.id)), book.balance)
      val ledger = CustomerEngine.ledger(d, c.id)
      assertEquals(ledger.map { it.tx.id }, book.entries.map { it.tx.id })
      ledger.zip(book.entries).forEach { (l, e) ->
        assertEquals(l.row, e.row)
        assertEquals(m(l.balance), e.balanceAfter)
        assertEquals(l.saleId, e.sale?.id)
        assertEquals(l.invoiceLabel, e.invoiceLabel)
      }
    }
    //  ۰٫۱ + ۰٫۲ در Double ⇒ ۰٫۳۰۰۰۰۰۰۰۰۰۰۰۰۰۰۴؛ این‌جا دقیقاً ۳۰ پول
    val c1 = DebtBook.book(d, "c1", "2026-10-17")!!
    val n = c1.entries.size
    assertEquals(c1.entries[n - 3].balanceAfter - m(0.3), c1.entries[n - 1].balanceAfter)
  }

  @Test fun `مرجع — دفترِ قدیمیِ JSON بی فیلدهای تازه همان خوانده و همان نوشته می‌شود`() {
    //  همان شکلی که وب و نسخه‌های قبلی می‌نویسند (کلیدهای ShopData پیش‌فرض)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    val oldDebtor = """{"id":"c9","name":"کریم","phone":"","notes":"","createdAt":5}"""
    val oldTx = """{"id":"t9","debtorId":"c9","type":"give","amount":1500.0,"date":"2026-09-01","notes":"","createdAt":6}"""
    val debtor = json.decodeFromString(Debtor.serializer(), oldDebtor)
    val t = json.decodeFromString(DebtTransaction.serializer(), oldTx)
    assertNull(debtor.creditLimit)
    assertNull(t.dueDate)
    assertEquals(oldDebtor, json.encodeToString(Debtor.serializer(), debtor))
    assertEquals(oldTx, json.encodeToString(DebtTransaction.serializer(), t))

    val d = ShopData(debtors = listOf(debtor), transactions = listOf(t))
    val book = DebtBook.book(d, "c9", "2026-10-17")!!
    assertEquals(m(1500.0), book.balance)
    assertNull(book.credit)
    assertTrue(book.overdue.isEmpty())
    //  سررسیدی که هست، نوشته و خوانده می‌شود
    val withDue = t.copy(dueDate = "2026-09-30")
    val back = json.decodeFromString(DebtTransaction.serializer(), json.encodeToString(DebtTransaction.serializer(), withDue))
    assertEquals("2026-09-30", back.dueDate)
  }

  @Test fun `مرجع — مدتِ قرض و پیام‌ها دست نخورده‌اند`() {
    val d = busy()
    //  همان تابعِ امروز؛ این‌جا فقط سنجیده می‌شود که با دفترِ تازه هم کار می‌کند
    assertNotNull(DebtorEngine.debtSince(d, "c1"))
    assertNull(DebtorEngine.debtSince(d, "c2"))
    assertEquals("حساب صاف است", DebtBook.state(m(0.0)))
  }

  /* ───────────────────────────── اقلام ───────────────────────────── */

  @Test fun `هر برد اقلامِ فاکتورش را با قیمتِ همان لحظه، وزن، واحد و تخفیف دارد`() {
    var d = creditSale(d0, "c1", listOf(SalesEngine.CartLine("p1", 1.25), SalesEngine.CartLine("p2", 2.0)), "2026-10-01", paid = 50.0, discount = 15.0)
    //  قیمتِ کالا بعداً بالا رفت — بردِ دیروز همان قیمتِ دیروز را نشان می‌دهد
    d = d.copy(products = d.products.map { if (it.id == "p1") it.copy(salePrice = 999.0) else it })
    val e = DebtBook.book(d, "c1", "2026-10-17")!!.entries.single()
    assertTrue(e.give)
    assertEquals(listOf("برنج", "روغن"), e.lines.map { it.name })
    assertEquals(1.25, e.lines[0].quantity, 0.0)
    assertEquals("کیلو", e.lines[0].unit)
    assertEquals(m(300.0), e.lines[0].unitPrice)
    assertEquals(m(375.0), e.lines[0].total)
    assertEquals(m(240.0), e.lines[1].total)
    val s = e.sale!!
    assertEquals(15.0, s.discount, 0.0)
    assertEquals(50.0, s.paidAmount, 0.0)
    //  قرضِ این برد = ۶۱۵ − ۱۵ − ۵۰
    assertEquals(m(550.0), e.amount)
    assertEquals(m(550.0), e.open)
  }

  /* ───────────────────────────── پرداخت ───────────────────────────── */

  @Test fun `پرداختِ جزئی از قدیمی‌ترین برد کم می‌شود و پیش‌پرداخت روی بردِ بعدی می‌نشیند`() {
    var d = tx(d0, "c1", DebtorEngine.Kind.RECEIVE, 100.0, "2026-10-01", 1)          // پیش‌پرداخت
    d = tx(d, "c1", DebtorEngine.Kind.GIVE, 500.0, "2026-10-02", 2)
    d = tx(d, "c1", DebtorEngine.Kind.GIVE, 300.0, "2026-10-03", 3)
    d = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 450.0, "2026-10-04", 4)
    val b = DebtBook.book(d, "c1", "2026-10-17")!!
    val (pre, g1, g2, pay) = b.entries
    assertEquals(m(0.0), pre.open)
    assertEquals(listOf(DebtBook.Allocation(g1.tx.id, m(100.0))), pre.allocations)
    assertTrue(g1.paid)
    assertEquals(listOf(m(100.0), m(400.0)), g1.allocations.map { it.amount })
    assertEquals(m(250.0), g2.open)
    assertFalse(g2.paid)
    assertEquals(listOf(g1.tx.id to m(400.0), g2.tx.id to m(50.0)), pay.allocations.map { it.txId to it.amount })
    assertEquals(m(700.0), pay.balanceBefore)
    assertEquals(m(250.0), pay.balanceAfter)
    assertEquals(m(250.0), b.balance)
  }

  @Test fun `مرجوعیِ یک فاکتور اول همان فاکتور را می‌بندد، نه قدیمی‌ترین را`() {
    var d = tx(d0, "c1", DebtorEngine.Kind.GIVE, 1000.0, "2026-09-01", 1)
    d = creditSale(d, "c1", listOf(SalesEngine.CartLine("p2", 2.0)), "2026-10-01", now = 2)
    val sale = d.sales.last()
    val item = d.saleItems.first { it.saleId == sale.id }
    d = ok(SalesEngine.recordReturn(d, sale.id, mapOf(item.id to 1.0), "", "2026-10-02", 3, ::newId))
    val b = DebtBook.book(d, "c1", "2026-10-17")!!
    val (old, credit, back) = b.entries
    assertEquals(m(1000.0), old.open)
    assertEquals(m(120.0), credit.open)
    assertEquals(sale.id, back.sale?.id)
    assertEquals(listOf(credit.tx.id), back.allocations.map { it.txId })
  }

  /* ───────────────────────────── سررسید ───────────────────────────── */

  @Test fun `سررسید — ثبت، اعتبارسنجی، عقب‌افتاده و نزدیک‌ترین`() {
    var d = tx(d0, "c1", DebtorEngine.Kind.GIVE, 500.0, "2026-10-01", 1, due = "2026-10-10")
    d = tx(d, "c1", DebtorEngine.Kind.GIVE, 200.0, "2026-10-02", 2, due = "2026-10-30")
    //  رسید سررسید نمی‌گیرد
    d = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 100.0, "2026-10-03", 3, due = "2026-10-30")
    assertNull(d.transactions.last().dueDate)

    val bad = DebtorEngine.addTransaction(d, "c1", DebtorEngine.Kind.GIVE, 5.0, "2026-10-05", "", "2026-10-05", 4, ::newId, dueDate = "2026-10-04")
    assertEquals("سررسید نمی‌تواند پیش از تاریخِ برد باشد", (bad as DebtorEngine.Result.Failed).message)
    val junk = DebtorEngine.addTransaction(d, "c1", DebtorEngine.Kind.GIVE, 5.0, "2026-10-05", "", "2026-10-05", 4, ::newId, dueDate = "1405/07/01")
    assertEquals("سررسید خوانده نشد", (junk as DebtorEngine.Result.Failed).message)

    val b = DebtBook.book(d, "c1", "2026-10-17")!!
    assertEquals(listOf(b.entries[0].tx.id), b.overdue.map { it.tx.id })
    assertEquals(m(400.0), b.overdueAmount)
    assertEquals(b.entries[1].tx.id, b.nextDue?.tx?.id)
    //  پیش از سررسید، عقب‌افتاده نیست
    assertTrue(DebtBook.book(d, "c1", "2026-10-10")!!.overdue.isEmpty())
    //  پرداختِ کامل ⇒ دیگر عقب‌افتاده نیست
    val paid = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 400.0, "2026-10-16", 5)
    assertTrue(DebtBook.book(paid, "c1", "2026-10-17")!!.overdue.isEmpty())
  }

  @Test fun `سررسیدِ فروشِ نسیه بعداً گذاشته می‌شود و ردش می‌ماند`() {
    val d = creditSale(d0, "c1", listOf(SalesEngine.CartLine("p1", 1.0)), "2026-10-01")
    val give = d.transactions.single()
    val r = DebtorEngine.setDueDate(d, give.id, "2026-11-01", "2026-10-02", 9, ::newId)
    val after = ok(r)
    assertEquals("2026-11-01", after.transactions.single().dueDate)
    assertEquals(give.amount, after.transactions.single().amount, 0.0)
    assertEquals("debt_due_set", after.auditLog.last().type)
    //  برداشتن
    val cleared = ok(DebtorEngine.setDueDate(after, give.id, null, "2026-10-03", 10, ::newId))
    assertNull(cleared.transactions.single().dueDate)
    //  رسید سررسید ندارد؛ همان مقدار ⇒ بی ردِ تازه
    val recv = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 10.0, "2026-10-02", 3)
    assertTrue(DebtorEngine.setDueDate(recv, recv.transactions.last().id, "2026-11-01") is DebtorEngine.Result.Failed)
    assertEquals(after.auditLog.size, ok(DebtorEngine.setDueDate(after, give.id, "2026-11-01")).auditLog.size)
  }

  /* ───────────────────────────── سقفِ اعتبار ───────────────────────────── */

  @Test fun `سقفِ اعتبار — بی سقف همان رفتار، با سقف تأیید لازم و ردش می‌ماند`() {
    //  بی سقف: هر مبلغی، بی هیچ پرسشی
    var d = tx(d0, "c1", DebtorEngine.Kind.GIVE, 4000.0, "2026-10-01", 1)
    assertNull(DebtBook.creditCheck(d, "c1", m(1_000_000.0)))

    d = ok(DebtorEngine.editWithLimit(d, "c1", DebtorEngine.DebtorDraft("احمد", "0790000000", "", creditLimit = 5000.0)))
    assertEquals("credit_limit_set", d.auditLog.last().type)
    assertEquals("0790000000", d.debtors.first().phone)
    val c = DebtBook.creditCheck(d, "c1", m(2000.0))!!
    assertTrue(c.over)
    assertEquals(m(1000.0), c.excess)
    assertEquals(m(1000.0), c.headroom)

    val refused = DebtorEngine.addTransaction(d, "c1", DebtorEngine.Kind.GIVE, 2000.0, "2026-10-02", "", "2026-10-02", 2, ::newId)
    assertTrue((refused as DebtorEngine.Result.Failed).message.startsWith("1,000 افغانی بیش از سقفِ اعتبار"))
    //  رسید هیچ‌وقت بسته نمی‌شود
    tx(d, "c1", DebtorEngine.Kind.RECEIVE, 10.0, "2026-10-02", 2)
    //  زیرِ سقف، بی پرسش
    tx(d, "c1", DebtorEngine.Kind.GIVE, 1000.0, "2026-10-02", 2)

    val approved = ok(DebtorEngine.addTransaction(d, "c1", DebtorEngine.Kind.GIVE, 2000.0, "2026-10-02", "", "2026-10-02", 2, ::newId, overLimit = true))
    assertEquals("credit_over_limit", approved.auditLog.last().type)
    assertEquals(m(6000.0), DebtBook.book(approved, "c1", "2026-10-17")!!.balance)

    assertTrue(DebtorEngine.setCreditLimit(d, "c1", -1.0) is DebtorEngine.Result.Failed)
    assertTrue(DebtorEngine.editWithLimit(d, "c1", DebtorEngine.DebtorDraft("احمد", creditLimit = Double.NaN)) is DebtorEngine.Result.Failed)
    assertTrue(DebtorEngine.add(d, DebtorEngine.DebtorDraft("تازه", creditLimit = -5.0), 1, ::newId) is DebtorEngine.Result.Failed)
    //  همان سقف ⇒ بی ردِ تازه؛ برداشتنِ سقف ⇒ رد
    assertEquals(d.auditLog.size, ok(DebtorEngine.setCreditLimit(d, "c1", 5000.0)).auditLog.size)
    val cleared = ok(DebtorEngine.setCreditLimit(d, "c1", null))
    assertNull(cleared.debtors.first().creditLimit)
    assertEquals("credit_limit_set", cleared.auditLog.last().type)
    //  `edit`ِ تنها به سقف دست نمی‌زند
    assertEquals(5000.0, ok(DebtorEngine.edit(d, "c1", DebtorEngine.DebtorDraft("احمد"))).debtors.first().creditLimit!!, 0.0)
  }

  /* ───────────────────────────── رسید ───────────────────────────── */

  @Test fun `رسیدِ برد با اقلام و رسیدِ دریافت با پیش و پس`() {
    var d = creditSale(d0, "c1", listOf(SalesEngine.CartLine("p1", 1.25)), "2026-10-01", paid = 75.0)
    d = tx(d, "c1", DebtorEngine.Kind.RECEIVE, 100.0, "2026-10-05", 5)
    val give = d.transactions.first()
    val r = DebtBook.receipt(d, "c1", give.id, "دکان توحید", "2026-10-17")!!
    assertEquals("دکان توحید — رسیدِ قرض", r.title)
    val rows = r.rows.toMap()
    assertEquals("375", rows["برنج — 1.25 کیلو × 300"])
    assertEquals("375 افغانی", rows["جمعِ اقلام"])
    assertEquals("75 افغانی", rows["پرداختِ همان لحظه"])
    assertEquals("300 افغانی", rows["قرضِ این ردیف"])
    assertEquals("ندارد", rows["سررسید"])
    assertEquals("200 افغانی", rows["باقیِ این قرض"])
    assertTrue(r.text().startsWith("دکان توحید — رسیدِ قرض\nمشتری: احمد"))
    assertEquals(listOf("دکان توحید — رسیدِ قرض", ""), r.sheet().rows.first())

    val pay = DebtBook.receipt(d, "c1", d.transactions.last().id, "", "2026-10-17")!!
    val p = pay.rows.toMap()
    assertEquals("فروشگاه — رسیدِ دریافت", pay.title)
    assertEquals("100 افغانی", p["مبلغِ دریافتی"])
    assertEquals("300 افغانی بدهکار", p["مانده پیش از دریافت"])
    assertEquals("200 افغانی بدهکار", p["مانده پس از این ردیف"])
    assertEquals("100 افغانی", p["بابتِ فاکتور #1"])
    assertNull(DebtBook.receipt(d, "c1", "nope", "", "2026-10-17"))
  }
}
