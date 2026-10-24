package ir.vil3ntec.tohid.core.hw

/**
 *  «چاپ روی کاغذِ خیالی» — همان بایت‌هایی که به چاپگر می‌رود، این‌جا
 *  خوانده و روی یک تصویر کشیده می‌شود (HW-11).
 *
 *  پیش‌نمایش از روی **بایت‌ها** ساخته می‌شود، نه از روی تصویرِ اصلی؛
 *  پس اگر ESC/POS خراب باشد پیش‌نمایش هم خراب است. همین آن را آزمون‌پذیر
 *  می‌کند: `decode(document(img)) == img` در آزمون‌ها، و صفحهٔ دستگاه‌ها
 *  بی چاپگر نشان می‌دهد چه بیرون می‌آید.
 *
 *  فقط دستورهایی که خودِ برنامه می‌سازد شناخته می‌شوند؛ هر بایتِ دیگری
 *  «ناشناخته» گزارش می‌شود و پیش‌نمایش آن را پنهان نمی‌کند.
 */
object EscPosPreview {

  data class Result(
    /** کاغذ — عرضش عرضِ چاپگر است */
    val paper: MonoImage?,
    /** رویدادها به ترتیب: `init`، `raster 384x120`، `cut partial`، `drawer pin2 50/500ms`… */
    val events: List<String>,
    /** بایت‌هایی که شناخته نشدند — باید خالی باشد */
    val unknown: List<String>,
  ) {
    val cuts: Int get() = events.count { it.startsWith("cut") }
    val drawerKicks: Int get() = events.count { it.startsWith("drawer") }
  }

  /** فاصلهٔ پیش‌فرضِ سطر در اپسون: ۳۰ نقطه */
  private const val DEFAULT_LINE = 30

  fun decode(bytes: ByteArray, paperDots: Int): Result {
    val rowBytes = (paperDots + 7) / 8
    val rows = ArrayList<ByteArray>()
    val events = ArrayList<String>()
    val unknown = ArrayList<String>()
    var lineSpacing = DEFAULT_LINE
    //  نوارِ `ESC *` تا رسیدنِ LF روی «سطرِ جاری» است
    var pending: Array<ByteArray>? = null

    fun blank(n: Int) { repeat(n) { rows.add(ByteArray(rowBytes)) } }
    fun u(i: Int) = bytes[i].toInt() and 0xFF

    var i = 0
    while (i < bytes.size) {
      val b = u(i)
      when {
        b == 0x1B && i + 1 < bytes.size && u(i + 1) == 0x40 -> { events += "init"; lineSpacing = DEFAULT_LINE; i += 2 }
        b == 0x1B && i + 2 < bytes.size && u(i + 1) == 0x33 -> { lineSpacing = u(i + 2); events += "spacing ${u(i + 2)}"; i += 3 }
        b == 0x1B && i + 1 < bytes.size && u(i + 1) == 0x32 -> { lineSpacing = DEFAULT_LINE; events += "spacing default"; i += 2 }
        b == 0x1B && i + 4 < bytes.size && u(i + 1) == 0x70 -> {
          events += "drawer pin${if (u(i + 2) == 1) 5 else 2} ${u(i + 3) * 2}/${u(i + 4) * 2}ms"; i += 5
        }
        b == 0x1B && i + 4 < bytes.size && u(i + 1) == 0x2A -> {
          val m = u(i + 2)
          val n = u(i + 3) or (u(i + 4) shl 8)
          val tall = if (m == 32 || m == 33) 3 else 1
          val start = i + 5
          if (start + n * tall > bytes.size) { unknown += "ESC * کوتاه"; break }
          val band = Array(tall * 8) { ByteArray(rowBytes) }
          for (x in 0 until minOf(n, paperDots)) for (k in 0 until tall) {
            val v = u(start + x * tall + k)
            for (bit in 0 until 8) if (v and (0x80 ushr bit) != 0) {
              val r = band[k * 8 + bit]
              r[x shr 3] = (r[x shr 3].toInt() or (0x80 ushr (x and 7))).toByte()
            }
          }
          pending = band
          events += "column ${n}x${tall * 8}"
          i = start + n * tall
        }
        b == 0x1D && i + 7 < bytes.size && u(i + 1) == 0x76 && u(i + 2) == 0x30 -> {
          val xb = u(i + 4) or (u(i + 5) shl 8)
          val h = u(i + 6) or (u(i + 7) shl 8)
          val start = i + 8
          if (start + xb * h > bytes.size) { unknown += "GS v 0 کوتاه"; break }
          for (r in 0 until h) {
            val row = ByteArray(rowBytes)
            bytes.copyInto(row, 0, start + r * xb, start + r * xb + minOf(xb, rowBytes))
            rows.add(row)
          }
          events += "raster ${xb * 8}x$h"
          i = start + xb * h
        }
        b == 0x1D && i + 2 < bytes.size && u(i + 1) == 0x56 -> {
          val m = u(i + 2)
          val withN = m == 0x41 || m == 0x42
          events += "cut ${if (m == 0x42 || m == 0x01 || m == 0x31) "partial" else "full"}"
          i += if (withN) 4 else 3
        }
        b == 0x10 && i + 2 < bytes.size && u(i + 1) == 0x04 -> { events += "status ${u(i + 2)}"; i += 3 }
        b == 0x0A -> {
          val band = pending
          if (band != null) {
            band.forEach { rows.add(it) }
            //  فاصلهٔ سطرِ بیشتر از نوار ⇒ سفید؛ کمتر ⇒ نوار روی هم نمی‌رود (پیش‌نمایشِ ساده)
            blank((lineSpacing - band.size).coerceAtLeast(0))
            pending = null
          } else blank(lineSpacing)
          events += "feed"
          i += 1
        }
        else -> { unknown += "0x" + b.toString(16).padStart(2, '0') + "@$i"; i += 1 }
      }
    }
    pending?.forEach { rows.add(it) }

    val paper = if (rows.isEmpty()) null else MonoImage(paperDots, rows.size, ByteArray(rowBytes * rows.size).also { all ->
      rows.forEachIndexed { r, row -> row.copyInto(all, r * rowBytes) }
    })
    return Result(paper, events, unknown)
  }
}
