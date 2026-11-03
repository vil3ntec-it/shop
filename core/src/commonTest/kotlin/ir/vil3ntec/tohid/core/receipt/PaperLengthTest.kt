package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.hw.PrinterProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** بلندیِ تخمینیِ کاغذ برای برگهٔ «چاپ» ِ تاریخچه، روی ۵۸ و ۸۰ میلی‌متر */
class PaperLengthTest {

  private fun doc(width: Int, blocks: List<ReceiptBlock>) = ReceiptDoc(width, ReceiptMetrics.of(width), blocks)

  private val invoice = listOf(
    ReceiptBlock.Text("#1245 · 10:32 · احمد", TextStyle.BODY, center = false),
    ReceiptBlock.Row("برنج × 2.5", "950", TextStyle.SMALL),
    ReceiptBlock.Row("روغن × 1", "450", TextStyle.SMALL),
    ReceiptBlock.Row("جمع", "1,400"),
    ReceiptBlock.Rule(),
  )

  @Test
  fun longerTextWrapsMoreOnNarrowPaper() {
    val text = "نامِ بسیار بلندِ یک کالا که روی کاغذِ باریک دو یا سه سطر می‌شود"
    val m58 = ReceiptMetrics.MM58
    val m80 = ReceiptMetrics.MM80
    val l58 = PaperLength.lines(text, m58.body, PrinterProfile.WIDTH_58MM - 2 * m58.pad)
    val l80 = PaperLength.lines(text, m80.body, PrinterProfile.WIDTH_80MM - 2 * m80.pad)
    assertTrue(l58 >= 2, "۵۸: $l58")
    assertTrue(l58 >= l80)
    assertEquals(2, PaperLength.lines("یک\nدو", m58.body, 300))
    assertEquals(1, PaperLength.lines("", m58.body, 300))
  }

  @Test
  fun moreInvoicesNeedMorePaperAndEightyMillimetreUsesBiggerType() {
    val one = PaperLength.dots(doc(PrinterProfile.WIDTH_58MM, invoice))
    val ten = PaperLength.dots(doc(PrinterProfile.WIDTH_58MM, List(10) { invoice }.flatten()))
    assertTrue(ten > one * 5, "$one / $ten")
    //  همان محتوا روی ۸۰ میلی‌متر قلمِ درشت‌تر دارد ⇒ هر سطر بلندتر
    val wide = PaperLength.dots(doc(PrinterProfile.WIDTH_80MM, invoice))
    assertTrue(wide > one, "$one / $wide")
  }

  @Test
  fun feedAndMarginsCountAndCentimetresRoundUp() {
    val d = doc(PrinterProfile.WIDTH_58MM, listOf(ReceiptBlock.Gap(100)))
    assertEquals(ReceiptMetrics.MM58.pad * 3 + 100, PaperLength.dots(d, feedLines = 0))
    assertEquals(ReceiptMetrics.MM58.pad * 3 + 100 + 3 * 34, PaperLength.dots(d))
    assertEquals(1, PaperLength.cm(1))
    assertEquals(1, PaperLength.cm(80))
    assertEquals(2, PaperLength.cm(81))
    assertEquals(0, PaperLength.cm(0))
  }
}
