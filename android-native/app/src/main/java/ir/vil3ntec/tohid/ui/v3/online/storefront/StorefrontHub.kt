package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.data.repo.StorefrontSpecDto

/**
 *  فهرستِ کالاهای ویترین برای برگهٔ «کالاها» و برگهٔ هر کالا (`storefront/products/{id}`) — همان
 *  بارِ صفحه‌به‌صفحهٔ پیشین (۱۰۰ تایی، تا ۱۰۰ صفحه) و همان ذخیرهٔ تک‌کالا؛ پس از ذخیره فقط همان
 *  ردیف عوض می‌شود. منطقِ ویترین همان `StorefrontRepository` است.
 */
internal object StorefrontHub {
  val products = mutableStateOf<List<StorefrontProductDto>?>(null)
  val error = mutableStateOf<String?>(null)

  suspend fun load(repo: StorefrontRepository) {
    val all = mutableListOf<StorefrontProductDto>()
    var cursor: String? = null
    var fail: ApiFailure? = null
    for (page in 0 until MAX_PAGES) {
      val r = repo.products(cursor)
      val v = r.valueOrNull()
      if (v == null) { r.onFailure { fail = it }; break }
      all += v.items
      cursor = v.nextCursor ?: break
    }
    val f = fail
    if (f != null && all.isEmpty()) error.value = f.userMessage else { products.value = all; error.value = null }
  }

  /** @return خطا، یا null یعنی ذخیره شد */
  suspend fun save(
    repo: StorefrontRepository,
    p: StorefrontProductDto,
    published: Boolean,
    online: Long?,
    max: Long?,
    description: String? = null,
    specs: List<Pair<String, String>>? = null,
  ): String? {
    var err: String? = null
    repo.setProduct(p.id, published, online, max, description, specs).onFailure { err = it.userMessage }
    if (err == null) products.value = products.value?.map {
      if (it.id != p.id) it
      else it.copy(
        published = published, onlinePriceMinor = online, maxQtyMilli = max,
        description = description ?: it.description,
        specs = specs?.map { s -> StorefrontSpecDto(s.first, s.second) } ?: it.specs,
      )
    }
    return err
  }

  /** انتشار/برداشتنِ گروهی — یکی‌یکی، با گزارشِ همان `StorefrontOwner.bulkReport` */
  suspend fun bulk(repo: StorefrontRepository, ids: Set<String>, publish: Boolean): StorefrontOwner.BulkReport {
    val list = products.value.orEmpty().filter { it.id in ids }
    val results = list.map { p -> p.id to save(repo, p, publish, p.onlinePriceMinor, p.maxQtyMilli) }
    return StorefrontOwner.bulkReport(results)
  }

  private const val MAX_PAGES = 100
}
