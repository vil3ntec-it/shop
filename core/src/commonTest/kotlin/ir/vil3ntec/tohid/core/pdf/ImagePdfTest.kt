package ir.vil3ntec.tohid.core.pdf

import ir.vil3ntec.tohid.core.hw.MonoImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** RPT-11 — PDFِ تصویریِ گزارش: فشرده‌سازیِ RLE و ساختارِ فایل (xref دقیق) */
class ImagePdfTest {

  /** صفحهٔ نمونه: کادر، چند سطرِ «متن» و خطِ جدول — بیشترش سفید، مثلِ گزارش */
  private fun sample(w: Int, h: Int, seed: Int): MonoImage {
    val img = MonoImage(w, h)
    img.fill(0, 0, w, 3); img.fill(0, h - 3, w, 3); img.fill(0, 0, 3, h); img.fill(w - 3, 0, 3, h)
    var y = 40
    var s = seed
    while (y < h - 40) {
      img.fill(20, y, w - 40, 1)
      var x = 30
      while (x < w - 60) { s = (s * 1103515245 + 12345) and 0x7fffffff; val len = 4 + s % 30; img.fill(x, y + 6, len, 12); x += len + 8 }
      y += 28
    }
    return img
  }

  private fun ascii(b: ByteArray, from: Int, len: Int) = b.copyOfRange(from, minOf(b.size, from + len)).decodeToString()

  @Test fun `RLE رفت و برگشت - سفید، تکرار، الگوی درهم و مرزهای ۱۲۸`() {
    val cases = listOf(
      ByteArray(0), ByteArray(1) { 7 }, ByteArray(1000), ByteArray(129) { 0x55 }, ByteArray(300) { (it % 7).toByte() },
      ByteArray(257) { if (it % 2 == 0) 1 else 2 }, byteArrayOf(1, 1, 2, 3, 3, 3, 4), ByteArray(128) { it.toByte() },
    )
    for (c in cases) {
      val enc = ImagePdf.runLength(c)
      assertEquals(128, enc.last().toInt() and 0xFF, "پایان با EOD")
      assertContentEquals(c, ImagePdf.runLengthDecode(enc))
    }
    //  صفحهٔ A4 ِ گزارش در ۱۵۰ نقطه (سطرهای پرِ نمونه، ۴۳٪ سیاه): دست‌کم دو برابر کوچک‌تر؛ صفحهٔ سفید پنجاه برابر
    val page = sample(1240, 1754, 1)
    assertTrue(ImagePdf.runLength(page.bits).size * 2 < page.bits.size, "${ImagePdf.runLength(page.bits).size} از ${page.bits.size}")
    val blank = MonoImage(1240, 1754)
    assertTrue(ImagePdf.runLength(blank.bits).size * 50 < blank.bits.size)  // سقفِ PackBits: ۱۲۸ بایت در ۲ بایت
  }

  @Test fun `ساختار PDF - هر ورودی xref دقیقاً سر شیء خودش، startxref و EOF`() {
    val pdf = ImagePdf.write(listOf(sample(1240, 1754, 1), sample(1240, 1754, 2)), dpi = 150, title = "گزارش فروش")
    val text = pdf.decodeToString()
    assertTrue(text.startsWith("%PDF-1.4\n"))
    assertTrue(text.endsWith("%%EOF\n"))
    val sx = text.lastIndexOf("startxref\n")
    val xrefAt = text.substring(sx + 10).substringBefore('\n').toInt()
    assertEquals("xref\n", ascii(pdf, xrefAt, 5))
    val lines = text.substring(xrefAt).lines()
    val count = lines[1].split(' ')[1].toInt()
    assertEquals(3 + 2 * 3 + 1, count)
    for (k in 1 until count) {
      val off = lines[2 + k].substring(0, 10).toInt()
      assertEquals("$k 0 obj\n", ascii(pdf, off, "$k 0 obj\n".length), "xref شیءِ $k")
    }
    assertTrue("/Count 2" in text)
    assertTrue("/MediaBox [0 0 595.2 841.92]" in text, "A4 در ۱۵۰ نقطه")
    assertTrue("/Title <${ImagePdf.utf16Hex("گزارش فروش")}>" in text, "عنوانِ دری در Info")
    assertEquals("FEFF06AF0632", ImagePdf.utf16Hex("گز"))
    //  طولِ هر جریان همان بایت‌های میانِ stream و endstream
    var at = 0
    while (true) {
      val l = text.indexOf("/Length ", at); if (l < 0) break
      val len = text.substring(l + 8).takeWhile { it.isDigit() }.toInt()
      val s = text.indexOf("stream\n", l) + 7
      assertEquals("\nendstream", ascii(pdf, s + len, 10))
      at = s + len
    }
  }

  @Test fun `اندازه صفحه و ورودی نامعتبر`() {
    assertEquals("595.2", ImagePdf.pt(1240, 150))
    assertEquals("72", ImagePdf.pt(300, 300))
    assertEquals("841.92", ImagePdf.pt(1754, 150))
    assertFailsWith<IllegalArgumentException> { ImagePdf.write(emptyList()) }
    assertFailsWith<IllegalArgumentException> { ImagePdf.write(listOf(MonoImage(8, 8)), dpi = 5) }
  }
}
