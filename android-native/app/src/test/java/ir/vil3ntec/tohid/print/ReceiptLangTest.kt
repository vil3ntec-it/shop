package ir.vil3ntec.tohid.print

import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrReceipt
import ir.vil3ntec.tohid.core.receipt.DiscountLine
import ir.vil3ntec.tohid.core.receipt.GiftReceiptLayout
import ir.vil3ntec.tohid.core.receipt.LineView
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptInput
import ir.vil3ntec.tohid.core.receipt.ReceiptLabels
import ir.vil3ntec.tohid.core.receipt.ReceiptLayout
import ir.vil3ntec.tohid.core.receipt.ShopHeader
import ir.vil3ntec.tohid.core.receipt.StatementInput
import ir.vil3ntec.tohid.core.receipt.StatementLabels
import ir.vil3ntec.tohid.core.receipt.StatementLayout
import ir.vil3ntec.tohid.core.receipt.StatementLine
import ir.vil3ntec.tohid.core.text.DariText
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalePayment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  #348 — فاکتور، رسیدِ هدیه و صورت‌حسابِ چاپی به هر چهار زبان (`StrReceipt` ⇐ `Receipts.labels`، `giftLabels`،
 *  `statementLabels`؛ رقم، پول و تاریخ با `Receipts.format`). دو بخش:
 *   - جدولِ برچسب‌ها در هر زبان: فارسی بایت‌به‌بایت همان `ReceiptLabels.FA` ِ هسته، دری همان فارسی از لایهٔ واژه، انگلیسی
 *     بی حرفِ فارسی، پشتو به خطِ عربی و جدا از فارسی؛ نامِ روشِ پرداخت فقط برای واژهٔ ثابتِ برنامه.
 *   - نمونهٔ کاملِ یک فاکتور در هر زبان (بلوک به بلوک، همان الگوی `ReceiptLayoutTest`): دادهٔ فروشنده (نامِ دکان، کالا، واحد،
 *     فروشنده، یادداشت) در هر چهار زبان دست‌نخورده.
 */
class ReceiptLangTest {

  @After fun reset() {
    I18n.source = { Lang.FA }
  }

  /** `LocaleChoice` در نخستین خواندن `I18n.source` را به زبانِ خودش وصل می‌کند ⇒ اول آن، بعد زبانِ آزمون */
  private fun <T> on(lang: Lang, block: () -> T): T {
    LocaleChoice.digits
    I18n.source = { lang }
    return block()
  }

  private val arabicLetter = Regex("[\\u0621-\\u064A\\u066E-\\u06D3\\u06FA-\\u06FF]")

  private fun fields(l: ReceiptLabels) = listOf(
    l.fallbackShop, l.invoiceNo, l.date, l.lunarDate, l.cashier, l.kind, l.cancelled, l.returned, l.listPrice, l.count, l.rows,
    l.rowOne, l.pieces, l.pieceOne, l.subtotal, l.discount, l.total, l.paid, l.tendered, l.change, l.remaining, l.debtor, l.phone, l.scan, l.storeScan,
    l.footer, l.cash, l.credit, l.online, l.note, l.rounding,
  )

  private fun fields(l: StatementLabels) = listOf(
    l.fallbackShop, l.title, l.customer, l.phone, l.date, l.give, l.receive, l.balance, l.totalGive, l.totalReceive, l.owes,
    l.credit, l.settled, l.empty, l.rows, l.rowOne, l.footer,
  )

  private fun fields(l: GiftReceiptLayout.Labels) = listOf(l.title, l.note) + fields(l.base)

  /** همهٔ برچسب‌های کاغذِ یک زبان */
  private fun all(): List<String> = fields(Receipts.labels()) + fields(Receipts.statementLabels()) + fields(Receipts.giftLabels())

  /* ------------------------------ جدولِ برچسب‌ها ------------------------------ */

  @Test fun `فارسی — بایت‌به‌بایت همان برچسب‌های هسته`() = on(Lang.FA) {
    assertEquals(ReceiptLabels.FA, Receipts.labels())
    assertEquals(StatementLabels.FA, Receipts.statementLabels())
    assertEquals(GiftReceiptLayout.Labels.FA, Receipts.giftLabels())
  }

