package ir.vil3ntec.tohid.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 *  رابطِ نسخهٔ ۳ — **همیشه روشن** (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۱۱: «شکلِ قدیم هرگز نیاید»؛ پیش‌تر پیش‌فرض خاموش، PR #359).
 *  کلیدش از همهٔ تنظیمات برداشته شد؛ `preview` فقط برای عکسِ کامپیوتر می‌ماند. پاک‌کردنِ `ui/legacy` گامِ بعد است.
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
  var on by mutableStateOf(true)
    private set

  /**
   *  مقدارِ پرچم: انتخابِ ذخیره‌شدهٔ همین دستگاه، وگرنه پیش‌فرض (خاموش؛ فقط نسخهٔ پیش‌نمایش روشن).
   *  خالص — آزمون: `UiV3Test`.
   */
  @Suppress("UNUSED_PARAMETER")
  fun resolve(stored: Boolean?, preview: Boolean): Boolean = true

  /** دیگر انتخابی نیست: همیشه نسخهٔ ۳ (مقدارِ کهنهٔ `ui_v3` نادیده می‌ماند) */
  @Suppress("UNUSED_PARAMETER")
  fun load(context: android.content.Context) { on = true }

  /** فقط پیش‌نمایش و عکسِ کامپیوتر (بی ذخیره) */
  fun preview(value: Boolean) { on = value }

  private fun isPreview(): Boolean = runCatching { ir.vil3ntec.tohid.BuildConfig.PREVIEW }.getOrDefault(false)
}
