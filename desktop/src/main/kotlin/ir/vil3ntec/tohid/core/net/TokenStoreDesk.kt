package ir.vil3ntec.tohid.core.net

import android.content.Context
import ir.vil3ntec.tohid.desktop.SecretBox

/**
 *  نگهداریِ توکن‌ها روی کامپیوتر — جایگزینِ `TokenStore`ِ اندروید.
 *
 *  اندروید کلید را در Keystoreِ سخت‌افزاری نگه می‌دارد. کامپیوتر چنین
 *  چیزی برای JVM ندارد؛ پس توکن‌ها **رمزشده** (AES-GCM) در فایلِ تنظیمات
 *  می‌نشینند و کلید در فایلِ جدایی که فقط صاحبِ حسابِ سیستم می‌خواندش
 *  (`SecretBox`). کپیِ پوشهٔ تنظیمات به تنهایی به نشست نمی‌رسد.
 *
 *  ⚠️ همان نام‌های فایل و کلیدهای اندروید، تا `SavedLogins.purgeTokens`
 *  و بقیه همان‌طور کار کنند.
 */
class TokenStore(context: Context) : TokenStorage {

  private val prefs = context.applicationContext.getSharedPreferences("tohid-session-secure", Context.MODE_PRIVATE)

  private fun read(key: String): String? =
    prefs.getString(key, null)?.let { SecretBox.open(it) }?.takeIf { it.isNotBlank() }

  private fun seal(value: String?): String? = value?.let { SecretBox.seal(it) }

  override var accessToken: String?
    get() = read(ACCESS)
    set(value) = prefs.edit().putString(ACCESS, seal(value)).apply()

  override var refreshToken: String?
    get() = read(REFRESH)
    set(value) = prefs.edit().putString(REFRESH, seal(value)).apply()

  override var accessExpiresAt: Long
    get() = prefs.getLong(EXPIRES, 0)
    set(value) = prefs.edit().putLong(EXPIRES, value).apply()

  override val signedIn: Boolean get() = accessToken != null

  override fun save(access: String?, refresh: String?, expiresAt: Long) {
    val editor = prefs.edit()
    editor.putString(ACCESS, seal(access))
    if (refresh != null) editor.putString(REFRESH, seal(refresh))
    if (expiresAt > 0) editor.putLong(EXPIRES, expiresAt)
    editor.apply()
  }

  override fun clear() {
    prefs.edit().remove(ACCESS).remove(REFRESH).remove(EXPIRES).apply()
  }

  private companion object {
    const val ACCESS = "access_token"
    const val REFRESH = "refresh_token"
    const val EXPIRES = "access_expires_at"
  }
}
