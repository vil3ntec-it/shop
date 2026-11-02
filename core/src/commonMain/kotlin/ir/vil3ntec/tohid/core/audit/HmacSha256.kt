package ir.vil3ntec.tohid.core.audit

/**
 *  HMAC-SHA256 (RFC 2104) روی همان [Sha256] ِ دست‌نویسِ commonMain — بی وابستگی، تا آیفون هم همان را بسازد.
 *  کاربرِ نخست: امضای بارکدهای «کارتِ فرمان» با رازِ همین دستگاه (`core/hw/ScanSale.kt`). بردارها: RFC 4231
 *  (`HmacSha256Test`).
 */
object HmacSha256 {
  private const val BLOCK = 64

  fun mac(key: ByteArray, message: ByteArray): ByteArray {
    val k = (if (key.size > BLOCK) Sha256.digest(key) else key).copyOf(BLOCK)
    val inner = ByteArray(BLOCK) { (k[it].toInt() xor 0x36).toByte() }
    val outer = ByteArray(BLOCK) { (k[it].toInt() xor 0x5c).toByte() }
    return Sha256.digest(outer + Sha256.digest(inner + message))
  }

  /** hex ِ کوچک‌حرف ِ HMAC ِ UTF-8 ِ [message] */
  fun hexOf(key: ByteArray, message: String): String = Sha256.hex(mac(key, message.encodeToByteArray()))

  /** hex (زوج‌رقم) ⇒ بایت؛ نویسهٔ نادرست ⇒ `null` */
  fun bytesOfHex(hex: String): ByteArray? {
    val h = hex.trim()
    if (h.isEmpty() || h.length % 2 != 0) return null
    val out = ByteArray(h.length / 2)
    for (i in out.indices) {
      val hi = h[2 * i].digitToIntOrNull(16) ?: return null
      val lo = h[2 * i + 1].digitToIntOrNull(16) ?: return null
      out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
  }
}
