package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

/**
 *  POS-22 — روشن/خاموشِ «چرا این قیمت؟» روی همین دستگاه (`tohid-ui:price_why`)؛ فایلِ مرزیِ پلتفرم
 *  (`SharedPreferences`). پیش‌فرض **روشن** (تنظیماتِ کم، ۱۴۰۵/۰۸/۱۱)؛ **خاموش**: کادرِ ویرایشِ ردیف همان پیشین است.
 */
object PriceWhyPrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "price_why"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, true)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }
}

/** همین تنظیم برای صفحه‌ها — خواندن این‌جا، تا صفحه `LocalContext` ِ تازه نگیرد */
@Composable
fun priceWhyOn(): Boolean {
  PriceWhyPrefs.load(LocalContext.current)
  return PriceWhyPrefs.on.value
}
