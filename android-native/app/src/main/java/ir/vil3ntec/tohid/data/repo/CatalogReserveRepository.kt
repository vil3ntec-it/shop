package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  M5 — مهلتِ نگه‌داشتنِ موجودی برای سفارشِ آنلاین (`/api/shop/catalog`، `server/src/lib/stock-reserve.js`).
 *
 *  ⛔ پیش‌فرض خاموش: `GET /config` فقط وقتی `shop.catalog` و `shop.stock_reserve` هر دو روشن‌اند `reserve`
 *  دارد؛ بی آن صفحه هیچ نشان نمی‌دهد. سرور مهلت را دوباره می‌سنجد (عددِ صحیح ۶۰ تا ۸۶۴۰۰ ثانیه) و با
 *  پرچمِ خاموش نادیده‌اش می‌گیرد. فقط همین یک فیلد فرستاده می‌شود؛ بقیهٔ تنظیمِ موجودی همان می‌ماند.
 */
class CatalogReserveRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<CatalogConfigDto> = result {
    ApiJson.decode<CatalogConfigDto>(api.get("$ROOT/config"))
  }

  /** ثانیه — پیش از این با `StockHold.parseTtlMinutes` سنجیده شده است. پاسخ = مهلتِ ثبت‌شدهٔ سرور (یا null). */
  suspend fun saveTtl(sec: Int): ApiResult<Int?> = result {
    val body = buildJsonObject { put("reserveTtlSec", sec) }
    ApiJson.decode<CatalogSettingsDto>(api.put("$ROOT/settings", body)["settings"] ?: JsonNull).reserveTtlSec
  }

  companion object {
    const val ROOT = "/shop/catalog"
  }
}

@Serializable
data class CatalogReserveDto(val enabled: Boolean = false, val ttlSec: Int = 0)

@Serializable
data class CatalogConfigDto(
  val enabled: Boolean = false,
  /** فقط با رزروِ نرمِ روشن (M5) */
  val reserve: CatalogReserveDto? = null,
)

@Serializable
data class CatalogSettingsDto(val reserveTtlSec: Int? = null)
