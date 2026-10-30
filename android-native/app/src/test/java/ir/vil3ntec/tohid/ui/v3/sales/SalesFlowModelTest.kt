package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalePromo
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.ui.components.pos.KeypadInput
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalBlock
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalModel
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutForm
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DiffKind
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryFilter
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryModel
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleState
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintKind
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintModel
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تسویه، تاریخچه، برگشت، صندوق، چاپ و تأیید — همان قاعده‌های پنجره‌های پیشین، پیش از موتور */
class SalesFlowModelTest {

  private val cart = listOf(CartLine("soap", 2.0), CartLine("rice", 1.5))

  /* ------------------------------ تسویه ------------------------------ */

  @Test
  fun `سبدِ با قرض دار نسیه و پرداختیِ صفر باز می شود، بی او نقدی`() {
    val credit = CheckoutModel.start("d1", ledger.debtors)
    assertEquals(SalesEngine.Payment.CREDIT, credit.payment)
    assertEquals("0", credit.paidText)
    assertEquals(SalesEngine.Payment.CASH, CheckoutModel.start(null, ledger.debtors).payment)
    //  قرض دارِ حذف شده ⇒ نقدی
    assertEquals(SalesEngine.Payment.CASH, CheckoutModel.start("gone", ledger.debtors).payment)
  }

  @Test
  fun `نقدی همیشه کلِ مبلغ را می پردازد، نسیه کمینهٔ نوشته و مبلغ`() {
    val cash = CheckoutForm(SalesEngine.Payment.CASH, null, paidText = "10")
    val n = CheckoutModel.numbers(ledger, cart, cash, CheckoutModel.checkout(cash, true, false, ""))
    assertEquals(SalesEngine.cartTotal(ledger, cart), n.finalTotal, 0.0)
    assertEquals(n.finalTotal, n.paid, 0.0)
    assertEquals(0.0, n.remaining, 0.0)

    val credit = CheckoutForm(SalesEngine.Payment.CREDIT, "d1", paidText = "50")
    val c = CheckoutModel.numbers(ledger, cart, credit, CheckoutModel.checkout(credit, true, false, ""))
    assertEquals(50.0, c.paid, 0.0)
    assertEquals(c.finalTotal - 50.0, c.remaining, 0.0)
    val over = credit.copy(paidText = "99999")
    assertEquals(c.finalTotal, CheckoutModel.numbers(ledger, cart, over, CheckoutModel.checkout(over, true, false, "")).paid, 0.0)
  }

  @Test
  fun `تخفیف فقط با اجازه، نادرست خطا و ثبت خاموش`() {
    val f = CheckoutForm(SalesEngine.Payment.CASH, null, discountText = "10")
    assertEquals(10.0, CheckoutModel.checkout(f, true, false, "").discountValue, 0.0)
    assertEquals(0.0, CheckoutModel.checkout(f, false, false, "").discountValue, 0.0)
    val bad = f.copy(discountText = "1o")
    assertTrue(CheckoutModel.discountError(bad, true) != null)
    assertNull(CheckoutModel.discountError(bad, false))
    assertFalse(CheckoutModel.canSubmit(false, "x", null, DiscountGate.State.FREE))
    assertFalse(CheckoutModel.canSubmit(false, null, null, DiscountGate.State.NEEDS_APPROVAL))
    assertFalse(CheckoutModel.canSubmit(false, null, null, DiscountGate.State.DENIED))
    assertFalse(CheckoutModel.canSubmit(true, null, null, DiscountGate.State.FREE))
    assertTrue(CheckoutModel.canSubmit(false, null, null, DiscountGate.State.APPROVED))
  }

  @Test
  fun `پرداختیِ نسیه نادرست فقط در پرداختِ ساده خطاست`() {
    val f = CheckoutForm(SalesEngine.Payment.CREDIT, "d1", paidText = "abc")
    assertTrue(CheckoutModel.paidError(f, payV2 = false) != null)
    assertNull(CheckoutModel.paidError(f, payV2 = true))
    assertNull(CheckoutModel.paidError(f.copy(payment = SalesEngine.Payment.CASH), payV2 = false))
  }

