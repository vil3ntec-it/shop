package ir.vil3ntec.tohid.print

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import ir.vil3ntec.tohid.core.hw.MonoImage
import java.io.FileOutputStream

/**
 *  برگهٔ A4ِ برچسب (HW-04، S24) با چاپِ سیستمِ اندروید — PDFِ یک‌صفحه‌ای که
 *  تصویرِ برگه (`Labels.A4.sheet`، ۸ نقطه در میلی‌متر) **تمامِ** صفحه را
 *  می‌گیرد، پس هر برچسب همان ۴۰×۳۰ میلی‌متر می‌ماند.
 *
 *  ⚠️ چاپگرِ واقعی حاشیهٔ فیزیکی دارد؛ در پنجرهٔ چاپ «بی حاشیه / اندازهٔ
 *  واقعی» را بزنید. روی کاغذِ برچسبِ واقعی آزموده نشده (NOT-VERIFIED).
 *  کامپیوتر جایگزینِ خودش را دارد (`LabelSheetPrintDesk.kt`).
 */
object LabelSheetPrint {

  fun print(context: Context, sheet: MonoImage, title: String) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val attrs = PrintAttributes.Builder()
      .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
      .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
      .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
      .build()
    manager.print(title, SheetAdapter(context.applicationContext, sheet, title), attrs)
  }

  private class SheetAdapter(
    private val context: Context,
    private val sheet: MonoImage,
    private val title: String,
  ) : PrintDocumentAdapter() {

    private var attrs: PrintAttributes? = null

    override fun onLayout(
      oldAttributes: PrintAttributes?,
      newAttributes: PrintAttributes,
      cancellationSignal: CancellationSignal?,
      callback: LayoutResultCallback,
      extras: Bundle?,
    ) {
      if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
      attrs = newAttributes
      val info = PrintDocumentInfo.Builder("$title.pdf")
        .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
        .setPageCount(1)
        .build()
      callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
      pages: Array<out PageRange>?,
      destination: ParcelFileDescriptor,
      cancellationSignal: CancellationSignal?,
      callback: WriteResultCallback,
    ) {
      val a = attrs ?: run { callback.onWriteFailed("اندازهٔ کاغذ معلوم نیست"); return }
      val pdf = PrintedPdfDocument(context, a)
      val bitmap = ThermalPrinter.toBitmap(sheet)
      try {
        val page = pdf.startPage(0)
        val info = page.info
        //  تمامِ صفحه — نه «جا شدن» در ناحیهٔ چاپی، تا اندازهٔ برچسب درست بماند
        page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, info.pageWidth.toFloat(), info.pageHeight.toFloat()), null)
        pdf.finishPage(page)
        if (cancellationSignal?.isCanceled == true) { callback.onWriteCancelled(); return }
        FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
      } catch (e: Exception) {
        callback.onWriteFailed(e.message)
      } finally {
        pdf.close()
        bitmap.recycle()
      }
    }
  }
}
