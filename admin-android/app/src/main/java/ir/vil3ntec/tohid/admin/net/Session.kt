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
 *  سالمی ندارند — به حالتِ ساده برمی‌گردیم، چون برنامه‌ای که اصلاً بالا
 *  نیاید بدتر از برنامه‌ای است که توکنش رمز نشده باشد. آن حالت هم فقط
 *  برای خودِ همین برنامه خواندنی است.
 */
class Session(context: Context) {

  private val prefs: SharedPreferences = runCatching {
    val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    EncryptedSharedPreferences.create(
      "tohid-admin-secure",
      alias,
      context,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    ) as SharedPreferences
  }.getOrElse {
    context.getSharedPreferences("tohid-admin", Context.MODE_PRIVATE)
  }

  var serverUrl: String
    get() = prefs.getString(SERVER, "") ?: ""
    set(v) = prefs.edit().putString(SERVER, v.trim().trimEnd('/')).apply()

  var token: String?
    get() = prefs.getString(TOKEN, null)
    set(v) = prefs.edit().putString(TOKEN, v).apply()

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
