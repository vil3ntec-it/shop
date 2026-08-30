package ir.vil3ntec.tohid.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 *  گفت‌وگو با سرورِ خودِ کاربر.
 *
 *  نه دامنه می‌خواهد نه سرویسِ بیرونی — همان نشانیِ `http://…` که پنلِ
 *  سرور نشان می‌دهد. مسیرها همان‌هایی است که نسخهٔ وب هم صدا می‌زند، پس
 *  یک سرور به هر دو نسخه جواب می‌دهد.
 *
 *  خطاها به فارسی و با دلیل برمی‌گردند، نه «خطای ۵۰۰»: کسی که پشتِ دخل
 *  ایستاده باید بفهمد مشکل از نت است، از رمز، یا از اشتراک.
 */
class ServerClient(private val baseUrl: String) {

  class ServerError(message: String, val code: String) : Exception(message)

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  data class Session(val accessToken: String, val refreshToken: String?, val userId: String, val name: String)

  /* ------------------------------ حساب ------------------------------ */

  suspend fun register(name: String, email: String, phone: String, password: String): JsonObject =
    post("/api/v1/auth/register", buildJsonObject {
      put("name", JsonPrimitive(name)); put("email", JsonPrimitive(email))
      put("phone", JsonPrimitive(phone)); put("password", JsonPrimitive(password))
    })

  suspend fun login(identifier: String, password: String): Session {
    val body = post("/api/v1/auth/login", buildJsonObject {
      put("identifier", JsonPrimitive(identifier)); put("password", JsonPrimitive(password))
    })
    val access = body["accessToken"]?.jsonPrimitive?.content
      ?: throw ServerError("سرور توکن نداد", "bad_response")
    val user = body["user"]?.jsonObject
    return Session(
      accessToken = access,
      refreshToken = body["refreshToken"]?.jsonPrimitive?.content,
      userId = user?.get("id")?.jsonPrimitive?.content.orEmpty(),
      name = user?.get("name")?.jsonPrimitive?.content.orEmpty(),
    )
  }

  /**
   *  کدِ یک‌بارمصرف برای ورود با شماره.
   *
   *  شماره رمز ندارد و نباید داشته باشد: کسی که شمارهٔ خودش را دارد،
   *  پیامکِ کد را می‌گیرد و همان اثباتِ اوست. رمزِ اضافه فقط یک چیزِ
   *  دیگر است که فروشنده فراموشش می‌کند.
   */
  suspend fun otpRequest(destination: String): JsonObject =
    post("/api/v1/auth/otp/request", destinationBody(destination))

