package android.graphics

import kotlin.math.roundToInt
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Rect as SkRect

/*
 *  shimِ iOS ِ رسمِ ساده روی `Bitmap` (‎Bitmap.kt‎) با Skia — آنچه بانکِ تصویر و عکسِ کالا می‌خواهند:
 *  رنگِ زمینه، کشیدنِ بیت‌مپ با `Matrix` (چرخشِ EXIF) یا در مستطیل. ترتیبِ ۹ عددِ `Matrix` همان
 *  اندروید است (سطری، MSCALE_X، MSKEW_X، MTRANS_X، …) که همان `Matrix33` ِ Skia است.
 */
object Color {
  const val BLACK = -0x1000000
  const val WHITE = -0x1
  const val TRANSPARENT = 0
  const val RED = -0x10000
  const val GREEN = -0xff0100
  const val BLUE = -0xffff01
  const val GRAY = -0x777778
  const val LTGRAY = -0x333334
  const val DKGRAY = -0xbbbbbc

  fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
  fun rgb(red: Int, green: Int, blue: Int): Int = argb(0xFF, red, green, blue)
  fun alpha(color: Int): Int = color ushr 24
  fun red(color: Int): Int = (color shr 16) and 0xFF
  fun green(color: Int): Int = (color shr 8) and 0xFF
  fun blue(color: Int): Int = color and 0xFF

  /** `#RRGGBB` یا `#AARRGGBB`؛ شکلِ دیگر ⇐ IllegalArgumentException (مثلِ اندروید) */
  fun parseColor(colorString: String): Int {
    val s = colorString.removePrefix("#")
    val v = s.toLongOrNull(16) ?: throw IllegalArgumentException("Unknown color")
    return when (s.length) {
      6 -> (v or 0xFF000000L).toInt()
      8 -> v.toInt()
      else -> throw IllegalArgumentException("Unknown color")
    }
  }
}

open class Paint(flags: Int = 0) {
  enum class Style { FILL, STROKE, FILL_AND_STROKE }
  enum class Align { LEFT, CENTER, RIGHT }

  var color: Int = Color.BLACK
  var isAntiAlias: Boolean = flags and ANTI_ALIAS_FLAG != 0
  var isFilterBitmap: Boolean = flags and FILTER_BITMAP_FLAG != 0
  var style: Style = Style.FILL
  var strokeWidth: Float = 0f
  var textSize: Float = 12f
  var typeface: Typeface? = null
  var textAlign: Align = Align.LEFT
  var alpha: Int
    get() = Color.alpha(color)
    set(v) { color = (color and 0x00FFFFFF) or (v shl 24) }

  /** همان سازندهٔ رونوشتِ اندروید (`TextPaint(paint)`) */
  constructor(other: Paint) : this(0) {
    color = other.color; isAntiAlias = other.isAntiAlias; isFilterBitmap = other.isFilterBitmap; style = other.style
    strokeWidth = other.strokeWidth; textSize = other.textSize; typeface = other.typeface; textAlign = other.textAlign
  }

  internal fun sk(): SkPaint = SkPaint().also {
    it.color = color
    it.isAntiAlias = isAntiAlias
    it.mode = when (style) {
      Style.FILL -> org.jetbrains.skia.PaintMode.FILL
      Style.STROKE -> org.jetbrains.skia.PaintMode.STROKE
      Style.FILL_AND_STROKE -> org.jetbrains.skia.PaintMode.STROKE_AND_FILL
    }
    it.strokeWidth = strokeWidth
  }

  companion object {
    const val ANTI_ALIAS_FLAG = 1
    const val FILTER_BITMAP_FLAG = 2
  }
}

class Matrix {
  private val v = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

