package ir.vil3ntec.tohid.admin.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 *  گفت‌وگو با بخشِ مدیریتِ سرور.
 *
 *  همهٔ مسیرها زیرِ `/api/v1/admin` هستند و توکنِ مدیر نوعِ جداگانه دارد
 *  (`kind = 'admin'`). یعنی حتی توکنِ معتبرِ یک کاربرِ عادی هم اینجا کار
 *  نمی‌کند — تصمیمش سمتِ سرور گرفته می‌شود، نه با پنهان کردنِ دکمه.
 *
 *  عمداً کتابخانهٔ شبکه‌ای اضافه نشده: `HttpURLConnection` و `org.json`
 *  هر دو داخلِ خودِ اندروید هستند و این برنامه کارِ سنگینی نمی‌کند.
 */
class AdminApi(private val baseUrl: String) {

  class ApiError(message: String, val code: String, val status: Int) : Exception(message)

  data class Login(val token: String, val name: String, val role: String, val expiresAt: Long)

  /* ------------------------------ ورود ------------------------------ */

  suspend fun login(username: String, password: String): Login {
    val body = post("/api/v1/admin/login", JSONObject().put("username", username).put("password", password), null)
    val admin = body.optJSONObject("admin")
    return Login(
      token = body.optString("token").ifBlank { throw ApiError("سرور توکن نداد", "bad_response", 0) },
      name = admin?.optString("name").orEmpty(),
      role = admin?.optString("role").orEmpty(),
      expiresAt = body.optLong("expiresAt"),
    )
  }

  suspend fun logout(token: String) { runCatching { post("/api/v1/admin/logout", JSONObject(), token) } }

  /* ------------------------------ داشبورد ------------------------------ */

  suspend fun stats(token: String): JSONObject = get("/api/v1/admin/stats", token)

  /* ------------------------------ کاربران ------------------------------ */

  suspend fun users(token: String, query: String = "", limit: Int = 50): JSONArray =
    get("/api/v1/admin/users?limit=$limit&q=${enc(query)}", token).optJSONArray("users") ?: JSONArray()

  suspend fun user(token: String, id: String): JSONObject = get("/api/v1/admin/users/$id", token)

  /** فعال یا بسته کردنِ حساب. بستن، همهٔ نشست‌هایش را هم می‌بندد. */
  suspend fun setUserStatus(token: String, id: String, status: String): JSONObject =
    post("/api/v1/admin/users/$id/status", JSONObject().put("status", status), token)

  /* ------------------------------ دکان‌ها ------------------------------ */

  suspend fun shops(token: String, query: String = "", limit: Int = 50): JSONArray =
    get("/api/v1/admin/shops?limit=$limit&q=${enc(query)}", token).optJSONArray("shops") ?: JSONArray()

  suspend fun shop(token: String, id: String): JSONObject = get("/api/v1/admin/shops/$id", token)

  /** دفترِ تغییرهای اشتراکِ یک دکان — چه کسی کِی چه تمدیدی داد */
  suspend fun shopHistory(token: String, id: String): JSONArray =
    get("/api/v1/admin/shops/$id/history", token).optJSONArray("history") ?: JSONArray()

  /* ------------------------------ اشتراک ------------------------------ */

  suspend fun plans(token: String): JSONArray =
    get("/api/v1/admin/plans", token).optJSONArray("plans") ?: JSONArray()

  /**
   *  صدور یا تمدیدِ اشتراک.
   *
   *  اگر دکان اشتراکِ زنده داشته باشد، سرور از **تاریخِ پایانِ همان**
   *  ادامه می‌دهد نه از امروز — پس روزهای باقی‌مانده از بین نمی‌رود.
   *  این تصمیم سمتِ سرور است و اینجا فقط خواسته می‌شود.
   */
  suspend fun grant(
    token: String,
    shopId: String,
    plan: String,
    days: Int?,
    note: String = "",
  ): JSONObject = post(
    "/api/v1/admin/subscriptions",
    JSONObject().apply {
      put("shopId", shopId)
      put("plan", plan)
      if (days != null) put("days", days)
      if (note.isNotBlank()) put("note", note)
    },
    token,
  )

  /**
   *  تغییرِ تاریخِ پایان (و پلن و یادداشت) یک اشتراکِ موجود.
   *
   *  با `grant` فرق دارد: آن یکی تمدید می‌کند و به تاریخِ پایان اضافه
   *  می‌کند؛ این یکی تاریخ را **می‌نشاند** — برای وقتی که اشتباهی شده و
   *  باید درست شود.
   */
  suspend fun setEndsAt(token: String, subscriptionId: String, endsAt: Long): JSONObject =
    put("/api/v1/admin/subscriptions/$subscriptionId", JSONObject().put("endsAt", endsAt), token)

