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
import kotlinx.serialization.json.JsonNull
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

  /**
   *  `caps=saleOnHandover`: این برنامه فروشِ تحویل را خودش ثبت می‌کند ⇒ سرور کفِ تحویلِ این دکان
   *  را (اگر نیست) همین لحظه می‌گذارد؛ تحویلِ پیش از آن هرگز فروش نمی‌گیرد.
   */
  suspend fun config(): ApiResult<OrdersConfigDto> =
    result { ApiJson.decode<OrdersConfigDto>(api.get("$ROOT/config?caps=saleOnHandover")) }

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
   *  همگام‌شدهٔ دکان نیست (`items`)، و برگشته‌هایی که فروششان لغو نشده (`reversals`).
   *  [since]: کفِ سرور؛ [after]: صفحهٔ بعد؛ [orderId]: فقط همان سفارش (پیش از هر ثبت).
   */
  suspend fun handover(since: Long, after: String? = null, orderId: String? = null): ApiResult<HandoverPageDto> = result {
    val q = buildString {
      append("$ROOT/handover?since=").append(since).append("&limit=20")
      if (!after.isNullOrBlank()) append("&after=").append(enc(after))
      if (!orderId.isNullOrBlank()) append("&orderId=").append(enc(orderId))
    }
    ApiJson.decode<HandoverPageDto>(api.get(q))
  }

  suspend fun create(clientKey: String, body: OrderDraft.Body): ApiResult<OrderDto> =
    result { order(api.post(ROOT, createJson(clientKey, body))) }

  /**
   *  ویرایشِ دستورِ تحویل تا پیش از ارسال (CHK-24) — فقط کلیدهای آمده؛ `expectedVersion` ِ کهنه ⇒
   *  ۴۰۹ و هیچ تغییری (ویرایشِ هم‌زمانِ دو دستگاه بی‌صدا رونویسی نمی‌شود).
   */
  suspend fun editDelivery(id: String, expectedVersion: Long, body: JsonObject): ApiResult<OrderDto> =
    result { order(api.post("$ROOT/${enc(id)}/delivery", JsonObject(body + ("expectedVersion" to JsonPrimitive(expectedVersion))))) }

  /**
   *  «بررسی شد» ِ صاحب یا مدیر برای ردیفِ بررسیِ فروشِ تحویل (`before_floor`، `reverse_blocked`) —
   *  ردیف برای همهٔ دستگاه‌ها می‌رود؛ تکرار بی‌اثر.
   */
  suspend fun reviewAck(id: String, kind: String): ApiResult<Unit> = result {
    api.post("$ROOT/${enc(id)}/review-ack", buildJsonObject { put("kind", kind) })
    Unit
  }

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

  /**
   *  ORD-07 «عقب انداختن» (`POST /:id/delay`، پرچمِ `shop.orders.delay`): [key] یک بار برای هر پنجره ⇒
   *  «تأیید» ِ دوباره پس از قطعِ نت دو بار عقب نمی‌برد. [expectedReadyAt] همان زمانی است که کاربر دید
   *  (`null` = «بی زمان»)؛ کسِ دیگری زودتر عوضش کرده ⇒ ۴۰۹ `ready_changed` و هیچ تغییری.
   */
  suspend fun delay(id: String, key: String, minutes: Int, reason: String, note: String, expectedReadyAt: Long?): ApiResult<OrderDto> = result {
    val body = buildJsonObject {
      put("clientKey", key)
      put("minutes", minutes)
      put("expectedReadyAt", expectedReadyAt?.let { JsonPrimitive(it) } ?: JsonNull)
      if (reason.isNotEmpty()) put("reason", reason)
      if (note.isNotBlank()) put("note", note.trim().take(300))
    }
    order(api.post("$ROOT/${enc(id)}/delay", body))
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
        //  CHK-14: فقط وقتی نوشته شده (سرورِ کهنه/پرچمِ خاموش همان بدنهٔ پیشین را می‌بیند)
        b.cashTenderMinor?.let { t -> put("delivery", buildJsonObject { put("cashTenderMinor", t) }) }
      }
      put("lines", buildJsonArray {
        for (l in b.lines) add(buildJsonObject {
          //  POS-38: قلمِ صندوق شناسهٔ کالا و واحد دارد؛ تهی ⇒ نوشته نمی‌شود (بدنهٔ سفارشِ دستی همان پیشین)
          if (l.productId.isNotEmpty()) put("productId", l.productId)
          put("name", l.name)
          if (l.unit.isNotEmpty()) put("unit", l.unit)
          put("qtyMilli", l.qtyMilli)
          put("unitPriceMinor", l.unitPriceMinor)
        })
      })
      if (b.note.isNotEmpty()) put("note", b.note)
      if (b.test) put("test", JsonPrimitive(true))
      //  CHK-15: فقط وقتی برگزیده شده (سرورِ کهنه/پرچمِ خاموش همان بدنهٔ پیشین را می‌بیند)
      if (b.substitution.isNotEmpty()) put("substitution", b.substitution)
    }

    /** کلیدِ یکتایی — یک بار برای هر فرم */
    fun newKey(): String = "app-" + java.util.UUID.randomUUID().toString()
  }
}
