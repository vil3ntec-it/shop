@file:OptIn(ExperimentalForeignApi::class)

package ir.vil3ntec.tohid.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import platform.Foundation.timeIntervalSince1970
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

internal actual fun secureRandomBytes(size: Int): ByteArray {
  val out = ByteArray(size)
  if (size == 0) return out
  val status = out.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) }
  //  بی تصادفیِ امن هیچ شناسه یا توکنی ساخته نمی‌شود
  check(status == errSecSuccess) { "SecRandomCopyBytes شکست خورد: $status" }
  return out
}

internal actual fun sha256(data: ByteArray): ByteArray {
  val out = UByteArray(CC_SHA256_DIGEST_LENGTH)
  //  addressOf(0) روی آرایهٔ خالی جایز نیست؛ طولِ صفر یعنی هیچ بایتی خوانده نمی‌شود
  val src = if (data.isEmpty()) ByteArray(1) else data
  src.usePinned { s -> out.usePinned { o -> CC_SHA256(s.addressOf(0), data.size.convert(), o.addressOf(0)) } }
  return out.asByteArray()
}

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

private val lock = NSLock()

internal actual fun <T> locked(block: () -> T): T {
  lock.lock()
  try {
    return block()
  } finally {
    lock.unlock()
  }
}
