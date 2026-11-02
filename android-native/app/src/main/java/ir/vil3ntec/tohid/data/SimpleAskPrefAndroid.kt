package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  RPT-16 — کلیدِ زبانهٔ «پرسشِ ساده» در «گزارش‌ها» روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ
 *  `simple_ask`، کنارِ `ShrinkReportPref`). پیش‌فرض **روشن** (تنظیماتِ کم، ۱۴۰۵/۰۸/۱۱)؛ **خاموش**: «گزارش‌ها»ی گوشی و کامپیوتر همان است که بود.
 *  خودِ پرسش فقط خواندنی است (`SimpleAskEngine`)؛ این کلید هیچ داده‌ای نمی‌نویسد، همگام نمی‌شود و به شبکه نمی‌رود.
 */
object SimpleAskPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "simple_ask"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, true) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
