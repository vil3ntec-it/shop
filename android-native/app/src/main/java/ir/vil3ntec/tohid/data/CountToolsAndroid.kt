package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  INV-06/07/08 — کلیدهای «انبارگردانیِ تازه» روی **همین دستگاه** (پروندهٔ `tohid-policy`، کنارِ `ShopPolicy`).
 *  `count_tools`: پنجرهٔ تازه با اسکن و برگهٔ چند دستگاه؛ `count_blind`: شمارشِ کور در همان پنجره.
 *  پیش‌فرض هر دو **خاموش**: «انبارگردانی» همان `StockCountSheet` ِ امروز است، بیت‌به‌بیت. همگام نمی‌شوند.
 */
object CountToolsPref {

  private const val PREFS = "tohid-policy"
  private const val TOOLS = "count_tools"
  private const val BLIND = "count_blind"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(TOOLS, false) }.getOrDefault(false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(TOOLS, on).apply()
  }

  fun blind(context: Context): Boolean = runCatching { prefs(context).getBoolean(BLIND, false) }.getOrDefault(false)

  fun setBlind(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(BLIND, on).apply()
  }
}
