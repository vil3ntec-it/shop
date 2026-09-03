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

  /** همان سنجش، ولی با نسخهٔ سرور — برای نشان دادن در برگهٔ وضعیت */
  suspend fun healthDetail(): ApiResult<ir.vil3ntec.tohid.core.model.ServerHealthDto> =
    result { ApiJson.decode<ir.vil3ntec.tohid.core.model.ServerHealthDto>(api.getPublic(ApiEndpoints.HEALTH)) }

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

  /**
   *  ساختِ حساب.
   *
   *  ── چرا این یکی به توکن بند نیست ──────────────────────────────────
   *  ثبت‌نام تا دیروز مثل ورود رفتار می‌کرد: اگر پاسخِ سرور توکن نداشت،
   *  کلِ کار «ناموفق» اعلام می‌شد — با پیامِ «توکن نیامد» — در حالی که
   *  حساب **ساخته شده بود**. کاربر پیامِ خطا می‌دید، دوباره می‌زد، و این
   *  بار سرور می‌گفت «این ایمیل از قبل ثبت شده است». یعنی یک حسابِ سالم،
   *  با یک پیامِ غلط، دست‌نیافتنی می‌شد.
   *
   *  سرورها هم یکسان جواب نمی‌دهند: نسخهٔ امروزِ سرور با ثبت‌نام نشست هم
   *  می‌دهد، ولی نسخهٔ قدیمی‌تر (یا نمونه‌ای که با ایمیجِ کهنه بالا آمده)
   *  فقط حساب را می‌سازد. هیچ‌کدام دلیلِ شکست نیست.
   *
   *  پس: توکن آمد، همان‌جا وارد می‌شویم — نیامد، حساب ساخته شده و
   *  صفحه می‌گوید «حالا وارد شوید». کدام‌یک بود را `isValid` می‌گوید.
   *  ──────────────────────────────────────────────────────────────────
   *
   *  فقط همان شناسه‌ای فرستاده می‌شود که مقدار دارد: سرور می‌گوید «یکی از
   *  ایمیل یا شماره کافی است» و فرستادنِ رشتهٔ خالی، شناسه به حساب نمی‌آید.
   */
  /**
   *  برگرداندنِ نشست با توکنی که از قبل داریم — «ورودِ سریع».
   *
   *  `/auth/refresh` فقط توکنِ دسترسیِ تازه می‌دهد، نه یک نشستِ کامل؛ پس
   *  `keep` به کار نمی‌آید و همان توکنِ تازه‌سازیِ قبلی سرِ جایش می‌ماند.
   *
   *  اگر سرور ردش کرد — باطل شده یا مهلتش تمام شده — خطا برمی‌گردد و
   *  صفحه به راهِ همیشگی (رمز یا کد) برمی‌گردد.
   */
  suspend fun resume(refreshToken: String): ApiResult<SessionDto> = result {
    val body = api.postPublic(
      ApiEndpoints.Auth.REFRESH,
      buildJsonObject { put("refreshToken", JsonPrimitive(refreshToken.trim())) },
    )
    val access = (body["accessToken"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
      ?: throw ApiFailure.SessionExpired()
    val expires = (body["accessExpiresAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    tokens.save(access, refreshToken, expires)

    /*
     *  حساب را از سرور می‌گیریم، نه از حافظه.
     *
     *  چرا لازم است: `/auth/refresh` فقط توکن می‌دهد و نام و دکان
     *  همراهش نیست. اگر نشستِ نصفه — با نام و دکانِ خالی — به صفحه
     *  برگردد، صفحه همان خالی را «حسابِ تازه» می‌فهمد و نامِ حساب و
     *  دفترِ دکان را روی گوشی پاک می‌کند. یعنی ورودِ سریع، داده می‌بُرد.
     */
    val me = ApiJson.decode<ir.vil3ntec.tohid.core.model.MeDto>(api.get(ApiEndpoints.Me.ROOT))
    val session = SessionDto(
      accessToken = access,
      accessExpiresAt = expires,
      refreshToken = refreshToken,
      user = me.user,
      shop = me.shop,
    )
    runCatching { onSignedIn(session) }
    session
  }

  suspend fun register(
    name: String,
    email: String,
    phone: String,
    password: String,
  ): ApiResult<SessionDto> = result {
    keepIfAny(
      api.postPublic(
        ApiEndpoints.Auth.REGISTER,
        buildJsonObject {
          put("name", JsonPrimitive(name.trim()))
          if (email.isNotBlank()) put("email", JsonPrimitive(email.trim()))
          if (phone.isNotBlank()) put("phone", JsonPrimitive(phone.trim()))
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

  /**
   *  نشست را می‌خواند، ذخیره می‌کند و خبر می‌دهد.
   *
   *  اگر توکن نبود، پیامِ خطا **می‌گوید سرور چه فرستاده**. پیامِ قبلی
   *  فقط «توکن نیامد» بود و آدم را سرِ حدس می‌گذاشت: پاسخ خالی بود؟
   *  سرورِ قدیمی؟ چیزی وسط راه جوابِ دیگری داد؟ حالا کلیدهای پاسخ در
   *  همان پیام هست و یک نگاه، تکلیف را روشن می‌کند.
   */
  private fun keep(body: JsonObject): SessionDto {
    val session = ApiJson.decode<SessionDto>(body)
    if (!session.isValid) throw ApiFailure.InvalidResponse(
      if (body.isEmpty()) "پاسخِ سرور خالی بود"
      else "توکن در پاسخ نبود — سرور فرستاد: ${body.keys.joinToString(", ").take(90)}"
    )
    tokens.save(session.accessToken, session.refreshToken, session.accessExpiresAt)
    runCatching { onSignedIn(session) }
    return session
  }

  /**
   *  مثل `keep`، ولی نبودنِ توکن خطا نیست.
   *
   *  برای ثبت‌نام: کارِ اصلی «ساختنِ حساب» است و آن انجام شده. توکن اگر
   *  آمد، هدیه است — کاربر یک مرحله کمتر دارد.
   */
  private fun keepIfAny(body: JsonObject): SessionDto {
    val session = ApiJson.decode<SessionDto>(body)
    if (!session.isValid) return session
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