  /**
   *  چند ثانیه تا اجازهٔ ارسالِ دوباره، از روی پاسخِ سرور.
   *
   *  ثانیه می‌آید نه زمانِ مطلق، چون ساعتِ گوشی ممکن است با سرور جور
   *  نباشد. اگر سرورِ قدیمی این را ندهد، دو دقیقه فرض می‌شود.
   */
  fun resendSecondsOf(body: JsonObject): Int =
    body["resendSeconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 120

  /**
   *  مقصدِ کد — شماره یا ایمیل.
   *
   *  خودِ سرور تصمیم می‌گیرد کد را با پیامک بفرستد یا با ایمیل؛ برنامه
   *  فقط می‌گوید «به این نشانی». هر دو کلیدِ قدیمی هم فرستاده می‌شود تا
   *  سرورهای قدیمی‌تر هم بفهمند.
   */
  private fun destinationBody(destination: String, extra: JsonObject? = null): JsonObject =
    buildJsonObject {
      val clean = destination.trim()
      put("destination", JsonPrimitive(clean))
      if (clean.contains("@")) put("email", JsonPrimitive(clean))
      else put("phone", JsonPrimitive(clean))
      extra?.forEach { (key, value) -> put(key, value) }
    }

  /**
   *  کد را می‌سنجد و وارد می‌کند. اگر این شماره حساب نداشته باشد، سرور
   *  همان‌جا با همین نام حسابش را می‌سازد — پس «ثبت‌نام» و «ورود» با
   *  شماره یک راه‌اند، نه دو تا.
   */
  suspend fun otpVerify(destination: String, code: String, name: String): Session =
    session(
      post(
        "/api/v1/auth/otp/verify",
        destinationBody(destination, buildJsonObject {
          put("code", JsonPrimitive(code))
          put("name", JsonPrimitive(name))
        }),
      )
    )

  /**
   *  ورود با گوگل.
   *
   *  خودِ برنامه هیچ‌چیزی از گوگل را نمی‌سنجد؛ فقط توکنی را که گوشی داده
   *  دست به دست به سرورِ خودِ کاربر می‌دهد و سرور آن را با کلیدِ گوگل
   *  می‌سنجد. پس کلیدِ محرمانه‌ای داخلِ برنامه نیست که لو برود.
   */
  suspend fun googleLogin(idToken: String): Session =
    session(post("/api/v1/auth/google", buildJsonObject { put("idToken", JsonPrimitive(idToken)) }))

  /* ------------------------------ تنظیماتِ سرور ------------------------------ */

  /**
   *  سرور خودش می‌گوید کدام راهِ ورود روی آن باز است.
   *
   *  اینجوری اگر مدیرِ سرور ورود با گوگل را روشن یا خاموش کرد، لازم نیست
   *  نسخهٔ تازه‌ای از برنامه ساخته شود.
   */
  suspend fun config(): JsonObject = get("/api/v1/config")

  /** اگر خالی برگردد یعنی ورود با گوگل روی این سرور راه نیفتاده است */
  suspend fun googleClientId(): String =
    runCatching { config()["googleClientId"]?.jsonPrimitive?.content.orEmpty() }.getOrDefault("")

  suspend fun refresh(refreshToken: String): String =
    post("/api/v1/auth/refresh", buildJsonObject { put("refreshToken", JsonPrimitive(refreshToken)) })["accessToken"]
      ?.jsonPrimitive?.content ?: throw ServerError("توکن تازه نشد", "bad_response")

  /* ------------------------------ اشتراک ------------------------------ */

  suspend fun publicKey(): String =
    get("/api/v1/license/public-key")["publicKey"]?.jsonPrimitive?.content
      ?: throw ServerError("کلید عمومی سرور خوانده نشد", "bad_response")

  /** مجوز را می‌گیرد یا تازه می‌کند. `deviceUid` همان شناسهٔ همین گوشی است. */
  suspend fun license(token: String, deviceUid: String, deviceName: String): JsonObject =
    post(
      "/api/v1/license/sync",
      buildJsonObject {
        put("device", buildJsonObject {
          put("uid", JsonPrimitive(deviceUid))
          put("name", JsonPrimitive(deviceName))
        })
      },
      token,
    )

  suspend fun plans(): JsonArray =
    get("/api/v1/billing/plans")["plans"]?.jsonArray ?: JsonArray(emptyList())

  /* ------------------------------ دکان ------------------------------ */

  suspend fun shopMe(token: String): JsonObject = get("/api/v1/shop/me", token)

  suspend fun createShop(token: String, name: String): JsonObject =
    post("/api/v1/shop/create", buildJsonObject { put("name", JsonPrimitive(name)) }, token)

  suspend fun joinShop(token: String, code: String): JsonObject =
    post("/api/v1/shop/join", buildJsonObject { put("code", JsonPrimitive(code)) }, token)

  suspend fun push(token: String, deviceId: String, changes: JsonArray, settings: JsonObject): JsonObject =
    post(
      "/api/v1/shop/sync/push",
      buildJsonObject {
        put("deviceId", JsonPrimitive(deviceId))
        put("changes", changes)
        put("settings", settings)
      },
      token,
    )

  data class Pulled(val changes: JsonArray, val settings: JsonObject?, val rev: Long, val hasMore: Boolean)

  suspend fun pull(token: String, since: Long, deviceId: String): Pulled {
    val body = get("/api/v1/shop/sync/pull?since=$since&deviceId=$deviceId", token)
    return Pulled(
      changes = body["changes"]?.jsonArray ?: JsonArray(emptyList()),
      settings = body["settings"] as? JsonObject,
      rev = body["rev"]?.jsonPrimitive?.content?.toLongOrNull() ?: since,
      hasMore = body["hasMore"]?.jsonPrimitive?.content?.toBoolean() ?: false,
    )
  }

  /** سلامتِ سرور — برای دکمهٔ «آزمایش اتصال» */
  /* ---------------------- کارمندان و کدهای شاگرد ---------------------- */

  /*
   *  «چند کاربر روی یک دکان» قابلیتی است که در صفحهٔ اشتراک فروخته
   *  می‌شود. سرور از اول همه‌ی این راه‌ها را داشت؛ برنامه هیچ‌کدام را صدا
   *  نمی‌زد، پس قابلیتی که پولش گرفته می‌شد اصلاً وجود نداشت.
   */

  suspend fun members(token: String): JsonObject = get("/api/v1/shop/members", token)

  suspend fun removeMember(token: String, memberId: String): JsonObject =
    request("DELETE", "/api/v1/shop/members/$memberId", null, token)

  /** کد ثابتِ دکان — همیشه همان است تا وقتی صاحبش عوضش کند */
  suspend fun standingCode(token: String): String =
    get("/api/v1/shop/staff-code", token)["code"]?.jsonPrimitive?.content.orEmpty()

  /** عوض کردنِ کد ثابت */
  suspend fun rotateStandingCode(token: String): String =
    post("/api/v1/shop/staff-code/rotate", buildJsonObject { }, token)["code"]
      ?.jsonPrimitive?.content.orEmpty()

  suspend fun staffCodes(token: String): JsonObject = get("/api/v1/shop/staff-codes", token)

  /** کدِ تازه. متنِ کد فقط همین یک بار برمی‌گردد و بعد فقط نشانه‌اش می‌ماند. */
  suspend fun createStaffCode(
    token: String,
    role: String = "staff",
    maxUses: Int = 1,
    expiresInDays: Int = 0,
  ): JsonObject = post(
    "/api/v1/shop/staff-code",
    buildJsonObject {
      put("role", JsonPrimitive(role))
      put("maxUses", JsonPrimitive(maxUses))
      put("expiresInDays", JsonPrimitive(expiresInDays))
    },
    token,
  )

  suspend fun revokeStaffCode(token: String, codeId: String): JsonObject =
    request("DELETE", "/api/v1/shop/staff-codes/$codeId", null, token)

  /* ------------------------------ دستگاه‌ها ------------------------------ */

  suspend fun devices(token: String): JsonObject = get("/api/v1/me/devices", token)

  /** بستنِ نشستِ یک دستگاه — برای گوشیِ گم‌شده */
  suspend fun revokeDevice(token: String, deviceId: String): JsonObject =
    request("DELETE", "/api/v1/me/devices/$deviceId", null, token)

  /* ------------------------------ حساب ------------------------------ */

  suspend fun changePassword(token: String, current: String, fresh: String): JsonObject =
    post(
      "/api/v1/auth/password",
      buildJsonObject {
        put("currentPassword", JsonPrimitive(current))
        put("newPassword", JsonPrimitive(fresh))
      },
      token,
    )

  /** درخواستِ خرید — مدیر بعد از گرفتنِ پول فعالش می‌کند */
  suspend fun purchaseRequest(token: String, plan: String, note: String): JsonObject =
    post(
      "/api/v1/me/purchase-request",
      buildJsonObject {
        put("plan", JsonPrimitive(plan))
        put("note", JsonPrimitive(note))
      },
      token,
    )

  suspend fun subscription(token: String): JsonObject = get("/api/v1/me/subscription", token)

  suspend fun health(): JsonObject = get("/api/v1/health")

  /* ------------------------------ لایهٔ HTTP ------------------------------ */

  /** پاسخِ ورود همه‌جا یک شکل است — یک بار خوانده می‌شود */
  private fun session(body: JsonObject): Session {
    val access = body["accessToken"]?.jsonPrimitive?.content
      ?: throw ServerError("سرور توکن نداد", "bad_response")
    val user = body["user"]?.jsonObject
    return Session(
      accessToken = access,
      refreshToken = body["refreshToken"]?.jsonPrimitive?.content,
      userId = user?.get("id")?.jsonPrimitive?.content.orEmpty(),
      name = user?.get("name")?.jsonPrimitive?.content.orEmpty(),
    )
  }

  private suspend fun get(path: String, token: String? = null): JsonObject =
    request("GET", path, null, token)

  private suspend fun post(path: String, body: JsonObject, token: String? = null): JsonObject =
    request("POST", path, body, token)

  private suspend fun request(
    method: String,
    path: String,
    body: JsonObject?,
    token: String?,
  ): JsonObject = withContext(Dispatchers.IO) {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) throw ServerError("آدرس سرور تنظیم نشده است", "no_server")
    if (!base.startsWith("http://") && !base.startsWith("https://")) {
      throw ServerError("آدرس سرور باید با http:// یا https:// شروع شود", "bad_server")
    }

    val connection = try {
      (URL(base + path).openConnection() as HttpURLConnection)
    } catch (e: Exception) {
      throw ServerError("آدرس سرور درست نیست", "bad_server")
    }

    try {
      connection.requestMethod = method
      connection.connectTimeout = 15_000
      connection.readTimeout = 20_000
      connection.setRequestProperty("Accept", "application/json")
      if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
      if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
      }

      val status = connection.responseCode
      val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
        ?.bufferedReader()?.use { it.readText() }.orEmpty()

      val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

      if (status !in 200..299) {
        // پیامِ خودِ سرور بهتر از هر پیامِ عمومی است
        val message = parsed?.get("message")?.jsonPrimitive?.content
          ?: parsed?.get("error")?.jsonPrimitive?.content
          ?: statusMessage(status)
        throw ServerError(message, parsed?.get("code")?.jsonPrimitive?.content ?: "http_$status")
      }

      parsed ?: JsonObject(emptyMap())
    } catch (e: ServerError) {
      throw e
    } catch (e: IOException) {
      throw ServerError("اتصال به سرور برقرار نشد — نت یا آدرس را بررسی کنید", "network")
    } finally {
      connection.disconnect()
    }
  }

  private fun statusMessage(status: Int): String = when (status) {
    401 -> "نام کاربری یا رمز عبور درست نیست"
    403 -> "این حساب اجازهٔ این کار را ندارد"
    404 -> "این آدرس روی سرور پیدا نشد — مطمئن شوید نشانی درست است"
    429 -> "درخواست‌ها زیاد بود؛ کمی بعد دوباره امتحان کنید"
    in 500..599 -> "سرور خطا داد"
    else -> "پاسخ سرور درست نبود ($status)"
  }
}
