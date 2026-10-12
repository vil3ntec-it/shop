package ir.vil3ntec.tohid.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 *  استودیوی عکسِ کالا — کیفیت، زمینهٔ سفید، جدا کردنِ کالا، برش و هم‌اندازه.
 *
 *  ── چرا این‌جا و چرا بی کتابخانه ───────────────────────────────────
 *  همهٔ کار روی آرایهٔ پیکسل (`IntArray`ِ ARGB) است، نه `Bitmap`: پس همین
 *  یک فایل روی گوشی، کامپیوتر و آزمون‌ها همان نتیجه را می‌دهد. مدلِ
 *  هوشِ مصنوعی (چند ده مگابایت) لازم ندارد و آفلاین کار می‌کند.
 *
 *  ── جدا کردنِ کالا از زمینه ───────────────────────────────────────
 *  عکسِ کالا تقریباً همیشه کالاست وسطِ یک زمینهٔ نسبتاً یک‌دست (میز،
 *  دیوار، پارچه). پس رنگِ زمینه از **لبه‌های عکس** برآورد می‌شود و از
 *  همهٔ لبه‌ها یک «سیل» (flood fill) راه می‌افتد که تا وقتی رنگ به زمینه
 *  نزدیک است پیش می‌رود. هرچه سیل به آن نرسید کالاست — حتی اگر رنگش
 *  شبیهِ زمینه باشد، چون از لبه راهی به آن نیست. بعد:
 *    • لکه‌های ریزِ جدا (گرد و خاک، سایهٔ کوچک) حذف می‌شوند
 *    • لبه نرم می‌شود (۲ پیکسل) تا دورِ کالا دندانه‌دندانه نشود
 *  ⚠️ زمینهٔ شلوغ (قفسهٔ پر از جنس) را کامل جدا نمی‌کند؛ برای همین
 *  «حساسیت» دستی است و پیش‌نمایش پیش از ذخیره دیده می‌شود.
 *
 *  ── کیفیت ─────────────────────────────────────────────────────────
 *  عکسِ کوچک با درون‌یابیِ دوخطیِ پله‌ای بزرگ می‌شود، بعد سطوحِ رنگ
 *  کشیده می‌شوند (تاریک‌ترین ۰٫۵٪ سیاه، روشن‌ترین ۰٫۵٪ سفید)، تعادلِ
 *  رنگِ سفید ملایم اصلاح می‌شود و در آخر «تیزکردن» (unsharp mask).
 *  جزئیاتی که در عکس نبوده ساخته نمی‌شود؛ عکسِ تار، روشن‌تر و تیزتر
 *  می‌شود، نه عکسِ تازه.
 *
 *  هر کار روی نخِ پس‌زمینه صدا زده شود: عکسِ ۱۲۰۰ پیکسلی روی کامپیوترِ
 *  معمولی کمتر از ۲۰۰ میلی‌ثانیه.
 *  ──────────────────────────────────────────────────────────────────
 */
object PhotoStudio {

  /** بلندترین ضلعِ کار — عکسِ بزرگ‌تر اول کوچک می‌شود تا کار سریع بماند */
  const val WORK_EDGE = 1200

  /** اندازهٔ خروجی: مربعِ هم‌اندازه برای همهٔ کالاها */
  const val OUT_EDGE = 800

  class Image(val width: Int, val height: Int, val px: IntArray) {
    init { require(px.size == width * height) }
  }

  data class Options(
    /** زمینه را سفید کن و کالا را جدا کن */
    val removeBackground: Boolean = true,
    /** حساسیتِ زمینه ۵ تا ۸۰ — بالاتر یعنی رنگ‌های دورتر هم زمینه حساب شوند */
    val tolerance: Int = 28,
    /** کالا را از زمینه برش بزن و وسطِ کادرِ مربع بگذار */
    val autoCrop: Boolean = true,
    /** فاصلهٔ کالا تا لبهٔ کادر، درصد (۰ تا ۲۵) */
    val margin: Int = 8,
    /** روشن‌سازی، سطوح، تعادلِ سفید */
    val enhance: Boolean = true,
    /** تیز کردن (۰ تا ۱۰۰) */
    val sharpen: Int = 50,
    /** روشنایی −۵۰ تا +۵۰ */
    val brightness: Int = 0,
    /** چرخش: ۰، ۹۰، ۱۸۰، ۲۷۰ */
    val rotate: Int = 0,
    /** اندازهٔ خروجی (مربع) */
    val outEdge: Int = OUT_EDGE,
  )

