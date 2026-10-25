package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** صندوق — نقدِ مورد انتظار هر پول را یک بار حساب می‌کند */
class CashShiftTest {

  private var seq = 0
  private fun newId(): String = "c${seq++}"

  @Test
  fun `باز، فروش نقدی و نسیه، رسید، مرجوعی، مصرف، بستن`() {
    var d = ShopData()
    d = (CashShift.open(d, 1000.0, "علی", "2026-10-12", 100, ::newId) as CashShift.Result.Ok).data
    assertTrue(CashShift.open(d, 5.0, "", "2026-10-12", 101, ::newId) is CashShift.Result.Failed)
    d = d.copy(
      sales = listOf(
        Sale(id = "s1", finalTotal = 500.0, createdAt = 110),                                  // نقدی
        Sale(id = "s2", finalTotal = 800.0, debtGiven = 600.0, paymentMethod = "credit", createdAt = 120), // ۲۰۰ پیش‌پرداخت
        Sale(id = "s3", finalTotal = 999.0, status = "cancelled", createdAt = 130),
        Sale(id = "old", finalTotal = 7777.0, createdAt = 50),                                  // پیش از باز شدن
      ),
      transactions = listOf(
        DebtTransaction("t1", "c", "receive", 300.0, createdAt = 140),
        DebtTransaction("t2", "c", "give", 600.0, createdAt = 120),
        DebtTransaction("t3", "c", "receive", 100.0, createdAt = 150),   // بخشِ بدهیِ مرجوعی
      ),
      saleReturns = listOf(SaleReturn(id = "r1", amount = 150.0, createdAt = 150)),  // ۵۰ نقد پس داده شد
      expenses = listOf(Expense(id = "e1", amount = 70.0, createdAt = 160)),
    )
    val t = CashShift.totalsOf(d, CashShift.current(d)!!, 200)
    //  ۱۰۰۰ + ۵۰۰ + ۲۰۰ + ۳۰۰ + ۱۰۰ − ۱۵۰ − ۷۰
    assertEquals(1880.0, t.expected, 1e-9)
    d = (CashShift.close(d, 1850.0, "", "علی", "2026-10-12", 210, ::newId) as CashShift.Result.Ok).data
    assertEquals(null, CashShift.current(d))
    assertTrue(d.auditLog.last().notes.contains("کسری 30"))
  }

