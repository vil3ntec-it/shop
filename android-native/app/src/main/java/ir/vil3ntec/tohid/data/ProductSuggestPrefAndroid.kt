package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  AI-06 — «پیشنهادِ دسته و نام» در فرمِ کالای نسخهٔ ۳ روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ
 *  `product_suggest`، کنارِ `SimpleAskPref`). ردهٔ A ِ `SettingsTiers`: پیش‌فرض **روشن** و بی ردیف در فهرست، چون فقط
 *  پیشنهاد نشان می‌دهد — هیچ کادری بی زدنِ کاربر پر نمی‌شود و هیچ قیمت، بارکد، موجودی یا دسترسی‌ای عوض نمی‌شود.
 *  **خاموش**: فرمِ کالا همان است که بود (نه تراشهٔ پیشنهاد، نه «افزودن از متن»). همگام نمی‌شود و به شبکه نمی‌رود.
 */
object ProductSuggestPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "product_suggest"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, true) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
