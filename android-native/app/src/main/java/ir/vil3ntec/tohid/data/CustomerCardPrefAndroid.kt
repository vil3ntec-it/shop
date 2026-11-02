package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  CRM-16 — «کارت یا بارکدِ مشتری» روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `customer_card`).
 *  پیش‌فرض **خاموش**: نه دکمهٔ کارت در پروندهٔ مشتری، نه شناختنِ کارت در صندوق — هر اسکن همان راهِ کالا.
 *  کارتی که پیش‌تر داده شده داده است و با خاموش کردن پاک نمی‌شود (`Debtor.cardCode`).
 */
object CustomerCardPref {

  private const val KEY = "customer_card"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
