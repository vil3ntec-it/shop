package ir.vil3ntec.tohid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.text.LocaleFormat

/**
 *  رقم، نامِ ماه و نمادِ پول — انتخابِ دکان‌دار، روی همین دستگاه (LOC-01، LOC-05، LOC-07).
 *
 *  پیش‌فرض‌ها **رفتارِ امروز** است: رقمِ لاتین، نامِ ماهِ دری و «افغانی». تا دکان‌دار
 *  خودش در تنظیمات عوض نکند، هیچ چیزی عوض نمی‌شود.
 *
 *  مثلِ `Motion`، حالت `mutableStateOf` است تا با زدنِ کلید، کلِ برنامه
 *  بی بازکردنِ دوباره عوض شود. `load` در `MainActivity` و `Main.kt`ِ
 *  کامپیوتر، کنارِ `Motion.load`، صدا زده می‌شود.
 */
object LocaleChoice {

  private const val PREFS = "tohid-locale"
  private const val DIGITS = "digits"
  private const val MONTHS = "months"
  private const val SYMBOL = "symbol"

  /** `latn` یا `fa` (`LocaleFormat.DIGITS_*`) */
  var digits by mutableStateOf(LocaleFormat.DIGITS_LATN)
    private set

  /** `fa` (دری) یا `ps` (پشتو) */
  var months by mutableStateOf(LocaleText.MONTHS_DARI)
    private set

  /** نمادِ پول در فاکتور: `word` (افغانی) یا `sign` (؋) — `LocaleFormat.SYMBOL_*` */
  var symbol by mutableStateOf(LocaleFormat.SYMBOL_WORD)
    private set

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context) {
    val p = runCatching { prefs(context) }.getOrNull() ?: return
    digits = LocaleText.digitsOf(p.getString(DIGITS, null))
    months = LocaleText.monthsOf(p.getString(MONTHS, null))
    symbol = LocaleText.symbolOf(p.getString(SYMBOL, null))
  }

  fun setDigits(context: Context, value: String) {
    digits = LocaleText.digitsOf(value)
    prefs(context).edit().putString(DIGITS, digits).apply()
  }

  fun setMonths(context: Context, value: String) {
    months = LocaleText.monthsOf(value)
    prefs(context).edit().putString(MONTHS, months).apply()
  }

  fun setSymbol(context: Context, value: String) {
    symbol = LocaleText.symbolOf(value)
    prefs(context).edit().putString(SYMBOL, symbol).apply()
  }
}