  /** PAY (v3 A4): فاکتورِ با `payments` فقط نقدِ ماندهٔ پس از پولِ پس را به دخل می‌آورد */
  @Test
  fun `فاکتورِ ترکیبی - کارت و کیف پول و به حساب نقدِ دخل نیستند`() {
    fun sale(id: String, at: Long, draft: PayFlow.Draft): Sale {
      val f = PayFlow.saleFields(PayFlow.checkout(draft).valueOrNull()!!)
      return Sale(
        id = id, finalTotal = draft.total, paymentMethod = f.paymentMethod, paidAmount = f.paidAmount,
        remaining = f.remaining, debtGiven = f.debtGiven, createdAt = at,
        payments = f.payments, tenderedMinor = f.tenderedMinor, changeMinor = f.changeMinor,
      )
    }
    var d = (CashShift.open(ShopData(), 1000.0, "", "2026-10-19", 100, ::newId) as CashShift.Result.Ok).data
    d = d.copy(
      sales = listOf(
        sale("a", 110, PayFlow.Draft(850.0, "1000")),                                                         // ۸۵۰ (۱۵۰ پولِ پس)
        sale("b", 120, PayFlow.Draft(2450.0, "1000", listOf(PayFlow.DraftRow("hesabpay", "HP-1", "1450")))),  // ۱۰۰۰
        sale("c", 130, PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("afpay_card", "C-1", "1200")))),  // ۰
        sale("e", 140, PayFlow.Draft(2000.0, "500", customerId = "d1")),                                     // ۵۰۰
        Sale(id = "old", finalTotal = 300.0, createdAt = 150),                                                // بی payments: ۳۰۰
      ),
    )
    assertEquals(listOf("cash", "mixed", "card", "credit", "cash"), d.sales.map { it.paymentMethod })
    assertEquals(listOf(850.0, 1000.0, 0.0, 500.0, 300.0), d.sales.map { CashShift.saleCash(it) })
    val shift = CashShift.current(d)!!
    val expected = CashShift.totalsOf(d, shift, 200).expected
    assertEquals(1000.0 + 850 + 1000 + 500 + 300, expected, 1e-9)
    //  همان عددِ هستهٔ pay-core (صندوقِ پرچم‌دار)
    assertEquals(expected, PayFlow.expectedCash(d, shift.open, 200), 1e-9)
    //  فیلترِ «نقدی»ِ تاریخچه و برنامهٔ کهنه (`paymentMethod == "cash"`) فقط نقدِ خالص را می‌گیرد
    assertEquals(listOf("a", "old"), d.sales.filter { it.paymentMethod == "cash" }.map { it.id })
  }

  @Test
  fun `فروش و مرجوعیِ اینترنتیِ سرور (سریِ W، کانالِ online) پولِ دخل نیست`() {
    var d = ShopData()
    d = (CashShift.open(d, 1000.0, "", "2026-10-19", 100, ::newId) as CashShift.Result.Ok).data
    d = d.copy(
      sales = listOf(
        Sale(id = "s1", finalTotal = 500.0, createdAt = 110),                                       // نقدیِ صندوق
        //  پرداختِ آنلاین و نقد هنگامِ تحویل (دستِ پیک تا تسویه) — هیچ‌کدام در دخل نیست
        Sale(id = "w-o1", finalTotal = 900.0, paymentMethod = "online", paidAmount = 900.0,
          series = "W", channel = "online", createdAt = 120),
        Sale(id = "w-o2", finalTotal = 700.0, paymentMethod = "cod", paidAmount = 700.0,
          series = "W", channel = "online", createdAt = 130),
        //  فاکتورِ پیش از این اصلاح با `paymentMethod = cash` هم بیرون می‌ماند (سری W)
        Sale(id = "w-o3", finalTotal = 300.0, paymentMethod = "cash", paidAmount = 300.0, series = "W", createdAt = 131),
        //  فقط کانال (بی سری) هم کافی است
        Sale(id = "w-o4", finalTotal = 50.0, channel = "online", createdAt = 132),
      ),
      saleReturns = listOf(
        SaleReturn(id = "w-o1-1-r", saleId = "w-o1", saleItemId = "w-o1-1", amount = 900.0, createdAt = 140, channel = "online"),
        //  ردیفِ سرور پیش از نشانِ `channel`: شناسهٔ ثابتِ `w-…` بر فاکتورِ اینترنتی
        SaleReturn(id = "w-o2-1-r", saleId = "w-o2", saleItemId = "w-o2-1", amount = 700.0, createdAt = 141),
        //  مرجوعیِ دستیِ همین صندوق برای فاکتورِ W: نقد از دخل رفت ⇒ کم می‌شود
        SaleReturn(id = "01JDEVICERETURN0000000000", saleId = "w-o3", amount = 100.0, createdAt = 142),
        SaleReturn(id = "r-pos", saleId = "s1", amount = 20.0, createdAt = 143),
      ),
    )
    val t = CashShift.totalsOf(d, CashShift.current(d)!!, 200)
    assertEquals(500.0, t.sales, 1e-9)
    assertEquals(120.0, t.returns, 1e-9)
    //  ۱۰۰۰ + ۵۰۰ − ۱۰۰ − ۲۰
    assertEquals(1380.0, t.expected, 1e-9)
    assertTrue(CashShift.isOnline(d.sales[1]))
    assertTrue(!CashShift.isOnline(d.sales[0]))
  }

  /** هر دو قاعده با هم: فقط نقدِ فاکتورِ صندوق (PAY v3 A4) و هیچ پولی از فاکتورِ W (#321) */
  @Test
  fun `ردیفِ cash ِ «نقد هنگامِ تحویل» روی فاکتورِ W نقدِ دخل نیست؛ فاکتورِ ترکیبیِ صندوق فقط نقدش`() {
    val f = PayFlow.saleFields(PayFlow.checkout(PayFlow.Draft(2450.0, "1000", listOf(PayFlow.DraftRow("hesabpay", "HP-1", "1450")))).valueOrNull()!!)
    val pos = Sale(id = "s1", finalTotal = 2450.0, paymentMethod = f.paymentMethod, paidAmount = f.paidAmount, payments = f.payments, createdAt = 110)
    val cod = Sale(
      id = "w-o2", finalTotal = 700.0, paymentMethod = "cod", paidAmount = 700.0, series = "W", channel = "online", createdAt = 120,
      payments = listOf(SalePayment("cod", "cash", 70000, "pending", custody = "courier")),
    )
    //  حتی اگر نشانِ W/online گم شده باشد، ردیفِ امانتِ پیک (`custody`) نقدِ دخل نیست
    val stray = cod.copy(id = "x", series = null, channel = null)
    assertEquals(listOf(1000.0, 0.0, 0.0), listOf(pos, cod, stray).map { CashShift.saleCash(it) })
    val d = ShopData(sales = listOf(pos, cod, stray))
    val t = CashShift.totals(d, 100, 200, 1000.0)
    assertEquals(1000.0, t.sales, 1e-9)
    assertEquals(2000.0, t.expected, 1e-9)
    assertEquals(t.expected, PayFlow.expectedCash(d, AuditEntry(id = "sh", createdAt = 100, amount = 1000.0), 200), 1e-9)
  }

  /** یافتهٔ بازبینِ پول (#286): ورود/خروجِ نقد داده است — با پرچمِ pay v2 و بی آن یک عدد */
  @Test
  fun `اولِ صندوق ۱۰۰۰، فروشِ کارتیِ ۵۰۰، خروجِ نقدِ ۳۰۰ ⇒ ۷۰۰ با پرچم و بی پرچم`() {
    var d = (CashShift.open(ShopData(), 1000.0, "", "2026-10-19", 100, ::newId) as CashShift.Result.Ok).data
    val shift = CashShift.current(d)!!
    val f = PayFlow.saleFields(PayFlow.checkout(PayFlow.Draft(500.0, rows = listOf(PayFlow.DraftRow("afpay_card", "C1", "500")))).valueOrNull()!!)
    d = d.copy(sales = listOf(Sale(id = "k", finalTotal = 500.0, paymentMethod = f.paymentMethod, paidAmount = f.paidAmount, payments = f.payments, createdAt = 110)))
    val out = PayFlow.moveRow(d, shift.open, PayFlow.Move("cash_out", "300", "owner_withdrawal"), 120, ::newId).valueOrNull()!!
    val inn = PayFlow.moveRow(d, shift.open, PayFlow.Move("cash_in", "50", "deposit"), 121, ::newId).valueOrNull()!!
    d = d.copy(auditLog = d.auditLog + out)
    val t = CashShift.totalsOf(d, shift, 200)
    assertEquals(300.0, t.cashOut, 1e-9)
    assertEquals(700.0, t.expected, 1e-9)
    assertEquals(700.0, PayFlow.expectedCash(d, shift.open, 200), 1e-9)
    //  ورودِ نقد هم؛ و ردیفِ صندوقِ دیگری (refId دیگر) شمرده نمی‌شود
    d = d.copy(auditLog = d.auditLog + inn + out.copy(id = "other", refId = "another-shift"))
    assertEquals(750.0, CashShift.totalsOf(d, shift, 200).expected, 1e-9)
    assertEquals(750.0, PayFlow.expectedCash(d, shift.open, 200), 1e-9)
    //  بستن هم همان عدد را می‌نویسد
    val closed = (CashShift.close(d, 750.0, "", "", "2026-10-19", 210, ::newId) as CashShift.Result.Ok).data
    assertTrue(closed.auditLog.last().notes.contains("بی اختلاف"))
  }

  /** یافتهٔ بازبینِ پول (#286): رسیدی که لغوِ نسیه می‌سازد فقط بدهی را می‌بندد، پولی به دخل نمی‌آورد */
  @Test
  fun `لغوِ فروشِ نسیه نقدِ دخل را بالا نمی‌برد؛ رسیدِ دستی و رسیدِ مرجوعی مثلِ پیش`() {
    var d = (CashShift.open(ShopData(debtors = listOf(Debtor(id = "d1", name = "نوشا"))), 1000.0, "", "2026-10-19", 100, ::newId) as CashShift.Result.Ok).data
    val shift = CashShift.current(d)!!
    d = d.copy(
      sales = listOf(
        Sale(id = "c1", invoiceNumber = 7, finalTotal = 100.0, paymentMethod = "credit", debtorId = "d1", debtGiven = 100.0, remaining = 100.0, paidAmount = 0.0, createdAt = 110),
      ),
    )
    d = (SalesEngine.cancel(d, "c1", "2026-10-19", 120, ::newId, "اشتباه") as SalesEngine.Result.Ok).data
    val rcv = d.transactions.single()
    assertEquals("receive", rcv.type)
    assertEquals("c1", rcv.refId)
    assertEquals(CashShift.CANCEL_RECEIPT, rcv.kind)
    assertFalse(CashShift.isCashReceipt(rcv))
    assertEquals(1000.0, CashShift.totalsOf(d, shift, 200).expected, 1e-9)
    assertEquals(1000.0, PayFlow.expectedCash(d, shift.open, 200), 1e-9)
    //  رسیدِ لغوِ برنامهٔ قدیمی (بی نشان، همان متنِ ثابت) هم؛ رسیدِ دستی شمرده می‌شود
    val legacy = rcv.copy(id = "t-old", kind = null, refId = null)
    val manual = DebtTransaction("t-man", "d1", "receive", 40.0, notes = "قسطِ ماه", createdAt = 130)
    d = d.copy(transactions = d.transactions + legacy + manual)
    assertFalse(CashShift.isCashReceipt(legacy))
    assertTrue(CashShift.isCashReceipt(manual))
    assertEquals(1040.0, CashShift.totalsOf(d, shift, 200).expected, 1e-9)
    assertEquals(1040.0, PayFlow.expectedCash(d, shift.open, 200), 1e-9)
    //  بدهی همان‌طور بسته می‌شود (رسیدِ لغو در دفترِ قرض‌دار هست)
    assertEquals(100.0, d.sales.single().debtSettled, 1e-9)
    //  مرجوعیِ نسیه: رسیدش نشان دارد ولی شمرده می‌شود تا کمِ کلِ مرجوعی را خنثی کند
    d = d.copy(
      sales = d.sales + Sale(id = "c2", invoiceNumber = 8, total = 100.0, finalTotal = 100.0, paymentMethod = "credit", debtorId = "d1",
        debtGiven = 100.0, remaining = 100.0, paidAmount = 0.0, createdAt = 140),
      saleItems = d.saleItems + SaleItem(id = "i2", saleId = "c2", productId = "", quantity = 1.0, unitPrice = 100.0, totalPrice = 100.0),
    )
    d = (SalesEngine.recordReturn(d, "c2", mapOf("i2" to 1.0), "خراب", "2026-10-19", 150, ::newId) as SalesEngine.Result.Ok).data
    val ret = d.transactions.last()
    assertEquals(CashShift.RETURN_RECEIPT to "c2", ret.kind to ret.refId)
    assertTrue(CashShift.isCashReceipt(ret))
    //  −۱۰۰ مرجوعی + ۱۰۰ رسیدِ مرجوعی = خالصِ صفر، مثلِ پیش
    assertEquals(1040.0, CashShift.totalsOf(d, shift, 200).expected, 1e-9)
    assertEquals(1040.0, PayFlow.expectedCash(d, shift.open, 200), 1e-9)
    assertFalse(CashShift.isCashReceipt(manual.copy(id = "t-sc", kind = CashShift.STORE_CREDIT_RECEIPT)))
  }
}
