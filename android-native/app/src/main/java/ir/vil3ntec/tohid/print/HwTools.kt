package ir.vil3ntec.tohid.print

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 *  «ابزارهای تازهٔ دستگاه» — تنظیمِ همین دستگاه، **پیش‌فرض خاموش**، همگام نمی‌شود (مثلِ «گزارشِ تازه»).
 *
 *  روشن ⇒ در «دستگاه‌ها»: بررسیِ کاملِ وضعیتِ چاپگر با `DLE EOT 1/2/3/4` (درِ باز، آفلاین، تیغه،
 *  کاغذ — HW-02/HW-11) و «کدام روش؟» با برگهٔ دوروشی (HW-01). خاموش ⇒ همان رفتارِ امروز:
 *  بررسیِ اتصال فقط کاغذ را می‌پرسد و دکمهٔ تازه‌ای نیست. منطق در `core/hw` و آزمون‌دار است؛
 *  هیچ‌کدام روی چاپگرِ واقعی آزموده نشده.
 */
object HwTools {
  private const val PREFS = "tohid-ui"
  private const val KEY = "hw_tools_v2"

  val on = mutableStateOf(false)
  private var loaded = false

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    on.value = runCatching { context.getSharedPreferences(PREFS, 0).getBoolean(KEY, false) }.getOrDefault(false)
  }

  fun set(context: Context, value: Boolean) {
    on.value = value
    runCatching { context.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY, value).apply() }
  }
}
