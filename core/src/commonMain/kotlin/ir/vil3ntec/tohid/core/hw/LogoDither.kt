package ir.vil3ntec.tohid.core.hw

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 *  لوگوی دکان ⇒ تصویرِ یک‌بیتیِ چاپگرِ حرارتی (فاکتورِ نسخهٔ ۲).
 *
 *  ورودی پیکسل‌های ARGB است (رمزگشایی کارِ سکو است: `BitmapFactory` / `ImageIO`)؛ همهٔ
 *  بقیه این‌جاست تا گوشی و کامپیوتر بیت‌به‌بیت یک لوگو چاپ کنند:
 *    ۱) شفاف روی سفید، روشنایی BT.601 (همان `MonoImage.isBlack`)
 *    ۲) کوچک کردن با میانگینِ مساحت تا جعبهٔ لوگو ([box])، بزرگ کردن حداکثر دو برابر
 *    ۳) «خطی»: آستانهٔ ۱۲۸؛ «عکس»: Floyd–Steinberg ِ مارپیچ با روشن‌کردنِ میانه‌ها
 *       (سرِ حرارتی خاکستری را تیره‌تر می‌زند)؛ «خودکار»: اگر ≥ ۹۰٪ِ نقطه‌ها نزدیکِ سیاه
 *       یا سفیدند خطی، وگرنه عکس.
 *  خروجی قطعی است؛ `LogoDitherTest` بیت‌ها را می‌سنجد.
 */
object LogoDither {

  enum class Mode(val key: String) {
    AUTO("auto"), LINE("line"), PHOTO("photo");

    companion object {
      fun of(key: String?): Mode = entries.firstOrNull { it.key == key } ?: AUTO
    }
  }

  /** پهنای جعبه: ۶۰٪ِ کاغذ */
  private const val WIDTH_SHARE = 0.6
  private const val MAX_H_58 = 112
  private const val MAX_H_80 = 144
  private const val MAX_UPSCALE = 2.0
  private const val THRESHOLD = 128
  private const val NEAR = 64
  private const val LINE_SHARE = 0.9
  /** < ۱ یعنی روشن‌ترشدنِ میانه‌ها */
  private const val PHOTO_GAMMA = 0.9

  /** بیشترین پهنا و بلندیِ لوگو روی این کاغذ، به نقطه */
  fun box(paperDots: Int): Pair<Int, Int> {
    val paper = PrinterProfile.paper(paperDots)
    val h = if (paper >= PrinterProfile.WIDTH_80MM) MAX_H_80 else MAX_H_58
    return (paper * WIDTH_SHARE).toInt() to h
  }

  /** پیکسل‌های چرخیده طبقِ برچسبِ EXIF (۱..۸) — رمزگشای سکو برچسب را اعمال نمی‌کند */
  class Oriented(val argb: IntArray, val width: Int, val height: Int)

  fun orient(argb: IntArray, width: Int, height: Int, orientation: Int): Oriented {
    if (orientation !in 2..8) return Oriented(argb, width, height)
    val swap = orientation >= 5
    val w = if (swap) height else width
    val h = if (swap) width else height
    val out = IntArray(w * h)
    for (y in 0 until height) for (x in 0 until width) {
      val (nx, ny) = when (orientation) {
        2 -> (width - 1 - x) to y
        3 -> (width - 1 - x) to (height - 1 - y)
        4 -> x to (height - 1 - y)
        5 -> y to x
        6 -> (height - 1 - y) to x
        7 -> (height - 1 - y) to (width - 1 - x)
        else -> y to (width - 1 - x)
      }
      out[ny * w + nx] = argb[y * width + x]
    }
    return Oriented(out, w, h)
  }