  fun reset() { floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f).copyInto(v) }
  fun setValues(values: FloatArray) { values.copyInto(v, 0, 0, 9) }
  fun getValues(values: FloatArray) { v.copyInto(values, 0, 0, 9) }
  fun isIdentity(): Boolean = v.contentEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

  fun postTranslate(dx: Float, dy: Float): Boolean = post(floatArrayOf(1f, 0f, dx, 0f, 1f, dy, 0f, 0f, 1f))
  fun postScale(sx: Float, sy: Float): Boolean = post(floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f))

  fun postRotate(degrees: Float): Boolean {
    val r = degrees / 180.0 * kotlin.math.PI
    val c = kotlin.math.cos(r).toFloat()
    val s = kotlin.math.sin(r).toFloat()
    return post(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
  }

  fun postRotate(degrees: Float, px: Float, py: Float): Boolean {
    postTranslate(-px, -py); postRotate(degrees); return postTranslate(px, py)
  }

  /** M ⇐ other × M (همان `post…` ِ اندروید) */
  private fun post(o: FloatArray): Boolean {
    val r = FloatArray(9)
    for (i in 0 until 3) for (j in 0 until 3) r[i * 3 + j] = o[i * 3] * v[j] + o[i * 3 + 1] * v[3 + j] + o[i * 3 + 2] * v[6 + j]
    r.copyInto(v)
    return true
  }

  internal fun sk(): Matrix33 = Matrix33(*v)
}

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0) {
  fun width(): Int = right - left
  fun height(): Int = bottom - top
  internal fun sk(): SkRect = SkRect.makeLTRB(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

class RectF(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
  fun width(): Float = right - left
  fun height(): Float = bottom - top
  internal fun sk(): SkRect = SkRect.makeLTRB(left, top, right, bottom)
}

/**
 *  [unit] پیکسل در هر واحدِ رسم: ۱ برای بیت‌مپ؛ صفحهٔ PDF ِ چاپ (‎android/graphics/pdf‎) واحدش پوینت است و
 *  روی بیت‌مپِ ۳۰۰ نقطه در اینچ کشیده می‌شود ⇐ `width`/`height` همان پوینت‌های اندروید (A4 = ۵۹۵ × ۸۴۱).
 */
class Canvas internal constructor(private val target: Bitmap, private val unit: Float) {
  constructor(target: Bitmap) : this(target, 1f)

  private val c = SkCanvas(target.sk).also { if (unit != 1f) it.scale(unit, unit) }

  val width: Int get() = if (unit == 1f) target.width else (target.width / unit).roundToInt()
  val height: Int get() = if (unit == 1f) target.height else (target.height / unit).roundToInt()

  fun drawColor(color: Int) { c.clear(color) }

  fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
    c.drawImage(Image.makeFromBitmap(bitmap.sk), left, top, paint?.sk())
  }

  fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
    c.save()
    c.concat(matrix.sk())
    c.drawImageRect(Image.makeFromBitmap(bitmap.sk), SkRect.makeWH(bitmap.width.toFloat(), bitmap.height.toFloat()),
      SkRect.makeWH(bitmap.width.toFloat(), bitmap.height.toFloat()), sampling(paint), paint?.sk(), true)
    c.restore()
  }

  fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) = drawRect(bitmap, src, dst.sk(), paint)
  fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) = drawRect(bitmap, src, dst.sk(), paint)

  private fun drawRect(bitmap: Bitmap, src: Rect?, dst: SkRect, paint: Paint?) {
    val s = src?.sk() ?: SkRect.makeWH(bitmap.width.toFloat(), bitmap.height.toFloat())
    c.drawImageRect(Image.makeFromBitmap(bitmap.sk), s, dst, sampling(paint), paint?.sk(), true)
  }

  fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
    c.drawRect(SkRect.makeLTRB(left, top, right, bottom), paint.sk())
  }

  fun drawRect(r: RectF, paint: Paint) = drawRect(r.left, r.top, r.right, r.bottom, paint)

  fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
    c.drawLine(startX, startY, stopX, stopY, paint.sk())
  }

  internal val sk: SkCanvas get() = c

  fun save(): Int = c.save()
  fun restore() { c.restore() }
  fun translate(dx: Float, dy: Float) { c.translate(dx, dy) }
  fun scale(sx: Float, sy: Float) { c.scale(sx, sy) }
  fun rotate(degrees: Float) { c.rotate(degrees) }

  private fun sampling(paint: Paint?) =
    FilterMipmap(if (paint?.isFilterBitmap == false) FilterMode.NEAREST else FilterMode.LINEAR, MipmapMode.NONE)
}