  @Test fun `دری — همان فارسی از لایهٔ واژه، مثلِ دورِ ۷`() = on(Lang.PRS) {
    assertEquals(fields(ReceiptLabels.FA).map(DariText::of), fields(Receipts.labels()))
    assertEquals(fields(StatementLabels.FA).map(DariText::of), fields(Receipts.statementLabels()))
    assertEquals(fields(GiftReceiptLayout.Labels.FA).map(DariText::of), fields(Receipts.giftLabels()))
    assertEquals("دکان", Receipts.labels().fallbackShop)
  }

  @Test fun `انگلیسی — هر برچسب پر و بی حرفِ فارسی`() = on(Lang.EN) {
    for (t in all()) {
      assertTrue("خالی", t.isNotBlank())
      assertFalse("حرفِ فارسی در کاغذِ انگلیسی: $t", arabicLetter.containsMatchIn(t))
    }
    val l = Receipts.labels()
    assertEquals("Invoice no.", l.invoiceNo)
    assertEquals("Total", l.total)
    assertEquals("Thank you for your purchase", l.footer)
    assertEquals("Gift receipt", Receipts.giftLabels().title)
    assertEquals("Customer statement", Receipts.statementLabels().title)
  }

  @Test fun `پشتو — هر برچسب پر، به خطِ عربی، و نه همان فارسی`() = on(Lang.PS) {
    for (t in all()) {
      assertTrue("خالی", t.isNotBlank())
      assertTrue("پشتو به خطِ عربی نیست: $t", arabicLetter.containsMatchIn(t))
    }
    val ps = fields(Receipts.labels())
    val fa = fields(ReceiptLabels.FA)
    //  «تخفیف» و «آنلاین» در پشتو هم همان‌اند؛ بقیه نه
    val same = ps.indices.filter { ps[it] == fa[it] }.map { fa[it] }
    assertEquals(listOf("تخفیف", "آنلاین"), same)
    assertEquals("د بل شمېره", Receipts.labels().invoiceNo)
    assertEquals("له پېرود څخه مو مننه", Receipts.labels().footer)
    assertNotEquals(StatementLabels.FA, Receipts.statementLabels())
    assertNotEquals(GiftReceiptLayout.Labels.FA.title, Receipts.giftLabels().title)
  }

  @Test fun `نامِ روشِ پرداخت — فقط واژهٔ ثابتِ برنامه، فارسیِ جدول همان واژهٔ KIND_LABELS`() {
    //  فارسیِ هر کلیدِ `StrReceipt.pay*` همان واژهٔ جدولِ داده است، پس نگاشت واژهٔ درست را ترجمه می‌کند
    on(Lang.FA) {
      val k = PayFlow.KIND_LABELS
      assertEquals(k.getValue("cash"), StrReceipt.payCash)
      assertEquals(k.getValue("credit"), StrReceipt.payCredit)
      assertEquals(k.getValue(PayFlow.ACCOUNT), StrReceipt.payCredit)
      assertEquals(k.getValue("card"), StrReceipt.payCard)
      assertEquals(k.getValue("transfer"), StrReceipt.payTransfer)
      assertEquals(k.getValue("wallet"), StrReceipt.payWallet)
      assertEquals(k.getValue("store_credit"), StrReceipt.payStoreCredit)
      assertEquals(k.getValue("gift_card"), StrReceipt.payGiftCard)
      assertEquals(k.getValue("other"), StrReceipt.payOther)
      assertEquals(k.getValue(PayFlow.MIXED), StrReceipt.payMixed)
      assertEquals(k.getValue(PayFlow.ONLINE), StrReceipt.payOnline)
      assertEquals(k.getValue(PayFlow.COD), StrReceipt.payCod)
      assertEquals(k.getValue(ir.vil3ntec.tohid.core.pay.Pay.FX), StrReceipt.payFx) // PAY-10
      assertEquals(PayFlow.methodName(PayFlow.ACCOUNT), StrReceipt.payAccount)
      assertEquals(PayFlow.methodName("afpay_card"), StrReceipt.payAfPayCard)
      //  فارسی ⇒ همان نام، هرچه باشد
      for (v in k.values) assertEquals(v, Receipts.payName(v))
    }
    on(Lang.EN) {
      for (v in PayFlow.KIND_LABELS.values) assertFalse(Receipts.payName(v), arabicLetter.containsMatchIn(Receipts.payName(v)))
      assertEquals("Transfer", Receipts.payName(PayFlow.methodName("hawala")))
      assertEquals("AfPay card", Receipts.payName(PayFlow.methodName("afpay_card")))
      //  نامِ تجاریِ لاتین و هر نامِ دیگر (دادهٔ دکان) همان
      assertEquals("HesabPay", Receipts.payName(PayFlow.methodName("hesabpay")))
      assertEquals("صرافیِ کریمی", Receipts.payName("صرافیِ کریمی"))
    }
    on(Lang.PS) {
      assertEquals("ګډ", Receipts.payName(PayFlow.KIND_LABELS.getValue(PayFlow.MIXED)))
      assertEquals("صرافیِ کریمی", Receipts.payName("صرافیِ کریمی"))
    }
    on(Lang.PRS) {
      assertEquals("صرافیِ کریمی", Receipts.payName("صرافیِ کریمی"))
    }
  }

