package ir.vil3ntec.tohid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.text.LocaleFormat

/**
 *  رقم و نامِ ماه — انتخابِ دکان‌دار، روی همین دستگاه (LOC-01، LOC-05).
 *
 *  پیش‌فرض‌ها **رفتارِ امروز** است: رقمِ لاتین و نامِ ماهِ دری. تا دکان‌دار
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

  /** `latn` یا `fa` (`LocaleFormat.DIGITS_*`) */
  var digits by mutableStateOf(LocaleFormat.DIGITS_LATN)
    private set

  /** `fa` (دری) یا `ps` (پشتو) */
  var months by mutableStateOf(LocaleText.MONTHS_DARI)
    private set

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context) {
    val p = runCatching { prefs(context) }.getOrNull() ?: return
    digits = LocaleText.digitsOf(p.getString(DIGITS, null))
    months = LocaleText.monthsOf(p.getString(MONTHS, null))
  }

  fun setDigits(context: Context, value: String) {
    digits = LocaleText.digitsOf(value)
    prefs(context).edit().putString(DIGITS, digits).apply()
  }

  fun setMonths(context: Context, value: String) {
    months = LocaleText.monthsOf(value)
    prefs(context).edit().putString(MONTHS, months).apply()
  }
}
