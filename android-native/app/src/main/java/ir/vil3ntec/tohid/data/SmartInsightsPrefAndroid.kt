package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  AI-07 — پیش‌بینیِ تقاضای قابلِ توضیح در «پیشنهادِ سفارش» (`PurchaseSuggest.rows(…, smart)`)، روی **همین دستگاه**
 *  (پروندهٔ `tohid-policy`، کلیدِ `ai_forecast`). پیش‌فرض **خاموش** ⇒ همان پیشنهادِ پیشین، عدد به عدد.
 */
object SmartForecastPref {

  private const val KEY = "ai_forecast"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}

/**
 *  AI-08 — تشخیصِ ناهنجاریِ صندوق و موجودی در زبانهٔ «ضدتقلب» ِ گزارشِ تازه (`AnomalyBook.view`)، روی **همین دستگاه**
 *  (پروندهٔ `tohid-policy`، کلیدِ `ai_anomaly`). پیش‌فرض **خاموش** ⇒ همان کادرِ هشدارِ RSK-10.
 */
object AnomalyPref {

  private const val KEY = "ai_anomaly"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
