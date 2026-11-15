package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «خریدها» (e-rows-63b) — نمای هر خرید از همان دفتر: مانده و حالِ پرداخت، رسیدن، اقلام با بارکد، سری و انقضا؛ پرداختِ نام‌دارِ
 *  یک فاکتور ([PurchaseBook.payNote])، دریافتِ یک‌بارهٔ سفارش، جست‌وجو با بارکد و ثبتِ تکرارناپذیر.
 */
class PurchaseViewsTest {
  private var seq = 0
  private fun newId() = "x${seq++}"
  private val today = "2026-11-15"

  private val d0 = ShopData(
    products = listOf(
      Product(id = "flour", name = "آرد", unit = "کیلو", purchasePrice = 40.0, barcodes = listOf("6261111111111")),
      Product(id = "oil", name = "روغن", unit = "بوتل", purchasePrice = 300.0, barcodes = listOf("6262222222222"),
        packs = listOf(ProductPack("کارتن", 12_000, listOf("16262222222222")))),
      Product(id = "tea", name = "چای", unit = "بسته", purchasePrice = 150.0, sku = "TEA-01"),
      Product(id = "rice", name = "برنج", unit = "کیلو", purchasePrice = 90.0),
      Product(id = "salt", name = "نمک", unit = "بسته", purchasePrice = 20.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "پخش البرز"), Supplier(id = "s2", name = "عمده‌فروشی پامیر")),
  )

  private fun record(d: ShopData, sid: String, lines: List<PurchaseBook.Line>, paid: Double, date: String, due: String? = null, key: String? = null) =
    PurchaseBook.record(d, sid, lines, paid, date, "", today, seq.toLong(), ::newId, dueDate = due, key = key)

  private fun ok(r: PurchaseBook.Result) = (r as PurchaseBook.Result.Ok).data

  @Test fun `خریدِ پنج‌قلمیِ نیمه‌پرداخت — اقلام، بارکد، سری و انقضا و ماندهٔ درست، و موجودی همان مقدار`() {
    val d = ok(
      record(
        d0, "s1",
        listOf(
          PurchaseBook.Line("flour", 50.0, 40.0, expiryDate = "2027-03-01"), PurchaseBook.Line("oil", 12.0, 310.0),
          PurchaseBook.Line("tea", 4.0, 150.0), PurchaseBook.Line("rice", 20.0, 90.0), PurchaseBook.Line("salt", 10.0, 20.0),
        ),
        paid = 5000.0, date = "2026-11-10",
      ),
    )
    val v = PurchaseViews.all(d, today).single()
    //  جمع ۲۰۰۰ + ۳۷۲۰ + ۶۰۰ + ۱۸۰۰ + ۲۰۰ = ۸۳۲۰
    assertEquals(8320.0, v.total, 0.0)
    assertEquals(5000.0, v.paid, 0.0)
    assertEquals(3320.0, v.remaining, 0.0)
    assertEquals(PurchaseViews.Pay.PARTIAL, v.pay)
    assertEquals(PurchaseViews.Receipt.RECEIVED, v.receipt)
    assertEquals("پخش البرز", v.supplierName)
    assertEquals(listOf("آرد", "روغن", "چای", "برنج", "نمک"), v.names)
    val flour = v.lines.first { it.productId == "flour" }
    assertEquals("6261111111111", flour.code)
    assertEquals("2027-03-01", flour.expiry)
    assertEquals(50.0, flour.qty, 0.0)
    assertEquals("کیلو", flour.unit)
    assertEquals(40.0, flour.unitCost, 0.0)
    assertEquals(2000.0, flour.total, 0.0)
    assertEquals(flour.ordered, flour.received, 0.0)
    //  بی بارکد ⇒ کدِ کالا
    assertEquals("TEA-01", v.lines.first { it.productId == "tea" }.code)
    assertNull(v.lines.first { it.productId == "rice" }.code)
    //  موجودی همان مقدارِ خرید، بهای خرید همان فیِ تازه
    assertEquals(50.0, ShopStore.stock(d, "flour"), 0.0)
    assertEquals(310.0, d.products.first { it.id == "oil" }.purchasePrice, 0.0)
  }

