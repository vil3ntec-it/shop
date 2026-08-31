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
) {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
  ): JsonObject = withContext(Dispatchers.IO) {
    var attempt = 0
    var wait = ApiConfig.RETRY_BACKOFF_MS

    while (true) {
      try {
        return@withContext once(method, path, body, token)
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

  /** یک تلاش، بدونِ تکرار */
  private fun once(method: String, path: String, body: JsonObject?, token: String?): JsonObject {
    val base = baseUrl()
    ApiConfig.reject(base, allowInsecure)?.let { reason ->
      throw if (reason == ApiConfig.Rejection.MISSING) ApiFailure.NotConfigured()
      else ApiFailure.BadConfiguration(reason.message)
    }
    //  نت که نیست، رفتن سراغِ شبکه فقط چند ثانیه انتظارِ بی‌فایده است
    if (!online()) throw ApiFailure.Offline()

    val url = runCatching { URL(ApiConfig.urlOf(base, path)) }.getOrNull()
      ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")

    val connection = runCatching { url.openConnection() as HttpURLConnection }.getOrNull()
      ?: throw ApiFailure.BadConfiguration("نشانی سرور درست نیست")

    try {
      connection.requestMethod = method
      connection.connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
      connection.readTimeout = ApiConfig.READ_TIMEOUT_MS
      connection.setRequestProperty("Accept", "application/json")
      if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")

      if (body != null) {
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