  /** کلِ کار به ترتیب — خروجی همیشه مربعِ `outEdge` با زمینهٔ سفید */
  fun process(src: Image, o: Options = Options()): Image {
    var img = fitWithin(src, WORK_EDGE)
    img = rotate(img, o.rotate)
    if (o.enhance) img = enhance(img)
    if (o.brightness != 0) img = brightness(img, o.brightness)

    val mask: FloatArray? = if (o.removeBackground) foregroundMask(img, o.tolerance) else null
    if (mask != null) img = compositeOnWhite(img, mask)

    val box = if (o.autoCrop) (mask?.let { bbox(it, img.width, img.height) } ?: contentBox(img)) else null
    img = placeOnSquare(img, box, o.margin, o.outEdge)
    if (o.sharpen > 0) img = sharpen(img, o.sharpen / 100f)
    return img
  }

  /* ============================ اندازه ============================ */

  fun fitWithin(src: Image, edge: Int): Image {
    val longest = max(src.width, src.height)
    if (longest <= edge) return src
    val r = edge.toFloat() / longest
    return resize(src, max(1, (src.width * r).roundToInt()), max(1, (src.height * r).roundToInt()))
  }

  /**
   *  تغییرِ اندازه با کیفیت: کوچک کردن با میانگینِ ناحیه (بی دندانه)،
   *  بزرگ کردن با دوخطی در پله‌های دوبرابر (نرم‌تر از یک‌جا).
   */
  fun resize(src: Image, w: Int, h: Int): Image {
    if (w == src.width && h == src.height) return src
    if (w < src.width && h < src.height) return areaDown(src, w, h)
    var cur = src
    while (cur.width * 2 < w && cur.height * 2 < h) cur = bilinear(cur, cur.width * 2, cur.height * 2)
    return bilinear(cur, w, h)
  }

  private fun areaDown(src: Image, w: Int, h: Int): Image {
    val out = IntArray(w * h)
    val sx = src.width.toFloat() / w
    val sy = src.height.toFloat() / h
    for (y in 0 until h) {
      val y0 = (y * sy).toInt(); val y1 = min(src.height, max(y0 + 1, ((y + 1) * sy).toInt()))
      for (x in 0 until w) {
        val x0 = (x * sx).toInt(); val x1 = min(src.width, max(x0 + 1, ((x + 1) * sx).toInt()))
        var a = 0; var r = 0; var g = 0; var b = 0; var n = 0
        for (yy in y0 until y1) {
          val row = yy * src.width
          for (xx in x0 until x1) {
            val c = src.px[row + xx]
            a += c ushr 24; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
          }
        }
        out[y * w + x] = argb(a / n, r / n, g / n, b / n)
      }
    }
    return Image(w, h, out)
  }

  private fun bilinear(src: Image, w: Int, h: Int): Image {
    val out = IntArray(w * h)
    val sx = (src.width - 1).toFloat() / max(1, w - 1)
    val sy = (src.height - 1).toFloat() / max(1, h - 1)
    for (y in 0 until h) {
      val fy = y * sy; val y0 = fy.toInt(); val y1 = min(src.height - 1, y0 + 1); val ty = fy - y0
      for (x in 0 until w) {
        val fx = x * sx; val x0 = fx.toInt(); val x1 = min(src.width - 1, x0 + 1); val tx = fx - x0
        val c00 = src.px[y0 * src.width + x0]; val c10 = src.px[y0 * src.width + x1]
        val c01 = src.px[y1 * src.width + x0]; val c11 = src.px[y1 * src.width + x1]
        fun ch(s: Int): Int {
          val a = ((c00 shr s) and 255) * (1 - tx) + ((c10 shr s) and 255) * tx
          val b = ((c01 shr s) and 255) * (1 - tx) + ((c11 shr s) and 255) * tx
          return (a * (1 - ty) + b * ty).roundToInt()
        }
        out[y * w + x] = argb(ch(24), ch(16), ch(8), ch(0))
      }
    }
    return Image(w, h, out)
  }

  fun rotate(src: Image, deg: Int): Image {
    val d = ((deg % 360) + 360) % 360
    if (d == 0) return src
    val w = src.width; val h = src.height
    return when (d) {
      180 -> Image(w, h, IntArray(w * h) { src.px[w * h - 1 - it] })
      90 -> { val out = IntArray(w * h); for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = src.px[y * w + x]; Image(h, w, out) }
      270 -> { val out = IntArray(w * h); for (y in 0 until h) for (x in 0 until w) out[(w - 1 - x) * h + y] = src.px[y * w + x]; Image(h, w, out) }
      else -> src
    }
  }