  /* ------------------------------ نمونهٔ کاملِ فاکتور در هر زبان ------------------------------ */

  private val url = "https://api.example.com/r/d2jcj71t/3t3gq9p07z23q5tf2b58cggxv6"

  /** فروشِ ترکیبی: ۳۰۰ نقد + ۲۷۰ حواله، ۴۰۰ دریافتِ نقد و ۱۰۰ پولِ پس */
  private val sale = Sale(
    id = "s-lang", invoiceNumber = 154, total = 600.0, finalTotal = 570.0, paidAmount = 570.0, discount = 30.0,
    createdAt = 1L, date = "2026-10-24", paymentMethod = "mixed",
    payments = listOf(
      SalePayment(method = "cash", kind = "cash", amountMinor = 30000, status = "confirmed"),
      SalePayment(method = "hawala", kind = "transfer", amountMinor = 27000, status = "confirmed"),
    ),
    tenderedMinor = 40000, changeMinor = 10000,
  )

  /** همان شکلِ `Receipts.input` (بی `Context`): برچسب، «نوع» و ردیف‌های پرداخت از خودِ `Receipts` */
  private fun invoice(): ReceiptDoc {
    val l = Receipts.labels()
    val input = ReceiptInput(
      shop = ShopHeader("سوپرمارکتِ نمونه", "کابل، شهرِ نو", "0799 123 456", ""),
      invoiceNo = "B1-154", dateIso = sale.date, time = "14:35", cashier = "احمد",
      kind = Receipts.kindOf(sale, l), cancelled = false,
      items = listOf(
        LineView("چای سبز", 3.0, "بسته", 150.0, 450.0),
        LineView("برنج", 1.5, "کیلو", 100.0, 150.0, returnedQty = 0.5, listPrice = 120.0),
      ),
      subtotal = 600.0, discounts = listOf(DiscountLine(l.discount, 30.0)), total = 570.0,
      payments = Receipts.paymentLines(sale), paid = 570.0, qrUrl = url, note = "تحویل فردا ساعت 10",
    )
    return ReceiptLayout.build(input, 576, Receipts.format(), l)
  }

  private fun texts(doc: ReceiptDoc) = doc.blocks.map {
    when (it) {
      is ReceiptBlock.Text -> "T:${it.text}"
      is ReceiptBlock.Row -> "R:${it.label}=${it.value}"
      is ReceiptBlock.Item -> "I:${it.name}|${it.detail}|${it.total}|${it.notes.joinToString(";")}"
      is ReceiptBlock.Rule -> if (it.dashed) "--" else "=="
      is ReceiptBlock.Gap -> "gap"
      is ReceiptBlock.Logo -> "logo"
      is ReceiptBlock.Qr -> "qr:${it.url}"
    }
  }

  private val lre = "‪"
  private val pdf = "‬"

  @Test fun `نمونهٔ فاکتور — فارسی، همان فاکتورِ پیشین`() = on(Lang.FA) {
    assertEquals(listOf(
      "T:سوپرمارکتِ نمونه", "T:کابل، شهرِ نو", "T:تلفن: ${lre}0799 123 456$pdf", "--",
      "R:شمارهٔ فاکتور=#B1-154", "R:تاریخ=1405/08/02 — 14:35", "R:فروشنده=احمد", "R:نوع=ترکیبی", "--",
      "I:چای سبز|3 بسته × 150|450|", "I:برنج|1.5 کیلو × 100|150|قیمتِ اصلی: 120;مرجوعی: 0.5", "--",
      "T:تعداد اقلام: 2 ردیف",
      "R:جمع اقلام=600 افغانی", "R:تخفیف=−30 افغانی", "==", "R:مبلغ نهایی=570 افغانی",
      "R:نقد=300 افغانی", "R:حواله=270 افغانی", "R:دریافتی=400 افغانی", "R:پولِ پس=100 افغانی", "--",
      "T:یادداشت: تحویل فردا ساعت 10", "T:برای دیدنِ این خرید اسکن کنید", "gap", "qr:$url", "gap", "T:تشکر از خرید شما",
    ), texts(invoice()))
  }

