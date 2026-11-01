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

  fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
    image.getRGB(x, y, width, height, pixels, offset, stride)
  }

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

    fun createBitmap(colors: IntArray, width: Int, height: Int, config: Config): Bitmap {
      val img = BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
      img.setRGB(0, 0, width, height, colors, 0, width)
      return Bitmap(img)
    }

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

/**
 *  `BitmapFactory`ِ اندروید روی کامپیوتر — با همان قراردادِ اندروید (IMG-PRO-09):
 *
 *  • `inJustDecodeBounds` فقط **سرآیند** را می‌خواند (`ImageReader.getWidth/Height`)؛
 *    پیش‌تر کلِ تصویر رمزگشایی می‌شد و سنجشِ «بیش از اندازه بزرگ» ِ `ImageBank.prepare`
 *    پس از پر شدنِ حافظه می‌آمد.
 *  • `inSampleSize` تصویرِ بزرگ را **هنگامِ خواندن** نمونه می‌گیرد (`setSourceSubsampling`)،
 *    پس آرایهٔ تمام‌اندازه ساخته نمی‌شود. تا `SAMPLE_FULL_BUDGET` همان راهِ پیشین
 *    (خواندنِ کامل + bicubic) می‌ماند تا کیفیتِ عکسِ معمولی عوض نشود.
 *  • رمزگشاییِ بی نمونه‌گیری بیش از `MAX_FULL_PIXELS` ⇒ null، مثلِ «خوانده نشد» (به‌جای
 *    خالی کردنِ heapِ کلِ برنامه). همهٔ خطاها مثلِ پیش null می‌دهند.
 *  `outWidth/outHeight`: با `inJustDecodeBounds` ابعادِ اصل، وگرنه ابعادِ خروجی (همان اندروید).
 */
object BitmapFactory {
  class Options {
    var inJustDecodeBounds = false
    var inSampleSize = 1
    var outWidth = 0
    var outHeight = 0
  }

  /** ۶۴ مگاپیکسل ≈ ۲۵۶ مگابایت در ARGB — بیشتر، بی `inSampleSize`، خوانده نمی‌شود */
  const val MAX_FULL_PIXELS = 64_000_000L
  /** تا این‌جا کوچک‌کردن مثلِ پیش: خواندنِ کامل و bicubic؛ بیشتر ⇒ نمونه‌گیری هنگامِ خواندن */
  const val SAMPLE_FULL_BUDGET = 24_000_000L

  fun decodeStream(stream: InputStream?): Bitmap? = decodeStream(stream, null, null)

  fun decodeStream(stream: InputStream?, pad: Any?, opts: Options?): Bitmap? {
    if (stream == null) return null
    return runCatching {
      ImageIO.createImageInputStream(stream)?.use { iis -> read(iis, opts) }
    }.getOrNull()
  }

  private fun read(iis: javax.imageio.stream.ImageInputStream, opts: Options?): Bitmap? {
    val readers = ImageIO.getImageReaders(iis)
    if (!readers.hasNext()) return null
    val reader = readers.next()
    try {
      reader.setInput(iis, true, true)
      val w = reader.getWidth(0)
      val h = reader.getHeight(0)
      if (w <= 0 || h <= 0) return null
      if (opts != null) { opts.outWidth = w; opts.outHeight = h }
      if (opts?.inJustDecodeBounds == true) return null
      val s = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
      //  دورهٔ نمونه‌گیریِ هنگامِ خواندن: توانِ ۲، هرگز بیش از s، تا حافظه زیرِ بودجه بماند
      var p = 1
      while (p * 2 <= s && pixels(w, h, p) > SAMPLE_FULL_BUDGET) p *= 2
      if (pixels(w, h, p) > MAX_FULL_PIXELS) return null
      val param = reader.defaultReadParam
      if (p > 1) param.setSourceSubsampling(p, p, 0, 0)
      var bmp = Bitmap(reader.read(0, param))
      if (s > 1 && p < s) bmp = Bitmap.createScaledBitmap(bmp, (w / s).coerceAtLeast(1), (h / s).coerceAtLeast(1), true)
      if (opts != null) { opts.outWidth = bmp.width; opts.outHeight = bmp.height }
      return bmp
    } finally {
      reader.dispose()
    }
  }

  /** پیکسل‌های خروجیِ نمونه‌گیری با دورهٔ p (گرد به بالا، همان `setSourceSubsampling`) */
  private fun pixels(w: Int, h: Int, p: Int): Long = ((w + p - 1L) / p) * ((h + p - 1L) / p)

  fun decodeFile(path: String?): Bitmap? = decodeFile(path, null)

  fun decodeFile(path: String?, opts: Options?): Bitmap? =
    path?.let { java.io.File(it) }?.takeIf { it.isFile }?.inputStream()?.use { decodeStream(it, null, opts) }

  fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? =
    decodeStream(data.inputStream(offset, length))

  fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? =
    decodeStream(data.inputStream(offset, length), null, opts)
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
