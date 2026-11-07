package ir.vil3ntec.tohid.customer

import android.content.Context
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue

/**
 *  نگه‌داریِ روی گوشی: SharedPreferences ِ خصوصیِ همین برنامه (پشتیبانِ ابری خاموش، `allowBackup=false`).
 *  `apply()` ناهم‌گام می‌نویسد تا صفحه نایستد.
 */
class StoreAndroid(context: Context) : ShopperKeyValue {
  private val prefs = context.applicationContext.getSharedPreferences("shopper", Context.MODE_PRIVATE)

  override fun get(key: String): String? = prefs.getString(key, null)

  override fun put(key: String, value: String?) {
    prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
  }
}
