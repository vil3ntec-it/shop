package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.image.ImageStudio
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult

/**
 *  ۴-۲۳ (۱) — درهای «استودیوی عکس» (`/api/shop/images/studio`) و عکس‌های خودِ دکان (`/api/shop/images/mine`). جدا از صفحه
 *  تا حالِ صفحه بی سرور آزموده شود؛ خواندن در `core/image/ImageStudio`. پرچمِ خاموش ⇒ ۴۰۳ (`ApiFailure.Forbidden`).
 */
interface ImageStudioSource {
  suspend fun config(): ApiResult<ImageStudio.Config?>
  suspend fun batches(): ApiResult<List<ImageStudio.Summary>>
  suspend fun batch(id: String, after: String = ""): ApiResult<ImageStudio.Batch>
  suspend fun create(key: String, imageIds: List<String>, preset: String): ApiResult<ImageStudio.Batch>
  suspend fun action(id: String, action: String): ApiResult<Unit>
  suspend fun review(imageId: String, version: Int, approve: Boolean, confirmRisk: Boolean): ApiResult<Unit>
  suspend fun mine(cursor: String = ""): ApiResult<Pair<List<ImageStudio.Mine>, String?>>
  suspend fun bytes(url: String): ApiResult<ByteArray>
}

class ImageStudioRepository(private val api: ApiClient) : ImageStudioSource {
  private val files = PhotoVersionsRepository(api)
  private fun batchOf(o: kotlinx.serialization.json.JsonObject) = ImageStudio.batchOf(o) ?: throw ApiFailure.InvalidResponse("batch")

  override suspend fun config() = result { ImageStudio.configOf(api.get(ImageStudio.configPath)) }
  override suspend fun batches() = result { ImageStudio.batchesOf(api.get(ImageStudio.batchesPath())) }
  override suspend fun batch(id: String, after: String) = result { batchOf(api.get(ImageStudio.batchPath(id, after))) }
  override suspend fun create(key: String, imageIds: List<String>, preset: String) =
    result { batchOf(api.post(ImageStudio.createPath, ImageStudio.createBody(key, imageIds, preset))) }
  override suspend fun action(id: String, action: String) = result { api.post(ImageStudio.actionPath(id), ImageStudio.actionBody(action)); Unit }
  override suspend fun review(imageId: String, version: Int, approve: Boolean, confirmRisk: Boolean) =
    result { api.post(ImageStudio.reviewPath(imageId, version), ImageStudio.reviewBody(approve, confirmRisk)); Unit }
  override suspend fun mine(cursor: String) = result { ImageStudio.mineOf(api.get(ImageStudio.minePath(cursor))) }
  override suspend fun bytes(url: String) = files.bytes(url)
}
