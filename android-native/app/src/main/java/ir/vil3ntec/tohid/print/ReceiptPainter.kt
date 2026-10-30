package ir.vil3ntec.tohid.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import ir.vil3ntec.tohid.R
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptMetrics
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster
import ir.vil3ntec.tohid.core.receipt.TextStyle

/**
 *  نوشته‌های فاکتورِ نسخهٔ ۲ ⇒ تصویرِ یک‌بیتی، با قلمِ وزیرمتن و راست‌به‌چپ — تنها
 *  کارِ سکو در فاکتور (`ReceiptRaster.TextRenderer`). خط، لوگو و QR را هسته می‌کشد.
 *
 *  کامپیوتر همین فایل را با `desktop/src/shim` (Java2D) می‌دواند، پس دو برنامه یک فاکتور
 *  چاپ می‌کنند. فقط همان چند فرمانی که شیم دارد: `Canvas`، `StaticLayout`، `getPixels`.
 */
class ReceiptPainter(context: Context) : ReceiptRaster.TextRenderer {

  private val regular: Typeface = Receipt.font(context, R.font.vazirmatn_regular)
  private val bold: Typeface = Receipt.font(context, R.font.vazirmatn_bold)

  private class Placed(val layout: StaticLayout, val x: Int, val y: Int)

  override fun render(block: ReceiptBlock, widthDots: Int, metrics: ReceiptMetrics): MonoImage {
    val inner = (widthDots - metrics.pad * 2).coerceAtLeast(1)
    val placed = ArrayList<Placed>()
    var y = 0

    /** برچسب راست، مقدار چپ؛ [share] سهمِ برچسب از پهنا */
    fun pair(right: String, left: String, rightPaint: TextPaint, leftPaint: TextPaint, share: Float) {
      val rw = (inner * share).toInt()
      val lw = inner - rw
      val r = Receipt.layout(right, rightPaint, rw, Layout.Alignment.ALIGN_NORMAL)
      val l = Receipt.layout(left, leftPaint, lw, Layout.Alignment.ALIGN_OPPOSITE)
      placed += Placed(r, metrics.pad + lw, y)
      placed += Placed(l, metrics.pad, y)
      y += maxOf(r.height, l.height)
    }

    fun line(text: String, paint: TextPaint, align: Layout.Alignment) {
      val l = Receipt.layout(text, paint, inner, align)
      placed += Placed(l, metrics.pad, y)
      y += l.height
    }

    when (block) {
      is ReceiptBlock.Text -> line(block.text, paint(block.style, metrics),
        if (block.center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
      is ReceiptBlock.Row -> paint(block.style, metrics).let { p -> pair(block.label, block.value, p, p, ROW_LABEL_SHARE) }
      is ReceiptBlock.Item -> {
        line(block.name, Receipt.paint(bold, metrics.size(TextStyle.BODY).toFloat()), Layout.Alignment.ALIGN_NORMAL)
        val small = paint(TextStyle.SMALL, metrics)
        pair(block.detail, block.total, small, small, ITEM_DETAIL_SHARE)
        block.notes.forEach { line(it, small, Layout.Alignment.ALIGN_NORMAL) }
        y += metrics.gap / 2
      }
      else -> error("بلوکِ نوشته نیست: $block")
    }
    return draw(placed, widthDots, y.coerceAtLeast(1))
  }

  private fun paint(style: TextStyle, m: ReceiptMetrics): TextPaint = Receipt.paint(
    if (style == TextStyle.TITLE || style == TextStyle.TOTAL) bold else regular,
    m.size(style).toFloat(),
  )

  private fun draw(placed: List<Placed>, width: Int, height: Int): MonoImage {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(bitmap)
      canvas.drawColor(Color.WHITE)
      placed.forEach { p ->
        canvas.save(); canvas.translate(p.x.toFloat(), p.y.toFloat()); p.layout.draw(canvas); canvas.restore()
      }
      return ThermalPrinter.toMono(bitmap, width)
    } finally {
      bitmap.recycle()
    }
  }

  private companion object {
    /** ردیفِ «برچسب ـ مقدار»: نیمه‌نیمه، همان فاکتورِ پیشین */
    const val ROW_LABEL_SHARE = 0.5f
    /** «۳ بسته × ۱۵۰» بیشتر از جمعِ قلم جا می‌خواهد */
    const val ITEM_DETAIL_SHARE = 0.6f
  }
}
