package javax.crypto

import ir.vil3ntec.tohid.ios.crypto.AesBlocks
import ir.vil3ntec.tohid.ios.crypto.Gcm
import ir.vil3ntec.tohid.ios.crypto.GcmTagMismatch
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess

/*
 *  shimِ iOS ِ `javax.crypto` (گامِ ۲-ب) — فقط آنچه دفترِ رمزدار (`core/atrest`) و پشتیبانِ عبارت
 *  می‌خواهند، با همان قراردادِ JDK:
 *    • `Cipher` «AES/GCM/NoPadding» ⇐ AES ِ CommonCrypto (ECB، سخت‌افزاری) + حالتِ GCM ِ ‎ios/crypto/Gcm.kt‎
 *      (همان بایت‌های JDK؛ آزمون: ‎src/jvmTest‎ و ‎src/iosTest‎). برچسبِ نخورده ⇐ AEADBadTagException.
 *    • `Mac` «HmacSHA256» ⇐ CCHmac؛ `SecretKeyFactory` «PBKDF2WithHmacSHA256» ⇐ CCKeyDerivationPBKDF
 *      (گذرواژه به UTF-8، مثلِ JDK).
 *  هر نامِ دیگر ⇐ NoSuchAlgorithmException.
 */
interface SecretKey : Key

open class BadPaddingException(message: String? = null) : GeneralSecurityException(message)
open class AEADBadTagException(message: String? = null) : BadPaddingException(message)
open class IllegalBlockSizeException(message: String? = null) : GeneralSecurityException(message)

/** AES ِ CommonCrypto در حالتِ ECB روی بلوک‌های پشتِ‌هم — همان «رمزِ بلوکی» ِ GCM */
internal class CommonCryptoAes(private val key: ByteArray) : AesBlocks {
  init {
    if (key.size != 16 && key.size != 24 && key.size != 32) throw InvalidKeyException("Invalid AES key length: ${key.size} bytes")
  }

  override fun encrypt(blocks: ByteArray): ByteArray {
    require(blocks.isNotEmpty() && blocks.size % 16 == 0)
    val out = ByteArray(blocks.size)
    memScoped {
      val moved = alloc<ULongVar>()
      val rc = key.usePinned { k ->
        blocks.usePinned { i ->
          out.usePinned { o ->
            CCCrypt(
              kCCEncrypt, kCCAlgorithmAES, kCCOptionECBMode,
              k.addressOf(0), key.size.toULong(), null,
              i.addressOf(0), blocks.size.toULong(),
              o.addressOf(0), out.size.toULong(), moved.ptr,
            )
          }
        }
      }
      if (rc != kCCSuccess) throw GeneralSecurityException("CCCrypt: $rc")
    }
    return out
  }
}

class Cipher private constructor(private val transformation: String) {
  private var mode = 0
  private var key: ByteArray? = null
  private var nonce: ByteArray? = null
  private var tagBytes = 16
  private val aad = java.io.ByteArrayOutputStream()
  private val input = java.io.ByteArrayOutputStream()
  private var used = false

  /** IV ِ همین رمز (پس از `init` ِ بی پارامتر، همان IV ِ تصادفی) */
  val iv: ByteArray? get() = nonce?.copyOf()

  fun init(opmode: Int, key: Key) {
    if (opmode != ENCRYPT_MODE) throw InvalidKeyException("Parameters missing")
    init(opmode, key, GCMParameterSpec(128, ByteArray(12).also { SecureRandom().nextBytes(it) }))
  }

  fun init(opmode: Int, key: Key, params: Any?) {
    val spec = params as? GCMParameterSpec ?: throw InvalidAlgorithmParameterException("GCMParameterSpec لازم است")
    if (opmode != ENCRYPT_MODE && opmode != DECRYPT_MODE) throw InvalidAlgorithmParameterException("mode $opmode")
    if (spec.tLen % 8 != 0 || spec.tLen / 8 !in 12..16) throw InvalidAlgorithmParameterException("Unsupported TLen value: ${spec.tLen}")
    val raw = key.encoded
    CommonCryptoAes(raw)
    this.mode = opmode
    this.key = raw.copyOf()
    this.nonce = spec.iv
    this.tagBytes = spec.tLen / 8
    aad.reset(); input.reset(); used = false
  }

  fun updateAAD(src: ByteArray) = updateAAD(src, 0, src.size)

  fun updateAAD(src: ByteArray, offset: Int, len: Int) {
    check(key != null) { "Cipher not initialized" }
    if (input.size() > 0) throw IllegalStateException("AAD must be supplied before encryption/decryption starts")
    aad.write(src, offset, len)
  }

  fun update(src: ByteArray): ByteArray = update(src, 0, src.size)

  /** GCM تا `doFinal` چیزی بیرون نمی‌دهد (مثلِ JDK در رمزگشایی؛ در رمزگذاری هم این‌جا یک‌جا) */
  fun update(src: ByteArray, offset: Int, len: Int): ByteArray {
    check(key != null) { "Cipher not initialized" }
    input.write(src, offset, len)
    return ByteArray(0)
  }

