package android.print.pdf

import android.content.Context
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import ir.vil3ntec.tohid.ios.print.RasterPdf

/*
 *  shimِ iOS ِ `PrintedPdfDocument` — اندازهٔ صفحه و ناحیهٔ چاپی از `PrintAttributes` (هزارمِ اینچ ⇐ پوینت، همان
 *  حسابِ اندروید: A4 = ۵۹۵ × ۸۴۱)، رسم روی Skia با `dpi` ِ خواسته‌شده یا ۳۰۰. «تک‌رنگ» ⇐ PDF ِ خاکستری.
 */
class PrintedPdfDocument(context: Context, attributes: PrintAttributes) :
  PdfDocument(
    (attributes.resolution?.horizontalDpi ?: PdfDocument.DEFAULT_DPI).coerceIn(72, PdfDocument.DEFAULT_DPI),
    gray = attributes.colorMode == PrintAttributes.COLOR_MODE_MONOCHROME,
  ) {

  private val media = attributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
  private val margins = attributes.minMargins ?: PrintAttributes.Margins.NO_MARGINS

  val pageWidth: Int = RasterPdf.points(media.widthMils)
  val pageHeight: Int = RasterPdf.points(media.heightMils)
  val pageContentRect: Rect = Rect(
    RasterPdf.points(margins.leftMils),
    RasterPdf.points(margins.topMils),
    pageWidth - RasterPdf.points(margins.rightMils),
    pageHeight - RasterPdf.points(margins.bottomMils),
  )

  fun startPage(pageNumber: Int): Page =
    startPage(PageInfo.Builder(pageWidth, pageHeight, pageNumber).setContentRect(pageContentRect).create())
}
