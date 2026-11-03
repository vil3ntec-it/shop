package ir.vil3ntec.tohid.core.gateway

/**
 *  حالتِ پرداختِ حساب‌پی در صندوقِ گوشیِ نسخهٔ ۳ (NFC/QR، PAY-21) — کاتلینِ خالص، بی ساعت و بی شبکه (`HesabTillTest`).
 *
 *  جریان: فروشنده «پرداخت با حساب‌پی (NFC/QR)» را می‌زند ⇒ [start] (در انتظار) ⇒ گوشیِ مشتری برچسبِ NFC (یا QR) را
 *  می‌خواند و در حساب‌پی می‌پردازد ⇒ فقط یکی از این دو فاکتور را «تأییدشده» می‌کند:
 *
 *   - **«خودکار»** ([Mode.AUTO]، حسابِ پذیرندهٔ خودِ دکان): وضعیتِ قصد از سرور ([Event.Gateway]). سرور «پرداخت شد» را فقط
 *     پس از وب‌هوکِ امضاشده + تأییدِ دوم + اثبات‌های #853 (پذیرنده، مرجع، گیرنده) می‌گذارد؛ صندوق **دوباره** می‌سنجد
 *     ([verdict]): همین فاکتور (`refId`)، همین مبلغ و ارز، همین حسابِ گیرنده (`payeeFp` = گیرندهٔ فعالِ دکان)، و نه
 *     «نیازمندِ بررسی». هر ناجوری ⇒ [State.Review] — ⛔ هرگز تأییدِ خودکار با چیزی ضعیف‌تر؛ «پول رسید» ِ فروشنده هم
 *     در این حالت چیزی را نمی‌بندد.
 *   - **«ساده»** ([Mode.SIMPLE]، شماره/QR ِ دکان، تا حسابِ پذیرنده آماده شود — D-08): حساب‌پی به برنامه خبر نمی‌دهد؛
 *     ⛔ فقط «پول رسید» ِ فروشنده ([Event.SellerConfirm]). هر رویدادِ درگاهی (یا اعلان) نادیده است.
 *
 *  لغو ([Event.Cancel]): «ساده» فوری؛ «خودکار» اول [State.Cancelling] تا سرور بگوید — اگر همان دم پول رسیده بود و همه
 *  می‌خواند، تأیید (فروش بسته می‌شود، پول گم نمی‌شود). زمان ([Event.Tick]): پس از [Expect.until] ⇒ [End.TIMED_OUT].
 *  حالت‌های پایانی ([State.Confirmed]، [State.Ended]) با هیچ رویدادی عوض نمی‌شوند؛ [State.Review] فقط بسته می‌شود.
 */
object HesabTill {

  enum class Mode { SIMPLE, AUTO }

  /** چه کسی تأیید کرد */
  enum class By { GATEWAY, SELLER }

  /** چرا خودکار بسته نشد */
  enum class Hold { NOT_PAID, OTHER_SALE, AMOUNT, PAYEE, PAYEE_UNKNOWN, FLAGGED }

  enum class End { CANCELLED, TIMED_OUT, FAILED, EXPIRED }

  /** آنچه این پرداخت باید باشد: همین فاکتور، همین کد، همین مبلغ (پول)، همین گیرنده، تا این زمان (ساعتِ همین دستگاه) */
  data class Expect(
    val mode: Mode,
    val saleKey: String,
    val ref: String,
    val amountMinor: Long,
    /** اثرِ انگشتِ گیرندهٔ فعالِ دکان (`TillHesab.payee.fp`)؛ خالی ⇒ «خودکار» هرگز تأیید نمی‌کند */
    val payeeFp: String,
    val until: Long,
  )

  sealed interface State {
    val expect: Expect
    data class Pending(override val expect: Expect, val intent: PaymentIntent? = null) : State
    data class Cancelling(override val expect: Expect, val intent: PaymentIntent?) : State
    data class Confirmed(override val expect: Expect, val by: By, val intent: PaymentIntent? = null) : State
    data class Review(override val expect: Expect, val hold: Hold, val intent: PaymentIntent) : State
    data class Ended(override val expect: Expect, val why: End) : State
  }