  @Test
  fun `ردیف های جمع با تخفیفِ خودکار جمعِ پیش از آن و ردیفِ منفی دارند`() {
    val f = CheckoutForm(SalesEngine.Payment.CASH, null)
    val n = CheckoutModel.numbers(ledger, cart, f, CheckoutModel.checkout(f, true, false, ""))
    val promo = PromoEngine.Outcome(gross = n.subtotal + 30, discount = 30.0, applied = emptyList(), codes = emptyList(), cart = cart)
    val rows = CheckoutModel.rows(n, promo, payV2 = false)
    assertEquals(n.subtotal + 30, rows[0].amount, 0.0)
    assertEquals(-30.0, rows[1].amount, 0.0)
    assertTrue(rows.single { it.strong }.amount == n.finalTotal)
    //  ترکیبی خلاصهٔ خودش را دارد ⇒ بی «پرداختی/باقی‌مانده»
    assertEquals(2, CheckoutModel.rows(n, null, payV2 = true).size)
  }

  @Test
  fun `نسیه بی پرداختیِ نوشته صفر می گیرد`() {
    val f = CheckoutModel.withPayment(CheckoutForm(SalesEngine.Payment.CASH, null), SalesEngine.Payment.CREDIT)
    assertEquals("0", f.paidText)
    assertEquals("7", CheckoutModel.withPayment(f.copy(paidText = "7"), SalesEngine.Payment.CASH).paidText)
  }

  /* ------------------------------ تاریخچه ------------------------------ */

  private val history = ledger.copy(
    sales = listOf(
      Sale("a", 10, finalTotal = 100.0, paidAmount = 100.0, createdAt = 3, date = "2026-10-24"),
      Sale("b", 11, finalTotal = 200.0, paidAmount = 50.0, remaining = 150.0, paymentMethod = "credit", debtorId = "d1", createdAt = 2, date = "2026-10-24"),
      Sale("c", 12, finalTotal = 300.0, status = "cancelled", createdAt = 1, date = "2026-09-30"),
      Sale("w", 4, finalTotal = 90.0, paidAmount = 90.0, series = "W", createdAt = 0, date = "2026-09-29"),
    ),
    saleItems = listOf(
      SaleItem("ai", "a", "soap", quantity = 2.0, unitPrice = 50.0, totalPrice = 100.0),
      SaleItem("bi", "b", "rice", quantity = 2.0, unitPrice = 100.0, totalPrice = 200.0, returnedQty = 2.0),
      SaleItem("ci", "c", "", name = "", quantity = 3.0, unitPrice = 100.0, totalPrice = 300.0),
    ),
  )

  @Test
  fun `فیلتر و جستجوی تاریخچه همان قاعدهٔ پیشین و تازه ترین اول`() {
    assertEquals(listOf("a", "b", "c", "w"), HistoryModel.shown(history, HistoryFilter.ALL, "", HistoryModel.ALL_MONTHS, false).map { it.id })
    assertEquals(listOf("c"), HistoryModel.shown(history, HistoryFilter.CANCELLED, "", HistoryModel.ALL_MONTHS, false).map { it.id })
    assertEquals(listOf("b"), HistoryModel.shown(history, HistoryFilter.ALL, "احمد", HistoryModel.ALL_MONTHS, false).map { it.id })
    assertEquals(listOf("a"), HistoryModel.shown(history, HistoryFilter.ALL, "10", HistoryModel.ALL_MONTHS, false).map { it.id })
    assertEquals(listOf("c", "w"), HistoryModel.shown(history, HistoryFilter.ALL, "", "2026-09", false).map { it.id })
    assertEquals(listOf(HistoryModel.ALL_MONTHS, "2026-10", "2026-09"), HistoryModel.months(history))
  }