  fun toMono(argb: IntArray, width: Int, height: Int, paperDots: Int, mode: Mode = Mode.AUTO): MonoImage {
    require(width > 0 && height > 0 && argb.size >= width * height) { "پیکسلِ لوگو کم است" }
    val (maxW, maxH) = box(paperDots)
    val scale = min(min(maxW.toDouble() / width, maxH.toDouble() / height), MAX_UPSCALE)
    val tw = max(1, (width * scale).roundToInt()).coerceAtMost(maxW)
    val th = max(1, (height * scale).roundToInt()).coerceAtMost(maxH)
    val source = luma(argb, width, height)
    //  نوعِ لوگو از خودِ عکس، نه از نسخهٔ کوچک‌شده (میانگین‌گیری لبه‌ها را خاکستری می‌کند)
    val chosen = if (mode == Mode.AUTO) detect(source) else mode
    val gray = resample(source, width, height, tw, th)
    return if (chosen == Mode.LINE) threshold(gray, tw, th) else dither(gray, tw, th)
  }

  /** روشنایی ۰..۲۵۵، شفاف روی سفید */
  private fun luma(argb: IntArray, w: Int, h: Int): DoubleArray = DoubleArray(w * h) { i ->
    val p = argb[i]
    val a = (p ushr 24) and 0xFF
    val lum = 0.299 * ((p ushr 16) and 0xFF) + 0.587 * ((p ushr 8) and 0xFF) + 0.114 * (p and 0xFF)
    (lum * a + 255.0 * (255 - a)) / 255.0
  }

  /** میانگینِ مساحت: هر نقطهٔ مقصد میانگینِ وزن‌دارِ پیکسل‌هایی است که رویش می‌افتند */
  private fun resample(src: DoubleArray, sw: Int, sh: Int, tw: Int, th: Int): DoubleArray {
    val fx = sw.toDouble() / tw
    val fy = sh.toDouble() / th
    val out = DoubleArray(tw * th)
    for (ty in 0 until th) {
      val y0 = ty * fy
      val y1 = y0 + fy
      for (tx in 0 until tw) {
        val x0 = tx * fx
        val x1 = x0 + fx
        var sum = 0.0
        var area = 0.0
        var y = y0.toInt()
        while (y < y1 && y < sh) {
          val wy = min(y1, y + 1.0) - max(y0, y.toDouble())
          var x = x0.toInt()
          while (x < x1 && x < sw) {
            val wx = min(x1, x + 1.0) - max(x0, x.toDouble())
            val wgt = wx * wy
            sum += src[y * sw + x] * wgt
            area += wgt
            x++
          }
          y++
        }
        out[ty * tw + tx] = if (area > 0) sum / area else 255.0
      }
    }
    return out
  }

  private fun detect(gray: DoubleArray): Mode {
    val near = gray.count { it < NEAR || it > 255 - NEAR }
    return if (near >= gray.size * LINE_SHARE) Mode.LINE else Mode.PHOTO
  }

  private fun threshold(gray: DoubleArray, w: Int, h: Int): MonoImage {
    val out = MonoImage(w, h)
    for (y in 0 until h) for (x in 0 until w) if (gray[y * w + x] < THRESHOLD) out[x, y] = true
    return out
  }

  /** Floyd–Steinberg، سطرِ زوج چپ‌به‌راست و فرد راست‌به‌چپ */
  private fun dither(gray: DoubleArray, w: Int, h: Int): MonoImage {
    val g = DoubleArray(gray.size) { 255.0 * (gray[it] / 255.0).coerceIn(0.0, 1.0).pow(PHOTO_GAMMA) }
    val out = MonoImage(w, h)
    for (y in 0 until h) {
      val ltr = y % 2 == 0
      val dir = if (ltr) 1 else -1
      var x = if (ltr) 0 else w - 1
      repeat(w) {
        val i = y * w + x
        val old = g[i]
        val black = old < THRESHOLD
        if (black) out[x, y] = true
        val err = old - if (black) 0.0 else 255.0
        fun spread(dx: Int, dy: Int, k: Double) {
          val nx = x + dx * dir
          val ny = y + dy
          if (nx in 0 until w && ny < h) g[ny * w + nx] += err * k
        }
        spread(1, 0, 7.0 / 16)
        spread(-1, 1, 3.0 / 16)
        spread(0, 1, 5.0 / 16)
        spread(1, 1, 1.0 / 16)
        x += dir
      }
    }
    return out
  }
}
