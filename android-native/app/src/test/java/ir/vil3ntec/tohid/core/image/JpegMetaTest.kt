package ir.vil3ntec.tohid.core.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** فرادادهٔ JPEG: orientation و برداشتنِ EXIF/GPS (v3 ۳۴.۳ بندِ ۳، ۳۶.۱۱). */
class JpegMetaTest {

  private fun seg(m: Int, body: ByteArray): ByteArray {
    val len = body.size + 2
    return byteArrayOf(0xFF.toByte(), m.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte()) + body
  }

  /** TIFF با IFD0: orientation و اشاره‌گرِ GPS؛ IFDِ GPS با عرضِ جغرافیایی */
  private fun exif(orientation: Int, littleEndian: Boolean): ByteArray {
    val t = ByteArray(92)
    fun w16(i: Int, v: Int) {
      if (littleEndian) { t[i] = v.toByte(); t[i + 1] = (v shr 8).toByte() } else { t[i] = (v shr 8).toByte(); t[i + 1] = v.toByte() }
    }
    fun w32(i: Int, v: Int) { if (littleEndian) { w16(i, v and 0xFFFF); w16(i + 2, v ushr 16) } else { w16(i, v ushr 16); w16(i + 2, v and 0xFFFF) } }
    t[0] = (if (littleEndian) 'I' else 'M').code.toByte(); t[1] = t[0]
    w16(2, 42); w32(4, 8)
    w16(8, 2)
    w16(10, 0x0112); w16(12, 3); w32(14, 1); w16(18, orientation)
    w16(22, 0x8825); w16(24, 4); w32(26, 1); w32(30, 38)
    w32(34, 0)
    w16(38, 1)
    w16(40, 0x0002); w16(42, 5); w32(44, 3); w32(48, 56)
    w32(52, 0)
    w32(56, 34); w32(60, 1); w32(64, 31); w32(68, 1); w32(72, 7); w32(76, 1)
    return "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + t
  }

  private val scan = byteArrayOf(1, 2, 3, 4, 0xFF.toByte(), 0x00, 5, 6)

  private fun jpeg(vararg extra: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
    out.write(seg(0xE0, "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray(Charsets.ISO_8859_1)))
    extra.forEach { out.write(it) }
    out.write(seg(0xE2, "ICC_PROFILE\u0000\u0001\u0001icc".toByteArray(Charsets.ISO_8859_1)))
    out.write(seg(0xDB, ByteArray(65)))
    out.write(seg(0xC0, byteArrayOf(8, 0, 2, 0, 3, 1, 1, 0x11, 0)))
    out.write(seg(0xDA, byteArrayOf(1, 1, 0, 0, 0x3F, 0)))
    out.write(scan)
    out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
    return out.toByteArray()
  }

  @Test fun `orientation از EXIFِ MM و II`() {
    for (o in 1..8) {
      assertEquals(o, JpegMeta.orientation(jpeg(seg(0xE1, exif(o, false)))))
      assertEquals(o, JpegMeta.orientation(jpeg(seg(0xE1, exif(o, true)))))
    }
    assertEquals(1, JpegMeta.orientation(jpeg()))                              // بی EXIF
    assertEquals(1, JpegMeta.orientation(jpeg(seg(0xE1, exif(9, false)))))      // مقدارِ بی‌معنی
    assertEquals(1, JpegMeta.orientation(byteArrayOf(1, 2, 3)))                 // اصلاً JPEG نیست
  }

  @Test fun `strip — EXIF و GPS و XMP و IPTC و توضیح می‌روند؛ JFIF و ICC و دادهٔ تصویر می‌مانند`() {
    val xmp = seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta GPS/>".toByteArray(Charsets.ISO_8859_1))
    val iptc = seg(0xED, "Photoshop 3.0\u0000".toByteArray(Charsets.ISO_8859_1))
    val com = seg(0xFE, "shot at 34.5,69.1".toByteArray(Charsets.ISO_8859_1))
    val src = jpeg(seg(0xE1, exif(6, false)), xmp, iptc, com)
    assertTrue(JpegMeta.hasExif(src))
    assertTrue(JpegMeta.hasMetadata(src))
    val out = JpegMeta.strip(src)
    assertFalse(JpegMeta.hasExif(out))
    assertFalse(JpegMeta.hasMetadata(out))
    assertEquals(listOf(0xE0, 0xE2, 0xDB, 0xC0, 0xDA), JpegMeta.segments(out).map { it.marker })
    val text = String(out, Charsets.ISO_8859_1)
    assertFalse(text.contains("Exif") || text.contains("xmpmeta") || text.contains("Photoshop") || text.contains("34.5"))
    assertTrue(text.contains("ICC_PROFILE"))
    //  دادهٔ فشرده بایت‌به‌بایت همان
    assertArrayEquals(src.copyOfRange(src.size - scan.size - 2, src.size), out.copyOfRange(out.size - scan.size - 2, out.size))
    assertEquals(1, JpegMeta.orientation(out))
    //  دو بار strip همان است
    assertArrayEquals(out, JpegMeta.strip(out))
  }

  @Test fun `strip — APP2ِ غیرِ ICC (MPF) و هرچه پس از EOI می‌رود`() {
    val mpf = seg(0xE2, "MPF\u0000MM\u0000\u002A".toByteArray(Charsets.ISO_8859_1))
    val tail = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + seg(0xE1, exif(6, false)) + "GPS 34.5,69.1".toByteArray(Charsets.ISO_8859_1)
    val src = jpeg(mpf) + tail
    val out = JpegMeta.strip(src)
    val text = String(out, Charsets.ISO_8859_1)
    assertFalse(text.contains("MPF") || text.contains("Exif") || text.contains("34.5"))
    assertTrue(text.contains("ICC_PROFILE"))
    assertEquals(listOf(0xE0, 0xE2, 0xDB, 0xC0, 0xDA), JpegMeta.segments(out).map { it.marker })
    //  دقیقاً تا EOI؛ دادهٔ فشرده (با 0xFF00ِ پرکن) دست‌نخورده
    assertEquals(0xD9, out.last().toInt() and 0xFF)
    assertArrayEquals(scan + byteArrayOf(0xFF.toByte(), 0xD9.toByte()), out.copyOfRange(out.size - scan.size - 2, out.size))
    assertArrayEquals(out, JpegMeta.strip(out))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `JPEGِ بریده خطاست، نه خروجیِ نیمه`() {
    val src = jpeg(seg(0xE1, exif(6, false)))
    JpegMeta.strip(src.copyOfRange(0, 40))
  }
}
