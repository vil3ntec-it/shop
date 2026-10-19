package ir.vil3ntec.tohid.core.image

import java.io.ByteArrayOutputStream

/**
 *  فرادادهٔ JPEG — خواندنِ orientation و برداشتنِ EXIF/GPS/XMP/IPTC/توضیح،
 *  بی هیچ کتابخانه (همتای `server/src/lib/image-bytes.js`).
 *
 *  ⛔ فقط سرآیند خوانده می‌شود؛ دادهٔ فشرده پس از SOS دست‌نخورده کپی می‌شود.
 *  ⛔ APP0 (JFIF)، APP2 (رنگ‌نمای ICC) و APP14 (Adobe) می‌مانند — بی آن‌ها
 *     رنگ یا رمزگشایی عوض می‌شود. بقیهٔ APPn و COM می‌روند.
 */
object JpegMeta {

  data class Segment(val marker: Int, val start: Int, val end: Int)

  private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
  private fun u16be(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)

  fun isJpeg(b: ByteArray): Boolean = b.size >= 4 && u8(b, 0) == 0xFF && u8(b, 1) == 0xD8

  /** قطعه‌های پیش از SOS (خودِ SOS آخرینِ فهرست است). خراب ⇒ خطا. */
  fun segments(b: ByteArray): List<Segment> {
    require(isJpeg(b)) { "JPEG نیست" }
    val out = ArrayList<Segment>()
    var i = 2
    while (i + 4 <= b.size) {
      require(u8(b, i) == 0xFF) { "نشانگرِ JPEG خراب است" }
      var m = u8(b, i + 1)
      while (m == 0xFF && i + 2 < b.size) { i++; m = u8(b, i + 1) }
      if (m == 0xD8 || m == 0x01 || m in 0xD0..0xD7) { i += 2; continue }
      val len = u16be(b, i + 2)
      require(len >= 2 && i + 2 + len <= b.size) { "طولِ قطعهٔ JPEG خراب است" }
      out += Segment(m, i, i + 2 + len)
      if (m == 0xDA) return out
      i += 2 + len
    }
    throw IllegalArgumentException("JPEG بی دادهٔ تصویر (SOS) تمام شد")
  }

  private fun isExif(b: ByteArray, s: Segment): Boolean =
    s.marker == 0xE1 && s.end - s.start >= 10 &&
      String(b, s.start + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000"

  fun hasExif(b: ByteArray): Boolean = runCatching { segments(b).any { isExif(b, it) } }.getOrDefault(false)

  /** هر قطعهٔ فرادادهٔ حساس (APP1/APP13/COM و دیگر APPnها) */
  fun hasMetadata(b: ByteArray): Boolean = runCatching { segments(b).any { !keep(it.marker) } }.getOrDefault(false)

  private fun keep(m: Int): Boolean = when (m) {
    0xE0, 0xE2, 0xEE -> true                  // JFIF، ICC، Adobe
    in 0xE1..0xEF, 0xFE -> false              // EXIF/XMP، IPTC، توضیح و دیگر APPn
    else -> true                              // DQT، SOF، DHT، DRI، SOS…
  }

  /** orientationِ EXIF (۱..۸)؛ نبود یا خراب ⇒ ۱ */
  fun orientation(b: ByteArray): Int = runCatching {
    val seg = segments(b).firstOrNull { isExif(b, it) } ?: return 1
    val t = seg.start + 10                     // آغازِ TIFF
    val end = seg.end
    if (t + 8 > end) return 1
    val le = when (String(b, t, 2, Charsets.ISO_8859_1)) { "II" -> true; "MM" -> false; else -> return 1 }
    fun r16(i: Int): Int = if (le) u8(b, i) or (u8(b, i + 1) shl 8) else u16be(b, i)
    fun r32(i: Int): Long = if (le) (r16(i).toLong() or (r16(i + 2).toLong() shl 16))
      else ((r16(i).toLong() shl 16) or r16(i + 2).toLong())
    if (r16(t + 2) != 42) return 1
    val ifd = t + r32(t + 4).toInt()
    if (ifd + 2 > end) return 1
    val n = r16(ifd)
    for (k in 0 until n) {
      val e = ifd + 2 + k * 12
      if (e + 12 > end) return 1
      if (r16(e) == 0x0112) {
        val v = r16(e + 8)
        return if (v in 1..8) v else 1
      }
    }
    1
  }.getOrDefault(1)

  /** همان JPEG بی فرادادهٔ حساس؛ دادهٔ تصویر بایت‌به‌بایت همان است. */
  fun strip(b: ByteArray): ByteArray {
    val segs = segments(b)
    val out = ByteArrayOutputStream(b.size)
    out.write(b, 0, 2)                        // SOI
    for (s in segs) {
      if (s.marker == 0xDA) { out.write(b, s.start, b.size - s.start); break }
      if (keep(s.marker)) out.write(b, s.start, s.end - s.start)
    }
    return out.toByteArray()
  }
}
