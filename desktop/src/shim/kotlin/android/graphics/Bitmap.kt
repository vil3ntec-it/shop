package android.graphics

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 *  `Bitmap`ِ اندروید روی کامپیوتر — پوسته‌ای روی `BufferedImage`.
 *
 *  عکسِ کالا، نشانِ حساب و تصویرِ فاکتور همه از همین می‌گذرند؛ پس
 *  `PhotoStore` و `ThermalPrinter` همان فایل‌های اندروید‌اند.
 */
class Bitmap(val image: BufferedImage) {
  val width: Int get() = image.width
  val height: Int get() = image.height
  val isRecycled: Boolean get() = false

  fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)
  fun setPixel(x: Int, y: Int, color: Int) = image.setRGB(x, y, color)
  fun recycle() {}

  fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean {
    val (img, name) = when (format) {
      CompressFormat.PNG -> image to "png"
      else -> {
        //  JPEG کانالِ شفافیت نمی‌پذیرد
        val rgb = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply {
          color = java.awt.Color.WHITE; fillRect(0, 0, width, height)
          drawImage(image, 0, 0, null); dispose()
        }
        rgb to "jpg"
      }
    }
    if (name == "jpg") {
      val writer = ImageIO.getImageWritersByFormatName("jpg").next()
      val param = writer.defaultWriteParam.apply {
        compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
        compressionQuality = (quality.coerceIn(1, 100) / 100f)
      }
      ImageIO.createImageOutputStream(out).use { ios ->
        writer.output = ios
        writer.write(null, javax.imageio.IIOImage(img, null, null), param)
        writer.dispose()
      }
      return true
    }
    return ImageIO.write(img, name, out)
  }

  enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }
  enum class Config { ARGB_8888, RGB_565, ALPHA_8 }

  companion object {
    fun createBitmap(width: Int, height: Int, config: Config): Bitmap =
      Bitmap(BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))

    fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap {
      val out = BufferedImage(w.coerceAtLeast(1), h.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
      out.createGraphics().apply {
        if (filter) {
          setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        }
        drawImage(src.image, 0, 0, w, h, null)
        dispose()
      }
      return Bitmap(out)
    }
  }
}

object BitmapFactory {
  class Options {
    var inJustDecodeBounds = false
    var inSampleSize = 1
    var outWidth = 0
    var outHeight = 0
  }

  fun decodeStream(stream: InputStream?): Bitmap? =
    stream?.let { runCatching { ImageIO.read(it) }.getOrNull() }?.let(::Bitmap)

  fun decodeStream(stream: InputStream?, pad: Any?, opts: Options?): Bitmap? {
    val bmp = decodeStream(stream) ?: return null
    if (opts != null) { opts.outWidth = bmp.width; opts.outHeight = bmp.height }
    if (opts?.inJustDecodeBounds == true) return null
    val s = opts?.inSampleSize?.coerceAtLeast(1) ?: 1
    return if (s > 1) Bitmap.createScaledBitmap(bmp, bmp.width / s, bmp.height / s, true) else bmp
  }

  fun decodeFile(path: String?): Bitmap? =
    path?.let { runCatching { ImageIO.read(java.io.File(it)) }.getOrNull() }?.let(::Bitmap)

  fun decodeFile(path: String?, opts: Options?): Bitmap? =
    path?.let { java.io.File(it) }?.takeIf { it.isFile }?.inputStream()?.use { decodeStream(it, null, opts) }

  fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? =
    decodeStream(data.inputStream(offset, length))
}

object Color {
  const val BLACK = -0x1000000
  const val WHITE = -0x1
  const val TRANSPARENT = 0
  fun alpha(c: Int) = c ushr 24
  fun red(c: Int) = (c shr 16) and 0xFF
  fun green(c: Int) = (c shr 8) and 0xFF
  fun blue(c: Int) = c and 0xFF
  fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
}
