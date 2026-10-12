package ir.vil3ntec.tohid.print

import android.app.DesktopNotify
import android.content.Context
import ir.vil3ntec.tohid.data.ReportCsv
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.text.AttributedString

/**
 *  «چاپ گزارش» روی کامپیوتر — بومی، با `PrinterJob`ِ جاوا.
 *
 *  پیش‌تر یک فایلِ HTML در مرورگرِ پیش‌فرض باز می‌شد تا مرورگر چاپش کند؛
 *  قاعدهٔ صاحب مخزن: برنامهٔ کامپیوتر «حتی یک درصد» سایت نباشد. حالا
 *  پنجرهٔ چاپِ خودِ سیستم‌عامل باز می‌شود (چاپگر یا «ذخیره به PDF»ِ
 *  سیستم) و جدول با قلمِ Vazirmatn و چیدمانِ راست‌به‌چپ رسم می‌شود — همان
 *  چیدمانِ `ReportPrint`ِ اندروید.
 */
object ReportPrint {

  fun print(@Suppress("UNUSED_PARAMETER") context: Context, sheet: ReportCsv.Sheet, title: String) {
    //  پنجرهٔ چاپ نخِ صفحه را نگه نمی‌دارد
    Thread({
      runCatching {
        val job = PrinterJob.getPrinterJob()
        job.jobName = sheet.name
        job.setPrintable(TablePrintable(sheet, title))
        if (job.printDialog()) job.print()
      }.onFailure { DesktopNotify.show("چاپ", "چاپ انجام نشد: ${it.message ?: "دلیلِ نامعلوم"}") }
    }, "tohid-print").apply { isDaemon = true }.start()
  }

  private class TablePrintable(private val sheet: ReportCsv.Sheet, private val title: String) : Printable {

    private val base: Font = runCatching {
      ReportPrint::class.java.classLoader.getResourceAsStream(ir.vil3ntec.tohid.R.path(ir.vil3ntec.tohid.R.font.vazirmatn_regular))
        !!.use { Font.createFont(Font.TRUETYPE_FONT, it) }
    }.getOrDefault(Font(Font.SANS_SERIF, Font.PLAIN, 10))

    private val header = sheet.rows.firstOrNull().orEmpty()
    private val body = sheet.rows.drop(1)
    private val cols = header.size.coerceAtLeast(1)
    private val frc = FontRenderContext(null, true, true)

    /** شروعِ ردیف‌های هر صفحه — یک بار، سرِ اولین صدا زدن، حساب می‌شود */
    private var pageStarts: List<Int>? = null

    private fun lines(text: String, font: Font, width: Float): List<TextLayout> {
      if (text.isEmpty()) return emptyList()
      val attr = AttributedString(text).apply {
        addAttribute(TextAttribute.FONT, font)
        addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL)
      }
      val m = LineBreakMeasurer(attr.iterator, frc)
      val out = ArrayList<TextLayout>()
      while (m.position < text.length) out += m.nextLayout(width.coerceAtLeast(10f))
      return out
    }

    private fun height(ls: List<TextLayout>, font: Font): Float =
      if (ls.isEmpty()) font.size2D * 1.3f else ls.sumOf { (it.ascent + it.descent + it.leading).toDouble() }.toFloat()

    private fun rowHeight(cells: List<String>, font: Font, colW: Float): Float =
      (0 until cols).maxOf { height(lines(cells.getOrElse(it) { "" }, font, colW - 6), font) } + 6f

    private fun paginate(pf: PageFormat): List<Int> {
      val text = base.deriveFont(8.5f)
      val head = base.deriveFont(Font.BOLD, 8.5f)
      val colW = pf.imageableWidth.toFloat() / cols
      val starts = mutableListOf(0)
      var y = 46f + rowHeight(header, head, colW)
      body.forEachIndexed { i, r ->
        val h = rowHeight(r, text, colW)
        if (y + h > pf.imageableHeight && i > starts.last()) {
          starts += i
          y = rowHeight(header, head, colW)
        }
        y += h
      }
      return starts
    }

    override fun print(g: Graphics, pf: PageFormat, pageIndex: Int): Int {
      val starts = pageStarts ?: paginate(pf).also { pageStarts = it }
      if (pageIndex >= starts.size) return Printable.NO_SUCH_PAGE
      val g2 = g as Graphics2D
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      g2.translate(pf.imageableX, pf.imageableY)
      val width = pf.imageableWidth.toFloat()
      val colW = width / cols
      val text = base.deriveFont(8.5f)
      val head = base.deriveFont(Font.BOLD, 8.5f)
      var y = 0f

      fun draw(ls: List<TextLayout>, right: Float, top: Float) {
        var yy = top
        ls.forEach { l ->
          yy += l.ascent
          l.draw(g2, right - l.advance, yy)   // راست‌چین
          yy += l.descent + l.leading
        }
      }

      fun row(cells: List<String>, font: Font, shaded: Boolean) {
        val ls = (0 until cols).map { lines(cells.getOrElse(it) { "" }, font, colW - 6) }
        val h = ls.maxOf { height(it, font) } + 6f
        if (shaded) { g2.color = Color(240, 240, 240); g2.fill(java.awt.geom.Rectangle2D.Float(0f, y, width, h)) }
        g2.color = Color.GRAY
        g2.stroke = BasicStroke(0.5f)
        ls.forEachIndexed { i, l ->
          val x = width - (i + 1) * colW
          g2.draw(java.awt.geom.Rectangle2D.Float(x, y, colW, h))
          g2.color = Color.BLACK
          draw(l, x + colW - 3, y + 3)
          g2.color = Color.GRAY
        }
        y += h
      }

      g2.color = Color.BLACK
      if (pageIndex == 0) {
        draw(lines(title, base.deriveFont(Font.BOLD, 13f), width), width, y); y += 20f
        g2.color = Color.DARK_GRAY
        draw(lines(sheet.name, base.deriveFont(7.5f), width), width, y); y += 26f
        g2.color = Color.BLACK
      }
      if (body.isEmpty()) {
        draw(lines("داده‌ای برای چاپ نیست.", text, width), width, y)
        return Printable.PAGE_EXISTS
      }
      row(header, head, true)
      val end = if (pageIndex + 1 < starts.size) starts[pageIndex + 1] else body.size
      for (i in starts[pageIndex] until end) row(body[i], text, false)
      return Printable.PAGE_EXISTS
    }
  }
}
