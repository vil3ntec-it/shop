package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject

/**
 *  خبرهای دکان — آنچه در نبودِ صاحب دکان اتفاق افتاده.
 *
 *  هشدارهای برنامه از روی دفترِ محلی حساب می‌شوند و فقط همان گوشی را
 *  می‌بینند. خبر فرق دارد: **اتفاق افتاده** و تاریخ دارد. صاحب دکانی که
 *  خانه است می‌خواهد بداند کریم امروز چه فروخت و چه کالایی تمام شد.
 */
class EventsRepository(private val api: ApiClient) {

  data class Event(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val userId: String,
    val userName: String,
    val at: Long,
  )

  data class Feed(
    val events: List<Event> = emptyList(),
    val unread: Int = 0,
    val serverTime: Long = 0,
  )

  /**
   *  فرستادنِ خبرها.
   *
   *  `clientId` شناسه‌ی خودِ برنامه است. گوشی‌ای که آفلاین بوده و صف را
   *  یک‌جا می‌فرستد، اگر پاسخ را نگیرد دوباره می‌فرستد — با این شناسه،
   *  صاحب دکان یک فروش را دو بار نمی‌بیند.
   */
  suspend fun send(events: List<Outgoing>): ApiResult<Int> = result {
    if (events.isEmpty()) return@result 0
    val body = api.post(
      ApiEndpoints.Events.ROOT,
      buildJsonObject {
        put("events", buildJsonArray {
          events.forEach { e ->
            add(buildJsonObject {
              put("kind", JsonPrimitive(e.kind))
              put("title", JsonPrimitive(e.title))
              put("body", JsonPrimitive(e.body))
              put("clientId", JsonPrimitive(e.clientId))
              put("at", JsonPrimitive(e.at))
            })
          }
        })
      },
    )
    ApiJson.long(body, "saved", 0).toInt()
  }

  private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

  data class Outgoing(
    val kind: String,
    val title: String,
    val body: String = "",
    val clientId: String,
    val at: Long = System.currentTimeMillis(),
  )

  suspend fun feed(since: Long = 0): ApiResult<Feed> = result {
    val body = api.get(ApiEndpoints.withQuery(ApiEndpoints.Events.ROOT, mapOf("since" to since)))
    Feed(
      events = (body["events"] as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        Event(
          id = ApiJson.text(row, "id"),
          kind = ApiJson.text(row, "kind"),
          title = ApiJson.text(row, "title"),
          body = ApiJson.text(row, "body"),
          userId = ApiJson.text(row, "userId"),
          userName = ApiJson.text(row, "userName"),
          at = ApiJson.long(row, "at", 0),
        )
      },
      unread = ApiJson.long(body, "unread", 0).toInt(),
      serverTime = ApiJson.long(body, "serverTime", 0),
    )
  }

  /** «تا اینجا خواندم» — نقطه‌ی قرمزِ زنگ از همین می‌آید */
  suspend fun markSeen(at: Long): ApiResult<Unit> = result {
    api.post(ApiEndpoints.Events.SEEN, buildJsonObject { put("at", JsonPrimitive(at)) })
    Unit
  }
}
