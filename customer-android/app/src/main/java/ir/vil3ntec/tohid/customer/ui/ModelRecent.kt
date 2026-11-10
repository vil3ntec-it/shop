package ir.vil3ntec.tohid.customer.ui

import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.shopper.RecentProducts
import ir.vil3ntec.tohid.core.shopper.StorePage

/*
 *  برنامهٔ خرید — «دیده‌شده‌های اخیر» (WEB-13)، همزادِ `server/public/store/recent.js` ِ وب، کنارِ [CustomerModel]
 *  (فایلِ جدا تا مدل از سقفِ اندازه نگذرد). فقط با `recent` ِ همان دکان؛ خاموش ⇒ نه نوشته، نه دیده (فهرستِ پیشین
 *  دست نمی‌خورد). فهرست فقط روی همین دستگاه است؛ قیمت و «هنوز در ویترین» از `?ids=` ِ امروز. قاعده‌ها: [RecentProducts].
 */

/** دکان رسید: کالاهای فهرست یک بار از `?ids=` (قیمتِ امروز)؛ نیامده ⇒ از فهرست بیرون؛ شبکه ⇒ ردیف پنهان، فهرست همان */
fun CustomerModel.loadRecent(slug: String, page: StorePage) {
  if (!page.recent) return
  //  برگهٔ کالا پیش از رسیدنِ دکان باز شده بود (پیوندِ عمیق) ⇒ همین حالا دیده شد
  val r = route
  val open = product.valueOrNull()?.product
  if (r is Route.Product && r.slug == slug && open != null && open.id == r.id) sawProduct(slug, open)
  val ids = RecentProducts.ids(saved, slug, clock())
  if (ids.isEmpty()) return
  request({ api.productsByIds(slug, ids) }) { res ->
    val got = res.valueOrNull() ?: return@request
    account.learn(slug, got.items)
    val next = RecentProducts.keep(saved, slug, ids, got.items.map { it.id }, clock())
    if (next != saved) persist(next)
  }
}

/** برگهٔ کالای [p] رسید ⇒ سرِ فهرستِ همین دکان (فقط با `recent` ِ همان دکان) */
fun CustomerModel.sawProduct(slug: String, p: PublicProduct) {
  if (slug != storeSlug || store.valueOrNull()?.recent != true) return
  account.learn(slug, listOf(p))
  persist(RecentProducts.saw(saved, slug, p.id, clock()))
}

/** کالاهای فهرست که امروز در ویترین دیده شده‌اند، تازه‌ترین اول */
fun CustomerModel.recentItems(slug: String): List<PublicProduct> {
  if (slug != storeSlug || store.valueOrNull()?.recent != true) return emptyList()
  val here = items.associateBy { it.id }
  return RecentProducts.ids(saved, slug, clock()).mapNotNull { here[it] ?: account.products[AccountBox.key(slug, it)] }
}

fun CustomerModel.clearRecent(slug: String) = persist(RecentProducts.clear(saved, slug))
