package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.image.StorefrontMediaSpec
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.Serializable

/**
 *  لوگو و تصویرِ سرِ ویترین — `/api/shop/storefront/media` (M3، مهاجرتِ ۱۳۳).
 *
 *  ⛔ پشتِ پرچمِ جدای `shop.storefront.media` (پیش‌فرض خاموش؛ `StorefrontConfigDto.media`).
 *  سرور نوع را از محتوا، حجم و ابعاد را دوباره می‌سنجد؛ shop_id از توکن می‌آید.
 *  بارگذاری PUT ِ همان بایت‌هاست ⇒ تکرار همان نسخه را برمی‌گرداند (بی‌خطر).
 */
class StorefrontMediaRepository(private val api: ApiClient) {

  suspend fun current(): ApiResult<StorefrontMediaDto> = result {
    ApiJson.decode<StorefrontMediaDto>(api.get(ROOT))
  }

  suspend fun upload(kind: StorefrontMediaSpec.Kind, jpeg: ByteArray): ApiResult<StorefrontMediaDto> = result {
    ApiJson.decode<StorefrontMediaDto>(api.putBytes("$ROOT/${kind.key}", jpeg, "image/jpeg"))
  }

  suspend fun remove(kind: StorefrontMediaSpec.Kind): ApiResult<StorefrontMediaDto> = result {
    ApiJson.decode<StorefrontMediaDto>(api.delete("$ROOT/${kind.key}"))
  }

  /** بایت‌های نسخه‌ای که سرور داد (`/api/shop/storefront/media/logo/3`) با نشستِ کاربر */
  suspend fun bytes(url: String): ApiResult<ByteArray> = result {
    val path = url.removePrefix("/api/v1").removePrefix("/api")
    require(path.startsWith("$ROOT/")) { "نشانیِ عکس درست نیست" }
    api.getBytes(path)
  }

  companion object {
    const val ROOT = "/shop/storefront/media"
  }
}

@Serializable
data class StorefrontMediaDto(
  val logo: StorefrontMediaItemDto? = null,
  val header: StorefrontMediaItemDto? = null,
) {
  fun of(kind: StorefrontMediaSpec.Kind): StorefrontMediaItemDto? =
    if (kind == StorefrontMediaSpec.Kind.LOGO) logo else header
}

@Serializable
data class StorefrontMediaItemDto(
  val version: Int = 0,
  val mime: String = "",
  val width: Int = 0,
  val height: Int = 0,
  val bytes: Long = 0,
  val url: String = "",
)
