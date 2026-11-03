package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.gateway.GatewayPaymentRow
import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.HesabTill
import ir.vil3ntec.tohid.core.gateway.IntentStatus
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.PaymentIntent
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.nfc.NdefType4Tag
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.pay.HceSlot
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.checkout.HesabTillModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «پرداخت با حساب‌پی (NFC/QR)» ِ صندوقِ گوشیِ نسخهٔ ۳ (`HesabTillRoute`): مبلغ، برچسبِ NFC (مبلغ، کد، گیرنده)، تأیید فقط با
 *  «پول رسید» ِ فروشنده («ساده») یا قصدِ همه‌خوانِ سرور («خودکار»)، و تسویه‌ای که همان `SalesEngine.record` می‌پذیرد —
 *  حتی وقتی فرم «نسیه» بی مشتری بود (کلِ فاکتور پرداخت شده، سهمِ به‌حسابی نمانده).
 *  دستگاه‌های واقعی (NFC ِ دو گوشی، برنامهٔ حساب‌پی) آزموده نشده‌اند.
 */
class HesabTillModelTest {

  private val fp = "hp1_0123456789abcdef"
  private val payee = Payee("دکانِ رحیم", "4321", fp)
  private val simple = TillHesab(false, HesabPayShop.SIMPLE, SimpleAccount("0700123456", ""), payee, flag = true)
  private val auto = TillHesab(true, HesabPayShop.AUTO, null, payee)
  private val cart = listOf(CartLine("soap", 2.0), CartLine("rice", 1.5)) // ۲۸۰ افغانی
  private fun ask(till: TillHesab, draft: PayFlow.Draft? = null) = HesabTillModel.Ask("sale-1", 280.0, draft, till) { null }

  private fun intent(status: IntentStatus, amount: Long = 28_000, payeeFp: String = fp) = PaymentIntent(
    id = "pi_1", kind = "pos_sale", refId = "sale-1", status = status, amountMinor = amount, currency = "AFN",
    checkoutUrl = "https://pay.hesab.example/s/1", qrPayload = "https://pay.hesab.example/s/1", expiresAt = null, paidAt = null,
    providerRef = "TX-1", needsReview = false,
    payment = if (status == IntentStatus.PAID) GatewayPaymentRow("hesabpay", "wallet", amount, "TX-1", "confirmed", "gateway", "pi_1", payeeFp) else null,
    payeeFp = payeeFp,
  )

  private fun text(file: ByteArray): String = file.decodeToString()

  @Test
  fun `ساده — برچسب مبلغ، کد و گیرنده را دارد و فقط «پول رسید» تأیید می‌کند`() {
    val a = ask(simple)
    assertEquals(HesabTill.Mode.SIMPLE, a.mode)
    assertEquals(28_000L, a.amountMinor)
    val s = HesabTillModel.start(a, now = 0)!!
    val tag = HesabTillModel.tag(a, s)!!
    val t = text(tag)
    assertTrue(t, t.contains("280 AFN") && t.contains(a.ref) && t.contains("دکانِ رحیم") && t.contains("••4321"))
    //  اعلان یا هر رویدادِ درگاهی چیزی را نمی‌بندد
    val ignored = HesabTill.step(s, HesabTill.Event.Gateway(intent(IntentStatus.PAID)))
    assertNull(HesabTillModel.settle(a, ignored))
    val ok = HesabTill.step(ignored, HesabTill.Event.SellerConfirm)
    val st = HesabTillModel.settle(a, ok)!!
    val row = st.payments.single { it.method == "hesabpay" }
    assertEquals("cashier", row.verification)
    assertEquals(fp, row.payee)
    assertEquals(a.ref, row.ref)
    //  پس از تأیید برچسب خاموش
    assertNull(HesabTillModel.tag(a, ok))
  }

