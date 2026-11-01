package ir.vil3ntec.tohid.core.hw

/**
 *  برچسبِ کالا (HW-04، PRD-23، S24) — اندازه‌ها و زبانِ چاپگرِ برچسب.
 *
 *  نام و قیمت با قلمِ برنامه روی تصویر کشیده می‌شوند (نامِ دری بی تصویر
 *  درست چاپ نمی‌شود)؛ این‌جا فقط چیزهایی است که به قلم نیاز ندارد:
 *  اندازهٔ برچسب، جای بارکد، و بسته‌بندیِ تصویر برای TSPL یا ESC/POS.
 */
object Labels {

  /** نقطه در هر میلی‌متر روی چاپگرِ ۲۰۳dpi */
  const val DOTS_PER_MM = 8

  data class Size(val widthMm: Int, val heightMm: Int, val gapMm: Int = 2) {
    val widthDots: Int get() = widthMm * DOTS_PER_MM
    val heightDots: Int get() = heightMm * DOTS_PER_MM
  }

  /** برچسبِ رایجِ کالا — همانی که سناریوی S24 می‌خواهد */
  val SIZE_40x30 = Size(40, 30)

  /**
   *  برگهٔ A4 با ۳ ستون و ۸ ردیف (۲۴ برچسب) — بر حسبِ دهمِ میلی‌متر، تا
   *  وب (CSS) و برنامه (PDF) یک جدول را بکشند.
   */
  object A4 {
    const val PAGE_W = 2100
    const val PAGE_H = 2970
    const val COLS = 3
    const val ROWS = 8
    const val MARGIN_X = 0
    const val MARGIN_Y = 45
    const val CELL_W = 700
    const val CELL_H = 360

    /** گوشهٔ بالا-چپِ خانهٔ `index` (۰ تا ۲۳)؛ ترتیب راست‌به‌چپ مثلِ متن */
    fun cell(index: Int): Pair<Int, Int> {
      val i = index % (COLS * ROWS)
      val row = i / COLS
      val colRtl = COLS - 1 - (i % COLS)
      return (MARGIN_X + colRtl * CELL_W) to (MARGIN_Y + row * CELL_H)
    }

    /**
     *  برگهٔ کامل با ۸ نقطه در میلی‌متر (۱۶۸۰×۲۳۷۶): `copies` برچسب (تا ۲۴)،
     *  هر کدام وسطِ خانهٔ خودش و به اندازهٔ واقعی (۴۰×۳۰ ⇐ ۳۲۰×۲۴۰ نقطه).
     *  همین تصویر به اندازهٔ کاملِ A4 چاپ می‌شود؛ پس هر نقطه ۰٫۱۲۵ میلی‌متر است.
     */
    fun sheet(label: MonoImage, copies: Int): MonoImage {
      val page = blank()
      for (i in 0 until copies.coerceIn(0, COLS * ROWS)) place(page, label, i)
      return page
    }

    /**
     *  برچسب‌های گوناگون (PRD-23، چند کالا با هم): هر ۲۴ برچسب یک برگه، به همان ترتیب و همان خانه‌بندیِ
     *  `sheet`. تکرارِ هر کالا را صدازننده پیش‌تر در فهرست گذاشته است (`LabelPlan`). فهرستِ خالی ⇒ هیچ برگه.
     */
    fun sheets(labels: List<MonoImage>): List<MonoImage> =
      labels.chunked(COLS * ROWS).map { chunk ->
        val page = blank()
        chunk.forEachIndexed { i, label -> place(page, label, i) }
        page
      }

    private fun blank(): MonoImage = MonoImage(PAGE_W * DOTS_PER_MM / 10, PAGE_H * DOTS_PER_MM / 10)

    /** برچسب وسطِ خانهٔ `index` */
    private fun place(page: MonoImage, label: MonoImage, index: Int) {
      val cw = CELL_W * DOTS_PER_MM / 10
      val ch = CELL_H * DOTS_PER_MM / 10
      val (x, y) = cell(index)
      page.blit(label, x * DOTS_PER_MM / 10 + (cw - label.width) / 2, y * DOTS_PER_MM / 10 + (ch - label.height) / 2)
    }
  }

  /**
   *  بزرگ‌ترین پهنای ماژول (نقطه) که بارکد با حاشیه‌اش در `maxDots` جا شود.
   *  کمتر از ۲ نقطه (۰٫۲۵ میلی‌متر) ⇒ `0`: بارکد برای این برچسب بلند است و
   *  بارکدخوانِ ارزان آن را نمی‌خواند — بهتر است گفته شود تا اینکه برچسبِ
   *  ناخوانا چاپ شود.
   */
  fun moduleDots(modules: Int, maxDots: Int, quiet: Int = 10): Int {
    val m = maxDots / (modules + quiet * 2)
    return if (m >= 2) minOf(m, 4) else 0
  }

  /**
   *  TSPL (چاپگرهای برچسبِ TSC، Xprinter و همانندها).
   *
   *  ⚠️ در `BITMAP` بیتِ **صفر** نقطهٔ سیاه است — وارونهٔ ESC/POS. همین
   *  وارونه‌سازی این‌جا انجام می‌شود. روی چاپگرِ واقعی آزموده نشده است.
   */
  fun tspl(image: MonoImage, size: Size, copies: Int = 1): ByteArray {
    val head = buildString {
      append("SIZE ${size.widthMm} mm,${size.heightMm} mm\r\n")
      append("GAP ${size.gapMm} mm,0 mm\r\n")
      append("DIRECTION 1\r\n")
      append("CLS\r\n")
      append("BITMAP 0,0,${image.rowBytes},${image.height},0,")
    }
    val data = ByteArray(image.bits.size) { (image.bits[it].toInt() xor 0xFF).toByte() }
    //  بیت‌های زیادیِ تهِ هر سطر (پهنای نامضربِ ۸) سفید بمانند
    val pad = image.rowBytes * 8 - image.width
    if (pad > 0) {
      val mask = (1 shl pad) - 1
      for (r in 0 until image.height) {
        val i = r * image.rowBytes + image.rowBytes - 1
        data[i] = (data[i].toInt() or mask).toByte()
      }
    }
    val tail = "\r\nPRINT ${copies.coerceIn(1, 999)},1\r\n"
    return ascii(head) + data + ascii(tail)
  }

  /** برچسب روی چاپگرِ حرارتیِ ESC/POS (کاغذِ برچسب‌دار) — همان تصویر، بی برش */
  fun escPos(image: MonoImage, copies: Int = 1): ByteArray {
    val one = EscPos.raster(image) + EscPos.feed(1)
    var out = EscPos.INIT
    repeat(copies.coerceIn(1, 999)) { out += one }
    return out
  }

  private fun ascii(s: String): ByteArray = ByteArray(s.length) { s[it].code.toByte() }
}
