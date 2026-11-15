package ir.vil3ntec.tohid.ui.v3.sales.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  برگهٔ «چاپ» ِ تاریخچه و کادرِ بالا (Compose، پس متن سنجیده می‌شود). خواستِ صاحب‌کار: «همهٔ فاکتورهای یک روز، ماه یا
 *  سال یک‌جا روی چاپگرِ حرارتی» ⇒ پیش‌فرض «همهٔ فاکتورها»، «خلاصه» می‌ماند، فیلترِ فهرست چیزِ چاپ‌شده را عوض نمی‌کند
 *  (فقط یک سطرِ آگاهی)، و تراشه‌های کادرِ بالا (چاپ، صندوق، بررسی) بریده نمی‌شوند.
 */
class HistoryPrintWiringTest {

  private fun src(name: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3/sales/history/$name") }.firstOrNull { it.isFile }?.readText()
      ?: error("$name پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test fun `پیش‌فرضِ برگهٔ چاپ همهٔ فاکتورهاست و خلاصه می‌ماند`() {
    val s = src("HistoryHostAndroid.kt")
    assertTrue(s.contains("var all by remember { mutableStateOf(p.sales.isNotEmpty()) }"))
    val all = s.indexOf("Option(StrHistory.allInvoices, StrHistory.allInvoicesHint")
    val summary = s.indexOf("Option(StrHistory.summary, StrHistory.summaryHint")
    assertTrue("$all / $summary", all > 0 && summary > all)
  }

  @Test fun `فیلترِ فهرست چیزِ چاپ‌شده را عوض نمی‌کند — فقط یک سطرِ آگاهی`() {
    val s = src("HistoryHostAndroid.kt")
    assertTrue(s.contains("filtered = query.filtered,"))
    assertTrue(s.contains("if (filtered && all) Text(StrHistory.printIgnoresFilter(plain(count))"))
    //  سندها از خودِ دوره ساخته می‌شوند، نه از فهرستِ فیلترشدهٔ صفحه
    assertTrue(s.contains("PeriodSlip.parts(PeriodSlip.sections(p, head, PeriodSlip.source(book.look), width, fmt, money), width)"))
    assertFalse(s.contains("filteredOf("))
  }

  /**
   *  ۶۳w: کادرِ گرادیانی (`wrapChips = true`) جایش را به خلاصهٔ آرام (`V3Summary`) داد که کارهایش را همیشه در `FlowRow`
   *  به ردیفِ بعد می‌برد؛ قاعده همان: «چاپ» پیش از صندوقِ بلند، تا در ردیفِ نخست بماند.
   */
  @Test fun `خلاصهٔ بالای تاریخچه کارها را به ردیفِ بعد می‌برد، نمی‌بُرد`() {
    assertTrue(src("HistoryParts.kt").contains("V3Summary("))
    val summary = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3/V3Summary.kt") }.first { it.isFile }.readText()
    assertTrue(summary.contains("FlowRow(Modifier.fillMaxWidth()"))
    val top = src("HistoryScreen.kt")
    assertTrue(top.indexOf("V3SummaryAction(StrHistory.print") in 1 until top.indexOf("V3SummaryAction(it.text, Icons.Outlined.PointOfSale"))
  }
}
