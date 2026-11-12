package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSettingsPages
import ir.vil3ntec.tohid.data.AnomalyPref
import ir.vil3ntec.tohid.data.SmartForecastPref

/*
 *  AI-07 و AI-08 — دو بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) و خواندنِ همان دو تنظیم برای صفحه‌ها؛ مرزِ پلتفرم
 *  همین پرونده است، تا «پیشنهادِ سفارش» و «گزارشِ تازه» یک خط بخواهند.
 */

/** توضیحِ بخش‌ها — یکی برای رابطِ امروز و صفحهٔ نسخهٔ ۳ (`settings/sales/forecast`، `settings/security/anomalies`) */
val SMART_FORECAST_NOTE: String get() = StrSettingsPages.smartForecastNote
val ANOMALY_NOTE: String get() = StrSettingsPages.anomalyNote

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
