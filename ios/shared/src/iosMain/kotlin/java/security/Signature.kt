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
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClassPrivate
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import java.net.toByteArray
import java.security.spec.PKCS8EncodedKeySpec
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256

/*
 *  shimِ iOS ِ ECDSA P-256 ِ JDK (`KeyPairGenerator("EC")`، `KeyFactory("EC")`، `Signature("SHA256withECDSA")`)
 *  روی Security.framework — وارسیِ امضای مجوز (`sync/License`) و کلیدِ تأییدکننده (`core/perm/PinCrypto`).
 *  همان قالب‌ها: کلیدِ عمومی SubjectPublicKeyInfo ِ DER، کلیدِ خصوصی PKCS#8 (SEC1، با کلیدِ عمومیِ همراه — مثلِ
 *  اندروید)، امضا DER ِ X9.62.
 *  ⛔ کلیدِ PKCS#8 ِ بی کلیدِ عمومی (خروجیِ JDK ِ کامپیوتر) در iOS امضا نمی‌کند (Security.framework نقطهٔ
 *  عمومی را می‌خواهد) ⇐ InvalidKeySpecException ِ روشن؛ گامِ ۳.
 *  منحنیِ دیگر یا کلیدِ بدشکل ⇐ InvalidKeySpecException؛ امضای بدشکل ⇐ false (مثلِ JDK ⇐ SignatureException).
 */
interface PublicKey : Key
interface PrivateKey : Key

class KeyPair(val public: PublicKey, val private: PrivateKey)

internal class EcPrivateKey(internal val x963: ByteArray) : PrivateKey {
  override val algorithm: String get() = "EC"
  override val format: String get() = "PKCS#8"
  override val encoded: ByteArray get() = Ec.pkcs8(x963)
}

open class SignatureException(message: String? = null) : GeneralSecurityException(message)

internal class EcPublicKey(override val encoded: ByteArray, internal val point: ByteArray) : PublicKey {
  override val algorithm: String get() = "EC"
  override val format: String get() = "X.509"
}

class KeyFactory private constructor(val algorithm: String) {
  fun generatePrivate(spec: Any): PrivateKey {
    val p = spec as? PKCS8EncodedKeySpec ?: throw InvalidKeySpecException("PKCS8EncodedKeySpec لازم است")
    return EcPrivateKey(Ec.fromPkcs8(p.encoded))
  }

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
  private var signer: EcPrivateKey? = null

  fun initSign(privateKey: PrivateKey) {
    signer = privateKey as? EcPrivateKey ?: throw InvalidKeyException("کلیدِ EC لازم است")
    key = null
    data.reset()
  }

  fun initSign(privateKey: PrivateKey, random: SecureRandom) = initSign(privateKey)

  fun sign(): ByteArray {
    val k = signer ?: throw SignatureException("object not initialized for signing")
    val msg = data.toByteArray()
    data.reset()
    return memScoped {
      val sec = Ec.secKey(k.x963, private = true)
      val m = CFBridgingRetain(msg.toNSData()) as CFDataRef?
      val error = alloc<CFErrorRefVar>()
      val out = SecKeyCreateSignature(sec, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, m, error.ptr)
      CFRelease(m); CFRelease(sec)
      (CFBridgingRelease(out) as? NSData)?.toByteArray() ?: throw SignatureException("SecKeyCreateSignature")
    }
  }
  private val data = java.io.ByteArrayOutputStream()

