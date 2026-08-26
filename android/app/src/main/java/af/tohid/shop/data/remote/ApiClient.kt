package af.tohid.shop.data.remote

import af.tohid.shop.BuildConfig
import af.tohid.shop.data.repo.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ساخت کلاینت شبکه.
 *
 * آدرس سرور از تنظیمات برنامه خوانده می‌شود و اگر خالی بود، از مقدار
 * پیش‌فرضِ زمان ساخت. هیچ آدرسی داخل کد قفل نشده؛ با جابه‌جا شدن سرور
 * از کامپیوتر خانگی به VPS فقط همین یک مقدار عوض می‌شود.
 *
 * توکن دسترسی خودکار ضمیمه می‌شود و اگر منقضی شده باشد، یک بار پشت پرده
 * تازه می‌شود تا کاربر وسط کار بیرون نیفتد.
 */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    @Volatile private var cachedBase: String? = null
    @Volatile private var cachedApi: TohidApi? = null

    /** آدرس پایه‌ی سرور، یا null اگر هیچ‌جا تنظیم نشده باشد. */
    fun baseUrl(session: SessionStore): String? {
        val raw = session.serverUrl()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SERVER_URL.takeIf { it.isNotBlank() }
            ?: return null
        val trimmed = raw.trim().trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://$trimmed/"
        }
        return "$trimmed/"
    }

    fun isConfigured(session: SessionStore): Boolean = baseUrl(session) != null

    fun api(session: SessionStore): TohidApi? {
        val base = baseUrl(session) ?: return null
        val existing = cachedApi
        if (existing != null && cachedBase == base) return existing
        return synchronized(this) {
            val again = cachedApi
            if (again != null && cachedBase == base) return again
            val built = build(base, session)
            cachedBase = base
            cachedApi = built
            built
        }
    }

    fun invalidate() = synchronized(this) { cachedApi = null; cachedBase = null }

    private fun build(base: String, session: SessionStore): TohidApi {
        val auth = Interceptor { chain ->
            val token = session.accessToken()
            val req = if (token.isNullOrBlank()) chain.request()
            else chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            chain.proceed(req)
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(auth)
            .authenticator(RefreshAuthenticator(base, session))
            .build()

        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA))
            .build()
            .create(TohidApi::class.java)
    }

    /**
     * وقتی سرور ۴۰۱ می‌دهد، یک بار با توکن تازه‌سازی نشست را نو می‌کند.
     * اگر آن هم نشد، نشست پاک می‌شود تا برنامه صفحه‌ی ورود را نشان دهد —
     * ولی دفتر دکان روی گوشی دست‌نخورده می‌ماند.
     */
    private class RefreshAuthenticator(
        private val base: String,
        private val session: SessionStore,
    ) : Authenticator {

        private val bare = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        override fun authenticate(route: Route?, response: Response): Request? {
            if (responseCount(response) >= 2) return null
            val used = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

            synchronized(this) {
                // شاید درخواست دیگری همین لحظه توکن را تازه کرده باشد
                val current = session.accessToken()
                if (!current.isNullOrBlank() && current != used) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $current").build()
                }

                val refresh = session.refreshToken()
                if (refresh.isNullOrBlank()) return null

                val fresh = runCatching { refreshToken(refresh) }.getOrNull()
                if (fresh.isNullOrBlank()) {
                    session.clearSession()
                    return null
                }
                session.updateAccessToken(fresh)
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $fresh").build()
            }
        }

        private fun refreshToken(refresh: String): String? {
            val body = ApiClient.json
                .encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
                .toRequestBody(ApiClient.JSON_MEDIA)
            val req = Request.Builder().url("${base}api/auth/refresh").post(body).build()
            bare.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val text = res.body?.string().orEmpty()
                if (text.isBlank()) return null
                return runCatching {
                    ApiClient.json.decodeFromString(RefreshResponse.serializer(), text).accessToken
                }.getOrNull()
            }
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) { count++; prior = prior.priorResponse }
            return count
        }
    }
}
