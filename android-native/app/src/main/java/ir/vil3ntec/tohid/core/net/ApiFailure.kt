package ir.vil3ntec.tohid.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  خطاهای اتصال — دسته‌بندی‌شده، نه یک «خطای سرور».
 *
 *  کسی که پشتِ دخل ایستاده باید بفهمد کارِ بعدی‌اش چیست: نت را وصل کند،
 *  چند لحظه صبر کند، دوباره وارد شود، یا به ما زنگ بزند. یک پیامِ واحد
 *  برای همهٔ حالت‌ها هیچ‌کدامِ این‌ها را نمی‌گوید.
 *
 *  ── باگی که اینجا بسته شد ─────────────────────────────────────────
 *  سرور خطا را این شکلی می‌فرستد:
 *
 *      { "error": { "code": "...", "message": "..." } }
 *
 *  لایهٔ قدیمی دنبالِ `message` و `error` در **سطحِ اول** می‌گشت و روی
 *  `error` — که خودش یک شیء است — `jsonPrimitive` صدا می‌زد. آن فراخوانی
 *  روی شیء استثنا پرتاب می‌کند. یعنی هر خطای سرور، به‌جای پیامِ فارسیِ
 *  خودش، یک استثنای انگلیسیِ داخلیِ Kotlin می‌شد و پیامِ واقعی — «رمز
 *  درست نیست»، «اشتراک تمام شده» — هیچ‌وقت به کاربر نمی‌رسید.
 *  ──────────────────────────────────────────────────────────────────
 */
