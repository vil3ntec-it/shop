package ir.vil3ntec.tohid.core.net

import ir.vil3ntec.tohid.core.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import ir.vil3ntec.tohid.platform.ServerSwitchAndroid
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
  /*
   *  ── هویتِ برنامه روی **هر** درخواست ───────────────────────────────
   *  قراردادِ `docs/LOGIN-fa.md`: `X-App` · `X-Device` · `X-App-Version`
   *  · `X-Request-Id`.
   *
   *  ⚠️ `X-App` فقط یک برچسب نیست: سرور نشستِ تازه را به همان بخش مهر
   *  می‌زند و توکنِ یک بخش در بخشِ دیگر **پیدا نمی‌شود**. نگفتنش یعنی
   *  تکیه بر پیش‌فرضِ سرور — همان چیزی که یک بار اپِ کارمندانِ پمپ را
   *  کاملاً مرده کرد.
   *
   *  ⛔ هیچ چیزِ شناسایی‌کنندهٔ **کاربر** در سرآیند نمی‌رود: نه ایمیل،
   *  نه نامِ گوشی. فقط شناسهٔ دستگاه که خودِ برنامه ساخته است.
   */
  private val identity: () -> Map<String, String> = { emptyMap() },
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
    /**
     *  سرآیندهای همین درخواست (مثلِ بلیتِ شمارهٔ خریدار `X-Store-Ticket` یا
     *  `Idempotency-Key`). ⛔ چیزِ شناسایی‌کنندهٔ کاربر جز همان بلیت‌ها نه؛
     *  توکن و هویتِ برنامه را نمی‌توانند عوض کنند (`PROTECTED_HEADERS`).
     */
    headers: Map<String, String> = emptyMap(),
    /** بی شناسهٔ دستگاه (`X-Device`) — درخواست‌های خریدار (`DEVICE_HEADERS`) */
    anonymous: Boolean = false,
  ): JsonObject = withContext(Dispatchers.IO) {
    var attempt = 0
    var wait = ApiConfig.RETRY_BACKOFF_MS

    while (true) {
      try {
        return@withContext once(method, path, body, token, raw, headers, anonymous)
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
    headers: Map<String, String> = emptyMap(),
    anonymous: Boolean = false,
  ): JsonObject {
    val first = prefix
    try {
      return attempt(method, path, body, token, first, raw, headers, anonymous)
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
        attempt(method, path, body, token, other, headers = headers, anonymous = anonymous)
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
    headers: Map<String, String> = emptyMap(),
    anonymous: Boolean = false,
  ): JsonObject {
    val base = baseUrl()
    //  همان `ApiConfig.reject` ِ امروز؛ فقط نشانیِ پروفایلِ سرور (پرچمِ `app.multi_server`) قاعدهٔ خودش را دارد
    ServerSwitchAndroid.reject(base, allowInsecure)?.let { throw it }
    //  نت که نیست، رفتن سراغِ شبکه فقط چند ثانیه انتظارِ بی‌فایده است
    if (!online()) throw ApiFailure.Offline()

    val url = runCatching { URL(ApiConfig.urlOf(base, path, prefix)) }.getOrNull()
      ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")

    val connection = runCatching { url.openConnection() as HttpURLConnection }.getOrNull()
      ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")
    //  گواهیِ سنجاق‌شدهٔ سرورِ شبکهٔ دکان — فقط اگر این نشانی چنین گواهی‌ای دارد
    ServerSwitchAndroid.prepare(connection, base)

    try {
      connection.requestMethod = method
      connection.connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
      connection.readTimeout = ApiConfig.READ_TIMEOUT_MS
      connection.setRequestProperty("Accept", "application/json")
      stamp(connection, token, headers, anonymous)

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
        //  همان سدِ سرآیندهای درخواست: بدنهٔ خام هم توکن و هویتِ برنامه را عوض نمی‌کند
        raw.headers.forEach { (k, v) -> if (k.lowercase() !in PROTECTED_HEADERS) connection.setRequestProperty(k, v) }
        connection.setFixedLengthStreamingMode(raw.bytes.size)
        val report = raw.onProgress
        if (report == null) connection.outputStream.use { it.write(raw.bytes) }
        else connection.outputStream.use { out ->
          //  CHAT-05: تکه‌تکه، تا صدا زننده بداند چند بایت به سوکت رسید (بی گزارش ⇒ همان یک write)
          var off = 0
          while (off < raw.bytes.size) {
            val n = minOf(RawBody.CHUNK, raw.bytes.size - off)
            out.write(raw.bytes, off, n)
            off += n
            runCatching { report(off.toLong()) }
          }
        }
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
      //  نشانیِ دیگرِ همان سرور (شبکهٔ دکان ⇐ اینترنت) دفعهٔ بعد از نو سنجیده شود
      ServerSwitchAndroid.reportFailure(base)
      //  هیچ استثنای خامی از این لایه بیرون نمی‌رود
      throw ApiFailure.fromException(error)
    } finally {
      runCatching { connection.disconnect() }
    }
  }

  /**
   *  توکن، هویتِ برنامه و سرآیندهای همین درخواست — به همین ترتیب و فقط این‌جا.
   *
   *  ⛔ سرآیندِ درخواست هرگز جای توکن یا هویتِ برنامه را نمی‌گیرد: «توکنِ صاحب
   *  دکان در درخواستِ خریدار نیست» ساختاری است، نه قرارِ صدازننده.
   *  ⛔ `anonymous`: شناسهٔ دستگاه نمی‌رود، تا سرور نتواند شمارهٔ خریدار و
   *  سفارش‌هایش را به دستگاهِ صاحب دکانی که همین گوشی را دارد پیوند بزند.
   */
  private fun stamp(connection: HttpURLConnection, token: String?, headers: Map<String, String>, anonymous: Boolean) {
    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
    runCatching { identity() }.getOrNull()?.forEach { (k, v) ->
      if (v.isNotBlank() && !(anonymous && k.lowercase() in DEVICE_HEADERS)) connection.setRequestProperty(k, v)
    }
    headers.forEach { (k, v) ->
      if (v.isNotBlank() && k.lowercase() !in PROTECTED_HEADERS) connection.setRequestProperty(k, v)
    }
  }

  companion object {
    /** نامِ کوچکِ سرآیندهایی که فقط خودِ این لایه می‌گذارد */
    val PROTECTED_HEADERS: Set<String> = setOf("authorization", "x-app", "x-app-id", "x-device", "x-app-version", "cookie")

    /** هویتِ دستگاه — در درخواستِ `anonymous` نمی‌رود */
    val DEVICE_HEADERS: Set<String> = setOf("x-device")
  }

  /**
   *  گرفتنِ **بایت‌های خام** (عکس و صدای چتِ دکان، v3 §۳۳) — همان نشانی،
   *  پیشوندِ پیداشده، سرآیندهای هویت و همان قاعدهٔ خطا؛ فقط بدنه JSON نیست.
   *
   *  ⚠️ سقف دارد: پاسخی بزرگ‌تر از `maxBytes` خوانده نمی‌شود (نه OOM، نه
   *  دانلودِ بی‌پایان روی اینترنتِ گران). یک بار، بی تلاشِ خودکار: صفحه
   *  خودش «دوباره» دارد.
   */
  suspend fun fetchBytes(
    path: String,
    token: String?,
    maxBytes: Int = 8 * 1024 * 1024,
    anonymous: Boolean = false,
  ): ByteArray =
    withContext(Dispatchers.IO) {
      val base = baseUrl()
      ServerSwitchAndroid.reject(base, allowInsecure)?.let { throw it }
      if (!online()) throw ApiFailure.Offline()
      val url = runCatching { URL(ApiConfig.urlOf(base, path, prefix)) }.getOrNull()
        ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")
      val connection = runCatching { url.openConnection() as HttpURLConnection }.getOrNull()
        ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")
      ServerSwitchAndroid.prepare(connection, base)
      try {
        connection.requestMethod = "GET"
        connection.connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
        connection.readTimeout = ApiConfig.READ_TIMEOUT_MS
        stamp(connection, token, emptyMap(), anonymous)
        val status = connection.responseCode
        if (status !in 200..299) {
          val text = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
          throw ApiFailure.fromHttp(status, text, authenticated = token != null)
        }
        val tooBig = { ApiFailure.Invalid("فایل بزرگ‌تر از سقفِ مجاز است", "too_large") }
        if (connection.contentLengthLong > maxBytes) throw tooBig()
        connection.inputStream.use { input ->
          val out = java.io.ByteArrayOutputStream()
          val buf = ByteArray(16 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > maxBytes) throw tooBig()
          }
          out.toByteArray()
        }
      } catch (failure: ApiFailure) {
        throw failure
      } catch (error: Throwable) {
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
  /** بایت‌های نوشته‌شده تا این‌جا (CHAT-05)؛ null ⇒ بدنه یک‌جا نوشته می‌شود، مثلِ پیش */
  val onProgress: ((Long) -> Unit)? = null,
) {
  companion object {
    /** اندازهٔ هر تکه وقتی پیشرفت خواسته شده */
    const val CHUNK = 16 * 1024
  }

  //  ⚠️ `ByteArray` در `data class` برابریِ ارجاعی دارد؛ کاتلین برای
  //  همین هشدار می‌دهد. این دو تا فقط آن هشدار را می‌بندند — هیچ‌جا
  //  دو `RawBody` مقایسه نمی‌شود.
  override fun equals(other: Any?): Boolean = this === other
  override fun hashCode(): Int = System.identityHashCode(this)
}
