package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.safe.SafeCurrency

/**
 *  تنظیم‌های ارزِ گاوصندوق در `tohid-policy` (`ShopSettingsKeys`):
 *  • `safe_currencies` — ارزهایی که دکان افزود (JSON ِ `SafeCurrencies.encode`)، **مالِ دکان**: با همان همگام‌سازیِ
 *    تنظیماتِ دکان به همهٔ دستگاه‌ها می‌رود (فقط صاحب/مدیر می‌فرستد)؛
 *  • `safe_last_currency` — آخرین ارزی که در کادرِ ورود/خروجِ **همین دستگاه** به کار رفت (پیش‌فرضِ دفعهٔ بعد)؛
 *  • `safe_view_currency` — ارزی که نمودار و ماندهٔ گاوصندوقِ **همین دستگاه** آخرین بار نشان داد.
 *  نبودنِ هر کدام ⇒ افغانی و همان صفحهٔ امروز.
 */
object SafeCurrencyPref {

  private const val LIST = "safe_currencies"
  private const val LAST = "safe_last_currency"
  private const val VIEW = "safe_view_currency"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(ShopSettingsKeys.POLICY, Context.MODE_PRIVATE)

  private fun read(context: Context, key: String): String? = runCatching { prefs(context).getString(key, null) }.getOrNull()

  private fun write(context: Context, key: String, value: String) {
    runCatching { prefs(context).edit().putString(key, value).apply() }
  }

  fun custom(context: Context): List<SafeCurrencies.Item> = SafeCurrencies.decode(read(context, LIST))

  fun saveCustom(context: Context, list: List<SafeCurrencies.Item>) = write(context, LIST, SafeCurrencies.encode(list))

  fun last(context: Context): String = read(context, LAST)?.takeIf(SafeCurrency::isCode) ?: SafeCurrency.AFN

  fun setLast(context: Context, code: String) {
    if (SafeCurrency.isCode(code)) write(context, LAST, code)
  }

  fun viewed(context: Context): String = read(context, VIEW)?.takeIf(SafeCurrency::isCode) ?: SafeCurrency.AFN

  fun setViewed(context: Context, code: String) {
    if (SafeCurrency.isCode(code)) write(context, VIEW, code)
  }
}
