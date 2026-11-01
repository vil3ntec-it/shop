package ir.vil3ntec.tohid.core.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 *  نسخهٔ ۷ تا ۱۳ برای نشانیِ درازِ پرداخت (حساب‌پی) — بی تغییرِ سقفِ فاکتور.
 *  خوانده‌شدنِ واقعی با zxing: `core/test-vectors/qr-large-v1.json` و `server/test/qr-large.test.js`.
 */
class QrLargeTest {

  @Test
  fun receiptLimitIsUnchanged() {
    assertEquals(listOf(14, 26, 42, 62, 84, 106), (1..6).map { QrCode.capacity(it) })
    assertEquals(106, QrCode.MAX_BYTES)
    assertEquals(6, QrCode.versionFor("x".repeat(106)))
    assertFailsWith<IllegalArgumentException> { QrCode.encode("x".repeat(107)) }
  }

  @Test
  fun largeCapacitiesAtLevelM() {
    //  ISO/IEC 18004 جدولِ ۷، حالتِ بایت، سطحِ M
    assertEquals(listOf(122, 152, 180, 213, 251, 287, 331), (7..13).map { QrCode.capacity(it) })
    assertEquals(7, QrCode.versionFor("x".repeat(107), QrCode.LARGE_MAX_VERSION))
    assertEquals(10, QrCode.versionFor("x".repeat(200), QrCode.LARGE_MAX_VERSION))
    assertEquals(13, QrCode.versionFor("x".repeat(331), QrCode.LARGE_MAX_VERSION))
    assertFailsWith<IllegalArgumentException> { QrCode.encode("x".repeat(332), QrCode.LARGE_MAX_VERSION) }
  }

  @Test
  fun smallTextStillPicksTheSmallVersion() {
    val text = "https://a.io/r/d2jcj71t"
    val a = QrCode.encode(text)
    val b = QrCode.encode(text, QrCode.LARGE_MAX_VERSION)
    assertEquals(a.map { it.toList() }, b.map { it.toList() })
  }

  @Test
  fun versionInformationBlocksMatchTheStandard() {
    //  اطلاعاتِ نسخهٔ ۷ = 0x07C94 (پیوستِ D ِ استاندارد)؛ ۱۰ = 0x0A4D3؛ ۱۳ = 0x0D847
    for ((version, expected) in listOf(7 to 0x07C94, 10 to 0x0A4D3, 13 to 0x0D847)) {
      val m = QrCode.encode("y".repeat(QrCode.capacity(version)), QrCode.LARGE_MAX_VERSION)
      val size = m.size
      assertEquals(17 + 4 * version, size)
      for (i in 0 until 18) {
        val bit = ((expected ushr i) and 1) == 1
        val a = size - 11 + i % 3
        val b = i / 3
        assertEquals(bit, m[b][a], "v$version bit $i top-right")
        assertEquals(bit, m[a][b], "v$version bit $i bottom-left")
      }
    }
  }

  @Test
  fun alignmentPatternsAndFindersAreInPlace() {
    val m = QrCode.encode("z".repeat(150), QrCode.LARGE_MAX_VERSION)
    assertEquals(8, (m.size - 17) / 4)
    //  نسخهٔ ۸: مرکزها ۶، ۲۴، ۴۲ — شش الگو (سه گوشهٔ نشانگرها نه)
    for ((x, y) in listOf(24 to 24, 42 to 42, 24 to 42, 42 to 24, 6 to 24, 24 to 6)) {
      assertTrue(m[y][x], "center $x,$y")
      assertTrue(!m[y][x + 1] && !m[y + 1][x] && !m[y - 1][x - 1], "ring $x,$y")
      assertTrue(m[y + 2][x + 2] && m[y - 2][x - 2], "edge $x,$y")
    }
    //  نشانگرِ بالا-چپ و ماژولِ تیرهٔ ثابت
    assertTrue(m[0][0] && m[6][6] && !m[1][1] && m[3][3])
    assertTrue(m[m.size - 8][8])
  }
}
