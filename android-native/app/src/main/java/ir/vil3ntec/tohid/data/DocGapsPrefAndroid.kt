package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  RPT-17 — زبانهٔ «شماره‌های جاافتاده» در «گزارش‌ها»ی کامپیوتر، روی **همین دستگاه**، کنارِ
 *  سیاست‌های دیگرِ دکان (`ShopPolicy`، پروندهٔ `tohid-policy`، کلیدِ `doc_gaps`). پیش‌فرض **خاموش**:
 *  تا دکان‌دار روشن نکند، صفحهٔ گزارش‌ها همان است که بود. خودِ گزارش فقط خواندنی است.
 */
object DocGapsPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "doc_gaps"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
