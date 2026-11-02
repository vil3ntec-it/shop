package ir.vil3ntec.tohid.ios.crypto

/*
 *  AES-GCM (NIST SP 800-38D) روی یک «رمزِ بلوکی» ِ بیرونی — گامِ ۲-ب ِ برنامهٔ آیفون.
 *
 *  iOS، AES-GCM ِ عمومیِ C ندارد (CommonCrypto فقط بلوک/ECB/CTR؛ CryptoKit فقط Swift). پس خودِ AES از
 *  CommonCrypto می‌آید (سخت‌افزاری، `CCCrypt` در حالتِ ECB، ‎ios/…/javax/crypto‎) و فقط «حالتِ GCM» این‌جاست:
 *  شمارنده (GCTR) و GHASH. همین فایل روی JVM هم کامپایل و با `AES/GCM/NoPadding` ِ JDK بیت‌به‌بیت سنجیده
 *  می‌شود (`ios/shared/src/jvmTest`)، پس پاکتی که اندروید یا کامپیوتر نوشته در آیفون باز می‌شود و برعکس.
 *
 *  ⛔ ضربِ GHASH بی جدول و بی شاخه روی داده است (ماسک، نه if) تا زمانش به کلید و داده بسته نباشد.
 *  ⛔ رمزگشایی پیش از درست بودنِ برچسب هیچ متنی برنمی‌گرداند؛ مقایسهٔ برچسب زمانِ ثابت است.
 */
fun interface AesBlocks {
  /** AES روی بلوک‌های پشتِ‌هم (اندازه مضربِ ۱۶)، بی حالت — همان ECB */
  fun encrypt(blocks: ByteArray): ByteArray
}

class GcmTagMismatch : Exception("AES-GCM: برچسب نمی‌خواند")

object Gcm {
  private const val BLOCK = 16
  private const val CHUNK_BLOCKS = 4096

  /** رمزنوشته + برچسب (`tagBytes` بایت، ۱۲ تا ۱۶) — همان خروجیِ `Cipher.doFinal` ِ JDK */
  fun seal(aes: AesBlocks, iv: ByteArray, aad: ByteArray, plain: ByteArray, tagBytes: Int = 16): ByteArray {
    require(tagBytes in 12..16) { "AES-GCM: طولِ برچسب" }
    val h = hashKey(aes)
    val j0 = j0(h, iv)
    val out = ByteArray(plain.size + tagBytes)
    gctr(aes, inc32(j0), plain, out)
    val tag = tag(aes, h, j0, aad, out, plain.size)
    tag.copyInto(out, plain.size, 0, tagBytes)
    return out
  }

  /** متنِ ساده، یا [GcmTagMismatch] اگر برچسب نخواند (پیش از آن هیچ متنی برنمی‌گردد) */
  fun open(aes: AesBlocks, iv: ByteArray, aad: ByteArray, sealed: ByteArray, tagBytes: Int = 16): ByteArray {
    require(tagBytes in 12..16) { "AES-GCM: طولِ برچسب" }
    if (sealed.size < tagBytes) throw GcmTagMismatch()
    val n = sealed.size - tagBytes
    val h = hashKey(aes)
    val j0 = j0(h, iv)
    val expected = tag(aes, h, j0, aad, sealed, n)
    var diff = 0
    for (i in 0 until tagBytes) diff = diff or (expected[i].toInt() xor sealed[n + i].toInt())
    if (diff != 0) throw GcmTagMismatch()
    val out = ByteArray(n)
    gctr(aes, inc32(j0), sealed.copyOf(n), out)
    return out
  }

  private fun hashKey(aes: AesBlocks): LongArray = toWords(aes.encrypt(ByteArray(BLOCK)), 0)

  private fun j0(h: LongArray, iv: ByteArray): ByteArray {
    require(iv.isNotEmpty()) { "AES-GCM: IV ِ خالی" }
    if (iv.size == 12) return iv.copyOf(BLOCK).also { it[15] = 1 }
    val g = Ghash(h)
    g.update(iv, 0, iv.size)
    g.lengths(0L, iv.size.toLong() * 8)
    return g.digest()
  }

