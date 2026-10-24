package ir.vil3ntec.tohid.core.qr

import kotlin.math.abs

/**
 *  QR برای فاکتور — کاتلینِ خالص، بی کتابخانه (فاکتورِ نسخهٔ ۲؛ از `core/qr` ِ #318 با
 *  نسخه‌های بیشتر).
 *
 *  فقط همان چیزی که فاکتور لازم دارد: حالتِ بایت، سطحِ تصحیحِ M، نسخهٔ ۱ تا ۶ (تا ۱۰۶
 *  بایت؛ نشانیِ `<سرور>/r/<نشان>/<توکن>` حدودِ ۶۰ بایت است). از نسخهٔ ۴ داده در چند
 *  بلوکِ رید-سولومون می‌رود و ستونی در هم بافته می‌شود (ISO/IEC 18004، جدولِ ۹). نسخهٔ
 *  ۱ تا ۶ بلوکِ «اطلاعاتِ نسخه» ندارد. بهترین ماسک با چهار قاعدهٔ جریمهٔ استاندارد.
 *
 *  ⛔ خروجی با `core/test-vectors/receipt-qr-v1.json` سنجیده می‌شود (`QrCodeVersionsTest`) و
 *  `server/test/receipt-qr-v2.test.js` همان ماتریس‌ها را با خوانندهٔ مستقلِ zxing
 *  (`license/vendor`) می‌خواند — درستیِ رمزگذاری با یک پیاده‌سازیِ دیگر سنجیده می‌شود، نه با
 *  خودش. نسخهٔ ۱ تا ۳ بیت‌به‌بیت همان رمزگذارِ #318 است. این را عوض کنی، بردار را هم.
 *
 *  خروجی: `rows[y][x]` (true = سیاه)، بی حاشیهٔ خالی؛ کشنده ۴ خانه حاشیه می‌گذارد.
 */
object QrCode {

  const val MAX_VERSION = 6

  /** حاشیهٔ خالیِ استاندارد، به خانه */
  const val QUIET_ZONE = 4

  /** سطحِ M: [بلوک‌ها، دادهٔ هر بلوک، تصحیحِ هر بلوک] برای نسخهٔ ۱..۶ */
  private val BLOCKS = arrayOf(
    intArrayOf(0, 0, 0),
    intArrayOf(1, 16, 10),
    intArrayOf(1, 28, 16),
    intArrayOf(1, 44, 26),
    intArrayOf(2, 32, 18),
    intArrayOf(2, 43, 24),
    intArrayOf(4, 27, 16),
  )

  /** مرکزِ الگوی هم‌ترازی (نسخهٔ ۱ ندارد؛ ۲ تا ۶ فقط یکی) */
  private val ALIGN = intArrayOf(0, 0, 18, 22, 26, 30, 34)

  private fun dataCodewords(version: Int) = BLOCKS[version][0] * BLOCKS[version][1]

  /** بیشترین بایتِ متن در این نسخه (حالتِ بایت: ۴ بیتِ حالت + ۸ بیتِ طول) */
  fun capacity(version: Int): Int = (dataCodewords(version) * 8 - 12) / 8

  /** بیشترین بایتِ ممکن (نسخهٔ ۶، سطحِ M) */
  val MAX_BYTES: Int get() = capacity(MAX_VERSION)

  /** کوچک‌ترین نسخه‌ای که این متن در آن جا می‌شود */
  fun versionFor(text: String): Int {
    val n = text.encodeToByteArray().size
    return (1..MAX_VERSION).firstOrNull { capacity(it) >= n }
      ?: throw IllegalArgumentException("متنِ QR بیش از ${MAX_BYTES} بایت است")
  }

