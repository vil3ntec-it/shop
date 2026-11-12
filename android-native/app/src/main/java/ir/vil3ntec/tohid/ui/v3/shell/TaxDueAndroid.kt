package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.report.QuarterTax
import ir.vil3ntec.tohid.data.TaxQuarterPref
import ir.vil3ntec.tohid.ui.screens.HomeAlerts

/**
 *  S49/RPT-10 — یادآورِ سررسیدِ مالیاتِ سه‌ماههٔ خانه از تنظیمِ همین دستگاه (`TaxQuarterPref`). تنظیم در هر ترکیب دوباره
 *  خوانده می‌شود (خواندنِ حافظهٔ SharedPreferences، ارزان) تا روشن/خاموش کردن در تنظیمات و برگشت به خانه همان دم دیده شود؛
 *  قاعده خالص در `HomeAlerts.taxDue` (آزمون: `TaxDueAlertTest`).
 */
@Composable
fun rememberTaxDue(canSeeMoney: Boolean, today: String): QuarterTax.Due? {
  val context = LocalContext.current
  val on = TaxQuarterPref.on(context)
  val cfg = TaxQuarterPref.config(context)
  return remember(on, canSeeMoney, today, cfg) { HomeAlerts.taxDue(on, canSeeMoney, today, cfg) }
}
