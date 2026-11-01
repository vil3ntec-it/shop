package ir.vil3ntec.tohid.core.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  کارگزارِ API — تنها راهِ حرف زدنِ برنامه با سرور.
 *
 *  کارِ این لایه چیزی است که تا دیروز اصلاً انجام نمی‌شد:
 *
 *  ── نشستی که خودش تازه می‌شود ─────────────────────────────────────
 *  توکنِ دسترسی روی سرور **یک ساعت** عمر دارد و توکنِ تازه‌سازی نود روز.
 *  برنامه توکنِ تازه‌سازی را ذخیره می‌کرد ولی هیچ‌وقت از آن استفاده
 *  نمی‌کرد و مسیرِ `/auth/refresh` هرگز صدا زده نمی‌شد. یعنی یک ساعت
 *  بعد از ورود، همگام‌سازی بی‌صدا می‌مرد و اگر کاربر سراغش می‌رفت
 *  می‌خواند: «نام کاربری یا رمز عبور درست نیست» — که نه درست بود و نه
 *  کاری از دستش برمی‌آمد.
 *
 *  حالا هر ۴۰۱ روی یک درخواستِ توکن‌دار یعنی «توکن پیر شده»: یک بار
 *  تازه‌اش می‌کنیم و همان درخواست را دوباره می‌فرستیم. کاربر هیچ‌کدام
 *  را نمی‌بیند. فقط اگر تازه‌سازی هم نگرفت — توکنِ نود‌روزه هم مرده، یا
 *  مدیر حساب را بسته — تازه آن‌وقت «دوباره وارد شوید».
 *
 *  تازه‌سازی پشتِ یک قفل است: اگر همگام‌سازی و صفحهٔ اشتراک با هم به
 *  ۴۰۱ بخورند، یکی تازه می‌کند و آن یکی منتظرِ همان می‌ماند — نه دو
 *  درخواستِ موازی که هرکدام دیگری را باطل کند.
 *  ──────────────────────────────────────────────────────────────────
 */