sealed class ApiFailure(
  /** متنی که مستقیم به کاربر نشان داده می‌شود */
  val userMessage: String,
  /** شناسهٔ ماشین‌خوان — برای تصمیم گرفتن در کد، نه برای نمایش */
  val code: String,
  /** آیا تکرارِ همین درخواست ممکن است جواب بدهد */
  val retryable: Boolean = false,
) : Exception(userMessage) {

  /** هنوز نشانی سروری تنظیم نشده — برنامه کاملاً آفلاین کار می‌کند */
  class NotConfigured : ApiFailure("نشانی سرور تنظیم نشده است", "not_configured")

  /** نشانی هست ولی پذیرفتنی نیست */
  class BadConfiguration(message: String) : ApiFailure(message, "bad_configuration")

  /** خودِ دستگاه نت ندارد — پیش از فرستادن معلوم می‌شود */
  class Offline : ApiFailure("اینترنت وصل نیست", "offline", retryable = true)

  /** نت هست ولی سرور جواب نداد: دامنه پیدا نشد، یا در دسترس نیست */
  class Unreachable : ApiFailure("سرور در دسترس نیست", "unreachable", retryable = true)

  /** سرور هست ولی به‌موقع جواب نداد */
  class Timeout : ApiFailure("سرور به‌موقع پاسخ نداد", "timeout", retryable = true)

  /** نشستِ کاربر منقضی شده و تازه هم نشد — باید دوباره وارد شود */
  class SessionExpired : ApiFailure("نشست شما تمام شد؛ دوباره وارد شوید", "session_expired")

  /** ورود نشد — رمز یا کد درست نیست */
  class Unauthorized(message: String, code: String) : ApiFailure(message, code)

  /** وارد هست ولی اجازهٔ این کار را ندارد (نقش، یا اشتراکِ تمام‌شده) */
  class Forbidden(message: String, code: String) : ApiFailure(message, code)

  /** این مسیر یا این چیز روی سرور نیست */
  class NotFound(message: String, code: String) : ApiFailure(message, code)

  /** چیزی که می‌سازد از قبل هست */
  class Conflict(message: String, code: String) : ApiFailure(message, code)

  /** زیادی درخواست فرستاده شده */
  class RateLimited(message: String, code: String) : ApiFailure(message, code)

  /** سرور ورودی را نپذیرفت — پیامش معمولاً دقیقاً می‌گوید چرا */
  class Invalid(message: String, code: String) : ApiFailure(message, code)

  /** خطای خودِ سرور — تقصیرِ کاربر نیست و ممکن است گذرا باشد */
  class ServerFault(message: String, code: String) :
    ApiFailure(message, code, retryable = true)

  /** پاسخ رسید ولی آن چیزی نبود که قرار بود باشد */
  class InvalidResponse(detail: String = "") :
    ApiFailure(if (detail.isEmpty()) "پاسخ سرور خوانده نشد" else "پاسخ سرور خوانده نشد ($detail)", "invalid_response")

  companion object {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     *  پیام و کدِ خطا را از بدنهٔ پاسخ درمی‌آورد.
     *
     *  اول شکلِ درستِ سرور (`error.code` / `error.message`) و بعد شکلِ
     *  تختِ قدیمی. هر جا از `as?` استفاده شده نه `jsonPrimitive`، چون
     *  دومی روی شیء استثنا پرتاب می‌کند و همان باگِ بالا بود.
     */
    fun parse(body: String?): Pair<String?, String?> {
      val tree = runCatching { json.parseToJsonElement(body ?: "") as? JsonObject }.getOrNull()
        ?: return null to null

      val nested = tree["error"] as? JsonObject
      val message = str(nested?.get("message")) ?: str(tree["message"]) ?: str(tree["error"])
      val code = str(nested?.get("code")) ?: str(tree["code"])
      return message to code
    }

    private fun str(element: kotlinx.serialization.json.JsonElement?): String? =
      (element as? JsonPrimitive)?.contentOrNullIfBlank()

    private fun JsonPrimitive.contentOrNullIfBlank(): String? =
      content.takeIf { it.isNotBlank() && it != "null" }

    /**
     *  ساختنِ خطا از پاسخِ HTTP.
     *
     *  پیامِ خودِ سرور همیشه مقدم است — سرور می‌داند «کدِ این شماره امروز
     *  زیاد خواسته شده»، ما فقط می‌دانیم «۴۲۹». پیامِ عمومی وقتی به کار
     *  می‌آید که سرور چیزی نگفته باشد.
     *
     *  @param authenticated آیا این درخواست با توکن فرستاده شده بود.
     *         ۴۰۱ روی درخواستِ توکن‌دار یعنی نشست تمام شده؛ روی درخواستِ
     *         بی‌توکن یعنی رمز یا کد غلط بوده. دو چیزِ کاملاً متفاوت با
     *         دو کارِ متفاوت برای کاربر.
     */
    fun fromHttp(status: Int, body: String?, authenticated: Boolean): ApiFailure {
      val (parsedMessage, parsedCode) = parse(body)
      val code = parsedCode ?: machineCode(parsedMessage) ?: "http_$status"
      val message = readable(parsedMessage) ?: known(code) ?: generic(status)

      return when {
        status == 401 && authenticated && parsedCode != "bad_credentials" -> SessionExpired()
        status == 401 -> Unauthorized(message, code)
        status == 403 -> Forbidden(message, code)
        status == 404 -> NotFound(message, code)
        status == 409 -> Conflict(message, code)
        status == 429 -> RateLimited(message, code)
        status in 400..499 -> Invalid(message, code)
        status in 500..599 -> ServerFault(message, code)
        else -> InvalidResponse("وضعیت $status")
      }
    }

    /**
     *  آیا این رشته «پیام» است یا «کدِ ماشین».
     *
     *  ── چه چیزی را می‌بندد ──────────────────────────────────────────
     *  در دکان، روی صفحه نوشته شد: `not_found`. همین. کاربر فارسی‌زبانِ
     *  دکان‌دار باید از یک کلمهٔ انگلیسیِ زیرخط‌دار می‌فهمید چه شده.
     *
     *  علتش این بود: سرورهای قدیمی‌تر خطا را به شکلِ
     *  `{"error": "not_found"}` می‌دهند — یعنی یک **رشته**، نه شیئی با
     *  `message`. لایهٔ خواندنِ خطا همان رشته را «پیامِ سرور» می‌گرفت و
     *  چون پیامِ سرور همیشه مقدم است، همان را به کاربر نشان می‌داد.
     *
     *  حالا اول سنجیده می‌شود: رشته‌ای که فقط حروفِ کوچکِ لاتین و زیرخط
     *  دارد و فاصله ندارد، پیام نیست — کد است. کد می‌رود سرِ جای خودش
     *  (`code`) و برای کاربر یک جملهٔ فارسی نوشته می‌شود.
     *  ──────────────────────────────────────────────────────────────
     */
    private fun machineCode(text: String?): String? =
      text?.takeIf { it.length <= 40 && it.matches(Regex("^[a-z][a-z0-9_]*$")) }

    /** فقط چیزی که واقعاً پیام است به کاربر می‌رسد */
    private fun readable(text: String?): String? =
      text?.takeIf { machineCode(it) == null }

    /**
     *  کدهایی که پیامِ فارسیِ خودشان را دارند.
     *
     *  `not_found` روی مسیرِ API معنایش برای دکان‌دار این است که سرورش
     *  آن قابلیت را ندارد — و تقریباً همیشه یعنی ظرفِ سرور با کدِ کهنه
     *  بالا آمده. `docker compose up -d` بدونِ `--build` ایمیج را از نو
     *  نمی‌سازد و همین یک نکته، ساعت‌ها گشتن می‌سازد.
     */
    private fun known(code: String): String? = when (code) {
      "not_found" -> "این قابلیت روی سرورِ شما نیست — سرور را با کدِ تازه بالا بیاورید"
      "no_shop" -> "برای این حساب دکانی ثبت نشده است"
      "shop_not_found" -> "دکان پیدا نشد"
      "code_not_found" -> "این کد پیدا نشد یا از قبل باطل شده است"
      "registration_closed" -> "ثبت‌نام روی این سرور بسته است"
      "already_registered" -> "این ایمیل یا شماره از قبل ثبت شده است"
      "weak_password" -> "رمز ضعیف است"
      "identifier_required" -> "ایمیل یا شماره موبایل لازم است"
      "otp_not_found" -> "کدی برای این شماره صادر نشده است"
      "google_no_email" -> "حساب گوگل ایمیل ندارد"
      "account_disabled" -> "این حساب غیرفعال است"
      "feature_locked" -> "این قابلیت در اشتراکِ فعلی نیست"
      else -> null
    }

    /** پیامِ جایگزین، فقط وقتی سرور خودش چیزی نگفته باشد */
    private fun generic(status: Int): String = when (status) {
      400 -> "درخواست درست نبود"
      401 -> "ایمیل/شماره یا رمز درست نیست"
      403 -> "این حساب اجازهٔ این کار را ندارد"
      404 -> "این مسیر روی سرور پیدا نشد"
      409 -> "این مورد از قبل ثبت شده است"
      429 -> "درخواست‌ها زیاد بود؛ کمی بعد دوباره امتحان کنید"
      in 500..599 -> "سرور خطا داد؛ کمی بعد دوباره امتحان کنید"
      else -> "پاسخ سرور درست نبود ($status)"
    }

    /**
     *  استثناهای شبکه را به خطای معنادار تبدیل می‌کند.
     *
     *  همه‌شان `IOException` بودند و همه یک پیام می‌گرفتند. حالا «نت
     *  نیست»، «دامنه پیدا نشد» و «وقت تمام شد» از هم جدا می‌شوند، چون
     *  کارِ بعدیِ کاربر در هر سه فرق دارد.
     */
    fun fromException(error: Throwable): ApiFailure = when (error) {
      is ApiFailure -> error
      is java.net.SocketTimeoutException -> Timeout()
      is java.net.UnknownHostException -> Unreachable()
      is java.net.ConnectException -> Unreachable()
      is java.net.NoRouteToHostException -> Unreachable()
      is javax.net.ssl.SSLException -> BadConfiguration("اتصال امن با سرور برقرار نشد")
      is java.io.IOException -> Unreachable()
      else -> InvalidResponse()
    }
  }
}

/**
 *  متنِ قابل نمایشِ هر خطا.
 *
 *  خطاهای این لایه پیامِ فارسیِ خودشان را دارند؛ برای هر چیزِ دیگری
 *  (خطای محلی، خواندنِ پرونده) متنِ جایگزین به کار می‌آید. هیچ‌وقت
 *  پیامِ خامِ انگلیسیِ یک کتابخانه به کاربر نشان داده نمی‌شود.
 */
fun Throwable.userText(fallback: String): String =
  (this as? ApiFailure)?.userMessage ?: fallback
