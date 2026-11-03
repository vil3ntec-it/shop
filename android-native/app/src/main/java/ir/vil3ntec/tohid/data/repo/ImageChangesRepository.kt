package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.image.ImageChanges
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonObject

/**
 *  IMG-PRO-17 — درهای «تغییرِ دسته‌ایِ عکسِ کالاها» (`/api/shop/images/changes`، #882) و وضعِ کنونیِ عکسِ هر کالا
 *  (`GET /api/shop/images/products/:id`). جدا از صفحه تا حالِ صفحه بی سرور آزموده شود؛ خواندن در `core/image/ImageChanges`.
 *  پرچمِ خاموشِ `shop.image_bank` ⇒ ۴۰۳ `image_bank_disabled` (`ApiFailure.Forbidden`).
 */
interface ImageChangesSource {
  suspend fun list(): ApiResult<List<ImageChanges.Change>>
  suspend fun detail(id: String): ApiResult<ImageChanges.Change>
  suspend fun current(productId: String): ApiResult<ImageChanges.Current>
  /** ⇒ دستهٔ ثبت‌شده (همان کلید دوباره ⇒ همان دسته) */
  suspend fun apply(body: JsonObject): ApiResult<ImageChanges.Change>
  /** ⇒ (دسته پس از برگشت، «پیش‌تر برگشته بود») */
  suspend fun rollback(id: String, reason: String): ApiResult<Pair<ImageChanges.Change, Boolean>>
}

class ImageChangesRepository(private val api: ApiClient) : ImageChangesSource {
  private fun change(o: JsonObject): ImageChanges.Change = ImageChanges.of(o) ?: throw ApiFailure.InvalidResponse("change")

  override suspend fun list() = result { ImageChanges.changesOf(api.get(ImageChanges.listPath())) }
  override suspend fun detail(id: String) = result { change(api.get(ImageChanges.detailPath(id))) }
  override suspend fun current(productId: String) = result { ImageChanges.currentOf(api.get(ImageChanges.productPath(productId))) }
  override suspend fun apply(body: JsonObject) = result { change(api.post(ImageChanges.PATH, body)) }
  override suspend fun rollback(id: String, reason: String) = result {
    val o = api.post(ImageChanges.rollbackPath(id), ImageChanges.rollbackBody(reason))
    change(o) to ImageChanges.already(o)
  }
}