  @Test
  fun `روزها به ترتیبِ فهرست و دیروزِ تقویمی`() {
    val days = HistoryModel.days(HistoryModel.shown(history, HistoryFilter.ALL, "", HistoryModel.ALL_MONTHS, false))
    assertEquals(listOf("2026-10-24", "2026-09-30", "2026-09-29"), days.map { it.date })
    assertEquals("2026-09-30", HistoryModel.yesterday("2026-10-01"))
    assertEquals("", HistoryModel.yesterday("bad"))
    //  از CivilDays ِ core: مرزِ سال و کبیسه، و تاریخِ نادرست دیگر «لِنیِنت» جابه‌جا نمی‌شود
    assertEquals("2025-12-31", HistoryModel.yesterday("2026-01-01"))
    assertEquals("2024-02-29", HistoryModel.yesterday("2024-03-01"))
    assertEquals("", HistoryModel.yesterday("2026-02-30"))
  }

  @Test
  fun `ساعتِ فاکتور حسابِ خالص با فاصلهٔ منطقه است`() {
    val at = 1_761_300_000_000L // 2025-10-24T10:00:00Z
    assertEquals("10:00", SaleTime.clock(at, 0))
    assertEquals("14:30", SaleTime.clock(at, (4.5 * 3_600_000).toInt())) // کابل
    assertEquals("23:00", SaleTime.clock(at, -11 * 3_600_000))
    assertEquals("", SaleTime.clock(0, 0))
  }

  @Test
  fun `سابقهٔ صندوق روی دفترِ همان لحظه نوشته می شود، نه تصویرِ کهنه`() {
    val rel = "ir/vil3ntec/tohid/ui/v3/sales/drawer/DrawerRoutes.kt"
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    assertFalse(src.contains("store.save("))
    assertEquals(2, Regex("""store\.appendAudit \{""").findAll(src).count())
  }

