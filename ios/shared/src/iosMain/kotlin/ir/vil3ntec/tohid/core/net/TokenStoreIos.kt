package ir.vil3ntec.tohid.core.net

import android.content.Context
import ir.vil3ntec.tohid.ios.keychain.Keychain

/*
 *  جایگزینِ iOS ِ `core/net/TokenStore.kt` (همان API ِ `TokenStorage`؛ اندروید: EncryptedSharedPreferences،
 *  کامپیوتر: ‎TokenStoreDesk‎ با SecretBox). توکن‌های ورود در کلیددانِ iOS («فقط همین دستگاه»، ‎Keychain.kt‎)؛
 *  زمانِ انقضا که راز نیست در همان پروندهٔ تنظیماتِ اندروید (`tohid-session-secure`).
 *  ⛔ خطای کلیددان (مثلاً دستگاهِ هنوز بازنشده) ⇐ «وارد نشده» برای خواندن؛ نوشتن خطا را بالا می‌دهد.
 */
class TokenStore(context: Context) : TokenStorage {
  private val prefs = context.applicationContext.getSharedPreferences("tohid-session-secure", Context.MODE_PRIVATE)

  private fun read(key: String): String? =
    runCatching { Keychain.read(key) }.getOrNull()?.decodeToString()?.takeIf { it.isNotBlank() }

  private fun write(key: String, value: String?) {
    if (value == null) Keychain.delete(key) else Keychain.put(key, value.encodeToByteArray())
  }

  override var accessToken: String?
    get() = read(ACCESS)
    set(value) = write(ACCESS, value)

  override var refreshToken: String?
    get() = read(REFRESH)
    set(value) = write(REFRESH, value)

  override var accessExpiresAt: Long
    get() = prefs.getLong(EXPIRES, 0)
    set(value) = prefs.edit().putLong(EXPIRES, value).apply()

  override val signedIn: Boolean get() = accessToken != null

  override fun save(access: String?, refresh: String?, expiresAt: Long) {
    write(ACCESS, access)
    if (refresh != null) write(REFRESH, refresh)
    if (expiresAt > 0) prefs.edit().putLong(EXPIRES, expiresAt).apply()
  }

  override fun clear() {
    write(ACCESS, null)
    write(REFRESH, null)
    prefs.edit().remove(EXPIRES).apply()
  }

  /** بایگانیِ توکنِ هر سرور هنگامِ «سرورِ بعدی» (`ServerSwitchAndroid.applyPending`، همان رفتارِ اندروید) */
  fun stashTo(scope: String) {
    val tag = "@srv:" + scope.ifEmpty { "default" }
    for (k in listOf(ACCESS, REFRESH)) write(k + tag, read(k))
    if (prefs.contains(EXPIRES)) prefs.edit().putLong(EXPIRES + tag, prefs.getLong(EXPIRES, 0)).commit()
    clear()
  }

  fun openFrom(scope: String) {
    val tag = "@srv:" + scope.ifEmpty { "default" }
    clear()
    for (k in listOf(ACCESS, REFRESH)) { write(k, read(k + tag)); write(k + tag, null) }
    if (prefs.contains(EXPIRES + tag)) prefs.edit().putLong(EXPIRES, prefs.getLong(EXPIRES + tag, 0)).remove(EXPIRES + tag).commit()
  }

  private companion object {
    const val ACCESS = "access_token"
    const val REFRESH = "refresh_token"
    const val EXPIRES = "access_expires_at"
  }
}
