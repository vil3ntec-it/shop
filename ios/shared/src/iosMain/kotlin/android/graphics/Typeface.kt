package android.graphics

import ir.vil3ntec.tohid.R
import java.net.toByteArray
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface as SkTypeface
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

/*
 *  shimِ iOS ِ `Typeface` روی Skia — قلمِ رسیدِ چاپی. پیش‌فرض همان Vazirmatn ِ بستهٔ برنامه است (رسیدِ فارسی بی
 *  قلمِ فارسی خوانا نیست)؛ `create(…, BOLD)` همان Vazirmatn Bold.
 */
class Typeface internal constructor(internal val sk: SkTypeface?, val style: Int = NORMAL) {
  val isBold: Boolean get() = style and BOLD != 0

  companion object {
    const val NORMAL = 0
    const val BOLD = 1
    const val ITALIC = 2
    const val BOLD_ITALIC = 3

    internal fun fromRes(id: Int, style: Int = NORMAL): Typeface? {
      val (name, ext) = R.file(id)
      val path = NSBundle.mainBundle.pathForResource(name, ofType = ext) ?: return null
      val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray() ?: return null
      return Typeface(FontMgr.default.makeFromData(Data.makeFromBytes(bytes)), style)
    }

    val DEFAULT: Typeface by lazy { fromRes(R.font.vazirmatn_regular) ?: Typeface(null) }
    val DEFAULT_BOLD: Typeface by lazy { fromRes(R.font.vazirmatn_bold, BOLD) ?: Typeface(null, BOLD) }

    fun create(family: Typeface?, style: Int): Typeface =
      if (style and BOLD != 0) DEFAULT_BOLD else (family?.takeIf { !it.isBold } ?: DEFAULT)

    fun defaultFromStyle(style: Int): Typeface = create(null, style)
  }
}
