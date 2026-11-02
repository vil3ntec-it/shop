package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.core.hw.MonoImage

/*
 *  جایگزینِ iOS ِ `print/LabelSheetPrint.kt` (برگهٔ A4 ِ برچسب از راهِ چاپگرِ سیستم؛ کامپیوتر: ‎LabelSheetPrintDesk‎).
 *  AirPrint (`UIPrintInteractionController`) گامِ ۳ است؛ تا آن روز «چاپِ برگه» در آیفون کاری نمی‌کند.
 */
object LabelSheetPrint {
  fun print(context: Context, sheet: MonoImage, title: String) = print(context, listOf(sheet), title)

  fun print(context: Context, sheets: List<MonoImage>, title: String) {}
}
