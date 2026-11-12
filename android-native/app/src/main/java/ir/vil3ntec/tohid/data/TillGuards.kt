package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.CheckoutRules
import ir.vil3ntec.tohid.core.stock.ExpiryGate

/**
 *  سدهای فروشِ صندوقِ **همین دستگاه** — یک جا برای هر راهی که فاکتورِ فروش می‌سازد: صندوقِ کامپیوتر (`DeskSale`)، صندوقِ
 *  گوشیِ نسخهٔ ۳ و فروشِ سریع (`CheckoutRoute`) و فروشِ «تعویض» در برگشت (`ReturnFlowSheet`، `ReturnsV2`):
 *  - [blockExpired] — «کالای منقضی فروخته نشود» (`ShopPolicy.blockExpired`)؛ همیشه می‌بندد.
 *  - [expiryGate] — S22 «منقضی فقط با تأییدِ مدیر» (`ExpiryPref.gate`؛ با «فروخته نشود» خاموش).
 *  - [rules] — POS-24 قاعده‌های پیش از پایانِ فاکتور (`CheckoutRulesPref`).
 *  همه پیش‌فرض خاموش ⇒ همان امروز. `SalesEngine.record` هر سه را دوباره می‌سنجد؛ خواندن از تنظیم: `TillGuardsPref`.
 *  ⛔ پیش از این، فروشِ تعویض `SalesEngine.Checkout(seller = me)` ِ خام می‌ساخت ⇒ منقضی بی تأیید و قاعده‌های POS-24 از راهِ
 *  «تعویض» دور زده می‌شدند.
 */
data class TillGuards(
  val blockExpired: Boolean = false,
  val expiryGate: Boolean = false,
  val rules: CheckoutRules = CheckoutRules.OFF,
) {

  /** همین سدها روی [base] */
  fun on(base: SalesEngine.Checkout): SalesEngine.Checkout = base.copy(blockExpired = blockExpired, expiryGate = expiryGate, rules = rules)

  /**
   *  فروشِ «تعویض» (RET-05): نقدِ کامل به نامِ [seller] با همین سدها و تأییدِ منقضیِ همین یک تلاش ([expiryApproval]).
   *  مشتریِ فاکتورِ تعویض همان مشتریِ برگشت است ([customerId]: مشتریِ فاکتورِ اصلی، یا مشتریِ «اعتبارِ فروشگاه» ِ برگشتِ بی رسید)
   *  — فقط اگر هنوز در دفتر و فعال باشد (ادغام‌شده، CRM-10 ⇒ بی مشتری)؛ «مشتری از مبلغِ X» (POS-24) با همان سنجیده می‌شود.
   */
  fun exchange(d: ShopData, seller: String, customerId: String?, expiryApproval: ExpiryGate.Approval?): SalesEngine.Checkout =
    on(
      SalesEngine.Checkout(
        seller = seller,
        debtorId = customerId?.takeIf { id -> d.debtors.any { it.id == id && CustomerCrm.isActive(it) } },
        expiryApproval = expiryApproval,
      ),
    )

  companion object {
    val OFF = TillGuards()

    /** S22 — پیامِ ردِ فروشِ منقضیِ بی تأیید؛ همان متنِ `SalesEngine.record` (تعویضی که برگهٔ تأیید را بی تأیید بست) */
    fun expiredRefusal(d: ShopData, need: Map<String, Long>): String =
      SalesEngine.expiredNeedText(d, need) + " — " + SalesEngine.CREDIT_NEEDS_APPROVAL
  }
}
