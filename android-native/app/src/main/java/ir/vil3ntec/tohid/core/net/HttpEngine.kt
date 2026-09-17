package ir.vil3ntec.tohid.core.net

import ir.vil3ntec.tohid.core.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 *  لایهٔ حملِ HTTP — پایین‌ترین لایه، و تنها جایی که برنامه به شبکه دست
 *  می‌زند.
 *
 *  اینجا چیزی از «دکان» و «فروش» و «اشتراک» نمی‌داند. فقط: بگیر، بفرست،
 *  و اگر نشد بگو **چرا** نشد. تصمیم‌های کاری یک لایه بالاتر گرفته می‌شود.
 *
 *  چه چیزی اینجا تضمین می‌شود:
 *    • نشانی همیشه از پیکربندی می‌آید، نه از صدازننده
 *    • مهلت همیشه گذاشته می‌شود — درخواستِ بی‌مهلت یعنی برنامهٔ قفل‌شده
 *    • خطا همیشه از نوعِ `ApiFailure` است، نه استثنایی ناشناخته
 *    • تلاشِ دوباره فقط جایی که تکرارش بی‌خطر است
 */
class HttpEngine(
  private val baseUrl: () -> String,
  private val allowInsecure: Boolean,
  /** آیا دستگاه اصلاً نت دارد — اگر ندارد، بی‌خود به شبکه نمی‌زنیم */
  private val online: () -> Boolean = { true },
  /** پیشوندی که دفعهٔ پیش روی این سرور جواب داده بود */
  rememberedPrefix: String? = null,
  /** تا دفعهٔ بعد لازم نباشد دوباره کشفش کنیم */
  private val onPrefixFound: (String) -> Unit = {},
) {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /*
   *  ── پیشوندی که روی **این** سرور کار می‌کند ─────────────────────────
   *  گزارشِ صاحب مخزن: نه حساب ساخته می‌شد، نه کدِ پیوستن، و صفحهٔ
   *  «کارمندان» می‌گفت این بخش روی سرور نیست — در حالی که سرور همان‌ها
   *  را داشت.
   *
   *  ریشه‌اش در خودِ سرور بود و درست شد (ترتیبِ سوار شدنِ `/api` و
   *  `/api/v1`)، ولی سرورِ هر دکان‌دار همان روز به‌روز نمی‌شود و تا آن
   *  روز، برنامهٔ تازه روی سرورِ قدیمی هیچ کاری نمی‌توانست بکند.
   *
   *  پس برنامه دیگر فرض نمی‌کند: اگر مسیرِ نسخه‌دار ۴۰۴ یا ۴۰۱ داد،
   *  **یک بار** همان درخواست را بی‌نسخه می‌فرستد. هر کدام جواب داد،
   *  همان می‌ماند و روی گوشی نوشته می‌شود تا دفعهٔ بعد این آزمون هم
   *  لازم نباشد.
   *
   *  چرا فقط ۴۰۴ و ۴۰۱: این دو تنها چیزی‌اند که آن اشکالِ سرور تولید
   *  می‌کرد. بقیهٔ خطاها پاسخِ واقعیِ خودِ مسیرند و دوباره فرستادنشان
   *  فقط یک درخواستِ اضافه است.
   */
  @Volatile private var prefix: String =
    rememberedPrefix?.takeIf { it == ApiConfig.API_PREFIX || it == ApiConfig.API_PREFIX_PLAIN }
      ?: ApiConfig.API_PREFIX

  /** پیشوندی که همین حالا با آن کار می‌کنیم — برای صفحهٔ وضعیتِ سرور */
  val activePrefix: String get() = prefix

  /**
   *  یک درخواست.
   *
   *  @param idempotent آیا تکرارِ این درخواست بی‌خطر است. خواندن بله؛
   *         «ثبتِ فروش» نه — دو بار ثبت شدنِ یک فاکتور از یک بار نرسیدنش
   *         بدتر است، پس تلاشِ دوباره برای نوشتن، خودخواسته خاموش است.
   */
  suspend fun send(
    method: String,
    path: String,
    body: JsonObject? = null,
    token: String? = null,
    idempotent: Boolean = method == "GET",
    raw: RawBody? = null,
  ): JsonObject = withContext(Dispatchers.IO) {
    var attempt = 0
    var wait = ApiConfig.RETRY_BACKOFF_MS

    while (true) {
      try {
        return@withContext once(method, path, body, token, raw)
      } catch (failure: ApiFailure) {
        val canRetry = idempotent && failure.retryable && attempt < ApiConfig.MAX_RETRIES
        if (!canRetry) throw failure
        attempt++
        delay(wait)
        wait *= 2
      }
    }
    @Suppress("UNREACHABLE_CODE")
    throw ApiFailure.InvalidResponse()
  }

  /**
   *  یک تلاش — و اگر لازم شد، یک بار هم با پیشوندِ دیگر.
   *
   *  ترتیبش مهم است: اول همان پیشوندی که می‌دانیم کار می‌کند. تنها وقتی
   *  ۴۰۴/۴۰۱ گرفتیم و پیشوندِ دیگری امتحان‌نشده مانده، دومی می‌رود.
   */
  private fun once(
    method: String,
    path: String,
    body: JsonObject?,
    token: String?,
    raw: RawBody? = null,
  ): JsonObject {
    val first = prefix
    try {
      return attempt(method, path, body, token, first, raw)
    } catch (failure: ApiFailure) {
      /*
       *  ── چرا رمزِ غلط از این آزمون بیرون است ────────────────────────
       *  «رمز اشتباه است» هم یک ۴۰۱ بی‌توکن است، پس تا دیروز درست
       *  می‌افتاد داخلِ همین شرط و هر ورودِ ناموفق **دو بار** فرستاده
       *  می‌شد. سه چیز از آن درمی‌آمد:
       *
       *    ۱) سقفِ نرخِ سرور نصف می‌شد — ده تلاش در ربع ساعت، عملاً پنج
       *    ۲) شمارندهٔ قفلِ حساب دو برابر می‌شمرد: کاربر بعد از چهار
       *       اشتباه قفل می‌شد، نه هشت تا
       *    ۳) هر اشتباه دو ردیف در `login_attempts` می‌نوشت
       *
       *  یعنی وصله‌ای که برای یک اشکالِ سرورِ قدیمی گذاشته شده بود، به
       *  محدودیت‌های ضدِ حدسِ رمز شلیک می‌کرد.
       *
       *  تفاوتشان روشن است: آن اشکالِ سرور ۴۰۱هایی می‌ساخت که از لایهٔ
       *  «توکن لازم است» می‌آمدند (`unauthorized` و `invalid_token`) —
       *  یعنی مسیر اصلاً پیدا نشده بود. `bad_credentials` پاسخِ **خودِ
       *  مسیرِ ورود** است: مسیر هست، پیدا شده، و جوابش را داده. دوباره
       *  فرستادنش فقط خرج است.
       *  ──────────────────────────────────────────────────────────────
       */
      val routeMaybeMissing = failure is ApiFailure.Unauthorized &&
        (failure.code == "unauthorized" || failure.code == "invalid_token" || failure.code == "http_401")

      val worthRetry = failure is ApiFailure.NotFound || failure is ApiFailure.SessionExpired ||
        (routeMaybeMissing && token == null)
      val other =
        if (first == ApiConfig.API_PREFIX) ApiConfig.API_PREFIX_PLAIN else ApiConfig.API_PREFIX
      if (!worthRetry) throw failure

      /*
       *  دوباره فرستادنِ همین درخواست بی‌خطر است — حتی اگر POST باشد.
       *  ۴۰۴ یعنی مسیری نبود و ۴۰۱ یعنی رد شد؛ در هر دو حالت سرور کاری
       *  **انجام نداده**. چیزی دو بار ثبت نمی‌شود.
       */
      val value = try {
        attempt(method, path, body, token, other)
      } catch (_: ApiFailure) {
        //  دومی هم نشد: خطای **اولی** را می‌گوییم، چون پاسخِ مسیرِ اصلی
        //  است و پیامش به کار می‌آید
        throw failure
      }
      //  دومی جواب داد؛ از این پس همین است
      prefix = other
      runCatching { onPrefixFound(other) }
      return value
    }
  }

  /** یک تلاش با یک پیشوندِ مشخص */
  private fun attempt(
    method: String,
    path: String,
    body: JsonObject?,
    token: String?,
    prefix: String,
    raw: RawBody? = null,
  ): JsonObject {
    val base = baseUrl()
    ApiConfig.reject(base, allowInsecure)?.let { reason ->
      throw if (reason == ApiConfig.Rejection.MISSING) ApiFailure.NotConfigured()
      else ApiFailure.BadConfiguration(reason.message)
    }
    //  نت که نیست، رفتن سراغِ شبکه فقط چند ثانیه انتظارِ بی‌فایده است
    if (!online()) throw ApiFailure.Offline()

    val url = runCatching { URL(ApiConfig.urlOf(base, path, prefix)) }.getOrNull()
      ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")

    val connection = runCatching { url.openConnection() as HttpURLConnection }.getOrNull()
      ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")

    try {
      connection.requestMethod = method
      connection.connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
      connection.readTimeout = ApiConfig.READ_TIMEOUT_MS
      connection.setRequestProperty("Accept", "application/json")
      if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")

      /*
       *  ⚠️ **بدنهٔ خام، نه JSON.**
       *
       *  پشتیبان یک فایل است، نه یک شیء. اگر داخلِ JSON می‌رفت باید
       *  base64 می‌شد: یک‌سوم بزرگ‌تر، و کلِ فایل دو بار در حافظهٔ
       *  گوشی — برای دفترِ چندمگابایتیِ یک دکانِ چندساله، همان‌جا
       *  `OutOfMemory`.
       *
       *  `Content-Type` هم عمداً JSON نیست: `express.json`ِ سرور فقط
       *  `application/json` را می‌خواند، پس فایل دست‌نخورده رد می‌شود و
       *  `express.raw`ِ خودِ مسیر برش می‌دارد.
       */
      if (raw != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", raw.contentType)
        raw.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.setFixedLengthStreamingMode(raw.bytes.size)
        connection.outputStream.use { it.write(raw.bytes) }
      } else if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
      }

      val status = connection.responseCode
      val ok = status in 200..299
      val text = (if (ok) connection.inputStream else connection.errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

      if (!ok) throw ApiFailure.fromHttp(status, text, authenticated = token != null)

      //  ۲۰۴ و پاسخِ خالی خطا نیست: «انجام شد» هم یک پاسخ است
      if (text.isBlank()) return JsonObject(emptyMap())

      return runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        ?: throw ApiFailure.InvalidResponse()
    } catch (failure: ApiFailure) {
      throw failure
    } catch (error: Throwable) {
      //  هیچ استثنای خامی از این لایه بیرون نمی‌رود
      throw ApiFailure.fromException(error)
    } finally {
      runCatching { connection.disconnect() }
    }
  }
}

/**
 *  بدنه‌ای که JSON نیست — پشتیبان، عکس، هر فایلی.
 *
 *  ⚠️ `headers` برای چیزهایی است که **کنارِ** فایل باید بروند و جایی
 *  داخلش ندارند: نسخهٔ برنامه، برچسبِ کاربر. بی این، تنها راه چسباندنشان
 *  به نشانی بود و نشانی جای داده نیست.
 */
data class RawBody(
  val bytes: ByteArray,
  val contentType: String = "application/octet-stream",
  val headers: Map<String, String> = emptyMap(),
) {
  //  ⚠️ `ByteArray` در `data class` برابریِ ارجاعی دارد؛ کاتلین برای
  //  همین هشدار می‌دهد. این دو تا فقط آن هشدار را می‌بندند — هیچ‌جا
  //  دو `RawBody` مقایسه نمی‌شود.
  override fun equals(other: Any?): Boolean = this === other
  override fun hashCode(): Int = System.identityHashCode(this)
}
