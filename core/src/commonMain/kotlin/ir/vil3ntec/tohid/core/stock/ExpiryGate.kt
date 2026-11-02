package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.credit.CreditGate

/**
 *  S22 (PRD-16، INV-18) — «کالای منقضی فقط با مجوز فروخته می‌شود»: فروشی که از ماندهٔ منقضیِ کالایی برمی‌دارد،
 *  تأییدِ مدیر یا صاحب می‌خواهد — همان راهِ «درخواستِ تأیید» ِ سقفِ اعتبار (`CreditGate`): کاربرِ مدیر/صاحب خودش با
 *  دلیل، شاگرد با PIN ِ تأییدکننده روی همین دستگاه (`ApproverVault`). تأیید به همان کالاها و تا همان مقدار بسته است.
 *  سرور انقضا را نمی‌سنجد، پس برگهٔ امضاشده ساخته نمی‌شود؛ تأیید با نام و دلیل در سابقهٔ همان فروش می‌ماند.
 *  منطقِ خالص؛ آزمون: `ScenarioS22Test`.
 */
object ExpiryGate {

  /** چه کسی، با چه نقشی، برای کدام کالاها تا چه مقدار (هزارمِ واحد) و چرا */
  data class Approval(val by: String, val role: String, val qtyMilli: Map<String, Long>, val reason: String)

  /** نیاز ([need]: کالا ⇐ مقدارِ منقضی به هزارم) را این تأیید می‌پوشاند؟ نیازِ خالی ⇒ همیشه */
  fun covers(need: Map<String, Long>, approval: Approval?): Boolean {
    if (need.isEmpty()) return true
    val a = approval ?: return false
    if (a.by.isBlank() || a.reason.isBlank() || !CreditGate.canApprove(a.role)) return false
    return need.all { (id, q) -> (a.qtyMilli[id] ?: 0L) >= q }
  }
}
