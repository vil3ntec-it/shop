package ir.vil3ntec.tohid.ui.components.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.print.CommandCardPrint
import ir.vil3ntec.tohid.print.ScannerSheetPrint
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.scan.ScanCashPref
import ir.vil3ntec.tohid.scan.ScanCashRun
import kotlinx.coroutines.launch

/**
 *  «کارتِ فرمان» در «دستگاه‌ها» (رابطِ امروز و نسخهٔ ۳): با تنظیمِ روشن، سه بارکد روی صفحه (فقط صاحب یا مدیر)، «چاپِ
 *  کارت» و «کارتِ تازه» (راز و نسلِ تازه؛ کارت‌های پیشین باطل)، و خطِ آخرین کارِ ثبتِ اسکنر. خاموش ⇒ یک خطِ راهنما.
 */
@Composable
fun CommandCardBody(colors: HubColors, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) { ScanCashPref.load(context) }
  val on by ScanCashPref.on
  val gen by ScanCashPref.gen
  val last by ScanCashRun.last
  val canManage = remember { ScanCashPref.canManage(context) }
  var askNew by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  val key = remember(gen, on) { if (on && canManage) ScanCashPref.key(context) else null }
  Column(Modifier.fillMaxWidth()) {
    if (!on) Text(StrDevices.cardOff, color = colors.muted, style = MaterialTheme.typography.bodySmall)
    else {
      Text(StrDevices.cardLine, color = colors.muted, style = MaterialTheme.typography.bodySmall)
      last?.let { Spacer(Modifier.height(4.dp)); Text(it, color = colors.text, style = MaterialTheme.typography.bodySmall) }
    }
    key?.let { k ->
      Spacer(Modifier.height(6.dp))
      Text(StrDevices.cardNumber(k.gen), color = colors.text, style = MaterialTheme.typography.labelMedium)
      CommandCardPrint.ORDER.forEach { c ->
        val bitmap = remember(k, c) {
          runCatching { ThermalPrinter.toBitmap(ScannerSheetPrint.barcode(CommandCardPrint.modules(c, k), ThermalPrinter.WIDTH_58MM, 90)) }.getOrNull()
        }
        Spacer(Modifier.height(8.dp))
        Text(CommandCardPrint.label(c), color = colors.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        //  بی زمینهٔ ثابت: خودِ تصویر سفید/سیاهِ کدر است (HardcodedColorsTest)
        bitmap?.let {
          Image(it.asImageBitmap(), contentDescription = CommandCardPrint.label(c), Modifier.fillMaxWidth().heightIn(max = 110.dp),
            contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        }
      }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy, onClick = {
          busy = true
          scope.launch {
            toast(CommandCardPrint.print(context, k) ?: StrDevices.cardPrinted)
            busy = false
          }
        }) { Text(StrDevices.printCard) }
        OutlinedButton(enabled = !busy, onClick = { askNew = true }) { Text(StrDevices.newCard) }
      }
    }
  }
  if (askNew) {
    AlertDialog(
      onDismissRequest = { askNew = false },
      text = { Text(StrDevices.newCardAsk) },
      confirmButton = {
        TextButton(onClick = {
          askNew = false
          ScanCashPref.rotate(context)
        }) { Text(StrDevices.newCard) }
      },
      dismissButton = { TextButton(onClick = { askNew = false }) { Text(Str.cancel) } },
    )
  }
}
