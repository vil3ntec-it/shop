package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.data.ReportCsv

/*
 *  جایگزینِ iOS ِ `print/ReportPrint.kt` (گزارش ⇐ PDF ⇐ چاپگرِ اندروید؛ کامپیوتر: ‎ReportPrintDesk‎).
 *  رسمِ PDF و AirPrint (`UIPrintInteractionController`) گامِ ۳ است؛ تا آن روز «چاپ» کاری نمی‌کند و
 *  «ذخیرهٔ PDF» پیامِ روشنِ «هنوز نیست» برمی‌گرداند (همان قالبِ پیامِ نسخهٔ اندروید).
 */
object ReportPrint {
  fun print(context: Context, sheet: ReportCsv.Sheet, title: String) {}

  fun savePdf(context: Context, sheet: ReportCsv.Sheet, title: String): String = "چاپ و PDF ِ گزارش روی آیفون هنوز نیست"
}
