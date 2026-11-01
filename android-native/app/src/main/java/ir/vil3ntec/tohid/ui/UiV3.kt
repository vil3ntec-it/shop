package ir.vil3ntec.tohid.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 *  پرچمِ «رابطِ نسخهٔ ۳» — **پیش‌فرض خاموش** (تصمیمِ صاحب محصول، ۱۴۰۵/۰۸/۰۸، PR #359).
 *
 *  خاموش ⇒ همان رابطِ امروز، بی هیچ تغییری: پوسته، خانه، فروش، قرض‌داران، تنظیمات، پروفایل،
 *  خوش‌آمد، پیک، VIP و پالتِ سبزآبیِ VILL3N از رونوشت‌های پوشهٔ `ui/legacy` (پسوندِ `Legacy`) (همان کدِ main).
 *  روشن ⇒ پوسته و صفحه‌های نسخهٔ ۳ (`ui/v3`، AppRoot ِ تازه، انتخابگرِ دکان/پیک، تمِ آبی).
 *
 *  تنظیمِ همین دستگاه است (`tohid-ui` › `ui_v3`) و همگام نمی‌شود — مثلِ «گزارشِ تازه». کلیدش در
 *  تنظیمات (هر دو رابط). نسخهٔ پیش‌نمایشِ اندروید (`BuildConfig.PREVIEW`، بستهٔ جدا) برای آزمایشِ
 *  همین رابط ساخته شده و پیش‌فرضش روشن است؛ نسخهٔ پایدار، آزمایشی و کامپیوتر خاموش.
 *  داده، همگام‌سازی و قالبِ ذخیره به این پرچم بسته نیستند — فقط رابط.
 */
object UiV3 {
  private const val PREFS = "tohid-ui"
  private const val KEY = "ui_v3"

  /** عنوانِ بخشِ روشن‌کردنِ همین پرچم در تنظیماتِ رابطِ امروز (گوشی و ستونِ کامپیوتر) */
  const val TOGGLE_TITLE = "رابطِ تازه (نسخهٔ ۳)"

  /** روشن؟ — `mutableStateOf`: با `set` کلِ برنامه بی راه‌اندازیِ دوباره عوض می‌شود */
  var on by mutableStateOf(resolve(stored = null, preview = isPreview()))
    private set

  /**
   *  مقدارِ پرچم: انتخابِ ذخیره‌شدهٔ همین دستگاه، وگرنه پیش‌فرض (خاموش؛ فقط نسخهٔ پیش‌نمایش روشن).
   *  خالص — آزمون: `UiV3Test`.
   */
  fun resolve(stored: Boolean?, preview: Boolean): Boolean = stored ?: preview

  fun load(context: android.content.Context) {
    val p = runCatching { context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }.getOrNull()
    val stored = p?.let { if (it.contains(KEY)) it.getBoolean(KEY, false) else null }
    on = resolve(stored, isPreview())
  }

  fun set(context: android.content.Context, value: Boolean) {
    on = value
    runCatching {
      context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putBoolean(KEY, value).apply()
    }
  }

  /** فقط پیش‌نمایش و عکسِ کامپیوتر (بی ذخیره) */
  fun preview(value: Boolean) { on = value }

  private fun isPreview(): Boolean = runCatching { ir.vil3ntec.tohid.BuildConfig.PREVIEW }.getOrDefault(false)
}
