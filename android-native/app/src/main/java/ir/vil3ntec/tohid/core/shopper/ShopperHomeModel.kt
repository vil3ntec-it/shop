package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.storefront.MarketCategoryDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/** حالتِ خانهٔ «خرید»: دسته‌ها، دکان‌ها، قفسه‌های کالا و جست‌وجو. */
data class HomeState(
  val loading: Boolean = true,
  val failure: ApiFailure? = null,
  /** بازارچه روی این سرور هنوز باز نیست (`market_not_found`) — نه خطا، یک وضعیت */
  val marketClosed: Boolean = false,
  val shopCount: Int = 0,
  val categories: List<MarketCategoryDto> = emptyList(),
  val verificationAvailable: Boolean = false,
  /** دستهٔ برگزیده؛ «» ⇒ همه */
  val category: String = "",
  val shops: List<ShopCard> = emptyList(),
  val total: Int = 0,
  val nextOffset: Int? = null,
  val loadingMore: Boolean = false,
  /** خانهٔ «اول کالا»؛ سرورِ قدیمی‌تر ⇒ null و فقط دکان‌ها */
  val feed: MarketFeed? = null,
  val query: String = "",
  val search: MarketSearch? = null,
  val searching: Boolean = false,
  val searchFailure: ApiFailure? = null,
  val near: GeoPoint? = null,
) {
  val inSearch: Boolean get() = query.trim().length >= ShopperHomeModel.MIN_QUERY
  val isEmpty: Boolean get() = !loading && failure == null && !marketClosed && shops.isEmpty() && feed?.items.isNullOrEmpty()
}

/**
 *  منطقِ خانهٔ «خرید» — بی UI. صفحه فقط `state` را می‌کشد و این تابع‌ها را
 *  از کوروتینِ خودش صدا می‌زند (درنگِ تایپ با خودِ صفحه است).
 *
 *  ⛔ پاسخِ کهنه هرگز روی تازه نمی‌نشیند: هر بار خواندنِ فهرست یا جست‌وجو یک
 *  شمارهٔ نسل می‌گیرد و فقط پاسخِ آخرین نسل اعمال می‌شود (دسته‌ای که پشتِ
 *  سرِ هم عوض شد، نتیجهٔ قبلی را نشان نمی‌دهد).
 */
class ShopperHomeModel(
  private val api: ShopperApi,
  private val favourites: ShopperFavourites,
) {
  private val flow = MutableStateFlow(HomeState())
  val state: StateFlow<HomeState> = flow.asStateFlow()

  private val listGen = AtomicInteger()
  private val moreGen = AtomicInteger()
  private val searchGen = AtomicInteger()

  suspend fun load() = coroutineScope {
    val gen = listGen.incrementAndGet()
    val s = flow.value
    //  «بیشتر» ِ نیمه‌کاره مالِ فهرستِ قبلی است — پاسخش کنار گذاشته می‌شود
    flow.update { it.copy(loading = true, failure = null, loadingMore = false) }
    val overview = async { api.overview() }
    val shops = async { api.shops(category = s.category, first = favourites.slugs(), near = s.near) }
    val feed = async { api.feed(category = s.category, near = s.near) }
    val o = overview.await()
    val page = shops.await()
    val f = feed.await()
    if (gen != listGen.get()) return@coroutineScope
    val failure = (o as? ApiResult.Failure)?.error ?: (page as? ApiResult.Failure)?.error
    if (failure != null) {
      flow.update { it.copy(loading = false, failure = failure.takeUnless(::closed), marketClosed = closed(failure)) }
      return@coroutineScope
    }
    val ov = o.valueOrNull()!!
    val p = page.valueOrNull()!!
    flow.update {
      it.copy(
        loading = false, failure = null, marketClosed = false,
        shopCount = ov.shopCount, categories = ov.categories, verificationAvailable = ov.verification.available,
        shops = p.shops, total = p.total, nextOffset = p.nextOffset,
        //  قفسهٔ کالا اختیاری است: نشد ⇒ خانه همان دکان‌ها
        feed = f.valueOrNull(),
      )
    }
  }

  /** همان دسته دوباره ⇒ «همه» */
  suspend fun selectCategory(key: String) {
    flow.update { it.copy(category = if (it.category == key) "" else key, shops = emptyList(), nextOffset = null, feed = null) }
    load()
  }

  /** «نزدیکِ من» — موقعیتِ درشت؛ null ⇒ بی ترتیبِ فاصله */
  suspend fun setNear(point: GeoPoint?) {
    flow.update { it.copy(near = point) }
    load()
  }

  suspend fun loadMore() {
    val s = flow.value
    val offset = s.nextOffset ?: return
    if (s.loadingMore || s.loading) return
    val gen = listGen.get()
    val more = moreGen.incrementAndGet()
    flow.update { it.copy(loadingMore = true) }
    val r = api.shops(category = s.category, offset = offset, first = favourites.slugs(), near = s.near)
    //  فهرست از نو خوانده شد ⇒ این صفحه مالِ فهرستِ قبلی است؛ پرچم فقط اگر «بیشترِ» تازه‌تری نیست
    if (gen != listGen.get()) {
      if (more == moreGen.get()) flow.update { it.copy(loadingMore = false) }
      return
    }
    when (r) {
      is ApiResult.Success -> flow.update {
        val seen = it.shops.map { c -> c.slug }.toSet()
        it.copy(loadingMore = false, shops = it.shops + r.value.shops.filterNot { c -> c.slug in seen }, nextOffset = r.value.nextOffset, total = r.value.total)
      }
      is ApiResult.Failure -> flow.update { it.copy(loadingMore = false) }
    }
  }

  /** کمتر از دو حرف ⇒ جست‌وجو بسته و خانه برمی‌گردد */
  suspend fun search(query: String) {
    val gen = searchGen.incrementAndGet()
    flow.update { it.copy(query = query) }
    if (query.trim().length < MIN_QUERY) {
      flow.update { it.copy(search = null, searching = false, searchFailure = null) }
      return
    }
    flow.update { it.copy(searching = true, searchFailure = null) }
    val r = api.search(query, flow.value.near)
    if (gen != searchGen.get()) return
    when (r) {
      is ApiResult.Success -> flow.update { it.copy(searching = false, search = r.value) }
      is ApiResult.Failure -> flow.update { it.copy(searching = false, searchFailure = r.error) }
    }
  }

  /** @return حالا دلخواه است یا نه */
  fun toggleFavourite(card: ShopCard): Boolean = favourites.toggle(card.slug, card.title, card.accent)

  private fun closed(f: ApiFailure): Boolean = f is ApiFailure.NotFound && f.code == MARKET_CLOSED

  companion object {
    const val MIN_QUERY = 2
    const val MARKET_CLOSED = "market_not_found"
  }
}
