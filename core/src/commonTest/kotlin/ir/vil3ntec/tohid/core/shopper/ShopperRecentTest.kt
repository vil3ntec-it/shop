package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.product.RecentlyViewed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  WEB-13 — «دیده‌شده‌های اخیر» ِ برنامهٔ خرید ([RecentProducts]): هر دکان فهرستِ خودش با همان قاعدهٔ [RecentlyViewed] ِ
 *  وب (`recent.js`)، کالای نیامده در `?ids=` بیرون، سقفِ دکان‌ها، و ماندن روی همین دستگاه (ذخیره، بار، نشانیِ تازهٔ دکان).
 */
class ShopperRecentTest {

  private val now = 1_800_000_000_000L
  private val day = 24L * 60 * 60 * 1000

  private class MapKv : ShopperKeyValue {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  @Test fun eachShopKeepsItsOwnNewestFirstListWithoutDuplicates() {
    var s = ShopperState()
    s = RecentProducts.saw(s, "golestan", "rice", now - 3_000)
    s = RecentProducts.saw(s, "golestan", "oil", now - 2_000)
    s = RecentProducts.saw(s, "kabul-mart", "soap", now - 1_000)
    s = RecentProducts.saw(s, "golestan", "rice", now)
    assertEquals(listOf("rice", "oil"), RecentProducts.ids(s, "golestan", now))
    assertEquals(listOf("soap"), RecentProducts.ids(s, "kabul-mart", now))
    assertEquals(emptyList(), RecentProducts.ids(s, "herat", now))
    //  شناسهٔ بد (همان الگوی شناسهٔ کالای ویترین) فهرست را خراب نمی‌کند
    assertEquals(listOf("rice", "oil"), RecentProducts.ids(RecentProducts.saw(s, "golestan", "no/slash", now), "golestan", now))
    //  حداکثر ۱۲ کالا برای هر دکان، تازه‌ترین اول
    var t = ShopperState()
    for (i in 1..15) t = RecentProducts.saw(t, "golestan", "p$i", now - (15 - i) * 1_000L)
    val ids = RecentProducts.ids(t, "golestan", now)
    assertEquals(RecentlyViewed.MAX, ids.size)
    assertEquals("p15", ids.first())
    assertEquals("p4", ids.last())
  }

  @Test fun productsMissingFromTheIdsReplyLeaveTheListAndClearIsPerShop() {
    var s = ShopperState()
    listOf("a", "b", "c").forEachIndexed { i, id -> s = RecentProducts.saw(s, "golestan", id, now - 10 + i) }
    //  پرسیده a، b، c؛ سرور b را نداد (پنهان/حذف) ⇒ b بیرون
    val kept = RecentProducts.keep(s, "golestan", listOf("a", "b", "c"), listOf("c", "a"), now)
    assertEquals(listOf("c", "a"), RecentProducts.ids(kept, "golestan", now))
    //  کالایی که پس از فرستادنِ پرسش دیده شد (پرسیده نشده) می‌ماند
    val later = RecentProducts.saw(s, "golestan", "d", now)
    assertEquals(listOf("d", "c", "a"), RecentProducts.ids(RecentProducts.keep(later, "golestan", listOf("a", "b", "c"), listOf("a", "c"), now), "golestan", now))
    //  دکانی که فهرست ندارد ⇒ همان حالت
    assertSame(s, RecentProducts.keep(s, "herat", listOf("a"), emptyList(), now))
    //  «پاک کردن» فقط همان دکان
    val two = RecentProducts.saw(s, "kabul-mart", "x", now)
    val cleared = RecentProducts.clear(two, "golestan")
    assertEquals(emptyList(), RecentProducts.ids(cleared, "golestan", now))
    assertEquals(listOf("x"), RecentProducts.ids(cleared, "kabul-mart", now))
    assertSame(cleared, RecentProducts.clear(cleared, "golestan"))
  }

  @Test fun shopsAreCappedAndExpiredListsLeaveOnTheNextWrite() {
    var s = ShopperState()
    for (i in 1..(RecentProducts.MAX_SHOPS + 5)) s = RecentProducts.saw(s, "shop-$i", "p", now - (100 - i) * 1_000L)
    assertEquals(RecentProducts.MAX_SHOPS, s.seen.size)
    assertTrue("shop-1" !in s.seen && "shop-${RecentProducts.MAX_SHOPS + 5}" in s.seen, "دکانی که دیرتر از همه دیده شد بیرون")
    //  ۳۱ روز بعد: فهرستِ کهنه با نوشتنِ بعدی می‌رود (حالتِ دستگاه بی‌پایان بزرگ نمی‌شود)
    val old = RecentProducts.saw(ShopperState(), "golestan", "rice", now - 31 * day)
    assertEquals(setOf("kabul-mart"), RecentProducts.saw(old, "kabul-mart", "soap", now).seen.keys)
  }

  @Test fun listSurvivesSaveLoadAndFollowsAMovedShop() {
    val kv = MapKv()
    val s = RecentProducts.saw(RecentProducts.saw(ShopperState(), "golestan", "rice", now - 1), "golestan", "oil", now)
    ShopperMemory.save(kv, s)
    assertEquals(s.seen, ShopperMemory.load(kv).seen)
    //  حالتِ نسخهٔ پیشین (بی `seen`) ⇒ فهرستِ تهی، نه خطا
    kv.map[ShopperMemory.KEY] = """{"carts":[],"lang":"fa"}"""
    assertEquals(emptyMap(), ShopperMemory.load(kv).seen)
    //  S3: دکان نشانی‌اش را عوض کرد ⇒ فهرست همراهش
    val moved = ShopperMemory.moved(s, "golestan", "golestan-2")
    assertEquals(listOf("oil", "rice"), RecentProducts.ids(moved, "golestan-2", now))
    assertEquals(emptyList(), RecentProducts.ids(moved, "golestan", now))
  }
}
