package java.security

import java.net.toNSData
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSNumber
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256

/*
 *  shimِ iOS ِ وارسیِ امضای ECDSA P-256 ِ JDK (`KeyFactory("EC")` + `X509EncodedKeySpec` +
 *  `Signature("SHA256withECDSA")`) روی Security.framework — برای وارسیِ امضای مجوز (`sync/License`).
 *  همان ورودی‌ها: کلید SubjectPublicKeyInfo ِ DER، امضا DER ِ X9.62. فقط وارسی؛ امضا کردن نیست.
 *  منحنیِ دیگر یا کلیدِ بدشکل ⇐ InvalidKeySpecException؛ امضای بدشکل ⇐ false (مثلِ JDK ⇐ SignatureException).
 */
interface PublicKey : Key

open class SignatureException(message: String? = null) : GeneralSecurityException(message)

internal class EcPublicKey(override val encoded: ByteArray, internal val point: ByteArray) : PublicKey {
  override val algorithm: String get() = "EC"
  override val format: String get() = "X.509"
}

class KeyFactory private constructor(val algorithm: String) {
  fun generatePublic(spec: Any): PublicKey {
    val x509 = spec as? X509EncodedKeySpec ?: throw InvalidKeySpecException("X509EncodedKeySpec لازم است")
    return EcPublicKey(x509.encoded, p256Point(x509.encoded))
  }

  companion object {
    fun getInstance(algorithm: String): KeyFactory {
      if (!algorithm.equals("EC", ignoreCase = true)) throw NoSuchAlgorithmException("iOS: فقط EC ($algorithm)")
      return KeyFactory("EC")
    }

    /** SPKI ِ P-256 ⇐ نقطهٔ X9.63 (۰۴‖X‖Y، ۶۵ بایت) */
    private val P256_SPKI_PREFIX = byteArrayOf(
      0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
      0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
    )

    internal fun p256Point(spki: ByteArray): ByteArray {
      if (spki.size != P256_SPKI_PREFIX.size + 65 || !spki.copyOf(P256_SPKI_PREFIX.size).contentEquals(P256_SPKI_PREFIX) ||
        spki[P256_SPKI_PREFIX.size] != 0x04.toByte()
      ) throw InvalidKeySpecException("iOS: فقط کلیدِ عمومیِ P-256 ِ نافشرده")
      return spki.copyOfRange(P256_SPKI_PREFIX.size, spki.size)
    }
  }
}

class Signature private constructor(val algorithm: String) {
  private var key: EcPublicKey? = null
  private val data = java.io.ByteArrayOutputStream()

  fun initVerify(publicKey: PublicKey) {
    key = publicKey as? EcPublicKey ?: throw InvalidKeyException("کلیدِ EC لازم است")
    data.reset()
  }

  fun update(b: ByteArray) = data.write(b, 0, b.size)
  fun update(b: ByteArray, off: Int, len: Int) = data.write(b, off, len)

  fun verify(signature: ByteArray): Boolean {
    val k = key ?: throw SignatureException("object not initialized for verification")
    val msg = data.toByteArray()
    data.reset()
    return memScoped {
      val attrs = CFDictionaryCreateMutable(null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
      CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
      CFDictionaryAddValue(attrs, kSecAttrKeyClass, kSecAttrKeyClassPublic)
      val bits = CFBridgingRetain(NSNumber(int = 256))
      CFDictionaryAddValue(attrs, kSecAttrKeySizeInBits, bits)
      val pointData = CFBridgingRetain(k.point.toNSData()) as CFDataRef?
      val error = alloc<CFErrorRefVar>()
      val sec: SecKeyRef? = SecKeyCreateWithData(pointData, attrs, error.ptr)
      CFRelease(bits); CFRelease(pointData); CFRelease(attrs)
      if (sec == null) throw InvalidKeyException("SecKeyCreateWithData")
      val m = CFBridgingRetain(msg.toNSData()) as CFDataRef?
      val s = CFBridgingRetain(signature.toNSData()) as CFDataRef?
      val ok = SecKeyVerifySignature(sec, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, m, s, null)
      CFRelease(m); CFRelease(s); CFRelease(sec)
      ok
    }
  }

  companion object {
    fun getInstance(algorithm: String): Signature {
      if (!algorithm.equals("SHA256withECDSA", ignoreCase = true)) throw NoSuchAlgorithmException("iOS: فقط SHA256withECDSA ($algorithm)")
      return Signature("SHA256withECDSA")
    }
  }
}
