package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  PUR-11 — «خرید به ارزِ دیگر» در فاکتورِ خریدِ **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `purchase_fx`، کنارِ
 *  `PriceListPrefs`). پیش‌فرض **خاموش**: فاکتورِ خرید همان فرمِ افغانیِ امروز و دفتر، بها، خروجی و همگام‌سازی بایت‌به‌بایت
 *  همان (`PurchaseFxOffTest`). خواندن و نمایشِ سندِ ارزیِ ثبت‌شده به این تنظیم بسته نیست (داده است؛ `PurchaseFx`).
 */
object PurchaseFxPref {

  private const val KEY = "purchase_fx"

  private fun prefs(context: Context) = context.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
