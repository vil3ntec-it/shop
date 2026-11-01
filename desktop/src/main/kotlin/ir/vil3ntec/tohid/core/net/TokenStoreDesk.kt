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

  /**
   *  همان `stashTo`/`openFrom` ِ اندروید (پرچمِ `app.multi_server`؛ صدا از `ServerSwitchAndroid`): نشستِ هر
   *  سرور زیرِ نامِ دامنه‌اش بایگانی می‌شود. مقدارها همان رشتهٔ رمزشدهٔ `SecretBox` جابه‌جا می‌شوند و هرگز باز
   *  نمی‌شوند؛ توکنِ یک سرور هرگز به سرورِ دیگر فرستاده نمی‌شود.
   */
  fun stashTo(scope: String) {
    val tag = "@srv:" + scope.ifEmpty { "default" }
    val e = prefs.edit()
    for (k in listOf(ACCESS, REFRESH)) {
      val v = prefs.getString(k, null)
      if (v != null) e.putString(k + tag, v) else e.remove(k + tag)
    }
    if (prefs.contains(EXPIRES)) e.putLong(EXPIRES + tag, prefs.getLong(EXPIRES, 0)) else e.remove(EXPIRES + tag)
    e.remove(ACCESS).remove(REFRESH).remove(EXPIRES).commit()
  }

  /** نشستِ بایگانی‌شدهٔ یک سرور — نبود ⇒ بی نشست (ورودِ تازه) */
  fun openFrom(scope: String) {
    val tag = "@srv:" + scope.ifEmpty { "default" }
    val e = prefs.edit().remove(ACCESS).remove(REFRESH).remove(EXPIRES)
    for (k in listOf(ACCESS, REFRESH)) prefs.getString(k + tag, null)?.let { e.putString(k, it) }
    if (prefs.contains(EXPIRES + tag)) e.putLong(EXPIRES, prefs.getLong(EXPIRES + tag, 0))
    e.remove(ACCESS + tag).remove(REFRESH + tag).remove(EXPIRES + tag).commit()
  }

  private companion object {
    const val ACCESS = "access_token"
    const val REFRESH = "refresh_token"
    const val EXPIRES = "access_expires_at"
  }
}
