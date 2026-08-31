package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.IssuedCodeDto
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.MembersPageDto
import ir.vil3ntec.tohid.core.model.ShopStateDto
import ir.vil3ntec.tohid.core.model.StaffCodeDto
import ir.vil3ntec.tohid.core.model.StaffCodesDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  دکان، کارمندان و کدهای شاگرد.
 *
 *  «چند کاربر روی یک دکان» قابلیتی است که در صفحهٔ اشتراک فروخته می‌شود؛
 *  اجازه‌اش را سرور می‌دهد نه برنامه. اگر سرور `403` داد یعنی اشتراک این
 *  را ندارد و پیامِ خودِ سرور همان را می‌گوید — دکمه را پنهان نمی‌کنیم و
 *  وانمود نمی‌کنیم که قابلیت نیست.
 */
class ShopRepository(private val api: ApiClient) {

  /** دکانِ من — یا `null` اگر هنوز دکانی ساخته نشده */
  suspend fun current(): ApiResult<ShopStateDto> =
    result { ApiJson.decode<ShopStateDto>(api.get(ApiEndpoints.Shop.ME)) }

  suspend fun create(name: String): ApiResult<ShopStateDto> = result {
    ApiJson.decode<ShopStateDto>(
      api.post(ApiEndpoints.Shop.CREATE, buildJsonObject { put("name", JsonPrimitive(name.trim())) })
    )
  }

  /** پیوستن با کدِ شاگرد */
  suspend fun join(code: String): ApiResult<ShopStateDto> = result {
    ApiJson.decode<ShopStateDto>(
      api.post(ApiEndpoints.Shop.JOIN, buildJsonObject { put("code", JsonPrimitive(code.trim())) })
    )
  }

  /* ------------------------------ کارمندان ------------------------------ */

  suspend fun members(): ApiResult<MembersPageDto> =
    result { ApiJson.decode<MembersPageDto>(api.get(ApiEndpoints.Shop.MEMBERS)) }

  suspend fun removeMember(memberId: String): ApiResult<Unit> =
    result { api.delete(ApiEndpoints.Shop.member(memberId)); Unit }

  suspend fun changeRole(memberId: String, role: String): ApiResult<MemberDto> = result {
    val body = api.put(
      ApiEndpoints.Shop.member(memberId),
      buildJsonObject { put("role", JsonPrimitive(role)) },
    )
    ApiJson.decodeAt<MemberDto>(body, "member") ?: MemberDto()
  }

  /* ------------------------------ کدهای شاگرد ------------------------------ */

  suspend fun staffCodes(): ApiResult<List<StaffCodeDto>> =
    result { ApiJson.decode<StaffCodesDto>(api.get(ApiEndpoints.Shop.STAFF_CODES)).codes }

  /**
   *  کدِ ثابتِ دکان.
   *
   *  سرورِ قدیمی این مسیر را ندارد و نبودنش نباید بقیهٔ صفحه را خراب کند،
   *  پس اینجا خطا به «کدی نیست» تبدیل می‌شود نه به یک پیامِ قرمز.
   */
  suspend fun standingCode(): String =
    result { ApiJson.text(api.get(ApiEndpoints.Shop.STAFF_CODE), "code") }.valueOrNull().orEmpty()

  suspend fun rotateStandingCode(): ApiResult<IssuedCodeDto> =
    result { ApiJson.decode<IssuedCodeDto>(api.post(ApiEndpoints.Shop.STAFF_CODE_ROTATE)) }

  /** کدِ تازه. متنِ کد فقط همین یک بار برمی‌گردد و بعد فقط نشانه‌اش می‌ماند. */
  suspend fun createStaffCode(
    role: String = "staff",
    maxUses: Int = 1,
    expiresInDays: Int = 0,
  ): ApiResult<IssuedCodeDto> = result {
    ApiJson.decode<IssuedCodeDto>(
      api.post(
        ApiEndpoints.Shop.STAFF_CODE,
        buildJsonObject {
          put("role", JsonPrimitive(role))
          put("maxUses", JsonPrimitive(maxUses))
          put("expiresInDays", JsonPrimitive(expiresInDays))
        },
      )
    )
  }

  suspend fun revokeStaffCode(codeId: String): ApiResult<Unit> =
    result { api.delete(ApiEndpoints.Shop.staffCode(codeId)); Unit }
}
