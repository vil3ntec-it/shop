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
class AdminApi private constructor(
  private val session: Session?,
  private val fixedUrl: String?,
) {

  /** موقعِ ورود، هنوز نشستی نیست — فقط همان نشانی که کاربر زد */
  constructor(baseUrl: String) : this(null, baseUrl)

  /**
   *  بعد از ورود: برنامه چند نشانی می‌شناسد و خودش امتحان می‌کند.
   *
   *  در خانه آی‌پیِ داخلی جواب می‌دهد، بیرون از خانه نشانیِ تونل. کاربر
   *  نباید هر بار که از در بیرون می‌رود نشانی عوض کند — این کارِ برنامه است.
   */
  constructor(session: Session) : this(session, null)


  class ApiError(message: String, val code: String, val status: Int) : Exception(message)

  data class Login(
    val token: String,
    val name: String,
    val role: String,
    val expiresAt: Long,
    /** نشانیِ همین سرور از بیرونِ خانه — خالی اگر تونل روشن نباشد */
    val remoteUrl: String,
  )

  /* ------------------------------ ورود ------------------------------ */

  suspend fun login(username: String, password: String): Login {
    val body = post("/api/v1/admin/login", JSONObject().put("username", username).put("password", password), null)
    val admin = body.optJSONObject("admin")
    return Login(
      token = body.optString("token").ifBlank { throw ApiError("سرور توکن نداد", "bad_response", 0) },
      name = admin?.optString("name").orEmpty(),
      role = admin?.optString("role").orEmpty(),
      expiresAt = body.optLong("expiresAt"),
      remoteUrl = body.optString("remoteUrl").takeIf { it.startsWith("http") }.orEmpty(),
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

  /* ------------------------------ لایهٔ HTTP ------------------------------ */

  companion object {
    /**
     *  نشانیِ خانگی: آی‌پیِ شبکهٔ داخلی یا خودِ همین دستگاه.
     *
     *  محدوده‌ها همان‌هایی است که هیچ‌وقت روی اینترنت مسیر ندارند:
     *  ۱۰.x، ۱۷۲.۱۶ تا ۱۷۲.۳۱، ۱۹۲.۱۶۸.x، ۱۶۹.۲۵۴.x و لوکال‌هاست.
     *  نامِ بدونِ نقطه (مثل «server») هم فقط داخلِ همان شبکه معنی دارد.
     */
    fun isHomeAddress(host: String): Boolean {
      val h = host.lowercase().substringBefore(':').trim('[', ']')
      if (h == "localhost" || h == "::1" || h.startsWith("127.")) return true
      if (h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home") || h.endsWith(".internal")) return true
      if (!h.contains('.')) return h.isNotEmpty() && !h.contains(':')

      val parts = h.split('.')
      if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
      val (a, b) = parts[0].toInt() to parts[1].toInt()
      return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        a == 169 && b == 254 -> true
        else -> false
      }
    }

    /**
     *  نشانیِ سرور را تمیز می‌کند و اگر به درد نمی‌خورد، **همان‌جا** و با
     *  دلیل رد می‌کند.
     *
     *  دربارهٔ `http://`: این برنامه توکنِ مدیر را می‌برد و می‌آورد؛ همان
     *  توکنی که با آن می‌شود اشتراکِ هر کسی را عوض کرد. روی اینترنت این را
     *  روی خطِ باز فرستادن یعنی هر کسی سرِ راه می‌تواند برش دارد — پس آنجا
     *  رد می‌شود.
     *
     *  ولی سرورِ خانگی روی ۱۹۲.۱۶۸.x است و https ندارد و قرار هم نیست
     *  داشته باشد: گواهیِ معتبر برای یک آی‌پیِ داخلی صادر نمی‌شود. سخت‌گیری
     *  آنجا از کسی محافظت نمی‌کرد، فقط برنامه را در خانهٔ صاحبش بی‌مصرف
     *  می‌کرد. پس http برای نشانیِ خانگی باز است و برای بقیه بسته.
     */
    fun normalizeBase(raw: String): String {
      val base = raw.trim().trimEnd('/')
      if (base.isEmpty()) throw ApiError("آدرس سرور تنظیم نشده است", "no_server", 0)

      if (base.startsWith("http://")) {
        val host = base.removePrefix("http://").substringBefore('/')
        if (!isHomeAddress(host)) {
          throw ApiError(
            "با http فقط به سرورِ داخلِ خانه می‌شود وصل شد. برای بیرون از خانه، " +
              "در پنل بخشِ توحید نشانیِ https را بردارید و همان را بزنید.",
            "cleartext",
            0,
          )
        }
        return base
      }

      if (!base.startsWith("https://")) {
        throw ApiError("آدرس سرور باید با http:// یا https:// شروع شود", "bad_server", 0)
      }
      return base
    }
  }

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

  private suspend fun get(path: String, token: String?): JSONObject =
    request("GET", path, null, token)

  private suspend fun post(path: String, body: JSONObject, token: String?): JSONObject =
    request("POST", path, body, token)

  private suspend fun put(path: String, body: JSONObject, token: String?): JSONObject =
    request("PUT", path, body, token)

  /** آنچه هست، به ترتیبی که امتحان می‌شود: آخرین نشانیِ موفق، اول */
  private fun candidates(): List<String> {
    if (fixedUrl != null) return listOf(fixedUrl)
    val s = session ?: return emptyList()
    return listOf(s.lastGoodUrl, s.serverUrl, s.remoteUrl)
      .map { it.trim().trimEnd('/') }
      .filter { it.isNotEmpty() }
      .distinct()
  }

  /**
   *  یک درخواست، روی هر نشانی‌ای که در دسترس باشد.
   *
   *  فقط وقتی سراغِ نشانیِ بعدی می‌رود که **نرسیده باشیم** — نه وقتی سرور
   *  جواب داده و جوابش خطا بوده. رمزِ غلط روی نشانیِ اول، رمزِ غلط است؛
   *  امتحانش روی نشانیِ دوم فقط یک تلاشِ ناموفقِ دیگر روی همان حساب است.
   */
  private suspend fun request(
    method: String,
    path: String,
    body: JSONObject?,
    token: String?,
  ): JSONObject {
    val bases = candidates()
    if (bases.isEmpty()) throw ApiError("آدرس سرور تنظیم نشده است", "no_server", 0)

    var last: ApiError? = null
    for (raw in bases) {
      val base = try {
        normalizeBase(raw)
      } catch (e: ApiError) {
        last = e
        continue
      }
      try {
        val answer = send(method, base, path, body, token)
        session?.lastGoodUrl = base
        return answer
      } catch (e: ApiError) {
        // سرور جواب داده — همین جواب است، جای دیگری را نگرد
        if (e.status != 0) throw e
        last = e
      }
    }
    throw last ?: ApiError("به سرور نرسیدیم — نت یا آدرس را بررسی کنید", "network", 0)
  }

  private suspend fun send(
    method: String,
    base: String,
    path: String,
    body: JSONObject?,
    token: String?,
  ): JSONObject = withContext(Dispatchers.IO) {
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
