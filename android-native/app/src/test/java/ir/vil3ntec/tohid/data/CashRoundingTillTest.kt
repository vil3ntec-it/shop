package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrRounding
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.ShiftRow
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-09 — گردکردنِ نقدی در صندوق، از ثبت تا صندوق، گزارش و برگشت: پلهٔ خاموش همان فاکتورِ امروز؛ نقدِ کامل
 *  و سهمِ نقدِ ترکیبی ردیفِ جدای `roundingMinor` می‌گیرند و مبلغِ نهایی همان پرداختی است؛ برگشت هرگز بیش از آن.
 */
class CashRoundingTillTest {

  private val a = Product(id = "p1", name = "الف", unit = "عدد", purchasePrice = 30.0, salePrice = 41.0)
  private val b = Product(id = "p2", name = "ب", unit = "عدد", purchasePrice = 60.0, salePrice = 126.0)
  private val c = Product(id = "p3", name = "ج", unit = "عدد", purchasePrice = 0.5, salePrice = 1.0)
  private val ahmad = Debtor(id = "d1", name = "احمد")

  private fun ledger() = ShopData(
    products = listOf(a, b, c),
    debtors = listOf(ahmad),
    warehouseEntries = listOf("p1", "p2", "p3").mapIndexed { i, p -> WarehouseEntry(id = "w$i", productId = p, units = 100.0) },
  )

  private var seq = 0
  private fun newId(): String = "t${seq++}"