  @Test fun `نسیه با سررسید، سپس سررسید گذشته — و همان در یادآورِ سررسید`() {
    val d = ok(record(d0, "s1", listOf(PurchaseBook.Line("flour", 10.0, 40.0)), 0.0, "2026-11-01", due = "2026-11-20"))
    val credit = PurchaseViews.all(d, today).single()
    assertEquals(PurchaseViews.Pay.CREDIT, credit.pay)
    assertEquals("2026-11-20", credit.dueDate)
    val late = PurchaseViews.all(d, "2026-11-21").single()
    assertEquals(PurchaseViews.Pay.OVERDUE, late.pay)
    assertEquals(400.0, late.remaining, 0.0)
    assertEquals(1, PurchaseBook.overdue(d, "2026-11-21").size)
    val paid = ok(record(d0, "s1", listOf(PurchaseBook.Line("flour", 10.0, 40.0)), 400.0, "2026-11-01"))
    assertEquals(PurchaseViews.Pay.PAID, PurchaseViews.all(paid, today).single().pay)
  }

  @Test fun `پرداختِ یک خرید همان خرید را صاف می‌کند و بدهیِ تأمین‌کننده را کم — بی نام همان FIFO`() {
    var d = ok(record(d0, "s1", listOf(PurchaseBook.Line("flour", 10.0, 40.0)), 0.0, "2026-11-01"))   // #1 = ۴۰۰
    d = ok(record(d, "s1", listOf(PurchaseBook.Line("tea", 2.0, 150.0)), 0.0, "2026-11-05"))           // #2 = ۳۰۰
    assertEquals(700.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    val second = PurchaseViews.all(d, today).first { it.no == 2 }
    //  «پرداخت» ِ جزئیاتِ #۲ با یادداشتی که آن را نام می‌برد
    val paid = (LedgerEngine.paySupplier(d, "s1", second.remaining, today, PurchaseBook.payNote(2), today, 9, ::newId, key = "k-pay") as LedgerEngine.Result.Ok).data
    assertEquals(400.0, ShopStore.supplierDebt(paid, "s1"), 0.0)
    val views = PurchaseViews.all(paid, today).associateBy { it.no }
    assertEquals(PurchaseViews.Pay.PAID, views.getValue(2).pay)
    assertEquals(400.0, views.getValue(1).remaining, 0.0)
    //  پرداختِ بی نام ⇒ کهنه‌ترین (FIFO، همان پیشین)
    val fifo = (LedgerEngine.paySupplier(d, "s1", 300.0, today, "حواله", today, 9, ::newId) as LedgerEngine.Result.Ok).data
    val f = PurchaseViews.all(fifo, today).associateBy { it.no }
    assertEquals(100.0, f.getValue(1).remaining, 0.0)
    assertEquals(300.0, f.getValue(2).remaining, 0.0)
    //  بیش از ماندهٔ فاکتورِ نام‌برده ⇒ بیشِ آن به FIFO؛ جمعِ مانده‌ها همیشه همان بدهی
    val over = (LedgerEngine.paySupplier(d, "s1", 500.0, today, PurchaseBook.payNote(2, "نقد"), today, 9, ::newId) as LedgerEngine.Result.Ok).data
    val o = PurchaseBook.open(over)
    assertEquals(0.0, o.first { it.invoice.no == 2 }.remaining, 0.0)
    assertEquals(200.0, o.first { it.invoice.no == 1 }.remaining, 0.0)
    assertEquals(ShopStore.supplierDebt(over, "s1"), o.sumOf { it.remaining }, 1e-9)
    assertEquals(2, PurchaseBook.paidInvoiceNo(PurchaseBook.payNote(2, "نقد")))
    assertNull(PurchaseBook.paidInvoiceNo("حواله #2"))
  }

  @Test fun `ثبتِ دوباره با همان کلید هیچ خرید، موجودی یا بدهیِ دومی نمی‌سازد`() {
    val lines = listOf(PurchaseBook.Line("oil", 6.0, 300.0), PurchaseBook.Line("salt", 5.0, 20.0))
    val d = ok(record(d0, "s1", lines, 0.0, "2026-11-10", key = "form-1"))
    val again = record(d, "s1", lines, 0.0, "2026-11-10", key = "form-1")
    assertTrue(again is PurchaseBook.Result.Failed)
    assertEquals(PurchaseBook.ALREADY_RECORDED, (again as PurchaseBook.Result.Failed).message)
    assertEquals(6.0, ShopStore.stock(d, "oil"), 0.0)
    assertEquals(1900.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    assertEquals(1, PurchaseViews.all(d, today).size)
  }

  /** سفارشِ ده‌تاییِ آرد و ده‌تاییِ روغن؛ رسیدِ اول ۶ آرد (روغن هنوز نه) */
  private val order = PurchaseOrderDto(
    id = "po-1", no = 41, supplierId = "s2", supplierName = "عمده‌فروشی پامیر", state = "sent", status = "partial",
    lines = listOf(PoLineDto("l1", "flour", "آرد", "کیلو", 10_000, 4_000), PoLineDto("l2", "oil", "روغن", "بوتل", 10_000, 30_000)),
    progress = listOf(PoProgressDto("l1", "flour", 10_000, 6_000, 0, 6_000, 4_000), PoProgressDto("l2", "oil", 10_000, 0, 0, 0, 10_000)),
    totals = PoTotalsDto(orderedMinor = 340_000, acceptedMinor = 24_000, remainingMinor = 316_000),
    sentAt = 1_763_000_000_000,
    receipts = listOf(
      PoReceiptDto(
        id = "01JS00000000000000000PV01", poId = "po-1",
        lines = listOf(PoReceiptLineDto("l1", 6_000, 0, 4_000)),
        posted = listOf(PoPostedDto("l1", 10_000, 6_000, 0, 6_000, 4_000, 0, 4_000, 24_000)),
        totalMinor = 24_000,
      ),
    ),
  )

  @Test fun `سفارشِ نیمه‌رسیده — رسیدنِ هر ردیف، یک خرید نه دو، و دریافتِ دوباره موجودی را دوباره بالا نمی‌برد`() {
    val r = PurchaseOrderBook.post(d0, order, order.receipts.single(), today, 5) as PurchaseOrderBook.Posting.Ok
    val d = r.data
    assertEquals(6.0, ShopStore.stock(d, "flour"), 0.0)
    assertEquals(240.0, ShopStore.supplierDebt(d, "s2"), 0.0)
    //  دوباره (دو لمس، دستگاهِ دیگر) ⇒ هیچ
    assertEquals(PurchaseOrderBook.Posting.Already, PurchaseOrderBook.post(d, order, order.receipts.single(), today, 6))
    //  بی سفارش‌های سرور (خاموش/بی اینترنت): فاکتورِ رسید «رسیده» و پیوندش به سفارش
    val offline = PurchaseViews.all(d, today).single()
    assertEquals(PurchaseViews.Receipt.RECEIVED, offline.receipt)
    assertEquals(41, offline.orderNo)
    //  با سفارشِ باز: یک ردیفِ «نیمه‌رسیده»، فاکتورِ رسیدش در همان تا شده
    val v = PurchaseViews.all(d, today, listOf(order)).single()
    assertEquals(PurchaseViews.Kind.ORDER, v.kind)
    assertEquals(PurchaseViews.Receipt.PARTIAL, v.receipt)
    assertEquals(PurchaseViews.orderKey("po-1"), v.key)
    assertEquals(3400.0, v.total, 0.0)
    assertEquals(240.0, v.billed, 0.0)
    assertEquals(240.0, v.remaining, 0.0)
    assertEquals(PurchaseViews.Pay.CREDIT, v.pay)
    assertEquals(listOf(6.0 to 10.0, 0.0 to 10.0), v.lines.map { it.received to it.ordered })
    assertEquals("6261111111111", v.lines.first().code)
    //  فاکتورِ تاشده با کلیدِ خودش هم باز می‌شود
    val inv = PurchaseBook.invoices(d).single()
    assertEquals(PurchaseViews.Kind.BILL, PurchaseViews.find(d, inv.key, today, listOf(order))?.kind)
    //  سفارشِ هنوز نرسیده: بی صورت‌حساب ⇒ حالِ پرداخت NONE
    val sent = order.copy(id = "po-2", no = 42, status = "sent", progress = emptyList(), totals = PoTotalsDto(340_000, 0, 340_000))
    val pending = PurchaseViews.all(d0, today, listOf(sent)).single()
    assertEquals(PurchaseViews.Receipt.PENDING, pending.receipt)
    assertEquals(PurchaseViews.Pay.NONE, pending.pay)
    //  پیش‌نویس و لغوشده خرید نیستند
    assertTrue(PurchaseViews.all(d0, today, listOf(sent.copy(status = "draft"), sent.copy(id = "po-3", status = "cancelled"))).isEmpty())
  }

  @Test fun `جست‌وجو با بارکد، بارکدِ بسته، کد، نام، تأمین‌کننده و شماره خریدِ دارای همان کالا را می‌یابد`() {
    var d = ok(record(d0, "s1", listOf(PurchaseBook.Line("flour", 1.0, 40.0), PurchaseBook.Line("tea", 1.0, 150.0)), 0.0, "2026-11-01"))
    d = ok(record(d, "s2", listOf(PurchaseBook.Line("oil", 1.0, 300.0)), 300.0, "2026-11-02"))
    val all = PurchaseViews.all(d, today)
    fun hit(q: String) = PurchaseViews.search(d, all, q).map { it.no }.sorted()
    assertEquals(listOf(2), hit("6262222222222"))
    assertEquals(listOf(2), hit("16262222222222"))
    assertEquals(listOf(1), hit("۶۲۶۱۱۱۱۱۱۱۱۱۱"))
    assertEquals(listOf(1), hit("TEA-01"))
    assertEquals(listOf(1), hit("آرد"))
    assertEquals(listOf(2), hit("پامیر"))
    assertEquals(listOf(1), hit("#1"))
    assertEquals(listOf(1, 2), hit(""))
    assertTrue(hit("6269999999999").isEmpty())
  }

  @Test fun `پالایه‌ها — تأمین‌کننده، پرداخت‌نشده، سررسیدگذشته، رسیدن و دوره`() {
    var d = ok(record(d0, "s1", listOf(PurchaseBook.Line("flour", 1.0, 40.0)), 0.0, "2026-10-01", due = "2026-10-15"))
    d = ok(record(d, "s2", listOf(PurchaseBook.Line("oil", 1.0, 300.0)), 300.0, today))
    val all = PurchaseViews.all(d, today)
    fun of(f: PurchaseViews.Filter, greg: Boolean = true) = all.filter { PurchaseViews.matches(it, f, greg, today) }.map { it.no }
    assertEquals(listOf(1), of(PurchaseViews.Filter(supplierId = "s1")))
    assertEquals(listOf(1), of(PurchaseViews.Filter(pay = PurchaseViews.PayFilter.UNPAID)))
    assertEquals(listOf(1), of(PurchaseViews.Filter(pay = PurchaseViews.PayFilter.OVERDUE)))
    assertEquals(listOf(2), of(PurchaseViews.Filter(pay = PurchaseViews.PayFilter.PAID)))
    assertTrue(of(PurchaseViews.Filter(receipt = PurchaseViews.ReceiptFilter.NOT_RECEIVED)).isEmpty())
    assertEquals(listOf(2), of(PurchaseViews.Filter(period = PurchasePeriods.day(today))))
    assertEquals(listOf(2), of(PurchaseViews.Filter(period = PurchasePeriods.month(today, gregorian = true))))
    assertEquals(listOf(1), of(PurchaseViews.Filter(period = PurchasePeriods.month("2026-10-01", gregorian = true))))
    //  خورشیدی: ۹ میزان و ۲۴ عقربِ ۱۴۰۵ دو ماهِ جدا
    assertEquals(listOf(2), of(PurchaseViews.Filter(period = PurchasePeriods.month(today, gregorian = false)), greg = false))
    val keys = PurchasePeriods.keys(all.map { it.date }, today, gregorian = false)
    assertEquals(PurchasePeriods.day(today), keys.first())
    assertEquals(PurchasePeriods.year(today, gregorian = false), keys.last())
    assertTrue(PurchasePeriods.month("2026-10-01", gregorian = false) in keys)
    assertEquals(PurchasePeriods.Kind.THIS_MONTH, PurchasePeriods.kind(keys[1], today, false))
  }

  @Test fun `برگشت به تأمین‌کننده ردیفِ خودش با مبلغِ منفی است و از جمعِ خرید بیرون`() {
    val d = ok(record(d0, "s1", listOf(PurchaseBook.Line("oil", 10.0, 300.0)), 0.0, "2026-11-01"))
    val back = ok(PurchaseBook.supplierReturn(d, "s1", "oil", 2.0, 300.0, "شکسته", "2026-11-03", today, 9, ::newId))
    val views = PurchaseViews.all(back, today)
    val ret = views.first { it.kind == PurchaseViews.Kind.RETURN }
    assertEquals(-600.0, ret.total, 0.0)
    assertEquals(PurchaseViews.Pay.NONE, ret.pay)
    val t = PurchaseViews.totals(views)
    assertEquals(1, t.count)
    assertEquals(3000.0, t.bought, 0.0)
    assertEquals(2400.0, t.remaining, 0.0)
  }

  @Test fun `یادداشتِ کاربر بی تکهٔ سیستمی`() {
    assertEquals("بارِ شب", PurchaseViews.userNote("فاکتورِ خرید #3 — بارِ شب"))
    assertEquals("", PurchaseViews.userNote("فاکتورِ خرید #3"))
    assertEquals("", PurchaseViews.userNote(PurchaseOrderBook.postedNote(9)))
    assertEquals("نقد", PurchaseViews.userNote("نقد"))
  }

  /** بودجهٔ نخستین قاب (۱۰۰ms): ساختنِ همهٔ نماها برای دفترِ بزرگ (۲٬۰۰۰ فاکتور × ۳ قلم، ۱٬۰۰۰ پرداخت) کم‌هزینه است */
  @Test fun `دفترِ بزرگ — نمای همهٔ خریدها زیرِ بودجهٔ نخستین قاب`() {
    val products = (0 until 500).map { Product(id = "p$it", name = "کالای $it", unit = "عدد", barcodes = listOf("626${it.toString().padStart(10, '0')}")) }
    val purchases = (0 until 2000).flatMap { inv ->
      (0 until 3).map { k ->
        val day = ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(today, -(inv % 400))!!
        Purchase(id = "u$inv-$k", productId = "p${(inv * 3 + k) % 500}", supplierId = if (inv % 2 == 0) "s1" else "s2", quantity = 2.0, unit = "عدد",
          purchasePrice = 50.0, totalAmount = 100.0, paidAmount = if (inv % 3 == 0) 100.0 else 0.0, debt = if (inv % 3 == 0) 0.0 else 100.0,
          date = day, createdAt = inv * 10L + k, invoiceNo = inv + 1, dueDate = if (inv % 5 == 0) day else null)
      }
    }
    val pays = (0 until 1000).map { SupplierPayment("y$it", if (it % 2 == 0) "s1" else "s2", 50.0, today, if (it % 7 == 0) PurchaseBook.payNote(it + 1) else "", it.toLong()) }
    val d = d0.copy(products = products, purchases = purchases, supplierPayments = pays)
    repeat(3) { PurchaseViews.all(d, today) }
    val t0 = System.nanoTime()
    val all = PurchaseViews.all(d, today)
    val found = PurchaseViews.search(d, all, "6260000000007")
    val ms = (System.nanoTime() - t0) / 1_000_000
    println("PurchaseViews.all + search: ${all.size} خرید در $ms ms")
    assertEquals(2000, all.size)
    assertTrue(found.isNotEmpty())
    assertTrue("$ms ms", ms < 400)
  }
}
