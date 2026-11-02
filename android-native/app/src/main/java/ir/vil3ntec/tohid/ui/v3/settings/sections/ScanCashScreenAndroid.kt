package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.scan.ScanCashPref
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/devices/scan-cash`: «فروشِ نقدی با کارتِ فرمان» — همان کلید و نوشتنِ رابطِ امروز (`ScanCashSettingsWired`،
 *  `SettingsPortTest`). دستهٔ B، پیش‌فرض خاموش، فقط همین دستگاه؛ فقط صاحب یا مدیر؛ روشن کردن تأیید می‌خواهد.
 */
@Composable
internal fun RouteScope.ScanCashScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val state = remember { ScanCashPref.load(context); ScanCashPref.on }
  val on by state
  val manage = remember { ScanCashPref.canManage(context) }
  var ask by remember { mutableStateOf(false) }
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.SCAN_CASH), subtitle = if (on) StrShell.switchOn else StrSettings.off, onBack = onBack) {
    item(key = "choice") {
      SettingsBlock {
        V3Segmented(SWITCH, if (on) 1 else 0, { pick ->
          if (manage) {
            if (pick == 1 && !on) ask = true
            else if (pick == 0 && on) ScanCashPref.set(context, false)
          }
        })
      }
      SettingsNote(StrDevices.cashCardNote)
      SettingsNote(StrDevices.cashCardSafety)
      if (!manage) SettingsNote(StrDevices.cashCardOwnerOnly)
      SettingsNote(StrShell.thisDeviceOnly)
    }
  }
  if (ask) {
    V3ConfirmSheet(
      title = StrDevices.cashCardAsk,
      message = StrDevices.cashCardAskBody,
      confirm = StrDevices.turnOn,
      danger = false,
      onDismiss = { ask = false },
      onConfirm = { ask = false; ScanCashPref.set(context, true) },
    )
  }
}
