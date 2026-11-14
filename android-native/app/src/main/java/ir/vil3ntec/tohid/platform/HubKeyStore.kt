package ir.vil3ntec.tohid.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 *  S29/SYN-09 — کلیدِ جفت‌شدنِ این دستگاه با هابِ کامپیوتر: همان روشِ `TokenStore` (Keystore ِ گوشی؛ Keystore ِ
 *  خراب ⇒ فایلِ ساده، چون برنامه‌ای که بالا نمی‌آید بدتر است). کامپیوتر: `HubKeyStoreDesk.kt` (`SecretBox`).
 *  کلید هرگز log نمی‌شود.
 */
object HubKeyStore {
  private const val SECURE_FILE = "tohid-hub-secure"
  private const val KEY = "hub.key"

  private fun prefs(c: Context): SharedPreferences = runCatching {
    EncryptedSharedPreferences.create(
      SECURE_FILE, MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), c.applicationContext,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    ) as SharedPreferences
  }.getOrElse { c.applicationContext.getSharedPreferences("$SECURE_FILE-plain", Context.MODE_PRIVATE) }

  fun get(context: Context): String? = prefs(context).getString(KEY, null)?.takeIf { it.isNotBlank() }

  fun put(context: Context, key: String?) {
    val e = prefs(context).edit()
    if (key.isNullOrBlank()) e.remove(KEY) else e.putString(KEY, key)
    e.commit()
  }
}
