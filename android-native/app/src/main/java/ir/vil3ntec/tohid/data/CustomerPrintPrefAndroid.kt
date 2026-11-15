package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  CRM-15 — «چاپِ شمارهٔ کامل» (تنظیمِ **دکان** `tohid-policy:print_full_phone`، به همهٔ دستگاه‌ها می‌رود — `ShopSettingsKeys`)،
 *  پیش‌فرض **خاموش**؛ فقط صاحب در تنظیمات می‌بیند (`SettingsLayout.OWNER_ONLY`). با پرچمِ نقش‌محورِ `shop.customer_role_mask`
 *  روشن، رسید و صورت‌حسابِ چاپی برای هر نقش شمارهٔ مشتری را «•••• ۰۰۸» می‌زنند مگر این روشن باشد
 *  (`core/crm/CustomerMask.printPhone`). پرچمِ خاموش ⇒ چاپ همان امروز (این تنظیم بی‌اثر).
 */
object CustomerPrintPref {

  const val KEY = "print_full_phone"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(ShopSettingsKeys.POLICY, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  /** پرچمِ نقش‌محورِ همین دکان از `policy` ِ ذخیره‌شده (`ShopRole.customerMask`، تازه با همین پرسش) */
  fun roleMask(context: Context): Boolean {
    ShopRole.allows(context, ir.vil3ntec.tohid.core.crm.CustomerMask.KEY)
    return ShopRole.customerMask.value
  }
}
