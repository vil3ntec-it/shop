package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.mutableStateOf

/**
 *  POS-04 — روشن/خاموشِ جست‌وجوی مقاومِ صندوقِ کامپیوتر روی همین دستگاه (`tohid-ui:pos_smart_search`)؛
 *  فایلِ مرزیِ پلتفرم (`SharedPreferences`). پیش‌فرض **خاموش**: همان `PosCatalog.matches` ِ پیشین.
 */
object PosSearchPrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "pos_smart_search"

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

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — مرزِ پلتفرم، تا `Settings` فقط یک خط بخواهد */
@androidx.compose.runtime.Composable
fun PosSearchSettingsWired(context: android.content.Context) {
  PosSearchPrefs.load(context)
  PosSearchSettings(PosSearchPrefs.on.value) { PosSearchPrefs.set(context, it) }
  //  POS-15 — قیمت‌پرسیِ صندوقِ کامپیوتر کنارِ همین بخش (هر دو فقط کامپیوتر، پیش‌فرض خاموش)
  PosPriceCheckSettingsWired(context)
}
