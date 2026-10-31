package ir.vil3ntec.tohid.core.atrest

import ir.vil3ntec.tohid.core.atrest.platform.PhraseBackup
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 *  OPS-02 / §۱۰.۳ — پشتیبانِ رمزدار با عبارتِ بازیابی (نه کلیدِ دستگاه).
 *
 *  بردارِ ثابت با Python (`hashlib.pbkdf2_hmac` + `cryptography` AESGCM) ساخته شده است؛ پس قالب
 *  و کلید از یک پیاده‌سازیِ دیگر هم همین است (آیفون/کامپیوتر بعداً همین بردار را می‌سنجند).
 */
class PhraseBackupTest {

  private val canonical = "K7QM2Z9P4XDH6VNB3RTC8WJF2"
  private val salt = ByteArray(16) { it.toByte() }
  private val plain = """{"v":1,"shop":"test"}""".toByteArray()
  private val vector =
    "8954454e430d0a1a01217270312e3130303030302e41414543417751464267634943516f4c4441304f4477" +
      "a0a1a2a3a4a5a6a7a8a9aaab77d57bca0d9a893cb71b3796bfdb701b9af6d48db1247a1e30709f9e16347826ae533989f5"

  /** SecureRandom با بایت‌های ثابت — فقط برای بردار */
  private class Fixed(private val bytes: ByteArray) : SecureRandom() {
    override fun nextBytes(out: ByteArray) { bytes.copyInto(out, 0, 0, out.size) }
  }

  private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

  @Test
  fun `کلید و پاکت همان بردارِ Python اند`() {
    val key = PhraseBackup.deriveKey(canonical, salt, 100_000)
    assertEquals("8da3a2ed25e2f04e6c85cf72419627f0d6105d04469e9926b85835d0a70b2308", hex(key.encoded))
    val kid = RecoveryPhrase.kidOf(salt, 100_000)
    val sealed = PhraseBackup.seal(plain, kid, key, Fixed(ByteArray(12) { (0xA0 + it).toByte() }))
    assertEquals(vector, hex(sealed))
  }

  @Test
  fun `دستگاهِ تازه فقط با عبارت باز می‌کند به هر شکلی نوشته شود`() {
    val sealed = vector.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    assertTrue(AtRestEnvelope.isSealed(sealed))
    for (typed in listOf("K7QM2-Z9P4X-DH6VN-B3RTC-8WJF2", "k7qm2 z9p4x dh6vn b3rtc 8wjf2", "K۷QM۲-Z۹P۴X-DH۶VN-B۳RTC-۸WJF۲")) {
      assertArrayEquals(typed, plain, PhraseBackup.open(sealed, typed))
    }
  }

  @Test
  fun `رفت و برگشت با نمکِ تازه`() {
    val phrase = RecoveryPhrase.generate()
    val (kid, key) = PhraseBackup.newKey(RecoveryPhrase.normalize(phrase)!!, SecureRandom(), 100_000)
    val big = ByteArray(300_000) { (it % 251).toByte() }
    val sealed = PhraseBackup.seal(big, kid, key, SecureRandom())
    assertArrayEquals(big, PhraseBackup.open(sealed, phrase))
  }

  private fun kindOf(block: () -> Unit): AtRestException.Kind? =
    try { block(); null } catch (e: AtRestException) { e.kind }

  @Test
  fun `عبارتِ دیگر و غلطِ تایپی و فایلِ دست‌خورده یا کلیدِ دستگاه هرگز باز نمی‌شود`() {
    val sealed = vector.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val other = RecoveryPhrase.normalize(RecoveryPhrase.generate())!!
    assertEquals(AtRestException.Kind.AUTH_FAILED, kindOf { PhraseBackup.open(sealed, other) })
    assertEquals(AtRestException.Kind.KEY_MISSING, kindOf { PhraseBackup.open(sealed, "K7QM2-Z9P4X-DH6VN-B3RTC-8WJF3") })
    //  یک بیت در رمزنوشته، و یک بیت در KID (تکرارِ دیگر) — هر دو زیرِ برچسب
    val flipped = sealed.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
    assertEquals(AtRestException.Kind.AUTH_FAILED, kindOf { PhraseBackup.open(flipped, canonical) })
    val kidAt = 10 + "rp1.10000".length
    val otherIter = sealed.copyOf().also { it[kidAt] = '1'.code.toByte() }   // 100000 ⇒ 100001
    assertEquals(AtRestException.Kind.AUTH_FAILED, kindOf { PhraseBackup.open(otherIter, canonical) })
    //  پاکتِ کلیدِ دستگاه (SEC-01) با عبارت باز نمی‌شود
    val device = AtRestEnvelope.seal(plain, "dk-2026-01", SecretKeySpec(ByteArray(32), "AES"), SecureRandom())
    assertEquals(AtRestException.Kind.KEY_MISSING, kindOf { PhraseBackup.open(device, canonical) })
    //  JSON ِ ساده پاکت نیست
    assertEquals(AtRestException.Kind.MALFORMED, kindOf { PhraseBackup.open(plain, canonical) })
  }

  @Test
  fun `تکرارِ بیرون از بازه یا KID ِ دیگر هرگز ساخته یا رمز نمی‌شود`() {
    for (n in listOf(99_999, 5_000_001)) {
      try { PhraseBackup.deriveKey(canonical, salt, n); fail("$n") } catch (_: IllegalArgumentException) { }
    }
    try {
      PhraseBackup.seal(plain, "dk-2026-01", SecretKeySpec(ByteArray(32), "AES"), SecureRandom()); fail()
    } catch (_: IllegalArgumentException) { }
  }
}
