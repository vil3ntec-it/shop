package ir.vil3ntec.tohid.core.gateway

import ir.vil3ntec.tohid.core.gateway.HesabTill.By
import ir.vil3ntec.tohid.core.gateway.HesabTill.End
import ir.vil3ntec.tohid.core.gateway.HesabTill.Event
import ir.vil3ntec.tohid.core.gateway.HesabTill.Hold
import ir.vil3ntec.tohid.core.gateway.HesabTill.Mode
import ir.vil3ntec.tohid.core.gateway.HesabTill.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 *  حالتِ پرداختِ حساب‌پی در صندوقِ گوشیِ نسخهٔ ۳: در انتظار ⇒ تأیید **فقط** با قصدِ پرداخت‌شده‌ای که مرجع، مبلغ و گیرنده‌اش
 *  همه می‌خواند («خودکار»، قاعده‌های #853) یا با «پول رسید» ِ فروشنده («ساده»)؛ لغو؛ پایانِ زمان.
 */
class HesabTillTest {

  private val fp = "hp1_aaaabbbbccccdddd"
  private fun expect(mode: Mode) = HesabTill.Expect(mode, saleKey = "sale-1", ref = "HP-7K2M-Q9XA", amountMinor = 145_000, payeeFp = fp, until = 10_000)

  private fun intent(
    status: IntentStatus = IntentStatus.PAID,
    refId: String = "sale-1",
    amount: Long = 145_000,
    payeeFp: String = fp,
    needsReview: Boolean = false,
    rowPayee: String? = fp,
    currency: String = "AFN",
  ) = PaymentIntent(
    id = "pi_1", kind = "pos_sale", refId = refId, status = status, amountMinor = amount, currency = currency,
    checkoutUrl = "https://pay/1", qrPayload = "https://pay/1", expiresAt = 10_000, paidAt = null, providerRef = "TX-9",
    needsReview = needsReview,
    payment = if (status == IntentStatus.PAID) GatewayPaymentRow("hesabpay", "wallet", amount, "TX-9", "confirmed", "gateway", "pi_1", rowPayee) else null,
    payeeFp = payeeFp,
  )

  private fun run(mode: Mode, vararg events: Event): State = events.fold(HesabTill.start(expect(mode))) { s, e -> HesabTill.step(s, e) }

  @Test
  fun autoConfirmsOnlyWhenReferenceAmountAndPayeeAllMatch() {
    val s = run(Mode.AUTO, Event.Gateway(intent(IntentStatus.PENDING)), Event.Gateway(intent()))
    assertIs<State.Confirmed>(s)
    assertEquals(By.GATEWAY, s.by)
  }

  @Test
  fun autoNeverConfirmsOnAnythingWeaker() {
    val cases = mapOf(
      intent(refId = "sale-2") to Hold.OTHER_SALE,
      intent(refId = "") to Hold.OTHER_SALE,
      intent(amount = 144_900) to Hold.AMOUNT,
      intent(currency = "USD") to Hold.AMOUNT,
      intent(payeeFp = "hp1_thief") to Hold.PAYEE,
      intent(rowPayee = "hp1_thief") to Hold.PAYEE,
      intent(payeeFp = "") to Hold.PAYEE_UNKNOWN,
      intent(needsReview = true) to Hold.FLAGGED,
    )
    for ((i, hold) in cases) {
      val s = run(Mode.AUTO, Event.Gateway(i))
      assertIs<State.Review>(s, "$hold")
      assertEquals(hold, s.hold)
      //  «پول رسید» ِ فروشنده هم بستنی نیست؛ فقط بستنِ برگه
      assertSame(s, HesabTill.step(s, Event.SellerConfirm))
      assertEquals(State.Ended(s.expect, End.CANCELLED), HesabTill.step(s, Event.Cancel))
    }
    //  گیرندهٔ دکان نامعلوم (سرورِ کهنه) ⇒ هرگز خودکار
    val blind = HesabTill.step(HesabTill.start(expect(Mode.AUTO).copy(payeeFp = "")), Event.Gateway(intent()))
    assertEquals(Hold.PAYEE_UNKNOWN, (blind as State.Review).hold)
  }

  @Test
  fun autoIgnoresTheSellersMoneyReceivedTap() {
    val s = run(Mode.AUTO, Event.SellerConfirm)
    assertIs<State.Pending>(s)
  }

  @Test
  fun simpleConfirmsOnlyWithTheSellersTapAndIgnoresGatewayEvents() {
    val s = run(Mode.SIMPLE, Event.Gateway(intent()))
    assertIs<State.Pending>(s)
    val done = HesabTill.step(s, Event.SellerConfirm)
    assertIs<State.Confirmed>(done)
    assertEquals(By.SELLER, done.by)
  }

  @Test
  fun cancel() {
    assertEquals(End.CANCELLED, (run(Mode.SIMPLE, Event.Cancel) as State.Ended).why)
    //  «خودکار» پیش از رسیدنِ قصد: چیزی در سرور برای لغو نیست ⇒ فوری
    assertEquals(End.CANCELLED, (run(Mode.AUTO, Event.Cancel) as State.Ended).why)
    //  «خودکار»: لغو منتظرِ سرور است؛ پاسخِ «لغو شد» ⇒ پایان
    val c = run(Mode.AUTO, Event.Gateway(intent(IntentStatus.PENDING)), Event.Cancel)
    assertIs<State.Cancelling>(c)
    assertEquals(End.CANCELLED, (HesabTill.step(c, Event.Gateway(intent(IntentStatus.CANCELLED))) as State.Ended).why)
    //  همان دم پول رسیده بود و همه می‌خواند ⇒ فروش بسته می‌شود (پول گم نمی‌شود)؛ ناجور ⇒ بررسی
    assertIs<State.Confirmed>(HesabTill.step(c, Event.Gateway(intent())))
    assertIs<State.Review>(HesabTill.step(c, Event.Gateway(intent(amount = 1))))
    //  هنوز زنده ⇒ همان انتظارِ لغو؛ «پول رسید» در لغو بی‌اثر
    assertSame(c, HesabTill.step(c, Event.Gateway(intent(IntentStatus.PENDING))))
    assertSame(c, HesabTill.step(c, Event.SellerConfirm))
  }

  @Test
  fun timeout() {
    assertIs<State.Pending>(run(Mode.SIMPLE, Event.Tick(9_999)))
    assertEquals(End.TIMED_OUT, (run(Mode.SIMPLE, Event.Tick(10_000)) as State.Ended).why)
    assertEquals(End.TIMED_OUT, (run(Mode.AUTO, Event.Gateway(intent(IntentStatus.PENDING)), Event.Tick(10_001)) as State.Ended).why)
    assertEquals(End.CANCELLED, (run(Mode.AUTO, Event.Cancel, Event.Tick(10_001)) as State.Ended).why)
  }

  @Test
  fun terminalStatesNeverMoveAgain() {
    val ok = run(Mode.SIMPLE, Event.SellerConfirm)
    for (e in listOf(Event.Cancel, Event.Tick(99_999), Event.Gateway(intent(IntentStatus.FAILED)), Event.SellerConfirm)) assertSame(ok, HesabTill.step(ok, e))
    val ended = run(Mode.AUTO, Event.Gateway(intent(IntentStatus.EXPIRED)))
    assertEquals(End.EXPIRED, (ended as State.Ended).why)
    assertSame(ended, HesabTill.step(ended, Event.Gateway(intent())))
    assertEquals(End.FAILED, (run(Mode.AUTO, Event.Gateway(intent(IntentStatus.FAILED))) as State.Ended).why)
  }

  @Test
  fun verdictIsNullOnlyForAFullMatch() {
    assertNull(HesabTill.verdict(expect(Mode.AUTO), intent()))
    assertEquals(Hold.NOT_PAID, HesabTill.verdict(expect(Mode.AUTO), intent(IntentStatus.PENDING)))
  }

  @Test
  fun intentAndRowCarryThePayeeFingerprintFromTheServer() {
    val i = PaymentIntent.fromMap(
      mapOf(
        "id" to "pi_1", "kind" to "pos_sale", "refId" to "sale-1", "status" to "paid", "amountMinor" to 145_000L, "currency" to "AFN",
        "checkoutUrl" to "https://pay/1", "payeeFp" to fp,
        "payment" to mapOf("method" to "hesabpay", "kind" to "wallet", "amount" to 145_000L, "status" to "confirmed", "verification" to "gateway", "payee" to fp),
      ),
    )!!
    assertEquals(fp, i.payeeFp)
    assertEquals(fp, i.payment?.payee)
    assertNull(HesabTill.verdict(expect(Mode.AUTO), i))
    //  سرورِ کهنه بی اثرِ انگشت ⇒ هرگز خودکار
    assertEquals("", PaymentIntent.fromMap(mapOf("id" to "pi_1", "amountMinor" to 1L, "currency" to "AFN"))!!.payeeFp)
  }

  @Test
  fun tillDoorNeedsTheFlagAndAKnownPayee() {
    val payee = mapOf("name" to "دکان", "last4" to "4321", "fp" to fp)
    fun till(vararg kv: Pair<String, Any?>) = TillHesab.fromMap(mapOf("hesabpay" to mapOf(*kv)))
    //  «ساده»: فقط با پرچمِ `pay.hesabpay` و گیرندهٔ معلوم
    val simple = arrayOf("mode" to "simple", "simple" to mapOf("account" to "0700123456"), "payee" to payee)
    assertEquals(true, till(*simple, "flag" to true).tillNfc)
    assertEquals(false, till(*simple, "flag" to false).tillNfc)
    assertEquals(false, till(*simple).tillNfc, "سرورِ کهنه بی `flag` ⇒ همان `enabled` (خاموش)")
    assertEquals(false, till("mode" to "simple", "simple" to mapOf("account" to "0700123456"), "flag" to true).tillNfc, "بی گیرنده")
    //  «خودکار»: درگاه خودش پشتِ پرچم است (`enabled`)
    assertEquals(true, till("mode" to "auto", "enabled" to true, "payee" to payee).tillNfc)
    assertEquals(false, till("mode" to "auto", "enabled" to false, "flag" to true, "payee" to payee).tillNfc)
    assertEquals(false, TillHesab.OFF.tillNfc)
  }
}
