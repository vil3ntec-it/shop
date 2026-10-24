package ir.vil3ntec.tohid.ui.v3.sales.print

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/** نامِ کارِ اجازهٔ USB — فقط داخلِ همین برنامه معنا دارد (همان برگهٔ چاپِ پیشین) */
private const val USB_PERMISSION_ACTION = "ir.vil3ntec.tohid.USB_PERMISSION"

/**
 *  برگهٔ چاپ (`sales/{id}/print`، رسیدِ برگشت هم): جای `PrinterDialog`ِ پیشین. چاپگرهای
 *  ثبت‌شده (دستگاه‌ها) اول؛ «چاپگرِ دیگر»: عرضِ کاغذ ۵۸|۸۰، راهِ وصل بلوتوث|وای‌فای|سیم — اجازهٔ
 *  بلوتوث، جفت‌شده‌ها، IP و درگاه (۹۱۰۰)، سیمیِ پیدا‌شده. هیچ فهرستِ ساختگی. چاپگرِ برگزیده
 *  پیش‌فرض می‌شود. فاکتورِ پیش‌تر چاپ‌شده ⇒ گامِ «دلیلِ چاپِ دوباره». تا فرستادن تمام نشده،
 *  برگه بسته نمی‌شود؛ پاسخِ چاپ (موفق یا خطا) پس از بستن به‌صورتِ پیام.
 */
@Composable
fun PrintSheet(
  title: String,
  needsReason: Boolean,
  onPrint: suspend (link: String, width: Int, reason: String?) -> String,
  onDone: (String) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  val saved = remember { runCatching { PrinterRegistry.list(context).filterNot { it.isLabel } }.getOrDefault(emptyList()) }
  var granted by remember {
    mutableStateOf(android.os.Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
  var form by remember {
    mutableStateOf(PrintModel.start(prefs.getString("printer_address", null), prefs.getInt("printer_width", ThermalPrinter.WIDTH_58MM), saved.map { it.link }))
  }
  var paired by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  var usbList by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  LaunchedEffect(granted, form.kind) { paired = if (granted && form.kind == PrintKind.BLUETOOTH) ThermalPrinter.paired(context) else emptyList() }
  //  با هر بار دیدنِ «سیم» دوباره نگاه می‌کنیم: کابل ممکن است همین حالا وصل شده باشد
  LaunchedEffect(form.kind) { if (form.kind == PrintKind.USB) usbList = ThermalPrinter.usbPrinters(context) }

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

  PrintBody(
    title = title,
    form = form,
    onForm = { f ->
      if (f.width != form.width) prefs.edit().putInt("printer_width", f.width).apply()
      if (f.usb != null && f.usb != form.usb) askUsb(f.usb)
      form = f
    },
    saved = saved,
    granted = granted,
    onAskBluetooth = { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) },
    paired = paired,
    usb = usbList,
    needsReason = needsReason,
    onPrint = { link, width, reason ->
      //  چاپگرِ برگزیده پیش‌فرض می‌شود؛ نشانیِ تازه به فهرستِ چاپگرها می‌رود (ثبت‌شده دست نمی‌خورد)
      prefs.edit().putString("printer_address", link).apply()
      if (saved.none { it.link == link }) runCatching { PrinterRegistry.add(context, "", link, width) }
      onPrint(link, width, reason)
    },
    onDone = onDone,
    onClose = onClose,
  )
}

/** حالتِ گام‌ها: برگزیدنِ چاپگر، یا دلیلِ چاپِ دوباره */
private enum class PrintStep { PICK, REASON }

/** بدنهٔ برگه — بی اندروید (عکس‌ها همین را با فهرستِ نمایشی صدا می‌زنند) */
@Composable
fun PrintBody(
  title: String,
  form: PrintForm,
  onForm: (PrintForm) -> Unit,
  saved: List<PrinterRegistry.Saved>,
  granted: Boolean,
  onAskBluetooth: () -> Unit,
  paired: List<ThermalPrinter.Printer>,
  usb: List<ThermalPrinter.Printer>,
  needsReason: Boolean,
  onPrint: suspend (link: String, width: Int, reason: String?) -> String,
  onDone: (String) -> Unit,
  onClose: () -> Unit,
  startReason: Boolean = false,
) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var step by remember { mutableStateOf(if (startReason) PrintStep.REASON else PrintStep.PICK) }
  var busy by remember { mutableStateOf(false) }
  var picked by remember { mutableStateOf<String?>(null) }
  var other by remember { mutableStateOf("") }
  val chosen = remember { booleanArrayOf(false) }
  val target = PrintModel.target(form)
  val width = saved.firstOrNull { it.link == form.saved }?.width ?: form.width

  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = title, dismissible = !busy, detents = false) { closer ->
    fun send(reason: String?) {
      val link = target ?: return
      busy = true
      scope.launch {
        val message = onPrint(link, width, reason)
        busy = false
        chosen[0] = true
        closer.close { onDone(message) }
      }
    }
    AnimatedContent(
      targetState = step,
      transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) },
      label = "printStep",
    ) { s ->
      Column(Modifier.fillMaxWidth()) {
        if (s == PrintStep.REASON) {
          Text(StrSale.reprintTitle, style = V3Type.headline, color = c.ink)
          Spacer(Modifier.height(4.dp))
          Text(StrSale.reprintBody, style = V3Type.footnote, color = c.inkMuted)
          Spacer(Modifier.height(12.dp))
          V3ChipRow(contentPadding = PaddingValues(0.dp)) {
            PrintModel.reasons().forEach { r -> V3Chip(r, picked == r, { picked = if (picked == r) null else r }) }
          }
          Spacer(Modifier.height(10.dp))
          V3TextField(other, { other = it.take(80); picked = null }, StrSale.otherReason)
          Spacer(Modifier.height(16.dp))
          val reason = PrintModel.reason(picked, other)
          V3Button(StrSale.reprint, { send(reason) }, Modifier.fillMaxWidth(), enabled = reason.isNotBlank() && !busy, loading = busy, haptic = true)
        } else {
          PickPrinter(form, onForm, saved, granted, onAskBluetooth, paired, usb)
          Spacer(Modifier.height(18.dp))
          V3Button(
            if (busy) StrSale.sendingToPrinter else title,
            { if (needsReason) step = PrintStep.REASON else send(null) },
            Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Print,
            enabled = target != null && !busy,
            loading = busy,
            haptic = true,
          )
        }
      }
    }
  }
}

