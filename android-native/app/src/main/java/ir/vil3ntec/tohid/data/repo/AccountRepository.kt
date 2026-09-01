package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.DeviceDto
import ir.vil3ntec.tohid.core.model.DevicesDto
import ir.vil3ntec.tohid.core.model.MeDto
import ir.vil3ntec.tohid.core.model.PlansDto
import ir.vil3ntec.tohid.core.model.SubscriptionDto
import ir.vil3ntec.tohid.core.model.UserWrapDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  حسابِ خودِ کاربر: دستگاه‌ها، اشتراک، درخواستِ خرید.
 */
class AccountRepository(private val api: ApiClient) {

  /* ------------------------------ خودِ حساب ------------------------------ */

  /** حسابِ همین کاربر، از سرور — نام و ایمیل و شماره و روزِ ساختنش */
  suspend fun me(): ApiResult<MeDto> =
    result { ApiJson.decode<MeDto>(api.get(ApiEndpoints.Me.ROOT)) }

  /**
   *  ویرایشِ پروفایل.
   *
   *  نام هر وقت عوض می‌شود. شماره فقط **یک بار** ثبت می‌شود و از آن به
   *  بعد سرور خودش ردش می‌کند — همان‌طور که ایمیل از اول قابلِ عوض کردن
   *  نبوده. اینجا هم فرستادنِ شماره برای کسی که از قبل شماره دارد،
   *  خطای `phone_locked` می‌گیرد؛ برنامه آن را نشان می‌دهد و بس.
   *
   *  @param phone خالی یعنی «دست نزن»، نه «پاک کن».
   */
  suspend fun updateProfile(name: String, phone: String = ""): ApiResult<UserWrapDto> = result {
    ApiJson.decode<UserWrapDto>(
      api.put(
        ApiEndpoints.Me.ROOT,
        buildJsonObject {
          put("name", JsonPrimitive(name.trim()))
          if (phone.isNotBlank()) put("phone", JsonPrimitive(phone.trim()))
        },
      )
    )
  }

  /* ------------------------------ دستگاه‌ها ------------------------------ */

  suspend fun devices(): ApiResult<List<DeviceDto>> =
    result { ApiJson.decode<DevicesDto>(api.get(ApiEndpoints.Me.DEVICES)).devices }

  /** بستنِ نشستِ یک دستگاه — برای گوشیِ گم‌شده */
  suspend fun revokeDevice(deviceId: String): ApiResult<Unit> =
    result { api.delete(ApiEndpoints.Me.device(deviceId)); Unit }

  /* ------------------------------ اشتراک ------------------------------ */

  suspend fun subscription(): ApiResult<SubscriptionDto> =
    result { ApiJson.decode<SubscriptionDto>(api.get(ApiEndpoints.Me.SUBSCRIPTION)) }

  /**
   *  فهرستِ پلن‌ها.
   *
   *  این مسیر روی سرور `requireUser` دارد. لایهٔ قبلی آن را **بدونِ
   *  توکن** صدا می‌زد، پس اگر روزی صفحه‌ای از آن استفاده می‌کرد، همیشه
   *  ۴۰۱ می‌گرفت. اینجا با توکن فرستاده می‌شود.
   */
  suspend fun plans(): ApiResult<PlansDto> =
    result { ApiJson.decode<PlansDto>(api.get(ApiEndpoints.Me.PLANS)) }

  /** درخواستِ خرید — مدیر بعد از گرفتنِ پول فعالش می‌کند */
  suspend fun requestPurchase(plan: String, note: String = ""): ApiResult<Unit> = result {
    api.post(
      ApiEndpoints.Me.PURCHASE_REQUEST,
      buildJsonObject {
        put("plan", JsonPrimitive(plan))
        put("note", JsonPrimitive(note))
      },
    )
    Unit
  }

}
