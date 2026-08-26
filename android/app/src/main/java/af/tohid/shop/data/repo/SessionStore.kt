package af.tohid.shop.data.repo

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * نگهداری آدرس سرور، توکن‌ها و وضعیت همگام‌سازی.
 *
 * توکن‌ها در حافظه‌ی رمزشده‌ی اندروید می‌روند. اگر روی دستگاهی
 * رمزنگاری در دسترس نباشد، به حافظه‌ی معمولی برمی‌گردد تا برنامه
 * از کار نیفتد (بدتر از آن، قفل شدن کاربر بیرون از برنامه است).
 */
class SessionStore(context: Context) {

    private val app = context.applicationContext

    private val secure: SharedPreferences by lazy {
        runCatching {
            val key = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app, "tohid_secure_prefs", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ) as SharedPreferences
        }.getOrElse {
            app.getSharedPreferences("tohid_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val plain: SharedPreferences =
        app.getSharedPreferences("tohid_prefs", Context.MODE_PRIVATE)

    // ---------- سرور ----------
    fun serverUrl(): String? = plain.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() }
    fun setServerUrl(url: String) {
        plain.edit().putString(KEY_SERVER, url.trim().trimEnd('/')).apply()
    }

    // ---------- حساب ----------
    fun accessToken(): String? = secure.getString(KEY_ACCESS, null)
    fun refreshToken(): String? = secure.getString(KEY_REFRESH, null)
    fun userId(): String = plain.getString(KEY_USER_ID, "") ?: ""
    fun userLabel(): String = plain.getString(KEY_USER_LABEL, "") ?: ""
    fun isLoggedIn(): Boolean = !accessToken().isNullOrBlank()

    fun saveSession(userId: String, label: String, access: String, refresh: String) {
        secure.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply()
        plain.edit().putString(KEY_USER_ID, userId).putString(KEY_USER_LABEL, label).apply()
    }
    fun updateAccessToken(access: String) {
        secure.edit().putString(KEY_ACCESS, access).apply()
    }
    fun clearSession() {
        secure.edit().clear().apply()
        plain.edit().remove(KEY_USER_ID).remove(KEY_USER_LABEL)
            .remove(KEY_SHOP_ID).remove(KEY_SHOP_NAME).remove(KEY_ROLE)
            .remove(KEY_REV).remove(KEY_INVOICE_BLOCK).remove(KEY_PERMISSIONS).apply()
    }

    // ---------- دکان ----------
    fun shopId(): String = plain.getString(KEY_SHOP_ID, "") ?: ""
    fun shopName(): String = plain.getString(KEY_SHOP_NAME, "") ?: ""
    fun role(): String = plain.getString(KEY_ROLE, "staff") ?: "staff"
    fun isOwner(): Boolean = role() == "owner"
    fun saveShop(id: String, name: String, role: String) {
        plain.edit().putString(KEY_SHOP_ID, id).putString(KEY_SHOP_NAME, name)
            .putString(KEY_ROLE, role).apply()
    }
    fun clearShop() {
        plain.edit().remove(KEY_SHOP_ID).remove(KEY_SHOP_NAME).remove(KEY_ROLE)
            .remove(KEY_PERMISSIONS).remove(KEY_REV).apply()
    }

    /**
     * دسترسی‌های این نقش، همان‌طور که سرور اعلام کرده است.
     *
     * این فقط برای چیدن دکمه‌هاست؛ تصمیم واقعی را سرور می‌گیرد و اگر
     * کسی از این طرف دور بزند، درخواستش آنجا رد می‌شود.
     */
    fun permissions(): Set<String> =
        plain.getStringSet(KEY_PERMISSIONS, emptySet()) ?: emptySet()

    fun savePermissions(list: Collection<String>) {
        plain.edit().putStringSet(KEY_PERMISSIONS, list.toSet()).apply()
    }

    fun can(permission: String): Boolean {
        val set = permissions()
        return if (set.isEmpty()) role() == "owner" else set.contains(permission)
    }

    /** کاربر گفته «فعلاً بدون حساب ادامه بده» — صفحه‌ی ورود دیگر سد راهش نشود. */
    fun authSkipped(): Boolean = plain.getBoolean(KEY_SKIP_AUTH, false)
    fun setAuthSkipped(v: Boolean) = plain.edit().putBoolean(KEY_SKIP_AUTH, v).apply()

    // ---------- همگام‌سازی ----------
    fun rev(): Long = plain.getLong(KEY_REV, 0L)
    fun setRev(v: Long) = plain.edit().putLong(KEY_REV, v).apply()
    fun lastSyncAt(): Long = plain.getLong(KEY_LAST_SYNC, 0L)
    fun setLastSyncAt(v: Long) = plain.edit().putLong(KEY_LAST_SYNC, v).apply()

    /** شناسه‌ی پایدار این دستگاه — یک بار ساخته و برای همیشه می‌ماند. */
    fun deviceId(): String {
        plain.getString(KEY_DEVICE, null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "")
        plain.edit().putString(KEY_DEVICE, id).apply()
        return id
    }

    /**
     * بازه‌ی شماره فاکتور این عضو.
     * بدون این، دو نفری که آفلاین می‌فروشند هر دو فاکتور ۱۰۰۰ صادر می‌کنند.
     */
    fun invoiceBlock(): Long = plain.getLong(KEY_INVOICE_BLOCK, 0L)
    fun setInvoiceBlock(v: Long) = plain.edit().putLong(KEY_INVOICE_BLOCK, v).apply()

    // ---------- به‌روزرسانی ----------
    fun lastUpdateCheck(): Long = plain.getLong(KEY_UPDATE_CHECK, 0L)
    fun setLastUpdateCheck(v: Long) = plain.edit().putLong(KEY_UPDATE_CHECK, v).apply()
    fun skippedVersion(): String = plain.getString(KEY_SKIP_VERSION, "") ?: ""
    fun setSkippedVersion(v: String) = plain.edit().putString(KEY_SKIP_VERSION, v).apply()

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_LABEL = "user_label"
        const val KEY_SHOP_ID = "shop_id"
        const val KEY_SHOP_NAME = "shop_name"
        const val KEY_ROLE = "shop_role"
        const val KEY_REV = "sync_rev"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_DEVICE = "device_id"
        const val KEY_INVOICE_BLOCK = "invoice_block"
        const val KEY_UPDATE_CHECK = "update_check_at"
        const val KEY_SKIP_VERSION = "skip_version"
        const val KEY_PERMISSIONS = "shop_permissions"
        const val KEY_SKIP_AUTH = "auth_skipped"
    }
}
