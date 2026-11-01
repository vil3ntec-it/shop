package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.mutableStateOf

/**
 *  POS-15 — قیمت‌پرسی با Ctrl+I در صندوقِ کامپیوتر، روی همین دستگاه (`tohid-ui:pos_price_check`)؛ فایلِ
 *  مرزیِ پلتفرم. پیش‌فرض **خاموش**: Ctrl+I همان امروز هیچ کاری نمی‌کند.
 */
object PosPriceCheckPrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "pos_price_check"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, false)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }
}

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — مرزِ پلتفرم */
@androidx.compose.runtime.Composable
fun PosPriceCheckSettingsWired(context: android.content.Context) {
  PosPriceCheckPrefs.load(context)
  PosPriceCheckSettings(PosPriceCheckPrefs.on.value) { PosPriceCheckPrefs.set(context, it) }
}
