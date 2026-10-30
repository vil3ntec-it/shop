package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  «ارسال و زمان» ِ ویترین — `/api/shop/storefront/delivery` (CHK-06/07/10/12).
 *  هر فهرستِ فرستاده‌شده در سرور کامل و اتمی جایگزین می‌شود؛ سرور همه‌چیز را
 *  دوباره می‌سنجد و shop_id از توکن می‌آید.
 */
class StorefrontDeliveryRepository(private val api: ApiClient) {

  suspend fun load(): ApiResult<StorefrontDeliveryDto> = result {
    ApiJson.decode<StorefrontDeliveryDto>(api.get(PATH))
  }

  suspend fun save(checked: StorefrontDelivery.Checked, allowAsap: Boolean): ApiResult<StorefrontDeliveryDto> = result {
    ApiJson.decode<StorefrontDeliveryDto>(api.put(PATH, body(checked, allowAsap)))
  }

  companion object {
    const val PATH = StorefrontRepository.ROOT + "/delivery"

    fun body(c: StorefrontDelivery.Checked, allowAsap: Boolean): JsonObject = buildJsonObject {
      put("zones", JsonArray(c.zones.map { z ->
        buildJsonObject {
          put("name", z.name)
          put("kind", z.kind)
          put("feeMinor", z.feeMinor)
          put("minOrderMinor", z.minOrderMinor)
          put("freeOverMinor", z.freeOverMinor?.let { JsonPrimitive(it) } ?: JsonNull)
          //  CHK-06 (مهاجرتِ ۱۳۸): همیشه فرستاده می‌شود — null ِ صریح یعنی «پاک کن»؛ سرور فقط کلیدِ
          //  نیامده (برنامهٔ کهنه) را «همان پیشین» می‌خواند
          put("smallBasketFeeMinor", z.smallBasketFeeMinor?.let { JsonPrimitive(it) } ?: JsonNull)
          put("active", z.active)
          if (z.kind == "radius") {
            z.center?.let { put("centerLat", it.lat); put("centerLng", it.lng) }
            z.radiusM?.let { put("radiusM", it) }
          } else {
            put("polygon", JsonArray(z.polygon.map { JsonArray(listOf(JsonPrimitive(it.lat), JsonPrimitive(it.lng))) }))
          }
        }
      }))
      put("slotRules", JsonArray(c.rules.map { r ->
        buildJsonObject {
          put("fulfillment", r.fulfillment)
          put("weekdays", JsonArray(r.weekdays.map { JsonPrimitive(it) }))
          put("startMin", r.startMin)
          put("endMin", r.endMin)
          put("capacity", r.capacity)
          put("cutoffMin", r.cutoffMin)
          //  CHK-07: هزینهٔ زمانِ تحویل (دریافت از دکان همیشه ۰)
          put("feeMinor", r.feeMinor)
          put("active", r.active)
        }
      }))
      put("allowAsap", allowAsap)
      put("slotDays", c.slotDays)
    }
  }
}

@Serializable
data class StorefrontZoneDto(
  val id: String = "",
  val name: String = "",
  val kind: String = "polygon",
  val polygon: List<List<Double>> = emptyList(),
  val centerLat: Double? = null,
  val centerLng: Double? = null,
  val radiusM: Int? = null,
  val feeMinor: Long = 0,
  val minOrderMinor: Long = 0,
  val freeOverMinor: Long? = null,
  /** CHK-06 — null: زیرِ حداقل رد؛ سرورِ پیش از ۱۳۸ نمی‌فرستد */
  val smallBasketFeeMinor: Long? = null,
  val active: Boolean = true,
) {
  fun draft(): StorefrontDelivery.ZoneDraft = StorefrontDelivery.ZoneDraft(
    name = name, kind = kind,
    fee = ir.vil3ntec.tohid.core.storefront.StorefrontOwner.formatMinor(feeMinor),
    min = ir.vil3ntec.tohid.core.storefront.StorefrontOwner.formatMinor(minOrderMinor),
    free = freeOverMinor?.let { ir.vil3ntec.tohid.core.storefront.StorefrontOwner.formatMinor(it) } ?: "",
    small = smallBasketFeeMinor?.let { ir.vil3ntec.tohid.core.storefront.StorefrontOwner.formatMinor(it) } ?: "",
    center = if (centerLat != null && centerLng != null) "$centerLat, $centerLng" else "",
    radiusKm = radiusM?.let { (it / 1000.0).toString().removeSuffix(".0") } ?: "2",
    polygon = StorefrontDelivery.formatPolygon(polygon.filter { it.size == 2 }.map { StorefrontDelivery.LatLng(it[0], it[1]) }),
    active = active,
  )
}

@Serializable
data class StorefrontSlotRuleDto(
  val id: String = "",
  val fulfillment: String = "delivery",
  val weekdays: List<Int> = emptyList(),
  val startMin: Int = 0,
  val endMin: Int = 0,
  val capacity: Int = 1,
  val cutoffMin: Int = 0,
  /** CHK-07 — هزینهٔ اضافهٔ این بازه؛ سرورِ پیش از ۱۳۸ نمی‌فرستد ⇒ ۰ */
  val feeMinor: Long = 0,
  val active: Boolean = true,
) {
  fun draft(): StorefrontDelivery.RuleDraft = StorefrontDelivery.RuleDraft(
    fulfillment = fulfillment, weekdays = weekdays.toSet(),
    range = ir.vil3ntec.tohid.core.storefront.StorefrontOwner.formatHm(startMin) + "-" +
      ir.vil3ntec.tohid.core.storefront.StorefrontOwner.formatHm(endMin),
    capacity = capacity.toString(), cutoff = cutoffMin.toString(),
    fee = ir.vil3ntec.tohid.core.storefront.StorefrontOwner.formatMinor(feeMinor), active = active,
  )
}

@Serializable
data class StorefrontDeliveryDto(
  val zones: List<StorefrontZoneDto> = emptyList(),
  val slotRules: List<StorefrontSlotRuleDto> = emptyList(),
  val allowAsap: Boolean = true,
  val slotDays: Int = 3,
)
