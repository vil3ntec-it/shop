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
}