@Composable
private fun PickPrinter(
  form: PrintForm,
  onForm: (PrintForm) -> Unit,
  saved: List<PrinterRegistry.Saved>,
  granted: Boolean,
  onAskBluetooth: () -> Unit,
  paired: List<ThermalPrinter.Printer>,
  usb: List<ThermalPrinter.Printer>,
) {
  val c = PhoneV3.colors
  if (saved.isNotEmpty()) {
    Text(StrSale.savedPrinters, style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(6.dp))
    V3Card(radius = 16.dp, padding = PaddingValues(vertical = 2.dp)) {
      saved.forEachIndexed { i, p ->
        if (i > 0) V3Divider(start = 60.dp)
        V3ListRow(
          p.name,
          subtitle = StrSale.paperMm(if (p.width == ThermalPrinter.WIDTH_80MM) 80 else 58),
          icon = Icons.Outlined.Print,
          tile = false,
          chevron = false,
          trailing = if (form.saved == p.link) ({ Check() }) else null,
          onClick = { onForm(form.copy(saved = p.link)) },
        )
      }
      V3Divider(start = 60.dp)
      V3ListRow(
        StrSale.otherPrinter,
        icon = Icons.Outlined.Wifi,
        tile = false,
        chevron = false,
        trailing = if (form.saved == null) ({ Check() }) else null,
        onClick = { onForm(form.copy(saved = null)) },
      )
    }
    if (form.saved != null) return
    Spacer(Modifier.height(16.dp))
  }
  Text(StrSale.paperWidth, style = V3Type.subhead, color = c.inkMuted)
  Spacer(Modifier.height(6.dp))
  V3Segmented(
    listOf(StrSale.paperMm(58), StrSale.paperMm(80)),
    PrintModel.WIDTHS.indexOf(form.width).coerceAtLeast(0),
    { onForm(form.copy(width = PrintModel.WIDTHS[it])) },
  )
  Spacer(Modifier.height(14.dp))
  Text(StrSale.connectedBy, style = V3Type.subhead, color = c.inkMuted)
  Spacer(Modifier.height(6.dp))
  V3Segmented(listOf(StrSale.kindBluetooth, StrSale.kindWifi, StrSale.kindCable), form.kind.ordinal, { onForm(form.copy(kind = PrintKind.entries[it])) })
  Spacer(Modifier.height(14.dp))
  when (form.kind) {
    PrintKind.BLUETOOTH -> when {
      !granted -> {
        Hint(StrSale.btPermission)
        V3Button(StrSale.allow, onAskBluetooth, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Bluetooth)
      }
      paired.isEmpty() -> Hint(StrSale.btEmpty)
      else -> Choices(paired, form.bt, { it.address }, Icons.Outlined.Bluetooth) { onForm(form.copy(bt = it.address)) }
    }
    PrintKind.NETWORK -> {
      Hint(StrSale.netNote)
      V3TextField(form.host, { onForm(form.copy(host = it)) }, StrSale.ipAddress, placeholder = "192.168.1.50")
      Spacer(Modifier.height(10.dp))
      V3TextField(form.port, { onForm(form.copy(port = PrintModel.port(it))) }, StrSale.port, kind = V3FieldKind.NUMBER, helper = StrSale.portHint)
    }
    PrintKind.USB -> if (usb.isEmpty()) Hint(StrSale.usbEmpty) else {
      Choices(usb, form.usb, { it.address.substringAfterLast('/') }, Icons.Outlined.Usb) { onForm(form.copy(usb = it.address)) }
      Spacer(Modifier.height(8.dp))
      Hint(StrSale.usbHint)
    }
  }
}

@Composable
private fun Hint(text: String) {
  Text(text, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.fillMaxWidth())
  Spacer(Modifier.height(10.dp))
}

@Composable
private fun Check() {
  Icon(Icons.Outlined.Check, contentDescription = StrSale.selectedWord, tint = PhoneV3Roles.brandText(PhoneV3.colors), modifier = Modifier.size(20.dp))
}

/** فهرستِ چاپگرهای پیدا‌شده با تیکِ برند روی برگزیده */
@Composable
private fun Choices(
  items: List<ThermalPrinter.Printer>,
  selected: String?,
  subtitle: (ThermalPrinter.Printer) -> String,
  icon: ImageVector,
  onPick: (ThermalPrinter.Printer) -> Unit,
) {
  V3Card(radius = 16.dp, padding = PaddingValues(vertical = 2.dp)) {
    items.forEachIndexed { i, p ->
      if (i > 0) V3Divider(start = 60.dp)
      V3ListRow(
        p.name,
        subtitle = subtitle(p),
        icon = icon,
        tile = false,
        chevron = false,
        trailing = if (p.address == selected) ({ Check() }) else null,
        onClick = { onPick(p) },
      )
    }
  }
}
