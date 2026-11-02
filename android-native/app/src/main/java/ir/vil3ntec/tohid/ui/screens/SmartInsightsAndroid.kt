package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.AnomalyPref
import ir.vil3ntec.tohid.data.SmartForecastPref
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  AI-07 و AI-08 — دو بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) و خواندنِ همان دو تنظیم برای صفحه‌ها؛ مرزِ پلتفرم
 *  همین پرونده است، تا «پیشنهادِ سفارش» و «گزارشِ تازه» یک خط بخواهند. عنوان‌ها در فهرستِ `DeskSettingsLegacy` هم هستند.
 */

const val SMART_FORECAST_TITLE = "پیش‌بینیِ تقاضا (آزمایشی)"
const val ANOMALY_TITLE = "تشخیصِ ناهنجاری (آزمایشی)"

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

@Composable
fun SmartForecastSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(SmartForecastPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.TrendingUp,
    title = SMART_FORECAST_TITLE,
    subtitle = if (on) StrShell.switchOn else StrSettings.off,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(
      "در «پیشنهادِ سفارش»: کالای پرفروش با میانگینِ متحرکِ ۲۸ روز و روزِ هفته، کالای کم‌فروش با روشِ Croston؛ " +
        "پیش‌بینیِ هر روزِ آینده، دلیل از همان عددها و اطمینان از درازای داده و نوسان. بی اینترنت، روی دفترِ همین دستگاه.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; SmartForecastPref.set(context, on) },
    )
  }
}

@Composable
fun AnomalySettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(AnomalyPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.Warning,
    title = ANOMALY_TITLE,
    subtitle = if (on) StrShell.switchOn else StrSettings.off,
    tint = if (on) Shop.colors.warning else Shop.colors.muted,
  ) {
    Text(
      "در زبانهٔ «ضدتقلب» ِ گزارشِ تازه: لغو و تخفیف و کشوی هر صندوقدار، اختلافِ صندوق، کمِ موجودی با «اصلاح»، فروش زیرِ بها، " +
        "فاکتورِ نیمه‌شب و روزهای بیرون از معمولِ برگشتی و تخفیف. هر مورد فقط «نیازمندِ بررسی» است و چیزی را نمی‌بندد.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; AnomalyPref.set(context, on) },
    )
  }
}
