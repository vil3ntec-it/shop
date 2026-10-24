package ir.vil3ntec.tohid.print

import android.app.DesktopNotify
import android.content.Context
import ir.vil3ntec.tohid.core.hw.MonoImage
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob

/**
 *  برگهٔ A4ِ برچسب روی کامپیوتر (HW-04، S24) — جایگزینِ `LabelSheetPrint.kt`ِ
 *  اندروید، با `PrinterJob`ِ جاوا (مثلِ `ReportPrintDesk`).
 *
 *  تصویرِ برگه (۸ نقطه در میلی‌متر) روی **تمامِ** کاغذِ A4 کشیده می‌شود تا
 *  هر برچسب همان ۴۰×۳۰ میلی‌متر بماند. ⚠️ روی کاغذِ برچسبِ واقعی آزموده نشده.
 */
object LabelSheetPrint {

  fun print(@Suppress("UNUSED_PARAMETER") context: Context, sheet: MonoImage, title: String) {
    //  پنجرهٔ چاپ نخِ صفحه را نگه نمی‌دارد
    Thread({
      runCatching {
        val image = BufferedImage(sheet.width, sheet.height, BufferedImage.TYPE_BYTE_BINARY)
        for (y in 0 until sheet.height) for (x in 0 until sheet.width) image.setRGB(x, y, if (sheet[x, y]) 0x000000 else 0xFFFFFF)
        val job = PrinterJob.getPrinterJob()
        job.jobName = title
        val format = job.defaultPage().apply {
          paper = Paper().apply {
            //  A4 بر حسبِ ۱/۷۲ اینچ، و ناحیهٔ چاپیِ تمامِ صفحه
            setSize(595.28, 841.89)
            setImageableArea(0.0, 0.0, 595.28, 841.89)
          }
          orientation = PageFormat.PORTRAIT
        }
        job.setPrintable(SheetPrintable(image), format)
        if (job.printDialog()) job.print()
      }.onFailure { DesktopNotify.show("چاپِ برچسب", "چاپ انجام نشد: ${it.message ?: "دلیلِ نامعلوم"}") }
    }, "tohid-label-print").apply { isDaemon = true }.start()
  }

  private class SheetPrintable(private val image: BufferedImage) : Printable {
    override fun print(g: Graphics, pf: PageFormat, page: Int): Int {
      if (page > 0) return Printable.NO_SUCH_PAGE
      val g2 = g as Graphics2D
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
      g2.drawImage(image, 0, 0, pf.width.toInt(), pf.height.toInt(), null)
      return Printable.PAGE_EXISTS
    }
  }
}
