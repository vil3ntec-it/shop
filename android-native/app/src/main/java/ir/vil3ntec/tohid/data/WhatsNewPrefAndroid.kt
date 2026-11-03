package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  OPS-05 — «تازه‌ها» روی **همین دستگاه**: کلیدِ نمایش (`tohid-policy:whats_new`)، کلیدهای سطرهای دیده‌شده
 *  (`tohid-whats-new:keys`) و «آخرین نسخهٔ پایهٔ دیده‌شده» ِ سامانهٔ پیشین (`tohid-whats-new:seen`، فقط خواندن برای
 *  گذر). کلیدِ نمایش ردهٔ A است (`SettingsLayout.WHATS_NEW`: **روشن و پنهان**؛ نشانیِ مستقیمش صفحهٔ کلید را باز
 *  می‌کند)؛ فقط کسی که خودش خاموشش کرده خاموش می‌ماند ⇒ هیچ چیز خوانده یا نوشته نمی‌شود. منطق در `core/guide/WhatsNew`.
 */
object WhatsNewPref {

  private const val POLICY = "tohid-policy"
  private const val KEY_ON = "whats_new"
  private const val STATE = "tohid-whats-new"
  private const val KEY_SEEN = "seen"
  private const val KEY_KEYS = "keys"

  private fun prefs(context: Context, name: String) = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context, POLICY).getBoolean(KEY_ON, true) }.getOrDefault(DEFAULT_ON)

  /** پیش‌فرضِ نمایش — روشن (ردهٔ A) */
  const val DEFAULT_ON = true

  fun setOn(context: Context, on: Boolean) {
    prefs(context, POLICY).edit().putBoolean(KEY_ON, on).apply()
  }

  /** «آخرین نسخهٔ پایهٔ دیده‌شده» ِ سامانهٔ پیشین (پیش از `docs/WHATS-NEW-fa.md`)؛ دیگر نوشته نمی‌شود */
  fun legacySeen(context: Context): String? = runCatching { prefs(context, STATE).getString(KEY_SEEN, null) }.getOrNull()

  /** کلیدهای سطرهای دیده‌شده (`WhatsNew.encode`)؛ `null` ⇒ این سامانه روی این دستگاه هنوز هیچ ننوشته */
  fun keys(context: Context): String? = runCatching { prefs(context, STATE).getString(KEY_KEYS, null) }.getOrNull()

  fun setKeys(context: Context, keys: String) {
    prefs(context, STATE).edit().putString(KEY_KEYS, keys).apply()
  }
}
