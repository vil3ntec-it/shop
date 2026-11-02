package android.text

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.paragraph.Alignment as SkAlign
import org.jetbrains.skia.paragraph.Direction
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider

/*
 *  shimِ iOS ِ `StaticLayout` روی پاراگرافِ Skia (همان موتورِ متنِ Compose): شکستنِ سطر در پهنا، تراز،
 *  جهتِ راست‌به‌چپ و قلمِ Vazirmatn — برای رسید و برگهٔ چاپی. همان API ِ `desktop/src/shim/kotlin/android/text`.
 */
open class TextPaint : Paint {
  constructor(flags: Int = 0) : super(flags)
  constructor(p: Paint) : super(p)
}

abstract class Layout {
  enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }
  abstract val height: Int
  abstract val width: Int
  abstract fun draw(canvas: Canvas)
  abstract val lineCount: Int
}

object TextDirectionHeuristics {
  val RTL = Any()
  val LTR = Any()
  val FIRSTSTRONG_LTR = Any()
}

class StaticLayout private constructor(
  private val text: String,
  private val paint: TextPaint,
  override val width: Int,
  private val align: Layout.Alignment,
  private val rtl: Boolean,
) : Layout() {
  @Deprecated("همان سازندهٔ قدیمیِ اندروید")
  constructor(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment, spacingMult: Float, spacingAdd: Float, includePad: Boolean) :
    this(text.toString(), paint, width, align, true)

  private val paragraph: Paragraph = build()

  override val height: Int get() = kotlin.math.ceil(paragraph.height).toInt()
  override val lineCount: Int get() = paragraph.lineNumber.toInt()

  private fun build(): Paragraph {
    val face = paint.typeface ?: Typeface.DEFAULT
    val family = if (face.isBold) "TohidBold" else "Tohid"
    val provider = TypefaceFontProvider()
    face.sk?.let { provider.registerTypeface(it, family) }
    val fonts = FontCollection().setDefaultFontManager(FontMgr.default).setAssetFontManager(provider)
    val style = ParagraphStyle().apply {
      direction = if (rtl) Direction.RTL else Direction.LTR
      alignment = when (align) {
        Layout.Alignment.ALIGN_CENTER -> SkAlign.CENTER
        Layout.Alignment.ALIGN_NORMAL -> SkAlign.START
        Layout.Alignment.ALIGN_OPPOSITE -> SkAlign.END
      }
    }
    val ts = TextStyle().setFontSize(paint.textSize).setColor(paint.color).setFontFamilies(arrayOf(family))
    return ParagraphBuilder(style, fonts).pushStyle(ts).addText(text).popStyle().build().also { it.layout(width.toFloat().coerceAtLeast(1f)) }
  }

  override fun draw(canvas: Canvas) {
    paragraph.paint(canvas.sk, 0f, 0f)
  }

  class Builder private constructor(private val text: String, private val paint: TextPaint, private val width: Int) {
    private var align = Layout.Alignment.ALIGN_NORMAL
    private var rtl = true
    fun setAlignment(a: Layout.Alignment) = apply { align = a }
    fun setTextDirection(d: Any) = apply { rtl = d === TextDirectionHeuristics.RTL }
    fun setIncludePad(b: Boolean) = this
    fun setLineSpacing(add: Float, mult: Float) = this
    fun setMaxLines(n: Int) = this
    fun build() = StaticLayout(text, paint, width, align, rtl)

    companion object {
      fun obtain(source: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int) =
        Builder(source.subSequence(start, end).toString(), paint, width)
    }
  }
}
