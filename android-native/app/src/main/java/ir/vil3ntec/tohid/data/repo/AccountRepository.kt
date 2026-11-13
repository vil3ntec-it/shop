package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.AccountDeletionDto
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
   *  ویرایشِ پروفایل («فقط ایمیل»، ۱۴۰۵/۰۸/۲۳).
   *
   *  نام هر وقت عوض می‌شود. شماره فقط **فیلدِ تماس** است (راهِ ورود نیست): بی کد افزوده، عوض یا پاک (`""`) می‌شود.
   *  ایمیل راهِ ورود است و فقط حسابِ بی‌ایمیل آن را یک بار با کدِ همان ایمیل می‌افزاید (AUTH-08 ⇐ ایمیل)؛ ایمیلِ ثبت‌شده
   *  از برنامه عوض نمی‌شود (`email_locked`).
   *
   *  @param phone null ⇒ «دست نزن»؛ `""` ⇒ پاک؛ وگرنه شمارهٔ تماسِ تازه.
   *  @param email خالی ⇒ فرستاده نمی‌شود؛ وگرنه ایمیلی که افزوده می‌شود (بی کد ⇒ ۴۰۰ `email_code_required`، `EmailClaim`).
   *  @param emailCode کدِ همان ایمیل (`requestEmailCode`)؛ خالی ⇒ فرستاده نمی‌شود.
   */
  suspend fun updateProfile(name: String, phone: String? = null, email: String = "", emailCode: String = ""): ApiResult<UserWrapDto> = result {
    ApiJson.decode<UserWrapDto>(
      api.put(
        ApiEndpoints.Me.ROOT,
        buildJsonObject {
          put("name", JsonPrimitive(name.trim()))
          if (phone != null) put("phone", JsonPrimitive(phone.trim()))
          if (email.isNotBlank()) put("email", JsonPrimitive(email.trim()))
          if (email.isNotBlank() && emailCode.isNotBlank()) put("emailCode", JsonPrimitive(emailCode))
        },
      )
    )
  }

  /**
   *  AUTH-08 ⇐ «فقط ایمیل»: کدِ یک‌بارمصرف به همان ایمیلی که به حسابِ بی‌ایمیل افزوده می‌شود. «این ایمیل حسابِ دیگری دارد»
   *  این‌جا گفته نمی‌شود. خروجی: مهلتِ «ارسالِ دوباره» به ثانیه از پاسخِ خودِ سرور (`EmailClaim.resendSeconds`؛ نبود ⇒ ۰).
   */
  suspend fun requestEmailCode(email: String): ApiResult<Int> = result {
    ir.vil3ntec.tohid.core.account.EmailClaim.resendSeconds(
      api.post(ApiEndpoints.Me.EMAIL_CODE, buildJsonObject { put("email", JsonPrimitive(email.trim())) }),
    )
  }

  /* ------------------------------ حذفِ حساب ------------------------------ */

  /** پیش‌نمایشِ «حذفِ حساب»، بی نوشتن — پرچم، سدِ پمپ، نشانیِ تأیید و دکان‌هایی که می‌روند */
  suspend fun deletionInfo(): ApiResult<AccountDeletionDto> =
    result { ApiJson.decode<AccountDeletionDto>(api.get(ApiEndpoints.Me.ACCOUNT_DELETION)) }

  /**
   *  حذفِ همیشگیِ حساب روی سرور (همان `lib/user-delete.js` ِ پنلِ مدیر). ۴۰۳ `reauth_required` را خودِ
   *  `ApiClient` با پنجرهٔ رمز (`ReauthGate`) جواب می‌دهد و یک بار دوباره می‌فرستد.
   */
  suspend fun deleteAccount(confirm: String): ApiResult<Unit> = result {
    api.post(ApiEndpoints.Me.ACCOUNT_DELETION, buildJsonObject { put("confirm", JsonPrimitive(confirm.trim())) })
    Unit
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