  fun getOutputSize(inputLen: Int): Int {
    val total = input.size() + inputLen
    return if (mode == ENCRYPT_MODE) total + tagBytes else maxOf(0, total - tagBytes)
  }

  fun doFinal(): ByteArray = doFinal(ByteArray(0))

  fun doFinal(src: ByteArray): ByteArray = doFinal(src, 0, src.size)

  fun doFinal(src: ByteArray, offset: Int, len: Int): ByteArray {
    val k = key ?: throw IllegalStateException("Cipher not initialized")
    input.write(src, offset, len)
    val data = input.toByteArray()
    val a = aad.toByteArray()
    input.reset(); aad.reset()
    val aes = CommonCryptoAes(k)
    return if (mode == ENCRYPT_MODE) {
      if (used) throw IllegalStateException("Cannot reuse iv for GCM encryption")
      used = true
      Gcm.seal(aes, nonce!!, a, data, tagBytes)
    } else {
      try {
        Gcm.open(aes, nonce!!, a, data, tagBytes)
      } catch (e: GcmTagMismatch) {
        throw AEADBadTagException("Tag mismatch")
      }
    }
  }

  /** همان JDK: خروجی در `output` از `outputOffset`، شمارِ بایت‌ها برمی‌گردد */
  fun doFinal(src: ByteArray, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int): Int {
    val r = doFinal(src, inputOffset, inputLen)
    if (output.size - outputOffset < r.size) throw ShortBufferException("Output buffer too short")
    r.copyInto(output, outputOffset)
    return r.size
  }

  companion object {
    const val ENCRYPT_MODE = 1
    const val DECRYPT_MODE = 2

    fun getInstance(transformation: String): Cipher {
      if (!transformation.equals("AES/GCM/NoPadding", ignoreCase = true)) {
        throw NoSuchAlgorithmException("iOS: فقط AES/GCM/NoPadding ($transformation)")
      }
      return Cipher(transformation)
    }
  }
}

open class ShortBufferException(message: String? = null) : GeneralSecurityException(message)

class Mac private constructor(val algorithm: String) {
  private var key: ByteArray? = null
  private val data = java.io.ByteArrayOutputStream()

  fun init(key: Key) { this.key = key.encoded.copyOf(); data.reset() }
  fun update(input: ByteArray) = data.write(input, 0, input.size)
  fun update(input: ByteArray, offset: Int, len: Int) = data.write(input, offset, len)
  fun doFinal(input: ByteArray): ByteArray { update(input); return doFinal() }
  fun reset() = data.reset()
  val macLength: Int get() = 32

  fun doFinal(): ByteArray {
    val k = key ?: throw IllegalStateException("MAC not initialized")
    val msg = data.toByteArray()
    data.reset()
    val out = ByteArray(32)
    val kk = if (k.isEmpty()) ByteArray(1) else k
    val mm = if (msg.isEmpty()) ByteArray(1) else msg
    kk.usePinned { kp ->
      mm.usePinned { mp ->
        out.usePinned { op ->
          CCHmac(kCCHmacAlgSHA256, kp.addressOf(0), k.size.toULong(), mp.addressOf(0), msg.size.toULong(), op.addressOf(0))
        }
      }
    }
    return out
  }

  companion object {
    fun getInstance(algorithm: String): Mac {
      if (!algorithm.equals("HmacSHA256", ignoreCase = true)) throw NoSuchAlgorithmException("iOS: فقط HmacSHA256 ($algorithm)")
      return Mac("HmacSHA256")
    }
  }
}

class SecretKeyFactory private constructor(val algorithm: String) {
  fun generateSecret(spec: Any): SecretKey {
    val pbe = spec as? PBEKeySpec ?: throw java.security.spec.InvalidKeySpecException("PBEKeySpec لازم است")
    //  گذرواژه به UTF-8 (همان JDK)؛ CommonCrypto آن را `const char*` با طولِ جدا می‌گیرد
    val text = pbe.password.concatToString()
    val password = text.encodeToByteArray()
    val len = pbe.keyLength / 8
    val out = ByteArray(len)
    val salt = pbe.salt.let { if (it.isEmpty()) ByteArray(1) else it }
    val rc = run {
      salt.usePinned { s ->
        out.usePinned { o ->
          CCKeyDerivationPBKDF(
            kCCPBKDF2, text, password.size.toULong(),
            s.addressOf(0).reinterpret<UByteVar>(), pbe.salt.size.toULong(),
            kCCPRFHmacAlgSHA256, pbe.iterationCount.toUInt(),
            o.addressOf(0).reinterpret<UByteVar>(), len.toULong(),
          )
        }
      }
    }
    if (rc != kCCSuccess) throw GeneralSecurityException("PBKDF2: $rc")
    return SecretKeySpec(out, "PBKDF2WithHmacSHA256")
  }

  companion object {
    fun getInstance(algorithm: String): SecretKeyFactory {
      if (!algorithm.equals("PBKDF2WithHmacSHA256", ignoreCase = true)) throw NoSuchAlgorithmException("iOS: فقط PBKDF2WithHmacSHA256 ($algorithm)")
      return SecretKeyFactory("PBKDF2WithHmacSHA256")
    }
  }
}
