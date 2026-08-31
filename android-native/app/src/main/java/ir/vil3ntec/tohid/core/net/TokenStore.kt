package ir.vil3ntec.tohid.core.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 *  نگهداریِ توکن‌ها — رمزشده.
 *
 *  توکنِ دسترسی یعنی «هرکس این را دارد، همان کاربر است». تا دیروز کنارِ
 *  بقیهٔ تنظیمات در یک `SharedPreferences` معمولی می‌نشست: روی گوشیِ
 *  root‌شده، در پشتیبانِ خودکارِ اندروید، و برای هر ابزارِ خواندنِ حافظه،
 *  خواندنی بود. برنامهٔ مدیریت از روزِ اول رمزش می‌کرد؛ این یکی نه.
 *
 *  حالا با کلیدی در Keystore خودِ گوشی رمز می‌شود. اگر Keystore روی
 *  گوشیِ خاصی سالم نباشد — روی چند گوشیِ قدیمی هست — به حالتِ ساده
 *  برمی‌گردیم، چون برنامه‌ای که اصلاً بالا نمی‌آید بدتر از برنامه‌ای است
 *  که توکنش رمز نشده.
 *
 *  توکن‌ها هیچ‌وقت log نمی‌شوند. حتی بریده‌شان.
 */
class TokenStore(context: Context) : TokenStorage {

  private val app = context.applicationContext

  private val prefs: SharedPreferences = runCatching {
    val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    EncryptedSharedPreferences.create(
      SECURE_FILE,
      alias,
      app,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    ) as SharedPreferences
  }.getOrElse {
    app.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
  }

  init { migrateLegacy() }

  override var accessToken: String?
    get() = prefs.getString(ACCESS, null)?.takeIf { it.isNotBlank() }
    set(value) = prefs.edit().putString(ACCESS, value).apply()

  override var refreshToken: String?
    get() = prefs.getString(REFRESH, null)?.takeIf { it.isNotBlank() }
    set(value) = prefs.edit().putString(REFRESH, value).apply()

  /** ساعتی که توکنِ دسترسی می‌میرد — برای تازه کردنِ پیش‌دستانه */
  override var accessExpiresAt: Long
    get() = prefs.getLong(EXPIRES, 0)
    set(value) = prefs.edit().putLong(EXPIRES, value).apply()

  override val signedIn: Boolean get() = accessToken != null

  override fun save(access: String?, refresh: String?, expiresAt: Long) {
    val editor = prefs.edit()
    editor.putString(ACCESS, access)
    //  سرور در پاسخِ تازه‌سازی، توکنِ تازه‌سازی را دوباره نمی‌فرستد؛
    //  نبودنش یعنی «عوض نشده»، نه «پاک کن»
    if (refresh != null) editor.putString(REFRESH, refresh)
    if (expiresAt > 0) editor.putLong(EXPIRES, expiresAt)
    editor.apply()
  }

  override fun clear() {
    prefs.edit().remove(ACCESS).remove(REFRESH).remove(EXPIRES).apply()
  }

  /**
   *  توکنی که از نسخهٔ قبلی در حافظهٔ ساده مانده را می‌آورد و آنجا را
   *  پاک می‌کند.
   *
   *  بدونِ این، کسی که برنامه را به‌روز می‌کند یا باید دوباره وارد شود
   *  (بدونِ اینکه بفهمد چرا)، یا توکنِ قدیمی‌اش تا ابد رمزنشده در گوشی
   *  می‌ماند. یک بار اجرا می‌شود و بس.
   */
  private fun migrateLegacy() {
    if (prefs.getBoolean(MIGRATED, false)) return
    runCatching {
      val legacy = app.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
      val access = legacy.getString(ACCESS, null)
      val refresh = legacy.getString(REFRESH, null)
      if (!access.isNullOrBlank() || !refresh.isNullOrBlank()) {
        prefs.edit().putString(ACCESS, access).putString(REFRESH, refresh).apply()
      }
      legacy.edit().remove(ACCESS).remove(REFRESH).apply()
    }
    prefs.edit().putBoolean(MIGRATED, true).apply()
  }

  private companion object {
    const val SECURE_FILE = "tohid-session-secure"
    const val FALLBACK_FILE = "tohid-session"
    /** همان پروندهٔ `SyncStore` — جایی که توکن‌ها تا دیروز رمزنشده بودند */
    const val LEGACY_FILE = "tohid-sync"

    const val ACCESS = "access_token"
    const val REFRESH = "refresh_token"
    const val EXPIRES = "access_expires_at"
    const val MIGRATED = "legacy_migrated"
  }
}
