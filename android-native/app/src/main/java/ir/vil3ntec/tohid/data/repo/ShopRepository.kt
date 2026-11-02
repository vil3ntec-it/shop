package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.IssuedCodeDto
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.MembersPageDto
import ir.vil3ntec.tohid.core.model.PermsPageDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopDevicesDto
import ir.vil3ntec.tohid.core.model.ShopStateDto
import ir.vil3ntec.tohid.core.model.StaffCodeDto
import ir.vil3ntec.tohid.core.model.StaffCodesDto
import ir.vil3ntec.tohid.core.model.StaffScopeConfigDto
import ir.vil3ntec.tohid.core.model.StaffScopeMembersDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
class ShopRepository(
  private val api: ApiClient,
  /** فقط برای پیوستن به‌عنوانِ پیک: سرور نشستِ تازهٔ دامنه‌دار می‌دهد (DLV-01) */
  private val tokens: ir.vil3ntec.tohid.core.net.TokenStorage? = null,
) {

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
    val body = api.post(ApiEndpoints.Shop.JOIN, buildJsonObject { put("code", JsonPrimitive(code.trim())) })
    /*
     *  کدِ دعوتِ پیک: سرور نشست‌های بی‌دامنهٔ این حساب را بست و نشستِ تازهٔ
     *  `courier` را همین‌جا داد. اگر نگه داشته نشود، برنامه با توکنِ باطل
     *  می‌ماند و کاربر بی‌دلیل بیرون می‌افتد.
     */
    val access = ApiJson.text(body, "accessToken")
    if (access.isNotBlank()) {
      tokens?.save(access, ApiJson.text(body, "refreshToken").ifBlank { null }, ApiJson.long(body, "accessExpiresAt"))
    }
    ApiJson.decode<ShopStateDto>(body)
  }

  /**
   *  «دعوتِ پیک» دیده شود؟ — پرچمِ `shop.delivery` روشن و اشتراکِ تحویل هست.
   *  سرورِ قدیمی یا هر خطا ⇒ نه (دکمهٔ بی‌اثر نشان داده نمی‌شود).
   */
  suspend fun courierInviteAllowed(): Boolean =
    result {
      val body = api.get(ApiEndpoints.Shop.DELIVERY_CONFIG)
      ApiJson.bool(body, "readable") && ApiJson.bool(body, "entitled")
    }.valueOrNull() == true

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

  /** کدِ ثابت و اینکه صاحب دکان خاموشش کرده یا نه (STF-11) — خطا ⇒ ("", false) */
  suspend fun standingState(): Pair<String, Boolean> =
    result {
      val body = api.get(ApiEndpoints.Shop.STAFF_CODE)
      ApiJson.text(body, "code") to ApiJson.bool(body, "disabled")
    }.valueOrNull() ?: ("" to false)

  /** خاموش کردنِ کدِ ثابت؛ «کد تازه» دوباره روشنش می‌کند */
  suspend fun disableStandingCode(): ApiResult<Unit> =
    result { api.post(ApiEndpoints.Shop.STAFF_CODE_DISABLE); Unit }

  /* ------------------------ مجوزها و دستگاه‌ها ------------------------ */

  suspend fun permissions(): ApiResult<PermsPageDto> =
    result { ApiJson.decode<PermsPageDto>(api.get(ApiEndpoints.Shop.PERMISSIONS)) }

  /** لایهٔ یک کارمند — `perms` همان قالبِ `PermCore` ({کلید: {mode}}) */
  suspend fun setMemberPerms(memberId: String, perms: JsonObject): ApiResult<Unit> =
    result { api.put(ApiEndpoints.Shop.memberPerms(memberId), buildJsonObject { put("perms", perms) }); Unit }

  /** STF-06: سرورِ قدیمی یا پرچمِ خاموش ⇒ `enabled = false` و هیچ چیزِ تازه‌ای دیده نمی‌شود */
  suspend fun staffScopeConfig(): StaffScopeConfigDto =
    result { ApiJson.decode<StaffScopeConfigDto>(api.get(ApiEndpoints.Shop.STAFF_SCOPE_CONFIG)) }.valueOrNull() ?: StaffScopeConfigDto()

  /** محدودهٔ هر عضو (صاحب و مدیر)؛ پرچمِ خاموش ⇒ `null` */
  suspend fun staffScopeMembers(): StaffScopeMembersDto? =
    result { ApiJson.decode<StaffScopeMembersDto>(api.get(ApiEndpoints.Shop.STAFF_SCOPE_MEMBERS)) }.valueOrNull()

  /** فقط صاحب؛ `places` ِ خالی = همهٔ مکان‌ها. سرور مرجع است (`location_out_of_scope`) */
  suspend fun setStaffScope(memberId: String, places: List<String>): ApiResult<Unit> =
    result { api.put(ApiEndpoints.Shop.staffScope(memberId), buildJsonObject { put("places", JsonArray(places.map(::JsonPrimitive))) }); Unit }

  /**
   *  ثبتِ کلیدِ عمومیِ تأییدِ همین دستگاه (STF-02). کلیدِ خصوصی و PIN هرگز
   *  نمی‌روند؛ پاسخ شناسهٔ کلید است که باید با `PinCrypto.keyId` بخواند.
   */
  suspend fun registerApproverKey(spkiB64: String, label: String, deviceId: String): ApiResult<String> =
    result {
      val body = api.post(
        ApiEndpoints.Shop.APPROVER_KEYS,
        buildJsonObject {
          put("public_key", JsonPrimitive(spkiB64))
          put("label", JsonPrimitive(label.take(60)))
          put("device_id", JsonPrimitive(deviceId.take(64)))
        },
      )
      ApiJson.text(body["key"] as? JsonObject ?: JsonObject(emptyMap()), "id")
    }

  /** باطل کردنِ کلیدِ تأییدِ قبلیِ همین دستگاه (پس از PINِ تازه) */
  suspend fun revokeApproverKey(id: String): ApiResult<Unit> =
    result { api.delete(ApiEndpoints.Shop.approverKey(id)); Unit }

  suspend fun devices(): ApiResult<List<ShopDeviceDto>> =
    result { ApiJson.decode<ShopDevicesDto>(api.get(ApiEndpoints.Shop.DEVICES)).devices }

  suspend fun disableDevice(deviceId: String): ApiResult<Unit> =
    result { api.post(ApiEndpoints.Shop.deviceDisable(deviceId)); Unit }

  suspend fun enableDevice(deviceId: String): ApiResult<Unit> =
    result { api.post(ApiEndpoints.Shop.deviceEnable(deviceId)); Unit }

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
