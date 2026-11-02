package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.hw.Barcodes
import ir.vil3ntec.tohid.core.hw.Code128
import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.pay.GiftCard
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutStoredPay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-14 / PAY-24 — شارژِ دوباره (نقدِ دخل + یک ردیفِ دفتر، هم‌اتم و تکرارناپذیر)، باطل با اجازه و دلیل، گزارشِ
 *  بدهیِ کارت‌ها (برابر با ردیف‌های خامِ دفتر)، برگهٔ چاپیِ کارت و پرداخت با کارت در صندوقِ v3 (`CheckoutStoredPay`)
 *  با همان API ِ صندوقِ قدیم. تنظیمِ خاموش ⇒ همان امروز (`GiftCardOffTest` و آخرین آزمونِ همین‌جا).
 */
class GiftCardOpsFlowTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-02"
  /** کدِ گوناگون (نه یک نویسهٔ تکراری) تا «فقط چهار نویسهٔ آخر» واقعاً سنجیده شود */
  private val code = run { var i = 0; GiftCard.make { n -> (i++ * 7 + 3) % n } }

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
  private val open get() = base.auditLog.first { it.id == "open1" }

  private fun okData(r: GiftCards.Issued): GiftCards.Issued.Ok = r as GiftCards.Issued.Ok
  private fun bad(r: GiftCards.Issued): String = (r as GiftCards.Issued.Bad).code
  private fun card(d: ShopData) = GiftCards.view(d).card(code)!!
  private fun gift(amount: String, c: String = code) = PayFlow.DraftRow("gift_card", c, amount)
  private fun settle(total: Double, cash: String, rows: List<PayFlow.DraftRow>): Settlement =
    (PayFlow.checkout(PayFlow.Draft(total, cash, rows, null, stored = true)) as PayFlow.Outcome.Ok).value
  private fun record(d: ShopData, st: Settlement, saleId: String, cart: List<SalesEngine.CartLine>) =
    SalesEngine.record(d, cart, SalesEngine.Checkout(seller = "رضا", settlement = st), today, 100, newId, saleId = saleId)

  @Test fun `شارژ یک ردیف است که هم دفترِ کارت است هم نقدِ دخل و تکرارش شارژِ دوم نمی‌سازد`() {
    val before = CashShift.totals(base, 50, 700, 500.0, "open1")
    val r = okData(GiftCards.recharge(base, "rc1", GiftCard.display(code), 50_000, today, 200, "رضا"))
    assertFalse(r.again)
    val d = r.data
    //  فقط یک ردیفِ تازه، همان کلید، کدِ کامل فقط در `payRef` و یادداشت نقاب‌دار
    assertEquals(base.auditLog.size + 1, d.auditLog.size)
    val row = d.auditLog.single { it.type == GiftCards.RECHARGE }
    assertEquals("rc1" to code, row.id to row.payRef)
    assertEquals(500.0, row.amount, 0.0)
    assertFalse(row.notes.contains(code))
    assertTrue(row.notes.contains(GiftCards.mask(code)))
    assertEquals(250_000L, card(d).balanceMinor)
    assertEquals(50_000L, card(d).rechargedMinor)
    //  دخل: همان ۵۰۰ افغانی، هم حسابِ ساده هم رویدادهای هستهٔ صندوق
    assertEquals(before.receipts + 500.0, CashShift.totals(d, 50, 700, 500.0, "open1").receipts, 0.0)
    val ev = PayFlow.ledgerEvents(d, open, 700).single { it.id == "gci:rc1" }
    assertEquals(Triple("receipt", PayFlow.CASH, 50_000L), Triple(ev.type, ev.method, ev.amount))
    //  همان کلید و همان محتوا ⇒ همان دفتر، بی ردیفِ دوم؛ محتوای دیگر ⇒ ناسازگار
    val again = okData(GiftCards.recharge(d, "rc1", code, 50_000, today, 999, "رضا"))
    assertTrue(again.again)
    assertSame(d, again.data)
    assertEquals(GiftCard.Code.ID_CONFLICT, bad(GiftCards.recharge(d, "rc1", code, 60_000, today, 999, "رضا")))
    assertEquals(1, d.auditLog.count { it.type == GiftCards.RECHARGE })
    //  ردشده‌ها هیچ نمی‌نویسند و دفترِ ورودی دست نمی‌خورد
    assertEquals(GiftCard.Code.INVALID_AMOUNT, bad(GiftCards.recharge(base, "rc2", code, 0, today, 200, null)))
    assertEquals(GiftCard.Code.UNKNOWN_CARD, bad(GiftCards.recharge(base, "rc2", GiftCard.make { 9 }, 100, today, 200, null)))
    assertEquals(GiftCard.Code.BAD_CODE, bad(GiftCards.recharge(base, "rc2", "ABCD", 100, today, 200, null)))
    assertEquals(200_000L, card(base).balanceMinor)
    assertTrue(base.auditLog.none { it.type == GiftCards.RECHARGE })
  }

  @Test fun `باطل فقط با اجازه و دلیل است و ماندهٔ کارت بی نقد از بدهی کنار می‌رود`() {
    //  بی اجازه یا بی دلیل: هیچ ردیفی
    assertEquals(GiftCards.NOT_ALLOWED, bad(GiftCards.void(base, "v1", code, "گم شد", false, today, 300, "رضا")))
    assertEquals(StrGiftCard.errNotAllowed, GiftCards.message(GiftCards.NOT_ALLOWED))
    for (r in listOf(null, "", "ab")) assertEquals("$r", GiftCard.Code.REASON_REQUIRED, bad(GiftCards.void(base, "v1", code, r, true, today, 300, "رضا")))
    assertEquals(StrGiftCard.errReason, GiftCards.message(GiftCard.Code.REASON_REQUIRED))
    val drawer = CashShift.totals(base, 50, 700, 500.0, "open1")
    val r = okData(GiftCards.void(base, "v1", code, "  گم شد  ", true, today, 300, "مدیر"))
    val d = r.data
    val row = d.auditLog.single { it.type == GiftCards.VOID }
    assertEquals(Triple("v1", "گم شد", "مدیر"), Triple(row.id, row.reason, row.by))
    assertEquals(0.0, row.amount, 0.0)
    assertTrue(row.notes.contains(GiftCards.mask(code)) && !row.notes.contains(code))
    //  مانده دست نخورد ولی بدهی نیست؛ دخل همان
    assertTrue(card(d).void)
    assertEquals(200_000L, card(d).balanceMinor)
    assertEquals(0L, GiftCards.liability(d).outstandingMinor)
    assertEquals(200_000L, GiftCards.liability(d).voidedMinor)
    assertEquals(drawer, CashShift.totals(d, 50, 700, 500.0, "open1"))
    //  پس از باطل: نه خرج، نه شارژ، نه باطلِ دوباره؛ تکرارِ همان کلید ⇒ همان دفتر
    val st = settle(3250.0, "2250", listOf(gift("1000")))
    assertEquals(StrGiftCard.errVoid, (record(d, st, "sA", listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0))) as SalesEngine.Result.Failed).message)
    assertEquals(GiftCard.Code.CARD_VOID, bad(GiftCards.recharge(d, "rc9", code, 100, today, 400, null)))
    assertEquals(GiftCard.Code.CARD_VOID, bad(GiftCards.void(d, "v2", code, "دوباره", true, today, 400, null)))
    assertSame(d, okData(GiftCards.void(d, "v1", code, "گم شد", true, today, 999, "مدیر")).data)
  }

  @Test fun `گزارشِ بدهی با ردیف‌های خامِ دفتر و با صدور و شارژ و خرج و برگشت و باطل می‌خواند`() {
    val other = GiftCard.make { 9 }
    val third = GiftCard.make { 13 }
    var d = okData(GiftCards.issue(base, "g2", other, 100_000, today, 61, "رضا")).data
    d = okData(GiftCards.issue(d, "g3", third, 30_000, today, 62, "رضا")).data
    d = okData(GiftCards.recharge(d, "rc1", other, 25_000, today, 70, "رضا")).data
    //  خرج با فاکتور، برگشتِ بخشی از آن به همان کارت، و باطلِ کارتِ سوم
    val cart = listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0))
    d = (record(d, settle(3250.0, "1250", listOf(gift("2000"))), "sA", cart) as SalesEngine.Result.Ok).data
    val rice = d.saleItems.first { it.saleId == "sA" && it.productId == "p2" }.id
    d = (ReturnFlow.post(d, ReturnFlow.Request(docId = "rA", saleId = "sA", picks = mapOf(rice to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, 200, newId) as ReturnFlow.Outcome.Ok).value.data
    d = okData(GiftCards.void(d, "v3", third, "گم شد", true, today, 300, "مدیر")).data
    val r = GiftCards.liability(d)
    //  جمعِ مستقیم از ردیف‌های سابقه (افغانی ⇒ یک‌صدم)، نه از هسته
    fun rows(type: String) = d.auditLog.filter { it.type == type }.sumOf { PayFlow.minor(it.amount) }
    assertEquals(rows(GiftCards.ISSUE), r.issuedMinor)
    assertEquals(rows(GiftCards.RECHARGE), r.rechargedMinor)
    assertEquals(rows(GiftCards.REDEEM), r.redeemedMinor)
    assertEquals(rows(GiftCards.REFUND), r.refundedMinor)
    assertEquals(listOf(330_000L, 25_000L, 200_000L, 80_000L), listOf(r.issuedMinor, r.rechargedMinor, r.redeemedMinor, r.refundedMinor))
    assertEquals(30_000L, r.voidedMinor)
    //  مانده‌ها: کارت ۸۰٬۰۰۰ (۲٬۰۰۰ − ۲٬۰۰۰ + ۸۰۰)، دوم ۱۲۵٬۰۰۰
    assertEquals(80_000L + 125_000L, r.outstandingMinor)
    assertEquals(0L, r.overdrawnMinor)
    assertEquals(r.issuedMinor + r.rechargedMinor - r.redeemedMinor + r.refundedMinor - r.voidedMinor, r.netMinor)
    assertTrue(r.reconciles)
    assertEquals(GiftCards.view(d).liabilityMinor, r.outstandingMinor)
    assertEquals(listOf(other, code), r.open.map { it.code })
    assertEquals(2 to 1, r.activeCount to r.voidCount)
    //  دفترِ بی کارت ⇒ گزارشِ صفر
    val none = GiftCards.liability(base.copy(auditLog = base.auditLog.filter { !GiftCards.isRow(it) }))
    assertEquals(0L, none.outstandingMinor + none.issuedMinor)
  }

  @Test fun `برگهٔ چاپیِ کارت کد را با نویسهٔ وارسی و بارکدِ همان کد دارد ولی رسید فقط چهار نویسهٔ آخر`() {
    val s = GiftCards.sheet(GiftCard.display(code).lowercase(), 200_000, " دکانِ رضا ", "۱۴۰۵/۰۸/۱۱")!!
    assertEquals(GiftCard.display(code), s.code)
    assertEquals(14, s.code.length)
    assertEquals(code, s.barcode)
    assertEquals(GiftCard.checkChar(code.dropLast(1)), s.check)
    assertEquals(code.last(), s.code.last())
    //  بارکد همان کد است و کادرِ کد هم نوشتهٔ برگه را می‌پذیرد
    assertEquals(code, GiftCard.normalize(s.barcode))
    assertEquals(code, GiftCard.normalize(s.code))
    assertEquals(Barcodes.modulesString(Code128.modules(code)), s.modules)
    assertTrue(s.modules.all { it == '0' || it == '1' } && s.modules.isNotEmpty())
    assertEquals(StrGiftCard.sheetTitle, s.title)
    assertEquals("دکانِ رضا", s.shop)
    assertEquals(StrGiftCard.sheetAmount(ir.vil3ntec.tohid.money(2000.0)), s.amount)
    assertEquals("۱۴۰۵/۰۸/۱۱", s.date)
    assertEquals(StrGiftCard.sheetNote, s.note)
    //  چاپِ دوباره بی مبلغ و بی نام؛ کدِ نادرست ⇒ هیچ برگه
    val again = GiftCards.sheet(code, null, "  ", null)!!
    assertNull(again.amount)
    assertNull(again.shop)
    assertNull(GiftCards.sheet("ABCD", 100, null, null))
    assertNull(GiftCards.sheet(code.dropLast(1) + GiftCard.ALPHABET.first { it != code.last() }, 100, null, null))
    //  رسیدِ فاکتورِ پرداخته با کارت: فقط ••••‌ و چهار نویسهٔ آخر
    val cart = listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0))
    val d = (record(base, settle(3250.0, "1250", listOf(gift("2000"))), "sA", cart) as SalesEngine.Result.Ok).data
    val lines = PayFlow.receiptLines(d.sales.single { it.id == "sA" }).joinToString("\n") { it.first }
    assertTrue(lines.contains(GiftCards.mask(code)))
    assertFalse(lines.contains(code.take(8)))
  }

  @Test fun `صندوقِ v3 با کارت فقط تا مانده می‌پردازد با همان API ِ صندوقِ قدیم`() {
    val cart = listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0))
    //  تا ته مانده: پذیرفته، و ثبت ردیفِ خرج را هم‌اتم با فاکتور می‌نویسد
    val full = CheckoutStoredPay.settle(base, PayFlow.Draft(3250.0, "1250", listOf(gift("2000")), null, stored = true))
    val st = (full as CheckoutStoredPay.Out.Ok).settlement
    val d = (record(base, st, "sV", cart) as SalesEngine.Result.Ok).data
    assertEquals(0L, card(d).balanceMinor)
    assertEquals("sV", d.auditLog.single { it.type == GiftCards.REDEEM }.refId)
    //  یک افغانی بیش از مانده: همان پیامِ صندوقِ قدیم، پیش از ثبت
    val over = CheckoutStoredPay.settle(base, PayFlow.Draft(3250.0, "1249", listOf(gift("2001")), null, stored = true))
    assertEquals(CheckoutStoredPay.Out.Bad(StrGiftCard.errBalance), over)
    assertEquals(GiftCards.message(GiftCards.problem(base, settle(3250.0, "1249", listOf(gift("2001"))), null)!!), (over as CheckoutStoredPay.Out.Bad).message)
    //  کارتی که خرج شد دیگر نمی‌پردازد؛ کارتِ ناشناخته و اعتبارِ بی مشتری هم رد
    assertEquals(CheckoutStoredPay.Out.Bad(StrGiftCard.errBalance), CheckoutStoredPay.settle(d, PayFlow.Draft(800.0, "300", listOf(gift("500")), null, stored = true)))
    assertEquals(CheckoutStoredPay.Out.Bad(StrGiftCard.errUnknown), CheckoutStoredPay.settle(base, PayFlow.Draft(800.0, "300", listOf(gift("500", GiftCard.make { 9 })), null, stored = true)))
    val noCustomer = CheckoutStoredPay.settle(base, PayFlow.Draft(800.0, "300", listOf(PayFlow.DraftRow("store_credit", "", "500")), null, stored = true))
    assertTrue(noCustomer is CheckoutStoredPay.Out.Bad)
    //  راهنمای زیرِ ردیف: ماندهٔ خرج‌شدنی و اعتبارِ مشتری
    val view = GiftCards.view(base)
    assertEquals(StrGiftCard.cardLeft(ir.vil3ntec.tohid.money(2000.0)), CheckoutStoredPay.hint(base, view, gift("", GiftCard.display(code)), null))
    assertNull(CheckoutStoredPay.hint(base, view, gift("", "ABCD"), null))
    assertEquals(StrGiftCard.creditLeft(ir.vil3ntec.tohid.money(0.0)), CheckoutStoredPay.hint(base, view, PayFlow.DraftRow("store_credit"), "d1"))
    assertNull(CheckoutStoredPay.hint(base, view, PayFlow.DraftRow("hesabpay", "HP-1", "10"), null))
  }

  @Test fun `تنظیمِ خاموش در صندوقِ v3 همان تسویه و پیام‌های امروز است`() {
    val drafts = listOf(
      PayFlow.Draft(3250.0, "3250"),
      PayFlow.Draft(3250.0, "2000", listOf(PayFlow.DraftRow("hesabpay", "HP-77", "1250"))),
      PayFlow.Draft(3250.0, "100"),
      PayFlow.Draft(3250.0, "2250", listOf(gift("1000"))),
      PayFlow.Draft(800.0, "300", listOf(PayFlow.DraftRow("store_credit", "", "500")), "d1"),
    )
    for (dr in drafts) {
      val want = when (val r = PayFlow.checkout(dr)) {
        is PayFlow.Outcome.Ok -> CheckoutStoredPay.Out.Ok(r.value)
        is PayFlow.Outcome.Bad -> CheckoutStoredPay.Out.Bad(PayFlow.message(r.problem))
      }
      assertEquals(dr.toString(), want, CheckoutStoredPay.settle(base, dr))
    }
    //  بی دفتر (تنظیمِ خاموش) نه راهنما، نه پیش‌سنجش؛ روش‌های پنل همان امروز
    assertNull(CheckoutStoredPay.hint(null, null, gift("1000"), null))
    assertNull(CheckoutStoredPay.problem(null, settle(3250.0, "1250", listOf(gift("2000"))), null))
    assertEquals(PayFlow.panelMethods(), PayFlow.panelMethods(false))
    //  دفترِ بی کارت: نه رسیدِ کارت در دخل، نه بدهی
    val plain = base.copy(auditLog = base.auditLog.filter { !GiftCards.isRow(it) })
    assertTrue(GiftCards.cashRows(plain, 0, Long.MAX_VALUE).isEmpty())
    assertEquals(0.0, GiftCards.cashIn(plain, 0, Long.MAX_VALUE), 0.0)
  }
}
