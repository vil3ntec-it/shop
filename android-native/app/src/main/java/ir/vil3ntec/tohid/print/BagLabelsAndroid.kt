package ir.vil3ntec.tohid.print

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.core.pick.BagLabelPrint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  PCK-09 — سمتِ پلتفرمِ `core/pick/BagLabelPrint`: برچسبِ همهٔ کیسه‌های یک سفارش روی چاپگرِ رسیدِ همین دستگاه
 *  (`PrinterRegistry.defaultSaved`)، نوشتهٔ زیرِ هر QR با قلمِ برنامه (`HwPages.text`) و صفِ چاپ (`PrintSpooler`،
 *  نوعِ گزارش: بی فروش). بی چاپگر ⇒ فقط پیام. متنِ QR همان `labels` ِ سرور است.
 */
object BagLabelsPrint {

  /** `labels`: (متنِ QR، نوشتهٔ زیرش) به ترتیبِ کیسه ⇒ پیامِ صفحه */
  suspend fun print(context: Context, orderNo: Long, labels: List<Pair<String, String>>): String {
    if (labels.isEmpty()) return StrPick.printFailed("—")
    val printer = PrinterRegistry.defaultSaved(context) ?: return StrPick.noPrinter
    val bytes = withContext(Dispatchers.IO) {
      runCatching {
        val p = printer.profile()
        val image = BagLabelPrint.image(labels.map { BagLabelPrint.Bag(it.first, it.second) }, p.paperDots) { text ->
          HwPages.text(context, p.paperDots, listOf(HwPages.Line(text, HwPages.Style.HEAD)))
        }
        EscPos.document(image, p)
      }
    }.getOrElse { return StrPick.printFailed(it.message ?: "—") }
    return PrintSpooler.submitAndWait(context, PrintQueue.Kind.REPORT, printer.link, StrPick.labelsJob(orderNo), bytes, done = StrPick.printed)
  }
}

/** همان چاپ برای صفحه‌ای که Context ندارد (برگهٔ جمع‌آوری) */
@Composable
internal fun rememberBagLabelPrint(): suspend (Long, List<Pair<String, String>>) -> String {
  val context = LocalContext.current
  return remember(context) { { orderNo, labels -> BagLabelsPrint.print(context, orderNo, labels) } }
}
