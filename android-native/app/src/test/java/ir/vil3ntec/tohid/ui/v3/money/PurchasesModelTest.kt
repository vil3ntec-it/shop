package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** «خریدها» (e-rows-63b): متنِ ردیف (اقلام، پرداخت، رسیدن) و گزینه‌های سادهٔ فرمِ «خریدِ تازه» */
class PurchasesModelTest {
  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private val today = "2026-11-15"
  private var n = 0
  private val d0 = ShopData(
    products = listOf("آرد", "روغن", "چای", "برنج", "نمک").mapIndexed { i, name -> Product(id = "p$i", name = name, unit = "کیلو", purchasePrice = 10.0) },
    suppliers = listOf(Supplier(id = "s1", name = "پخش البرز")),
  )

  private fun view(count: Int, paid: Double, due: String? = null): PurchaseViews.View {
    val lines = (0 until count).map { PurchaseBook.Line("p$it", 1.0, 100.0) }
    val d = (PurchaseBook.record(d0, "s1", lines, paid, "2026-11-10", "", today, 1, { "i${n++}" }, dueDate = due) as PurchaseBook.Result.Ok).data
    return PurchaseViews.all(d, today).single()
  }

  @Test fun `اقلام — یکی، دو تا، و «آرد، روغن و ۳ قلمِ دیگر»`() {
    assertEquals("آرد", PurchaseRowText.items(view(1, 0.0)))
    assertEquals("آرد، روغن", PurchaseRowText.items(view(2, 0.0)))
    assertEquals("آرد، روغن و 3 قلمِ دیگر", PurchaseRowText.items(view(5, 0.0)))
    val gone = view(1, 0.0).let { it.copy(lines = it.lines.map { l -> l.copy(name = "") }) }
    assertEquals(StrMoney.deletedProduct, PurchaseRowText.items(gone))
  }

  @Test fun `حالِ پرداخت و رسیدن به متنِ آرام، رنگ فقط برای آنچه توجه می‌خواهد`() {
    val paid = PurchaseRowText.of(view(2, 200.0), today)
    assertEquals("پخش البرز", paid.title)
    assertEquals(StrPurchases.paid, paid.pay?.text)
    assertEquals(V3RowTone.QUIET, paid.pay?.tone)
    assertEquals(StrPurchases.received, paid.receipt?.text)
    assertEquals(V3RowTone.QUIET, paid.receipt?.tone)
    assertTrue(paid.subtitle.endsWith("آرد، روغن"))

    val part = PurchaseRowText.of(view(5, 300.0), today)
    assertEquals(StrPurchases.partly(money(200.0)), part.pay?.text)
    assertEquals(V3RowTone.ATTENTION, part.pay?.tone)
    assertEquals(MoneyText.afn(500.0), part.amount)

    val credit = PurchaseRowText.of(view(1, 0.0, due = "2026-11-28"), today)
    assertTrue(credit.pay!!.text.startsWith(StrPurchases.credit + " — "))
    val late = PurchaseRowText.of(view(1, 0.0, due = "2026-11-12"), today)
    assertEquals(StrPurchases.overdue(money(100.0)), late.pay?.text)
    assertEquals(V3RowTone.DANGER, late.pay?.tone)

    val order = view(1, 0.0).copy(kind = PurchaseViews.Kind.ORDER, receipt = PurchaseViews.Receipt.PENDING, pay = PurchaseViews.Pay.NONE, no = 41)
    val o = PurchaseRowText.of(order, today)
    assertNull(o.pay)
    assertEquals(StrPurchases.notReceived, o.receipt?.text)
    assertEquals(V3RowTone.ATTENTION, o.receipt?.tone)
    assertTrue(o.subtitle.contains(StrPurchases.orderNo(41)))
    val half = PurchaseRowText.of(order.copy(receipt = PurchaseViews.Receipt.PARTIAL), today)
    assertEquals(StrPurchases.partReceived, half.receipt?.text)
  }

