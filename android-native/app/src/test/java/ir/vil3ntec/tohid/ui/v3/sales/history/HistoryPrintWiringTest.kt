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

  @Test fun `کادرِ بالای تاریخچه تراشه‌ها را به ردیفِ بعد می‌برد، نمی‌بُرد`() {
    assertTrue(src("HistoryParts.kt").contains("wrapChips = true,"))
    val top = src("HistoryScreen.kt")
    //  صندوقِ بلند واپسین تراشه است تا «چاپ» در ردیفِ نخست بماند
    assertTrue(top.indexOf("V3HeroChip(StrHistory.print") in 1 until top.indexOf("V3HeroChip(it.text, Icons.Outlined.PointOfSale"))
  }
}
