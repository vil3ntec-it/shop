package ir.vil3ntec.tohid.ios.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *  حالتِ GCM ِ آیفون (`commonMain/…/Gcm.kt`) در برابرِ خودِ JDK: همان کلید/IV/AAD/متن ⇐ همان بایت‌ها.
 *  پس پاکتی که اندروید یا کامپیوتر نوشته در آیفون باز می‌شود و برعکس. AES ِ بلوکی این‌جا همان
 *  `AES/ECB/NoPadding` ِ JDK است؛ در آیفون CommonCrypto (`src/iosTest` همین بردارها را روی شبیه‌ساز می‌سنجد).
 */
class GcmJvmTest {
  private fun aes(key: ByteArray) = AesBlocks { blocks ->
    Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }.doFinal(blocks)
  }

  private fun jdkSeal(key: ByteArray, iv: ByteArray, aad: ByteArray, plain: ByteArray, tagBits: Int = 128): ByteArray =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagBits, iv))
      updateAAD(aad)
    }.doFinal(plain)

  @Test
  fun sameBytesAsJdkOnRandomInputs() {
    val r = Random(20261102)
    val sizes = listOf(0, 1, 15, 16, 17, 31, 32, 33, 100, 1000, 4096 * 16 + 5, 70_000)
    for (keyLen in listOf(16, 24, 32)) for (ivLen in listOf(12, 8, 16, 60)) for (n in sizes) {
      val key = r.nextBytes(keyLen)
      val iv = r.nextBytes(ivLen)
      val aad = r.nextBytes(r.nextInt(0, 40))
      val plain = r.nextBytes(n)
      val expected = jdkSeal(key, iv, aad, plain)
      val got = Gcm.seal(aes(key), iv, aad, plain)
      assertContentEquals(expected, got, "key=$keyLen iv=$ivLen n=$n")
      assertContentEquals(plain, Gcm.open(aes(key), iv, aad, got))
    }
  }

  @Test
  fun shortTagsMatchJdk() {
    val key = ByteArray(16) { it.toByte() }
    val iv = ByteArray(12) { (it + 1).toByte() }
    for (bits in listOf(96, 104, 112, 120, 128)) {
      val plain = "tag $bits".encodeToByteArray()
      assertContentEquals(jdkSeal(key, iv, ByteArray(0), plain, bits), Gcm.seal(aes(key), iv, ByteArray(0), plain, bits / 8))
    }
  }

  @Test
  fun sharedVectors() {
    for (v in GcmVectors.all) {
      assertEquals(v.sealed, hex(jdkSeal(unhex(v.key), unhex(v.iv), unhex(v.aad), unhex(v.plain))), v.name)
      assertEquals(v.sealed, hex(Gcm.seal(aes(unhex(v.key)), unhex(v.iv), unhex(v.aad), unhex(v.plain))), v.name)
    }
  }

  @Test
  fun tamperedTagOrAadIsRejected() {
    val key = ByteArray(32) { 7 }
    val iv = ByteArray(12) { 9 }
    val sealed = Gcm.seal(aes(key), iv, "a".encodeToByteArray(), "دفتر".encodeToByteArray())
    val flipped = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
    assertFailsWith<GcmTagMismatch> { Gcm.open(aes(key), iv, "a".encodeToByteArray(), flipped) }
    assertFailsWith<GcmTagMismatch> { Gcm.open(aes(key), iv, "b".encodeToByteArray(), sealed) }
    assertFailsWith<GcmTagMismatch> { Gcm.open(aes(key), iv, ByteArray(0), ByteArray(5)) }
  }
}
