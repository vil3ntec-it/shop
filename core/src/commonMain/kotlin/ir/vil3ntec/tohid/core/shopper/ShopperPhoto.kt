package ir.vil3ntec.tohid.core.shopper

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 *  عکسِ گفت‌وگو پیش از فرستادن (CHAT-05/06) — خالص و بی سکو، برای اندروید و آیفون از یک سورس.
 *  همان قاعده‌های `ChatImage` ِ برنامهٔ دکان (`android-native/.../core/chat/ChatImage.kt`) بی `java.*`:
 *
 *    ۱. جهتِ EXIF از خودِ بایت‌های JPEG ([orientationOf])؛ `BitmapFactory` ِ اندروید آن را اعمال نمی‌کند و عکسِ
 *       عمودیِ دوربین خوابیده می‌رسید؛
 *    ۲. پیکسل‌ها پیش از فشرده‌سازی چرخانده/آینه می‌شوند ([orient])؛
 *    ۳. شفافیت روی زمینهٔ **سفید** ([flattenOnWhite]؛ JPEG شفافیت ندارد و اندروید سیاه می‌گذاشت)؛
 *    ۴. ابعاد ≤ [MAIN_PX] و پیش‌نمایش ≤ [THUMB_PX] ([fit])؛ JPEG ِ تازه از روی پیکسل ⇒ EXIF (مکان، دستگاه، زمان)
 *       دیگر نیست — سرور هم دوباره پاکش می‌کند (`chatMedia.stripImageMetadata`).
 *
 *  فشرده‌سازی کارِ سکوست (`PhotoAndroid.kt`، آیفون: `PhotoIos.kt`)؛ سقفِ سرور ۱٫۵ مگابایت (عکس) و ۶۴ کیلوبایت (پیش‌نمایش).
 */
object ShopperPhoto {
  const val MAIN_PX = 1600
  const val THUMB_PX = 320
  const val MAIN_QUALITY = 82
  const val THUMB_QUALITY = 60
  const val MIN_QUALITY = 40
  /** همان `CHAT_IMAGE_MAX_KB` / `CHAT_THUMB_MAX_KB` ِ پیش‌فرضِ سرور */
  const val MAIN_MAX_BYTES = 1536 * 1024
  const val THUMB_MAX_BYTES = 64 * 1024

  /** ابعادِ کوچک‌شده با همان نسبت؛ هرگز بزرگ‌تر از اصل */
  fun fit(w: Int, h: Int, maxEdge: Int): Pair<Int, Int> {
    val ww = max(1, w)
    val hh = max(1, h)
    val s = min(1.0, maxEdge.toDouble() / max(ww, hh))
    return max(1, (ww * s).roundToInt()) to max(1, (hh * s).roundToInt())
  }

  /** پله‌های کیفیت تا بایت‌ها زیرِ سقف بروند (۸۲، ۷۲، … ۴۰) */
  fun qualities(start: Int): List<Int> = generateSequence(start) { it - 10 }.takeWhile { it >= MIN_QUALITY }.toList().ifEmpty { listOf(MIN_QUALITY) }

  /** جهتِ EXIF (۱ تا ۸) از آغازِ یک JPEG؛ نبود یا خراب ⇒ ۱ */
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
      if (marker == 0xE1 && start + 6 <= jpeg.size && ascii(jpeg, start, "Exif") && jpeg[start + 4] == 0.toByte() && jpeg[start + 5] == 0.toByte()) {
        return try { tiffOrientation(jpeg, start + 6, min(jpeg.size, start + len - 2)) } catch (_: Exception) { 1 }
      }
      i += 2 + len
    }
    return 1
  }

  private fun tiffOrientation(b: ByteArray, t: Int, end: Int): Int {
    val le = when {
      ascii(b, t, "II") -> true
      ascii(b, t, "MM") -> false
      else -> return 1
    }
    val ifd = t + u32(b, t + 4, le)
    if (ifd < t || ifd + 2 > end) return 1
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
        val nx: Int
        val ny: Int
        when (orientation) {
          2 -> { nx = w - 1 - x; ny = y }                 // آینهٔ افقی
          3 -> { nx = w - 1 - x; ny = h - 1 - y }         // ۱۸۰°
          4 -> { nx = x; ny = h - 1 - y }                 // آینهٔ عمودی
          5 -> { nx = y; ny = x }                         // ترانهاده
          6 -> { nx = h - 1 - y; ny = x }                 // ۹۰° ساعت‌گرد
          7 -> { nx = h - 1 - y; ny = w - 1 - x }         // ترانهادهٔ وارونه
          else -> { nx = y; ny = w - 1 - x }              // ۸: ۹۰° پادساعت‌گرد
        }
        out[ny * ow + nx] = px[y * w + x]
      }
    }
    return Triple(out, ow, oh)
  }

  /** پیکسلِ نیمه‌شفاف روی زمینهٔ سفید — در جا، روی همان آرایه */
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

  /** نوعِ عکس از بایت (همان شناساییِ سرور برای عکس): jpeg · png · webp؛ دیگر ⇒ null (سرور هم ۴۱۵ می‌دهد) */
  fun mimeOf(b: ByteArray): String? = when {
    b.size >= 3 && u8(b, 0) == 0xFF && u8(b, 1) == 0xD8 && u8(b, 2) == 0xFF -> "image/jpeg"
    b.size >= 8 && u8(b, 0) == 0x89 && ascii(b, 1, "PNG") -> "image/png"
    b.size >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP") -> "image/webp"
    else -> null
  }

  private fun ascii(b: ByteArray, at: Int, s: String): Boolean =
    at >= 0 && at + s.length <= b.size && s.indices.all { b[at + it].toInt() == s[it].code }

  private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
  private fun u16(b: ByteArray, i: Int, le: Boolean) =
    if (le) u8(b, i) or (u8(b, i + 1) shl 8) else (u8(b, i) shl 8) or u8(b, i + 1)
  private fun u32(b: ByteArray, i: Int, le: Boolean) =
    if (le) u16(b, i, true) or (u16(b, i + 2, true) shl 16) else (u16(b, i, false) shl 16) or u16(b, i + 2, false)
}
