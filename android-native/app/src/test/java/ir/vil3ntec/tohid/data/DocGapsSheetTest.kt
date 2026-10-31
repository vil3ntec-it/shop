package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.report.DocGaps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RPT-17 — دفتر ⇒ شماره‌های جاافتاده: سریِ دستگاه، سریِ سرور «O»، فاکتورِ خرید و برگه */
class DocGapsSheetTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private fun sale(id: String, no: Int?, series: String?, status: String = "completed", date: String = "2026-10-20") =
    Sale(id = id, invoiceNumber = no, series = series, status = status, date = date)

  private fun buy(id: String, no: Int, date: String = "2026-10-01") = Purchase(id = id, invoiceNo = no, date = date)

  /**
   *  A1: ۱، ۲ (لغو)، ۳، ۴۲ — پرش به خاطرِ «O-41» (فروشِ سفارشِ آنلاین)؛ ۴۴ ⇒ ۴۳ جاافتاده.
   *  B1: ۱۰ و ۱۲ ⇒ ۱۱ جاافتاده. خرید: #۱ (دو ردیف) و #۳ ⇒ #۲ جاافتاده.
   */
  private val d = ShopData(
    sales = listOf(
      sale("s1", 1, "A1"), sale("s2", 2, "A1", status = "cancelled"), sale("s3", 3, "A1"),
      sale("o-1", 41, "O"), sale("s4", 42, "A1"), sale("s5", 44, "A1", date = "2026-10-22"),
      sale("s6", 10, "B1"), sale("s7", 12, "B1"), sale("old", null, null),
    ),
    purchases = listOf(buy("p1", 1), buy("p1b", 1), buy("p3", 3, "2026-10-03"), buy("legacy", 0)),
  )

  @Test
  fun ledgerMapsToDocsWithServerSeriesExplainingJumps() {
    val r = DocGapsSheet.report(d)
    assertEquals(listOf("A1", "B1", ""), r.runs.map { it.series })
    assertEquals(1, r.external)
    assertEquals(1, r.unnumbered)
    assertEquals(1, r.cancelled)
    val a1 = r.runs.first { it.series == "A1" }
    assertEquals(listOf(DocGaps.Cause.JUMP to (4L..41L), DocGaps.Cause.MISSING to (43L..43L)), a1.gaps.map { it.cause to (it.from..it.to) })
    assertEquals("O", a1.gaps.first().jumpSeries)
    assertEquals(3L, r.missing)
    //  دو ردیفِ یک فاکتورِ خرید یک سندند، نه شمارهٔ تکراری
    val buys = r.runs.first { it.kind == DocGaps.Kind.PURCHASE }
    assertEquals(2, buys.docs)
    assertEquals(0, r.duplicates)
    assertEquals(2L to 2L, buys.gaps.single().let { it.from to it.to })
  }

  @Test
  fun sheetListsMissingFirstWithLabelsAndDates() {
    I18n.source = { Lang.FA }
    val rows = DocGapsSheet.sheet(d, "2026-10-31").rows
    assertEquals(StrReports.colKind, rows[0][0])
    assertEquals(8, rows[0].size)
    val body = rows.drop(1)
    assertEquals(4, body.size)
    //  اول سه جاافتاده (A1-43، B1-11، خریدِ #2)، بعد پرش
    assertEquals(listOf("A1-43", "B1-11", "#2", "A1-4"), body.map { it[2] })
    assertTrue(body.take(3).all { it[5] == StrReports.statusMissing })
    assertEquals(StrReports.statusJump("O"), body[3][5])
    assertEquals("A1-41", body[3][3])
    assertEquals("38", body[3][4])
    assertEquals(listOf("2026-10-20", "2026-10-22"), body[0].subList(6, 8))
    assertEquals(StrReports.kindPurchase, body[2][0])
    assertEquals(StrReports.noSeries, body[2][1])
  }

  @Test
  fun duplicatesAndCapAreRows() {
    val dup = d.copy(sales = d.sales + sale("x", 3, "A1"))
    val rows = DocGapsSheet.sheet(dup, "2026-10-31", maxGaps = 1).rows.drop(1)
    assertEquals("A1-43", rows[0][2])
    assertEquals(StrReports.statusDuplicate(2), rows[1][5])
    assertEquals(StrReports.moreGaps(3), rows.last()[5])
  }

  @Test
  fun cleanLedgerHasOnlyHeader() {
    val clean = ShopData(sales = listOf(sale("a", 1, "A1"), sale("b", 2, "A1")))
    assertTrue(DocGapsSheet.sheet(clean, "2026-10-31").isEmpty)
    assertTrue(DocGapsSheet.report(clean).clean)
  }
}