class ApiClient(
  private val engine: HttpEngine,
  private val tokens: TokenStorage,
  /** وقتی نشست واقعاً از دست رفت — تا برنامه کاربر را به صفحهٔ ورود ببرد */
  private val onSessionLost: () -> Unit = {},
  /**
   *  «رمز را دوباره بزنید» برای کارِ حساس (`reauth_required`) — یک بار
   *  برای همهٔ مسیرها؛ میزبانش `ui/ReauthHost` است. `ReauthGate` را ببینید.
   */
  val reauth: ReauthGate = ReauthGate(),
) {

  private val refreshLock = Mutex()

  /* ------------------------ درخواست‌های بی‌نیاز به ورود ------------------------ */

  suspend fun getPublic(path: String): JsonObject =
    engine.send("GET", path, token = null)

  suspend fun postPublic(path: String, body: JsonObject): JsonObject =
    engine.send("POST", path, body = body, token = null)

  /* ------------------------ درخواست‌های حساب‌دار ------------------------ */

  suspend fun get(path: String): JsonObject = authorized("GET", path, null, idempotent = true)

  suspend fun post(path: String, body: JsonObject = EMPTY): JsonObject =
    authorized("POST", path, body, idempotent = false)

  /**
   *  نوشتنِ JSON با `Idempotency-Key` — سرور همان کلید را «همان کار» می‌داند
   *  و پاسخِ قبلی را برمی‌گرداند. پس اگر پاسخ پس از ثبت گم شد (قطعِ نت،
   *  مهلت)، تلاشِ دوبارهٔ خودکار بی‌خطر است و کار «ناموفق» دیده نمی‌شود.
   *  فقط برای درهایی که سرورشان کلید را می‌خواند.
   */
  suspend fun postKeyed(path: String, body: JsonObject, idempotencyKey: String): JsonObject =
    authorized(
      "POST", path, null, idempotent = true,
      raw = RawBody(
        body.toString().toByteArray(Charsets.UTF_8),
        "application/json; charset=utf-8",
        mapOf("Idempotency-Key" to idempotencyKey),
      ),
    )

  suspend fun put(path: String, body: JsonObject = EMPTY): JsonObject =
    authorized("PUT", path, body, idempotent = false)

  /** همان `postKeyed` برای `PUT` (انتشارِ گروهیِ ویترین) */
  suspend fun putKeyed(path: String, body: JsonObject, idempotencyKey: String): JsonObject =
    authorized(
      "PUT", path, null, idempotent = true,
      raw = RawBody(
        body.toString().toByteArray(Charsets.UTF_8),
        "application/json; charset=utf-8",
        mapOf("Idempotency-Key" to idempotencyKey),
      ),
    )

  suspend fun delete(path: String): JsonObject = authorized("DELETE", path, null, idempotent = false)

  /**
   *  فرستادنِ یک **فایل** — نه یک شیء.
   *
   *  ⚠️ چرا جدا: پشتیبانِ یک دکانِ چندساله چند مگابایت است. داخلِ JSON
   *  باید base64 می‌شد (یک‌سومِ بزرگ‌تر) و کلِ فایل دو بار در حافظهٔ
   *  گوشی می‌نشست — همان‌جا `OutOfMemory`.
   *
   *  ⚠️ `idempotent = false`: فرستادنِ دوباره یک نسخهٔ **دوم** می‌سازد،
   *  پس تلاشِ خودکار نباید بزند. اگر نرفت، دورِ بعدِ خودِ `AutoBackup`
   *  می‌فرستدش.
   */
  suspend fun postBytes(
    path: String,
    bytes: ByteArray,
    contentType: String = "application/octet-stream",
    headers: Map<String, String> = emptyMap(),
    onProgress: ((Long) -> Unit)? = null,
  ): JsonObject = authorized(
    "POST", path, null, idempotent = false,
    raw = RawBody(bytes, contentType, headers, onProgress),
  )

  /**
   *  فرستادنِ فایل با PUT — نسخهٔ کوچکِ عکس در بانکِ تصاویر. سرور برای همان
   *  بایت‌ها همان پاسخ را می‌دهد (یک‌بارنویس)، پس تکرار بی‌خطر است.
   */
  suspend fun putBytes(
    path: String,
    bytes: ByteArray,
    contentType: String = "application/octet-stream",
  ): JsonObject = authorized("PUT", path, null, idempotent = true, raw = RawBody(bytes, contentType))

  /**
   *  گرفتنِ **بایت‌های خام** با توکن (رسانهٔ چتِ دکان) — همان تازه‌سازیِ
   *  یک‌باره‌ی نشستِ پیر که `authorized` دارد.
   */
  suspend fun getBytes(path: String): ByteArray {
    val token = tokens.accessToken ?: throw ApiFailure.SessionExpired()
    return try {
      engine.fetchBytes(path, token)
    } catch (failure: ApiFailure) {
      if (failure !is ApiFailure.SessionExpired) throw failure
      engine.fetchBytes(path, renew(usedToken = token))
    }
  }

  /**
   *  درخواست با توکن — و اگر توکن پیر بود، یک بار تازه‌سازی و تکرار.
   *
   *  «یک بار» عمدی است: اگر بعد از توکنِ تازه هم ۴۰۱ آمد، مشکل از پیریِ
   *  توکن نیست و تکرارِ دوباره فقط یک حلقه می‌سازد.
   */
  private suspend fun authorized(
    method: String,
    path: String,
    body: JsonObject?,
    idempotent: Boolean,
    raw: RawBody? = null,
  ): JsonObject =
    try {
      renewing(method, path, body, idempotent, raw)
    } catch (failure: ApiFailure) {
      /*
       *  کارِ حساس با نشستِ کهنه (۴۰۳ `reauth_required`): سرور پیش از هر
       *  اثری رد کرده، پس پس از تأییدِ دوبارهٔ کاربر همان درخواست **یک بار**
       *  دوباره می‌رود — حتی `POST`. بارِ دوم هم رد شد ⇒ همان خطا، بی حلقه.
       */
      if (!ReauthGate.applies(failure, path) || !reauth.confirm()) throw failure
      renewing(method, path, body, idempotent, raw)
    }

  /** یک درخواستِ توکن‌دار — و اگر توکن پیر بود، یک بار تازه‌سازی و تکرار */
  private suspend fun renewing(
    method: String,
    path: String,
    body: JsonObject?,
    idempotent: Boolean,
    raw: RawBody?,
  ): JsonObject {
    val token = tokens.accessToken ?: throw ApiFailure.SessionExpired()

    try {
      return engine.send(method, path, body, token, idempotent, raw)
    } catch (failure: ApiFailure) {
      if (failure !is ApiFailure.SessionExpired) throw failure

      val fresh = renew(usedToken = token)
      return engine.send(method, path, body, fresh, idempotent, raw)
    }
  }

  /**
   *  گرفتنِ توکنِ دسترسیِ تازه.
   *
   *  @param usedToken توکنی که همین حالا ۴۰۱ گرفت. اگر توکنِ ذخیره‌شده
   *         با آن فرق دارد یعنی درخواستِ دیگری زودتر تازه‌اش کرده — پس
   *         بی‌خود دوباره تازه نمی‌کنیم و همان تازه را برمی‌داریم.
   */
  private suspend fun renew(usedToken: String): String = refreshLock.withLock {
    tokens.accessToken?.let { current -> if (current != usedToken) return@withLock current }

    val refresh = tokens.refreshToken ?: run {
      forgetSession()
      throw ApiFailure.SessionExpired()
    }

    val body = try {
      engine.send(
        "POST",
        ApiEndpoints.Auth.REFRESH,
        buildJsonObject { put("refreshToken", JsonPrimitive(refresh)) },
        token = null,
      )
    } catch (failure: ApiFailure) {
      /*
       *  فقط «سرور خودش گفت این توکن مرده» (۴۰۱) نشست را می‌بندد.
       *
       *  تا امروز هر خطای غیرِ گذرا نشست را پاک می‌کرد: ۴۲۹ِ سقفِ نرخ،
       *  صفحهٔ HTMLِ وای‌فایِ هتل یا اپراتور (۲۰۰ ولی نه JSON)، ۴۰۳ِ یک
       *  پراکسی… هیچ‌کدام دربارهٔ خودِ توکن چیزی نمی‌گویند و کاربر بی‌دلیل
       *  بیرون می‌افتاد (بندِ ۲۳.۴ و ۲۱.۱ سندِ v3). نشست می‌ماند و خطا بالا
       *  می‌رود؛ درخواستِ بعدی دوباره امتحان می‌کند.
       */
      if (failure.status != 401) throw failure
      forgetSession()
      throw ApiFailure.SessionExpired()
    }

    val access = ApiJson.text(body, "accessToken").ifBlank { ApiJson.text(body, "access_token") }
      .takeIf { it.isNotBlank() }
      ?: throw ApiFailure.InvalidResponse("توکنِ تازه نیامد")

    /*
     *  ⛔ توکنِ تازه‌سازیِ **چرخیده** ذخیره می‌شود.
     *
     *  سرور در هر تازه‌سازی توکنِ تازه‌سازیِ تازه می‌دهد و قبلی را می‌بندد
     *  (`lib/tokens.rotateRefresh`)؛ اگر قبلی بیرون از پنجرهٔ ارفاقِ سی‌ثانیه‌ای
     *  برگردد، «دزدی» حساب می‌شود و کلِ نشستِ این دستگاه بسته می‌شود. این‌جا
     *  تا امروز `refresh = null` بود — یعنی همیشه همان توکنِ اولِ ورود
     *  می‌رفت و دومین تازه‌سازی (حدودِ دو ساعت کار، یا فردای همان روز)
     *  کاربر را بیرون می‌انداخت. نیامدنش هنوز یعنی «عوض نشده».
     */
    val rotated = ApiJson.text(body, "refreshToken").ifBlank { ApiJson.text(body, "refresh_token") }
      .takeIf { it.isNotBlank() }

    val expiresAt = (body["accessExpiresAt"] as? JsonPrimitive)?.content?.toLongOrNull()
      ?: (body["access_expires_in"] as? JsonPrimitive)?.content?.toLongOrNull()
        ?.let { System.currentTimeMillis() + it * 1000 }
      ?: 0
    tokens.save(access = access, refresh = rotated, expiresAt = expiresAt)
    access
  }

  private fun forgetSession() {
    tokens.clear()
    runCatching { onSessionLost() }
  }

  private companion object {
    val EMPTY = JsonObject(emptyMap())
  }
}