  private fun tag(aes: AesBlocks, h: LongArray, j0: ByteArray, aad: ByteArray, cipher: ByteArray, cipherLen: Int): ByteArray {
    val g = Ghash(h)
    g.update(aad, 0, aad.size)
    g.update(cipher, 0, cipherLen)
    g.lengths(aad.size.toLong() * 8, cipherLen.toLong() * 8)
    val s = g.digest()
    val ek = aes.encrypt(j0)
    for (i in 0 until BLOCK) s[i] = (s[i].toInt() xor ek[i].toInt()).toByte()
    return s
  }

  /** GCTR: هر بلوک XOR با AES ِ شمارنده (۳۲ بیتِ پایینِ شمارنده چرخشی) */
  private fun gctr(aes: AesBlocks, start: ByteArray, input: ByteArray, out: ByteArray) {
    val ctr = start.copyOf()
    var off = 0
    val total = input.size
    while (off < total) {
      val blocks = minOf(CHUNK_BLOCKS, (total - off + BLOCK - 1) / BLOCK)
      val counters = ByteArray(blocks * BLOCK)
      for (b in 0 until blocks) {
        ctr.copyInto(counters, b * BLOCK)
        incInPlace(ctr)
      }
      val stream = aes.encrypt(counters)
      val n = minOf(blocks * BLOCK, total - off)
      for (i in 0 until n) out[off + i] = (input[off + i].toInt() xor stream[i].toInt()).toByte()
      off += n
    }
  }

  private fun inc32(b: ByteArray): ByteArray = b.copyOf().also { incInPlace(it) }

  private fun incInPlace(b: ByteArray) {
    var i = 15
    while (i >= 12) {
      val v = (b[i].toInt() and 0xFF) + 1
      b[i] = v.toByte()
      if (v < 0x100) return
      i--
    }
  }

  internal fun toWords(b: ByteArray, off: Int): LongArray {
    var hi = 0L
    var lo = 0L
    for (i in 0 until 8) hi = (hi shl 8) or (b[off + i].toLong() and 0xFF)
    for (i in 8 until 16) lo = (lo shl 8) or (b[off + i].toLong() and 0xFF)
    return longArrayOf(hi, lo)
  }

  /** GHASH با ضربِ بیت‌به‌بیتِ GF(2^128)، چندجمله‌ایِ x^128 + x^7 + x^2 + x + 1 (نمایشِ وارونهٔ GCM) */
  private class Ghash(h: LongArray) {
    private val hHi = h[0]
    private val hLo = h[1]
    private var yHi = 0L
    private var yLo = 0L
    private val pending = ByteArray(BLOCK)
    private var fill = 0

    fun update(b: ByteArray, off: Int, len: Int) {
      var i = off
      val end = off + len
      while (i < end) {
        pending[fill++] = b[i++]
        if (fill == BLOCK) { absorb(pending); fill = 0 }
      }
      //  هر بخش (AAD، رمزنوشته، IV) جدا تا مرزِ بلوک با صفر پر می‌شود
      if (fill > 0) {
        for (k in fill until BLOCK) pending[k] = 0
        absorb(pending)
        fill = 0
      }
    }

    fun lengths(aBits: Long, cBits: Long) {
      yHi = yHi xor aBits
      yLo = yLo xor cBits
      mul()
    }

    fun digest(): ByteArray {
      val out = ByteArray(BLOCK)
      for (i in 0 until 8) out[i] = (yHi ushr (56 - 8 * i)).toByte()
      for (i in 0 until 8) out[8 + i] = (yLo ushr (56 - 8 * i)).toByte()
      return out
    }

    private fun absorb(block: ByteArray) {
      val w = toWords(block, 0)
      yHi = yHi xor w[0]
      yLo = yLo xor w[1]
      mul()
    }

    /** Y ⇐ Y · H */
    private fun mul() {
      var zHi = 0L
      var zLo = 0L
      var vHi = hHi
      var vLo = hLo
      for (i in 0 until 128) {
        val word = if (i < 64) yHi else yLo
        val bit = (word ushr (63 - (i and 63))) and 1L
        val m = -bit
        zHi = zHi xor (vHi and m)
        zLo = zLo xor (vLo and m)
        val carry = -(vLo and 1L)
        vLo = (vLo ushr 1) or (vHi shl 63)
        vHi = (vHi ushr 1) xor (R and carry)
      }
      yHi = zHi
      yLo = zLo
    }

    companion object {
      private const val R = -0x1f00000000000000L // 0xE1 << 56
    }
  }
}