  suspend fun setSubscriptionStatus(token: String, id: String, status: String): JSONObject =
    post("/api/v1/admin/subscriptions/$id/status", JSONObject().put("status", status), token)

  suspend fun subscriptions(token: String, status: String = "", limit: Int = 50): JSONArray =
    get("/api/v1/admin/subscriptions?limit=$limit&status=${enc(status)}", token)
      .optJSONArray("subscriptions") ?: JSONArray()

  /* ------------------------------ پیامک ------------------------------ */

  /**
   *  تنظیماتِ سرویس پیامک.
   *
   *  کلیدِ سرویس هرگز کامل نمی‌آید — سرور فقط چهار رقمِ آخرش را می‌دهد.
   *  پس این برنامه هم کلید را ندارد و اگر گوشی دستِ کسی بیفتد، چیزی
   *  گیرش نمی‌آید.
   */
  suspend fun smsSettings(token: String): JSONObject =
    get("/api/v1/admin/sms", token).optJSONObject("sms") ?: JSONObject()

  /**
   *  ذخیره. کلید فقط وقتی فرستاده می‌شود که مدیر چیزی نوشته باشد؛
   *  خالی گذاشتنش یعنی «همان قبلی بماند».
   */
  suspend fun saveSmsSettings(
    token: String,
    provider: String,
    url: String,
    method: String,
    sender: String,
    headers: String,
    body: String,
    template: String,
    key: String?,
  ): JSONObject = put(
    "/api/v1/admin/sms",
    JSONObject().apply {
      put("provider", provider)
      put("url", url)
      put("method", method)
      put("sender", sender)
      put("headers", headers)
      put("body", body)
      put("template", template)
      if (!key.isNullOrBlank()) put("key", key)
    },
    token,
  )

  /** یک پیامکِ آزمایشی به شمارهٔ خودتان. جایی ثبت نمی‌شود و کدش کار نمی‌کند. */
  suspend fun testSms(token: String, to: String): JSONObject =
    post("/api/v1/admin/sms/test", JSONObject().put("to", to), token)

  /* ------------------------- درخواست‌های خرید ------------------------- */

  suspend fun purchaseRequests(token: String): JSONArray =
    get("/api/v1/admin/purchase-requests", token).optJSONArray("requests") ?: JSONArray()

  suspend fun approveRequest(token: String, id: String, days: Int?): JSONObject =
    post(
      "/api/v1/admin/purchase-requests/$id/approve",
      JSONObject().apply { if (days != null) put("days", days) },
      token,
    )

  suspend fun rejectRequest(token: String, id: String, reason: String): JSONObject =
    post("/api/v1/admin/purchase-requests/$id/reject", JSONObject().put("reason", reason), token)

  /* ------------------------------ سابقه ------------------------------ */

  suspend fun audit(token: String, limit: Int = 100): JSONArray =
    get("/api/v1/admin/audit?limit=$limit", token).optJSONArray("entries")
      ?: get("/api/v1/admin/audit?limit=$limit", token).optJSONArray("logs")
      ?: JSONArray()

  suspend fun health(): JSONObject = get("/api/v1/health", null)

  /* ==========================================================
     بخش‌های تازه
     ========================================================== */

  /**
   *  خلاصهٔ خانه — یک درخواست به‌جای هفت‌تا.
   *
   *  روی نتِ ضعیف، صفحهٔ خانه هفت بار منتظر می‌ماند؛ این‌طور یک بار.
   */
  suspend fun overview(token: String): JSONObject = get("/api/v1/admin/overview", token)

  /* ------------------------------ تخفیف ------------------------------ */

  /**
   *  گذاشتنِ تخفیف روی یک پلن.
   *
   *  دو راه: درصد، یا قیمتِ ثابتِ تخفیفی. قیمتِ اصلی دست نمی‌خورد — پس
   *  وقتی مهلت تمام شد، خودش برمی‌گردد و لازم نیست عددِ قبلی را جایی
   *  یادداشت کنید.
   */
  suspend fun setDiscount(
    token: String,
    code: String,
    percent: Int?,
    price: Int?,
    label: String,
    until: Long?,
  ): JSONObject = put(
    "/api/v1/admin/plans/$code/discount",
    JSONObject().apply {
      if (percent != null) put("percent", percent)
      if (price != null) put("price", price)
      if (label.isNotBlank()) put("label", label)
      if (until != null) put("until", until)
    },
    token,
  )

  suspend fun clearDiscount(token: String, code: String): JSONObject =
    request("DELETE", "/api/v1/admin/plans/$code/discount", null, token)

