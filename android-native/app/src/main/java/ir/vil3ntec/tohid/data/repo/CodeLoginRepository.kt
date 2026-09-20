package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.TokenStorage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  ورود با کدِ شش‌رقمیِ ایمیلی — قراردادِ `docs/LOGIN-fa.md`.
 *
 *      POST /auth/{app}/request-code    ⇒ {request_id, resend_after, …}
 *      GET  /auth/{app}/request-status  ⇒ {state, reason, can_resend_in}
 *      POST /auth/{app}/verify          ⇒ توکن‌ها + user + subscription
 *
 *  ⛔ **این جای `AuthRepository` را نمی‌گیرد.** آن یکی ورودِ امروزِ
 *  مشتری‌هاست (رمز، گوگل، ثبت‌نامِ سه‌پله‌ای) و نسخه‌های نصب‌شده روی
 *  گوشیِ مردم با همان کار می‌کنند. این یکی قراردادِ ثابتِ بندِ ۲۰.۷
 *  است. هر دو یک دفترِ حساب دارند و کنارِ هم می‌مانند.
 *
 *  ⚠️ کد هیچ‌وقت روی گوشی ذخیره نمی‌شود — نه خام، نه هش‌شده. فقط یک
 *  بار فرستاده می‌شود و جوابش توکن است.
 */
class CodeLoginRepository(
  private val api: ApiClient,
  private val tokens: TokenStorage,
) {

  data class CodeRequest(
    val requestId: String,
    val expiresIn: Int,
    val resendAfter: Int,
    val maskedEmail: String,
  )

  data class DeliveryStatus(
    val state: String,
    val reason: String,
    val attempts: Int,
    val canResendIn: Int,
  )

  data class Session(
    val userId: String,
    val email: String,
    val name: String,
    val created: Boolean,
  )

  private val app: String get() = AppConfig.appId

  suspend fun requestCode(email: String, deviceName: String): ApiResult<CodeRequest> =
    ApiResult.of {
      val body = buildJsonObject {
        put("email", JsonPrimitive(email.trim()))
        put("device_name", JsonPrimitive(deviceName))
      }
      val r = api.postPublic(ApiEndpoints.Auth.requestCode(app), body)
      CodeRequest(
        requestId = ApiJson.text(r, "request_id"),
        expiresIn = ApiJson.int(r, "expires_in", 300),
        resendAfter = ApiJson.int(r, "resend_after", RESEND_SECONDS),
        maskedEmail = ApiJson.text(r, "masked_email"),
      )
    }

  /** «ایمیل رفت یا نرفت، و چرا» — بندِ ۲ قرارداد. */
  suspend fun deliveryStatus(requestId: String): ApiResult<DeliveryStatus> =
    ApiResult.of {
      val r = api.getPublic(ApiEndpoints.Auth.requestStatus(app, requestId))
      DeliveryStatus(
        state = ApiJson.text(r, "state").ifBlank { "queued" },
        reason = ApiJson.text(r, "reason"),
        attempts = ApiJson.int(r, "attempts"),
        canResendIn = ApiJson.int(r, "can_resend_in"),
      )
    }

  /**
   *  سنجشِ کد و نشاندنِ نشست.
   *
   *  ⚠️ نامِ فیلدِ توکن `access_token` است (قراردادِ پرامپت) و
   *  `accessToken` هم کنارش می‌آید (نامِ امروزِ برنامه‌ها). هر دو خوانده
   *  می‌شوند تا سرورِ قدیمی و تازه هر دو کار کنند.
   */
  suspend fun verify(requestId: String, code: String, deviceId: String, deviceName: String): ApiResult<Session> =
    ApiResult.of {
      val body = buildJsonObject {
        put("request_id", JsonPrimitive(requestId))
        put("code", JsonPrimitive(englishDigits(code)))
        put("device_id", JsonPrimitive(deviceId))
        put("device_name", JsonPrimitive(deviceName))
      }
      val r = api.postPublic(ApiEndpoints.Auth.verifyCode(app), body)
      val access = ApiJson.text(r, "access_token").ifBlank { ApiJson.text(r, "accessToken") }
      val refresh = ApiJson.text(r, "refresh_token").ifBlank { ApiJson.text(r, "refreshToken") }
      if (access.isBlank()) throw ir.vil3ntec.tohid.core.net.ApiFailure.InvalidResponse("توکن نیامد")
      val expiresAt = ApiJson.long(r, "accessExpiresAt").takeIf { it > 0 }
        ?: (System.currentTimeMillis() + ApiJson.long(r, "access_expires_in", 3600) * 1000)
      tokens.save(access = access, refresh = refresh.ifBlank { null }, expiresAt = expiresAt)

      val user = r["user"] as? JsonObject
      Session(
        userId = user?.let { ApiJson.text(it, "id") }.orEmpty(),
        email = user?.let { ApiJson.text(it, "email") }.orEmpty(),
        name = user?.let { ApiJson.text(it, "name") }.orEmpty(),
        created = user?.let { ApiJson.bool(it, "created") } ?: false,
      )
    }

  /**
   *  تپش — همهٔ آن‌چه «اشتراکِ من» لازم دارد، در یک درخواست (بندِ ۲۰.۷).
   *
   *  ⛔ هیچ عددِ قیمتی از این‌جا ساخته نمی‌شود؛ هرچه هست از سرور آمده.
   */
  suspend fun heartbeat(): ApiResult<JsonObject> = ApiResult.of {
    api.get(ApiEndpoints.Me.HEARTBEAT)
  }

  companion object {
    const val RESEND_SECONDS = 60

    /**
     *  ارقامِ فارسی و عربی ⇒ انگلیسی.
     *
     *  صفحه‌کلیدِ گوشیِ کاربر فارسی است و کدِ ایمیل انگلیسی. بی این، کدِ
     *  **درست** هم «کد اشتباه است» می‌گرفت — و کاربر هیچ راهی برای
     *  فهمیدنش نداشت.
     */
    fun englishDigits(raw: String): String {
      val fa = "۰۱۲۳۴۵۶۷۸۹"
      val ar = "٠١٢٣٤٥٦٧٨٩"
      val out = StringBuilder(raw.length)
      for (ch in raw) {
        val f = fa.indexOf(ch)
        val a = ar.indexOf(ch)
        out.append(
          when {
            f >= 0 -> '0' + f
            a >= 0 -> '0' + a
            else -> ch
          },
        )
      }
      return out.toString()
    }

    /** فقط رقم — فاصله و هر چیزِ دیگر بیرون می‌ماند. */
    fun digitsOnly(raw: String): String =
      englishDigits(raw).filter { it in '0'..'9' }
  }
}
