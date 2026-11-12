package ir.vil3ntec.tohid.customer.ui

import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.shopper.ShopperFunnel
import ir.vil3ntec.tohid.core.shopper.StorePage

/*
 *  برنامهٔ خرید — تپشِ بالای قیف (RPT-14؛ بازدید ⇐ دیدنِ کالا ⇐ سبد)، همزادِ `beat` ِ `server/public/store/store.js`، کنارِ
 *  [CustomerModel] (فایلِ جدا تا مدل از سقفِ اندازه نگذرد). قاعده‌ها در هسته: [ShopperFunnel] (فقط با `features.funnel`،
 *  یک بار برای هر گام در هر بازکردنِ دکان، گامِ پیش از رسیدنِ دکان منتظر). فرستادن روی نخِ شبکه و پاسخ نادیده — همان
 *  `sendBeacon` ِ وب: نه دوباره‌فرستادن، نه پیامی به مشتری.
 */

/** پروفایلِ دکان رسید: «دیده‌شده‌های اخیر» (WEB-13) و تپشِ «بازدید» با هر گامِ منتظرِ همین دکان */
internal fun CustomerModel.storeArrived(slug: String, page: StorePage) {
  loadRecent(slug, page)
  funnelBeat(slug, funnel.ready(slug, page))
}

/** برگهٔ کالا رسید: «دیده‌شده‌های اخیر» و تپشِ «دیدنِ کالا» */
internal fun CustomerModel.productArrived(slug: String, p: PublicProduct) {
  sawProduct(slug, p)
  funnelStep(slug, ShopperFunnel.Step.VIEW)
}

/** سبدِ همین دکان بزرگ‌تر شد (افزودن یا «+»): تپشِ «سبد» */
internal fun CustomerModel.cartGrew(slug: String) = funnelStep(slug, ShopperFunnel.Step.CART)

private fun CustomerModel.funnelStep(slug: String, step: ShopperFunnel.Step) {
  val page = if (slug == storeSlug) store.valueOrNull() else null
  if (funnel.take(slug, page, step)) funnelBeat(slug, listOf(step))
}

private fun CustomerModel.funnelBeat(slug: String, steps: List<ShopperFunnel.Step>) {
  for (s in steps) request({ api.funnel(slug, s) }) { }
}
