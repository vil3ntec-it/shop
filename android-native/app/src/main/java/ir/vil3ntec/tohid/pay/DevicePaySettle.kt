package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.terminal.DeviceAttempt
import ir.vil3ntec.tohid.core.terminal.DevicePay
import ir.vil3ntec.tohid.data.PayFlow

/**
 *  PAY-08 — تسویهٔ فاکتور با ردیفِ «تأییدِ دستگاه» (`core/terminal/DevicePay`)، همان راهِ ردیفِ تأییدشدهٔ حساب‌پی
 *  (`HesabPaySettle.withRow`: تکه‌های پنل + همان ردیف، `Pay.settle`)؛ گوشی و کامپیوتر یکی.
 *
 *  ⛔ فقط [DevicePay.Verdict.Confirmed] فروش را می‌بندد، فقط برای همان فاکتور ([DeviceAttempt.saleKey] و فاکتوری که دستگاه
 *  بازگرداند)، همان کلیدِ تلاش، افغانی، و فقط وقتی مبلغِ ردیف دقیقاً همان مانده است. «نیازمندِ بررسی» و هر چیزِ دیگر ⇒ `Bad` (فروش باز می‌ماند؛ صفحه بررسی را نشان می‌دهد).
 *  ⛔ هیچ دستگاهِ واقعی‌ای تا سندِ یکپارچه‌سازی (D-08) این را صدا نمی‌زند؛ سنجه با دستگاهِ ساختگی (`DevicePaySettleTest`).
 */
object DevicePaySettle {

  fun settle(total: Double, draft: PayFlow.Draft?, saleKey: String, attempt: DeviceAttempt, v: DevicePay.Verdict): PayFlow.Outcome<Settlement> {
    val c = v as? DevicePay.Verdict.Confirmed ?: return bad("NOT_CONFIRMED")
    val row = c.row
    if (attempt.saleKey != saleKey || c.saleRef != saleKey) return bad("OTHER_SALE")
    //  حکمِ همین تلاش، نه تلاشِ دیگری با همان مبلغ؛ و فقط افغانی (همان قاعدهٔ `HesabPaySettle.settle`)
    if (c.key != attempt.key) return bad("OTHER_KEY")
    if (attempt.currency != AFN || c.currency != AFN) return bad("CURRENCY_MISMATCH")
    val due = (HesabPaySettle.due(total, draft) as? HesabPaySettle.Due.Amount)?.minor ?: return bad("TOTAL_MISMATCH")
    if (row.amount != due || row.amount != attempt.amountMinor) return bad("TOTAL_MISMATCH")
    return HesabPaySettle.withRow(total, draft, row)
  }

  private const val AFN = "AFN"

  private fun bad(code: String) = PayFlow.Outcome.Bad(PayFlow.Problem(code))
}
