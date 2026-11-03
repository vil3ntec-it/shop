package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پولِ نسیه همیشه صاحب دارد (صاحب‌کار ۱۴۰۵/۰۸/۱۳، `CreditDebtor`) — روی خودِ ثبت (`SalesEngine.record`)، پس هر صندوقی که
 *  از آن می‌گذرد: گوشیِ نسخهٔ ۳ و فروشِ سریع (`TillSale.record`)، صندوقِ کامپیوتر (`DeskSale` ⇐ `SalesEngine.record`)،
 *  آیفون (همان کد) و پنلِ ترکیبی (`PayFlow` ⇐ `settlement`).
 *
 *  روی main: نسیه به مشتریِ **ادغام‌شده** ثبت می‌شد (برگهٔ «انتخابِ مشتری» ِ گوشی ادغام‌شده‌ها را هم نشان می‌داد) و بدهی
 *  به مشتری‌ای می‌رفت که در هیچ فهرستی نیست — این آزمون‌ها آن‌جا سرخ‌اند.
 */
class CreditDebtorTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "عدد", purchasePrice = 200.0, salePrice = 300.0)
  private val keeper = Debtor(id = "d1", name = "احمد رضایی")
  private val merged = Debtor(id = "d2", name = "احمد", mergedInto = "d1")
  private val ledger = ShopData(
    products = listOf(rice),
    debtors = listOf(keeper, merged),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0)),
  )
  private val cart = listOf(SalesEngine.CartLine("p1", 2.0)) // ۶۰۰ افغانی
  private var seq = 0
  private fun newId() = "t${seq++}"

  private fun record(checkout: SalesEngine.Checkout, d: ShopData = ledger) =
    SalesEngine.record(d, cart, checkout, today = "2026-11-03", now = 1_800_000_000_000, newId = ::newId)

  /** گوشیِ نسخهٔ ۳ و فروشِ سریع همین زنجیره را می‌روند (`CheckoutRoute` ⇐ `TillSale.record`) */
  private fun phone(checkout: SalesEngine.Checkout) =
    TillSale.record(ledger, cart, checkout, null, null, null, "2026-11-03", 1_800_000_000_000, ::newId, saleId = "s1")

  private fun failed(r: SalesEngine.Result): String {
    assertTrue("انتظار می‌رفت رد شود، ولی ثبت شد: $r", r is SalesEngine.Result.Failed)
    return (r as SalesEngine.Result.Failed).message
  }

  private fun ok(r: SalesEngine.Result): SalesEngine.Result.Ok {
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is SalesEngine.Result.Ok)
    return r as SalesEngine.Result.Ok
  }

  private val credit = SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0)
  private val mergedText = "«احمد» با «احمد رضایی» یکی شده است؛ برای نسیه «احمد رضایی» را انتخاب کنید"

  /** پنلِ ترکیبی: نقد + باقی «به حسابِ» [customer] (همان `PayFlow.checkout` ِ صندوق‌ها) */
  private fun split(cash: String, customer: String?): Settlement =
    (PayFlow.checkout(PayFlow.Draft(600.0, cash = cash, customerId = customer)) as PayFlow.Outcome.Ok).value

  @Test
  fun `نسیهٔ کامل بی مشتری در هر صندوق رد می‌شود و هیچ چیزی نوشته نمی‌شود`() {
    assertEquals("برای فروش نسیه، قرض‌دار را انتخاب کنید", failed(record(credit)))
    assertEquals("برای فروش نسیه، قرض‌دار را انتخاب کنید", failed(phone(credit)))
    assertEquals("برای فروش نسیه، قرض‌دار را انتخاب کنید", failed(record(credit.copy(debtorId = "  "))))
  }

  @Test
  fun `نسیهٔ کامل به مشتریِ ادغام‌شده رد می‌شود و مشتریِ ماندگار را نام می‌برد`() {
    assertEquals(mergedText, failed(record(credit.copy(debtorId = "d2"))))
    assertEquals(mergedText, failed(phone(credit.copy(debtorId = "d2"))))
  }

  @Test
  fun `نسیهٔ بخشی، نقد و نسیه، فقط با مشتریِ فعال`() {
    val part = credit.copy(paidAmount = 250.0)
    assertEquals("برای فروش نسیه، قرض‌دار را انتخاب کنید", failed(record(part)))
    assertEquals(mergedText, failed(record(part.copy(debtorId = "d2"))))
    val r = ok(record(part.copy(debtorId = "d1")))
    val sale = r.data.sales.single()
    assertEquals("d1", sale.debtorId)
    assertEquals(350.0, sale.debtGiven, 0.0)
    assertEquals(350.0, r.data.transactions.single { it.debtorId == "d1" }.amount, 0.0)
  }

  @Test
  fun `پرداختِ ترکیبی با باقیِ «به حساب» فقط به مشتریِ فعال می‌رود`() {
    //  باقیِ ۴۰۰ «به حسابِ» ادغام‌شده: روی main ثبت می‌شد
    val toMerged = split("200", "d2")
    assertEquals(40_000L, toMerged.toAccount)
    assertEquals(mergedText, failed(record(SalesEngine.Checkout(settlement = toMerged, debtorId = "d2"))))
    //  تسویهٔ «به حساب» با مشتری ولی فاکتورِ بی مشتری (پنل و فرم ناهمخوان)
    assertEquals("برای فروش نسیه، قرض‌دار را انتخاب کنید", failed(record(SalesEngine.Checkout(settlement = split("200", "d1")))))
    val r = ok(record(SalesEngine.Checkout(settlement = split("200", "d1"), debtorId = "d1")))
    assertEquals(400.0, r.data.sales.single().debtGiven, 0.0)
  }

  @Test
  fun `پرداختِ ترکیبیِ ناقص بی مشتری نه از پنل می‌گذرد نه از ثبت`() {
    val short = PayFlow.checkout(PayFlow.Draft(600.0, cash = "200"))
    assertTrue(short is PayFlow.Outcome.Bad && short.problem.code == "SHORT")
    val forged = (PayFlow.preview(PayFlow.Draft(600.0, cash = "200")) as PayFlow.Outcome.Ok).value
    assertEquals("short", forged.status)
    assertTrue(record(SalesEngine.Checkout(settlement = forged)) is SalesEngine.Result.Failed)
  }

  @Test
  fun `مشتریِ ناپیدا رد می‌شود`() {
    assertEquals("قرض‌دار پیدا نشد", failed(record(credit.copy(debtorId = "gone"))))
  }

  @Test
  fun `نقدِ کامل همان امروز است — مشتری اختیاری، ادغام‌شده هم فقط نام`() {
    ok(record(SalesEngine.Checkout()))
    assertEquals("d2", ok(record(SalesEngine.Checkout(debtorId = "d2"))).data.sales.single().debtorId)
    //  «نسیه» ای که کامل پرداخت شده بدهی ندارد ⇒ مشتری لازم نیست (همان امروز)
    assertTrue(ok(record(credit.copy(paidAmount = 600.0))).data.transactions.isEmpty())
    ok(record(SalesEngine.Checkout(settlement = split("600", null))))
  }

  @Test
  fun `قاعدهٔ صندوق — پیامِ کنارِ «ثبت» فقط وقتی سهمِ به‌حساب صاحبِ درست ندارد`() {
    assertNull(CreditDebtor.tillBlock(ledger, null, 0, split = false))
    assertNull(CreditDebtor.tillBlock(ledger, "d1", 60_000, split = false))
    assertEquals(CreditDebtor.Issue.MISSING, CreditDebtor.issue(ledger, null))
    assertEquals(CreditDebtor.Issue.MERGED, CreditDebtor.issue(ledger, "d2"))
    assertEquals(CreditDebtor.Issue.UNKNOWN, CreditDebtor.issue(ledger, "gone"))
    assertTrue(CreditDebtor.tillBlock(ledger, null, 60_000, split = false)!!.isNotBlank())
    assertTrue(CreditDebtor.tillBlock(ledger, null, 40_000, split = true)!!.contains("400"))
    assertTrue(CreditDebtor.tillBlock(ledger, "d2", 60_000, split = false)!!.contains("احمد رضایی"))
    assertEquals("d1", CreditDebtor.keeperOf(ledger, "d2"))
    //  پرداختِ ساده: فقط «نسیه» و فقط باقی‌مانده
    assertEquals(0L, CreditDebtor.plainOwed(false, 600.0))
    assertEquals(35_000L, CreditDebtor.plainOwed(true, 350.0))
    assertEquals(40_000L, CreditDebtor.splitOwed((PayFlow.preview(PayFlow.Draft(600.0, cash = "200")) as PayFlow.Outcome.Ok).value))
  }
}
