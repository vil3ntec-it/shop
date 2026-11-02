package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.StrCustomerCard
import ir.vil3ntec.tohid.core.i18n.StrLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  چاپِ کارتِ مشتری (CRM-16) — همان برچسبِ ۴۰×۳۰ ِ بارکدِ کالا (`HwPages.label`: نام، یک خط و بارکد؛
 *  EAN-13 ِ درست EAN-13 و بقیه Code128، رشته همان کدِ ثبت‌شده) و همان راه: چاپگرِ برچسب در صف
 *  (`PrintSpooler`)، وگرنه برگهٔ A4 با پنجرهٔ چاپِ سیستم (`LabelSheetPrint`). ⚠️ روی چاپگرِ واقعی آزموده نشده.
 */
object CustomerCardPrint {

  suspend fun print(context: Context, name: String, code: String): String {
    val r = withContext(Dispatchers.Default) { HwPages.label(context, name, StrCustomerCard.title, code) }
    val img = r.image ?: return StrLabel.labelFailed(name, r.error.orEmpty())
    val printer = PrinterRegistry.labelPrinter(context)
    if (printer == null) {
      val pages = withContext(Dispatchers.Default) { Labels.A4.sheets(listOf(img)) }
      if (pages.isEmpty()) return StrLabel.nothingToPrint
      LabelSheetPrint.print(context, pages, StrCustomerCard.printLabel(name))
      return StrLabel.a4Opened(pages.size)
    }
    return PrintSpooler.submitAndWait(
      context, PrintQueue.Kind.LABEL, printer.link, StrCustomerCard.printLabel(name), HwPages.labelBytes(printer, img, 1), done = StrLabel.printed(1),
    )
  }
}