  /* ============================ کیفیت ============================ */

  /** کشیدنِ سطوح (۰٫۵٪ تا ۹۹٫۵٪) + تعادلِ سفیدِ ملایم */
  fun enhance(src: Image): Image {
    val n = src.px.size
    val hr = IntArray(256); val hg = IntArray(256); val hb = IntArray(256)
    var sr = 0L; var sg = 0L; var sb = 0L
    for (c in src.px) {
      val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
      hr[r]++; hg[g]++; hb[b]++; sr += r; sg += g; sb += b
    }
    fun lo(h: IntArray): Int { var s = 0; for (i in 0..255) { s += h[i]; if (s > n * 0.005) return i }; return 0 }
    fun hi(h: IntArray): Int { var s = 0; for (i in 255 downTo 0) { s += h[i]; if (s > n * 0.005) return i }; return 255 }
    //  همهٔ کانال‌ها با یک بازه کشیده می‌شوند تا رنگ عوض نشود؛ تعادلِ سفید جدا
    val l = min(lo(hr), min(lo(hg), lo(hb)))
    val u = max(hi(hr), max(hi(hg), hi(hb)))
    val span = max(1, u - l).toFloat()
    //  دنیای خاکستری، نصفه: رنگِ غالبِ نورِ زرد یا آبی را تا نیمه برمی‌گرداند
    val avg = (sr + sg + sb) / (3f * n)
    fun gain(s: Long): Float { val m = s.toFloat() / n; return if (m < 1f) 1f else 1f + (avg / m - 1f) * 0.5f }
    val gr = gain(sr); val gg = gain(sg); val gb = gain(sb)
    val lut = IntArray(256) { ((it - l) * 255f / span).roundToInt().coerceIn(0, 255) }
    val out = IntArray(n) { i ->
      val c = src.px[i]
      argb(c ushr 24,
        (lut[((c shr 16) and 255)] * gr).roundToInt().coerceIn(0, 255),
        (lut[((c shr 8) and 255)] * gg).roundToInt().coerceIn(0, 255),
        (lut[(c and 255)] * gb).roundToInt().coerceIn(0, 255))
    }
    return Image(src.width, src.height, out)
  }

  fun brightness(src: Image, amount: Int): Image {
    val k = amount.coerceIn(-50, 50) * 2.2f
    return Image(src.width, src.height, IntArray(src.px.size) { i ->
      val c = src.px[i]
      argb(c ushr 24, (((c shr 16) and 255) + k).roundToInt().coerceIn(0, 255),
        (((c shr 8) and 255) + k).roundToInt().coerceIn(0, 255), ((c and 255) + k).roundToInt().coerceIn(0, 255))
    })
  }

