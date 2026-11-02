package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ledger.AppendOnce
import ir.vil3ntec.tohid.core.pos.Layaway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-27 گام ۲ — فروشِ امانی روی دفترِ دکان: سند در سابقه، پیش‌پرداخت و هر قسط رسیدِ مشتری با کلیدِ ساخته از سند،
 *  تحویل فقط پس از تسویهٔ کامل و فقط یک بار (فروشِ عادیِ صندوق، همه «به حساب»)، لغو با بازپرداختِ دقیقِ نقد از دخل.
 *  همان ترکیبی که `LayawayPref.record` می‌دود (`AppendOnce` روی دفترِ همین لحظه)؛ این‌جا دیسک یک متغیر است.
 */
class LayawaysTest {

  private val today = "2026-11-02"
  private val t0 = 1_762_000_000_000L
  private val base = ShopData(
    debtors = listOf(Debtor(id = "d1", name = "احمد")),
    products = listOf(
      Product(id = "p1", name = "یخچال", salePrice = 25_000.0, unit = "عدد"),
      Product(id = "p2", name = "پتو", salePrice = 1_250.0, unit = "عدد"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 3.0), WarehouseEntry(id = "w2", productId = "p2", units = 10.0)),
  )
  /** ۲۵٬۰۰۰ + ۲ × ۱٬۲۵۰ = ۲۷٬۵۰۰ افغانی */
  private val cart = listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 2.0))
  private var seq = 0
  private fun newId() = "n${seq++}"

  private class Disk(var ledger: ShopData) {
    var writes = 0
    fun update(transform: (ShopData?) -> ShopData?): Boolean? {
      val next = transform(ledger) ?: return null
      ledger = next; writes++
      return true
    }
  }

  private fun run(disk: Disk, key: String, has: (ShopData, String) -> Boolean, build: (ShopData, String) -> Layaways.Built): Any {
    var bad: Layaways.Built.Bad? = null
    val out = AppendOnce.append(key, has, { cur: ShopData, k: String ->
      when (val b = build(cur, k)) {
        is Layaways.Built.Ok -> b.data
        is Layaways.Built.Bad -> { bad = b; null }
      }
    }) { t -> disk.update(t) }
    return bad ?: out
  }

  private fun code(r: Any): String = (r as Layaways.Built.Bad).code

  private fun draft(deposit: Long? = 550_000, count: Int? = 3, every: Int? = 30, first: String = "2026-12-02", c: List<SalesEngine.CartLine> = cart) =
    Layaways.NewLayaway("d1", c, deposit, count, every, first)

  private fun create(disk: Disk, o: Layaways.NewLayaway = draft(), key: String = "L1", at: Long = t0) =
    run(disk, key, Layaways::hasRow) { cur, k -> Layaways.create(cur, k, o, today, at, "رحیم") }

  private fun view(disk: Disk, day: String = today) = Layaways.find(disk.ledger, "L1", day)!!

  private fun pay(disk: Disk, amount: Long, key: String = Layaways.payKey(view(disk)), at: Long = t0 + 1000) =
    run(disk, key, Layaways.hasPayment("L1", amount)) { cur, k -> Layaways.pay(cur, k, "L1", amount, today, at, "رحیم") }

  private fun deliver(disk: Disk, key: String = Layaways.deliverKey(view(disk)), at: Long = t0 + 5000) =
    run(disk, key, Layaways::hasSale) { cur, k -> Layaways.deliver(cur, k, "L1", today, at, "رحیم", "B1", false, ::newId) }

  private fun cancel(disk: Disk, reason: String = "مشتری منصرف شد", at: Long = t0 + 6000) =
    run(disk, Layaways.cancelKey(view(disk)), Layaways::hasRow) { cur, k -> Layaways.cancel(cur, k, "L1", reason, today, at, "رحیم") }

  private fun cash(d: ShopData) = CashShift.totals(d, t0 - 1, t0 + 100_000, 0.0)

  @Test fun `ساخت یک سند با جدولِ دقیق و یک رسیدِ پیش‌پرداخت می‌سازد — موجودی دست نمی‌خورد`() {
    val disk = Disk(base)
    assertTrue(create(disk) is AppendOnce.Outcome.Wrote<*>)
    val d = disk.ledger
    val row = d.auditLog.single()
    assertEquals(Layaways.TYPE, row.type)
    assertEquals(2_750_000L, row.layaway?.totalMinor)
    assertEquals(listOf(25_000_00L, 1_250_00L), row.layaway?.lines?.map { it.unitMinor })
    //  ۲۷٬۵۰۰ − ۵٬۵۰۰ = ۲۲٬۰۰۰ به سه قسط: افغانیِ باقی روی قسطِ اول، جمع همان باقی
    assertEquals(listOf(733_400L, 733_300L, 733_300L), row.layaway?.schedule?.map { it.amountMinor })
    assertEquals(listOf("2026-12-02", "2027-01-01", "2027-01-31"), row.layaway?.schedule?.map { it.due })
    val dep = d.transactions.single()
    assertEquals("L1-p0", dep.id)
    assertEquals(Layaways.DEPOSIT, dep.kind)
    assertEquals("receive", dep.type)
    assertEquals(5_500.0, dep.amount, 0.0)
    //  نقدِ دخل و پیش‌پرداختِ حسابِ مشتری؛ نه فروشی، نه حرکتِ موجودی
    assertEquals(5_500.0, cash(d).expected, 0.0)
    assertEquals(-5_500.0, ShopStore.debt(d, "d1"), 0.0)
    assertTrue(d.sales.isEmpty() && d.stockMovements.isEmpty())
    assertEquals(3.0, ShopStore.stock(d, "p1"), 0.0)
    val v = view(disk)
    assertEquals(Layaways.Status.OPEN, v.status)
    assertEquals(2_200_000L, v.progress.remainingMinor)
    assertFalse(v.review || v.paidUp || v.late)
    //  همان کلید دوباره ⇒ هیچ نوشتنی
    assertEquals(AppendOnce.Outcome.Already, create(disk))
    assertEquals(1, disk.writes)
  }

  @Test fun `ساختِ نادرست هیچ نمی‌نویسد`() {
    val disk = Disk(base)
    assertEquals("NO_CUSTOMER", code(run(disk, "x", Layaways::hasRow) { cur, k -> Layaways.create(cur, k, draft().copy(debtorId = null), today, t0, "") }))
    assertEquals("EMPTY", code(create(disk, draft(c = emptyList()))))
    assertEquals("DEPOSIT_OVER_TOTAL", code(create(disk, draft(deposit = 2_750_000))))
    assertEquals("NOT_WHOLE", code(create(disk, draft(deposit = 550_050))))
    assertEquals("BAD_COUNT", code(create(disk, draft(count = 37))))
    assertEquals("DUE_PAST", code(create(disk, draft(first = "2026-11-01"))))
    assertEquals("NO_PRODUCT", code(create(disk, draft(c = listOf(SalesEngine.CartLine("gone", 1.0))))))
    //  سبدی که صندوق نمی‌فروشد (کالای غیرفعال) امانی هم نمی‌شود
    val off = Disk(base.copy(products = base.products.map { if (it.id == "p2") it.copy(active = false) else it }))
    assertEquals("SALE", code(create(off)))
    assertEquals(0, disk.writes + off.writes)
  }

  @Test fun `هر قسط یک رسیدِ نقد با کلیدِ ساخته از سند و شمارهٔ پرداخت`() {
    val disk = Disk(base)
    create(disk)
    assertEquals("L1-p1", Layaways.payKey(view(disk)))
    assertTrue(pay(disk, 733_400) is AppendOnce.Outcome.Wrote<*>)
    assertEquals("L1-p2", Layaways.payKey(view(disk)))
    assertTrue(pay(disk, 500_000) is AppendOnce.Outcome.Wrote<*>)
    val d = disk.ledger
    val rows = d.transactions.filter { it.kind == Layaways.INSTALLMENT }
    assertEquals(listOf("L1-p1", "L1-p2"), rows.map { it.id })
    assertTrue(rows.all { it.type == "receive" && it.refId == "L1" && it.debtorId == "d1" })
    val v = view(disk)
    assertEquals(1_233_400L, v.paymentsMinor)
    assertEquals(966_600L, v.progress.remainingMinor)
    assertEquals(listOf(Layaway.State.PAID, Layaway.State.PARTIAL, Layaway.State.DUE), v.progress.lines.map { it.state })
    //  دخل: پیش‌پرداخت + دو قسط؛ حسابِ مشتری همان پیش‌پرداختِ بیشتر
    assertEquals(5_500.0 + 7_334.0 + 5_000.0, cash(d).expected, 0.0)
    assertEquals(-(5_500.0 + 7_334.0 + 5_000.0), ShopStore.debt(d, "d1"), 0.0)
    assertTrue(d.sales.isEmpty())
  }

  @Test fun `تکرارِ همان قسط یک رسید است و همان کلید با مبلغِ دیگر رد می‌شود`() {
    val disk = Disk(base)
    create(disk)
    val key = Layaways.payKey(view(disk))
    assertTrue(pay(disk, 733_400, key) is AppendOnce.Outcome.Wrote<*>)
    //  دو ضربه یا تکرار پس از قطع: همان کلید و همان مبلغ ⇒ هیچ نوشتنی
    assertEquals(AppendOnce.Outcome.Already, pay(disk, 733_400, key))
    //  دستگاهِ دیگر با همان شمارهٔ پرداخت ولی مبلغِ دیگر ⇒ STALE، نه رسیدِ دوم
    assertEquals("STALE", code(pay(disk, 100_000, key)))
    assertEquals(1, disk.ledger.transactions.count { it.kind == Layaways.INSTALLMENT })
    assertEquals(2, disk.writes)
  }

  @Test fun `بیش از مانده هرگز گرفته نمی‌شود`() {
    val disk = Disk(base)
    create(disk)
    assertEquals("OVER_REMAINING", code(pay(disk, 2_200_100)))
    assertEquals("NOT_WHOLE", code(pay(disk, 50)))
    assertEquals("BAD_AMOUNT", code(pay(disk, 0)))
    assertTrue(pay(disk, 2_000_000) is AppendOnce.Outcome.Wrote<*>)
    assertEquals("OVER_REMAINING", code(pay(disk, 200_100)))
    assertTrue(pay(disk, 200_000) is AppendOnce.Outcome.Wrote<*>)
    assertEquals("SETTLED", code(pay(disk, 100)))
    val v = view(disk)
    assertEquals(0L, v.progress.remainingMinor)
    assertEquals(0L, v.progress.overMinor)
    //  همهٔ پرداختی دقیقاً همان جمع
    assertEquals(2_750_000L, v.heldMinor)
    assertTrue(v.paidUp)
  }

  @Test fun `تحویل فقط پس از تسویهٔ کامل و فقط یک بار — موجودی همان لحظه کم می‌شود`() {
    val disk = Disk(base)
    create(disk)
    pay(disk, 1_000_000)
    //  پیش از تسویه هیچ فروشی
    assertEquals("NOT_SETTLED", code(deliver(disk)))
    assertTrue(disk.ledger.sales.isEmpty())
    pay(disk, 1_200_000)
    assertEquals("L1-d1", Layaways.deliverKey(view(disk)))
    assertTrue(deliver(disk) is AppendOnce.Outcome.Wrote<*>)
    val d = disk.ledger
    val sale = d.sales.single()
    assertEquals("L1-d1", sale.id)
    assertEquals("B1", sale.series)
    assertEquals(27_500.0, sale.finalTotal, 0.0)
    assertEquals(27_500.0, sale.debtGiven, 0.0)
    assertEquals("d1", sale.debtorId)
    assertEquals(listOf(1.0, 2.0), d.saleItems.map { it.quantity })
    assertEquals(2.0, ShopStore.stock(d, "p1"), 0.0)
    assertEquals(8.0, ShopStore.stock(d, "p2"), 0.0)
    assertTrue(d.transactions.single { it.kind == SalesEngine.SALE_CREDIT }.notes.startsWith("تسویه از پرداخت‌های فروشِ امانی"))
    //  حسابِ مشتری صفر؛ دخل همان پرداختی‌ها (فاکتور نقدی به دخل نیاورد)
    assertEquals(0.0, ShopStore.debt(d, "d1"), 0.0)
    assertEquals(27_500.0, cash(d).expected, 0.0)
    val v = view(disk)
    assertEquals(Layaways.Status.DELIVERED, v.status)
    assertEquals("L1-d1", v.saleId)
    //  دوباره نه: همان کلید ⇒ همان فروش؛ کلیدِ دیگر ⇒ ردِ وضعیت؛ قسط و لغو هم نه
    assertEquals(AppendOnce.Outcome.Already, deliver(disk, key = "L1-d1"))
    assertEquals("NOT_ALLOWED", code(deliver(disk, key = "L1-d2")))
    assertEquals("NOT_ALLOWED", code(cancel(disk)))
    assertEquals(1, disk.ledger.sales.size)
    assertEquals(1, disk.ledger.stockMovements.count { it.type == "sale" && it.productId == "p1" })
  }

  @Test fun `قیمتِ سند ثابت است — تغییرِ قیمتِ فهرست فاکتورِ تحویل را عوض نمی‌کند`() {
    val disk = Disk(base)
    create(disk)
    pay(disk, 2_200_000)
    disk.ledger = disk.ledger.copy(products = disk.ledger.products.map { it.copy(salePrice = it.salePrice * 2) })
    assertTrue(deliver(disk) is AppendOnce.Outcome.Wrote<*>)
    assertEquals(27_500.0, disk.ledger.sales.single().finalTotal, 0.0)
  }

  @Test fun `لغو دقیقاً همهٔ پرداختی را نقد از دخل پس می‌دهد`() {
    val disk = Disk(base)
    create(disk)
    pay(disk, 733_400)
    pay(disk, 266_600)
    assertEquals("BAD_REASON", code(cancel(disk, reason = "نه")))
    assertTrue(cancel(disk) is AppendOnce.Outcome.Wrote<*>)
    val d = disk.ledger
    val back = d.transactions.single { Layaways.isRefund(it) }
    assertEquals("L1-r", back.id)
    assertEquals("give", back.type)
    //  ۵٬۵۰۰ پیش‌پرداخت + ۷٬۳۳۴ + ۲٬۶۶۶ = ۱۵٬۵۰۰
    assertEquals(15_500.0, back.amount, 0.0)
    assertEquals(0.0, ShopStore.debt(d, "d1"), 0.0)
    assertEquals(0.0, cash(d).expected, 0.0)
    assertEquals(15_500.0, cash(d).returns, 0.0)
    val v = view(disk)
    assertEquals(Layaways.Status.CANCELLED, v.status)
    assertEquals(0L, v.heldMinor)
    assertTrue(d.sales.isEmpty() && d.stockMovements.isEmpty())
    //  لغو پایانی است: نه قسط، نه تحویل، نه لغوِ دوم
    assertEquals(AppendOnce.Outcome.Already, cancel(disk))
    assertEquals("NOT_ALLOWED", code(pay(disk, 100_000)))
    assertEquals(1, d.transactions.count { Layaways.isRefund(it) })
  }

  @Test fun `لغوِ فاکتورِ تحویل سند را دوباره باز می‌کند و لغوِ سند همان پرداختی را پس می‌دهد`() {
    val disk = Disk(base)
    create(disk, draft(deposit = 0, count = 1))
    pay(disk, 2_750_000)
    deliver(disk)
    disk.ledger = (SalesEngine.cancel(disk.ledger, "L1-d1", today, t0 + 5500, ::newId, reason = "اشتباه", by = "رحیم") as SalesEngine.Result.Ok).data
    val v = view(disk)
    assertEquals(Layaways.Status.OPEN, v.status)
    assertTrue(v.paidUp)
    assertEquals("L1-d2", Layaways.deliverKey(v))
    assertTrue(cancel(disk) is AppendOnce.Outcome.Wrote<*>)
    assertEquals(27_500.0, disk.ledger.transactions.single { Layaways.isRefund(it) }.amount, 0.0)
    assertEquals(0.0, ShopStore.debt(disk.ledger, "d1"), 0.0)
    assertEquals(0.0, cash(disk.ledger).expected, 0.0)
  }

  @Test fun `دیر فقط پس از روزِ سررسید و فهرست دیرها را اول می‌آورد`() {
    val disk = Disk(base)
    create(disk)
    assertFalse(view(disk, "2026-12-02").late)
    val late = view(disk, "2026-12-03")
    assertTrue(late.late)
    assertEquals(733_400L, late.progress.lateMinor)
    assertEquals(Layaway.State.LATE, late.progress.lines.first().state)
    pay(disk, 733_400)
    assertFalse(view(disk, "2026-12-03").late)
    assertTrue(view(disk, "2027-01-02").late)
    //  سندِ دوم بی دیر؛ فهرست دیر را اول می‌گذارد و پروندهٔ مشتری همان‌ها را
    create(disk, draft(first = "2027-06-01"), key = "L2", at = t0 + 10)
    assertEquals(listOf("L1", "L2"), Layaways.all(disk.ledger, "2027-01-02").map { it.id })
    assertEquals(2, Layaways.ofCustomer(disk.ledger, "d1", today).size)
  }

  @Test fun `دخل و رویدادهای صندوق رسیدهای امانی را نقد و بازپرداخت را پولِ پس می‌شمارند`() {
    val open = AuditEntry(id = "open1", type = CashShift.OPEN, date = today, createdAt = t0 - 10, amount = 0.0)
    val disk = Disk(base.copy(auditLog = listOf(open)))
    create(disk)
    pay(disk, 733_400)
    cancel(disk)
    val ev = PayFlow.ledgerEvents(disk.ledger, open, t0 + 100_000)
    val receipts = ev.filter { it.type == "receipt" }
    assertEquals(listOf("rcv:L1-p0", "rcv:L1-p1"), receipts.map { it.id })
    assertEquals(listOf(550_000L, 733_400L), receipts.map { it.amount })
    val refund = ev.single { it.type == "refund" }
    assertEquals("lyr:L1-r", refund.id)
    assertEquals(1_283_400L, refund.rows!!.single().amount)
    assertEquals(PayFlow.CASH, refund.rows!!.single().method)
    val t = CashShift.totals(disk.ledger, t0 - 10, t0 + 100_000, 0.0, "open1")
    assertEquals(12_834.0, t.receipts, 0.0)
    assertEquals(12_834.0, t.returns, 0.0)
    assertEquals(0.0, t.expected, 0.0)
  }

  @Test fun `مشتریِ ادغام‌شده دنبال می‌شود و سندِ ناسازگار نه قسط می‌گیرد نه تحویل`() {
    val merged = base.copy(debtors = listOf(Debtor(id = "d1", name = "احمد", mergedInto = "d9"), Debtor(id = "d9", name = "احمد ک")))
    val disk = Disk(merged)
    create(disk)
    assertEquals("d9", disk.ledger.transactions.single().debtorId)
    assertEquals("d9", view(disk).debtorId)
    //  جدولی که با جمع نمی‌خواند (دستِ دیگری) ⇒ بازبینی
    val row = disk.ledger.auditLog.single()
    val bent = row.copy(layaway = row.layaway!!.copy(schedule = row.layaway.schedule!!.dropLast(1)))
    disk.ledger = disk.ledger.copy(auditLog = listOf(bent))
    assertTrue(view(disk).review)
    assertEquals("REVIEW", code(pay(disk, 100_000)))
    assertNull(Layaways.find(disk.ledger, "nope", today))
    assertNotNull(Layaways.find(disk.ledger, "L1", today))
  }
}
