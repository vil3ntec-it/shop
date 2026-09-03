package ir.vil3ntec.tohid.scan

import java.nio.ByteBuffer

/**
 *  حالِ یک فریم، در پنج عدد.
 *
 *  ── چرا اصلاً لازم است ─────────────────────────────────────────────
 *  تا امروز تحلیل‌گرِ ما به **پیکسل‌ها دست نمی‌زد**: فریم را می‌گرفت و
 *  دست‌نخورده می‌داد به ML Kit. یعنی برنامه هیچ نمی‌دانست تصویر روشن
 *  است یا تاریک، بارکد سوخته یا کم‌نور، کنتراست دارد یا ندارد.
 *
 *  و بی این دانستن، هیچ تصمیمِ خودکاری دربارهٔ چراغ ممکن نیست. «اگر نور
 *  کم بود چراغ را روشن کن» یعنی اول باید نور را **اندازه گرفت**.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── چرا این پنج تا، و نه روشناییِ ساده ─────────────────────────────
 *  روشناییِ کلِ تصویر گمراه‌کننده است. دکانِ تاریک با یک بارکدِ زیرِ
 *  لامپ، «تاریک» شمرده می‌شود و چراغ بی‌جهت روشن می‌شود — و همان چراغ
 *  روی پلاستیکِ براق بازتاب می‌اندازد و بارکد را می‌سوزاند. پس:
 *
 *   • `luma` — روشناییِ کلِ کادر. فقط برای مقایسه.
 *   • `roiLuma` — روشناییِ **همان‌جا که بارکد است**. تصمیمِ چراغ با این
 *     گرفته می‌شود، نه با کل.
 *   • `roiSpread` — فاصلهٔ روشن‌ترین و تاریک‌ترینِ آن ناحیه (صدکِ ۹۰ منهای
 *     صدکِ ۱۰). بارکد یعنی خطِ سیاه روی زمینهٔ سفید؛ کنتراستش اگر بمیرد،
 *     رمزگشا هم می‌میرد. این عدد همان کنتراست است.
 *   • `roiClipped` — چه کسری از آن ناحیه **سوخته** است (نزدیکِ ۲۵۵).
 *     نشانهٔ قطعیِ بازتاب: نور به پلاستیک خورده و برگشته.
 *   • `samples` — چند نقطه واقعاً خوانده شد؛ صفر یعنی این عددها بی‌معنی‌اند.
 *  ──────────────────────────────────────────────────────────────────
 */
data class Look(
  val luma: Int,
  val roiLuma: Int,
  val roiSpread: Int,
  val roiClipped: Float,
  val samples: Int,
) {
  /** فریمی که چیزی از آن خوانده نشد — هیچ تصمیمی نباید رویش گرفته شود */
  val usable: Boolean get() = samples >= MIN_SAMPLES

  companion object {
    /** کمتر از این تعداد نقطه، آمار نیست */
    const val MIN_SAMPLES = 64

    val NOTHING = Look(0, 0, 0, 0f, 0)
  }
}

/**
 *  اندازه‌گیریِ فریم — ارزان، بی‌تخصیصِ حافظه، و روی صفحهٔ روشناییِ خام.
 *
 *  ── چرا کند نمی‌کند ────────────────────────────────────────────────
 *  کلِ فریم خوانده نمی‌شود. یک تورِ ثابت روی تصویر انداخته می‌شود — حدودِ
 *  ۶۴×۶۴ نقطه — و هر چه رزولوشنِ دوربین بالاتر برود، گامِ تور درشت‌تر
 *  می‌شود و تعدادِ نقطه‌ها همان می‌ماند. یعنی هزینه‌اش روی ۷۲۰p و ۴K یکی
 *  است: چند هزار خواندنِ بایت، در حدِ چند ده میکروثانیه.
 *
 *  و فقط صفحهٔ **Y** خوانده می‌شود (روشنایی)، نه رنگ. بارکد سیاه و سفید
 *  است؛ رنگ در این تصمیم هیچ نقشی ندارد و خواندنش دو برابر خرج است.
 *
 *  با `get(index)` خوانده می‌شود نه `get()`: آن یکی مکان‌نمای بافر را
 *  جابه‌جا می‌کند و همین بافر همان لحظه دستِ ML Kit هم هست. خواندنِ
 *  مکان‌دار، بافر را دست‌نخورده می‌گذارد.
 *  ──────────────────────────────────────────────────────────────────
 */
