package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 *  یک اجازهٔ دسترسیِ پشتیبانی به دادهٔ همین دکان — همان `shape` ِ `lib/support-access.js`. `consent`: `tenant` (با رضایتِ صاحب)
 *  یا `break_glass` («شکستنِ شیشه»: فقط مالکِ پلتفرم، با دلیلِ نوشته، خودش بسته می‌شود). `status` را سرور با ساعتِ خودش
 *  می‌سازد (`pending`، `active`، `denied`، `revoked`، `expired`).
 */
@Serializable
data class SupportGrantDto(
  val id: String = "",
  val scope: String = "",
  val consent: String = "",
  val status: String = "",
  val reason: String = "",
  val ticketId: String = "",
  val minutes: Int = 0,
  val requestedBy: String = "",
  val createdAt: Long = 0,
  val startsAt: Long? = null,
  val expiresAt: Long? = null,
  val requestExpiresAt: Long? = null,
  val uses: Int = 0,
)

@Serializable
data class SupportAccessDto(val grants: List<SupportGrantDto> = emptyList())

@Serializable
private data class SupportGrantOut(val grant: SupportGrantDto)

/**
 *  TEN-04 / ۲-۹ — دسترسیِ پشتیبانی، سمتِ صاحبِ دکان (`/shop/support-access`، `routes/shop-support-access.js`): دیدن، پذیرفتن،
 *  رد و بستن. ⛔ فقط صاحب (سرور: ۴۰۳ `owner_only`)؛ «پذیرفتن» تأییدِ دوبارهٔ ورود می‌خواهد (`reauth_required` ⇐ همان پنجرهٔ
 *  `ReauthHost` خودش باز می‌شود و درخواست یک بار دوباره می‌رود)؛ رد و بستن (کم کردنِ دسترسی) نه.
 */
class SupportAccessRepository(private val api: ApiClient) {

  suspend fun list(): ApiResult<List<SupportGrantDto>> = result { ApiJson.decode<SupportAccessDto>(api.get(ROOT)).grants }

  suspend fun accept(id: String): ApiResult<SupportGrantDto> = decide(id, "accept")
  suspend fun deny(id: String): ApiResult<SupportGrantDto> = decide(id, "deny")
  suspend fun revoke(id: String): ApiResult<SupportGrantDto> = decide(id, "revoke")

  private suspend fun decide(id: String, action: String): ApiResult<SupportGrantDto> = result {
    ApiJson.decode<SupportGrantOut>(api.post("$ROOT/${java.net.URLEncoder.encode(id, "UTF-8")}/$action", JsonObject(emptyMap()))).grant
  }

  companion object {
    const val ROOT = "/shop/support-access"
  }
}
