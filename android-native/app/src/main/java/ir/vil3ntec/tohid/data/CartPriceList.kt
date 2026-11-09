package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.PriceList

/**
 *  POS-18 — لیستِ قیمتِ دستیِ فاکتور میانِ صندوق (`PriceListAtPos`، حالتِ `PriceList.State`) و سبد (`CartStore.priceList`،
 *  `SaleDraft.priceList`، `HeldInvoice.priceList`).
 *
 *  پیش‌تر حالتِ لیست فقط در صفحه بود: فاکتورِ عمده‌ای که صندوق‌دار دستی عمده کرده بود با نگه‌داشتن و بازگشت (یا
 *  بازیابی پس از بسته شدن) دوباره خرده قیمت می‌خورد — پولِ فاکتور عوض می‌شد. حالا انتخابِ دستی با خودِ فاکتور می‌رود.
 *  بی انتخابِ دستی ⇒ `null` و همان رفتارِ امروز (لیستِ مشتری یا خرده).
 */
object CartPriceList {

  /** آنچه با فاکتور نوشته می‌شود: فقط انتخابِ دستی */
  fun hand(state: PriceList.State): String? = if (state.byHand) PriceList.of(state.list) else null

  /** حالتِ صندوق برای فاکتورِ کنونی: انتخابِ دستیِ همان فاکتور، وگرنه [state] */
  fun effective(state: PriceList.State, hand: String?): PriceList.State =
    if (hand == null) state else PriceList.State(PriceList.of(hand), byHand = true)

  /**
   *  فاکتورِ صندوق عوض شد (بازگشت، بازیابی، خالی شدن) و [hand] ِ تازه رسید: انتخابِ دستیِ همان، وگرنه از نو با لیستِ
   *  مشتری ([customerList]، `null` = بی مشتری) — همان `PriceList.FRESH` ِ فاکتورِ تازه.
   */
  fun restore(hand: String?, customerList: String?): PriceList.State =
    if (hand != null) PriceList.State(PriceList.of(hand), byHand = true) else PriceList.onCustomer(PriceList.FRESH, customerList)

  /**
   *  ردیف‌های سندِ ثابت‌قیمت (اصلاحِ فاکتور، پیش‌فاکتورِ پیش از این نسخه) که به سبدِ صندوق می‌روند: کدام انتخابِ دستیِ لیست
   *  پولِ هیچ ردیفی را با بازقیمت‌گذاریِ صندوق (`repriceCart`، همان `PriceList.cartUnit`) عوض نمی‌کند. حالتِ تازهٔ فاکتور
   *  (لیستِ مشتری یا خرده) بس است ⇒ `null`؛ وگرنه لیستِ دیگر اگر همه را همان نگه می‌دارد؛ هیچ‌کدام ⇒ `null`.
   *  بی این، ردیفِ عمدهٔ فاکتورِ اصلاح‌شده (فیِ برابر با عمدهٔ کالا) در سبدِ خرده دوباره خرده قیمت می‌خورد.
   */
  fun keep(d: ShopData, lines: List<SalesEngine.CartLine>, customerList: String?): String? {
    val fresh = restore(null, customerList).list
    if (holds(d, lines, fresh)) return null
    val other = if (fresh == PriceList.WHOLESALE) PriceList.RETAIL else PriceList.WHOLESALE
    return other.takeIf { holds(d, lines, it) }
  }

  /** پولِ هر ردیفِ کالادارِ [lines] زیرِ لیستِ [list] همان می‌ماند؟ (فیِ خالی = خردهٔ کالا، مثلِ `repriceCart`) */
  fun holds(d: ShopData, lines: List<SalesEngine.CartLine>, list: String): Boolean {
    val byId = d.products.associateBy { it.id }
    return lines.all { l ->
      val p = byId[l.productId]
      if (l.free || p == null) return@all true
      val prices = PriceList.Prices(Math.round(p.salePrice * 100.0), Math.round(p.wholesalePrice * 100.0))
      val unit = l.unitPrice?.let { Math.round(it * 100.0) }
      (PriceList.cartUnit(prices, unit, list) ?: prices.retailMinor) == (unit ?: prices.retailMinor)
    }
  }
}
