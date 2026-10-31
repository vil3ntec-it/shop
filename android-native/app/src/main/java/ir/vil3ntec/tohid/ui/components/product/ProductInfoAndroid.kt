package ir.vil3ntec.tohid.ui.components.product

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

/**
 *  PRD-02/PRD-26 — روشن/خاموشِ «نام‌های دیگر و محلِ قفسه» روی همین دستگاه (`tohid-ui:product_info`)؛
 *  فایلِ مرزیِ پلتفرم (`SharedPreferences`). پیش‌فرض **خاموش**: فرمِ کالا، جست‌وجو و اقلامِ سفارش
 *  همان پیشین‌اند و هیچ فیلدِ تازه‌ای نوشته نمی‌شود.
 */
object ProductInfoPrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "product_info"

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

/** همین تنظیم برای صفحه‌ها — خواندن این‌جا، تا صفحه `LocalContext` ِ تازه نگیرد */
@Composable
fun productInfoOn(): Boolean {
  ProductInfoPrefs.load(LocalContext.current)
  return ProductInfoPrefs.on.value
}

/** بخشِ تنظیمات با خواندن و نوشتنِ همین تنظیم — مرزِ پلتفرم، تا `Settings` یک خط بخواهد */
@Composable
fun ProductInfoSettingsWired(context: android.content.Context) {
  ProductInfoPrefs.load(context)
  ProductInfoSettings(ProductInfoPrefs.on.value) { ProductInfoPrefs.set(context, it) }
}
