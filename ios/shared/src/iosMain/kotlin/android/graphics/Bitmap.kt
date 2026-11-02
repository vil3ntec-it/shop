package android.graphics

import java.io.InputStream
import java.io.OutputStream
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Bitmap as SkBitmap

/*
 *  shimِ iOS ِ `android.graphics.Bitmap`/`BitmapFactory` روی Skia (همان موتورِ رسمِ Compose در iOS) —
 *  گامِ ۲-پ. فقط آنچه عکسِ کالا و کاشیِ صندوق می‌خواهند: خواندنِ JPEG/PNG/WebP، اندازهٔ کوچک‌شده،
 *  پیکسل‌ها (ARGB ِ بی پیش‌ضرب، مثلِ اندروید)، و فشرده‌سازیِ JPEG/PNG/WebP. JPEG روی زمینهٔ سفید
 *  (بی آلفا)، مثلِ shimِ کامپیوتر.
 */
class Bitmap internal constructor(internal val sk: SkBitmap) {
  val width: Int get() = sk.width
  val height: Int get() = sk.height
  var isRecycled: Boolean = false
    private set
  val config: Config get() = Config.ARGB_8888

  fun recycle() { isRecycled = true }

  fun getPixel(x: Int, y: Int): Int = sk.getColor(x, y)

  fun setPixel(x: Int, y: Int, color: Int) {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    px[y * width + x] = color
    sk.installPixels(argbInfo(width, height), toBytes(px), width * 4)
  }

  /** ARGB ِ بی پیش‌ضرب، سطربه‌سطر، مثلِ `getPixels` ِ اندروید */
  fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
    val bytes = sk.readPixels(argbInfo(width, height), width * 4, x, y) ?: return
    for (r in 0 until height) for (c in 0 until width) {
      val i = (r * width + c) * 4
      pixels[offset + r * stride + c] = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8) or
        ((bytes[i + 2].toInt() and 0xFF) shl 16) or ((bytes[i + 3].toInt() and 0xFF) shl 24)
    }
  }

  fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean {
    val q = quality.coerceIn(0, 100)
    val data = when (format) {
      CompressFormat.PNG -> Image.makeFromBitmap(sk).encodeToData(EncodedImageFormat.PNG, 100)
      CompressFormat.JPEG -> {
        val flat = blank(width, height).also { c ->
          Canvas(c.sk).apply { clear(0xFFFFFFFF.toInt()); drawImage(Image.makeFromBitmap(sk), 0f, 0f) }
        }
        Image.makeFromBitmap(flat.sk).encodeToData(EncodedImageFormat.JPEG, q)
      }
      else -> Image.makeFromBitmap(sk).encodeToData(EncodedImageFormat.WEBP, if (format == CompressFormat.WEBP_LOSSLESS) 100 else q)
    } ?: return false
    out.write(data.bytes)
    return true
  }

  fun copy(config: Config, isMutable: Boolean): Bitmap = scaled(this, width, height)

  enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }
  enum class Config { ARGB_8888, RGB_565, ALPHA_8 }

  companion object {
    fun createBitmap(width: Int, height: Int, config: Config): Bitmap = blank(width, height)

    fun createBitmap(colors: IntArray, width: Int, height: Int, config: Config): Bitmap {
      val b = blank(width, height)
      b.sk.installPixels(argbInfo(width, height), toBytes(colors.copyOf(width * height)), width * 4)
      return b
    }

    fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap = scaled(src, dstWidth, dstHeight)

    internal fun blank(w: Int, h: Int): Bitmap =
      Bitmap(SkBitmap().also { it.allocPixels(ImageInfo.makeN32Premul(w.coerceAtLeast(1), h.coerceAtLeast(1))); it.erase(0) })

    internal fun scaled(src: Bitmap, w: Int, h: Int): Bitmap {
      val dst = blank(w, h)
      Canvas(dst.sk).drawImageRect(
        Image.makeFromBitmap(src.sk),
        Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
        Rect.makeWH(w.toFloat(), h.toFloat()),
        FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
        null,
        true,
      )
      return dst
    }

    internal fun argbInfo(w: Int, h: Int) = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)

    internal fun toBytes(px: IntArray): ByteArray {
      val b = ByteArray(px.size * 4)
      for (i in px.indices) {
        val v = px[i]
        b[i * 4] = v.toByte(); b[i * 4 + 1] = (v shr 8).toByte(); b[i * 4 + 2] = (v shr 16).toByte(); b[i * 4 + 3] = (v ushr 24).toByte()
      }
      return b
    }
  }
}

object BitmapFactory {
  class Options {
    var inJustDecodeBounds: Boolean = false
    var inSampleSize: Int = 1
    var inPreferredConfig: Bitmap.Config? = null
    var inMutable: Boolean = false
    var outWidth: Int = -1
    var outHeight: Int = -1
    var outMimeType: String? = null
  }

  fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = decodeByteArray(data, offset, length, null)

  /** همان قراردادِ اندروید: خوانده نشد ⇐ null؛ `inJustDecodeBounds` ⇐ فقط اندازه و null */
  fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
    val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
    val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null.also { opts?.outWidth = -1; opts?.outHeight = -1 }
    opts?.outWidth = image.width
    opts?.outHeight = image.height
    if (opts?.inJustDecodeBounds == true) return null
    val sample = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
    val w = (image.width / sample).coerceAtLeast(1)
    val h = (image.height / sample).coerceAtLeast(1)
    val full = Bitmap.blank(image.width, image.height)
    if (!image.readPixels(full.sk, 0, 0)) return null
    return if (sample == 1) full else Bitmap.scaled(full, w, h)
  }

  fun decodeFile(pathName: String): Bitmap? = decodeFile(pathName, null)

  fun decodeFile(pathName: String, opts: Options?): Bitmap? {
    val bytes = runCatching { java.io.File(pathName).readBytes() }.getOrNull() ?: return null
    return decodeByteArray(bytes, 0, bytes.size, opts)
  }

  fun decodeStream(input: InputStream?): Bitmap? = decodeStream(input, null, null)

  fun decodeStream(input: InputStream?, outPadding: Any?, opts: Options?): Bitmap? {
    val bytes = input?.readBytes() ?: return null
    return decodeByteArray(bytes, 0, bytes.size, opts)
  }
}
