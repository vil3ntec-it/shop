package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.promo.Loyalty
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.core.promo.PromoRedeem

/**
 *  ثبتِ فاکتورِ صندوق — همان زنجیرهٔ صندوقِ کامپیوتر (`DeskSale`) برای صندوقِ گوشیِ نسخهٔ ۳ (`CheckoutRoute`)، خالص و
 *  آزمون‌پذیر (`TillSaleTest`):
 *  سبدِ پس از امتیاز (PRM-13؛ بی مصرف ⇒ همان سبدِ پس از تخفیفِ خودکار) + یادداشتِ فاکتور (POS-11) ⇒ `SalesEngine.record`
 *  ⇒ نشانِ تخفیفِ خودکار (`PromoEngine.stamp`) ⇒ ردیف‌های مصرف و کسبِ امتیاز در همان دفتر (`LoyaltyPoints.post`؛ با
 *  همگام‌سازیِ همیشگیِ دفتر می‌رود). هیچ قاعده‌ای این‌جا نیست؛ فقط ترتیبِ همان سه موتور.
 *  پیش از همهٔ این‌ها، زیرِ قفلِ ثبت: دروازهٔ کدِ سقف‌دار ([redeem]، PRM-02) — مشترکِ همهٔ صندوق‌ها.
 */
object TillSale {
  /**
   *  امتیازِ همین فاکتور: قاعدهٔ در کار (`LoyaltyPref.effective`) و برنامهٔ مصرفی که صندوق‌دار برگزید (`LoyaltyTill`).
   *  خاموش ⇒ [on] false و بی برنامه: `LoyaltyPoints.post` همان نتیجه را برمی‌گرداند.
   */
  data class Points(
    val on: Boolean,
    val rule: Loyalty.Rule,
    val plan: LoyaltyPoints.Plan? = null,
    /** سبدِ پس از مصرف؛ `null` ⇒ بی مصرف */
    val cart: List<SalesEngine.CartLine>? = null,
  ) {
    fun post(r: SalesEngine.Result, by: String?): SalesEngine.Result = LoyaltyPoints.post(r, on, rule, plan, by)
  }

  /**
   *  PRM-02 — پاسخِ `POST /api/shop/promos/redeem` برای [redeem]: `ok` = ۲xx؛ `status` ۰ = پاسخی نرسید؛ `code` کدِ خطای
   *  سرور؛ `message` پیامِ خودِ سرور (نام و دلیلِ کد).
   */
  data class Reply(val ok: Boolean, val status: Int = 0, val code: String = "", val message: String? = null)

  /** نتیجهٔ [redeem]: `message == null` ⇒ ثبت؛ `decision == null` ⇒ سرور پرسیده نشد */
  data class Redeem(val message: String?, val decision: PromoRedeem.Decision? = null)

  /**
   *  PRM-02 — دروازهٔ کدِ سقف‌دار پیش از ساختنِ فاکتور (`CartStore.post` ⇐ `SalePost.run`، زیرِ قفلِ ثبت و با کلیدِ همان
   *  فاکتور) برای **همهٔ** صندوق‌ها: گوشیِ نسخهٔ ۳ (`CheckoutRoute`)، کامپیوتر (`DeskSale`)، `Sale` و `QuickSale` — همه
   *  از `PromoLive.redeemGate` که فقط حالِ دکان، وصل بودن و شبکه را می‌دهد. قاعده فقط همین‌جاست.
   *  ⛔ پرچمِ `redeem` خاموش، کلیدِ دکان خاموش (`promo == null`) یا بی کدِ اعمال‌شدهٔ سقف‌دار ⇒ هیچ پرسشی، ثبت مثلِ امروز.
   *  ⛔ بی اینترنت کدِ سقف‌دار پذیرفته نمی‌شود (سقف را فقط سرور می‌شمارد، `PromoRedeem`)؛ صندوقدار کد را برمی‌دارد یا وصل
   *     می‌شود — هرگز ثبتِ بی شمارش.
   *  ⛔ رد (ظرفیتِ پر، بیرونِ بازه، کدِ خاموش) ⇒ پیامِ خودِ سرور؛ هیچ فروشی و هیچ تخفیفی ثبت نمی‌شود و سبد سرِ جایش است.
   *  همان کلید دوباره ⇒ سرور مصرفِ دوم نمی‌سازد؛ لغوِ همین فاکتور با همین شناسه آزادش می‌کند.
   *  @param online فقط وقتی پرسیده می‌شود که کدِ سقف‌دار هست
   *  @param call فقط با کدهای سقف‌دار و فقط وقتی وصل است
   */
  suspend fun redeem(
    on: Boolean,
    rules: List<PromoCore.Rule>,
    promo: PromoEngine.Outcome?,
    online: () -> Boolean,
    call: suspend (codes: List<String>) -> Reply,
  ): Redeem {
    if (!on || promo == null) return Redeem(null)
    val codes = PromoRedeem.capped(rules, promo.codes)
    if (codes.isEmpty()) return Redeem(null)
    val offline = StrPos.couponNeedsOnline(codes.joinToString("، "))
    if (!online()) return Redeem(offline)
    val r = call(codes)
    return when (val d = PromoRedeem.decide(r.ok, r.status, r.code)) {
      PromoRedeem.Decision.GO -> Redeem(null, d)
      PromoRedeem.Decision.REJECTED -> Redeem(r.message ?: offline, d)
      PromoRedeem.Decision.UNREACHABLE -> Redeem(offline, d)
    }
  }

  /** سبدی که جمع، سقفِ اعتبار و ثبت با آن است */
  fun cart(priced: List<SalesEngine.CartLine>, points: Points?): List<SalesEngine.CartLine> = points?.cart ?: priced

  fun record(
    d: ShopData,
    priced: List<SalesEngine.CartLine>,
    sale: SalesEngine.Checkout,
    note: String?,
    promo: PromoEngine.Outcome?,
    points: Points?,
    today: String,
    now: Long,
    newId: () -> String,
    series: String = "",
    saleId: String? = null,
    receiptToken: String? = null,
  ): SalesEngine.Result {
    val r = PromoEngine.stamp(
      SalesEngine.record(d, cart(priced, points), sale.copy(note = note), today, now, newId, series = series, saleId = saleId, receiptToken = receiptToken),
      promo,
    )
    return points?.post(r, sale.seller) ?: r
  }
}
