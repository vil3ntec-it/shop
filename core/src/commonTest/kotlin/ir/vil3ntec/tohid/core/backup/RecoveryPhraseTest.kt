package ir.vil3ntec.tohid.core.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  OPS-02 / §۱۰.۳ — عبارتِ بازیابیِ فروشگاه: شکل، وارسیِ غلطِ تایپی و KID ِ پاکت.
 *  بردارها با Python (hashlib + base64) ساخته شده‌اند؛ همان بردارِ `PhraseBackupTest`.
 */
class RecoveryPhraseTest {

  private val canonical = "K7QM2Z9P4XDH6VNB3RTC8WJF2"

  @Test
  fun generateIsFiveGroupsWithAValidCheckChar() {
    val p = RecoveryPhrase.generate { n -> ByteArray(n) { it.toByte() } }
    assertEquals("000G4-0R40M-30E20-9185G-R38E4", p)
    val real = RecoveryPhrase.generate()
    assertTrue(Regex("^([0-9A-HJKMNP-TV-Z]{5}-){4}[0-9A-HJKMNP-TV-Z]{5}$").matches(real), real)
    assertEquals(real.replace("-", ""), RecoveryPhrase.normalize(real))
    assertNotEquals(real, RecoveryPhrase.generate(), "تصادفی")
  }

  @Test
  fun normalizeAcceptsEveryWayOfTypingIt() {
    for (typed in listOf(
      "K7QM2-Z9P4X-DH6VN-B3RTC-8WJF2",
      "k7qm2 z9p4x dh6vn b3rtc 8wjf2",
      "K7QM2Z9P4XDH6VNB3RTC8WJF2",
      "K۷QM۲-Z۹P۴X-DH۶VN-B۳RTC-۸WJF۲",
      "K٧QM٢ Z٩P٤X DH٦VN B٣RTC ٨WJF٢",
      " K7QM2-Z9P4X\nDH6VN-B3RTC-8WJF2 ",
    )) assertEquals(canonical, RecoveryPhrase.normalize(typed), typed)
    //  O ⇐ 0 و I/L ⇐ 1 (Crockford)
    val withZero = "000G40R40M30E209185GR38E4"
    assertEquals(withZero, RecoveryPhrase.normalize("OOOG4-OR4OM-3OE2O-9185G-R38E4"))
  }

  @Test
  fun typoOrWrongLengthOrForeignCharIsRejectedBeforeAnySlowWork() {
    for (bad in listOf(
      "K7QM2-Z9P4X-DH6VN-B3RTC-8WJF3",   // وارسی نمی‌خواند
      "K7QM2-Z9P4X-DH6VN-B3RTC-8WJF",    // کوتاه
      "K7QM2-Z9P4X-DH6VN-B3RTC-8WJF22",  // بلند
      "K7QM2-Z9P4X-DH6VN-B3RTC-8WJU2",   // U در Crockford نیست
      "K7QM2-Z9P4X-DH6VN-B3RTC-8WJ#2",
      "",
      "عبارتِ بازیابی",
    )) assertNull(RecoveryPhrase.normalize(bad), bad)
    //  جابه‌جاییِ دو نویسه (رایج‌ترین غلط) هم گرفته می‌شود
    assertNull(RecoveryPhrase.normalize("7KQM2-Z9P4X-DH6VN-B3RTC-8WJF2"))
  }

  @Test
  fun kidCarriesSaltAndIterationsAndRejectsDangerousValues() {
    val salt = ByteArray(16) { it.toByte() }
    val kid = RecoveryPhrase.kidOf(salt, 100_000)
    assertEquals("rp1.100000.AAECAwQFBgcICQoLDA0ODw", kid)
    val p = RecoveryPhrase.parseKid(kid)!!
    assertEquals(100_000, p.iterations)
    assertContentEquals(salt, p.salt)
    assertEquals(RecoveryPhrase.ITERATIONS, RecoveryPhrase.parseKid(RecoveryPhrase.kidOf(salt))!!.iterations)
    for (bad in listOf(
      "rp1.99999.AAECAwQFBgcICQoLDA0ODw",      // کمتر از کمینه
      "rp1.5000001.AAECAwQFBgcICQoLDA0ODw",    // بیشتر از بیشینه: فایلِ دست‌کاری‌شده برنامه را قفل نکند
      "rp1.999999999.AAECAwQFBgcICQoLDA0ODw",
      "rp1.100000.AAECAwQFBgcICQoLDA0O",       // نمکِ کوتاه
      "rp1.100000.AAECAwQFBgcICQoLDA0OD+",     // نویسهٔ بیگانه
      "dk-2026-01",                              // کلیدِ دستگاه
      "rp2.100000.AAECAwQFBgcICQoLDA0ODw",
    )) assertNull(RecoveryPhrase.parseKid(bad), bad)
  }
}
