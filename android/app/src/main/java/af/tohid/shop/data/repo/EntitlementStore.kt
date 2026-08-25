package af.tohid.shop.data.repo

import android.content.Context
import af.tohid.shop.data.remote.ApiClient
import af.tohid.shop.data.remote.EntitlementDto
import af.tohid.shop.data.remote.TrialDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * وضعیت دسترسی کاربر (رایگان / دوره آزمایشی / اشتراک).
 *
 * تصمیم را همیشه سرور می‌گیرد. آخرین پاسخ کش می‌شود تا برنامه آفلاین هم
 * بداند چه چیزی باز است — ولی ساعت گوشی هیچ نقشی در آن ندارد.
 */
class EntitlementStore(context: Context, private val session: SessionStore) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tohid_entitlement", Context.MODE_PRIVATE)

    private val json = ApiClient.json

    /** قابلیت‌های رایگان — همان فهرست سرور، برای وقتی هنوز پاسخی نگرفته‌ایم. */
    private val freeDefaults = listOf(
        "warehouse", "expenses", "purchasing", "reports", "audit_log", "backup", "csv_export",
    )
    private val coreDefaults = listOf("dashboard", "products", "settings")

    @Volatile
    private var cached: EntitlementDto = load()

    val current: EntitlementDto get() = cached

    private fun load(): EntitlementDto {
        val raw = prefs.getString(KEY, null) ?: return EntitlementDto(
            source = "guest", features = freeDefaults, free = freeDefaults, core = coreDefaults,
        )
        return runCatching { json.decodeFromString(EntitlementDto.serializer(), raw) }
            .getOrElse {
                EntitlementDto(source = "guest", features = freeDefaults,
                    free = freeDefaults, core = coreDefaults)
            }
    }

    private fun save(dto: EntitlementDto) {
        cached = dto
        runCatching {
            prefs.edit().putString(KEY, json.encodeToString(EntitlementDto.serializer(), dto)).apply()
        }
    }

    fun has(feature: String): Boolean =
        coreDefaults.contains(feature) || cached.features.contains(feature)

    /** پیام وضعیت برای نمایش: روزهای باقی‌مانده یا پایان دوره. */
    fun statusText(): String {
        val t = cached.trial
        return when {
            cached.source == "subscription" -> "اشتراک فعال"
            t.active && t.daysLeft <= 1 -> "کمتر از یک روز از دوره آزمایشی باقی مانده"
            t.active -> "${t.daysLeft} روز از دوره آزمایشی باقی مانده است"
            t.used -> "دوره آزمایشی به پایان رسیده است"
            else -> "حساب رایگان بسازید و ۷ روز رایگان امتحان کنید"
        }
    }

    suspend fun refresh(): EntitlementDto = withContext(Dispatchers.IO) {
        val api = ApiClient.api(session) ?: return@withContext cached
        try {
            val res = api.entitlement()
            save(res.entitlement)
            res.entitlement
        } catch (e: Exception) {
            cached      // آفلاین: با وضعیت کش‌شده ادامه می‌دهیم
        }
    }

    private companion object { const val KEY = "entitlement_json" }
}