  @Test fun `نوشتهٔ کادرِ دوره`() {
    assertEquals(StrPurchases.allTime, PurchaseRowText.period(null, today, gregorian = false))
    assertEquals(ir.vil3ntec.tohid.core.i18n.Str.today, PurchaseRowText.period("day:$today", today, gregorian = false))
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrHistory.thisMonth, PurchaseRowText.period("m:1405-08", today, gregorian = false))
    assertEquals("میزان 1405", PurchaseRowText.period("m:1405-07", today, gregorian = false))
  }

  @Test fun `فرمِ تازه — پرداختِ کامل پیش‌فرض، بخشی تایپی، بعداً با سررسید حتی بی موتورِ تازه`() {
    var f = PurchaseFormModel.initial("s1", today).copy(productId = "p0", quantity = "4", price = "250")
    assertEquals(PurchasePay.FULL, f.pay)
    assertEquals(1000.0, PurchaseFormModel.paid(f), 0.0)
    assertEquals(0.0, PurchaseFormModel.remaining(f), 0.0)
    assertFalse(PurchaseFormModel.typesPaid(f))
    assertFalse(PurchaseFormModel.dueShown(f, extrasOn = false))
    assertEquals(1000.0, PurchaseFormModel.draft(f, "کیلو").paidAmount, 0.0)

    f = PurchaseFormModel.setPay(f, PurchasePay.PART).copy(paid = "300")
    assertTrue(PurchaseFormModel.typesPaid(f))
    assertEquals(700.0, PurchaseFormModel.remaining(f), 0.0)
    assertTrue(PurchaseFormModel.dueShown(f, extrasOn = false))

    f = PurchaseFormModel.setPay(f, PurchasePay.LATER).copy(due = "1405/09/10")
    assertEquals("", f.paid)
    assertEquals(0.0, PurchaseFormModel.paid(f), 0.0)
    assertEquals(1000.0, PurchaseFormModel.remaining(f), 0.0)
    assertFalse(PurchaseFormModel.errors(f).any)
    assertTrue(PurchaseFormModel.dueShown(f, extrasOn = false))
    //  سررسید با «بعداً» به فاکتور می‌رود، هزینهٔ جانبی فقط با موتورِ تازه
    val ex = PurchaseFormModel.extras(f, on = false)
    assertEquals(PurchaseFormModel.dueIso(f), ex.dueDate)
    assertEquals(0.0, ex.landedTotal, 0.0)

    //  «همه» سررسید را پاک می‌کند
    assertEquals("", PurchaseFormModel.setPay(f, PurchasePay.FULL).due)
    //  کادرِ پیشین (pay = null): همان «خالی = نسیه»، سررسید فقط با موتورِ تازه
    val legacy = f.copy(pay = null, paid = "")
    assertEquals(0.0, PurchaseFormModel.paid(legacy), 0.0)
    assertNull(PurchaseFormModel.extras(legacy, on = false).dueDate)
  }

  @Test fun deferredDeliveryRetainsCostsDueDatesAndEveryLine() {
    val form = PurchaseFormModel.initial("s1", today).copy(
      productId = "p0", quantity = "4", price = "250", due = "1405/09/10", landed = "50", later = true,
      expiry = "2027-02-01", packaging = PurchasePackaging(production = "2026-10-01"),
      more = listOf(PurchaseRow(productId = "p1", quantity = "2", price = "90")),
    )
    val credit = PurchaseFormModel.setPay(form, PurchasePay.LATER)
    val extras = PurchaseFormModel.extras(credit, on = true)
    assertTrue(extras.awaitDelivery)
    assertEquals(50.0, extras.landedTotal, 0.0)
    assertEquals(PurchaseFormModel.dueIso(form), extras.dueDate)
    val lines = PurchaseFormModel.lines(credit) { "عدد" }
    assertEquals(listOf("p0", "p1"), lines.map { it.productId })
    assertEquals(listOf(4.0, 2.0), lines.map { it.quantity })
    assertEquals("2027-02-01", lines.first().expiryDate)
    assertEquals("2026-10-01", lines.first().productionDate)
    assertEquals(1180.0, PurchaseFormModel.remaining(credit), 0.0)
  }
}