  /** عوض کردنِ خودِ نرخ (نه تخفیف) — قیمت، عنوان، مدت. */
  suspend fun savePlan(
    token: String,
    code: String,
    title: String,
    price: Int,
    amount: Int,
    unit: String,
    badge: String,
    active: Boolean,
  ): JSONObject = request(
    "PATCH",
    "/api/v1/admin/plans/$code",
    JSONObject().apply {
      put("title", title)
      put("price", price)
      put("amount", amount)
      put("unit", unit)
      put("badge", badge)
      put("active", active)
    },
    token,
  )

  /* --------------------------- کد وی‌آی‌پی --------------------------- */

  suspend fun vipCodes(token: String): JSONArray =
    get("/api/v1/admin/vip-codes", token).optJSONArray("codes") ?: JSONArray()

  /**
   *  ساختِ کد و — اگر ایمیل بدهید — فرستادنش.
   *
   *  کدِ خام فقط در همین یک پاسخ می‌آید؛ بعد از آن حتی سرور هم نمی‌تواند
   *  نشانش بدهد. اگر ایمیل داده باشید، لازم نیست خودتان کد را برسانید.
   */
  suspend fun createVipCode(
    token: String,
    plan: String,
    days: Int?,
    email: String,
    note: String,
    shopId: String?,
    expiresInDays: Int,
  ): JSONObject = post(
    "/api/v1/admin/vip-codes",
    JSONObject().apply {
      put("plan", plan)
      if (days != null) put("days", days)
      if (email.isNotBlank()) put("email", email)
      if (note.isNotBlank()) put("note", note)
      if (!shopId.isNullOrBlank()) put("shopId", shopId)
      put("expiresInDays", expiresInDays)
    },
    token,
  )

  suspend fun revokeVipCode(token: String, id: String): JSONObject =
    post("/api/v1/admin/vip-codes/$id/revoke", JSONObject(), token)

  /* ------------------------- بازدیدکننده‌ها ------------------------- */

  /**
   *  کسانی که آمده‌اند — چه حساب ساخته باشند چه نه.
   *
   *  `onlyGuests` همان چیزی است که تا امروز اصلاً دیده نمی‌شد: کسی که
   *  برنامه را باز کرده ولی هنوز ثبت‌نام نکرده.
   */
  suspend fun visitors(token: String, app: String = "", onlyGuests: Boolean = false, query: String = ""): JSONObject =
    get(
      "/api/v1/admin/visitors?limit=200&app=${enc(app)}&guests=${if (onlyGuests) 1 else 0}&q=${enc(query)}",
      token,
    )

  /* --------------------------- پشتیبانی --------------------------- */

  suspend fun supportThreads(token: String, status: String = "", query: String = ""): JSONObject =
    get("/api/v1/admin/support/threads?status=${enc(status)}&q=${enc(query)}", token)

  suspend fun supportThread(token: String, id: String, after: Long = 0): JSONObject =
    get("/api/v1/admin/support/threads/$id?after=$after", token)

  suspend fun supportReply(token: String, id: String, body: String): JSONObject =
    post("/api/v1/admin/support/threads/$id/messages", JSONObject().put("body", body), token)

  suspend fun supportStatus(token: String, id: String, status: String): JSONObject =
    post("/api/v1/admin/support/threads/$id/status", JSONObject().put("status", status), token)

  /** پیام همگانی — به کسانی که اشتراکشان رو به پایان است، یا به همه. */
  suspend fun broadcast(token: String, body: String, target: String): JSONObject =
    post(
      "/api/v1/admin/support/broadcast",
      JSONObject().put("body", body).put("target", target),
      token,
    )

  /* --------------------- اشتراک‌های رو به پایان --------------------- */

  suspend fun expiring(token: String, days: Int = 7): JSONArray =
    get("/api/v1/admin/subscriptions/expiring?days=$days", token)
      .optJSONArray("expiring") ?: JSONArray()

  suspend fun notifyExpiring(token: String): JSONObject =
    post("/api/v1/admin/subscriptions/notify-expiring", JSONObject(), token)

  /* ------------------------ برنامه‌های دیگر ------------------------ */

  suspend fun apps(token: String): JSONArray =
    get("/api/v1/admin/apps", token).optJSONArray("apps") ?: JSONArray()

  suspend fun createApp(
    token: String,
    slug: String,
    title: String,
    kind: String,
    url: String,
    healthUrl: String,
  ): JSONObject = post(
    "/api/v1/admin/apps",
    JSONObject().put("slug", slug).put("title", title).put("kind", kind)
      .put("url", url).put("healthUrl", healthUrl),
    token,
  )

