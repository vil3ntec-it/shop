package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.desktop.SecretBox

/** کامپیوتر: کلیدِ جفت‌شدن با هاب با همان `SecretBox` ِ توکن‌ها (`TokenStoreDesk`) — جایگزینِ `HubKeyStore.kt` ِ اندروید */
object HubKeyStore {
  private const val FILE = "tohid-hub-secure"
  private const val KEY = "hub.key"

  private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

  fun get(context: Context): String? = prefs(context).getString(KEY, null)?.let { SecretBox.open(it) }?.takeIf { it.isNotBlank() }

  fun put(context: Context, key: String?) {
    val e = prefs(context).edit()
    if (key.isNullOrBlank()) e.remove(KEY) else e.putString(KEY, SecretBox.seal(key))
    e.commit()
  }
}
