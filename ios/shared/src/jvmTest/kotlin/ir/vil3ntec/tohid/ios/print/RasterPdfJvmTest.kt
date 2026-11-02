package ir.vil3ntec.tohid.ios.print

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 *  PDF ِ صفحه‌ایِ آیفون (چاپ و «ذخیرهٔ PDF» ِ گزارش، برگهٔ برچسب) — ساختار، جدولِ xref و بایت‌های تصویر.
 *  پرونده‌ای هم در `build/raster-pdf` می‌گذارد تا با `pdfinfo`/`pdftoppm` دستی هم سنجیده شود.
 */
class RasterPdfJvmTest {

  private fun zlib(raw: ByteArray): ByteArray {
    val d = Deflater()
    d.setInput(raw); d.finish()
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (!d.finished()) out.write(chunk, 0, d.deflate(chunk))
    d.end()
    return out.toByteArray()
  }

  private fun inflate(data: ByteArray): ByteArray {
    val i = Inflater()
    i.setInput(data)
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (!i.finished()) out.write(chunk, 0, i.inflate(chunk))
    i.end()
    return out.toByteArray()
  }

  /** صفحهٔ سفید با یک نوارِ سیاه — مثلِ یک سطرِ جدول */
  private fun grayPage(w: Int, h: Int, bar: Int): ByteArray = ByteArray(w * h) { i -> if (i / w in bar until bar + 4) 0 else -1 }

  private fun latin(b: ByteArray) = String(b, Charsets.ISO_8859_1)

  private fun objectAt(pdf: String, offset: Int): Int = Regex("^(\\d+) 0 obj").find(pdf.substring(offset))!!.groupValues[1].toInt()

  @Test
  fun androidA4PageSizeInPoints() {
    assertEquals(595, RasterPdf.points(8270))
    assertEquals(841, RasterPdf.points(11690))
    assertEquals(2479, RasterPdf.pixels(595, 300))
    assertEquals(3504, RasterPdf.pixels(841, 300))
  }

  @Test
  fun pointsUseADotWhateverTheLocale() {
    assertEquals("595.00", RasterPdf.pt(595.0))
    assertEquals("841.68", RasterPdf.pt(841.68))
    assertEquals("0.05", RasterPdf.pt(0.049))
  }

  @Test
  fun xrefPointsAtEveryObjectAndPagesAreCounted() {
    val a = grayPage(60, 80, 10)
    val b = grayPage(60, 80, 40)
    val pdf = RasterPdf.write(listOf(
      RasterPdf.Page(595.0, 841.0, 60, 80, gray = true, flate = zlib(a)),
      RasterPdf.Page(595.0, 841.0, 60, 80, gray = true, flate = zlib(b)),
    ))
    val s = latin(pdf)
    assertTrue(s.startsWith("%PDF-1.4\n"))
    assertTrue(s.endsWith("%%EOF\n"))
    assertTrue("/Kids [3 0 R 6 0 R] /Count 2" in s)
    val startxref = Regex("startxref\n(\\d+)\n%%EOF\n$").find(s)!!.groupValues[1].toInt()
    assertTrue(s.substring(startxref).startsWith("xref\n0 9\n"))
    val rows = s.substring(startxref).lines().drop(3).take(8)
    rows.forEachIndexed { i, row -> assertEquals(i + 1, objectAt(s, row.substring(0, 10).toInt()), "xref ِ شیءِ ${i + 1}") }
    File("build/raster-pdf").mkdirs()
    File("build/raster-pdf/two-pages.pdf").writeBytes(pdf)
  }

  @Test
  fun imageStreamIsTheSamePixelsAndLengthIsExact() {
    val px = grayPage(33, 21, 5)
    val flate = zlib(px)
    val pdf = RasterPdf.write(listOf(RasterPdf.Page(100.0, 50.0, 33, 21, gray = true, flate = flate)))
    val s = latin(pdf)
    val head = Regex("4 0 obj\n<< (.*?) /Length (\\d+) >>\nstream\n").find(s)!!
    assertTrue("/Width 33 /Height 21 /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode" in head.groupValues[1])
    val len = head.groupValues[2].toInt()
    assertEquals(flate.size, len)
    val body = pdf.copyOfRange(head.range.last + 1, head.range.last + 1 + len)
    assertContentEquals(px, inflate(body))
    assertTrue(s.substring(head.range.last + 1 + len).startsWith("\nendstream\nendobj\n"))
    assertTrue("q 100.00 0 0 50.00 0 0 cm /Im0 Do Q" in s)
  }

  @Test
  fun colourPageUsesRgb() {
    val rgb = ByteArray(4 * 3 * 3) { 0x7f }
    val s = latin(RasterPdf.write(listOf(RasterPdf.Page(10.0, 10.0, 4, 3, gray = false, flate = zlib(rgb)))))
    assertTrue("/ColorSpace /DeviceRGB" in s)
  }

  @Test
  fun noPagesIsAnError() {
    assertFailsWith<IllegalArgumentException> { RasterPdf.write(emptyList()) }
  }
}
