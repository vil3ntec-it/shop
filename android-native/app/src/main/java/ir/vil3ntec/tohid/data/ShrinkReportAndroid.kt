package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  INV-17 — کلیدِ «گزارشِ ضایعات و کسری» روی **همین دستگاه**، کنارِ سیاست‌های دیگرِ دکان (`ShopPolicy`،
 *  پروندهٔ `tohid-policy`). پیش‌فرض **خاموش**: «گزارش‌ها»ی گوشی و کامپیوتر همان است که بود، بیت‌به‌بیت.
 *  خودِ گزارش فقط خواندنی است (`ShrinkLoss`)؛ این کلید هیچ داده‌ای نمی‌نویسد و همگام نمی‌شود.
 */
object ShrinkReportPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "shrink_report"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
