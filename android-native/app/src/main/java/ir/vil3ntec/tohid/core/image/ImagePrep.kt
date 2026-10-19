package ir.vil3ntec.tohid.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 *  آماده‌سازیِ عکس پیش از فرستادن به بانکِ تصاویر (v3 ۳۴.۳، ۳۶.۹) — منطقِ خالص،
 *  بی `android.*`؛ گوشی و کامپیوتر هر دو همین را می‌گیرند. همتای وب:
 *  `license/image-bank.js` (`prepare`).
 *
 *  ── سقف‌ها همان سرور (`server/src/lib/image-bank.js`، `DERIV`) ─────────
 *    display  ≤۱۲۰۰ پیکسل و ≤۶۰۰KB — همان که «اصل» و «نمایش» می‌شود
 *    thumb    ≤۴۰۰ پیکسل و ≤۱۵۰KB، با همان نسبت (مرزِ ۲٪ِ سرور)
 *  سرور نسخهٔ کوچکِ بزرگ‌تر از اصل، نسبتِ ناجور یا EXIF orientation > ۱ را
 *  رد می‌کند؛ پس چرخش **روی پیکسل‌ها** می‌نشیند (`orient`) و هیچ EXIFی
 *  نمی‌ماند (`JpegMeta.strip`).
 *  ──────────────────────────────────────────────────────────────────
 */
object ImagePrep {
  const val DISPLAY_SIDE = 1200
  const val DISPLAY_BYTES = 600 * 1024
  const val THUMB_SIDE = 400
  const val THUMB_BYTES = 150 * 1024
  /** کوچک‌تر ⇒ هشدارِ «عکس کوچک است» (همان `MIN_SIDE`ِ سرور) */
  const val MIN_SIDE = 300
  /** بیشتر ⇒ رد پیش از رمزگشاییِ کامل (بمبِ فشرده) */
  const val MAX_PIXELS = 60_000_000L
  /** پله‌های کیفیتِ JPEG؛ اگر نشد، ۸۵٪ کوچک‌تر و دوباره */
  val QUALITIES = intArrayOf(86, 80, 72, 64, 56)

  data class Size(val w: Int, val h: Int) {
    val ratio: Double get() = w.toDouble() / h
  }

  /** بزرگ‌ترین ضلع ≤ side، نسبت ثابت، هرگز بزرگ‌تر از اصل */
  fun fit(w: Int, h: Int, side: Int): Size {
    require(w > 0 && h > 0) { "ابعادِ عکس صفر است" }
    val r = min(1.0, side.toDouble() / max(w, h))
    return Size(max(1, (w * r).roundToInt()), max(1, (h * r).roundToInt()))
  }

  /** EXIF 5..8 ⇒ عرض و ارتفاع جابه‌جا */
  fun oriented(w: Int, h: Int, orientation: Int): Size =
    if (orientation in 5..8) Size(h, w) else Size(w, h)

  /**
   *  `inSampleSize`ِ رمزگشایی: بزرگ‌ترین توانِ ۲ که ضلعِ بزرگ هنوز ≥ side
   *  بماند — حافظهٔ گوشی برای عکسِ ۴۸ مگاپیکسلی پر نمی‌شود و کوچک‌کردنِ
   *  نهایی از تصویرِ بزرگ‌تر انجام می‌شود (لبه‌ها دندانه‌دار نمی‌شوند).
   */
  fun sampleSize(w: Int, h: Int, side: Int): Int {
    var s = 1
    while (max(w, h) / (s * 2) >= side) s *= 2
    return s
  }

  /** نسبتِ دو اندازه در مرزِ سرور (۲٪) */
  fun aspectOk(a: Size, b: Size, tolerance: Double = 0.02): Boolean =
    kotlin.math.abs(a.ratio - b.ratio) / b.ratio <= tolerance

  fun tooManyPixels(w: Int, h: Int): Boolean = w.toLong() * h > MAX_PIXELS

  /**
   *  چرخش/آینهٔ EXIF روی پیکسل‌های ARGB ⇒ تصویرِ ایستاده (orientation ۱).
   *  جدولِ EXIF: ۲ آینهٔ افقی · ۳ ۱۸۰° · ۴ آینهٔ عمودی · ۵ ترانهاده ·
   *  ۶ ۹۰° ساعت‌گرد · ۷ ترانهادهٔ معکوس · ۸ ۹۰° پادساعت‌گرد.
   */
  fun orient(px: IntArray, w: Int, h: Int, orientation: Int): Pair<IntArray, Size> {
    require(px.size >= w * h) { "پیکسل کم است" }
    if (orientation !in 2..8) return px to Size(w, h)
    val out = oriented(w, h, orientation)
    val dst = IntArray(out.w * out.h)
    for (y in 0 until out.h) {
      val row = y * out.w
      for (x in 0 until out.w) {
        val (sx, sy) = when (orientation) {
          2 -> (w - 1 - x) to y
          3 -> (w - 1 - x) to (h - 1 - y)
          4 -> x to (h - 1 - y)
          5 -> y to x
          6 -> y to (h - 1 - x)
          7 -> (w - 1 - y) to (h - 1 - x)
          else -> (w - 1 - y) to x          // ۸
        }
        dst[row + x] = px[sy * w + sx]
      }
    }
    return dst to out
  }

  /**
   *  کدگذاری زیرِ سقفِ حجم: پله‌های کیفیت، بعد ۸۵٪ کوچک‌تر (حداکثر شش دور).
   *  `encode(size, quality)` کارِ سکوست (Bitmap.compress یا ImageIO).
   */
  fun encodeUnder(start: Size, maxBytes: Int, encode: (Size, Int) -> ByteArray): Pair<ByteArray, Size> {
    var size = start
    repeat(6) {
      for (q in QUALITIES) {
        val bytes = encode(size, q)
        if (bytes.isNotEmpty() && bytes.size <= maxBytes) return bytes to size
      }
      size = Size(max(1, (size.w * 0.85).roundToInt()), max(1, (size.h * 0.85).roundToInt()))
    }
    throw IllegalStateException("عکس به اندازهٔ لازم کوچک نشد")
  }
}
