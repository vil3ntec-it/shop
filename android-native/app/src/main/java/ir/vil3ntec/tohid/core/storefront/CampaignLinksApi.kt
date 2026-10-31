package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.marketing.CampaignLinks
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  پیوندِ کمپین و QR ِ دکان — درِ سرور (`/api/shop/storefront/campaigns`،
 *  `server/src/routes/storefront-campaigns.js`). قاعده‌های خالص در
 *  `core/marketing/CampaignLinks` (commonMain) است؛ این‌جا فقط پاسخ و بدنه.
 *
 *  ⛔ «ثبت شد» فقط پس از پاسخِ سرور. ساختن با `clientId` ِ ساختهٔ برنامه است، پس
 *  تلاشِ دوباره (قطعِ نت پس از ثبت) همان پیوند را برمی‌گرداند، نه پیوندِ دوم.
 */
object CampaignLinksApi {

  /** چرا بخش فهرست ندارد */
  enum class Gate { READY, OFF, NO_PERMISSION, ERROR }

  /** خطای `GET` ⇒ حالتِ بخش. ۴۰۴ = سرورِ قدیمی‌تر بی این مسیر = هنوز روشن نیست */
  fun gateOf(status: Int, code: String): Gate = when {
    status == 404 -> Gate.OFF
    status == 403 && (code == "campaigns_disabled" || code == "storefront_disabled") -> Gate.OFF
    status == 403 -> Gate.NO_PERMISSION
    else -> Gate.ERROR
  }

  fun parseConfig(body: JsonElement): CampaignConfigDto = ApiJson.decode<CampaignConfigDto>(body)

  fun parseList(body: JsonElement): CampaignListDto = ApiJson.decode<CampaignListDto>(body)

  /** پاسخِ ساختن و ویرایش ⇒ `{ campaign }` */
  fun parseOne(body: JsonObject): CampaignDto = ApiJson.decode<CampaignDto>(body["campaign"] ?: JsonNull)

  /** بدنهٔ ساختن — نام همان پاک‌سازیِ سرور */
  fun createBody(clientId: String, name: String, source: CampaignLinks.Source): JsonObject = buildJsonObject {
    put("clientId", clientId)
    put("name", CampaignLinks.normalizeName(name))
    put("source", source.key)
  }

  fun archiveBody(archived: Boolean): JsonObject = buildJsonObject { put("archived", archived) }
}

class CampaignLinksRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<CampaignConfigDto> = ApiResult.of {
    CampaignLinksApi.parseConfig(api.get("${CampaignLinks.ROOT}/config"))
  }

  suspend fun list(): ApiResult<CampaignListDto> = ApiResult.of {
    CampaignLinksApi.parseList(api.get(CampaignLinks.ROOT))
  }

  /** با کلیدِ یکتایی: تلاشِ دوبارهٔ خودکار بی‌خطر است (سرور با `clientId` همان را برمی‌گرداند) */
  suspend fun create(clientId: String, name: String, source: CampaignLinks.Source): ApiResult<CampaignDto> = ApiResult.of {
    CampaignLinksApi.parseOne(api.postKeyed(CampaignLinks.ROOT, CampaignLinksApi.createBody(clientId, name, source), clientId))
  }

  suspend fun setArchived(id: String, archived: Boolean): ApiResult<CampaignDto> = ApiResult.of {
    CampaignLinksApi.parseOne(api.put("${CampaignLinks.ROOT}/$id", CampaignLinksApi.archiveBody(archived)))
  }
}

@Serializable
data class CampaignPermissionsDto(val manage: Boolean = false)

/** `GET …/config` */
@Serializable
data class CampaignConfigDto(
  /** پرچم‌های ویترین و `shop.storefront.campaigns` با هم */
  val flag: Boolean = false,
  /** پرچم + اشتراک — فقط آن‌وقت ساختن/بایگانی ذخیره می‌شود */
  val enabled: Boolean = false,
  val sources: List<String> = emptyList(),
  val permissions: CampaignPermissionsDto = CampaignPermissionsDto(),
)

/** شمارِ `days` روزِ اخیر — فقط شمار، هیچ نام یا شماره‌ای */
@Serializable
data class CampaignStatsDto(
  val visits: Int = 0,
  val orders: Int = 0,
  val doneOrders: Int = 0,
  val doneMinor: Long = 0,
)

@Serializable
data class CampaignDto(
  val id: String = "",
  val clientId: String = "",
  val code: String = "",
  val path: String = "",
  val name: String = "",
  val source: String = "",
  val archived: Boolean = false,
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val stats: CampaignStatsDto? = null,
)

@Serializable
data class CampaignListDto(
  val days: Int = 30,
  val sources: List<String> = emptyList(),
  val campaigns: List<CampaignDto> = emptyList(),
)
