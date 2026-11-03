package ir.vil3ntec.tohid.ui.v3.settings.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.hw.CustomerFacing
import ir.vil3ntec.tohid.print.EasySetupPref
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.ui.screens.CustomerDisplayPanel
import ir.vil3ntec.tohid.ui.screens.HwToolsSwitch
import ir.vil3ntec.tohid.ui.screens.LabelsPanel
import ir.vil3ntec.tohid.ui.screens.PrintQueuePanel
import ir.vil3ntec.tohid.ui.screens.PrinterFinderDialog
import ir.vil3ntec.tohid.ui.screens.PrinterSettingsDialog
import ir.vil3ntec.tohid.ui.screens.ScalePanel
import ir.vil3ntec.tohid.ui.screens.ScannerSetupTools
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  ابزارهای سخت‌افزارِ «دستگاه‌ها» که در صفحهٔ دستگاه‌هایِ رابطِ قدیم بودند و پس از پاک شدنش بی‌راه ماندند
 *  (`V3ReachabilityTest`): کلیدِ «ابزارهای تازهٔ دستگاه» (`hw_tools_v2`)، تنظیمِ هر چاپگر (کشو، رمزگذاری، آزمون)،
 *  پیدا کردنِ خودکارِ چاپگر و ابزارِ بارکدخوان (HW-12، با «راه‌اندازیِ آسان»)، صفِ چاپ، چاپگرِ برچسب، ترازو و
 *  نمایشگرِ مشتری — همان اجزای `ui/screens` با همان دسترسی‌ها (تنظیم فقط صاحب/مدیر، کشو با `drawer.open`).
 */
@Composable
internal fun DevicesHwExtras(data: ShopData) {
  val context = LocalContext.current
  val toasts = LocalV3Toasts.current
  val toast: (String) -> Unit = { toasts?.show(it) }
  LaunchedEffect(Unit) { EasySetupPref.load(context) }
  val policy by ShopRole.policyVersion.collectAsState()
  val canAdmin = remember(policy) { ShopRole.canOpenSettings(context) }
  val canDrawer = remember(policy) { ShopRole.canOpenDrawer(context) }
  val easy = EasySetupPref.on.value
  var tick by remember { mutableIntStateOf(0) }
  val printers = remember(tick) { PrinterRegistry.list(context) }
  var editing by remember { mutableStateOf<PrinterRegistry.Saved?>(null) }
  var finding by remember { mutableStateOf(false) }

  if (canAdmin) {
    DeviceCard(StrReach.printerSettings, null, false, padding = PaddingValues(16.dp)) {
      HwToolsSwitch()
      printers.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(p.name, style = V3Type.callout, color = PhoneV3.colors.ink, modifier = Modifier.weight(1f))
          V3Button(StrReach.printerSettings, { editing = p }, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Tune, height = 36.dp)
        }
      }
      if (easy) {
        V3Button(StrReach.findPrinter, { finding = true }, Modifier.fillMaxWidth().padding(top = 10.dp), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Search)
        Column(Modifier.padding(top = 10.dp)) { ScannerSetupTools(canAdmin, toast) }
      }
    }
  }
  Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    PrintQueuePanel(toast, canAdmin, canDrawer)
    LabelsPanel(data, toast)
    ScalePanel(data, toast)
    if (CustomerFacing.supported) CustomerDisplayPanel()
  }

  editing?.let { p ->
    PrinterSettingsDialog(p, onDismiss = { editing = null }, onSaved = { editing = null; tick++; toast(StrReach.printerSaved) }, toast = toast, canDrawer = canDrawer)
  }
  if (finding) {
    PrinterFinderDialog(onDismiss = { finding = false; tick++ }, onAdded = { tick++; toast(StrReach.printerAdded(it)) }, toast = toast)
  }
}
