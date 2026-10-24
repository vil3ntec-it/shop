package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/** حالتِ صفحهٔ یک دکان: پروفایل، قفسه‌ها و کاتالوگِ صفحه‌بندی‌شده. */
data class ShopState(
  val loading: Boolean = true,
  val failure: ApiFailure? = null,
  /** این ویترین نیست یا بسته شده (`store_not_found`) */
  val notFound: Boolean = false,
  val store: StoreProfile? = null,
  val verificationAvailable: Boolean = false,
  val marketAvailable: Boolean = false,
  val highlights: Highlights? = null,
  val category: String = "",
  val query: String = "",
  val items: List<StoreProduct> = emptyList(),
  val nextCursor: String? = null,
  val loadingList: Boolean = false,
  val loadingMore: Boolean = false,
  val listFailure: ApiFailure? = null,
) {
  val cartShop: CartShop? get() = store?.let { CartShop(it.slug, it.title, it.accent) }
}

/**
 *  منطقِ صفحهٔ دکان — بی UI. سبد جداست (`ShopperCarts`) تا نوارِ سبد در هر
 *  صفحه همان باشد؛ این‌جا فقط خواندن است.
 */
class ShopperShopModel(
  private val api: ShopperApi,
  val slug: String,
) {
  private val flow = MutableStateFlow(ShopState())
  val state: StateFlow<ShopState> = flow.asStateFlow()

  private val listGen = AtomicInteger()
  private val moreGen = AtomicInteger()

  suspend fun load() = coroutineScope {
    flow.update { it.copy(loading = true, failure = null, notFound = false) }
    val home = async { api.store(slug) }
    val shelves = async { api.highlights(slug) }
    when (val h = home.await()) {
      is ApiResult.Failure -> {
        val gone = h.error is ApiFailure.NotFound
        flow.update { it.copy(loading = false, notFound = gone, failure = h.error.takeUnless { gone }) }
        shelves.cancel()
      }
      is ApiResult.Success -> {
        //  قفسه‌ها اختیاری‌اند: نشد ⇒ بی قفسه، نه خطای صفحه
        val shelf = shelves.await().valueOrNull()
        flow.update {
          it.copy(
            loading = false, store = h.value.store,
            verificationAvailable = h.value.verification.available, marketAvailable = h.value.market.available,
            highlights = shelf,
          )
        }
        reloadList()
      }
    }
  }

  /** همان دسته دوباره ⇒ همه */
  suspend fun selectCategory(category: String) {
    flow.update { it.copy(category = if (it.category == category) "" else category) }
    reloadList()
  }

  suspend fun search(query: String) {
    flow.update { it.copy(query = query) }
    reloadList()
  }

  suspend fun loadMore() {
    val s = flow.value
    val cursor = s.nextCursor ?: return
    if (s.loadingMore || s.loadingList) return
    val gen = listGen.get()
    val more = moreGen.incrementAndGet()
    flow.update { it.copy(loadingMore = true) }
    val r = api.catalog(slug, s.query, s.category, cursor)
    //  فهرست از نو خوانده شد ⇒ این صفحه مالِ فهرستِ قبلی است؛ پرچم فقط اگر «بیشترِ» تازه‌تری نیست
    if (gen != listGen.get()) {
      if (more == moreGen.get()) flow.update { it.copy(loadingMore = false) }
      return
    }
    flow.update {
      when (r) {
        is ApiResult.Success -> {
          val seen = it.items.map { p -> p.id }.toSet()
          it.copy(loadingMore = false, items = it.items + r.value.items.filterNot { p -> p.id in seen }, nextCursor = r.value.nextCursor)
        }
        is ApiResult.Failure -> it.copy(loadingMore = false, listFailure = r.error)
      }
    }
  }

  /** برگهٔ کالا: جزئیات، «مشابه» و «مکمل» */
  suspend fun product(id: String): ApiResult<ProductDetail> = api.product(slug, id)

  private suspend fun reloadList() {
    val gen = listGen.incrementAndGet()
    val s = flow.value
    flow.update { it.copy(loadingList = true, listFailure = null, loadingMore = false) }
    val r = api.catalog(slug, s.query, s.category)
    if (gen != listGen.get()) return
    flow.update {
      when (r) {
        is ApiResult.Success -> it.copy(loadingList = false, items = r.value.items, nextCursor = r.value.nextCursor)
        is ApiResult.Failure -> it.copy(loadingList = false, listFailure = r.error)
      }
    }
  }
}
