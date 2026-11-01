package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.courier.CourierEvent
import ir.vil3ntec.tohid.core.courier.EventResult
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 *  درِ پیک (`/api/courier/…`) — DLV-01/02/08/10، v3 §۳.۱۱.
 *
 *  صفحه‌ها نشانیِ سرور و توکن را نمی‌دانند؛ فقط همین مخزن را صدا می‌زنند.
 *  ⛔ توکنِ پیک جز همین درها جایی را باز نمی‌کند (سرور: `403 courier_scope`).
 */
class CourierRepository(private val api: ApiClient) {

  /**
   *  `callEnabled`: دکان دکمهٔ «تماس با مشتری» را روشن کرده (`shop.delivery_courier_call`)؛
   *  `routeEnabled`/`ratingsEnabled`: «مسیرِ پیشنهادی» (DLV-04) و «امتیاز» (DLV-18) — پرچم‌های خاموشِ دکان
   */
  data class Me(
    val id: String, val name: String, val active: Boolean, val cashCapMinor: Long, val callEnabled: Boolean = false,
    val routeEnabled: Boolean = false, val ratingsEnabled: Boolean = false,
  )

  suspend fun me(): ApiResult<Me> = result {
    val out = api.get(ApiEndpoints.Courier.ME)
    val c = out["courier"] as? JsonObject ?: JsonObject(emptyMap())
    Me(
      CourierWire.str(c, "id"), CourierWire.str(c, "name"), CourierWire.bool(c, "active"),
      CourierWire.long(c, "cashCapMinor") ?: 0L, CourierWire.bool(out, "callEnabled"),
      CourierWire.bool(out, "routeEnabled"), CourierWire.bool(out, "ratingsEnabled"),
    )
  }

  /**
   *  شمارهٔ کاملِ مشتری برای همین یک تماس (DLV-02). ⛔ جایی نگه داشته نمی‌شود: فقط به
   *  صفحهٔ شماره‌گیر داده می‌شود؛ کارت و فهرست همان شمارهٔ پوشیده را دارند.
   */
  suspend fun callNumber(assignmentId: String): ApiResult<String> = result {
    CourierWire.str(api.post(ApiEndpoints.Courier.call(assignmentId), JsonObject(emptyMap())), "phone")
  }

  /**
   *  امتیازِ پیک به یک تحویل (DLV-18) — فقط آنلاین، یک بار؛ همان امتیاز دوباره ⇒ همان پاسخ.
   *  ⇒ `true` وقتی ثبت شد یا پیش‌تر ثبت شده بود.
   */
  suspend fun rate(assignmentId: String, stars: Int, tags: List<String>): ApiResult<Boolean> = result {
    api.post(
      ApiEndpoints.Courier.rating(assignmentId),
      buildJsonObject {
        put("stars", JsonPrimitive(stars))
        put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
      },
    )
    true
  }

  /** `scope`: «active» (زنده) یا «done» (پایان‌یافته؛ سرور نشانی را نمی‌دهد) */
  suspend fun missions(scope: String): ApiResult<List<MissionCard>> = result {
    val items = api.get(ApiEndpoints.Courier.missions(scope))["items"] as? JsonArray ?: JsonArray(emptyList())
    items.mapNotNull { (it as? JsonObject)?.let(CourierWire::card) }
  }

  /** یک بسته از صف؛ پاسخ برای هر رویداد جدا (applied/duplicate/unchanged/rejected) */
  suspend fun send(deviceId: String, events: List<CourierEvent>): ApiResult<List<EventResult>> = result {
    val body = buildJsonObject {
      put("deviceId", JsonPrimitive(deviceId))
      put("events", buildJsonArray { events.forEach { add(CourierWire.eventJson(it)) } })
    }
    val results = api.post(ApiEndpoints.Courier.EVENTS, body)["results"] as? JsonArray ?: JsonArray(emptyList())
    results.map { el ->
      val o = el as? JsonObject ?: JsonObject(emptyMap())
      CourierWire.result(o)
    }
  }

  /** عکسِ بسته (DLV-08) — `parcelOnly` همیشه true: پیک پیش از گرفتن تأیید کرده */
  suspend fun photo(assignmentId: String, eventId: String, imageBase64: String, deviceTime: Long): ApiResult<String> = result {
    val out = api.post(
      ApiEndpoints.Courier.photos(assignmentId),
      buildJsonObject {
        put("eventId", JsonPrimitive(eventId))
        put("imageBase64", JsonPrimitive(imageBase64))
        put("parcelOnly", JsonPrimitive(true))
        put("deviceTime", JsonPrimitive(deviceTime))
      },
    )
    CourierWire.str(out, "photoId")
  }
}
