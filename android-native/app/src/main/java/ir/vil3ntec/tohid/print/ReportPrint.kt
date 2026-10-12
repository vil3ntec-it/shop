package ir.vil3ntec.tohid.print

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import ir.vil3ntec.tohid.data.ReportCsv
import java.io.FileOutputStream

/**
 *  «چاپ گزارش» — بومی، بی WebView.
 *
 *  ── چرا بازنویسی شد ───────────────────────────────────────────────
 *  پیش‌تر یک صفحهٔ HTML در WebView ساخته و به `PrintManager` داده می‌شد.
 *  قاعدهٔ صاحب مخزن: اندروید و کامپیوتر «حتی یک درصد» وب یا WebView
 *  نباشند — و بالا آوردنِ WebView برای هر چاپ کُند هم بود. حالا جدول
 *  مستقیم روی صفحهٔ PDFِ چاپ رسم می‌شود (`PrintedPdfDocument` + `Canvas`)
 *  با قلمِ خودِ برنامه و چیدمانِ راست‌به‌چپ. چاپگر و «ذخیره به PDF»
 *  همان پنجرهٔ چاپِ اندروید است.
 *
 *  همان جدولی چاپ می‌شود که خروجی CSV دارد، تا عددِ کاغذ و فایل یکی باشد.
 *  روی کامپیوتر جایگزینش `ReportPrintDesk.kt` است (همین چیدمان با Java2D).
 *  ──────────────────────────────────────────────────────────────────
 */
object ReportPrint {

  fun print(context: Context, sheet: ReportCsv.Sheet, title: String) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val attrs = PrintAttributes.Builder()
      .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
      .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
      .build()
    manager.print(sheet.name, TableAdapter(context.applicationContext, sheet, title), attrs)
  }

  private class TableAdapter(
    private val context: Context,
    private val sheet: ReportCsv.Sheet,
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
      val info = PrintDocumentInfo.Builder("${sheet.name}.pdf")
        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
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
      try {
        draw(pdf, cancellationSignal)
        if (cancellationSignal?.isCanceled == true) { callback.onWriteCancelled(); return }
        FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
      } catch (e: Exception) {
        callback.onWriteFailed(e.message)
      } finally {
        pdf.close()
      }
    }

    private fun draw(pdf: PrintedPdfDocument, cancel: CancellationSignal?) {
      val face = runCatching {
        androidx.core.content.res.ResourcesCompat.getFont(context, ir.vil3ntec.tohid.R.font.vazirmatn_regular)
      }.getOrNull() ?: Typeface.DEFAULT
      val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 8.5f; color = Color.BLACK }
      val head = TextPaint(text).apply { typeface = Typeface.create(face, Typeface.BOLD) }
      val big = TextPaint(head).apply { textSize = 13f }
      val small = TextPaint(text).apply { textSize = 7.5f; color = Color.DKGRAY }
      val line = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = Color.GRAY }
      val shade = Paint().apply { style = Paint.Style.FILL; color = Color.rgb(240, 240, 240) }

      val header = sheet.rows.firstOrNull().orEmpty()
      val body = sheet.rows.drop(1)
      val cols = header.size.coerceAtLeast(1)

      var number = 0
      var page = pdf.startPage(number)
      var canvas = page.canvas
      val margin = 28f
      val width = canvas.width - 2 * margin
      val bottom = canvas.height - margin
      val colW = width / cols
      var y = margin

      fun layout(s: String, p: TextPaint, w: Float): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, p, w.toInt().coerceAtLeast(10))
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setTextDirection(TextDirectionHeuristics.RTL)
          .build()

      fun at(l: StaticLayout, x: Float, top: Float) {
        canvas.save(); canvas.translate(x, top); l.draw(canvas); canvas.restore()
      }

      /** یک ردیف — ستونِ اول سمتِ راست، مثلِ جدولِ فارسی */
      fun row(cells: List<String>, p: TextPaint, shaded: Boolean): Float {
        val layouts = (0 until cols).map { layout(cells.getOrElse(it) { "" }, p, colW - 6) }
        val h = (layouts.maxOf { it.height } + 6).toFloat()
        if (shaded) canvas.drawRect(margin, y, margin + width, y + h, shade)
        layouts.forEachIndexed { i, l ->
          val x = margin + width - (i + 1) * colW
          canvas.drawRect(x, y, x + colW, y + h, line)
          at(l, x + 3, y + 3)
        }
        return h
      }

      fun newPage() {
        pdf.finishPage(page)
        number++
        page = pdf.startPage(number)
        canvas = page.canvas
        y = margin
        y += row(header, head, true)
      }

      val t = layout(title, big, width)
      at(t, margin, y); y += t.height + 2
      val sub = layout(sheet.name, small, width)
      at(sub, margin, y); y += sub.height + 10

      if (body.isEmpty()) {
        val none = layout("داده‌ای برای چاپ نیست.", text, width)
        at(none, margin, y)
      } else {
        y += row(header, head, true)
        for (r in body) {
          if (cancel?.isCanceled == true) break
          val probe = (0 until cols).maxOf { layout(r.getOrElse(it) { "" }, text, colW - 6).height } + 6f
          if (y + probe > bottom) newPage()
          y += row(r, text, false)
        }
      }
      pdf.finishPage(page)
    }
  }
}
