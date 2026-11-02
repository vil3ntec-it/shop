package ir.vil3ntec.tohid.ios.crypto

import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *  shimهای رمزنگاریِ iOS روی شبیه‌ساز (CommonCrypto) در برابرِ بردارهای JDK — همان بایت‌های اندروید و کامپیوتر.
 *  (کارِ `ios-app` در ios-spike.yml؛ روی لینوکس فقط کامپایل می‌شود.)
 */
class CryptoIosTest {
  private fun seal(key: ByteArray, iv: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
      updateAAD(aad)
    }.doFinal(plain)

  @Test
  fun gcmMatchesJdkVectors() {
    for (v in GcmVectors.all) {
      val sealed = seal(unhex(v.key), unhex(v.iv), unhex(v.aad), unhex(v.plain))
      assertEquals(v.sealed, hex(sealed), v.name)
      val open = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(unhex(v.key), "AES"), GCMParameterSpec(128, unhex(v.iv)))
        updateAAD(unhex(v.aad))
      }.doFinal(sealed)
      assertContentEquals(unhex(v.plain), open, v.name)
    }
  }

  @Test
  fun tamperedIsRejected() {
    val v = GcmVectors.all.first()
    val bad = unhex(v.sealed).also { it[0] = (it[0].toInt() xor 1).toByte() }
    assertFailsWith<AEADBadTagException> {
      Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(unhex(v.key), "AES"), GCMParameterSpec(128, unhex(v.iv)))
        updateAAD(unhex(v.aad))
      }.doFinal(bad)
    }
  }

  @Test
  fun digestsMatchJdk() {
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(MessageDigest.getInstance("SHA-256").digest("abc".encodeToByteArray())))
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("Jefe".encodeToByteArray(), "HmacSHA256")) }
    assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", hex(mac.doFinal("what do ya want for nothing?".encodeToByteArray())))
    val pbkdf2 = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      .generateSecret(PBEKeySpec("رمز-۱۲۳".toCharArray(), "salt".encodeToByteArray(), 1000, 256)).encoded
    assertEquals("076a34eb24d4b5382eba9de7004123ff9e860e6b64a78a93a71651a3d102f723", hex(pbkdf2))
  }
}
