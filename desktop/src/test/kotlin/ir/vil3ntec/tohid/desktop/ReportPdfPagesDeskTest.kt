package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.print.ReportPrint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  RPT-11 — PDF ِ مستقیمِ گزارش روی کامپیوتر بیش از سقفِ صفحه بی‌صدا بریده نمی‌شد که «ذخیره شد» بگوید:
 *  یک صفحه بیش از سقف ⇒ خطای روشن با متنِ سه‌زبانه، و هیچ PDF ِ ناقصی ساخته نمی‌شود.
 *  (سقفِ واقعی ۵۰۰ است؛ این‌جا با سقفِ کوچک همان راه سنجیده می‌شود تا ۵۰۱ صفحه رسم نشود.)
 */
class ReportPdfPagesDeskTest {

  /** ۲۰۰ ردیف ⇒ چند صفحهٔ A4 */
  private val sheet = ReportCsv.Sheet("گزارش-آزمون", listOf(listOf("کالا", "مقدار", "مبلغ")) + List(200) { listOf("کالای $it", "$it", "${it * 10}") })

  @Test fun `بیش از سقفِ صفحه ⇒ خطای روشن، نه PDF ِ ناقص`() {
    try {
      ReportPrint.render(sheet, "آزمون", maxPages = 1)
      fail("گزارشِ چندصفحه‌ای با سقفِ یک صفحه نباید PDF بسازد")
    } catch (e: ReportPrint.TooManyPages) {
      assertEquals(StrShell.pdfTooManyPages("1"), e.message)
    }
  }

  @Test fun `زیرِ سقف همان PDF ِ کامل`() {
    val pdf = ReportPrint.render(sheet, "آزمون", maxPages = 50)
    assertTrue(String(pdf.copyOfRange(0, 5), Charsets.US_ASCII) == "%PDF-")
  }
}
