package ir.vil3ntec.tohid.ui.v3.settings.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import kotlinx.coroutines.launch

/**
 *  چاپگرها (`PrinterRegistry`): هر ردیف نام، «راه · نشانی · عرض» و قرصِ «پیش‌فرض»؛
 *  کشیدن ⇐ «حذف» یا «پیش‌فرض»، دکمهٔ «آزمون» ⇐ برگهٔ آزمون و نتیجه در پیامِ کوتاه،
 *  و «افزودنِ چاپگر» ⇐ برگهٔ افزودن.
 */
@Composable
internal fun PrintersCard(highlight: Boolean) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val toasts = LocalV3Toasts.current
  var tick by remember { mutableIntStateOf(0) }
  val printers = remember(tick) { PrinterRegistry.list(context) }
  val def = remember(tick) { PrinterRegistry.defaultLink(context) }
  var adding by remember { mutableStateOf(false) }
  //  چاپگرِ حذف‌شده با جمع شدنِ نرمِ ردیف می‌رود (`V3Collapse`) و «برگرداندن» بازش می‌کند
  var gone by remember(tick) { mutableStateOf(emptySet<String>()) }
  if (adding) {
    AddPrinterSheet(onDismiss = { adding = false }) { name, link, width ->
      PrinterRegistry.add(context, name, link, width)
      tick++
      toasts?.show(StrSettings.printerAdded)
    }
  }
  DeviceCard(StrSettings.printers, StrSettings.printersNote, highlight) {
    if (printers.isEmpty()) V3ListRow(StrSettings.noPrinterYet, icon = Icons.Outlined.Print, tile = false, chevron = false)
    printers.forEachIndexed { i, p ->
      val isDefault = p.link == def
      V3Collapse(visible = p.id !in gone) {
        PrinterRow(
          p, isDefault, divider = i > 0,
          onTest = {
            toasts?.show(StrSettings.testSent(p.name.ifBlank { PrinterRegistry.label(p.link) }))
            scope.launch { toasts?.show(PrinterRegistry.testPrint(context, p) ?: StrSettings.testDone) }
          },
          onRemove = {
            PrinterRegistry.remove(context, p.id); gone = gone + p.id
            //  ثبتِ دوباره همان کارِ وارونهٔ موتور است
            toasts?.undo(StrSettings.printerRemoved) {
              val back = PrinterRegistry.add(context, p.name, p.link, p.width)
              if (isDefault) PrinterRegistry.makeDefault(context, back)
              tick++
            }
          },
          onDefault = { PrinterRegistry.makeDefault(context, p); tick++ },
        )
      }
    }
    V3Divider(start = 64.dp)
    V3ListRow(StrSettings.addPrinter, icon = Icons.Outlined.Add, onClick = { adding = true })
  }
}

@Composable
private fun PrinterRow(p: PrinterRegistry.Saved, isDefault: Boolean, divider: Boolean, onTest: () -> Unit, onRemove: () -> Unit, onDefault: () -> Unit) {
  Column {
    if (divider) V3Divider(start = 64.dp)
    V3ListRow(
      p.name.ifBlank { PrinterRegistry.label(p.link) },
      subtitle = "${PrinterRegistry.kind(p.link)} · ${PrinterRegistry.label(p.link)} · " +
        StrSettings.paperMm(if (p.width >= ThermalPrinter.WIDTH_80MM) 80 else 58),
      icon = Icons.Outlined.Print,
      chevron = false,
      trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isDefault) { V3StatusPill(V3Status.DELIVERED, StrSettings.defaultPrinter); Spacer(Modifier.width(4.dp)) }
          V3TextButton(StrSettings.test, onTest)
        }
      },
      endActions = listOf(V3SwipeAction(Icons.Outlined.Delete, StrSettings.remove, V3ButtonKind.DANGER, onRemove)),
      startActions = if (isDefault) emptyList() else listOf(V3SwipeAction(Icons.Outlined.Star, StrSettings.makeDefault, V3ButtonKind.PRIMARY, onDefault)),
    )
  }
}
