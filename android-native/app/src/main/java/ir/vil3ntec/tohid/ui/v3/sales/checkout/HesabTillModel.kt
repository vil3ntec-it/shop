package ir.vil3ntec.tohid.ui.v3.sales.checkout

import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.HesabTill
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.nfc.PayTag
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.pay.HesabPaySettle

/**
 *  «پرداخت با حساب‌پی (NFC/QR)» ِ صندوقِ گوشیِ نسخهٔ ۳ (PAY-21، پشتِ `pay.hesabpay`؛ `TillHesab.tillNfc`) — آنچه برگه
 *  (`HesabTillRoute`) از تسویه می‌گیرد و حسابِ خالصش (`HesabTillModelTest`). حالت و تأیید فقط از `HesabTill` (core).
 *
 *  - مبلغ: پرداختِ ساده ⇒ کلِ فاکتور؛ پنلِ ترکیبی ⇒ باقیِ همان پنل (`HesabPaySettle.due`).
 *  - کدِ یکتای فاکتور (`HesabPayShop.invoiceRef`) و گیرنده (نام و ۴ رقم، #853) کنارِ QR **و** درونِ برچسبِ NFC.
 *  - برچسب ([tag]): «خودکار» ⇒ نشانیِ پرداختِ قصدِ سرور؛ «ساده» ⇒ QR ِ خودِ دکان اگر نشانی است. هر دو با متنِ مبلغ، کد و
 *    گیرنده (`PayTag`). فقط تا «در انتظار» است؛ پس از آن برچسب خاموش.
 *  - تسویه ([settle]): «ساده» + «پول رسید» ⇒ `settleSimple` (`verification: cashier`، اثرِ انگشتِ گیرنده)؛
 *    «خودکار» + تأییدِ همه‌خوان ⇒ ردیفِ آمادهٔ سرور (`settle`، `verification: gateway`). هیچ راهِ دیگری.
 */
object HesabTillModel {

  /** مهلتِ «در انتظار» (۱۵ دقیقهٔ قصدِ سرور + یک دقیقه برای ساعتِ دستگاه) */
  const val WINDOW_MS = 16 * 60 * 1000L

  /** آنچه تسویه به برگه می‌سپارد (`CheckoutSession.hesab`) */
  data class Ask(
    val saleKey: String,
    /** «مبلغ نهایی» ِ فاکتور (افغانی) */
    val total: Double,
    /** پنلِ ترکیبی (`payV2`)؛ `null` ⇒ کلِ فاکتور */
    val draft: PayFlow.Draft?,
    val till: TillHesab,
    /** «همین حالا ثبت‌شدنی است؟» (موجودی، تکرار) پیش از گرفتنِ پول — پیام یا `null` */
    val precheck: () -> String?,
  ) {
    val mode: HesabTill.Mode get() = if (till.gateway) HesabTill.Mode.AUTO else HesabTill.Mode.SIMPLE
    val payee: Payee? get() = till.payee
    val ref: String get() = HesabPayShop.invoiceRef(saleKey)

    /** مبلغِ حساب‌پی به پول؛ چیزی نمانده یا پنلِ نادرست ⇒ `null` */
    val amountMinor: Long? get() = (HesabPaySettle.due(total, draft) as? HesabPaySettle.Due.Amount)?.minor
  }

  /**
   *  پیش از گرفتنِ پول: همین سبد همین حالا ثبت‌شدنی است؟ (موجودی، کالای غیرفعال، تکرارِ همان فاکتور) — آزمایشیِ همان موتور با
   *  پرداختِ کامل، بی نوشتن؛ پیامِ رد یا `null`. همان سنجشِ صندوقِ کامپیوتر (`GatewayHook.precheck`).
   */
  fun precheck(d: ShopData, cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout, saleKey: String, today: String): String? {
    var n = 0
    val dry = SalesEngine.record(d, cart, checkout.copy(payment = SalesEngine.Payment.CASH, settlement = null), today, 0L, { "hp-dry-${n++}" }, saleId = saleKey)
    return (dry as? SalesEngine.Result.Failed)?.message
  }

  /** آغاز: «در انتظار» تا [now] + [WINDOW_MS]؛ گیرندهٔ نامعلوم ⇒ اثرِ انگشتِ خالی (خودکار هرگز تأیید نمی‌کند) */
  fun start(a: Ask, now: Long): HesabTill.State? {
    val amount = a.amountMinor ?: return null
    return HesabTill.start(HesabTill.Expect(a.mode, a.saleKey, a.ref, amount, a.payee?.fp.orEmpty(), now + WINDOW_MS))
  }

  /** نشانیِ پرداختِ QR و برچسب: «خودکار» ⇒ قصدِ سرور؛ «ساده» ⇒ QR ِ خودِ دکان (یا شماره) */
  fun link(a: Ask, s: HesabTill.State?): String? = when (a.mode) {
    HesabTill.Mode.AUTO -> (s as? HesabTill.State.Pending)?.intent?.qrPayload?.takeIf { it.isNotBlank() }
    HesabTill.Mode.SIMPLE -> a.till.simple?.let { HesabPayShop.qrWithAmount(it, s?.expect?.amountMinor ?: 0L, a.ref) }?.takeIf { it.isNotBlank() }
  }

  /** پروندهٔ NDEF ِ HCE — فقط «در انتظار» و با QR ِ آماده؛ وگرنه `null` (برچسب خاموش) */
  fun tag(a: Ask, s: HesabTill.State?): ByteArray? {
    if (s !is HesabTill.State.Pending) return null
    val qr = link(a, s) ?: return null
    val p = a.payee ?: return null
    return PayTag.file(qr, PayTag.Info(s.expect.amountMinor, a.ref, p.name, p.last4))
  }

  /** تسویهٔ «تأییدشده» برای ثبت؛ هر حالتِ دیگر یا ناجوری ⇒ `null` */
  fun settle(a: Ask, s: HesabTill.State): Settlement? {
    if (s !is HesabTill.State.Confirmed) return null
    val out = when (s.by) {
      HesabTill.By.SELLER -> HesabPaySettle.settleSimple(a.total, a.draft, a.ref, a.payee?.fp)
      HesabTill.By.GATEWAY -> s.intent?.let { HesabPaySettle.settle(a.total, a.draft, a.saleKey, it) } ?: return null
    }
    return (out as? PayFlow.Outcome.Ok)?.value
  }
}
