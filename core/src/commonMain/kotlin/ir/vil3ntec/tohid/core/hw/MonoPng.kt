package ir.vil3ntec.tohid.core.hw

/**
 *  «چاپ به PNG» — کاغذِ خیالیِ `EscPosPreview` یا یک برچسب، به‌صورتِ یک
 *  فایلِ PNGِ سیاه‌وسفید (HW-11).
 *
 *  خالص است (بی javax.imageio و بی android.graphics) تا اندروید، کامپیوتر
 *  و آزمون‌ها یک خروجی بدهند. فشرده‌سازی ندارد (بلوک‌های «stored»ِ
 *  deflate)؛ تصویرِ یک‌بیتیِ رسید همین‌طور هم کوچک است.
 */
object MonoPng {

  fun encode(image: MonoImage): ByteArray {
    val rb = image.rowBytes
    //  هر سطر: بایتِ صافی (۰) و سپس نقطه‌ها؛ در PNGِ خاکستریِ یک‌بیتی ۰ سیاه است ⇒ وارونه
    val raw = ByteArray((rb + 1) * image.height)
    val pad = rb * 8 - image.width
    for (y in 0 until image.height) {
      val at = y * (rb + 1)
      raw[at] = 0
      for (i in 0 until rb) raw[at + 1 + i] = (image.bits[y * rb + i].toInt() xor 0xFF).toByte()
      if (pad > 0) raw[at + rb] = (raw[at + rb].toInt() or ((1 shl pad) - 1)).toByte()
    }
    val out = EscPos.Bytes()
    out.add(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    val ihdr = ByteArray(13)
    put32(ihdr, 0, image.width); put32(ihdr, 4, image.height)
    ihdr[8] = 1 // عمقِ بیت
    ihdr[9] = 0 // خاکستری
    chunk(out, "IHDR", ihdr)
    chunk(out, "IDAT", zlibStored(raw))
    chunk(out, "IEND", ByteArray(0))
    return out.toByteArray()
  }

  private fun zlibStored(data: ByteArray): ByteArray {
    val out = EscPos.Bytes()
    out.add(byteArrayOf(0x78, 0x01))
    var at = 0
    do {
      val n = minOf(65_535, data.size - at)
      val last = at + n >= data.size
      out.add(byteArrayOf(if (last) 1 else 0, (n and 0xFF).toByte(), (n shr 8).toByte(), (n.inv() and 0xFF).toByte(), ((n.inv() shr 8) and 0xFF).toByte()))
      out.add(data, at, n)
      at += n
    } while (!last)
    var a = 1L
    var b = 0L
    for (x in data) { a = (a + (x.toInt() and 0xFF)) % 65_521; b = (b + a) % 65_521 }
    val adler = ByteArray(4)
    put32(adler, 0, ((b shl 16) or a).toInt())
    out.add(adler)
    return out.toByteArray()
  }

  private fun chunk(out: EscPos.Bytes, type: String, data: ByteArray) {
    val len = ByteArray(4)
    put32(len, 0, data.size)
    out.add(len)
    val td = ByteArray(4 + data.size)
    for (i in 0 until 4) td[i] = type[i].code.toByte()
    data.copyInto(td, 4)
    out.add(td)
    val crc = ByteArray(4)
    put32(crc, 0, crc32(td))
    out.add(crc)
  }

  private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
    c
  }

  private fun crc32(data: ByteArray): Int {
    var c = -1
    for (x in data) c = CRC_TABLE[(c xor x.toInt()) and 0xFF] xor (c ushr 8)
    return c.inv()
  }

  private fun put32(b: ByteArray, at: Int, v: Int) {
    b[at] = (v ushr 24).toByte(); b[at + 1] = (v ushr 16).toByte(); b[at + 2] = (v ushr 8).toByte(); b[at + 3] = v.toByte()
  }
}
