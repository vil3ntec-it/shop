package ir.vil3ntec.tohid.admin.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 *  آنچه بینِ اجراها می‌ماند: نشانیِ سرور و توکنِ مدیر.
 *
 *  توکنِ مدیر یعنی اجازهٔ عوض کردنِ اشتراکِ هر کسی و بستنِ هر حسابی. پس
 *  رمزنشده روی گوشی نمی‌نشیند: با کلیدی در Keystore خودِ گوشی رمز می‌شود.
 *
 *  اگر رمزگذاری روی گوشیِ خاصی نگیرد — بعضی گوشی‌های قدیمی Keystore
 *  سالمی ندارند — برنامه بالا می‌آید ولی توکن **هرگز** رمزنشده روی دیسک
 *  نمی‌رود: فقط تا بسته شدنِ برنامه در حافظه می‌ماند (هر بار ورودِ دوباره).
 *  نشانیِ سرور و نام، که راز نیستند، در حالتِ ساده می‌مانند.
 */
class Session(context: Context) {

  private val encrypted: SharedPreferences? = runCatching {
    val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    EncryptedSharedPreferences.create(
      "tohid-admin-secure",
      alias,
      context,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    ) as SharedPreferences
  }.getOrNull()

  private val prefs: SharedPreferences =
    encrypted ?: context.getSharedPreferences("tohid-admin", Context.MODE_PRIVATE)

  /** فقط وقتی رمزگذاری نگرفت — توکن در حافظه، نه روی دیسک */
  private var memoryToken: String? = null

  init {
    //  توکنی که نسخه‌های پیشین رمزنشده نوشته‌اند پاک شود
    if (encrypted == null) prefs.edit().remove(TOKEN).apply()
  }

  var serverUrl: String
    get() = prefs.getString(SERVER, "") ?: ""
    set(v) = prefs.edit().putString(SERVER, v.trim().trimEnd('/')).apply()

  var token: String?
    get() = if (encrypted != null) encrypted.getString(TOKEN, null) else memoryToken
    set(v) { if (encrypted != null) encrypted.edit().putString(TOKEN, v).apply() else memoryToken = v }

  var adminName: String
    get() = prefs.getString(NAME, "") ?: ""
    set(v) = prefs.edit().putString(NAME, v).apply()

  var role: String
    get() = prefs.getString(ROLE, "") ?: ""
    set(v) = prefs.edit().putString(ROLE, v).apply()

  /** توکنِ مدیر مهلت دارد؛ بعد از آن سرور خودش ردش می‌کند */
  var expiresAt: Long
    get() = prefs.getLong(EXPIRES, 0)
    set(v) = prefs.edit().putLong(EXPIRES, v).apply()

  val signedIn: Boolean get() = !token.isNullOrBlank() && serverUrl.isNotBlank()

  fun signOut() {
    memoryToken = null
    prefs.edit().remove(TOKEN).remove(NAME).remove(ROLE).remove(EXPIRES).apply()
  }

  private companion object {
    const val SERVER = "server_url"
    const val TOKEN = "admin_token"
    const val NAME = "admin_name"
    const val ROLE = "admin_role"
    const val EXPIRES = "expires_at"
  }
}
