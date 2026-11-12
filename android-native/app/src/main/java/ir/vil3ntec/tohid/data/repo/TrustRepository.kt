package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** `GET /shop/trust/config` — پرچمِ `market.trust` ِ همین دکان، مهلتِ اعتراض و «می‌تواند پاسخ دهد؟» (`trust.respond`) */
@Serializable
data class TrustConfigDto(
  val enabled: Boolean = false,
  val appealDays: Int = 14,
  val permissions: TrustPermsDto = TrustPermsDto(),
)

@Serializable
data class TrustPermsDto(val respond: Boolean = false)

/** یک رخدادِ پرونده از دیدِ دکان (`shopView.history` ِ سرور): کننده فقط customer/shop/platform/system */
@Serializable
data class TrustEventDto(
  val type: String = "",
  val at: Long = 0,
  val actor: String = "",
  val body: String? = null,
  val outcome: String? = null,
  val kind: String? = null,
)

@Serializable
data class TrustAppealDto(
  val body: String = "",
  val at: Long = 0,
  val outcome: String? = null,
  val reason: String = "",
  val decidedAt: Long? = null,
)

@Serializable
data class TrustSanctionDto(
  val id: String = "",
  val kind: String = "",
  val productId: String? = null,
  val reason: String = "",
  val createdAt: Long = 0,
  val active: Boolean = false,
)

/**
 *  پرونده از دیدِ دکان — همان فهرستِ سفیدِ `lib/market-trust.shopView`: ⛔ هیچ نشانی از شاکی (شماره، نام، توکن، شمارهٔ
 *  سفارش) در پاسخِ سرور نیست و این‌جا هم جایی برایش نیست.
 */
@Serializable
data class TrustCaseDto(
  val id: String = "",
  val kind: String = "",
  val subject: String = "",
  val productId: String? = null,
  val category: String = "",
  val status: String = "",
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val severity: String = "",
  val body: String = "",
  val outcome: String? = null,
  /** فقط در فهرست (`listForShop`) */
  val appealOutcome: String? = null,
  val decisionReason: String = "",
  val decidedAt: Long? = null,
  val appeal: TrustAppealDto? = null,
  val canAppeal: Boolean = false,
  val appealDeadline: Long? = null,
  val evidenceCount: Int = 0,
  val sanctions: List<TrustSanctionDto> = emptyList(),
  val history: List<TrustEventDto> = emptyList(),
)

@Serializable
data class TrustPageDto(val cases: List<TrustCaseDto> = emptyList(), val nextBefore: Long? = null)

@Serializable
private data class TrustOneDto(val case: TrustCaseDto)

@Serializable
private data class TrustOutDto(val case: TrustCaseDto, val replayed: Boolean = false)

/**
 *  ABUSE-01 — شکایت‌ها و گزارش‌های بازارچه دربارهٔ همین دکان، سمتِ برنامه (`/shop/trust`، `routes/shop-trust.js`).
 *  ⛔ دکان و نقش را سرور از توکن می‌گیرد؛ دیدن `trust.view`، پاسخ و اعتراض `trust.respond` (صاحب و مدیر) — سرور می‌سنجد.
 *  پاسخ با `clientId` ِ یک‌بارِ هر برگه: «فرستادن» ِ دوباره پس از قطعِ شبکه پاسخِ دوم نمی‌سازد (`replayed`).
 *  پرچمِ خاموش ⇒ `config.enabled = false` و بقیهٔ درها ۴۰۴.
 */
class TrustRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<TrustConfigDto> = result { ApiJson.decode<TrustConfigDto>(api.get("$ROOT/config")) }

  suspend fun cases(): ApiResult<List<TrustCaseDto>> = result { ApiJson.decode<TrustPageDto>(api.get("$ROOT/cases?limit=50")).cases }

  suspend fun case(id: String): ApiResult<TrustCaseDto> = result { ApiJson.decode<TrustOneDto>(api.get("$ROOT/cases/${enc(id)}")).case }

  suspend fun respond(id: String, text: String, clientId: String): ApiResult<TrustCaseDto> = result {
    ApiJson.decode<TrustOutDto>(api.post("$ROOT/cases/${enc(id)}/responses", buildJsonObject { put("body", text.trim()); put("clientId", clientId) })).case
  }

  /** اعتراض یک بار و در مهلت؛ همان متنِ دوباره (پاسخِ گم‌شده) ⇒ همان نتیجه (`replayed`)، متنِ دیگر ⇒ ۴۰۹ `already_appealed` */
  suspend fun appeal(id: String, text: String): ApiResult<TrustCaseDto> = result {
    ApiJson.decode<TrustOutDto>(api.post("$ROOT/cases/${enc(id)}/appeal", buildJsonObject { put("body", text.trim()) })).case
  }

  companion object {
    const val ROOT = "/shop/trust"
    private fun enc(s: String): String = ir.vil3ntec.tohid.core.nav.RoutePaths.encode(s)
    /** شناسهٔ یک‌بارِ برگه — همان الگوی سرور (`[A-Za-z0-9_.:-]{8,128}`) */
    fun newKey(): String = OrdersRepository.newKey()
  }
}
