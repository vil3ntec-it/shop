package ir.vil3ntec.tohid.ui.v3.sales.drawer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPrint
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.print.HwPages
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  PAY-15 / RPT-04 — چاپِ برگهٔ X/Z ِ نسخهٔ ۳ روی چاپگرِ فاکتور، از صفِ چاپ با نوعِ `REPORT` (همان راهِ `ShiftPanelV2`).
 *  روی کاغذ همان است که روی برگه دیده شد: [lines] = `DrawerModel.report` ِ نمای همین بیننده (شاگردِ کور بی موردِ انتظار و
 *  اختلاف) و [notes] = همان یادداشت‌های زیرِ برگه. [copy] ⇒ زیرِ سرخط «چاپِ دوباره» (Z ای که از سابقه دوباره چاپ می‌شود؛
 *  اصل فقط همان لحظهٔ بستن). خروجی = پیامِ کاربر (چاپ شد، چاپگر نیست، یا دلیلِ شکست).
 */
object ShiftPrint {

  /**
   *  «چاپ» ِ برگهٔ X/Z فقط با مجوزِ `shift.report` (همان `ShiftPanelV2`)؛ null ⇒ بی دکمه. [show] پیامِ نتیجه را نشان می‌دهد.
   */
  @Composable
  fun button(copy: Boolean, show: (String) -> Unit): ((String, List<ReportLine>, List<String>) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allowed = remember { ShopRole.allows(context, "shift.report") }
    if (!allowed) return null
    return { title, lines, notes -> scope.launch { show(print(context, title, lines, notes, copy)) } }
  }

  suspend fun print(context: Context, title: String, lines: List<ReportLine>, notes: List<String>, copy: Boolean): String {
    val printer = PrinterRegistry.defaultSaved(context) ?: return StrShift.noReceiptPrinter
    val bytes = withContext(Dispatchers.IO) {
      runCatching {
        val p = printer.profile()
        val page = listOf(HwPages.Line(title, HwPages.Style.TITLE)) +
          (if (copy) listOf(HwPages.Line(StrPrint.copyMark, HwPages.Style.HEAD)) else emptyList()) +
          lines.map { HwPages.reportLine(if (it.value.isEmpty()) it.label else "${it.label}: ${it.value}", it.strong) } +
          notes.map { HwPages.reportLine(it) }
        EscPos.document(HwPages.text(context, p.paperDots, page), p)
      }
    }.getOrElse { return StrShift.reportNotPrinted(it.message ?: Str.unknownReason) }
    return PrintSpooler.submitAndWait(context, PrintQueue.Kind.REPORT, printer.link, title, bytes, done = StrShift.reportPrinted)
  }
}
