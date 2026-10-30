package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoOrderDto
import ir.vil3ntec.tohid.data.PoPageDto
import ir.vil3ntec.tohid.data.PoReceivedDto
import ir.vil3ntec.tohid.data.PoReceiptLineDto
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 *  سفارشِ خرید — `/api/shop/purchase-orders` (`server/src/routes/purchase-orders.js`، PUR-02/04).
 *
 *  ⛔ دکان و نقش را سرور از توکن می‌گیرد. ⛔ بی پرچمِ `shop.purchase_orders` فقط `config` جواب
 *  می‌دهد و صفحه دیده نمی‌شود. ساختن با `Idempotency-Key` و رسید با شناسهٔ خودش (کلیدِ یکتا):
 *  تکرارِ همان درخواست (قطعِ نت، دو بار زدن) همان سفارش/رسید است، نه دومی.
 */
class PurchaseOrderRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<PoConfigDto> = result { ApiJson.decode<PoConfigDto>(api.get("$ROOT/config")) }

  suspend fun list(state: String? = null, cursor: String? = null): ApiResult<PoPageDto> = result {
    val q = buildString {
      append("$ROOT?limit=50")
      if (!state.isNullOrBlank()) append("&state=").append(enc(state))
      if (!cursor.isNullOrBlank()) append("&cursor=").append(enc(cursor))
    }
    ApiJson.decode<PoPageDto>(api.get(q))
  }

  suspend fun get(id: String): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.get("$ROOT/${enc(id)}")).order }

  /** پیش‌نویسِ تازه؛ [key] را صفحه یک بار برای همین فرم می‌سازد */
  suspend fun create(key: String, draft: Draft): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.postKeyed(ROOT, draft.json(version = null), key)).order }

  /** ویرایشِ پیش‌نویس با نسخهٔ دیده‌شده (کهنه ⇒ ۴۰۹ `version_conflict`) */
  suspend fun update(id: String, version: Int, draft: Draft): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.put("$ROOT/${enc(id)}", draft.json(version))).order }

  suspend fun send(id: String): ApiResult<PurchaseOrderDto> =
    result { ApiJson.decode<PoOrderDto>(api.post("$ROOT/${enc(id)}/send")).order }

  suspend fun cancel(id: String, reason: String): ApiResult<PurchaseOrderDto> = result {
    ApiJson.decode<PoOrderDto>(api.post("$ROOT/${enc(id)}/cancel", buildJsonObject { put("reason", reason) })).order
  }

  /** رسیدِ دریافت؛ [receiptId] کلیدِ یکتای همین رسید است (صفحه یک بار می‌سازد) */
  suspend fun receive(id: String, receiptId: String, lines: List<PoReceiptLineDto>, allowOver: Boolean, note: String): ApiResult<PoReceivedDto> =
    result {
      val body = buildJsonObject {
        put("id", receiptId)
        put("allowOver", allowOver)
        put("note", note)
        put("lines", buildJsonArray {
          lines.forEach { l ->
            add(buildJsonObject {
              put("lineId", l.lineId)
              put("receivedMilli", l.receivedMilli)
              put("damagedMilli", l.damagedMilli)
              put("priceMinor", l.priceMinor)
            })
          }
        })
      }
      ApiJson.decode<PoReceivedDto>(api.post("$ROOT/${enc(id)}/receipts", body))
    }

  data class Draft(
    val supplierId: String,
    val supplierName: String,
    val lines: List<PoLineDto>,
    val expectedDate: String?,
    val note: String,
  ) {
    fun json(version: Int?): JsonObject = buildJsonObject {
      if (version != null) put("version", version)
      put("supplierId", supplierId)
      put("supplierName", supplierName)
      if (!expectedDate.isNullOrBlank()) put("expectedDate", expectedDate)
      put("note", note)
      put("lines", buildJsonArray {
        lines.forEach { l ->
          add(buildJsonObject {
            put("id", l.id)
            put("productId", l.productId)
            put("name", l.name)
            put("unit", l.unit)
            put("qtyMilli", l.qtyMilli)
            put("priceMinor", l.priceMinor)
          })
        }
      })
    }
  }

  private companion object {
    const val ROOT = "/shop/purchase-orders"
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
  }
}
