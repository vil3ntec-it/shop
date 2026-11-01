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
    val base = draft ?: PayFlow.Draft(total)
    val tenders = ArrayList<Tender>()
    PayFlow.amountOf(base.cash)?.takeIf { !it.isNaN() && it > 0 }?.let { tenders += Tender(PayFlow.CASH, PayFlow.minor(it)) }
    base.rows.forEach { r ->
      val a = PayFlow.amountOf(r.amount)
      if (a != null && !a.isNaN() && a > 0) tenders += Tender(r.method, PayFlow.minor(a), ref = r.ref.trim().ifEmpty { null })
    }
    tenders += Tender(METHOD, row.amountMinor, ref = ref)
    val settled = when (val s = Pay.settle(SettleInput(total = PayFlow.minor(total), tenders = tenders, customerId = base.customerId?.trim()?.ifEmpty { null }))) {
      is PayResult.Ok -> s.value
      is PayResult.Err -> return bad(s.code)
    }
    if (settled.status != "paid") return bad("TOTAL_MISMATCH")
    //  ردیفِ ثبتِ دستیِ همین تکه ⇐ ردیفِ تأییدشدهٔ سرور (همان روش، مبلغ و مرجع)
    val at = settled.payments.indexOfLast { it.method == METHOD && it.amount == row.amountMinor && it.ref == ref }
    if (at < 0) return bad("TOTAL_MISMATCH")
    val confirmed = PaymentRow(row.method, row.kind, row.amountMinor, ref, row.status, row.verification)
    return PayFlow.Outcome.Ok(settled.copy(payments = settled.payments.mapIndexed { i, p -> if (i == at) confirmed else p }))
  }

  private fun bad(code: String) = PayFlow.Outcome.Bad(PayFlow.Problem(code))
}
