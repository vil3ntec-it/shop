package ir.vil3ntec.tohid.core.chat

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 *  عکسِ چت پیش از فرستادن — خالص، بی اندروید (v3 §۳۳.۲ بندِ ۱ و ۲؛ CHAT-05/06).
 *
 *  نه `BitmapFactory` ِ اندروید و نه `ImageIO` ِ کامپیوتر جهتِ EXIF را
 *  اعمال نمی‌کنند؛ عکسِ عمودیِ دوربین خوابیده می‌رسید. پس این‌جا:
 *    ۱. جهت از خودِ بایت‌های JPEG خوانده می‌شود (`orientationOf`)؛
 *    ۲. پیکسل‌ها پیش از فشرده‌سازی چرخانده/آینه می‌شوند (`orient`)؛
 *    ۳. JPEG ِ تازه از روی پیکسل ساخته می‌شود ⇒ EXIF (مکان، دستگاه، زمان)
 *       دیگر وجود ندارد — سرور هم دوباره پاکش می‌کند.
 */
object ChatImage {
  const val MAIN_PX = 1600
  const val THUMB_PX = 320
  const val MAIN_QUALITY = 82
  const val THUMB_QUALITY = 60
  const val MIN_QUALITY = 40

  /** ابعادِ کوچک‌شده با همان نسبت؛ هرگز بزرگ‌تر از اصل. */
  fun fit(w: Int, h: Int, maxEdge: Int): Pair<Int, Int> {
    val W = max(1, w); val H = max(1, h)
    val s = min(1.0, maxEdge.toDouble() / max(W, H))
    return max(1, (W * s).roundToInt()) to max(1, (H * s).roundToInt())
  }

  /** جهتِ EXIF (۱ تا ۸) از آغازِ یک JPEG؛ نبود یا خراب ⇒ ۱. */
  fun orientationOf(jpeg: ByteArray): Int {
    if (jpeg.size < 4 || u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) return 1
    var i = 2
    while (i + 4 <= jpeg.size) {
      if (u8(jpeg, i) != 0xFF) return 1
      val marker = u8(jpeg, i + 1)
      if (marker == 0xDA || marker == 0xD9) return 1
      val len = u16(jpeg, i + 2, false)
      if (len < 2) return 1
      val start = i + 4
      if (marker == 0xE1 && start + 6 <= jpeg.size && String(jpeg, start, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
        return runCatching { tiffOrientation(jpeg, start + 6, min(jpeg.size, start + len - 2)) }.getOrDefault(1)
      }
      i += 2 + len
    }
    return 1
  }

  private fun tiffOrientation(b: ByteArray, t: Int, end: Int): Int {
    val le = when (String(b, t, 2, Charsets.ISO_8859_1)) { "II" -> true; "MM" -> false; else -> return 1 }
    val ifd = t + u32(b, t + 4, le)
    if (ifd + 2 > end) return 1
    val n = u16(b, ifd, le)
    for (k in 0 until n) {
      val e = ifd + 2 + k * 12
      if (e + 12 > end) return 1
      if (u16(b, e, le) == 0x0112) {
        val v = u16(b, e + 8, le)
        return if (v in 1..8) v else 1
      }
    }
    return 1
  }

  /** پیکسل‌های ARGB ِ سطری ⇐ همان تصویر با جهتِ درست. @return (پیکسل‌ها، پهنا، بلندی) */
  fun orient(px: IntArray, w: Int, h: Int, orientation: Int): Triple<IntArray, Int, Int> {
    if (orientation !in 2..8) return Triple(px, w, h)
    val swap = orientation >= 5
    val ow = if (swap) h else w
    val oh = if (swap) w else h
    val out = IntArray(ow * oh)
    for (y in 0 until h) {
      for (x in 0 until w) {
        val (nx, ny) = when (orientation) {
          2 -> (w - 1 - x) to y               // آینهٔ افقی
          3 -> (w - 1 - x) to (h - 1 - y)     // ۱۸۰°
          4 -> x to (h - 1 - y)               // آینهٔ عمودی
          5 -> y to x                         // ترانهاده
          6 -> (h - 1 - y) to x               // ۹۰° ساعت‌گرد
          7 -> (h - 1 - y) to (w - 1 - x)     // ترانهادهٔ وارونه
          else -> y to (w - 1 - x)            // ۸: ۹۰° پادساعت‌گرد
        }
        out[ny * ow + nx] = px[y * w + x]
      }
    }
    return Triple(out, ow, oh)
  }

  /**
   *  JPEG شفافیت ندارد: پیکسلِ نیمه‌شفاف روی زمینهٔ **سفید** نشانده می‌شود
   *  (اندروید بی این کار زمینهٔ سیاه می‌گذاشت). در جا، روی همان آرایه.
   */
  fun flattenOnWhite(px: IntArray): IntArray {
    for (i in px.indices) {
      val c = px[i]
      val a = c ushr 24
      if (a == 255) continue
      fun ch(shift: Int) = (((c shr shift) and 0xFF) * a + 255 * (255 - a)) / 255
      px[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    return px
  }

  private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
  private fun u16(b: ByteArray, i: Int, le: Boolean) =
    if (le) u8(b, i) or (u8(b, i + 1) shl 8) else (u8(b, i) shl 8) or u8(b, i + 1)
  private fun u32(b: ByteArray, i: Int, le: Boolean) =
    if (le) u16(b, i, true) or (u16(b, i + 2, true) shl 16) else (u16(b, i, false) shl 16) or u16(b, i + 2, false)
}
