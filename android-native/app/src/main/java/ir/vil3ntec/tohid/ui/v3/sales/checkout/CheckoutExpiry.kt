package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.stock.ExpiryGate
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.approval.CreditApprovalSheet

/**
 *  S22 — فروشی که منتظرِ تأییدِ «کالای منقضی» است (`sale/checkout/expiry`): نیاز (کالا ⇐ مقدارِ منقضی به هزارمِ واحد)، خلاصه
 *  و کاربرِ صندوق (نام، نقش، شناسه برای فهرستِ تأییدکننده‌های همین دستگاه).
 */
data class ExpiryAsk(val need: Map<String, Long>, val summary: String, val seller: String, val selfRole: String, val requesterUserId: String)

/**
 *  S22 «منقضی فقط با مجوزِ مدیر» در صندوقِ گوشیِ نسخهٔ ۳ (و فروشِ سریع) — همان قاعدهٔ `DeskSale.finish`: با `ExpiryPref.gate`
 *  (`Checkout.expiryGate`) فروشی که از ماندهٔ منقضی برمی‌دارد پیش از ثبت تأییدِ مدیر/صاحب می‌خواهد؛ تأیید فقط همان کالاها و تا
 *  همان مقدار را می‌پوشاند (`ExpiryGate.covers`) — سبدی که پس از تأیید منقضیِ بیشتر یا کالای منقضیِ دیگری برمی‌دارد دوباره
 *  می‌پرسد، و `SalesEngine.record` با همان `expiryApproval` دوباره می‌سنجد. «فروخته نشود» (`blockExpired`) همیشه می‌بندد.
 */
object CheckoutExpiry {

  /** نیازِ منقضیِ همین سبد که تأییدِ همراهِ [checkout] نمی‌پوشاند؛ `null` ⇒ ثبت آزاد است (تنظیمِ خاموش، بستن، بی منقضی یا پوشیده) */
  fun uncovered(d: ShopData, cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout, today: String): Map<String, Long>? {
    if (!checkout.expiryGate || checkout.blockExpired) return null
    return SalesEngine.expiredNeed(d, cart, today).takeUnless { ExpiryGate.covers(it, checkout.expiryApproval) }
  }

  fun ask(d: ShopData, need: Map<String, Long>, seller: String, selfRole: String, requesterUserId: String): ExpiryAsk =
    ExpiryAsk(need, SalesEngine.expiredNeedText(d, need), seller, selfRole, requesterUserId)

  /** تأییدِ برگهٔ PIN (همان `CreditApprovalSheet` ِ سقفِ اعتبار) ⇒ تأییدِ همین کالاها و تا همین مقدار */
  fun approval(ok: CreditGate.Approval, need: Map<String, Long>): ExpiryGate.Approval = ExpiryGate.Approval(ok.by, ok.role, need, ok.reason)
}

/** `sale/checkout/expiry`: تأییدِ فروشِ کالای منقضی؛ تأیید در `CheckoutSession` می‌ماند و «ثبت» با آن می‌رود */
@Composable
fun RouteScope.ExpiryApprovalRoute() {
  val ask = CheckoutSession.expiry
  if (ask == null) { LaunchedEffect(Unit) { back() }; return }
  CreditApprovalSheet(
    summary = ask.summary,
    debtorId = "",
    addMinor = 0L,
    selfRole = ask.selfRole,
    selfName = ask.seller,
    requesterUserId = ask.requesterUserId,
    onApproved = { ok -> CheckoutSession.expiryApproval = CheckoutExpiry.approval(ok, ask.need); CheckoutSession.expiry = null; back() },
    onClose = { CheckoutSession.expiry = null; CheckoutSession.expiryPending = false; back() },
    title = StrPos.expiredSaleTitle,
  )
}
