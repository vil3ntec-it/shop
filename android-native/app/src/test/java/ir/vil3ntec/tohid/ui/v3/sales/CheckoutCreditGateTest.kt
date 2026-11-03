package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.CreditDebtor
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerPickerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  «ثبت» ِ صندوق با نسیهٔ بی مشتری خاموش است و پیامِ روشن با «انتخابِ مشتری» دارد (صاحب‌کار ۱۴۰۵/۰۸/۱۳، `CreditDebtor`):
 *  گوشیِ نسخهٔ ۳ و فروشِ سریع (همان برگهٔ `sale/checkout`، `CheckoutModel`)، صندوقِ کامپیوتر (`DeskSale`، پنلِ
 *  `PayPanelDialog`) و آیفون (همان کدِ مشترک). نسیه، نسیهٔ بخشی و پرداختِ ترکیبی.
 *  روی main: برگهٔ «انتخابِ مشتری» ِ گوشی ادغام‌شده‌ها را هم نشان می‌داد و «ثبت» هیچ‌گاه به خاطرِ مشتری خاموش نبود.
 */
class CheckoutCreditGateTest {

  /** «محمود» در «احمد کریمی» ادغام شده (CRM-10) */
  private val d = ledger.copy(debtors = ledger.debtors.map { if (it.id == "d2") it.copy(mergedInto = "d1") else it })
  private val cart = listOf(CartLine("soap", 2.0), CartLine("rice", 1.5)) // ۱۰۰ + ۱۸۰ = ۲۸۰
  private val split = PayFlow.Draft(280.0, cash = "100")

  private fun block(payment: SalesEngine.Payment, debtor: String?, paid: String = "0", payV2: Boolean = false, draft: PayFlow.Draft = split): String? {
    val form = CheckoutModel.start(null, d.debtors).let { CheckoutModel.withPayment(it, payment).copy(debtorId = debtor, paidText = paid) }
    val n = CheckoutModel.numbers(d, cart, form, CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = ""))
    val preview = if (payV2) PayFlow.preview(draft.copy(customerId = debtor)).valueOrNull() else null
    return CheckoutModel.creditBlock(d, form, n, payV2, preview)
  }

  @Test
  fun `گوشی — نسیه بی مشتری «ثبت» را خاموش می‌کند`() {
    val m = block(SalesEngine.Payment.CREDIT, null)
    assertNotNull(m)
    assertFalse(CheckoutModel.canSubmit(false, null, null, DiscountGate.State.FREE, m))
    assertTrue(CheckoutModel.canSubmit(false, null, null, DiscountGate.State.FREE, block(SalesEngine.Payment.CREDIT, "d1")))
  }

  @Test
  fun `گوشی — نسیهٔ بخشی بی مشتری خاموش، نسیهٔ کامل‌پرداخت و نقد آزاد`() {
    assertNotNull(block(SalesEngine.Payment.CREDIT, null, paid = "100"))
    assertNull(block(SalesEngine.Payment.CREDIT, null, paid = "280"))
    assertNull(block(SalesEngine.Payment.CASH, null))
    assertNull(block(SalesEngine.Payment.CASH, "d2"))
  }

  @Test
  fun `گوشی — مشتریِ ادغام‌شده یا ناپیدا برای نسیه پذیرفته نیست و نامِ ماندگار را می‌گوید`() {
    assertTrue(block(SalesEngine.Payment.CREDIT, "d2")!!.contains("احمد کریمی"))
    assertNotNull(block(SalesEngine.Payment.CREDIT, "gone"))
  }

  @Test
  fun `پرداختِ ترکیبی — باقیِ پرداخت‌نشده بی مشتری خاموش، کامل یا «به حسابِ» مشتریِ فعال آزاد`() {
    assertTrue(block(SalesEngine.Payment.CASH, null, payV2 = true)!!.contains("180"))
    assertNull(block(SalesEngine.Payment.CASH, "d1", payV2 = true))
    assertNotNull(block(SalesEngine.Payment.CASH, "d2", payV2 = true))
    assertNull(block(SalesEngine.Payment.CASH, null, payV2 = true, draft = PayFlow.Draft(280.0, cash = "300")))
  }

  @Test
  fun `برگهٔ «انتخابِ مشتری» ادغام‌شده را نشان نمی‌دهد و فاکتورِ نگه‌داشته با او نسیه باز نمی‌شود`() {
    assertEquals(listOf("d1", "d3"), CustomerPickerModel.rows(d, "").map { it.id })
    assertTrue(CustomerPickerModel.rows(d, "محمود").isEmpty())
    assertEquals(SalesEngine.Payment.CASH, CheckoutModel.start("d2", d.debtors).payment)
    assertEquals(SalesEngine.Payment.CREDIT, CheckoutModel.start("d1", d.debtors).payment)
    val onlyMerged = d.copy(debtors = listOf(Debtor(id = "x", name = "x", mergedInto = "d1")))
    assertEquals(CustomerPickerModel.emptyText(ledger.copy(debtors = emptyList())), CustomerPickerModel.emptyText(onlyMerged))
  }

  @Test
  fun `کامپیوتر — همان قاعده برای F9 و پنلِ پرداخت`() {
    //  `DeskSale`: «نسیه» با پرداختیِ ۱۰۰ از ۲۸۰، بی مشتری ⇒ پیام؛ با مشتریِ فعال ⇒ آزاد
    val owed = CreditDebtor.plainOwed(true, 280.0 - 100.0)
    assertNotNull(CreditDebtor.tillBlock(d, null, owed, split = false))
    assertNull(CreditDebtor.tillBlock(d, "d1", owed, split = false))
    //  `PayPanelDialog`: باقیِ پنل بی مشتری ⇒ پیام
    val s = PayFlow.preview(split).valueOrNull()
    assertNotNull(CreditDebtor.tillBlock(d, null, CreditDebtor.splitOwed(s), split = true))
    //  سیم‌کشی: هر دو صندوق از همین قاعده می‌خوانند و «ثبت» را با آن خاموش می‌کنند
    val src = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui")
    val desk = File(src, "screens/DeskSale.kt").readText()
    assertTrue(desk.contains("CreditDebtor.tillBlock(") && desk.contains("canPost = cart.isNotEmpty() && deskCredit == null"))
    val panel = File(src, "components/pos/PayPanel.kt").readText()
    assertTrue(panel.contains("CreditDebtor.tillBlock(") && panel.contains("enabled = !busy && creditBlock == null"))
    val phone = File(src, "v3/sales/checkout/CheckoutRoute.kt").readText()
    assertTrue(phone.contains("CheckoutModel.creditBlock(") && phone.contains("canSubmit = CheckoutModel.canSubmit(busy, discountError, paidError, gate, creditBlock)"))
  }

  private companion object {
    val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
  }
}
