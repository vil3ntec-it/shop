package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.scan.ScanCashPref
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  «فروشِ نقدی با کارتِ فرمان» (راهِ «ب» ِ پرداخت پس از اسکنِ پس‌زمینه) — بخشِ تنظیماتِ رابطِ امروز؛ همان کلید و
 *  نوشتنِ صفحهٔ نسخهٔ ۳ (`settings/devices/scan-cash`). دستهٔ B، پیش‌فرض خاموش، فقط همین دستگاه؛ فقط صاحب یا مدیر؛
 *  روشن کردن تأیید می‌خواهد (پولی است). کارِ اصلی در «دستگاه‌ها» (`CommandCardBody`) و `scan/ScanCashAndroid.kt`.
 */

/** همان کلیدِ `SettingsLayout.SCAN_CASH` */
const val SCAN_CASH_TITLE = "فروشِ نقدی با کارتِ فرمان"

@Composable
fun ScanCashSettingsWired(context: android.content.Context) {
  LaunchedEffect(Unit) { ScanCashPref.load(context) }
  val on by ScanCashPref.on
  val manage = remember { ScanCashPref.canManage(context) }
  var ask by remember { mutableStateOf(false) }
  SettingsSection(
    icon = Icons.Filled.PointOfSale,
    title = SCAN_CASH_TITLE,
    subtitle = if (on) StrShell.switchOn else StrSettings.off,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrDevices.cashCardNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrDevices.cashCardSafety, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    if (!manage) Text(StrDevices.cashCardOwnerOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { pick ->
        if (manage) {
          if (pick == "on" && !on) ask = true
          else if (pick == "off" && on) ScanCashPref.set(context, false)
        }
      },
    )
  }
  if (ask) {
    AlertDialog(
      onDismissRequest = { ask = false },
      title = { Text(StrDevices.cashCardAsk) },
      text = { Text(StrDevices.cashCardAskBody) },
      confirmButton = { TextButton(onClick = { ask = false; ScanCashPref.set(context, true) }) { Text(StrDevices.turnOn) } },
      dismissButton = { TextButton(onClick = { ask = false }) { Text(Str.cancel) } },
    )
  }
}
