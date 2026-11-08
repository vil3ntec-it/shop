package ir.vil3ntec.tohid.core.image

import ir.vil3ntec.tohid.core.shopper.ShopperPhoto
import kotlin.math.max
import kotlin.math.min

/**
 *  نسخهٔ کارتِ ویترین برای عکسِ کالا (WEB-15، مهاجرتِ ۳۵۰ ِ سرور) — خالص و بی سکو: گوشی، کامپیوتر و آیفون از یک سورس.
 *
 *  کارتِ ویترین تا امروز همان عکسِ همگام‌شدهٔ ۸۰۰ پیکسلی (~۳۰KB) را می‌گرفت و روی 2G LCP ِ خانه ~۹٫۷ ثانیه بود. سرور
 *  رمزگشای تصویر ندارد (`server/src/lib/image-pipeline.js`: `resize` = missing)، پس نسخهٔ کوچک را برنامه می‌سازد و
 *  سرور می‌سنجد (`product-photos.checkThumb`):
 *
 *    • اندازه: بلندترین ضلع [EDGE] = ۳۲۰px. کارت روی گوشیِ ۳۶۰ دو ستونی است (~۱۶۰px ِ CSS، با حاشیهٔ ۱۲٪ ِ
 *      `.p-img.tile` ~۱۲۰px) ⇒ با DPR ۲ دست‌بالا ۳۲۰ پیکسلِ واقعی؛ بزرگ‌تر فقط بایت است. سقفِ سرور ۴۰۰.
 *    • JPEG، چون برنامه امروز JPEG می‌سازد (`PhotoStore`، کیفیتِ ۸۰). کیفیت [QUALITY] = ۷۰: در اندازهٔ نمایش یا
 *      کوچک‌تر (۳۲۰ ⇐ ≤۳۲۰ پیکسلِ واقعی) لبه‌ها و نوشتهٔ بسته‌بندی تیز می‌مانند. سنجیده با رمزگذارِ JPEG ِ کرومیوم
 *      (libjpeg-turbo، همان خانوادهٔ `Bitmap.compress`) روی ۳۲۰px، PSNR در برابرِ همان کارتِ بی‌اتلاف: کالای زمینه‌سفید
 *      با نوشته ۳٫۶KB و ۳۵٫۴dB (۸۰ ⇒ ۴٫۱KB و ۳۶٫۲dB؛ ۶۰ ⇒ ۳٫۲KB و ۳۴٫۷dB)، عکسِ شلوغِ آزمونِ کارایی ۱۲KB و ۳۱٫۷dB
 *      (۸۰ ⇒ ۱۵KB؛ اصلِ ۸۰۰/۶۰۰px ~۳۰KB). بالای ۷۰ هر dB حدودِ ۲۵٪ بایت می‌خواهد و در ۳۲۰px دیده نمی‌شود. نشد زیرِ
 *      [MAX_BYTES] ⇒ پله‌های کیفیت ([qualities]، همه بالای [ImageQuality.FLOOR]) و بعد ضلعِ ۳/۴ (تا [MIN_EDGE]).
 *    • جهتِ EXIF روی پیکسل‌ها (`ShopperPhoto.orient`)؛ سرور کارتِ با orientation > ۱ را رد می‌کند. JPEG ِ تازه از
 *      روی پیکسل ⇒ بی EXIF/GPS (سرور هم `strip_metadata` می‌زند). شفافیت روی سفید (JPEG شفافیت ندارد).
 *    • اول کوچک، بعد چرخش: حافظه به اندازهٔ کارت، نه عکس.
 *
 *  کدگذاریِ JPEG کارِ سکوست ([Encoder]: `Bitmap.compress` ِ اندروید، ImageIO ِ کامپیوتر، Skia ِ آیفون).
 */
object PhotoThumb {
  const val EDGE = 320
  const val MIN_EDGE = 160
  const val QUALITY = 70
  /** پایین‌ترین پله هنوز بالای مرزِ «فشرده‌سازیِ زیاد» ِ [ImageQuality.FLOOR] (۵۰) */
  const val MIN_QUALITY = 54
  /** سقفِ برنامه؛ سرور تا [SERVER_MAX_BYTES] و [SERVER_MAX_EDGE] می‌پذیرد (`product-photos.THUMB_MAX_*`) */
  const val MAX_BYTES = 32 * 1024
  const val SERVER_MAX_BYTES = 64 * 1024
  const val SERVER_MAX_EDGE = 400

  /** پیکسل‌های ARGB ِ سطری ⇒ بایت‌های JPEG با کیفیتِ داده‌شده */
  fun interface Encoder {
    fun jpeg(px: IntArray, width: Int, height: Int, quality: Int): ByteArray
  }

  class Made(val bytes: ByteArray, val width: Int, val height: Int, val quality: Int)

  /** پله‌های کیفیت: ۷۰، ۶۲، ۵۴ */
  fun qualities(): List<Int> = generateSequence(QUALITY) { it - 8 }.takeWhile { it >= MIN_QUALITY }.toList()

  /** ابعادِ کارت (ایستاده) برای عکسِ [w]×[h] با جهتِ EXIF ِ [orientation] و بلندترین ضلعِ [edge] */
  fun size(w: Int, h: Int, orientation: Int, edge: Int = EDGE): Pair<Int, Int> {
    val (sw, sh) = ShopperPhoto.fit(w, h, edge)
    return if (orientation in 5..8) sh to sw else sw to sh
  }

  /**
   *  @param px پیکسل‌های عکسِ رمزگشایی‌شده (هنوز نچرخیده)، [w]×[h]
   *  @param orientation جهتِ EXIF ِ همان بایت‌ها (`ShopperPhoto.orientationOf`)
   *  @return کارت، یا null اگر عکس نیست یا حتی در [MIN_EDGE] و [MIN_QUALITY] زیرِ [MAX_BYTES] نرفت
   */
  fun make(px: IntArray, w: Int, h: Int, orientation: Int, encode: Encoder): Made? {
    if (w <= 0 || h <= 0 || px.size < w * h) return null
    var edge = EDGE
    while (true) {
      val (sw, sh) = ShopperPhoto.fit(w, h, edge)
      val small = shrink(px, w, h, sw, sh)
      val (opx, ow, oh) = ShopperPhoto.orient(ShopperPhoto.flattenOnWhite(small), sw, sh, orientation)
      for (q in qualities()) {
        val b = encode.jpeg(opx, ow, oh, q)
        if (b.isNotEmpty() && b.size <= MAX_BYTES) return Made(b, ow, oh, q)
      }
      if (edge <= MIN_EDGE) return null
      edge = max(MIN_EDGE, edge * 3 / 4)
    }
  }

  /** کوچک کردن با میانگینِ ناحیه (بی دندانه)؛ همیشه آرایهٔ تازه (ورودی دست نمی‌خورد) */
  internal fun shrink(px: IntArray, w: Int, h: Int, tw: Int, th: Int): IntArray {
    if (tw >= w && th >= h) return px.copyOf(w * h)
    val out = IntArray(tw * th)
    for (y in 0 until th) {
      val y0 = y * h / th
      val y1 = max(y0 + 1, min(h, (y + 1) * h / th))
      for (x in 0 until tw) {
        val x0 = x * w / tw
        val x1 = max(x0 + 1, min(w, (x + 1) * w / tw))
        var a = 0; var r = 0; var g = 0; var b = 0; var n = 0
        for (yy in y0 until y1) {
          val row = yy * w
          for (xx in x0 until x1) {
            val c = px[row + xx]
            a += c ushr 24; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
          }
        }
        out[y * tw + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
      }
    }
    return out
  }
}
