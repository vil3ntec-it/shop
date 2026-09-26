package android.text

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.text.AttributedString

class TextPaint(flags: Int = 0) : Paint(flags)

abstract class Layout {
  enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }
  abstract val height: Int
  abstract fun draw(canvas: Canvas)
}

object TextDirectionHeuristics {
  @JvmField val RTL = Any()
  @JvmField val LTR = Any()
}

/**
 *  چیدنِ متنِ چندخطی — با `LineBreakMeasurer`ِ جاوا که دوجهته (bidi) را
 *  درست می‌چیند: پایهٔ راست‌به‌چپ، عدد و حرفِ لاتین سرِ جای خودشان.
 *
 *  «NORMAL» در متنِ راست‌به‌چپ یعنی **راست‌چین**، و «OPPOSITE» چپ‌چین —
 *  همان معنای اندروید.
 */
class StaticLayout private constructor(
  private val text: String,
  private val paint: TextPaint,
  private val width: Int,
  private val align: Alignment,
  private val rtl: Boolean,
) : Layout() {

  private val frc = FontRenderContext(null, true, true)
  private val lines: List<TextLayout> = build()

  override val height: Int = lines.sumOf { (it.ascent + it.descent + it.leading).toDouble() }.let { Math.ceil(it).toInt() }

  @Deprecated("همان سازندهٔ قدیمیِ اندروید")
  constructor(text: CharSequence, paint: TextPaint, width: Int, align: Alignment, spacingMult: Float, spacingAdd: Float, includePad: Boolean) :
    this(text.toString(), paint, width, align, true)

  private fun font(): java.awt.Font = (paint.typeface ?: Typeface.DEFAULT).font.deriveFont(paint.textSize)

  private fun build(): List<TextLayout> {
    if (text.isEmpty()) return listOf(TextLayout(" ", font(), frc))
    val out = ArrayList<TextLayout>()
    for (para in text.split('\n')) {
      if (para.isEmpty()) { out += TextLayout(" ", font(), frc); continue }
      val attr = AttributedString(para).apply {
        addAttribute(TextAttribute.FONT, font())
        addAttribute(TextAttribute.RUN_DIRECTION, if (rtl) TextAttribute.RUN_DIRECTION_RTL else TextAttribute.RUN_DIRECTION_LTR)
      }
      val m = LineBreakMeasurer(attr.iterator, frc)
      while (m.position < para.length) out += m.nextLayout(width.toFloat().coerceAtLeast(1f))
    }
    return out
  }

  override fun draw(canvas: Canvas) {
    val g = canvas.g
    g.color = java.awt.Color(paint.color, true)
    var y = 0f
    for (l in lines) {
      y += l.ascent
      val adv = l.visibleAdvance
      val right = rtl == (align == Alignment.ALIGN_NORMAL)
      val x = when (align) {
        Alignment.ALIGN_CENTER -> (width - adv) / 2f
        else -> if (right) width - adv else 0f
      }
      l.draw(g, x, y)
      y += l.descent + l.leading
    }
  }

  class Builder private constructor(private val text: String, private val paint: TextPaint, private val width: Int) {
    private var align = Alignment.ALIGN_NORMAL
    private var rtl = true
    fun setAlignment(a: Alignment) = apply { align = a }
    fun setTextDirection(d: Any) = apply { rtl = d === TextDirectionHeuristics.RTL }
    fun setIncludePad(b: Boolean) = this
    fun setLineSpacing(add: Float, mult: Float) = this
    fun build() = StaticLayout(text, paint, width, align, rtl)
    companion object {
      fun obtain(source: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int) =
        Builder(source.subSequence(start, end).toString(), paint, width)
    }
  }

}