  private fun sell(cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout): Pair<ShopData, Sale> {
    seq = 0
    val r = SalesEngine.record(ledger(), cart, checkout, today = "2026-11-02", now = 1_700_000_000_000, newId = ::newId)
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is SalesEngine.Result.Ok)
    val d = (r as SalesEngine.Result.Ok).data
    return d to d.sales.single()
  }

  private val threeA = listOf(SalesEngine.CartLine("p1", 3.0))
  private val json = Json { encodeDefaults = true }

  @Test
  fun `پلهٔ خاموش یا نادرست همان فاکتورِ امروز است — بی ردیفِ گرد و بایت‌به‌بایت همان`() {
    val (_, today) = sell(threeA, SalesEngine.Checkout())
    assertEquals(123.0, today.finalTotal, 0.0)
    assertNull(today.roundingMinor)
    val text = json.encodeToString(Sale.serializer(), today)
    assertFalse(text.contains("roundingMinor"))
    for (step in listOf(0L, 250L, -500L, 700L)) {
      val (_, s) = sell(threeA, SalesEngine.Checkout(cashRounding = step))
      assertEquals("پله $step", text, json.encodeToString(Sale.serializer(), s))
    }
  }

  @Test
  fun `نقدِ کامل با پلهٔ ۵ و ۱۰ ردیفِ جدا می‌گیرد و صندوق و گزارش همان پرداختی را می‌شمارند`() {
    val (d5, s5) = sell(threeA, SalesEngine.Checkout(cashRounding = 500))
    assertEquals(123.0, s5.total, 0.0)
    assertEquals(0.0, s5.discount, 0.0)
    assertEquals(200L, s5.roundingMinor)
    assertEquals(125.0, s5.finalTotal, 0.0)
    assertEquals(125.0, s5.paidAmount, 0.0)
    assertEquals(0.0, s5.remaining, 0.0)
    assertEquals(125.0, CashShift.saleCash(s5), 0.0)
    assertEquals(listOf(ShiftRow(PayFlow.CASH, 12_500L)), PayFlow.saleRows(s5))
    assertEquals(2.0, PayFlow.rounding(s5)!!, 0.0)
    val r = ReportEngine.sales(d5, "2026-11-02", "2026-11-02")
    assertEquals(2.0, r.rounding, 0.0)
    assertEquals(r.gross - r.discount + r.rounding, r.net, 0.0)
    //  پیش‌نمایشِ صندوقِ ساده همان حسابِ ثبت است
    assertEquals(2.0, PayFlow.cashRounding(123.0, 500L), 0.0)

    val (_, s10) = sell(threeA, SalesEngine.Checkout(cashRounding = 1_000))
    assertEquals(-300L, s10.roundingMinor)
    assertEquals(120.0, s10.finalTotal, 0.0)
    assertEquals(120.0, CashShift.saleCash(s10), 0.0)
  }

  @Test
  fun `نسیه گرد نمی‌شود و ترکیبی فقط سهمِ نقدی را که فاکتور را می‌بندد`() {
    val (_, credit) = sell(threeA, SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 50.0, debtorId = "d1", cashRounding = 500))
    assertNull(credit.roundingMinor)
    assertEquals(123.0, credit.finalTotal, 0.0)
    assertEquals(73.0, credit.remaining, 0.0)

    //  ۱۲۳ = کیف پول ۱۰۰ + نقد؛ سهمِ نقد ۲۳ ⇒ ۲۵، مشتری ۳۰ داد
    val draft = PayFlow.Draft(123.0, cash = "30", rows = listOf(PayFlow.DraftRow("hesabpay", ref = "R1", amount = "100")), cashRounding = 500)
    val st = (PayFlow.checkout(draft) as PayFlow.Outcome.Ok).value
    assertEquals(200L, st.rounding)
    val (_, mixed) = sell(threeA, SalesEngine.Checkout(settlement = st))
    assertEquals(200L, mixed.roundingMinor)
    assertEquals(125.0, mixed.finalTotal, 0.0)
    assertEquals(125.0, mixed.paidAmount, 0.0)
    assertEquals(500L, mixed.changeMinor)
    assertEquals(25.0, CashShift.saleCash(mixed), 0.0)
    assertEquals(12_500L, mixed.payments!!.sumOf { it.amountMinor })

    //  بی پله، همان تسویهٔ امروز
    val plain = (PayFlow.checkout(draft.copy(cashRounding = 0)) as PayFlow.Outcome.Ok).value
    assertEquals(0L, plain.rounding)
    assertNull(sell(threeA, SalesEngine.Checkout(settlement = plain)).second.roundingMinor)
  }

  @Test
  fun `برگشتِ فاکتورِ گردشده هرگز بیش از پرداختی نیست و برگشتِ کامل دقیقاً همان است`() {
    //  گردِ مثبت: ۴۱ × ۳ = ۱۲۳ ⇒ ۱۲۵؛ سه برگشتِ یکی‌یکی ⇒ ۴۱ + ۴۱ + ۴۳
    var (d, sale) = sell(threeA, SalesEngine.Checkout(cashRounding = 500))
    val item = d.saleItems.single { it.saleId == sale.id }
    repeat(3) { k ->
      val r = SalesEngine.recordReturn(d, sale.id, mapOf(item.id to 1.0), "", "2026-11-02", 1_700_000_100_000 + k, ::newId)
      d = (r as SalesEngine.Result.Ok).data
    }
    assertEquals(listOf(41.0, 41.0, 43.0), d.saleReturns.filter { it.saleId == sale.id }.map { it.amount })
    //  همان فاکتور با پنجرهٔ برگشت (`ReturnFlow`): یک‌جا ⇒ همان ۱۲۵
    val (fresh, s2) = sell(threeA, SalesEngine.Checkout(cashRounding = 500))
    val all = fresh.saleItems.filter { it.saleId == s2.id }.associate { it.id to ReturnFlow.Pick(it.quantity) }
    assertEquals(125.0, (ReturnFlow.quote(fresh, s2.id, all) as ReturnFlow.Outcome.Ok).value.total, 0.0)

    //  گردِ منفی: ۱۲۶ + ۱ = ۱۲۷ ⇒ ۱۲۵؛ برگشتِ ۱۲۶ فقط ۱۲۵، بعد قلمِ ۱ ⇒ ۰ (پولِ نگرفته بیرون نمی‌رود)
    var (dn, neg) = sell(listOf(SalesEngine.CartLine("p2", 1.0), SalesEngine.CartLine("p3", 1.0)), SalesEngine.Checkout(cashRounding = 500))
    assertEquals(-200L, neg.roundingMinor)
    val items = dn.saleItems.filter { it.saleId == neg.id }.associateBy { it.productId }
    val partial = mapOf(items.getValue("p2").id to ReturnFlow.Pick(1.0))
    assertEquals(125.0, (ReturnFlow.quote(dn, neg.id, partial) as ReturnFlow.Outcome.Ok).value.total, 0.0)
    for (p in listOf("p2", "p3")) {
      dn = (SalesEngine.recordReturn(dn, neg.id, mapOf(items.getValue(p).id to 1.0), "", "2026-11-02", 1_700_000_200_000, ::newId) as SalesEngine.Result.Ok).data
    }
    val back = dn.saleReturns.filter { it.saleId == neg.id }.map { it.amount }
    assertEquals(listOf(125.0, 0.0), back)
    assertEquals(neg.finalTotal, back.sum(), 0.0)
  }

  @Test
  fun `فاکتورِ بی گرد همان برگشتِ امروز را دارد`() {
    var (d, sale) = sell(threeA, SalesEngine.Checkout())
    val item = d.saleItems.single { it.saleId == sale.id }
    repeat(3) { k ->
      d = (SalesEngine.recordReturn(d, sale.id, mapOf(item.id to 1.0), "", "2026-11-02", 1_700_000_100_000 + k, ::newId) as SalesEngine.Result.Ok).data
    }
    assertEquals(listOf(41.0, 41.0, 41.0), d.saleReturns.filter { it.saleId == sale.id }.map { it.amount })
  }

  @Test
  fun `برگهٔ تسویهٔ نسخهٔ ۳ و فاکتورِ ثبت‌شده همان ردیفِ گرد و همان مبلغ را نشان می‌دهند`() {
    val form = CheckoutModel.start(null, emptyList())
    val co = CheckoutModel.checkout(form, canDiscount = false, blockExpired = false, seller = "", cashRounding = 500)
    val n = CheckoutModel.numbers(ledger(), threeA, form, co)
    assertEquals(2.0, n.rounding, 0.0)
    assertEquals(125.0, n.payable, 0.0)
    assertEquals(125.0, n.paid, 0.0)
    assertEquals(0.0, n.remaining, 0.0)
    val rows = CheckoutModel.rows(n, null, payV2 = false)
    assertEquals(listOf(StrSale.itemsTotal, StrRounding.line, StrSale.finalAmount, StrSale.paid, StrSale.remaining), rows.map { it.label })
    assertEquals(125.0, rows.single { it.strong }.amount, 0.0)
    //  خاموش ⇒ همان ردیف‌های امروز
    val off = CheckoutModel.numbers(ledger(), threeA, form, CheckoutModel.checkout(form, false, false, ""))
    assertEquals(123.0, off.payable, 0.0)
    assertEquals(listOf(StrSale.itemsTotal, StrSale.finalAmount, StrSale.paid, StrSale.remaining), CheckoutModel.rows(off, null, payV2 = false).map { it.label })
    //  فاکتورِ ثبت‌شده: «گردکردنِ نقدی» میانِ تخفیف و مبلغِ نهایی
    val (_, sale) = sell(threeA, co)
    val shown = ReceiptModel.totals(sale)
    assertEquals(2.0, shown.single { it.label == StrRounding.line }.amount, 0.0)
    assertEquals(125.0, shown.single { it.label == StrSale.finalAmount }.amount, 0.0)
    assertTrue(ReceiptModel.totals(sell(threeA, SalesEngine.Checkout()).second).none { it.label == StrRounding.line })
  }
}
