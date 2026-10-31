package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.sale.CheckoutRules

/**
 *  POS-24 — سمتِ پلتفرمِ `core/sale/CheckoutRules`: قاعده‌های پیش از پایانِ فاکتور روی **همین دستگاه**،
 *  کنارِ سیاست‌های دیگرِ دکان (`ShopPolicy`، پروندهٔ `tohid-policy`). پیش‌فرض **خاموش** (`CheckoutRules.OFF`)
 *  = همان رفتارِ امروز؛ «نسیه بی مشتری» همیشه رد است و این‌جا خاموش‌شدنی نیست.
 */
object CheckoutRulesPref {

  private const val PREFS = "tohid-policy"
  /** آستانهٔ «مشتری لازم است» به پول؛ نبودِ کلید یا منفی ⇒ خاموش */
  private const val CUSTOMER_FROM = "checkout_customer_from_minor"
  private const val BLOCK_ZERO = "checkout_block_zero_total"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun get(context: Context): CheckoutRules = runCatching {
    val p = prefs(context)
    CheckoutRules.of(if (p.contains(CUSTOMER_FROM)) p.getLong(CUSTOMER_FROM, -1L) else null, p.getBoolean(BLOCK_ZERO, false))
  }.getOrDefault(CheckoutRules.OFF)

  fun set(context: Context, rules: CheckoutRules) {
    val e = prefs(context).edit()
    val from = rules.customerFromMinor
    if (from == null || from < 0) e.remove(CUSTOMER_FROM) else e.putLong(CUSTOMER_FROM, from)
    e.putBoolean(BLOCK_ZERO, rules.blockZeroTotal).apply()
  }
}
