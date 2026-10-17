package ir.vil3ntec.tohid.migrate

import java.security.MessageDigest

/**
 *  شناسهٔ مدلِ تازه = ULID (بخشِ ۳.۴).
 *
 *  شناسهٔ قدیمی که خودش ULID است همان می‌ماند. بقیه (`Math.random`ِ نسخه‌های
 *  قدیمِ وب، شمارنده‌های دست‌ساز) ULIDِ **قطعی** می‌گیرند: زمان = `createdAt`ِ
 *  همان ردیف، و هشتاد بیتِ تصادفی = SHA-256ِ «موجودیت/شناسه/تکرار». یعنی هر
 *  بار اجرا همان شناسه را می‌سازد و گزارشِ دو اجرا قابلِ مقایسه است.
 *  نگاشتِ قدیم ⇐ تازه در جدولِ `legacyId` می‌ماند.
 */
object Ids {

  private const val B32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private val ULID = Regex("^[0-7][0-9A-HJKMNP-TV-Z]{25}$")

  fun isUlid(s: String): Boolean = ULID.matches(s)

  fun derive(entity: String, legacyId: String, occurrence: Int, timeMs: Long): String {
    if (occurrence == 1 && isUlid(legacyId)) return legacyId
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$entity/$legacyId/$occurrence".toByteArray(Charsets.UTF_8))
    return encode(timeMs.coerceIn(0L, (1L shl 48) - 1), digest.copyOf(10))
  }

  fun encode(timeMs: Long, random10: ByteArray): String {
    require(random10.size == 10)
    val out = CharArray(26)
    var t = timeMs
    for (i in 9 downTo 0) { out[i] = B32[(t % 32).toInt()]; t /= 32 }
    var acc = 0
    var bits = 0
    var pos = 10
    for (b in random10) {
      acc = (acc shl 8) or (b.toInt() and 0xFF)
      bits += 8
      while (bits >= 5) {
        out[pos++] = B32[(acc shr (bits - 5)) and 31]
        bits -= 5
      }
    }
    return String(out)
  }

  /** کلیدِ گزارش برای هر موجودیت — بی نام و بی شناسهٔ خام (بخشِ ۸، بند ۲ مهاجرت) */
  fun anon(prefix: String, legacyId: String): String {
    val d = MessageDigest.getInstance("SHA-256").digest(legacyId.toByteArray(Charsets.UTF_8))
    return prefix + ":" + d.take(5).joinToString("") { "%02x".format(it) }
  }
}
