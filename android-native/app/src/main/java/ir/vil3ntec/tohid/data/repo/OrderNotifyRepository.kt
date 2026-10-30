package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderNotice
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 *  اعلانِ سفارش — `/shop/order-notify` (`server/src/routes/order-notify.js`، v3 §۳۰، M6).
 *  ⛔ پیش‌فرض خاموش: بی `shop.order_notify` فقط `/config` جواب می‌دهد و `enabled` نه است.
 *  ⛔ متنِ اعلان دادهٔ مشتری ندارد؛ لمسش سفارش را تازه از `/shop/orders/:id` می‌خواند.
 */
class OrderNotifyRepository(private val api: ApiClient) {

  suspend fun enabled(): ApiResult<Boolean> =
    result { (api.get("$ROOT/config")["enabled"] as? JsonPrimitive)?.booleanOrNull == true }

  suspend fun pending(): ApiResult<List<OrderNotice>> = result {
    (api.get("$ROOT/pending")["notifications"] as? JsonArray).orEmpty().mapNotNull { e ->
      val o = e as? JsonObject ?: return@mapNotNull null
      val id = (o["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
      val orderId = (o["orderId"] as? JsonPrimitive)?.content ?: return@mapNotNull null
      OrderNotice(id, orderId, (o["orderNo"] as? JsonPrimitive)?.longOrNull ?: 0)
    }
  }

  /** «باز شد» ⇒ سرور ردیفِ outbox را `acked` با `ack_source = 'device'` می‌کند؛ تکرار بی‌خطر است. */
  suspend fun ack(notifId: String): ApiResult<Unit> =
    result { api.post("$ROOT/${enc(notifId)}/ack", buildJsonObject { put("kind", "opened") }); Unit }

  private companion object {
    const val ROOT = "/shop/order-notify"
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
  }
}
