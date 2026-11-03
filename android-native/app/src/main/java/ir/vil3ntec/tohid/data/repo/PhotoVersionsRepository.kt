package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.image.ImageChanges
import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult

/**
 *  IMG-PRO-16 — نسخه‌های نهاییِ عکسِ بانکِ یک کالا: `GET /api/shop/images/products/:id` (کدام عکس و نشانیِ هر اندازه) و
 *  بایت‌های هر نسخه با نشستِ کاربر. پرچمِ خاموشِ `shop.image_bank` ⇒ ۴۰۳ (`ApiFailure.Forbidden`).
 *  ⛔ فقط نشانیِ فایلِ خودِ بانک (`ImageQuality.filePath`) — هیچ نشانیِ دیگری خوانده نمی‌شود.
 */
interface PhotoVersionsSource {
  suspend fun shown(productId: String): ApiResult<ImageQuality.Shown>
  suspend fun bytes(url: String): ApiResult<ByteArray>
}

class PhotoVersionsRepository(private val api: ApiClient) : PhotoVersionsSource {
  override suspend fun shown(productId: String) = result { ImageQuality.shownOf(api.get(ImageChanges.productPath(productId))) }
  override suspend fun bytes(url: String) = result {
    api.getBytes(ImageQuality.filePath(url) ?: throw ApiFailure.InvalidResponse("url"))
  }
}
