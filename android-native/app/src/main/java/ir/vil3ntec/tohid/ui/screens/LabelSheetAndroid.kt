package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.data.InternalBarcodes
import ir.vil3ntec.tohid.data.LabelToolsPref
import ir.vil3ntec.tohid.data.ScaleLabels
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.print.ProductLabelPrint
import ir.vil3ntec.tohid.todayIso
import kotlinx.coroutines.launch

/**
 *  مرزِ پلتفرمِ «چاپِ برچسب» (PRD-23/PRD-05): قاعده‌های ترازوی همین دستگاه، چاپگرِ برچسب و چاپ.
 *  ثبتِ بارکدِ داخلی با `onApply` ِ صفحهٔ انبار است (همان `store.save` و پیامِ همیشگی).
 */
@Composable
fun LabelSheetWired(
  context: android.content.Context,
  d: ShopData,
  onDismiss: () -> Unit,
  onApply: (WarehouseEngine.Result, String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val hasPrinter = remember { ProductLabelPrint.hasLabelPrinter(context) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  LabelSheet(
    d = d,
    hasLabelPrinter = hasPrinter,
    busy = busy,
    message = message,
    onDismiss = onDismiss,
    onMakeCodes = { ids ->
      val n = InternalBarcodes.missing(d, ids).size
      val r = InternalBarcodes.assign(d, ids, ScaleLabels.rules(context), todayIso(), System.currentTimeMillis(), ::newId)
      message = when (r) {
        is WarehouseEngine.Result.Failed -> r.message
        is WarehouseEngine.Result.Ok -> StrLabel.codesMade(n)
      }
      onApply(r, StrLabel.codesMade(n))
    },
    onPrint = { plan, kind, a4 ->
      if (!busy) {
        busy = true
        scope.launch {
          message = runCatching { ProductLabelPrint.print(context, d.products.associateBy { it.id }, plan, kind, a4) }
            .getOrElse { it.message ?: StrLabel.nothingToPrint }
          busy = false
        }
      }
    },
  )
}

/** بخشِ «برچسب و بارکدِ داخلی» با خواندن و نوشتنِ `LabelToolsPref` — تا `Settings` یک خط بخواهد */
@Composable
fun LabelToolsSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(LabelToolsPref.on(context)) }
  LabelToolsSettings(on) { on = it; LabelToolsPref.set(context, it) }
}