  @Test
  fun `مدل های فروشِ نسخهٔ ۳ جز درزِ ساعت java نمی خوانند`() {
    val dir = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/v3/sales") }.first { it.isDirectory }
    val bad = dir.walk().filter { it.name.endsWith("Model.kt") }
      .filter { f -> f.readLines().any { l -> !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") && Regex("""\bjava\.|\bMath\.""").containsMatchIn(l) } }
      .map { it.name }.toList()
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `آمار و ردیف ها لغوشده و اینترنتی را جدا می کنند`() {
    val s = HistoryModel.stats(history)
    assertEquals(390.0, s.total, 0.0)
    assertEquals(3, s.count)
    assertEquals(1, s.cancelled)
    assertEquals(SaleState.CANCELLED, HistoryModel.row(history, history.sales[2], false).state)
    assertFalse(HistoryModel.row(history, history.sales[2], false).canCancel)
    assertEquals(SaleState.ONLINE, ReceiptModel.state(history.sales[3]))
    assertFalse(HistoryModel.row(history, history.sales[3], false).canCancel)
    assertTrue(HistoryModel.row(history, history.sales[0], false).canReturn)
    //  همه برگشته ⇒ مرجوعی نه
    assertFalse(HistoryModel.row(history, history.sales[1], false).canReturn)
  }

  @Test
  fun `اصلاحِ فاکتور فقط برنگشته ها را با همان قیمت به سبد می برد`() {
    val withReturn = history.copy(saleItems = history.saleItems + SaleItem("ai2", "a", "rice", quantity = 3.0, unitPrice = 90.0, totalPrice = 270.0, returnedQty = 1.0))
    val lines = HistoryModel.reopenLines(withReturn, withReturn.sales[0]) { "n" }
    assertEquals(listOf(CartLine("soap", 2.0, unitPrice = 50.0), CartLine("rice", 2.0, unitPrice = 90.0)), lines)
    val free = HistoryModel.reopenLines(history, history.sales[2]) { "n1" }.single()
    assertEquals(CartLine("n1", 3.0, unitPrice = 100.0, label = HistoryModel.FREE_ITEM), free)
  }

  @Test
  fun `رسید جمع ها و حال را از خودِ فاکتور می سازد`() {
    val rows = ReceiptModel.totals(history.sales[1])
    assertTrue(rows.single { it.warn }.amount == 150.0)
    assertEquals(200.0, rows.single { it.strong }.amount, 0.0)
    assertEquals("احمد کریمی", ReceiptModel.debtorName(history, history.sales[1]))
    assertEquals("W-4", ReceiptModel.number(history.sales[3]))
  }

  @Test
  fun `نشانِ تخفیفِ خودکار در رسید فقط با پرچم است و از جمع کم نمی شود`() {
    val sale = Sale("p", 1, total = 90.0, finalTotal = 90.0, promoDiscountMinor = 1000)
    val item = SaleItem("pi", "p", "soap", quantity = 2.0, unitPrice = 45.0, totalPrice = 90.0,
      listPriceMinor = 5000, promo = listOf(SalePromo("r1", 1, "عید", 1000)))
    val d = ledger.copy(sales = listOf(sale), saleItems = listOf(item))
    //  خاموش ⇒ همان ردیف‌ها و همان متن
    assertEquals(ReceiptModel.totals(sale).map { it.label }, ReceiptModel.totals(sale, d.saleItems, false).map { it.label })
    assertEquals(ReceiptModel.lines(d, sale).single().detail, ReceiptModel.lines(d, sale, false).single().detail)
    assertFalse(ReceiptModel.lines(d, sale).single().detail.contains("50"))
    //  روشن ⇒ قیمتِ فهرست و ردیفِ آگاهی؛ «جمع اقلام» و «مبلغ نهایی» همان
    assertTrue(ReceiptModel.lines(d, sale, true).single().detail.contains("50"))
    val on = ReceiptModel.totals(sale, d.saleItems, true)
    assertEquals(ReceiptModel.totals(sale).size + 1, on.size)
    assertEquals(10.0, on[1].amount, 0.0)
    assertTrue(on[1].label.contains("عید"))
    assertEquals(90.0, on.single { it.strong }.amount, 0.0)
  }

  /* ------------------------------ برگشتِ ساده ------------------------------ */

  @Test
  fun `پیش نمایشِ مرجوعی همان فرمولِ ثبت است، با سهمِ تخفیفِ فاکتور`() {
    val sale = Sale("r", 1, total = 200.0, discount = 50.0, finalTotal = 150.0)
    val d = ledger.copy(sales = listOf(sale), saleItems = listOf(SaleItem("ri", "r", "soap", quantity = 4.0, unitPrice = 50.0, totalPrice = 200.0, returnedQty = 1.0)))
    val line = ReturnModel.lines(d, sale).single()
    assertEquals(3.0, line.left, 0.0)
    assertEquals(75.0, ReturnModel.total(sale, listOf(line), mapOf("ri" to "2")), 0.0)
    //  همان که موتور ثبت می‌کند
    val ok = SalesEngine.recordReturn(d, "r", mapOf("ri" to 2.0), "", "2026-10-24", 1L) { "x" } as SalesEngine.Result.Ok
    assertEquals(75.0, ok.data.saleReturns.sumOf { it.amount }, 0.0)
  }

  @Test
  fun `پلهٔ مرجوعی در بازهٔ مجاز می ماند و بیش از آن خطاست`() {
    val sale = Sale("r", 1, total = 100.0, finalTotal = 100.0)
    val d = ledger.copy(sales = listOf(sale), saleItems = listOf(SaleItem("ri", "r", "soap", quantity = 2.0, unitPrice = 50.0, totalPrice = 100.0)))
    val line = ReturnModel.lines(d, sale).single()
    assertEquals("1", ReturnModel.bump("", line, +1))
    assertEquals("2", ReturnModel.bump("2", line, +1))
    assertEquals("", ReturnModel.bump("1", line, -1))
    assertTrue(ReturnModel.error("3", line) != null)
    assertNull(ReturnModel.error("2", line))
    assertEquals(mapOf("ri" to 2.0), ReturnModel.quantities(mapOf("ri" to "2", "x" to "0", "y" to "")))
  }

  /* ------------------------------ صندوق ------------------------------ */

  @Test
  fun `اختلافِ شمارش زیرِ نیم افغانی بی اختلاف است`() {
    assertEquals(DiffKind.NONE, DrawerModel.diff(100.3, 100.0)!!.kind)
    assertEquals(DiffKind.OVER, DrawerModel.diff(110.0, 100.0)!!.kind)
    assertEquals(DiffKind.SHORT, DrawerModel.diff(90.0, 100.0)!!.kind)
    assertEquals(10.0, DrawerModel.diff(90.0, 100.0)!!.amount, 0.0)
    assertNull(DrawerModel.diff(null, 100.0))
  }

  @Test
  fun `ریزِ صندوق همان مورد انتظارِ موتور است`() {
    val open = (CashShift.open(ShopData(), 500.0, "", "2026-10-24", 1L) { "o" } as CashShift.Result.Ok).data
    val t = CashShift.totalsOf(open, CashShift.current(open)!!, 2L)
    val rows = DrawerModel.breakdown(t)
    assertEquals(t.expected, rows.single { it.strong }.amount, 0.0)
    assertEquals(500.0, rows.first().amount, 0.0)
    assertFalse(DrawerModel.line(ShopData(), 0L, blind = false).open)
    assertTrue(DrawerModel.line(open, 2L, blind = true).open)
  }

  @Test
  fun `شمارشِ اسکناس فقط رقم و نادرست منفی`() {
    assertEquals(mapOf("1000" to 2L, "500" to -1L), DrawerModel.counts(mapOf("1000" to "2", "500" to "x", "100" to " ")))
  }

  /* ------------------------------ چاپ ------------------------------ */

  @Test
  fun `چاپگرِ دفعهٔ قبل همان راه را باز می کند`() {
    //  همان قالبِ ذخیرهٔ `ThermalPrinter.Link.save`
    val net = PrintModel.start("net:192.168.1.50:9200", ThermalPrinter.WIDTH_80MM, emptyList())
    assertEquals(PrintKind.NETWORK, net.kind)
    assertEquals("9200", net.port)
    assertEquals(ThermalPrinter.WIDTH_80MM, net.width)
    assertEquals("net:192.168.1.50:9200", PrintModel.target(net))
    //  نشانیِ لختِ نسخه‌های قبلی بلوتوث است
    assertEquals(PrintKind.BLUETOOTH, PrintModel.start("00:11:22:33:44:55", ThermalPrinter.WIDTH_58MM, emptyList()).kind)
    val saved = PrintModel.start("net:192.168.1.50", 1, listOf("net:192.168.1.50"))
    assertEquals("net:192.168.1.50", saved.saved)
    assertEquals(ThermalPrinter.WIDTH_58MM, saved.width)
    assertNull(PrintModel.target(PrintModel.start(null, ThermalPrinter.WIDTH_58MM, emptyList())))
    assertEquals("123", PrintModel.port("1a2۳"))
  }

  @Test
  fun `چاپِ دوباره دلیل می خواهد اگر این جا یا در سابقهٔ دکان چاپ شده`() {
    assertTrue(PrintModel.needsReason(true, ShopData(), "s"))
    assertFalse(PrintModel.needsReason(false, ShopData(), "s"))
    val d = ShopData(auditLog = listOf(ir.vil3ntec.tohid.data.AuditEntry(id = "a", type = "reprint", refId = "s")))
    assertTrue(PrintModel.needsReason(false, d, "s"))
    assertEquals("x", PrintModel.reason(null, " x "))
    assertEquals("p", PrintModel.reason("p", "x"))
  }

  /* ------------------------------ تأیید ------------------------------ */

  @Test
  fun `PIN شش رقمی و بی دکان یا تأییدکننده بسته`() {
    var pin = ""
    "1234567".forEach { pin = ApprovalModel.press(pin, it, 6) }
    assertEquals("123456", pin)
    assertTrue(ApprovalModel.complete(pin, 6))
    assertEquals("12345", ApprovalModel.press(pin, KeypadInput.BACKSPACE, 6))
    assertEquals("1", ApprovalModel.press("1", '.', 6))
    assertEquals(ApprovalBlock.NO_SHOP, ApprovalModel.block("", emptyList()))
    assertEquals(ApprovalBlock.NO_APPROVER, ApprovalModel.block("shop", emptyList()))
  }
}