object FrameLook {

  /** بزرگیِ تور در هر بُعد — ۶۴×۶۴ یعنی حدودِ چهار هزار نقطه */
  private const val GRID = 64

  /** از این روشن‌تر یعنی سوخته */
  private const val CLIP = 250

  /**
   *  @param roi ناحیهٔ بارکد در دستگاهِ خودِ بافر، هر چهار عدد بینِ ۰ و ۱
   *    به ترتیبِ چپ، بالا، راست، پایین
   */
  fun measure(
    y: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    roi: FloatArray,
  ): Look {
    if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return Look.NOTHING

    val left = (roi[0].coerceIn(0f, 1f) * width).toInt()
    val top = (roi[1].coerceIn(0f, 1f) * height).toInt()
    val right = (roi[2].coerceIn(0f, 1f) * width).toInt()
    val bottom = (roi[3].coerceIn(0f, 1f) * height).toInt()

    val stepX = (width / GRID).coerceAtLeast(1)
    val stepY = (height / GRID).coerceAtLeast(1)

    var allSum = 0L
    var allCount = 0
    var roiSum = 0L
    var roiCount = 0
    var clipped = 0
    /*
     *  هیستوگرامِ ناحیه — برای صدکِ ۱۰ و ۹۰، که میانگین آن‌ها را نمی‌دهد.
     *
     *  هر فریم یک آرایهٔ یک‌کیلوبایتی ساخته می‌شود و نه یکی که بازاستفاده
     *  شود. عمدی است: آرایهٔ مشترک یعنی اگر روزی دو نما با هم اندازه
     *  بگیرند، عددها بی‌صدا قاتی می‌شوند. یک کیلوبایت در نسلِ جوانِ
     *  حافظه، بهایِ ارزانی است برای درستیِ بی‌قید‌وشرط.
     */
    val hist = IntArray(256)

    val limit = y.limit()
    var py = 0
    while (py < height) {
      val rowAt = py * rowStride
      var px = 0
      while (px < width) {
        val at = rowAt + px * pixelStride
        if (at >= limit) { px += stepX; continue }
        val value = y.get(at).toInt() and 0xFF
        allSum += value
        allCount++
        if (px in left..right && py in top..bottom) {
          roiSum += value
          roiCount++
          hist[value]++
          if (value >= CLIP) clipped++
        }
        px += stepX
      }
      py += stepY
    }

    if (allCount == 0) return Look.NOTHING

    //  اگر ناحیه چیزی نداد (کادرِ بارکد از تصویر بیرون افتاده)، کلِ فریم
    //  جایش می‌نشیند — بی‌جواب ماندن بدتر از جوابِ درشت است
    if (roiCount < Look.MIN_SAMPLES) {
      val whole = (allSum / allCount).toInt()
      return Look(whole, whole, 0, 0f, allCount)
    }

    return Look(
      luma = (allSum / allCount).toInt(),
      roiLuma = (roiSum / roiCount).toInt(),
      roiSpread = spread(hist, roiCount),
      roiClipped = clipped.toFloat() / roiCount,
      samples = allCount,
    )
  }

  /**
   *  صدکِ ۹۰ منهای صدکِ ۱۰.
   *
   *  چرا نه «بیشینه منهای کمینه»: یک نقطهٔ سوخته یا یک نقطهٔ کاملاً سیاه
   *  آن را همیشه ۲۵۵ می‌کند و عدد بی‌معنی می‌شود. صدک، نویز را می‌اندازد
   *  بیرون و آنچه می‌ماند کنتراستِ واقعیِ خط‌های بارکد است.
   */
  private fun spread(hist: IntArray, total: Int): Int {
    val lowAt = (total * 0.10f).toInt()
    val highAt = (total * 0.90f).toInt()
    var seen = 0
    var low = -1
    var high = 255
    for (value in 0..255) {
      seen += hist[value]
      if (low < 0 && seen > lowAt) low = value
      if (seen > highAt) { high = value; break }
    }
    if (low < 0) low = 0
    return (high - low).coerceAtLeast(0)
  }
}