  /** unsharp mask با تاریِ ۳×۳ — فقط جاهایی که لبه هست (آستانهٔ ۴) */
  fun sharpen(src: Image, amount: Float): Image {
    val w = src.width; val h = src.height
    if (w < 3 || h < 3) return src
    val out = src.px.copyOf()
    val a = amount.coerceIn(0f, 1f) * 1.2f
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
      val i = y * w + x
      var r = 0; var g = 0; var b = 0
      for (dy in -1..1) for (dx in -1..1) {
        val c = src.px[i + dy * w + dx]; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
      }
      val c = src.px[i]
      fun ch(v: Int, blur: Int): Int { val d = v - blur / 9; return if (abs(d) < 4) v else (v + d * a).roundToInt().coerceIn(0, 255) }
      out[i] = argb(c ushr 24, ch((c shr 16) and 255, r), ch((c shr 8) and 255, g), ch(c and 255, b))
    }
    return Image(w, h, out)
  }

  /* ========================= جدا کردنِ کالا ========================= */

  /**
   *  ماسکِ کالا: ۱ کالا، ۰ زمینه، میانه‌ها لبهٔ نرم.
   *  `null` یعنی زمینه یک‌دست نبود و جدا کردن خطرناک است (کلِ عکس کالا).
   */
  fun foregroundMask(img: Image, tolerance: Int): FloatArray? {
    val w = img.width; val h = img.height; val n = w * h
    val bg = borderColor(img) ?: return null
    val tol = tolerance.coerceIn(5, 80).toFloat()
    val tol2 = tol * tol
    //  گامِ هم‌سایه سخت‌گیرتر: سیل از روی لبهٔ تیزِ کالا رد نشود
    val stepTol2 = (tol * 0.6f) * (tol * 0.6f)
    val isBg = BooleanArray(n)
    val queue = IntArray(n)
    var head = 0; var tail = 0
    fun seed(i: Int) { if (!isBg[i] && dist2(img.px[i], bg) <= tol2) { isBg[i] = true; queue[tail++] = i } }
    for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
    for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
    while (head < tail) {
      val i = queue[head++]; val x = i % w; val y = i / w; val c = img.px[i]
      fun go(j: Int) {
        if (isBg[j]) return
        val cj = img.px[j]
        if (dist2(cj, bg) <= tol2 || (dist2(cj, c) <= stepTol2 && dist2(cj, bg) <= tol2 * 2.2f)) { isBg[j] = true; queue[tail++] = j }
      }
      if (x > 0) go(i - 1); if (x < w - 1) go(i + 1); if (y > 0) go(i - w); if (y < h - 1) go(i + w)
    }
    val bgCount = tail
    //  زمینه کمتر از ۵٪ ⇒ عکس از نزدیک گرفته شده یا زمینه شلوغ است؛ دست نزن
    if (bgCount < n * 0.05f) return null
    //  همه زمینه شد ⇒ حساسیت زیاد بود
    if (bgCount > n * 0.985f) return null
    keepMainObjects(isBg, w, h)
    return feather(isBg, w, h)
  }

  /** رنگِ زمینه: میانهٔ پیکسل‌های لبه — اگر لبه‌ها خیلی ناهمگون بودند `null` */
  private fun borderColor(img: Image): Int? {
    val w = img.width; val h = img.height
    val samples = ArrayList<Int>()
    val step = max(1, (w + h) / 400)
    for (x in 0 until w step step) { samples += img.px[x]; samples += img.px[(h - 1) * w + x] }
    for (y in 0 until h step step) { samples += img.px[y * w]; samples += img.px[y * w + w - 1] }
    if (samples.isEmpty()) return null
    fun med(s: Int) = samples.map { (it shr s) and 255 }.sorted()[samples.size / 2]
    val bg = argb(255, med(16), med(8), med(0))
    //  چند درصدِ لبه به این رنگ نزدیک است؟ کمتر از ۴۰٪ یعنی زمینهٔ یک‌دست نیست
    val near = samples.count { dist2(it, bg) <= 45f * 45f }
    return if (near < samples.size * 0.4f) null else bg
  }

  /** لکه‌های کالایی که از ۰٫۵٪ِ بزرگ‌ترین تکه کوچک‌ترند زمینه می‌شوند */
  private fun keepMainObjects(isBg: BooleanArray, w: Int, h: Int) {
    val n = w * h
    val label = IntArray(n)
    val sizes = ArrayList<Int>().apply { add(0) }
    val queue = IntArray(n)
    for (s in 0 until n) {
      if (isBg[s] || label[s] != 0) continue
      val id = sizes.size
      var head = 0; var tail = 0
      queue[tail++] = s; label[s] = id
      while (head < tail) {
        val i = queue[head++]; val x = i % w; val y = i / w
        fun go(j: Int) { if (!isBg[j] && label[j] == 0) { label[j] = id; queue[tail++] = j } }
        if (x > 0) go(i - 1); if (x < w - 1) go(i + 1); if (y > 0) go(i - w); if (y < h - 1) go(i + w)
      }
      sizes += tail
    }
    val biggest = sizes.maxOrNull() ?: return
    val minKeep = max(16, (biggest * 0.005f).toInt())
    for (i in 0 until n) if (!isBg[i] && sizes[label[i]] < minKeep) isBg[i] = true
  }

  /** لبهٔ نرم: میانگینِ ۵×۵ِ ماسک — فقط کنارِ مرز حساب می‌شود */
  private fun feather(isBg: BooleanArray, w: Int, h: Int): FloatArray {
    val m = FloatArray(w * h) { if (isBg[it]) 0f else 1f }
    val out = m.copyOf()
    for (y in 0 until h) for (x in 0 until w) {
      val i = y * w + x
      val edge = (x > 0 && isBg[i - 1] != isBg[i]) || (x < w - 1 && isBg[i + 1] != isBg[i]) ||
        (y > 0 && isBg[i - w] != isBg[i]) || (y < h - 1 && isBg[i + w] != isBg[i])
      if (!edge) continue
      for (dy in -2..2) for (dx in -2..2) {
        val xx = x + dx; val yy = y + dy
        if (xx !in 0 until w || yy !in 0 until h) continue
        var s = 0f; var c = 0
        for (ey in -1..1) for (ex in -1..1) {
          val ax = xx + ex; val ay = yy + ey
          if (ax in 0 until w && ay in 0 until h) { s += m[ay * w + ax]; c++ }
        }
        out[yy * w + xx] = s / c
      }
    }
    return out
  }

  fun compositeOnWhite(img: Image, mask: FloatArray): Image =
    Image(img.width, img.height, IntArray(img.px.size) { i ->
      val c = img.px[i]; val a = mask[i] * ((c ushr 24) / 255f)
      fun ch(v: Int) = (v * a + 255 * (1 - a)).roundToInt().coerceIn(0, 255)
      argb(255, ch((c shr 16) and 255), ch((c shr 8) and 255), ch(c and 255))
    })

  /* ============================ برش ============================ */

  data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
  }

  private fun bbox(mask: FloatArray, w: Int, h: Int): Box? {
    var l = w; var t = h; var r = -1; var b = -1
    for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x] > 0.5f) {
      if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
    }
    return if (r < l || b < t) null else Box(l, t, r + 1, b + 1)
  }

  /** بی جدا کردن: حاشیهٔ تقریباً سفید/یک‌دستِ دورِ عکس بریده می‌شود */
  private fun contentBox(img: Image): Box? {
    val bg = borderColor(img) ?: return null
    val w = img.width; val h = img.height
    fun rowEmpty(y: Int) = (0 until w).all { dist2(img.px[y * w + it], bg) < 30f * 30f }
    fun colEmpty(x: Int) = (0 until h).all { dist2(img.px[it * w + x], bg) < 30f * 30f }
    var t = 0; while (t < h - 1 && rowEmpty(t)) t++
    var b = h; while (b > t + 1 && rowEmpty(b - 1)) b--
    var l = 0; while (l < w - 1 && colEmpty(l)) l++
    var r = w; while (r > l + 1 && colEmpty(r - 1)) r--
    return Box(l, t, r, b)
  }

  /** کالا (یا کلِ عکس) وسطِ مربعِ سفید با حاشیهٔ یکسان */
  fun placeOnSquare(img: Image, box: Box?, marginPct: Int, edge: Int): Image {
    val b = box ?: Box(0, 0, img.width, img.height)
    val crop = Image(b.width, b.height, IntArray(b.width * b.height) { i ->
      img.px[(b.top + i / b.width) * img.width + b.left + i % b.width]
    })
    val inner = (edge * (1f - 2f * marginPct.coerceIn(0, 25) / 100f)).roundToInt().coerceAtLeast(1)
    val r = inner.toFloat() / max(crop.width, crop.height)
    val sw = max(1, (crop.width * r).roundToInt()); val sh = max(1, (crop.height * r).roundToInt())
    val scaled = resize(crop, sw, sh)
    val out = IntArray(edge * edge) { WHITE }
    val ox = (edge - sw) / 2; val oy = (edge - sh) / 2
    for (y in 0 until sh) {
      val ty = oy + y; if (ty !in 0 until edge) continue
      for (x in 0 until sw) {
        val tx = ox + x; if (tx !in 0 until edge) continue
        val c = scaled.px[y * sw + x]; val a = (c ushr 24) / 255f
        out[ty * edge + tx] = if (a >= 0.999f) c else {
          fun ch(v: Int) = (v * a + 255 * (1 - a)).roundToInt().coerceIn(0, 255)
          argb(255, ch((c shr 16) and 255), ch((c shr 8) and 255), ch(c and 255))
        }
      }
    }
    return Image(edge, edge, out)
  }

  /* ============================ ابزار ============================ */

  private const val WHITE = -0x1

  private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

  /** فاصلهٔ رنگ با وزنِ چشم (قرمزِ کم‌اهمیت‌تر از سبز) */
  private fun dist2(c1: Int, c2: Int): Float {
    val dr = ((c1 shr 16) and 255) - ((c2 shr 16) and 255)
    val dg = ((c1 shr 8) and 255) - ((c2 shr 8) and 255)
    val db = (c1 and 255) - (c2 and 255)
    return (dr * dr * 0.3f + dg * dg * 0.59f + db * db * 0.11f) * 1.9f
  }

  /** برای آزمون و نمایش: فاصلهٔ رنگ به‌صورتِ عدد */
  fun colorDistance(c1: Int, c2: Int): Float = sqrt(dist2(c1, c2))
}