  @Test fun `نمونهٔ فاکتور — دری، برچسب‌ها از لایهٔ واژه و داده دست‌نخورده`() = on(Lang.PRS) {
    val t = texts(invoice())
    //  همان ترتیب و همان عددهای فارسی؛ فقط برچسب‌ها دری
    assertEquals(listOf(
      "T:سوپرمارکتِ نمونه", "T:کابل، شهرِ نو", "T:${DariText.of("تلفن")}: ${lre}0799 123 456$pdf", "--",
      "R:${DariText.of("شمارهٔ فاکتور")}=#B1-154", "R:${DariText.of("تاریخ")}=1405/08/02 — 14:35", "R:${DariText.of("فروشنده")}=احمد",
      "R:${DariText.of("نوع")}=${DariText.of("ترکیبی")}", "--",
      "I:چای سبز|3 بسته × 150|450|", "I:برنج|1.5 کیلو × 100|150|${DariText.of("قیمتِ اصلی")}: 120;${DariText.of("مرجوعی")}: 0.5", "--",
      "T:${DariText.of("تعداد اقلام")}: 2 ${DariText.of("ردیف")}",
      "R:${DariText.of("جمع اقلام")}=600 افغانی", "R:${DariText.of("تخفیف")}=−30 افغانی", "==", "R:${DariText.of("مبلغ نهایی")}=570 افغانی",
      "R:${DariText.of("نقد")}=300 افغانی", "R:${DariText.of("حواله")}=270 افغانی", "R:${DariText.of("دریافتی")}=400 افغانی",
      "R:${DariText.of("پولِ پس")}=100 افغانی", "--",
      "T:${DariText.of("یادداشت")}: تحویل فردا ساعت 10", "T:${DariText.of("برای دیدنِ این خرید اسکن کنید")}", "gap", "qr:$url", "gap",
      "T:${DariText.of("تشکر از خرید شما")}",
    ), t)
  }

  @Test fun `نمونهٔ فاکتور — انگلیسی، میلادی، AFN و دادهٔ فارسیِ فروشنده دست‌نخورده`() = on(Lang.EN) {
    assertEquals(listOf(
      "T:سوپرمارکتِ نمونه", "T:کابل، شهرِ نو", "T:Phone: ${lre}0799 123 456$pdf", "--",
      "R:Invoice no.=#B1-154", "R:Date=2026-10-24 — 14:35", "R:Cashier=احمد", "R:Type=Mixed", "--",
      "I:چای سبز|3 بسته × 150|450|", "I:برنج|1.5 کیلو × 100|150|List price: 120;Returned: 0.5", "--",
      "T:Items: 2 lines",
      "R:Items total=600 AFN", "R:Discount=−30 AFN", "==", "R:Total=570 AFN",
      "R:Cash=300 AFN", "R:Transfer=270 AFN", "R:Received=400 AFN", "R:Change=100 AFN", "--",
      "T:Note: تحویل فردا ساعت 10", "T:Scan to view this purchase", "gap", "qr:$url", "gap", "T:Thank you for your purchase",
    ), texts(invoice()))
  }

  @Test fun `نمونهٔ فاکتور — پشتو، خورشیدی و افغانۍ`() = on(Lang.PS) {
    assertEquals(listOf(
      "T:سوپرمارکتِ نمونه", "T:کابل، شهرِ نو", "T:ټیلیفون: ${lre}0799 123 456$pdf", "--",
      "R:د بل شمېره=#B1-154", "R:نېټه=1405/08/02 — 14:35", "R:پلورونکی=احمد", "R:ډول=ګډ", "--",
      "I:چای سبز|3 بسته × 150|450|", "I:برنج|1.5 کیلو × 100|150|اصلي بیه: 120;بېرته راګرځېدلي: 0.5", "--",
      "T:د توکو شمېر: 2 قطارونه",
      "R:د توکو ټولګه=600 افغانۍ", "R:تخفیف=−30 افغانۍ", "==", "R:وروستی مبلغ=570 افغانۍ",
      "R:نغد=300 افغانۍ", "R:حواله=270 افغانۍ", "R:ترلاسه شوې=400 افغانۍ", "R:پاتې پیسې=100 افغانۍ", "--",
      "T:یادښت: تحویل فردا ساعت 10", "T:د دې پېرود لیدو لپاره سکن کړئ", "gap", "qr:$url", "gap", "T:له پېرود څخه مو مننه",
    ), texts(invoice()))
  }

