package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.product.VisualSearch

/** درهای «جست‌وجو با عکس» (AI-11) — جدا تا حالِ صفحه بی سرور آزموده شود */
interface VisualSearchSource {
  /** پرچمِ `shop.visual_search` روشن است؟ (خاموش ⇒ ۴۰۴) */
  suspend fun status(): ApiResult<Boolean>

  /** بایت‌های JPEG ⇒ فهرستِ «مشابه» (عکس در سرور ذخیره نمی‌شود) */
  suspend fun search(jpeg: ByteArray): ApiResult<VisualSearch.Answer>
}

/** `/api/shop/visual-search` (`server/src/routes/shop-visual-search.js`) با `data.read` */
class VisualSearchRepository(private val api: ApiClient) : VisualSearchSource {

  override suspend fun status(): ApiResult<Boolean> = result { VisualSearch.enabled(api.get(VisualSearch.ROOT)) }

  override suspend fun search(jpeg: ByteArray): ApiResult<VisualSearch.Answer> =
    result { VisualSearch.parse(api.postBytes(VisualSearch.searchPath(), jpeg, "image/jpeg")) }
}