  @Test
  fun `خودکار — بی قصد برچسب نیست؛ با قصد نشانیِ پرداخت؛ تأیید فقط با قصدِ همه‌خوان`() {
    val a = ask(auto)
    assertEquals(HesabTill.Mode.AUTO, a.mode)
    val s0 = HesabTillModel.start(a, now = 0)!!
    assertNull(HesabTillModel.tag(a, s0))
    val s1 = HesabTill.step(s0, HesabTill.Event.Gateway(intent(IntentStatus.PENDING)))
    assertEquals("https://pay.hesab.example/s/1", HesabTillModel.link(a, s1))
    val tag = HesabTillModel.tag(a, s1)!!
    assertTrue(text(tag).contains("pay.hesab.example/s/1") && text(tag).contains(a.ref))
    //  «پول رسید» در «خودکار» هیچ
    assertNull(HesabTillModel.settle(a, HesabTill.step(s1, HesabTill.Event.SellerConfirm)))
    //  گیرندهٔ دیگر ⇒ بررسی، نه ثبت
    val wrong = HesabTill.step(s1, HesabTill.Event.Gateway(intent(IntentStatus.PAID, payeeFp = "hp1_thief")))
    assertTrue(wrong is HesabTill.State.Review)
    assertNull(HesabTillModel.settle(a, wrong))
    val ok = HesabTill.step(s1, HesabTill.Event.Gateway(intent(IntentStatus.PAID)))
    val row = HesabTillModel.settle(a, ok)!!.payments.single { it.method == "hesabpay" }
    assertEquals("gateway", row.verification)
    assertEquals(fp, row.payee)
  }

  @Test
  fun `پنلِ ترکیبی — حساب‌پی فقط باقی را می‌پردازد`() {
    val a = ask(simple, PayFlow.Draft(280.0, cash = "100"))
    assertEquals(18_000L, a.amountMinor)
    val ok = HesabTill.step(HesabTillModel.start(a, 0)!!, HesabTill.Event.SellerConfirm)
    val st = HesabTillModel.settle(a, ok)!!
    assertEquals(0L, st.remaining)
    assertEquals(18_000L, st.payments.single { it.method == "hesabpay" }.amount)
    //  چیزی نمانده ⇒ در باز نمی‌شود
    assertNull(HesabTillModel.start(ask(simple, PayFlow.Draft(280.0, cash = "300")), 0))
  }

  @Test
  fun `تسویهٔ حساب‌پی فاکتورِ «نسیه» ِ بی مشتری را کامل و بی بدهی ثبت می‌کند`() {
    val a = ask(simple)
    val st = HesabTillModel.settle(a, HesabTill.step(HesabTillModel.start(a, 0)!!, HesabTill.Event.SellerConfirm))!!
    val credit = SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, settlement = st)
    val r = SalesEngine.record(ledger, cart, credit, "2026-11-03", 1L, { "n" + System.nanoTime() }, saleId = "sale-1")
    val sale = (r as SalesEngine.Result.Ok).data.sales.single { it.id == "sale-1" }
    assertEquals(0.0, sale.debtGiven, 0.0)
    assertNull(sale.debtorId)
    assertEquals(listOf("hesabpay"), sale.payments.orEmpty().map { it.method })
    assertEquals(28_000L, sale.payments.orEmpty().single().amountMinor)
  }

  @Test
  fun `HCE همان پروندهٔ برگه را می‌دهد و بی برگه خاموش است`() {
    val a = ask(simple)
    val file = HesabTillModel.tag(a, HesabTillModel.start(a, 0))!!
    try {
      HceSlot.ndef = file
      assertArrayEquals(file, HceSlot.file())
      assertArrayEquals(NdefType4Tag.SW_OK, NdefType4Tag().processFile(byteArrayOf(0, 0xA4.toByte(), 4, 0, 7) + NdefType4Tag.AID + byteArrayOf(0), HceSlot.file()))
    } finally {
      HceSlot.ndef = null
    }
    assertNull(HceSlot.file())
  }

  @Test
  fun `بی پرچم یا بی گیرنده در دیده نمی‌شود`() {
    assertTrue(simple.tillNfc)
    assertTrue(!simple.copy(flag = false).tillNfc)
    assertTrue(!simple.copy(payee = null).tillNfc)
    assertTrue(auto.tillNfc)
  }
}
