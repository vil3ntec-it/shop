package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.HandoverPageDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPageDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.model.OrdersSummaryDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDraft
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  بخشِ «سفارش‌ها»ی برنامهٔ دکان — `/shop/orders` (فاز ۴).
 *
 *  ⛔ دکان و نقش را سرور از توکن می‌گیرد؛ این‌جا هیچ شناسهٔ دکانی فرستاده نمی‌شود.
 *  ⛔ وضعیت را فقط سرور عوض می‌کند؛ `from` همراهِ هر گذار می‌رود تا اگر کسِ
 *  دیگری زودتر زده بود، ۴۰۹ (`status_changed`) بیاید نه گذارِ روی وضعیتِ کهنه.
 *  ⛔ سفارشِ دستی با `clientKey`: تکرارِ همان فرم (قطعِ نت، دو بار زدن) همان
 *  سفارش است، نه دومی (CHK-19).
 */
class OrdersRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<OrdersConfigDto> =
    result { ApiJson.decode<OrdersConfigDto>(api.get("$ROOT/config")) }

  suspend fun summary(): ApiResult<OrdersSummaryDto> =
    result { ApiJson.decode<OrdersSummaryDto>(api.get("$ROOT/summary")) }

  suspend fun list(tab: String, cursor: String? = null, limit: Int = PAGE): ApiResult<OrderPageDto> = result {
    val q = buildString {
      append("$ROOT?tab=").append(enc(tab)).append("&limit=").append(limit)
      if (!cursor.isNullOrBlank()) append("&cursor=").append(enc(cursor))
    }
    ApiJson.decode<OrderPageDto>(api.get(q))
  }

  suspend fun get(id: String): ApiResult<OrderDto> =
    result { order(api.get("$ROOT/${enc(id)}")) }

  /**
   *  سفارش‌های تحویل‌شده (مشتری گرفت / به پیک داده شد / رسید) که فروششان هنوز در دفترِ
   *  همگام‌شدهٔ دکان نیست — `OrderFulfillment` برای هر کدام فروشِ عادی می‌سازد. [since]: فقط
   *  تحویل‌های پس از نخستین به‌کارگیریِ همین قابلیت روی این دستگاه.
   */
  suspend fun handover(since: Long): ApiResult<HandoverPageDto> =
    result { ApiJson.decode<HandoverPageDto>(api.get("$ROOT/handover?since=$since&limit=20")) }

  suspend fun create(clientKey: String, body: OrderDraft.Body): ApiResult<OrderDto> =
    result { order(api.post(ROOT, createJson(clientKey, body))) }

  suspend fun transition(
    id: String,
    to: String,
    from: String,
    reason: String = "",
    note: String = "",
    readyInMinutes: Int? = null,
  ): ApiResult<OrderDto> = result {
    val body = buildJsonObject {
      put("to", to)
      put("from", from)
      if (reason.isNotEmpty()) put("reason", reason)
      if (note.isNotBlank()) put("note", note.trim())
      if (readyInMinutes != null) put("readyInMinutes", readyInMinutes)
    }
    order(api.post("$ROOT/${enc(id)}/transition", body))
  }

  private fun order(body: JsonObject): OrderDto =
    ApiJson.decodeAt<OrderDto>(body, "order") ?: throw ApiFailure.InvalidResponse()

  companion object {
    const val ROOT = "/shop/orders"
    const val PAGE = 30

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /** بدنهٔ `POST /shop/orders` — همان شکلی که `parseCreate`ِ سرور می‌خواند */
    fun createJson(clientKey: String, b: OrderDraft.Body): JsonObject = buildJsonObject {
      put("clientKey", clientKey)
      put("source", b.source)
      put("fulfillment", b.fulfillment)
      put("paymentMethod", "cod")
      put("customer", buildJsonObject {
        put("name", b.customerName)
        if (b.customerPhone.isNotEmpty()) put("phone", b.customerPhone)
      })
      if (b.fulfillment == "delivery") {
        put("address", buildJsonObject {
          if (b.addressText.isNotEmpty()) put("text", b.addressText)
          if (b.landmark.isNotEmpty()) put("landmark", b.landmark)
        })
        if (b.deliveryFeeMinor > 0) put("deliveryFeeMinor", b.deliveryFeeMinor)
      }
      put("lines", buildJsonArray {
        for (l in b.lines) add(buildJsonObject {
          put("name", l.name)
          put("qtyMilli", l.qtyMilli)
          put("unitPriceMinor", l.unitPriceMinor)
        })
      })
      if (b.note.isNotEmpty()) put("note", b.note)
      if (b.test) put("test", JsonPrimitive(true))
    }

    /** کلیدِ یکتایی — یک بار برای هر فرم */
    fun newKey(): String = "app-" + java.util.UUID.randomUUID().toString()
  }
}
