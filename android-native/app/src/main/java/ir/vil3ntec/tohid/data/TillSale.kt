package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.Loyalty

/**
 *  ثبتِ فاکتورِ صندوق — همان زنجیرهٔ صندوقِ کامپیوتر (`DeskSale`) برای صندوقِ گوشیِ نسخهٔ ۳ (`CheckoutRoute`)، خالص و
 *  آزمون‌پذیر (`TillSaleTest`):
 *  سبدِ پس از امتیاز (PRM-13؛ بی مصرف ⇒ همان سبدِ پس از تخفیفِ خودکار) + یادداشتِ فاکتور (POS-11) ⇒ `SalesEngine.record`
 *  ⇒ نشانِ تخفیفِ خودکار (`PromoEngine.stamp`) ⇒ ردیف‌های مصرف و کسبِ امتیاز در همان دفتر (`LoyaltyPoints.post`؛ با
 *  همگام‌سازیِ همیشگیِ دفتر می‌رود). هیچ قاعده‌ای این‌جا نیست؛ فقط ترتیبِ همان سه موتور.
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