  suspend fun updateApp(token: String, id: String, patch: JSONObject): JSONObject =
    put("/api/v1/admin/apps/$id", patch, token)

  suspend fun archiveApp(token: String, id: String): JSONObject =
    request("DELETE", "/api/v1/admin/apps/$id", null, token)

  /**
   *  کلیدِ تازه برای یک برنامه. خام فقط همین یک بار برمی‌گردد؛ بعد از آن
   *  حتی سرور هم نمی‌تواند نشانش بدهد.
   */
  suspend fun rotateAppKey(token: String, id: String): String =
    post("/api/v1/admin/apps/$id/key", JSONObject(), token).optString("key")

  /** سنجیدنِ سلامتِ همه — از سرور، نه از این گوشی که ممکن است پشت فیلتر باشد. */
  suspend fun checkApps(token: String): JSONArray =
    post("/api/v1/admin/apps/health", JSONObject(), token).optJSONArray("apps") ?: JSONArray()

  /* --------------------------- ایمیل --------------------------- */

  /** تنظیماتِ ایمیل. رمز و کلید هرگز کامل برنمی‌گردند. */
  suspend fun emailSettings(token: String): JSONObject =
    get("/api/v1/admin/email", token).optJSONObject("email") ?: JSONObject()

  suspend fun saveEmailSettings(token: String, patch: JSONObject): JSONObject =
    put("/api/v1/admin/email", patch, token).optJSONObject("email") ?: JSONObject()

  /** یک ایمیل واقعی به نشانیِ خودتان — تا معلوم شود راه افتاده یا نه. */
  suspend fun testEmail(token: String, to: String): JSONObject =
    post("/api/v1/admin/email/test", JSONObject().put("to", to), token)

  /* ---------------------------- پوش ---------------------------- */

  suspend fun pushSettings(token: String): JSONObject =
    get("/api/v1/admin/push", token).optJSONObject("push") ?: JSONObject()

  suspend fun savePushSettings(token: String, enabled: Boolean, serviceAccount: String?): JSONObject =
    put(
      "/api/v1/admin/push",
      JSONObject().apply {
        put("enabled", enabled)
        if (!serviceAccount.isNullOrBlank()) put("serviceAccount", serviceAccount)
      },
      token,
    ).optJSONObject("push") ?: JSONObject()

  /* ------------------------------ لایهٔ HTTP ------------------------------ */

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

  private suspend fun get(path: String, token: String?): JSONObject =
    request("GET", path, null, token)

  private suspend fun post(path: String, body: JSONObject, token: String?): JSONObject =
    request("POST", path, body, token)

  private suspend fun put(path: String, body: JSONObject, token: String?): JSONObject =
    request("PUT", path, body, token)

  private suspend fun request(
    method: String,
    path: String,
    body: JSONObject?,
    token: String?,
  ): JSONObject = withContext(Dispatchers.IO) {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) throw ApiError("آدرس سرور تنظیم نشده است", "no_server", 0)
    if (!base.startsWith("http://") && !base.startsWith("https://")) {
      throw ApiError("آدرس سرور باید با https:// شروع شود", "bad_server", 0)
    }

    val connection = try {
      URL(base + path).openConnection() as HttpURLConnection
    } catch (e: Exception) {
      throw ApiError("آدرس سرور درست نیست", "bad_server", 0)
    }

    try {
      connection.requestMethod = method
      connection.connectTimeout = 15_000
      connection.readTimeout = 25_000
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

      val parsed = runCatching { JSONObject(text) }.getOrNull()

      if (status !in 200..299) {
        //  پیام خودِ سرور بهتر از هر پیامِ عمومی است
        val err = parsed?.optJSONObject("error")
        val message = err?.optString("message")?.ifBlank { null }
          ?: parsed?.optString("message")?.ifBlank { null }
          ?: reason(status)
        throw ApiError(message, err?.optString("code").orEmpty().ifBlank { "http_$status" }, status)
      }

      parsed ?: JSONObject()
    } catch (e: ApiError) {
      throw e
    } catch (e: IOException) {
      throw ApiError("به سرور نرسیدیم — نت یا آدرس را بررسی کنید", "network", 0)
    } finally {
      connection.disconnect()
    }
  }

  private fun reason(status: Int): String = when (status) {
    401 -> "نام کاربری یا رمز درست نیست"
    403 -> "این حساب اجازهٔ این کار را ندارد"
    404 -> "این مسیر روی سرور نیست — نشانی یا نسخهٔ سرور را بررسی کنید"
    429 -> "درخواست زیاد بود؛ کمی بعد دوباره"
    in 500..599 -> "سرور خطا داد ($status)"
    else -> "پاسخ سرور درست نبود ($status)"
  }
}
