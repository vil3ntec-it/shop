package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.pay.TillRounding

/**
 *  PAY-09 — پلهٔ گردکردنِ نقدیِ صندوق روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `cash_rounding`، به پول:
 *  ۰، ۵۰۰ یا ۱۰۰۰). پیش‌فرض **خاموش** (D-11): هیچ ردیفِ گردی ساخته نمی‌شود و صندوق همان امروز. مقدارِ خراب یا
 *  ناشناخته ⇒ خاموش (`TillRounding.step`). حساب فقط در `core/pay/TillRounding` و `Pay.settle`.
 */
object CashRoundPref {
  private const val KEY = "cash_rounding"
  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun step(context: Context): Long = runCatching { TillRounding.step(prefs(context).getLong(KEY, 0L)) }.getOrDefault(0L)

  fun set(context: Context, step: Long) {
    prefs(context).edit().putLong(KEY, TillRounding.step(step)).apply()
  }
}
