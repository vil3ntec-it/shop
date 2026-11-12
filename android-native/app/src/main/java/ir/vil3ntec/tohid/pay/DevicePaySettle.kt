package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.terminal.DeviceAttempt
import ir.vil3ntec.tohid.core.terminal.DevicePay
import ir.vil3ntec.tohid.data.PayFlow

/**
 *  PAY-08 — تسویهٔ فاکتور با ردیفِ «تأییدِ دستگاه» (`core/terminal/DevicePay`)، همان راهِ ردیفِ تأییدشدهٔ حساب‌پی
 *  (`HesabPaySettle.withRow`: تکه‌های پنل + همان ردیف، `Pay.settle`)؛ گوشی و کامپیوتر یکی.
 *
 *  ⛔ فقط [DevicePay.Verdict.Confirmed] فروش را می‌بندد، فقط برای همان فاکتور ([DeviceAttempt.saleKey]) و فقط وقتی مبلغِ ردیف
 *  دقیقاً همان مانده است. «نیازمندِ بررسی» و هر چیزِ دیگر ⇒ `Bad` (فروش باز می‌ماند؛ صفحه بررسی را نشان می‌دهد).
 *  ⛔ هیچ دستگاهِ واقعی‌ای تا سندِ یکپارچه‌سازی (D-08) این را صدا نمی‌زند؛ سنجه با دستگاهِ ساختگی (`DevicePaySettleTest`).
 */
object DevicePaySettle {

  fun settle(total: Double, draft: PayFlow.Draft?, saleKey: String, attempt: DeviceAttempt, v: DevicePay.Verdict): PayFlow.Outcome<Settlement> {
    val row = (v as? DevicePay.Verdict.Confirmed)?.row ?: return bad("NOT_CONFIRMED")
    if (attempt.saleKey != saleKey) return bad("OTHER_SALE")
    val due = (HesabPaySettle.due(total, draft) as? HesabPaySettle.Due.Amount)?.minor ?: return bad("TOTAL_MISMATCH")
    if (row.amount != due || row.amount != attempt.amountMinor) return bad("TOTAL_MISMATCH")
    return HesabPaySettle.withRow(total, draft, row)
  }

  private fun bad(code: String) = PayFlow.Outcome.Bad(PayFlow.Problem(code))
}
