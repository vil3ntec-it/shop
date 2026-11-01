package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  SHA-256 ِ خالص با بردارهای NIST (FIPS 180-4، پیوستِ «SHA256.pdf» و «SHA2_Additional») و مرزهای
 *  لایه‌گذاری (۵۵/۵۶ و ۶۳/۶۴/۶۵ بایت: طول یک بلوک یا دو بلوک می‌شود). مقدارها با `crypto` ِ Node
 *  هم یکی‌اند. هم‌سنجیِ گسترده با `MessageDigest` در jvmTest است (`Sha256ParityTest`).
 */
class Sha256Test {

  @Test
  fun nistShortMessages() {
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hexOf(""))
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hexOf("abc"))
    assertEquals(
      "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
      Sha256.hexOf("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
    )
    assertEquals(
      "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
      Sha256.hexOf("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"),
    )
  }

  @Test
  fun nistOneMillionA() {
    assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hexOf("a".repeat(1_000_000)))
  }

  @Test
  fun paddingBoundaries() {
    val want = mapOf(
      55 to "d5e285683cd4efc02d021a5c62014694958901005d6f71e89e0989fac77e4072",
      56 to "04c26261370ee7541549d16dee320c723e3fd14671e66a099afe0a377c16888e",
      63 to "75220b47218278e656f2013bb8f0c455a25eaf01e86c64924e9d48d89776d6f2",
      64 to "7ce100971f64e7001e8fe5a51973ecdfe1ced42befe7ee8d5fd6219506b5393c",
      65 to "9537c5fdf120482f7d58d25e9ed583f52c02b4e304ea814db1633ad565aed7e9",
      119 to "000b48d4edf0fa7bee3c6236ecd2785baa5db4eeb8bb54341b029e0d9fa5fb0c",
      120 to "13f05a0b594787f5ecd315edc96141bd3243203d1b7d4f0836f37308b276ba98",
    )
    for ((n, hex) in want) assertEquals(hex, Sha256.hexOf("x".repeat(n)), "طولِ $n")
  }

  @Test
  fun utf8MultiByteText() {
    //  فارسی (دوبایتی) و شکلک (جفتِ جانشین ⇐ چهاربایتی) — همان بایت‌های Buffer.from(s, 'utf8')
    assertEquals("f8bafa73afa021e351473c163adf2431a03a8d9398be06b2a8ebe971c1858a28", Sha256.hexOf("دکان توحید 😀"))
  }

  @Test
  fun digestIsThirtyTwoBytesAndHexIsLowercase() {
    assertEquals(32, Sha256.digest(ByteArray(0)).size)
    assertEquals("00ff7f80", Sha256.hex(byteArrayOf(0, -1, 127, -128)))
  }
}
