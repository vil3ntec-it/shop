package ir.vil3ntec.tohid.ui.v3.settings.devices

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.settings.devices.DevicesModel.Kind

/** نامِ کارِ اجازهٔ USB — فقط داخلِ همین برنامه معنا دارد (همان برگهٔ چاپِ فروش) */
private const val USB_PERMISSION_ACTION = "ir.vil3ntec.tohid.USB_PERMISSION"

private val WIDTHS = listOf(ThermalPrinter.WIDTH_58MM, ThermalPrinter.WIDTH_80MM)

/**
 *  «افزودنِ چاپگر»: نام (اختیاری)، عرضِ کاغذ و راهِ وصل — بلوتوث (فقط جفت‌شده‌ها،
 *  با اجازهٔ بلوتوث)، وای‌فای (IP و درگاه) یا سیم/چاپگرِ سیستم. هیچ فهرستِ ساختگی:
 *  چیزی نبود، همین گفته می‌شود. ثبت با `PrinterRegistry.add`.
 */
@Composable
internal fun AddPrinterSheet(onDismiss: () -> Unit, onAdd: (name: String, link: String, width: Int) -> Unit) {
  val context = LocalContext.current
  var name by remember { mutableStateOf("") }
  var width by remember { mutableIntStateOf(ThermalPrinter.WIDTH_58MM) }
  var kind by remember { mutableStateOf(Kind.BLUETOOTH) }
  var granted by remember {
    mutableStateOf(android.os.Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
  var paired by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  var usbList by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  var bt by remember { mutableStateOf<String?>(null) }
  var usb by remember { mutableStateOf<String?>(null) }
  var host by remember { mutableStateOf("") }
  var port by remember { mutableStateOf(ThermalPrinter.RAW_PORT.toString()) }
  LaunchedEffect(granted, kind) { if (granted && kind == Kind.BLUETOOTH) paired = ThermalPrinter.paired(context) }
  //  هر بار دوباره نگاه می‌کنیم: کابل ممکن است همین حالا وصل شده باشد
  LaunchedEffect(kind) { if (kind == Kind.USB) usbList = ThermalPrinter.usbPrinters(context) }
  val link = DevicesModel.link(kind, bt, host, port, usb)

  fun askUsb(deviceName: String) {
    val manager = ThermalPrinter.usbManager(context) ?: return
    val device = ThermalPrinter.usbDevice(context, deviceName) ?: return
    if (manager.hasPermission(device)) return
    val intent = android.app.PendingIntent.getBroadcast(
      context, 0,
      android.content.Intent(USB_PERMISSION_ACTION).setPackage(context.packageName),
      android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
    )
    runCatching { manager.requestPermission(device, intent) }
  }

  V3Sheet(onDismiss = onDismiss, title = StrSettings.addPrinter) { closer ->
    V3TextField(name, { name = it.take(40) }, StrSettings.printerName, Modifier.fillMaxWidth(), placeholder = StrSettings.optional, imeAction = ImeAction.Done)
    Spacer(Modifier.height(14.dp))
    Text(StrSettings.paperWidth, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(6.dp))
    V3Segmented(listOf(StrSettings.paperMm(58), StrSettings.paperMm(80)), WIDTHS.indexOf(width).coerceAtLeast(0), { width = WIDTHS[it] })
    Spacer(Modifier.height(14.dp))
    Text(StrSettings.connectedBy, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(6.dp))
    V3Segmented(
      listOf(StrSettings.kindBluetooth, StrSettings.kindWifi, if (Platform.DESKTOP) StrSettings.kindSystem else StrSettings.kindCable),
      kind.ordinal,
      { kind = Kind.entries[it] },
    )
    Spacer(Modifier.height(14.dp))
    when (kind) {
      Kind.BLUETOOTH -> when {
        !granted -> {
          Hint(StrSettings.btPermission)
          V3Button(StrSettings.allow, { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Bluetooth)
        }
        paired.isEmpty() -> Hint(StrSettings.btEmpty)
        else -> Choices(paired, bt, subtitle = { it.address }, icon = Icons.Outlined.Bluetooth) { bt = it.address }
      }
      Kind.NETWORK -> {
        Hint(StrSettings.netNote)
        V3TextField(host, { host = it }, StrSettings.ipAddress, Modifier.fillMaxWidth(), kind = V3FieldKind.TEXT, placeholder = "192.168.1.50")
        Spacer(Modifier.height(10.dp))
        V3TextField(port, { port = it.filter { c -> c in '0'..'9' }.take(5) }, StrSettings.port, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER, helper = StrSettings.portHint)
      }
      Kind.USB -> if (usbList.isEmpty()) Hint(StrSettings.usbEmpty) else {
        Choices(usbList, usb, subtitle = { it.address.substringAfterLast('/') }, icon = Icons.Outlined.Usb) { usb = it.address; askUsb(it.address) }
        Hint(StrSettings.usbHint)
      }
    }
    Spacer(Modifier.height(18.dp))
    V3Button(
      StrSettings.savePrinter,
      { val chosen = link ?: return@V3Button; closer.close { onAdd(name, chosen.save(), width) } },
      Modifier.fillMaxWidth(),
      enabled = link != null,
      haptic = true,
    )
  }
}

@Composable
private fun Hint(text: String) {
  Text(text, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.fillMaxWidth())
  Spacer(Modifier.height(10.dp))
}

/** فهرستِ چاپگرهای پیدا‌شده با تیکِ برند روی انتخاب‌شده */
@Composable
private fun Choices(
  items: List<ThermalPrinter.Printer>,
  selected: String?,
  subtitle: (ThermalPrinter.Printer) -> String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onPick: (ThermalPrinter.Printer) -> Unit,
) {
  val c = PhoneV3.colors
  V3Card {
    items.forEachIndexed { i, p ->
      if (i > 0) V3Divider(start = 60.dp)
      V3ListRow(
        p.name,
        subtitle = subtitle(p),
        icon = icon,
        tile = false,
        chevron = false,
        trailing = if (p.address == selected) ({ Icon(Icons.Outlined.Check, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) }) else null,
        onClick = { onPick(p) },
      )
    }
  }
  Spacer(Modifier.height(10.dp))
}
