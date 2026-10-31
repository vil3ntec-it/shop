package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  RPT-13 — کلیدهای دوره و مقایسه در «گزارش‌ها»ی کامپیوتر، روی **همین دستگاه** (`tohid-policy:report_keys`،
 *  **پیش‌فرض خاموش**): خاموش ⇒ −، + و Ctrl+Shift+C در گزارش‌ها هیچ نمی‌کنند و صفحه همان است که بود.
 */
object ReportKeysPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "report_keys"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