  fun initVerify(publicKey: PublicKey) {
    key = publicKey as? EcPublicKey ?: throw InvalidKeyException("کلیدِ EC لازم است")
    signer = null
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

class KeyPairGenerator private constructor(val algorithm: String) {
  fun initialize(params: Any?) {
    val name = (params as? java.security.spec.ECGenParameterSpec)?.name
    if (name != null && name !in setOf("secp256r1", "prime256v1", "P-256")) throw InvalidAlgorithmParameterException("iOS: فقط P-256 ($name)")
  }

  fun initialize(params: Any?, random: SecureRandom) = initialize(params)

  /** کلیدِ تازه با مولدِ تصادفیِ امنِ iOS (SecKeyCreateRandomKey) */
  fun generateKeyPair(): KeyPair = memScoped {
    val attrs = CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
    val bits = CFBridgingRetain(NSNumber(int = 256))
    CFDictionaryAddValue(attrs, kSecAttrKeySizeInBits, bits)
    val error = alloc<CFErrorRefVar>()
    val priv = SecKeyCreateRandomKey(attrs, error.ptr)
    CFRelease(bits); CFRelease(attrs)
    if (priv == null) throw GeneralSecurityException("SecKeyCreateRandomKey")
    val ext = SecKeyCopyExternalRepresentation(priv, error.ptr)
    CFRelease(priv)
    val x963 = (CFBridgingRelease(ext) as? NSData)?.toByteArray() ?: throw GeneralSecurityException("SecKeyCopyExternalRepresentation")
    if (x963.size != 97) throw GeneralSecurityException("P-256: ${x963.size}")
    val point = x963.copyOf(65)
    KeyPair(EcPublicKey(Ec.spki(point), point), EcPrivateKey(x963))
  }

  companion object {
    fun getInstance(algorithm: String): KeyPairGenerator {
      if (!algorithm.equals("EC", ignoreCase = true)) throw NoSuchAlgorithmException("iOS: فقط EC ($algorithm)")
      return KeyPairGenerator("EC")
    }
  }
}

/** قالب‌های DER ِ P-256 */
internal object Ec {
  private val SPKI_PREFIX = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
  )
  private val ALG = byteArrayOf(
    0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07,
  )

  fun spki(point: ByteArray): ByteArray = SPKI_PREFIX + point

  /** SEC1 درونِ PKCS#8، با کلیدِ عمومی ([1])، مثلِ خروجیِ اندروید */
  fun pkcs8(x963: ByteArray): ByteArray {
    val d = x963.copyOfRange(65, 97)
    val pub = x963.copyOf(65)
    val sec1 = byteArrayOf(0x30, 0x6b, 0x02, 0x01, 0x01, 0x04, 0x20) + d +
      byteArrayOf(0xa1.toByte(), 0x44, 0x03, 0x42, 0x00) + pub
    return byteArrayOf(0x30, 0x81.toByte(), 0x87.toByte(), 0x02, 0x01, 0x00) + ALG + byteArrayOf(0x04, 0x6d) + sec1
  }

  /** PKCS#8 ⇐ 04‖X‖Y‖D؛ کلیدِ عمومیِ همراه لازم است */
  fun fromPkcs8(der: ByteArray): ByteArray {
    val dAt = indexOf(der, byteArrayOf(0x02, 0x01, 0x01, 0x04, 0x20))
    val pubAt = indexOf(der, byteArrayOf(0x03, 0x42, 0x00, 0x04))
    if (dAt < 0 || dAt + 5 + 32 > der.size) throw InvalidKeySpecException("PKCS#8 ِ P-256 نیست")
    if (pubAt < 0 || pubAt + 3 + 65 > der.size) {
      throw InvalidKeySpecException("کلیدِ خصوصیِ بی کلیدِ عمومی (ساختِ JDK ِ کامپیوتر) در iOS امضا نمی‌کند")
    }
    return der.copyOfRange(pubAt + 3, pubAt + 3 + 65) + der.copyOfRange(dAt + 5, dAt + 5 + 32)
  }

  private fun indexOf(h: ByteArray, n: ByteArray): Int {
    outer@ for (i in 0..h.size - n.size) {
      for (j in n.indices) if (h[i + j] != n[j]) continue@outer
      return i
    }
    return -1
  }

  fun secKey(x963: ByteArray, private: Boolean): SecKeyRef = memScoped {
    val attrs = CFDictionaryCreateMutable(null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
    CFDictionaryAddValue(attrs, kSecAttrKeyClass, if (private) kSecAttrKeyClassPrivate else kSecAttrKeyClassPublic)
    val bits = CFBridgingRetain(NSNumber(int = 256))
    CFDictionaryAddValue(attrs, kSecAttrKeySizeInBits, bits)
    val d = CFBridgingRetain(x963.toNSData()) as CFDataRef?
    val error = alloc<CFErrorRefVar>()
    val k = SecKeyCreateWithData(d, attrs, error.ptr)
    CFRelease(bits); CFRelease(d); CFRelease(attrs)
    k ?: throw InvalidKeyException("SecKeyCreateWithData")
  }
}
