package ir.vil3ntec.tohid.scan

/**
 *  کجای تصویر بارکد است، و کدام بارکد.
 *
 *  این پرونده عمداً هیچ چیزِ اندرویدی ندارد: نه `Rect`، نه `ImageProxy`،
 *  نه دوربین. ریاضیِ اینجا همان جایی است که اشتباهش گران تمام می‌شود —
 *  یک علامتِ منفیِ جابه‌جا و فوکوس می‌رود سرِ نقطهٔ قرینه — پس باید بشود
 *  روی JVM و بی دوربین سنجیدش. سنجه‌هایش در `AimTest`.
 */
object Aim {

  /** یک کادر، همه بینِ صفر و یک: چپ، بالا، راست، پایین */
  data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
  }

  /** میانهٔ کادر — وقتی هنوز بارکدی دیده نشده */
  val CENTER = Box(0.30f, 0.30f, 0.70f, 0.70f)

  /**
   *  کادرِ بارکد را از دستگاهِ **تصویرِ سرِپا** به دستگاهِ **بافرِ خام**
   *  برمی‌گرداند.
   *
   *  ── چرا این کار لازم است ─────────────────────────────────────────
   *  ML Kit تصویر را چرخانده می‌گیرد (`rotationDegrees`) و کادرِ بارکد را
   *  در همان دستگاهِ چرخانده — یعنی همان‌طور که آدم می‌بیند — پس می‌دهد.
   *  ولی نقطهٔ فوکوس با `SurfaceOrientedMeteringPointFactory` ساخته
   *  می‌شود و آن، دستگاهِ **بافرِ نچرخیدهٔ** دوربین را می‌فهمد. این دو
   *  روی گوشیِ عمودی ۹۰ درجه با هم فرق دارند.
   *
   *  اگر این ترجمه نباشد یا وارونه باشد، فوکوس می‌رود جایی که بارکد
   *  نیست — و آن، از فوکوس نکردن هم بدتر است.
   *  ──────────────────────────────────────────────────────────────────
   *
   *  چرخشِ `r` درجه در جهتِ عقربه، نقطهٔ بافری `(u,v)` را می‌برد به:
   *    ۹۰ → `(1-v, u)` ، ۱۸۰ → `(1-u, 1-v)` ، ۲۷۰ → `(v, 1-u)`
   *  و اینجا وارونهٔ همان حساب می‌شود.
   */
  fun toBuffer(box: Box, rotation: Int): Box {
    val r = ((rotation % 360) + 360) % 360
    val corners = listOf(
      unrotate(box.left, box.top, r),
      unrotate(box.right, box.top, r),
      unrotate(box.left, box.bottom, r),
      unrotate(box.right, box.bottom, r),
    )
    return Box(
      left = corners.minOf { it.first },
      top = corners.minOf { it.second },
      right = corners.maxOf { it.first },
      bottom = corners.maxOf { it.second },
    )
  }

  private fun unrotate(x: Float, y: Float, r: Int): Pair<Float, Float> = when (r) {
    90 -> y to (1f - x)
    180 -> (1f - x) to (1f - y)
    270 -> (1f - y) to x
    else -> x to y
  }

  /**
   *  کادر را کمی گشاد می‌کند و در مرزِ تصویر نگه می‌دارد.
   *
   *  ناحیهٔ فوکوسِ چسبیده به خطوطِ بارکد، برای دوربین سوژهٔ خوبی نیست:
   *  بارکدِ نازک ممکن است باریک‌تر از کمترین ناحیهٔ فوکوسِ حسگر باشد.
   *  کمی حاشیه، هم به دوربین بافت می‌دهد هم تکانِ دست را می‌پوشاند.
   */
  fun padded(box: Box, by: Float = 0.25f): Box {
    val padX = box.width * by
    val padY = box.height * by
    return Box(
      left = (box.left - padX).coerceIn(0f, 1f),
      top = (box.top - padY).coerceIn(0f, 1f),
      right = (box.right + padX).coerceIn(0f, 1f),
      bottom = (box.bottom + padY).coerceIn(0f, 1f),
    )
  }

  /**
   *  از میانِ چند بارکد، کدام.
   *
   *  ── چرا انتخابِ اولی غلط بود ─────────────────────────────────────
   *  تا امروز `codes.firstOrNull { … }` نوشته شده بود، یعنی هر چه ML Kit
   *  اول برگرداند. ترتیبِ آن فهرست هیچ تضمینی ندارد. روی قفسه‌ای که دو
   *  کالا کنارِ هم است — یا روی بسته‌ای که بارکدِ خودش و بارکدِ کارتنِ
   *  زیرش هر دو در کادرند — یعنی گاهی کالای کناری به سبد می‌رفت. همان
   *  «نتیجهٔ اشتباه» که گزارش شد.
   *
   *  و «نزدیک‌ترین را حدس بزن» هم جواب نیست؛ خواسته صریح بود: حدس نزن.
   *  ──────────────────────────────────────────────────────────────────
   *
   *  ── قاعده ────────────────────────────────────────────────────────
   *  آن‌که کاربر **قصدش** را دارد، بزرگ‌تر و میانه‌تر است: آدم گوشی را
   *  روی همانی می‌گیرد که می‌خواهد. پس امتیاز = مساحت، تقسیم بر جریمهٔ
   *  فاصله از میانه.
   *
   *  و اگر دو تا **هم‌وزن** درآمدند (اختلافِ امتیاز کمتر از یک‌دهم)، هیچ‌کدام
   *  انتخاب نمی‌شود: `null` برمی‌گردد و همان فریم رد می‌شود. یک فریمِ رد
   *  شده یعنی سی‌اُمِ ثانیه تأخیر؛ کالای اشتباه در فاکتور یعنی پولِ اشتباه
   *  از مشتری.
   */
  fun <T> bestOf(items: List<T>, box: (T) -> Box?): T? {
    if (items.isEmpty()) return null
    if (items.size == 1) return items.first()

    var best: T? = null
    var bestScore = -1f
    var runnerUp = -1f
    for (item in items) {
      val at = box(item) ?: continue
      val score = score(at)
      if (score > bestScore) {
        runnerUp = bestScore
        best = item
        bestScore = score
      } else if (score > runnerUp) {
        runnerUp = score
      }
    }
    if (best == null) return null
    //  دو تا که به هم نزدیک‌اند، یعنی معلوم نیست کدام را می‌خواهد
    if (runnerUp > 0f && runnerUp > bestScore * 0.90f) return null
    return best
  }

  private fun score(box: Box): Float {
    if (box.area <= 0f) return 0f
    val dx = box.cx - 0.5f
    val dy = box.cy - 0.5f
    val away = kotlin.math.sqrt(dx * dx + dy * dy)
    //  فاصله از میانه تا نیمِ قطر می‌رسد (~۰٫۷۱)؛ جریمه‌اش تا دو برابر
    return box.area / (1f + away * 1.4f)
  }
}
