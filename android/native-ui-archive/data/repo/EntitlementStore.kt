package af.tohid.shop.data.repo

import android.content.Context
import af.tohid.shop.data.remote.ApiClient
import af.tohid.shop.data.remote.EntitlementDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * وضعیت دسترسی دکان (رایگان / دوره آزمایشی / اشتراک).
 *
 * تصمیم را همیشه سرور می‌گیرد و با ساعت سرور. آخرین پاسخ کش می‌شود تا
 * برنامه در نبود اینترنت هم بداند چه چیزی باز است — ولی عوض کردن تاریخ
 * گوشی هیچ اثری روی آن ندارد، چون تاریخ‌ها از سرور آمده‌اند و برنامه
 * خودش چیزی به آن‌ها اضافه نمی‌کند.
 */
class EntitlementStore(context: Context, private val session: SessionStore) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tohid_entitlement", Context.MODE_PRIVATE)

    private val json = ApiClient.json

    /** قابلیت‌های رایگان — تا وقتی پاسخی از سرور نگرفته‌ایم. */
    private val freeDefaults = listOf(
        "warehouse", "expenses", "purchasing", "reports", "audit_log", "backup", "csv_export",
    )
    private val coreDefaults = listOf("dashboard", "products", "settings")

    @Volatile
    private var cached: EntitlementDto = load()

    val current: EntitlementDto get() = cached

    private fun fallback() = EntitlementDto(source = "guest", features = freeDefaults + coreDefaults)

    private fun load(): EntitlementDto {
        val raw = prefs.getString(KEY, null) ?: return fallback()
        return runCatching { json.decodeFromString(EntitlementDto.serializer(), raw) }
            .getOrElse { fallback() }
    }

    private fun save(dto: EntitlementDto) {
        cached = dto
        runCatching {
            prefs.edit().putString(KEY, json.encodeToString(EntitlementDto.serializer(), dto)).apply()
        }
    }

    fun clear() {
        cached = fallback()
        prefs.edit().remove(KEY).apply()
    }

    /**
     * تا وقتی سروری تنظیم نشده، هیچ قابلیتی قفل نمی‌شود.
     * قفل کردن بدون سرور نه قابل اتکاست و نه راهی برای خرید باقی می‌گذارد.
     */
    private fun enforcing(): Boolean = ApiClient.isConfigured(session) && session.isLoggedIn()

    fun has(feature: String): Boolean =
        coreDefaults.contains(feature) || !enforcing() || cached.features.contains(feature)

    /** پیام وضعیت برای نمایش. */
    fun statusText(): String {
        val t = cached.trial
        val sub = cached.subscription
        return when {
            !enforcing() -> "همه‌ی قابلیت‌ها باز است"
            cached.source == "subscription" && sub.daysLeft > 0 ->
                "اشتراک فعال — ${sub.daysLeft} روز باقی مانده"
            cached.source == "subscription" -> "اشتراک فعال"
            sub.status == "suspended" -> "اشتراک این دکان معلق شده است"
            sub.status == "expired" -> "اشتراک این دکان به پایان رسیده است"
            t.active && t.daysLeft <= 1 -> "کمتر از یک روز از دوره آزمایشی باقی مانده"
            t.active -> "${t.daysLeft} روز از دوره آزمایشی باقی مانده است"
            t.used -> "دوره آزمایشی به پایان رسیده است"
            else -> "برای شروع، حساب بسازید"
        }
    }

    /** آیا اشتراک واقعاً فعال است (نه آزمایشی). */
    fun isPaid(): Boolean = cached.source == "subscription" && cached.subscription.active

    suspend fun refresh(): EntitlementDto = withContext(Dispatchers.IO) {
        val api = ApiClient.api(session) ?: return@withContext cached
        if (!session.isLoggedIn()) return@withContext cached
        try {
            val res = api.subscription()
            val dto = res.toEntitlement()
            save(dto)
            dto
        } catch (e: Exception) {
            cached      // آفلاین: با وضعیت کش‌شده ادامه می‌دهیم
        }
    }

    /** ذخیره‌ی وضعیتی که همراه پاسخ‌های دیگر آمده (ورود، دکان). */
    fun update(dto: EntitlementDto?) { if (dto != null) save(dto) }

    private companion object { const val KEY = "entitlement_json" }
}
