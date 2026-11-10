package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.image.ImageChanges
import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.image.ProductGallery
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult

/**
 *  PRD-17/PRD-27 — عکس‌های تکمیلی و عکسِ جدای ویترینِ یک کالا روی بانکِ تصاویرِ دکان (`/api/shop/images`، پرچمِ
 *  `shop.image_bank`؛ خاموش ⇒ ۴۰۳). نشانی‌ها و بدنه‌ها از `core/image/ProductGallery`؛ بایت‌ها همان نسخه‌های آماده‌شدهٔ
 *  `ImageBank.prepare` (نمایش ≤۶۰۰KB، کوچک ≤۱۵۰KB — همان `DERIV` ِ سرور).
 *  ⛔ فقط نشانیِ فایلِ خودِ بانک خوانده می‌شود (`ImageQuality.filePath`).
 */
interface ProductGallerySource {
  suspend fun items(productId: String): ApiResult<List<ProductGallery.Item>>
  suspend fun shown(productId: String): ApiResult<ImageQuality.Shown>
  suspend fun bytes(url: String): ApiResult<ByteArray>
  /** بارگذاری با نقشِ [role]، سپس نسخهٔ نمایش و کوچک؛ [select] ⇒ همان عکسِ ویترین. @return شناسهٔ عکس */
  suspend fun upload(productId: String, role: String, display: ByteArray, thumb: ByteArray?, select: Boolean): ApiResult<String>
  suspend fun detach(imageId: String): ApiResult<Unit>
}

class ProductGalleryRepository(private val api: ApiClient) : ProductGallerySource {
  override suspend fun items(productId: String) = result { ProductGallery.itemsOf(api.get(ProductGallery.minePath(productId))) }

  override suspend fun shown(productId: String) = result { ImageQuality.shownOf(api.get(ImageChanges.productPath(productId))) }

  override suspend fun bytes(url: String) = result {
    api.getBytes(ImageQuality.filePath(url) ?: throw ApiFailure.InvalidResponse("url"))
  }

  override suspend fun upload(productId: String, role: String, display: ByteArray, thumb: ByteArray?, select: Boolean) = result {
    //  POST خودکار تکرار نمی‌شود (`postBytes`)؛ نسخه‌های کوچک با PUT ِ یک‌بارنویس
    val made = api.postBytes(ProductGallery.uploadPath(productId, role), display, "image/jpeg")
    val (id, version) = ProductGallery.uploadedOf(made) ?: throw ApiFailure.InvalidResponse("id")
    api.putBytes(ProductGallery.derivativePath(id, version, "display"), display, "image/jpeg")
    if (thumb != null) api.putBytes(ProductGallery.derivativePath(id, version, "thumb"), thumb, "image/jpeg")
    if (select) api.post(ProductGallery.SELECT_PATH, ProductGallery.selectBody(productId, id, version))
    id
  }

  override suspend fun detach(imageId: String) = result { api.post(ProductGallery.detachPath(imageId)); Unit }
}
