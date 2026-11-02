package java.security

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH

/*
 *  shimِ iOS ِ ریشهٔ رمزنگاریِ JDK (گامِ ۲-ب): ردهٔ خطاها، `Key`، و `MessageDigest` روی CommonCrypto.
 *  نامِ ناشناخته ⇐ NoSuchAlgorithmException (مثلِ JDK)، نه چکیدهٔ دیگر.
 */
open class GeneralSecurityException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
open class NoSuchAlgorithmException(message: String? = null) : GeneralSecurityException(message)
open class InvalidKeyException(message: String? = null) : GeneralSecurityException(message)
open class InvalidAlgorithmParameterException(message: String? = null) : GeneralSecurityException(message)

interface Key {
  val algorithm: String
  val format: String
  val encoded: ByteArray
}

class MessageDigest private constructor(val algorithm: String) {
  private val buffer = java.io.ByteArrayOutputStream()

  fun update(input: ByteArray) = buffer.write(input, 0, input.size)
  fun update(input: ByteArray, offset: Int, len: Int) = buffer.write(input, offset, len)
  fun update(b: Byte) = buffer.write(b.toInt())
  fun reset() = buffer.reset()

  fun digest(input: ByteArray): ByteArray { update(input); return digest() }

  fun digest(): ByteArray {
    val data = buffer.toByteArray()
    buffer.reset()
    return hash(algorithm, data)
  }

  val digestLength: Int get() = hash(algorithm, ByteArray(0)).size

  companion object {
    fun getInstance(algorithm: String): MessageDigest {
      val name = algorithm.uppercase().replace("-", "")
      if (name !in setOf("SHA256", "SHA1", "SHA512", "MD5")) throw NoSuchAlgorithmException("$algorithm MessageDigest not available")
      return MessageDigest(name)
    }

    internal fun hash(name: String, data: ByteArray): ByteArray = memScoped {
      val len = when (name) {
        "SHA256" -> CC_SHA256_DIGEST_LENGTH
        "SHA1" -> CC_SHA1_DIGEST_LENGTH
        "SHA512" -> CC_SHA512_DIGEST_LENGTH
        else -> CC_MD5_DIGEST_LENGTH
      }
      val out = allocArray<UByteVar>(len)
      val input = if (data.isEmpty()) ByteArray(1) else data
      input.usePinned { p ->
        val n = data.size.toUInt()
        when (name) {
          "SHA256" -> CC_SHA256(p.addressOf(0), n, out)
          "SHA1" -> CC_SHA1(p.addressOf(0), n, out)
          "SHA512" -> CC_SHA512(p.addressOf(0), n, out)
          else -> CC_MD5(p.addressOf(0), n, out)
        }
      }
      out.readBytes(len)
    }
  }
}
