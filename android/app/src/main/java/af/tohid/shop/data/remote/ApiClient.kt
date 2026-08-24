package af.tohid.shop.data.remote

import af.tohid.shop.data.repo.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ساخت کلاینت شبکه.
 *
 * آدرس سرور را کاربر می‌دهد و هر وقت عوض شود، کلاینت دوباره ساخته می‌شود.
 * توکن دسترسی خودکار ضمیمه می‌شود و در صورت انقضا یک بار تازه می‌شود.
 */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Volatile private var cachedBase: String? = null
    @Volatile private var cachedApi: TohidApi? = null

    fun api(session: SessionStore): TohidApi? {
        val base = session.serverUrl()?.trimEnd('/')?.plus("/") ?: return null
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
            .build()

        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TohidApi::class.java)
    }
}
