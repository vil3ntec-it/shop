package ir.vil3ntec.tohid.core.pdf

import ir.vil3ntec.tohid.core.hw.MonoImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 *  RPT-11 — فایلِ نمونه برای سنجشِ بیرونی: `build/tmp/imagepdf/sample.pdf` و بیت‌های خامِ صفحهٔ اول
 *  (`page1.bits`). سنجش با `pdfinfo` و pypdf (بازکردنِ RunLengthDecode و برابریِ بیت‌ها) در PR آمده است.
 */
class ImagePdfFileTest {
  @Test fun writesSampleFile() {
    val a = MonoImage(1240, 1754)
    a.fill(0, 0, 1240, 4); a.fill(100, 200, 1040, 2); a.fill(100, 400, 300, 120)
    val b = MonoImage(1240, 1754)
    b.fill(600, 800, 40, 40)
    val dir = File("build/tmp/imagepdf").apply { mkdirs() }
    File(dir, "sample.pdf").writeBytes(ImagePdf.write(listOf(a, b), 150, "گزارش آزمون"))
    File(dir, "page1.bits").writeBytes(a.bits)
    assertTrue(File(dir, "sample.pdf").length() > 0)
  }
}