  sealed interface Event {
    /** وضعیتِ تازهٔ قصد از سرور (ساختن، پاییدن، پاسخِ لغو) */
    data class Gateway(val intent: PaymentIntent) : Event
    /** «پول رسید» ِ فروشنده */
    data object SellerConfirm : Event
    data object Cancel : Event
    data class Tick(val now: Long) : Event
  }

  fun start(e: Expect): State = State.Pending(e)

  /**
   *  قصدِ «پرداخت شد» همین پرداخت است؟ `null` ⇒ همه می‌خواند (همین فاکتور، مبلغ و ارز، ردیفِ درگاهی، همین گیرنده،
   *  بی «نیازمندِ بررسی»)؛ وگرنه نخستین ناجوری.
   */
  fun verdict(e: Expect, i: PaymentIntent): Hold? {
    if (i.status != IntentStatus.PAID) return Hold.NOT_PAID
    if (i.refId != e.saleKey) return Hold.OTHER_SALE
    if (i.amountMinor != e.amountMinor || i.currency != "AFN") return Hold.AMOUNT
    i.payment?.let { p -> if (p.amountMinor != e.amountMinor || p.verification != "gateway") return Hold.AMOUNT }
    if (e.payeeFp.isBlank() || i.payeeFp.isBlank()) return Hold.PAYEE_UNKNOWN
    if (i.payeeFp != e.payeeFp || (i.payment?.payee?.let { it != e.payeeFp } == true)) return Hold.PAYEE
    if (i.needsReview) return Hold.FLAGGED
    return null
  }

  fun step(s: State, ev: Event): State = when (s) {
    is State.Confirmed, is State.Ended -> s
    is State.Review -> if (ev is Event.Cancel) State.Ended(s.expect, End.CANCELLED) else s
    is State.Pending -> pending(s, ev)
    is State.Cancelling -> cancelling(s, ev)
  }

  private fun pending(s: State.Pending, ev: Event): State {
    val e = s.expect
    return when (ev) {
      is Event.Tick -> if (ev.now >= e.until) State.Ended(e, End.TIMED_OUT) else s
      Event.SellerConfirm -> if (e.mode == Mode.SIMPLE) State.Confirmed(e, By.SELLER) else s
      //  «خودکار» بی قصدِ رسیده هنوز چیزی در سرور برای لغو ندارد (ساختن دوباره همان کلید را ازسر می‌گیرد) ⇒ فوری
      Event.Cancel -> if (e.mode == Mode.SIMPLE || s.intent == null) State.Ended(e, End.CANCELLED) else State.Cancelling(e, s.intent)
      is Event.Gateway -> if (e.mode == Mode.SIMPLE) s else gateway(e, ev.intent) ?: State.Pending(e, ev.intent)
    }
  }

  private fun cancelling(s: State.Cancelling, ev: Event): State {
    val e = s.expect
    return when (ev) {
      //  پاسخِ لغو: پرداخت‌شده ⇒ همان سنجش؛ پایانی ⇒ لغو؛ هنوز زنده (لغو نرسید) ⇒ همان انتظار
      is Event.Gateway -> gateway(e, ev.intent)?.let { if (it is State.Ended) State.Ended(e, End.CANCELLED) else it } ?: s
      is Event.Tick -> if (ev.now >= e.until) State.Ended(e, End.CANCELLED) else s
      Event.SellerConfirm, Event.Cancel -> s
    }
  }

  /** وضعیتِ پایانیِ قصد ⇒ حالتِ تازه؛ هنوز زنده ⇒ `null` */
  private fun gateway(e: Expect, i: PaymentIntent): State? = when (i.status) {
    IntentStatus.PAID -> verdict(e, i)?.let { State.Review(e, it, i) } ?: State.Confirmed(e, By.GATEWAY, i)
    IntentStatus.FAILED -> State.Ended(e, End.FAILED)
    IntentStatus.EXPIRED -> State.Ended(e, End.EXPIRED)
    IntentStatus.CANCELLED -> State.Ended(e, End.CANCELLED)
    IntentStatus.REFUNDED -> State.Review(e, Hold.NOT_PAID, i)
    else -> null
  }
}
