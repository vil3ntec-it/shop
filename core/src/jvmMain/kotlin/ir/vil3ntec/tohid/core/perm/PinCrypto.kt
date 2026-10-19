package ir.vil3ntec.tohid.core.perm

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 *  رمزنگاریِ PIN و کلیدِ تأیید — STF-02 و STF-04 (بخشِ ۱۰.۱ و ۱۰.۲).
 *
 *  • PIN با PBKDF2-HMAC-SHA256 (نمکِ تصادفیِ ۱۶ بایتی، ۲۱۰ هزار دور) سنجیده
 *    می‌شود؛ خودِ PIN هیچ‌جا نمی‌ماند و هشش هرگز همگام نمی‌شود.
 *  • کلیدِ تأیید ECDSA P-256 است. کلیدِ خصوصی (PKCS#8) با AES-256-GCM و کلیدی
 *    که از همان PIN (با نمکِ جدا) ساخته می‌شود بسته می‌ماند — «PIN فقط کلید را
 *    باز می‌کند». PINِ غلط ⇐ برچسبِ GCM نمی‌خواند ⇐ null.
 *  • امضا DER است (همان که سرور با `dsaEncoding: 'der'` می‌خواند).
 *
 *  فقط JDK — اندروید و کامپیوتر هر دو همین را دارند.
 */
object PinCrypto {

  const val ITERATIONS = 210_000
  private const val SALT_BYTES = 16
  private const val IV_BYTES = 12
  private val rnd = SecureRandom()
  private val b64 = Base64.getEncoder()
  private val unb64 = Base64.getDecoder()

  fun salt(): ByteArray = ByteArray(SALT_BYTES).also { rnd.nextBytes(it) }

  fun b64(bytes: ByteArray): String = b64.encodeToString(bytes)

  fun unb64(s: String): ByteArray = unb64.decode(s)

  /**
   *  PBKDF2-HMAC-SHA256 با خروجیِ ۳۲ بایت (یک بلوک، RFC 8018). دستی نوشته شده
   *  چون `SecretKeyFactory("PBKDF2WithHmacSHA256")` در اندرویدِ ۷ (API 24/25)
   *  نیست؛ `Mac("HmacSHA256")` همه‌جا هست. آزمون با بردارِ RFC 7914 می‌سنجدش.
   */
  internal fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
    val password = PinGuard.normalize(pin).toByteArray(Charsets.UTF_8)
    require(password.isNotEmpty() && iterations >= 1) { "PIN خالی است" }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(password, "HmacSHA256"))
    mac.update(salt)
    mac.update(byteArrayOf(0, 0, 0, 1))
    var u = mac.doFinal()
    val out = u.copyOf()
    repeat(iterations - 1) {
      u = mac.doFinal(u)
      for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
    }
    return out
  }

  /** اثرِ PIN (hex) */
  fun pinHash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): String =
    pbkdf2(pin, salt, iterations).joinToString("") { "%02x".format(it) }

  /** مقایسهٔ ثابت‌زمان */
  fun matches(pin: String, salt: ByteArray, iterations: Int, hashHex: String): Boolean =
    MessageDigest.isEqual(pinHash(pin, salt, iterations).toByteArray(), hashHex.toByteArray())

  /** جفت‌کلیدِ تازه: (PKCS#8ِ خصوصی، SPKIِ عمومی) */
  fun newKeyPair(): Pair<ByteArray, ByteArray> {
    val g = KeyPairGenerator.getInstance("EC")
    g.initialize(ECGenParameterSpec("secp256r1"), rnd)
    val kp = g.generateKeyPair()
    return kp.private.encoded to kp.public.encoded
  }

  /** شناسهٔ کلید — همان `keyIdOf`ِ سرور */
  fun keyId(spki: ByteArray): String =
    "ak_" + MessageDigest.getInstance("SHA-256").digest(spki).joinToString("") { "%02x".format(it) }.take(24)

  /** بستنِ کلیدِ خصوصی با PIN ⇐ base64(iv ‖ ciphertext+tag) */
  fun seal(secret: ByteArray, pin: String, salt: ByteArray, iterations: Int = ITERATIONS): String {
    val iv = ByteArray(IV_BYTES).also { rnd.nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(pbkdf2(pin, salt, iterations), "AES"), GCMParameterSpec(128, iv))
    return b64(iv + c.doFinal(secret))
  }

  /** باز کردن؛ PINِ غلط یا دادهٔ دست‌خورده ⇐ null */
  fun open(sealed: String, pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray? {
    val all = runCatching { unb64(sealed) }.getOrNull() ?: return null
    if (all.size <= IV_BYTES + 16) return null
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(
      Cipher.DECRYPT_MODE, SecretKeySpec(pbkdf2(pin, salt, iterations), "AES"),
      GCMParameterSpec(128, all, 0, IV_BYTES),
    )
    return try {
      c.doFinal(all, IV_BYTES, all.size - IV_BYTES)
    } catch (_: AEADBadTagException) {
      null
    }
  }

  /** امضای DER (base64) با کلیدِ خصوصیِ PKCS#8 */
  fun sign(pkcs8: ByteArray, text: String): String {
    val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    val s = Signature.getInstance("SHA256withECDSA")
    s.initSign(key)
    s.update(text.toByteArray(Charsets.UTF_8))
    return b64(s.sign())
  }

  /** nonceِ یک‌بارمصرف — ۱۸ بایتِ تصادفی، base64url بی پَد (۲۴ نویسه) */
  fun nonce(): String {
    val b = ByteArray(18).also { rnd.nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
  }
}
