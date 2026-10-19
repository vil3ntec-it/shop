package ir.vil3ntec.tohid.core.chat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** جهت و ابعادِ عکسِ چت پیش از فرستادن (CHAT-05/06). */
class ChatImageTest {

  private fun seg(marker: Int, payload: ByteArray): ByteArray {
    val len = payload.size + 2
    return byteArrayOf(0xFF.toByte(), marker.toByte(), (len shr 8).toByte(), len.toByte()) + payload
  }

  /** همان ساختارِ `jpegWithGps` ِ آزمونِ سرور: IFD0 با جهت + اشاره‌گرِ GPS. */
  private fun jpeg(orientation: Int, littleEndian: Boolean = false): ByteArray {
    val t = ByteArray(38)
    fun w16(i: Int, v: Int) { if (littleEndian) { t[i] = v.toByte(); t[i + 1] = (v shr 8).toByte() } else { t[i] = (v shr 8).toByte(); t[i + 1] = v.toByte() } }
    fun w32(i: Int, v: Int) { if (littleEndian) { w16(i, v and 0xFFFF); w16(i + 2, v ushr 16) } else { w16(i, v ushr 16); w16(i + 2, v and 0xFFFF) } }
    val bom = if (littleEndian) "II" else "MM"
    t[0] = bom[0].code.toByte(); t[1] = bom[1].code.toByte()
    w16(2, 42); w32(4, 8); w16(8, 2)
    w16(10, 0x8825); w16(12, 4); w32(14, 1); w32(18, 99)   // GPS پیش از جهت: پیمایش باید ادامه دهد
    w16(22, 0x0112); w16(24, 3); w32(26, 1); w16(30, orientation)
    w32(34, 0)
    val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + t
    return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
      seg(0xE0, "JFIF\u0000".toByteArray(Charsets.ISO_8859_1)) +
      seg(0xE1, exif) +
      seg(0xDA, byteArrayOf(1, 1, 0)) + ByteArray(10) + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
  }

  @Test fun readsOrientationBothByteOrders() {
    for (o in 1..8) {
      assertEquals(o, ChatImage.orientationOf(jpeg(o)))
      assertEquals(o, ChatImage.orientationOf(jpeg(o, littleEndian = true)))
    }
  }

  @Test fun missingOrBrokenExifMeansNormal() {
    assertEquals(1, ChatImage.orientationOf(byteArrayOf()))
    assertEquals(1, ChatImage.orientationOf("PNG?".toByteArray()))
    assertEquals(1, ChatImage.orientationOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + seg(0xDA, byteArrayOf(0))))
    assertEquals(1, ChatImage.orientationOf(jpeg(9)))
    assertEquals(1, ChatImage.orientationOf(jpeg(6).copyOf(30)))
  }

  /**
   *  تصویرِ ۳×۲:   1 2 3
   *               4 5 6
   */
  private val px = intArrayOf(1, 2, 3, 4, 5, 6)

  @Test fun orientsPixels() {
    fun o(n: Int) = ChatImage.orient(px, 3, 2, n)
    assertArrayEquals(px, o(1).first)
    assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), o(2).first)
    assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), o(3).first)
    assertArrayEquals(intArrayOf(4, 5, 6, 1, 2, 3), o(4).first)
    //  ۶ = ۹۰° ساعت‌گرد ⇒ ۲×۳:  4 1 / 5 2 / 6 3
    val r6 = o(6)
    assertEquals(2, r6.second); assertEquals(3, r6.third)
    assertArrayEquals(intArrayOf(4, 1, 5, 2, 6, 3), r6.first)
    //  ۸ = ۹۰° پادساعت‌گرد ⇒ 3 6 / 2 5 / 1 4
    assertArrayEquals(intArrayOf(3, 6, 2, 5, 1, 4), o(8).first)
    assertArrayEquals(intArrayOf(1, 4, 2, 5, 3, 6), o(5).first)
    assertArrayEquals(intArrayOf(6, 3, 5, 2, 4, 1), o(7).first)
  }

  @Test fun transparentPixelsLandOnWhiteNotBlack() {
    val out = ChatImage.flattenOnWhite(intArrayOf(0x00000000, 0xFF102030.toInt(), 0x80000000.toInt()))
    assertEquals(0xFFFFFFFF.toInt(), out[0])
    assertEquals(0xFF102030.toInt(), out[1])
    assertEquals(0xFF7F7F7F.toInt(), out[2])
  }

  @Test fun fitKeepsRatioAndNeverUpscales() {
    assertEquals(1600 to 1200, ChatImage.fit(4000, 3000, 1600))
    assertEquals(240 to 320, ChatImage.fit(3000, 4000, 320))
    assertEquals(200 to 100, ChatImage.fit(200, 100, 1600))
  }
}
