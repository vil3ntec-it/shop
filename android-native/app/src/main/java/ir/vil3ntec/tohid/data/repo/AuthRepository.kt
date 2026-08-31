package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.OtpChallengeDto
import ir.vil3ntec.tohid.core.model.ServerConfigDto
import ir.vil3ntec.tohid.core.model.SessionDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.TokenStorage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  ورود و نشست — همهٔ راه‌ها از اینجا.
 *
 *  صفحه‌ها هیچ‌وقت توکن را نمی‌بینند و ذخیره نمی‌کنند. تا دیروز هر صفحه‌ای
 *  که وارد می‌کرد، خودش `state.accessToken = …` می‌نوشت؛ یعنی اگر جایی
 *  فراموش می‌شد، ورود «انجام می‌شد» ولی چیزی نمی‌ماند. حالا نگهداریِ
 *  نشست کارِ همین لایه است و بس.
 */
class AuthRepository(
  private val api: ApiClient,
  private val tokens: TokenStorage,
  /** برای خبر دادن به بقیهٔ برنامه که حساب عوض شد */
  private val onSignedIn: (SessionDto) -> Unit = {},
  private val onSignedOut: () -> Unit = {},
) {

  /* ------------------------------ شناختِ سرور ------------------------------ */

  /** آیا سرور بالاست — برای دکمهٔ «آزمایش اتصال» */
  suspend fun health(): ApiResult<Boolean> = result { api.getPublic(ApiEndpoints.HEALTH); true }

  suspend fun serverConfig(): ApiResult<ServerConfigDto> =
    result { ApiJson.decode<ServerConfigDto>(api.getPublic(ApiEndpoints.CONFIG)) }

  /** خالی یعنی ورود با گوگل روی این سرور راه نیفتاده */
  suspend fun googleClientId(): String =
    serverConfig().valueOrNull()?.googleClientId.orEmpty()

  /* ------------------------------ ورود ------------------------------ */

  suspend fun login(identifier: String, password: String): ApiResult<SessionDto> = result {
    keep(
      api.postPublic(
        ApiEndpoints.Auth.LOGIN,
        buildJsonObject {
          put("identifier", JsonPrimitive(identifier.trim()))
          put("password", JsonPrimitive(password))
        },
      )
    )
  }

  suspend fun register(
    name: String,
    email: String,
    phone: String,
    password: String,
  ): ApiResult<SessionDto> = result {
    keep(
      api.postPublic(
        ApiEndpoints.Auth.REGISTER,
        buildJsonObject {
          put("name", JsonPrimitive(name.trim()))
          put("email", JsonPrimitive(email.trim()))
          put("phone", JsonPrimitive(phone.trim()))
          put("password", JsonPrimitive(password))
        },
      )
    )
  }

  /**
   *  ورود با گوگل.
   *
   *  برنامه هیچ‌چیزی از گوگل را خودش نمی‌سنجد؛ توکنی را که گوشی داده به
   *  سرور می‌دهد و سرور با کلیدِ گوگل می‌سنجدش. پس رازی داخلِ برنامه
   *  نیست که لو برود.
   */
  /**
   *  ورود شاگرد با کدی که صاحب دکان به او داده.
   *
   *  هیچ حسابی از قبل لازم نیست: سرور خودش برای همین دستگاه یک حساب
   *  شاگرد می‌سازد و روی همان دکان می‌نشاندش. دفعه‌ی بعد که همین
   *  دستگاه همان کد را بزند، همان حساب برمی‌گردد — نه یک عضو تازه.
   *
   *  @param deviceUid همان شناسه‌ای که همگام‌سازی با آن کار می‌کند؛
   *    همین است که «همان گوشی، همان حساب» را ممکن می‌کند.
   */
  suspend fun loginWithStaffCode(
    code: String,
    name: String,
    deviceUid: String,
    deviceName: String,
  ): ApiResult<SessionDto> = result {
    keep(
      api.postPublic(
        ApiEndpoints.Auth.STAFF,
        buildJsonObject {
          put("code", JsonPrimitive(code.trim().uppercase()))
          put("name", JsonPrimitive(name.trim()))
          put("device", buildJsonObject {
            put("uid", JsonPrimitive(deviceUid))
            put("name", JsonPrimitive(deviceName))
            put("platform", JsonPrimitive("android"))
          })
        },
      )
    )
  }

  suspend fun loginWithGoogle(idToken: String): ApiResult<SessionDto> = result {
    keep(
      api.postPublic(
        ApiEndpoints.Auth.GOOGLE,
        buildJsonObject { put("idToken", JsonPrimitive(idToken)) },
      )
    )
  }

  /* ------------------------------ کد یک‌بارمصرف ------------------------------ */

  /**
   *  خواستنِ کد برای شماره یا ایمیل.
   *
   *  کدام‌یک را خودِ سرور از روی مقصد تشخیص می‌دهد. هر دو کلیدِ قدیمی
   *  (`phone` / `email`) هم فرستاده می‌شود تا سرورِ به‌روزنشده هم بفهمد.
   */
  suspend fun requestOtp(destination: String): ApiResult<OtpChallengeDto> = result {
    ApiJson.decode<OtpChallengeDto>(
      api.postPublic(ApiEndpoints.Auth.OTP_REQUEST, destinationBody(destination))
    )
  }

  /**
   *  سنجشِ کد و ورود.
   *
   *  اگر این شماره حساب نداشته باشد، سرور همان‌جا می‌سازدش — «ثبت‌نام» و
   *  «ورود» با شماره یک راه‌اند، نه دو تا.
   */
  suspend fun verifyOtp(destination: String, code: String, name: String = ""): ApiResult<SessionDto> =
    result {
      keep(
        api.postPublic(
          ApiEndpoints.Auth.OTP_VERIFY,
          destinationBody(destination) {
            put("code", JsonPrimitive(code.trim()))
            put("name", JsonPrimitive(name.trim()))
          },
        )
      )
    }

  /* ------------------------------ رمز ------------------------------ */

  suspend fun forgotPassword(email: String): ApiResult<OtpChallengeDto> = result {
    ApiJson.decode<OtpChallengeDto>(
      api.postPublic(
        ApiEndpoints.Auth.PASSWORD_FORGOT,
        buildJsonObject { put("email", JsonPrimitive(email.trim())) },
      )
    )
  }

  /** رمزِ تازه با کدی که به ایمیل رفته — و همان‌جا ورود */
  suspend fun resetPassword(email: String, code: String, password: String): ApiResult<SessionDto> =
    result {
      keep(
        api.postPublic(
          ApiEndpoints.Auth.PASSWORD_RESET,
          buildJsonObject {
            put("email", JsonPrimitive(email.trim()))
            put("code", JsonPrimitive(code.trim()))
            put("password", JsonPrimitive(password))
          },
        )
      )
    }

  suspend fun changePassword(current: String, fresh: String): ApiResult<Unit> = result {
    api.post(
      ApiEndpoints.Auth.PASSWORD,
      buildJsonObject {
        put("currentPassword", JsonPrimitive(current))
        put("newPassword", JsonPrimitive(fresh))
      },
    )
    Unit
  }

  /* ------------------------------ خروج ------------------------------ */

  /**
   *  خروج.
   *
   *  اول به سرور خبر می‌دهیم تا توکن همان‌جا باطل شود، ولی نتیجه‌اش
   *  اهمیتی ندارد: کاربری که «خروج» زده باید خارج شود، حتی اگر نت نباشد.
   *  توکنِ محلی در هر حال پاک می‌شود.
   */
  suspend fun signOut(): ApiResult<Unit> {
    val refresh = tokens.refreshToken
    val attempt = result {
      api.post(
        ApiEndpoints.Auth.LOGOUT,
        buildJsonObject { refresh?.let { put("refreshToken", JsonPrimitive(it)) } },
      )
      Unit
    }
    tokens.clear()
    runCatching { onSignedOut() }
    return if (attempt is ApiResult.Failure && attempt.error is ApiFailure.SessionExpired)
      ApiResult.Success(Unit) else attempt
  }

  /* ------------------------------ درونی ------------------------------ */

  /** نشست را می‌خواند، ذخیره می‌کند و خبر می‌دهد */
  private fun keep(body: JsonObject): SessionDto {
    val session = ApiJson.decode<SessionDto>(body)
    if (!session.isValid) throw ApiFailure.InvalidResponse("توکن نیامد")
    tokens.save(session.accessToken, session.refreshToken, session.accessExpiresAt)
    runCatching { onSignedIn(session) }
    return session
  }

  private inline fun destinationBody(
    destination: String,
    extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
  ): JsonObject = buildJsonObject {
    val clean = destination.trim()
    put("destination", JsonPrimitive(clean))
    if (clean.contains("@")) put("email", JsonPrimitive(clean))
    else put("phone", JsonPrimitive(clean))
    extra()
  }
}