  fun encode(text: String): Array<BooleanArray> {
    val bytes = text.encodeToByteArray()
    val version = versionFor(text)
    val size = 17 + 4 * version
    val m = Matrix(size)
    m.functionPatterns(version)
    m.placeData(codewords(bytes, version))
    var best = -1
    var bestScore = Int.MAX_VALUE
    for (mask in 0 until 8) {
      m.mask(mask)
      m.formatBits(mask)
      val s = m.penalty()
      if (s < bestScore) { bestScore = s; best = mask }
      m.mask(mask) // برگرداندن
    }
    m.mask(best)
    m.formatBits(best)
    return Array(size) { y -> BooleanArray(size) { x -> m.dark[y][x] } }
  }

  /** دادهٔ کامل: حالت + طول + بایت‌ها + پایان + پُرکن؛ بعد بلوک‌ها و تصحیح، در هم بافته */
  private fun codewords(bytes: ByteArray, version: Int): IntArray {
    val cap = dataCodewords(version)
    val bits = ArrayList<Boolean>()
    fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += ((v ushr i) and 1) == 1 }
    put(0b0100, 4)
    put(bytes.size, 8)
    bytes.forEach { put(it.toInt() and 0xFF, 8) }
    repeat(minOf(4, cap * 8 - bits.size)) { bits += false }
    while (bits.size % 8 != 0) bits += false
    val data = IntArray(cap)
    var i = 0
    while (i * 8 < bits.size) {
      var b = 0
      for (k in 0 until 8) b = (b shl 1) or (if (bits[i * 8 + k]) 1 else 0)
      data[i++] = b
    }
    var pad = 0xEC
    while (i < cap) { data[i++] = pad; pad = if (pad == 0xEC) 0x11 else 0xEC }

