package ir.vil3ntec.tohid.core.image

import kotlin.math.ceil
import kotlin.math.max

/**
 *  سنجشِ ساده و روی‌دستگاهِ عکسِ تازه (v3 ۳۶.۶، بی هوشِ مصنوعی): تاری و
 *  نورِ نامناسب **پیش از** ثبت، با پیشنهادِ «نور بیشتر» / «دوربین را ثابت
 *  نگه دار». فقط هشدار است؛ تصمیم با کاربر (دوباره بگیرد یا همین را بردارد).
 *
 *  ── معیارها ─────────────────────────────────────────────────────────
 *    روشنایی   میانگینِ luma (۰..۲۵۵)
 *    سوختگی    سهمِ پیکسل‌های ≥۲۵۰ (نورِ تند/بازتاب)
 *    وضوح      واریانسِ لاپلاسینِ ۴-همسایه روی تصویرِ خاکستریِ ≤۳۲۰ پیکسلی
 *  مرزها محافظه‌کارانه‌اند و با مجموعهٔ آزمون (`CaptureQualityTest`) سنجیده
 *  شده‌اند؛ «قطعی» نیستند و برنامه آن‌ها را «تقریبی» می‌نامد.
 *  ──────────────────────────────────────────────────────────────────
 */
object CaptureQuality {
  const val DARK_MEAN = 55.0
  const val BRIGHT_CLIPPED = 0.35
  const val BLUR_VARIANCE = 40.0
  const val SAMPLE_SIDE = 320

  enum class Hint(val text: String) {
    DARK("نور کم است — کنارِ پنجره یا زیرِ چراغ دوباره بگیرید"),
    BRIGHT("نور تند است یا برق می‌زند — کمی زاویه را عوض کنید"),
    BLUR("عکس تار است — دوربین را ثابت نگه دارید و دوباره بگیرید"),
    SMALL("عکس کوچک است — دوربین را نزدیک‌تر ببرید"),
  }

  data class Metrics(val brightness: Double, val clipped: Double, val sharpness: Double, val width: Int, val height: Int)
  data class Verdict(val ok: Boolean, val hints: List<Hint>, val metrics: Metrics)

  fun luma(argb: Int): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000
  }

  /** روی شبکهٔ نمونه (هر `step` پیکسل یکی) — سریع روی گوشیِ ارزان */
  fun measure(argb: IntArray, w: Int, h: Int, sampleSide: Int = SAMPLE_SIDE): Metrics {
    require(w > 0 && h > 0 && argb.size >= w * h)
    val step = max(1, ceil(max(w, h).toDouble() / sampleSide).toInt())
    val gw = (w + step - 1) / step
    val gh = (h + step - 1) / step
    val g = IntArray(gw * gh)
    var sum = 0L
    var hot = 0
    for (y in 0 until gh) for (x in 0 until gw) {
      val v = luma(argb[(y * step) * w + x * step])
      g[y * gw + x] = v
      sum += v
      if (v >= 250) hot++
    }
    val n = gw * gh
    var lapSum = 0.0
    var lapSq = 0.0
    var m = 0
    for (y in 1 until gh - 1) for (x in 1 until gw - 1) {
      val c = g[y * gw + x]
      val l = (g[y * gw + x - 1] + g[y * gw + x + 1] + g[(y - 1) * gw + x] + g[(y + 1) * gw + x] - 4 * c).toDouble()
      lapSum += l; lapSq += l * l; m++
    }
    val variance = if (m > 0) lapSq / m - (lapSum / m) * (lapSum / m) else 0.0
    return Metrics(sum.toDouble() / n, hot.toDouble() / n, variance, w, h)
  }

  fun judge(m: Metrics): Verdict {
    val hints = ArrayList<Hint>()
    if (m.brightness < DARK_MEAN) hints += Hint.DARK
    if (m.clipped > BRIGHT_CLIPPED) hints += Hint.BRIGHT
    //  تاریکیِ مطلق لاپلاسین را هم صفر می‌کند؛ آن‌جا «نور» پیشنهادِ درست است، نه «ثابت نگه دار»
    if (m.sharpness < BLUR_VARIANCE && Hint.DARK !in hints) hints += Hint.BLUR
    if (max(m.width, m.height) < ImagePrep.MIN_SIDE) hints += Hint.SMALL
    return Verdict(hints.isEmpty(), hints, m)
  }

  /** بهترین عکس از چند تلاش: اول بی‌هشدار، بعد وضوحِ بیشتر (انتخابِ دستی همیشه آزاد است) */
  fun best(list: List<Verdict>): Int {
    if (list.isEmpty()) return -1
    return list.indices.maxWith(compareBy<Int>({ if (list[it].ok) 1 else 0 }, { -list[it].hints.size }, { list[it].metrics.sharpness }))
  }
}
