package ir.vil3ntec.tohid.core.nfc

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** برچسبِ نوعِ ۴ با رکوردِ URI — همان گفت‌وگویی که گوشیِ مشتری با دستگاهِ دکان می‌کند */
class NdefTagTest {

  private fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
  private fun ByteArray.hexOf(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

  private val selectApp = hex("00 A4 04 00 07 D2760000850101 00")
  private val selectCc = hex("00 A4 00 0C 02 E103")
  private val selectNdef = hex("00 A4 00 0C 02 E104")
  private fun readBinary(offset: Int, len: Int) = byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), len.toByte())

  private val url = "https://pay.example/c/pi_1"

  @Test
  fun fullReaderSessionReturnsTheUri() {
    val tag = NdefType4Tag()
    assertEquals("9000", tag.process(selectApp, url).hexOf())
    assertEquals("9000", tag.process(selectCc, url).hexOf())
    //  CC: نسخهٔ ۲٫۰، MLe ۵۹، MLc ۵۲، پروندهٔ E104 تا ۱۰۲۴ بایت، خواندن آزاد، نوشتن هرگز
    assertEquals("000F20003B00340406E104040000FF9000", tag.process(readBinary(0, 15), url).hexOf())
    assertEquals("9000", tag.process(selectNdef, url).hexOf())
    val nlen = tag.process(readBinary(0, 2), url)
    val n = ((nlen[0].toInt() and 0xFF) shl 8) or (nlen[1].toInt() and 0xFF)
    assertEquals("9000", nlen.copyOfRange(2, 4).hexOf())
    //  خواندن تکه‌تکه، هر بار حداکثر MLe
    val msg = ArrayList<Byte>()
    var at = 2
    while (at < n + 2) {
      val r = tag.process(readBinary(at, minOf(NdefType4Tag.MLE, n + 2 - at)), url)
      assertEquals("9000", r.copyOfRange(r.size - 2, r.size).hexOf())
      msg += r.copyOfRange(0, r.size - 2).toList()
      at += r.size - 2
    }
    val m = msg.toByteArray()
    //  رکوردِ کوتاه، TNF = well-known، نوعِ «U»، پیشوندِ 0x04 = https://
    assertEquals(0xD1, m[0].toInt() and 0xFF)
    assertEquals(1, m[1].toInt())
    assertEquals(m.size - 4, m[2].toInt() and 0xFF)
    assertEquals('U'.code, m[3].toInt())
    assertEquals(0x04, m[4].toInt())
    assertEquals("pay.example/c/pi_1", m.copyOfRange(5, m.size).decodeToString())
  }

  @Test
  fun noActivePaymentMeansFileNotFound() {
    val tag = NdefType4Tag()
    assertEquals("6A82", tag.process(selectApp, null).hexOf())
    assertEquals("6A82", tag.process(selectNdef, null).hexOf())
    assertEquals("6A82", tag.process(readBinary(0, 2), null).hexOf())
    //  پرداخت فعال شد و دوباره بسته شد ⇐ انتخابِ قبلی هم باطل
    assertEquals("9000", tag.process(selectApp, url).hexOf())
    assertEquals("6A82", tag.process(selectNdef, null).hexOf())
    assertEquals("6986", tag.process(readBinary(0, 2), url).hexOf())
  }

  @Test
  fun otherAidsAndFilesAreRefused() {
    val tag = NdefType4Tag()
    assertEquals("6A82", tag.process(hex("00 A4 04 00 07 A0000000041010 00"), url).hexOf())
    //  پرونده پیش از انتخابِ برنامه
    assertEquals("6A82", tag.process(selectCc, url).hexOf())
    tag.process(selectApp, url)
    assertEquals("6A82", tag.process(hex("00 A4 00 0C 02 E105"), url).hexOf())
    assertEquals("6986", tag.process(readBinary(0, 2), url).hexOf())
  }

  @Test
  fun readBinaryBoundsAreChecked() {
    val tag = NdefType4Tag()
    tag.process(selectApp, url)
    tag.process(selectCc, url)
    //  بیرون از پرونده
    assertEquals("6B00", tag.process(readBinary(16, 1), url).hexOf())
    //  درازتر از پرونده ⇐ فقط آنچه هست
    assertEquals(15 + 2, tag.process(readBinary(0, 0x30), url).size)
    //  پایانِ پرونده ⇐ خالی و 9000
    assertEquals("9000", tag.process(readBinary(15, 1), url).hexOf())
    //  شناسهٔ کوتاهِ پرونده در P1 پشتیبانی نمی‌شود
    assertEquals("6B00", tag.process(byteArrayOf(0x00, 0xB0.toByte(), 0x81.toByte(), 0x00, 0x02), url).hexOf())
    //  Le = 0 یعنی ۲۵۶، ولی هرگز بیش از MLe
    val long = "https://pay.example/" + "b".repeat(200)
    tag.process(selectApp, long)
    tag.process(selectNdef, long)
    val r = tag.process(readBinary(0, 0), long)
    assertEquals(NdefType4Tag.MLE + 2, r.size)
    assertEquals("9000", r.copyOfRange(r.size - 2, r.size).hexOf())
  }

  @Test
  fun malformedApdusAreRejected() {
    val tag = NdefType4Tag()
    assertEquals("6700", tag.process(byteArrayOf(0x00, 0xA4.toByte()), url).hexOf())
    assertEquals("6E00", tag.process(hex("80 A4 04 00 07 D2760000850101 00"), url).hexOf())
    assertEquals("6D00", tag.process(hex("00 D6 00 00 01 00"), url).hexOf())
    assertEquals("6700", tag.process(hex("00 A4 04 00 09 D2760000850101"), url).hexOf())
    assertEquals("6B00", tag.process(hex("00 A4 02 00 02 E104"), url).hexOf())
  }

  @Test
  fun resetForgetsSelection() {
    val tag = NdefType4Tag()
    tag.process(selectApp, url)
    tag.process(selectNdef, url)
    tag.reset()
    assertEquals("6986", tag.process(readBinary(0, 2), url).hexOf())
  }

  @Test
  fun longUriUsesLongRecordAndTooLongIsRefused() {
    val long = "https://pay.example/" + "a".repeat(300)
    val m = Ndef.uriMessage(long)
    assertEquals(0xC1, m[0].toInt() and 0xFF)
    assertEquals(1 + 300 + "pay.example/".length, ((m[2].toInt() and 0xFF) shl 24) or ((m[3].toInt() and 0xFF) shl 16) or ((m[4].toInt() and 0xFF) shl 8) or (m[5].toInt() and 0xFF))
    assertNull(Ndef.file("https://pay.example/" + "a".repeat(NdefType4Tag.MAX_NDEF_FILE)))
    assertEquals("6A82", NdefType4Tag().process(selectApp, "https://pay.example/" + "a".repeat(2000)).hexOf())
  }

  @Test
  fun onlyWebLinksAreServed() {
    assertNull(Ndef.file(""))
    assertNull(Ndef.file("javascript:alert(1)"))
    assertNull(Ndef.file("intent://x"))
    assertNull(Ndef.file("https://pay.example/a b"))
    assertContentEquals(byteArrayOf(0x03) + "x.io".encodeToByteArray(), Ndef.uriMessage("http://x.io").copyOfRange(4, 9))
    assertEquals(0x02, Ndef.uriMessage("https://www.x.io")[4].toInt())
  }
}
