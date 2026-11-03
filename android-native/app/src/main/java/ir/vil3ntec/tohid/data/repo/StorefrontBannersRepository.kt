package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.storefront.AreaRequests
import ir.vil3ntec.tohid.core.storefront.StorefrontBanners
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  WEB-24 — بنرهای زمان‌بندی‌شدهٔ ویترین (`/api/shop/storefront/banners`، پرچمِ `shop.storefront.banners`).
 *  کلِ فهرست یک‌جا و اتمی جایگزین می‌شود؛ سرور همه‌چیز را دوباره می‌سنجد و shop_id از توکن می‌آید.
 */
class StorefrontBannersRepository(private val api: ApiClient) {

  suspend fun load(): ApiResult<StorefrontBannersDto> = result { ApiJson.decode<StorefrontBannersDto>(api.get(PATH)) }

  suspend fun save(list: List<StorefrontBanners.Banner>): ApiResult<StorefrontBannersDto> = result {
    ApiJson.decode<StorefrontBannersDto>(api.put(PATH, body(list)))
  }

  companion object {
    const val PATH = StorefrontRepository.ROOT + "/banners"

    fun body(list: List<StorefrontBanners.Banner>): JsonObject = buildJsonObject {
      put("banners", JsonArray(list.map { b ->
        buildJsonObject {
          put("title", b.title)
          put("body", b.body)
          put("startAt", b.startAt)
          put("endAt", b.endAt)
          put("sort", b.sort)
          put("zoneIds", JsonArray(b.zoneIds.map { JsonPrimitive(it) }))
          put("link", buildJsonObject { put("type", b.linkType); put("value", b.linkValue) })
        }
      }))
    }
  }
}

@Serializable
data class StorefrontBannerLinkDto(val type: String = StorefrontBanners.NONE, val value: String = "")

@Serializable
data class StorefrontBannerDto(
  val id: String = "",
  val title: String = "",
  val body: String = "",
  val link: StorefrontBannerLinkDto = StorefrontBannerLinkDto(),
  val zoneIds: List<String> = emptyList(),
  val startAt: Long = 0,
  val endAt: Long = 0,
  val sort: Int = 0,
) {
  fun banner(): StorefrontBanners.Banner =
    StorefrontBanners.Banner(title, body, link.type, link.value, zoneIds, startAt, endAt, sort)
}

@Serializable
data class StorefrontBannersDto(val banners: List<StorefrontBannerDto> = emptyList(), val max: Int = StorefrontBanners.MAX)

/**
 *  CHK-10/S34 — «وقتی ارسال به این‌جا رسید خبرم کن» (`/api/shop/storefront/area-requests`، فقط صاحب و مدیر با
 *  پرچمِ `shop.storefront.notify_area`). شماره فقط برای محلی که ناحیهٔ امروز پوشانده؛ «خبر دادم» ردیف را پاک
 *  می‌کند. ⛔ سرور پیامی نمی‌فرستد (D-09).
 */
class AreaRequestsRepository(private val api: ApiClient) {

  suspend fun load(): ApiResult<AreaRequests.View> = result { ApiJson.decode<AreaRequestsDto>(api.get(PATH)).view() }

  suspend fun notified(id: String): ApiResult<Unit> = result {
    //  همان رمزگذاریِ شناسه که `StorefrontRepository.saveProduct` دارد
    api.post(PATH + "/" + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20") + "/notified")
    Unit
  }

  companion object {
    const val PATH = StorefrontRepository.ROOT + "/area-requests"
  }
}

@Serializable
data class AreaCellDto(val lat: Double = 0.0, val lng: Double = 0.0, val count: Int = 0, val coveredBy: String? = null)

@Serializable
data class AreaContactDto(val id: String = "", val phone: String = "", val zone: String = "", val requestedAt: Long = 0)

@Serializable
data class AreaRequestsDto(
  val total: Int = 0,
  val covered: Int = 0,
  val cells: List<AreaCellDto> = emptyList(),
  val contacts: List<AreaContactDto> = emptyList(),
) {
  fun view(): AreaRequests.View = AreaRequests.view(
    total, covered,
    cells.map { AreaRequests.Cell(it.lat, it.lng, it.count, it.coveredBy) },
    contacts.map { AreaRequests.Contact(it.id, it.phone, it.zone, it.requestedAt) },
  )
}
