package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.gateway.GatewayPaymentRow
import ir.vil3ntec.tohid.core.gateway.IntentStatus
import ir.vil3ntec.tohid.core.gateway.PaymentIntent
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.PaymentRow
import ir.vil3ntec.tohid.core.pay.SettleInput
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.Tender
import ir.vil3ntec.tohid.data.PayFlow

/**
 *  حساب‌پی ⇐ تسویهٔ فروش، از همان راهِ پرداخت‌های دیگر (`PayFlow` و هستهٔ `core/pay`، PAY-01..08).
 *
 *  - بی پنلِ ترکیبی (`draft == null`، صندوقِ امروز): حساب‌پی **کلِ** مبلغِ فاکتور را می‌پردازد.
 *  - با پنلِ ترکیبی (`ShopPolicy.payV2`): حساب‌پی **باقی‌ماندهٔ** همان پنل را می‌پردازد (نقد و ردیف‌های
 *    دیگر سرِ جایشان) و فاکتور بی باقی بسته می‌شود.
 *  - ردیفِ حساب‌پی **همان ردیفِ آمادهٔ سرور** است (`payment` ِ قصدِ پرداخت‌شده: `confirmed`، `gateway`)،
 *    نه ثبتِ دستیِ `pending`؛ چون خودِ ارائه‌دهنده تأیید کرده (PAY-08).
 *  - هر ناجوری (مبلغ، فاکتورِ دیگر، وضعیتِ نه-paid) ⇐ خطا و فروش بسته نمی‌شود.
 *
 *  خالص است: نه Context، نه ساعت؛ آزمون: `HesabPaySettleTest`.
 */
object HesabPaySettle {

  const val METHOD = "hesabpay"

  sealed interface Due {
    data class Amount(val minor: Long) : Due
    data object None : Due
    data class Bad(val problem: PayFlow.Problem) : Due
  }

  /** چقدر با حساب‌پی؟ `total` همان `finalTotal` ِ فاکتور (افغانی) */
  fun due(total: Double, draft: PayFlow.Draft?): Due {
    if (draft == null) {
      val m = PayFlow.minor(total)
      return if (m > 0) Due.Amount(m) else Due.None
    }
    return when (val p = PayFlow.preview(draft)) {
      is PayFlow.Outcome.Bad -> Due.Bad(p.problem)
      is PayFlow.Outcome.Ok -> if (p.value.remaining > 0) Due.Amount(p.value.remaining) else Due.None
    }
  }

  /** ردیفِ پرداختِ قصدِ پرداخت‌شده — ردیفِ سرور؛ اگر سرورِ کهنه نداد، همان شکل از خودِ قصد */
  fun rowOf(intent: PaymentIntent): GatewayPaymentRow? {
    if (intent.status != IntentStatus.PAID) return null
    intent.payment?.let { return it }
    return GatewayPaymentRow(
      method = METHOD, kind = "wallet", amountMinor = intent.amountMinor,
      ref = intent.providerRef.ifEmpty { intent.id }, status = "confirmed", verification = "gateway", intentId = intent.id,
    )
  }

  /**
   *  تسویهٔ نهایی با ردیفِ حساب‌پی. `saleKey` شناسهٔ همین فاکتور است و باید همان `refId` ِ قصد باشد؛
   *  مبلغِ ردیف باید دقیقاً همان مبلغی باشد که حساب‌پی می‌بایست بپردازد.
   */
  fun settle(total: Double, draft: PayFlow.Draft?, saleKey: String, intent: PaymentIntent): PayFlow.Outcome<Settlement> {
    val row = rowOf(intent) ?: return bad("NOT_PAID")
    if (intent.refId.isNotEmpty() && intent.refId != saleKey) return bad("OTHER_SALE")
    if (row.method != METHOD || row.amountMinor != intent.amountMinor || intent.currency != "AFN") return bad("TOTAL_MISMATCH")
    val due = (due(total, draft) as? Due.Amount)?.minor ?: return bad("TOTAL_MISMATCH")
    if (row.amountMinor != due) return bad("TOTAL_MISMATCH")
    val ref = row.ref?.trim()?.ifEmpty { null } ?: row.intentId ?: intent.id
    //  #853: اثرِ انگشتِ گیرنده (سرور) روی ردیف — گزارشِ «پول به کدام حساب رفت»؛ سرورِ کهنه ⇒ بی آن، مثلِ پیش
    val payee = row.payee?.trim()?.ifEmpty { null } ?: intent.payeeFp.trim().ifEmpty { null }
    return withRow(total, draft, PaymentRow(row.method, row.kind, row.amountMinor, ref, row.status, row.verification, payee))
  }

