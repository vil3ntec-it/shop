package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.AnomalyPref
import ir.vil3ntec.tohid.data.SmartForecastPref

/*
 *  AI-07 و AI-08 — دو بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) و خواندنِ همان دو تنظیم برای صفحه‌ها؛ مرزِ پلتفرم
 *  همین پرونده است، تا «پیشنهادِ سفارش» و «گزارشِ تازه» یک خط بخواهند.
 */

const val SMART_FORECAST_TITLE = "پیش‌بینیِ تقاضا (آزمایشی)"
const val ANOMALY_TITLE = "تشخیصِ ناهنجاری (آزمایشی)"

/** توضیحِ بخش‌ها — یکی برای رابطِ امروز و صفحهٔ نسخهٔ ۳ (`settings/sales/forecast`، `settings/security/anomalies`) */
const val SMART_FORECAST_NOTE =
  "در «پیشنهادِ سفارش»: کالای پرفروش با میانگینِ متحرکِ ۲۸ روز و روزِ هفته، کالای کم‌فروش با روشِ Croston؛ " +
  "پیش‌بینیِ هر روزِ آینده، دلیل از همان عددها و اطمینان از درازای داده و نوسان. بی اینترنت، روی دفترِ همین دستگاه."
const val ANOMALY_NOTE =
  "در زبانهٔ «ضدتقلب» ِ گزارشِ تازه: لغو و تخفیف و کشوی هر صندوقدار، اختلافِ صندوق، کمِ موجودی با «اصلاح»، فروش زیرِ بها، " +
  "فاکتورِ نیمه‌شب و روزهای بیرون از معمولِ برگشتی و تخفیف. هر مورد فقط «نیازمندِ بررسی» است و چیزی را نمی‌بندد."

/** «پیشنهادِ سفارش» با پیش‌بینیِ قابلِ توضیح؟ (AI-07) */
@Composable
fun rememberSmartForecastOn(): Boolean {
  val context = LocalContext.current
  return remember { SmartForecastPref.on(context) }
}

/** زبانهٔ «ضدتقلب» با تشخیصِ ناهنجاری؟ (AI-08) */
@Composable
fun rememberAnomalyOn(): Boolean {
  val context = LocalContext.current
  return remember { AnomalyPref.on(context) }
}
