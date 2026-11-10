package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.product.RecentlyViewed

/**
 *  WEB-13 — «دیده‌شده‌های اخیر» ِ برنامهٔ خرید، همزادِ `server/public/store/recent.js` ِ وب: هر دکان فهرستِ خودش
 *  (کلیدِ `store-recent:<slug>` ِ وب ⇐ [ShopperState.seen])، ⛔ فقط روی همین دستگاه و هرگز به سرور نمی‌رود.
 *
 *  • قاعدهٔ هر فهرست همان [RecentlyViewed] (تازه‌ترین اول، بی تکرار، حداکثر ۱۲، ۳۰ روز، ردیفِ بد کنار).
 *  • قیمت و «هنوز در ویترین» هر بار از `GET …/products?ids=` ِ همان دکان؛ کالایی که پرسیده شد و نیامد از فهرست هم
 *    می‌رود ([keep])؛ شکستِ شبکه فهرست را دست نمی‌زند.
 *  • فقط با `recent: true` ِ `GET /api/store/:slug` (پرچمِ `shop.storefront.recent`)؛ خاموش ⇒ نه نوشته، نه دیده.
 *  • وب برای هر دکان کلیدِ جدا دارد؛ این‌جا یک حالتِ یگانه است، پس حداکثر [MAX_SHOPS] دکان (کهنه‌ترین بیرون).
 */
object RecentProducts {
  const val MAX_SHOPS: Int = 20

  /** فهرستِ پاکِ [slug] (تازه‌ترین اول) */
  fun of(s: ShopperState, slug: String, now: Long): List<RecentlyViewed.Seen> = RecentlyViewed.fresh(s.seen[slug].orEmpty(), now)

  fun ids(s: ShopperState, slug: String, now: Long): List<String> = of(s, slug, now).map { it.id }

  /** برگهٔ کالای [id] دیده شد ⇒ سرِ فهرستِ همان دکان */
  fun saw(s: ShopperState, slug: String, id: String, now: Long): ShopperState =
    put(s, slug, RecentlyViewed.add(s.seen[slug].orEmpty(), id, now), now)

  /** پاسخِ `?ids=` برای [asked]: هر چه پرسیده شد و در [present] نیامد (پنهان یا حذف‌شده) بیرون می‌رود */
  fun keep(s: ShopperState, slug: String, asked: Collection<String>, present: Collection<String>, now: Long): ShopperState {
    val cur = s.seen[slug] ?: return s
    val gone = asked.toSet() - present.toSet()
    return put(s, slug, RecentlyViewed.fresh(cur.filter { it.id !in gone }, now), now)
  }

  /** «پاک کردن» ِ همین دکان */
  fun clear(s: ShopperState, slug: String): ShopperState = if (slug in s.seen) s.copy(seen = s.seen - slug) else s

  /** فهرستِ تهی برداشته می‌شود؛ بیش از [MAX_SHOPS] دکان ⇒ دکانی که دیرتر از همه دیده شد بیرون */
  private fun put(s: ShopperState, slug: String, list: List<RecentlyViewed.Seen>, now: Long): ShopperState {
    val rest = (s.seen - slug).mapValues { RecentlyViewed.fresh(it.value, now) }.filterValues { it.isNotEmpty() }
    val all = if (list.isEmpty()) rest else rest + (slug to list)
    val kept = all.entries.sortedByDescending { e -> e.value.maxOf { it.atMs } }.take(MAX_SHOPS).associate { it.key to it.value }
    return s.copy(seen = kept)
  }
}