  /** حالتِ ساده: تأییدِ صندوقدار (نه درگاه) */
  const val BY_CASHIER = "cashier"

  /**
   *  حالتِ ساده («حساب‌پیِ دکان» با شماره/QR ِ خودِ دکان): فقط وقتی صندوقدار «پول رسید» را زد. ردیف همان روشِ
   *  حساب‌پی است با کدِ فاکتور (`ref`)، `confirmed` و `verification = cashier` — تا گزارش «تأییدِ درگاه» را از
   *  «تأییدِ صندوقدار» جدا ببیند. ⛔ هیچ راهِ دیگری (اعلان، پیامک، مبلغِ برابر) به این‌جا نمی‌رسد.
   *  `payee` = اثرِ انگشتِ حسابِ گیرندهٔ فعال که سرور به صندوق داد (`TillHesab.payee.fp`) ⇐ گزارشِ «پول به کدام حساب رفت».
   */
  fun settleSimple(total: Double, draft: PayFlow.Draft?, ref: String, payee: String? = null): PayFlow.Outcome<Settlement> {
    val clean = ref.trim()
    if (clean.isEmpty()) return bad("REF_REQUIRED")
    val due = (due(total, draft) as? Due.Amount)?.minor ?: return bad("TOTAL_MISMATCH")
    return withRow(total, draft, PaymentRow(METHOD, "wallet", due, clean, "confirmed", BY_CASHIER, payee?.trim()?.ifEmpty { null }))
  }

  /**
   *  تسویه با تکه‌های پنل + ردیفِ تأییدشده (حساب‌پی، یا «تأییدِ دستگاه» ِ `DevicePaySettle`)؛ ردیفِ ثبتِ دستیِ همان تکه ⇐
   *  `confirmed` (همان روش، مبلغ و مرجع)
   */
  internal fun withRow(total: Double, draft: PayFlow.Draft?, confirmed: PaymentRow): PayFlow.Outcome<Settlement> {
    val base = draft ?: PayFlow.Draft(total)
    val tenders = ArrayList<Tender>()
    PayFlow.amountOf(base.cash)?.takeIf { !it.isNaN() && it > 0 }?.let { tenders += Tender(PayFlow.CASH, PayFlow.minor(it)) }
    base.rows.forEach { r ->
      val a = PayFlow.amountOf(r.amount)
      if (a != null && !a.isNaN() && a > 0) tenders += Tender(r.method, PayFlow.minor(a), ref = r.ref.trim().ifEmpty { null })
    }
    tenders += Tender(confirmed.method, confirmed.amount, ref = confirmed.ref)
    //  PAY-10: ارزِ نقدِ همان پنل (رکوردِ همان لحظه)؛ بی ارز ⇒ خالی و همان تسویهٔ امروز
    val fx: List<ir.vil3ntec.tohid.core.pay.ExchangeRecord> = when (val r = ir.vil3ntec.tohid.data.FxTillFlow.record(base.fx)) {
      null -> emptyList()
      is PayResult.Ok -> listOf(r.value)
      is PayResult.Err -> return bad(r.code)
    }
    val settled = when (val s = Pay.settle(SettleInput(total = PayFlow.minor(total), tenders = tenders, customerId = base.customerId?.trim()?.ifEmpty { null }, fx = fx))) {
      is PayResult.Ok -> s.value
      is PayResult.Err -> return bad(s.code)
    }
    if (settled.status != "paid") return bad("TOTAL_MISMATCH")
    val at = settled.payments.indexOfLast { it.method == confirmed.method && it.amount == confirmed.amount && it.ref == confirmed.ref }
    if (at < 0) return bad("TOTAL_MISMATCH")
    return PayFlow.Outcome.Ok(settled.copy(payments = settled.payments.mapIndexed { i, p -> if (i == at) confirmed else p }))
  }

  private fun bad(code: String) = PayFlow.Outcome.Bad(PayFlow.Problem(code))
}
