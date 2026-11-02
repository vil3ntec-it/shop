package java.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/** shimِ iOS ِ `SecureRandom` — `SecRandomCopyBytes` ِ خودِ iOS (همان منبعِ `core/iosMain`)؛ شکست ⇐ خطا، نه بایتِ ضعیف */
open class SecureRandom {
  open fun nextBytes(bytes: ByteArray) {
    if (bytes.isEmpty()) return
    val rc = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), it.addressOf(0)) }
    check(rc == errSecSuccess) { "SecRandomCopyBytes: $rc" }
  }

  fun nextInt(bound: Int): Int {
    require(bound > 0)
    val b = ByteArray(4)
    while (true) {
      nextBytes(b)
      val r = ((b[0].toInt() and 0x7F) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
      val v = r % bound
      if (r - v + (bound - 1) >= 0) return v
    }
  }

  fun nextLong(): Long {
    val b = ByteArray(8)
    nextBytes(b)
    return b.fold(0L) { acc, x -> (acc shl 8) or (x.toLong() and 0xFF) }
  }
}
