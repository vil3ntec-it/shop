package ir.vil3ntec.tohid.core.hw

/**
 *  تصویرِ یک‌بیتی — همان چیزی که چاپگرِ حرارتی یا برچسب روی کاغذ می‌زند.
 *
 *  هر سطر `rowBytes` بایت است و هر بیت یک نقطه؛ بیتِ پرارزش = نقطهٔ چپ‌تر.
 *  `true` یعنی «سیاه» (نقطه داغ می‌شود). همین قالب را `GS v 0` می‌خواهد،
 *  پس چاپ رستری فقط یک کپی است.
 *
 *  خالص است — نه android.graphics، نه java.awt — تا اندروید، کامپیوتر و
 *  آزمون‌ها همین یک کد را بدوانند (HW-01).
 */
class MonoImage(val width: Int, val height: Int, val bits: ByteArray = ByteArray(((width + 7) / 8) * height)) {

  init {
    require(width > 0 && height > 0) { "اندازهٔ تصویر باید مثبت باشد" }
    require(bits.size == rowBytes * height) { "اندازهٔ داده با تصویر نمی‌خواند" }
  }

  val rowBytes: Int get() = (width + 7) / 8

  operator fun get(x: Int, y: Int): Boolean {
    if (x !in 0 until width || y !in 0 until height) return false
    return (bits[y * rowBytes + (x shr 3)].toInt() and (0x80 ushr (x and 7))) != 0
  }

  operator fun set(x: Int, y: Int, black: Boolean) {
    if (x !in 0 until width || y !in 0 until height) return
    val i = y * rowBytes + (x shr 3)
    val m = 0x80 ushr (x and 7)
    bits[i] = (if (black) bits[i].toInt() or m else bits[i].toInt() and m.inv()).toByte()
  }

  /** یک مستطیلِ سیاه — برای خطِ بارکد و کادر */
  fun fill(x0: Int, y0: Int, w: Int, h: Int, black: Boolean = true) {
    for (y in y0 until y0 + h) for (x in x0 until x0 + w) this[x, y] = black
  }

  /** چسباندنِ تصویرِ دیگر؛ فقط نقطه‌های سیاهِ آن (پس‌زمینه دست نمی‌خورد) */
  fun blit(src: MonoImage, x0: Int, y0: Int) {
    for (y in 0 until src.height) for (x in 0 until src.width) if (src[x, y]) this[x0 + x, y0 + y] = true
  }

  fun blackCount(): Int {
    var n = 0
    for (b in bits) n += (b.toInt() and 0xFF).countOneBits()
    return n
  }

  override fun equals(other: Any?): Boolean =
    other is MonoImage && other.width == width && other.height == height && other.bits.contentEquals(bits)

  override fun hashCode(): Int = (width * 31 + height) * 31 + bits.contentHashCode()

  companion object {

    /**
     *  از پیکسل‌های ARGB (همان `Bitmap.getPixels`ِ اندروید) به یک‌بیتی.
     *
     *  قاعده **عیناً** همانِ `ThermalPrinter.writeImage`ِ پیشین است — آزمونِ
     *  مشخصه (`hw-v1.json` ⇐ `legacyRaster`) همین را می‌پاید:
     *    • آلفا کمتر از ۱۲۸ ⇒ سفید (وگرنه پس‌زمینهٔ شفاف کلِ کاغذ را سیاه می‌کرد)
     *    • روشنایی `0.299R + 0.587G + 0.114B` کمتر از ۱۲۸ ⇒ سیاه
     *    • ستون‌های بیرون از تصویر سفیدند؛ عرضِ خروجی همان `paperWidth` است
     */
    fun fromArgb(argb: IntArray, width: Int, height: Int, paperWidth: Int = width): MonoImage {
      require(argb.size >= width * height) { "پیکسل کم است" }
      val out = MonoImage(paperWidth, height)
      for (y in 0 until height) for (x in 0 until minOf(width, paperWidth)) {
        val p = argb[y * width + x]
        if (isBlack((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF, (p ushr 24) and 0xFF)) out[x, y] = true
      }
      return out
    }

    /** همان، از بایت‌های RGBA (کانواسِ وب و آزمون‌ها) */
    fun fromRgba(rgba: ByteArray, width: Int, height: Int, paperWidth: Int = width): MonoImage {
      require(rgba.size >= width * height * 4) { "پیکسل کم است" }
      val out = MonoImage(paperWidth, height)
      for (y in 0 until height) for (x in 0 until minOf(width, paperWidth)) {
        val i = (y * width + x) * 4
        val r = rgba[i].toInt() and 0xFF
        val g = rgba[i + 1].toInt() and 0xFF
        val b = rgba[i + 2].toInt() and 0xFF
        val a = rgba[i + 3].toInt() and 0xFF
        if (isBlack(r, g, b, a)) out[x, y] = true
      }
      return out
    }

    fun isBlack(r: Int, g: Int, b: Int, a: Int): Boolean {
      if (a < 128) return false
      val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
      return lum < 128
    }
  }
}
