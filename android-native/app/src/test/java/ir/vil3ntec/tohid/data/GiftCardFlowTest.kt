package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.pay.GiftCard
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.StoredPay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-24 گام ۲ — کارتِ هدیه و اعتبارِ فروشگاه در صندوق (`PayFlow` با تنظیم ⇐ `SalesEngine.record`، `ReturnFlow`،
 *  `SalesEngine.cancel`، دخل). پول به یک‌صدمِ افغانی؛ ردیف‌های خرج هم‌اتم با فاکتور؛ شناسهٔ خرج از شناسهٔ فاکتور.
 *  تنظیمِ خاموش ⇒ همان امروز: `GiftCardOffTest`.
 */
class GiftCardFlowTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-02"
  private val code = GiftCard.make { 5 }
  private val other = GiftCard.make { 9 }

  /** دو کالا، یک مشتری، صندوقِ باز و کارتِ ۲٬۰۰۰ افغانی */
  private val base: ShopData = run {
    val d = ShopData(
      products = listOf(
        Product(id = "p1", name = "روغن", salePrice = 2450.0, purchasePrice = 2000.0, unit = "عدد"),
        Product(id = "p2", name = "برنج", salePrice = 800.0, purchasePrice = 600.0, unit = "عدد"),
      ),
      warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 20.0), WarehouseEntry(id = "w2", productId = "p2", units = 20.0)),
      debtors = listOf(Debtor(id = "d1", name = "نوشا")),
      auditLog = listOf(AuditEntry(id = "open1", type = CashShift.OPEN, date = today, notes = "باز", createdAt = 50, amount = 500.0)),
    )
    (GiftCards.issue(d, "g1", code, 200_000, today, 60, "رضا") as GiftCards.Issued.Ok).data
  }
  private val both = listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0))

  private fun settle(total: Double, cash: String, rows: List<PayFlow.DraftRow>, customer: String? = null): Settlement =
    (PayFlow.checkout(PayFlow.Draft(total, cash, rows, customer, stored = true)) as PayFlow.Outcome.Ok).value

  private fun gift(amount: String, c: String = code) = PayFlow.DraftRow("gift_card", c, amount)
  private fun credit(amount: String) = PayFlow.DraftRow("store_credit", "", amount)

  private fun record(d: ShopData, st: Settlement, saleId: String, cart: List<SalesEngine.CartLine> = both, debtor: String? = null) =
    SalesEngine.record(d, cart, SalesEngine.Checkout(seller = "رضا", settlement = st, debtorId = debtor), today, 100, newId, saleId = saleId)

  private fun ok(r: SalesEngine.Result): ShopData = (r as SalesEngine.Result.Ok).data
  private fun failed(r: SalesEngine.Result): String = (r as SalesEngine.Result.Failed).message
  private fun card(d: ShopData) = GiftCards.view(d).card(code)!!

  @Test fun `فاکتور و خرجِ کارت هم‌اتم‌اند — هر دو در همان دفتر یا هیچ‌کدام`() {
    val st = settle(3250.0, "1250", listOf(gift("2000")))
    val d = ok(record(base, st, "sA"))
    //  فاکتور و ردیفِ خرج در همان یک دفتر
    val s = d.sales.single { it.id == "sA" }
    val redeem = d.auditLog.single { it.type == GiftCards.REDEEM }
    assertEquals("sA-g0", redeem.id)
    assertEquals("sA", redeem.refId)
    assertEquals(code, redeem.payRef)
    assertEquals(0L, card(d).balanceMinor)
    //  ردیفِ کارت «تأییدشده با دفتر» است، نه ثبتِ دستی
    val row = s.payments!!.single { it.kind == "gift_card" }
    assertEquals("confirmed" to StoredPay.LEDGER, row.status to row.verification)
    assertEquals(200_000L, row.amountMinor)
    //  فاکتوری که به هر دلیلِ دیگری ثبت نشود خرجی هم نمی‌سازد (کالای ناکافی)
    val many = listOf(SalesEngine.CartLine("p2", 50.0))
    val big = settle(40_000.0, "38000", listOf(gift("2000")))
    assertTrue(record(base, big, "sX", many) is SalesEngine.Result.Failed)
    //  ثبتِ ردشده دفترِ ورودی را دست نمی‌زند (`record` خالص است)
    assertEquals(200_000L, card(base).balanceMinor)
    assertTrue(base.auditLog.none { it.type == GiftCards.REDEEM })
  }

  @Test fun `تلاشِ دوباره با همان کلیدِ فاکتور خرجِ دوم نمی‌سازد`() {
    val st = settle(3250.0, "2250", listOf(gift("1000")))
    val d = ok(record(base, st, "sA"))
    assertEquals(SalesEngine.ALREADY_POSTED, failed(record(d, st, "sA")))
    assertEquals(1, d.auditLog.count { it.type == GiftCards.REDEEM })
    assertEquals(100_000L, card(d).balanceMinor)
    //  همان رویداد از دستگاهی که دفترش آن را دارد: duplicate، هیچ ردیفِ تازه
    val again = GiftCards.forSale(d, StoredPay.confirm(st), "sA", null, "1", today, 999, "رضا") as GiftCards.Out.Ok
    assertTrue(again.rows.empty)
  }

  @Test fun `خرجِ بیش از ماندهٔ کارت رد می‌شود و هیچ چیز نوشته نمی‌شود`() {
    val over = settle(3250.0, "1000", listOf(gift("2250")))
    assertEquals(StrGiftCard.errBalance, failed(record(base, over, "sA")))
    //  دو ردیفِ همان کارت روی هم سنجیده می‌شوند
    val split = settle(3250.0, "1000", listOf(gift("1200"), gift("1050")))
    assertEquals(StrGiftCard.errBalance, failed(record(base, split, "sA")))
    //  کارتِ ناشناخته و کدِ نادرست
    assertEquals(StrGiftCard.errUnknown, failed(record(base, settle(3250.0, "2250", listOf(gift("1000", other))), "sA")))
    val badCode = PayFlow.preview(PayFlow.Draft(3250.0, "2250", listOf(gift("1000", "ABCD")), stored = true))
    assertEquals(GiftCard.Code.BAD_CODE, (badCode as PayFlow.Outcome.Bad).problem.code)
    //  پیش‌سنجشِ پنل همان پاسخ را می‌دهد
    assertEquals(GiftCard.Code.INSUFFICIENT_BALANCE, GiftCards.problem(base, over, null))
    assertNull(GiftCards.problem(base, settle(3250.0, "1250", listOf(gift("2000"))), null))
  }

  @Test fun `برگشت به کارت فقط تا همان که از کارت پرداخت شد`() {
    //  ۱٬۰۰۰ از کارت، ۲٬۲۵۰ نقد؛ کارت ردیفِ آخر است ⇒ برگشت اول از آن
    var d = ok(record(base, settle(3250.0, "2250", listOf(gift("1000"))), "sA"))
    val rice = d.saleItems.first { it.saleId == "sA" && it.productId == "p2" }.id
    val oil = d.saleItems.first { it.saleId == "sA" && it.productId == "p1" }.id
    //  حالتِ «نقد»: سهمِ کارت باز هم به کارت می‌رود، نه نقد (کارتِ هدیه با برگشت نقد نمی‌شود)
    val r1 = (ReturnFlow.post(d, ReturnFlow.Request(docId = "rA", saleId = "sA", picks = mapOf(rice to ReturnFlow.Pick(1.0)), reason = "دیگر", mode = ReturnFlow.CASH), today, 200, newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(mapOf("gift_card" to 800.0), r1.split.methods)
    assertEquals(0.0, r1.split.cash, 0.0)
    d = r1.data
    assertEquals(180_000L, card(d).balanceMinor)
    assertEquals(0.0, ReturnFlow.drawerAmount(d.saleReturns.single { it.docId == "rA" }), 0.0)
    //  برگشتِ دوم ۲٬۴۵۰: فقط ۲۰۰ ِ باقی به کارت، بقیه نقد
    val r2 = (ReturnFlow.post(d, ReturnFlow.Request(docId = "rB", saleId = "sA", picks = mapOf(oil to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, 300, newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(200.0, r2.split.methods["gift_card"]!!, 0.0)
    assertEquals(2250.0, r2.split.cash, 0.0)
    d = r2.data
    assertEquals(100_000L, card(d).refundedMinor)
    assertEquals(200_000L, card(d).balanceMinor)
    //  مستقیم: بیش از خرجِ کارت رد
    val sale = d.sales.single { it.id == "sA" }
    assertEquals(GiftCards.NO_CARD_ROWS, (GiftCards.forReturn(d, sale, "rC", 1, 0, "x", today, 400, null) as GiftCards.Out.Bad).code)
    //  لغوِ فاکتورِ تا ته برگشته چیزی به کارت نمی‌افزاید
    val c = ok(SalesEngine.cancel(d, "sA", today, 500, newId, reason = "اشتباه"))
    assertEquals(200_000L, card(c).balanceMinor)
  }

  @Test fun `لغوِ فاکتور کارت و اعتبار را برمی‌گرداند و فروشِ کارت نقدِ دخل است`() {
    val d = ok(record(base, settle(3250.0, "2250", listOf(gift("1000"))), "sA"))
    val c = ok(SalesEngine.cancel(d, "sA", today, 500, newId, reason = "اشتباه", by = "رضا"))
    assertEquals(200_000L, card(c).balanceMinor)
    assertEquals("sA", c.auditLog.single { it.type == GiftCards.REFUND }.refId)
    //  فروشِ کارت: ۲٬۰۰۰ نقد در دخل، هم حسابِ ساده هم هستهٔ صندوق
    val t = CashShift.totals(base, 50, 700, 500.0, "open1")
    assertEquals(2000.0, t.receipts, 0.0)
    val open = base.auditLog.first { it.id == "open1" }
    val ev = PayFlow.ledgerEvents(base, open, 700).single()
    assertEquals("receipt" to 200_000L, ev.type to ev.amount)
    //  فروشِ با کارت نقدی به دخل نمی‌آورد جز سهمِ نقدش
    assertEquals(2250.0, CashShift.saleCash(d.sales.single { it.id == "sA" }), 0.0)
  }

  @Test fun `پرداخت با اعتبارِ فروشگاه فقط تا طلبِ مشتری`() {
    //  طلبِ ۵۰۰ افغانی: برگشتی که به اعتبار رفت
    val withCredit = base.copy(transactions = listOf(DebtTransaction(id = "t1", debtorId = "d1", type = "receive", amount = 500.0, date = today, createdAt = 10, kind = GiftCards.CREDIT)))
    assertEquals(50_000L, GiftCards.creditOf(withCredit, "d1"))
    val over = settle(800.0, "200", listOf(credit("600")), customer = "d1")
    assertEquals(StrGiftCard.errCredit, failed(record(withCredit, over, "sC", listOf(SalesEngine.CartLine("p2", 1.0)), "d1")))
    //  بی مشتری: پنل خودش رد می‌کند
    val noCustomer = PayFlow.preview(PayFlow.Draft(800.0, "300", listOf(credit("500")), null, stored = true))
    assertEquals("NO_CUSTOMER", (noCustomer as PayFlow.Outcome.Bad).problem.code)
    //  مشتریِ بی طلب
    assertEquals(StrGiftCard.errCredit, failed(record(base, settle(800.0, "300", listOf(credit("500")), customer = "d1"), "sC", listOf(SalesEngine.CartLine("p2", 1.0)), "d1")))
    //  تا ته طلب: برد با `kind` ِ اعتبار، ماندهٔ مشتری صفر، و هیچ نقدی از دخل
    val st = settle(800.0, "300", listOf(credit("500")), customer = "d1")
    val d = ok(record(withCredit, st, "sC", listOf(SalesEngine.CartLine("p2", 1.0)), "d1"))
    val give = d.transactions.single { it.refId == "sC" }
    assertEquals(Triple("give", GiftCards.CREDIT, 500.0), Triple(give.type, give.kind, give.amount))
    assertEquals(0L, GiftCards.balanceMinor(d, "d1"))
    assertEquals(0.0, d.sales.single { it.id == "sC" }.debtGiven, 0.0)
    assertFalse(CashShift.isCashReceipt(give))
    //  برگشتِ همان فاکتور: سهمِ اعتبار به اعتبارِ همان مشتری (رسیدِ غیرنقد)، بیش از ۵۰۰ نه
    val item = d.saleItems.first { it.saleId == "sC" }.id
    val back = (ReturnFlow.post(d, ReturnFlow.Request(docId = "rC", saleId = "sC", picks = mapOf(item to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, 300, newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(500.0, back.split.methods["store_credit"]!!, 0.0)
    val rcv = back.data.transactions.single { it.refId == "rC" && it.kind == GiftCards.CREDIT }
    assertEquals("receive" to 500.0, rcv.type to rcv.amount)
    assertEquals(-50_000L, GiftCards.balanceMinor(back.data, "d1"))
  }

  @Test fun `بی تنظیم پنل همان روش‌های امروز را دارد و کارت و اعتبار را نمی‌پذیرد`() {
    assertEquals(PayFlow.panelMethods(), PayFlow.panelMethods(false))
    assertTrue(PayFlow.panelMethods().none { it.kind in StoredPay.KINDS })
    assertTrue(PayFlow.panelMethods(true).map { it.id }.containsAll(listOf("gift_card", "store_credit")))
    val off = PayFlow.preview(PayFlow.Draft(3250.0, "2250", listOf(gift("1000"))))
    assertEquals("METHOD_DISABLED", (off as PayFlow.Outcome.Bad).problem.code)
    //  تسویهٔ بی کارت/اعتبار همان شیء و هیچ ردیفِ تازه
    val plain = (PayFlow.checkout(PayFlow.Draft(3250.0, "3250")) as PayFlow.Outcome.Ok).value
    assertSame(plain, StoredPay.confirm(plain))
    assertTrue((GiftCards.forSale(base, plain, "sZ", null, "1", today, 1, null) as GiftCards.Out.Ok).rows.empty)
  }
}
