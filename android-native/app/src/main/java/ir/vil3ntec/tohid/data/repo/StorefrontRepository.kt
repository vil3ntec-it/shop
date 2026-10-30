package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  ویترینِ اینترنتی از سمتِ دکان — `/api/shop/storefront` (فاز ۴، WEB-01/03،
 *  PRD-27، §۳۱). سرور همهٔ قاعده‌ها را دوباره می‌سنجد؛ shop_id از توکن می‌آید.
 *
 *  ⛔ پیش‌فرض خاموش: بی پرچم‌های `shop.orders` و `shop.storefront` فقط
 *  `config` جواب می‌دهد و صفحه اصلاً دیده نمی‌شود.
 */
class StorefrontRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<StorefrontConfigDto> = result {
    ApiJson.decode<StorefrontConfigDto>(api.get(ROOT + "/config"))
  }

  /** فقط فیلدهای آمده عوض می‌شوند (همان قراردادِ سرور) */
  suspend fun save(body: JsonObject): ApiResult<StorefrontSettingsDto> = result {
    ApiJson.decode<StorefrontSettingsDto>(api.put(ROOT, body)["settings"] ?: JsonNull)
  }

  /** اعلامِ مالک — همان `eventId` در تکرار ⇒ سرور بی‌اثرش می‌کند (PRES-08/09) */
  suspend fun declare(p: StorefrontOwner.PendingPresence, deviceId: String): ApiResult<PresenceResultDto> = result {
    val body = buildJsonObject {
      put("eventId", p.eventId)
      put("mode", p.mode)
      p.declaredUntil?.let { put("declaredUntil", it) }
      put("clientEventAt", p.clientEventAt)
      if (deviceId.isNotBlank()) put("deviceId", deviceId.take(80))
    }
    ApiJson.decode<PresenceResultDto>(api.post("$ROOT/presence", body))
  }

  suspend fun products(cursor: String?): ApiResult<StorefrontProductPageDto> = result {
    val q = if (cursor.isNullOrBlank()) "?limit=100" else "?limit=100&cursor=" + java.net.URLEncoder.encode(cursor, "UTF-8")
    ApiJson.decode<StorefrontProductPageDto>(api.get("$ROOT/products$q"))
  }

  /** `description`/`specs` ِ null ⇒ نفرستاده ⇒ سرور همان قبلی را نگه می‌دارد */
  suspend fun setProduct(
    id: String, published: Boolean, onlinePriceMinor: Long?, maxQtyMilli: Long?,
    description: String? = null, specs: List<Pair<String, String>>? = null,
  ): ApiResult<Unit> = result {
    val body = buildJsonObject {
      put("published", published)
      put("onlinePriceMinor", onlinePriceMinor?.let { JsonPrimitive(it) } ?: JsonNull)
      put("maxQtyMilli", maxQtyMilli?.let { JsonPrimitive(it) } ?: JsonNull)
      description?.let { put("description", it) }
      specs?.let { list ->
        put("specs", JsonArray(list.map { buildJsonObject { put("name", it.first); put("value", it.second) } }))
      }
    }
    api.put("$ROOT/products/" + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20"), body)
    Unit
  }

  companion object {
    const val ROOT = "/shop/storefront"

    /** ساعت‌های هفته برای سرور: {"0":[[480,1200]], …} */
    fun weeklyHoursJson(days: Map<Int, List<Pair<Int, Int>>>): JsonObject = buildJsonObject {
      for ((day, ranges) in days) if (ranges.isNotEmpty()) {
        put(day.toString(), JsonArray(ranges.map { JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.second))) }))
      }
    }
  }
}

@Serializable
data class StorefrontConfigDto(
  val flag: Boolean = false,
  val ordersFlag: Boolean = false,
  val storefrontFlag: Boolean = false,
  val entitled: Boolean = false,
  val enabled: Boolean = false,
  val settings: StorefrontSettingsDto? = null,
  val permissions: StorefrontPermsDto = StorefrontPermsDto(),
  /** پرچمِ جدای ناحیه و زمان‌بندی (`shop.storefront.zones`)؛ سرورِ قدیمی نمی‌فرستد ⇒ خاموش */
  val zones: Boolean = false,
  /** پرچمِ `shop.orders.subscribers_only`: سفارشِ آنلاینِ تازه فقط با اشتراکِ فعال؛ سرورِ قدیمی نمی‌فرستد ⇒ خاموش */
  val subscribersOnly: Boolean = false,
  /** اشتراکِ فعالِ همین لحظه به گفتهٔ سرور (دورهٔ آزمایشی نه) */
  val subscribed: Boolean = false,
)

@Serializable
data class StorefrontPermsDto(val manage: Boolean = false)

@Serializable
data class StorefrontFulfillmentDto(val delivery: Boolean = true, val pickup: Boolean = true)

@Serializable
data class StorefrontSettingsDto(
  val slug: String = "",
  val title: String = "",
  val tagline: String = "",
  val contactPhone: String = "",
  val accent: String = "",
  val status: PresenceStatusDto? = null,
  val fulfillment: StorefrontFulfillmentDto = StorefrontFulfillmentDto(),
  val deliveryFeeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  val enabled: Boolean = false,
  val notice: String = "",
  val timezone: String = "Asia/Kabul",
  val weeklyHours: Map<String, List<List<Int>>> = emptyMap(),
  val closedDates: List<String> = emptyList(),
  val presenceMode: String = "auto",
  val declaredUntil: Long? = null,
  val updatedAt: Long = 0,
)

/** همان شیءِ `status` که مشتری در ویترین می‌گیرد (§۳۱.۱) */
@Serializable
data class PresenceStatusDto(
  val badge: String = "",
  val mode: String = "auto",
  val declaredUntil: Long? = null,
  val connection: String = "",
  val lastConfirmedAt: Long? = null,
  val orderAcceptance: String = "",
  val timezone: String = "Asia/Kabul",
  val localTime: String = "",
  val hoursSet: Boolean = false,
  val holiday: Boolean = false,
  val today: List<List<Int>> = emptyList(),
  val notice: String = "",
)

@Serializable
data class PresenceResultDto(
  val status: PresenceStatusDto = PresenceStatusDto(),
  val replayed: Boolean = false,
  val applied: Boolean? = null,
  val serverReceivedAt: Long = 0,
)

@Serializable
data class StorefrontProductDto(
  val id: String,
  val name: String = "",
  val unit: String = "",
  val category: String = "",
  /** قیمتِ فروشِ دفتر به یک‌صدم (null = بی‌قیمت ⇒ در ویترین دیده نمی‌شود) */
  val priceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
  val photo: Boolean = false,
  val published: Boolean = false,
  val onlinePriceMinor: Long? = null,
  val active: Boolean = true,
  /** WEB-08: توضیح و مشخصاتِ عمومی (سرورِ قدیمی‌تر نمی‌فرستد ⇒ خالی) */
  val description: String = "",
  val specs: List<StorefrontSpecDto> = emptyList(),
)

@Serializable
data class StorefrontSpecDto(val name: String = "", val value: String = "")

@Serializable
data class StorefrontProductPageDto(
  val items: List<StorefrontProductDto> = emptyList(),
  val nextCursor: String? = null,
)