    val (count, perBlock, ecLen) = BLOCKS[version].let { Triple(it[0], it[1], it[2]) }
    val div = divisor(ecLen)
    val blocks = List(count) { data.copyOfRange(it * perBlock, (it + 1) * perBlock) }
    val ecs = blocks.map { remainder(it, div) }
    val out = IntArray(cap + count * ecLen)
    var o = 0
    for (c in 0 until perBlock) for (b in blocks) out[o++] = b[c]
    for (c in 0 until ecLen) for (e in ecs) out[o++] = e[c]
    return out
  }

  private fun mul(x: Int, y: Int): Int {
    var z = 0
    for (i in 7 downTo 0) {
      z = (z shl 1) xor ((z ushr 7) * 0x11D)
      z = z xor (((y ushr i) and 1) * x)
    }
    return z
  }

  private fun divisor(degree: Int): IntArray {
    val r = IntArray(degree)
    r[degree - 1] = 1
    var root = 1
    for (i in 0 until degree) {
      for (j in r.indices) {
        r[j] = mul(r[j], root)
        if (j + 1 < r.size) r[j] = r[j] xor r[j + 1]
      }
      root = mul(root, 0x02)
    }
    return r
  }

  private fun remainder(data: IntArray, div: IntArray): IntArray {
    val r = IntArray(div.size)
    for (b in data) {
      val factor = b xor r[0]
      r.copyInto(r, 0, 1, r.size)
      r[r.size - 1] = 0
      for (i in r.indices) r[i] = r[i] xor mul(div[i], factor)
    }
    return r
  }

  private class Matrix(val size: Int) {
    val dark = Array(size) { BooleanArray(size) }
    val fixed = Array(size) { BooleanArray(size) }

    fun set(x: Int, y: Int, v: Boolean) { dark[y][x] = v; fixed[y][x] = true }

    fun functionPatterns(version: Int) {
      for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
      finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
      val a = ALIGN[version]
      if (a > 0) for (dy in -2..2) for (dx in -2..2) set(a + dx, a + dy, maxOf(abs(dx), abs(dy)) != 1)
      formatBits(0) // رزرو؛ بعد از ماسک دوباره نوشته می‌شود
    }

    private fun finder(cx: Int, cy: Int) {
      for (dy in -4..4) for (dx in -4..4) {
        val x = cx + dx; val y = cy + dy
        if (x in 0 until size && y in 0 until size) {
          val dist = maxOf(abs(dx), abs(dy))
          set(x, y, dist != 2 && dist != 4)
        }
      }
    }

    /** سطحِ M = ۰۰ */
    fun formatBits(mask: Int) {
      val data = (0 shl 3) or mask
      var rem = data
      for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
      val bits = ((data shl 10) or rem) xor 0x5412
      fun bit(i: Int) = ((bits ushr i) and 1) == 1
      for (i in 0..5) set(8, i, bit(i))
      set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
      for (i in 9 until 15) set(14 - i, 8, bit(i))
      for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
      for (i in 8 until 15) set(8, size - 15 + i, bit(i))
      set(8, size - 8, true)
    }

    /** بیت‌های اضافیِ آخر (نسخهٔ ۲ تا ۶: هفت بیت) روشن می‌مانند — همان صفرِ استاندارد */
    fun placeData(data: IntArray) {
      var i = 0
      var right = size - 1
      while (right >= 1) {
        if (right == 6) right = 5
        for (vert in 0 until size) for (j in 0..1) {
          val x = right - j
          val upward = ((right + 1) and 2) == 0
          val y = if (upward) size - 1 - vert else vert
          if (!fixed[y][x] && i < data.size * 8) {
            dark[y][x] = ((data[i ushr 3] ushr (7 - (i and 7))) and 1) == 1
            i++
          }
        }
        right -= 2
      }
    }

    fun mask(m: Int) {
      for (y in 0 until size) for (x in 0 until size) {
        if (fixed[y][x]) continue
        val flip = when (m) {
          0 -> (x + y) % 2 == 0
          1 -> y % 2 == 0
          2 -> x % 3 == 0
          3 -> (x + y) % 3 == 0
          4 -> (x / 3 + y / 2) % 2 == 0
          5 -> x * y % 2 + x * y % 3 == 0
          6 -> (x * y % 2 + x * y % 3) % 2 == 0
          else -> ((x + y) % 2 + x * y % 3) % 2 == 0
        }
        if (flip) dark[y][x] = !dark[y][x]
      }
    }

    fun penalty(): Int {
      var score = 0
      fun at(x: Int, y: Int, col: Boolean) = if (col) dark[x][y] else dark[y][x]
      for (col in listOf(false, true)) for (a in 0 until size) {
        // قاعدهٔ ۱: پنج یا بیشتر هم‌رنگِ پیاپی
        var run = 1
        for (b in 1 until size) {
          if (at(b, a, col) == at(b - 1, a, col)) {
            run++
            if (run == 5) score += 3 else if (run > 5) score++
          } else run = 1
        }
        // قاعدهٔ ۳: الگوی شبیهِ نشانگر (۱:۱:۳:۱:۱ با چهار روشنِ کنارش)
        for (b in 0..size - 11) {
          val p = BooleanArray(11) { at(b + it, a, col) }
          val core = p[0] && !p[1] && p[2] && p[3] && p[4] && !p[5] && p[6]
          val coreR = p[4] && !p[5] && p[6] && p[7] && p[8] && !p[9] && p[10]
          if (core && !p[7] && !p[8] && !p[9] && !p[10]) score += 40
          if (coreR && !p[0] && !p[1] && !p[2] && !p[3]) score += 40
        }
      }
      // قاعدهٔ ۲: مربعِ ۲×۲ هم‌رنگ
      for (y in 0 until size - 1) for (x in 0 until size - 1) {
        val c = dark[y][x]
        if (c == dark[y][x + 1] && c == dark[y + 1][x] && c == dark[y + 1][x + 1]) score += 3
      }
      // قاعدهٔ ۴: نسبتِ سیاه
      var black = 0
      for (row in dark) for (v in row) if (v) black++
      val total = size * size
      val k = (abs(black * 20 - total * 10) + total - 1) / total - 1
      score += k * 10
      return score
    }
  }
}
