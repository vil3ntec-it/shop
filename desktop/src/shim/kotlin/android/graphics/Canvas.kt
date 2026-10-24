package android.graphics

import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D

/**
 *  بومِ کشیدن روی `Bitmap` — همان چند فرمانی که `Receipt` لازم دارد، با
 *  Java2D. این‌طور فاکتورِ چاپی روی کامپیوتر **همان** فاکتورِ گوشی است و
 *  چیدمانش دو جا نوشته نشده.
 */
class Canvas(bitmap: Bitmap) {
  internal val g: Graphics2D = bitmap.image.createGraphics().apply {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
  }
  private val w = bitmap.width
  private val h = bitmap.height
  private val saved = ArrayDeque<AffineTransform>()

  fun drawColor(color: Int) {
    g.color = java.awt.Color(color, true)
    val t = g.transform
    g.transform = AffineTransform()
    g.fillRect(0, 0, w, h)
    g.transform = t
  }

  fun save(): Int { saved.addLast(g.transform); return saved.size }
  fun restore() { saved.removeLastOrNull()?.let { g.transform = it } }
  fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())

  fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
    g.color = java.awt.Color(paint.color, true)
    g.stroke = BasicStroke(paint.strokeWidth.coerceAtLeast(1f))
    g.draw(Line2D.Float(x1, y1, x2, y2))
  }

  fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) {
    g.color = java.awt.Color(paint.color, true)
    g.fill(java.awt.geom.Rectangle2D.Float(l, t, r - l, b - t))
  }

  /** عکس با ماتریسِ نگاشت (چرخشِ EXIFِ بانکِ تصاویر) */
  fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
    val t = g.transform
    g.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION,
      if (paint != null && paint.flags and Paint.FILTER_BITMAP_FLAG != 0) RenderingHints.VALUE_INTERPOLATION_BILINEAR
      else RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
    )
    g.transform(matrix.affine())
    g.drawImage(bitmap.image, 0, 0, null)
    g.transform = t
  }
}

/** `Matrix`ِ اندروید — فقط `setValues` (ترتیبِ اندروید: sx, kx, tx, ky, sy, ty, …) */
class Matrix {
  private val v = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
  fun setValues(values: FloatArray) { values.copyInto(v, endIndex = 9) }
  internal fun affine() = AffineTransform(
    v[0].toDouble(), v[3].toDouble(), v[1].toDouble(), v[4].toDouble(), v[2].toDouble(), v[5].toDouble(),
  )
}

open class Paint(val flags: Int = 0) {
  var color: Int = Color.BLACK
  var strokeWidth: Float = 1f
  var isAntiAlias: Boolean = flags and ANTI_ALIAS_FLAG != 0
  var typeface: Typeface? = null
  var textSize: Float = 12f
  companion object {
    const val ANTI_ALIAS_FLAG = 1
    const val FILTER_BITMAP_FLAG = 2
  }
}

class Typeface internal constructor(internal val font: java.awt.Font) {
  companion object {
    @JvmField val DEFAULT = Typeface(java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12))
    @JvmField val DEFAULT_BOLD = Typeface(java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 12))
    fun of(font: java.awt.Font) = Typeface(font)
  }
}