  @Test fun `رسیدِ هدیه و صورت‌حساب در انگلیسی — برچسب انگلیسی، دادهٔ فروشنده همان`() = on(Lang.EN) {
    val l = Receipts.labels()
    val gift = GiftReceiptLayout.build(
      ReceiptInput(
        shop = ShopHeader("سوپرمارکتِ نمونه"), invoiceNo = "B1-154", dateIso = "2026-10-24", time = "", cashier = "",
        kind = Receipts.kindOf(sale, l), cancelled = false, items = listOf(LineView("چای سبز", 2.0, "بسته", 150.0, 300.0)),
        subtotal = 300.0, discounts = emptyList(), total = 300.0, payments = emptyList(), paid = 300.0,
      ),
      576, Receipts.format(), Receipts.giftLabels(),
    )
    assertEquals(listOf(
      "T:سوپرمارکتِ نمونه", "--", "T:Gift receipt", "R:Invoice no.=#B1-154", "R:Date=2026-10-24", "--",
      "I:چای سبز|2 بسته||", "--", "T:Items: 1 line · 2 pcs", "--",
      "T:This receipt is for exchange and shows no prices", "T:Thank you for your purchase",
    ), texts(gift))

    val statement = StatementLayout.build(
      StatementInput(
        shop = ShopHeader("سوپرمارکتِ نمونه"), customer = "کریمی", todayIso = "2026-10-24",
        lines = listOf(StatementLine("2026-10-20", "قرضِ آرد", 50_000, 0, 50_000), StatementLine("2026-10-22", "", 0, 20_000, 30_000)),
      ),
      576, Receipts.format(), Receipts.statementLabels(),
    )
    assertEquals(listOf(
      "T:سوپرمارکتِ نمونه", "--", "T:Customer statement", "R:Customer=کریمی", "R:Date=2026-10-24", "--",
      "I:2026-10-20 — قرضِ آرد|Given: 500 AFN|Balance: 500 AFN|", "I:2026-10-22 — Received|Received: 200 AFN|Balance: 300 AFN|", "--",
      "T:2 lines", "R:Total given=500 AFN", "R:Total received=200 AFN", "==", "R:Balance (owing)=300 AFN", "--",
      "T:This statement was made from the shop's ledger",
    ), texts(statement))
  }

  @Test fun `یک ردیف — مفردِ انگلیسی و پشتو، فارسی همان «ردیف»`() {
    fun one(): Pair<String, String> {
      val l = Receipts.labels()
      val doc = ReceiptLayout.build(
        ReceiptInput(
          shop = ShopHeader("دکان"), invoiceNo = "1", dateIso = "2026-10-24", time = "", cashier = "", kind = l.cash, cancelled = false,
          items = listOf(LineView("چای", 1.0, "", 10.0, 10.0)), subtotal = 10.0, discounts = emptyList(), total = 10.0,
          payments = emptyList(), paid = 10.0,
        ),
        576, Receipts.format(), l,
      )
      val st = StatementLayout.build(
        StatementInput(ShopHeader("دکان"), "کریمی", todayIso = "2026-10-24", lines = listOf(StatementLine("2026-10-20", "", 100, 0, 100))),
        576, Receipts.format(), Receipts.statementLabels(),
      )
      val count = texts(doc).first { it.startsWith("T:${l.count}") }
      val rows = texts(st).first { it.startsWith("T:1 ") }
      return count to rows
    }
    on(Lang.FA) { assertEquals("T:تعداد اقلام: 1 ردیف · 1 عدد" to "T:1 ردیف", one()) }
    on(Lang.EN) { assertEquals("T:Items: 1 line · 1 pc" to "T:1 line", one()) }
    on(Lang.PS) { assertEquals("T:د توکو شمېر: 1 قطار · 1 دانه" to "T:1 قطار", one()) }
  }
}
