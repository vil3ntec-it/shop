package ir.vil3ntec.tohid.core.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RPT-13 — ستونِ «دورهٔ قبل» و «تغییر» در خودِ جدولِ گزارش، ردیف‌به‌ردیف با کلیدِ ستونِ اول (`ReportCompare`) */
class ReportCompareTest {

  private val head = listOf("فروشنده", "تعدادِ فاکتور", "فروشِ خالص")

  @Test
  fun rowsPairByTheirKeyAndPreviousOnlyRowsComeLast() {
    val cur = listOf(head, listOf("علی", "3", "1500"), listOf("رضا", "1", "250.5"))
    val prev = listOf(head, listOf("رضا", "2", "400"), listOf("مینا", "4", "900"), listOf("علی", "1", "1000"))
    val out = ReportCompare.column(cur, prev, 2, "دورهٔ قبل", "تغییر")!!
    assertEquals(head + "دورهٔ قبل" + "تغییر", out[0])
    assertEquals(listOf("علی", "3", "1500", "1000", "500"), out[1])
    assertEquals(listOf("رضا", "1", "250.5", "400", "-149.5"), out[2])
    //  فروشندهٔ دورهٔ قبل که این دوره نفروخت گم نمی‌شود
    assertEquals(listOf("مینا", "", "", "900", "-900"), out[3])
    assertEquals(4, out.size)
  }

  @Test
  fun aNewRowHasNoPreviousValueButStillAChange() {
    val out = ReportCompare.column(listOf(head, listOf("نو", "1", "70")), listOf(head), 2, "p", "c")!!
    assertEquals(listOf("نو", "1", "70", "", "70"), out[1])
  }

  @Test
  fun listSheetsWithoutAUniqueKeyOrADifferentShapeDoNotPair() {
    val dup = listOf(head, listOf("علی", "1", "1"), listOf("علی", "2", "2"))
    assertNull(ReportCompare.column(dup, listOf(head), 2, "p", "c"), "کلیدِ تکراری")
    assertNull(ReportCompare.column(listOf(head, listOf("", "1", "1")), listOf(head), 2, "p", "c"), "کلیدِ خالی")
    assertNull(ReportCompare.column(listOf(head), listOf(listOf("x", "y", "z")), 2, "p", "c"), "سرخطِ دیگر")
    assertNull(ReportCompare.column(listOf(head), listOf(head), 0, "p", "c"), "ستونِ کلید مقایسه نمی‌شود")
    assertNull(ReportCompare.column(listOf(head), listOf(head), 3, "p", "c"))
    assertNull(ReportCompare.column(listOf(head, listOf("a", "1", "متن")), listOf(head), 2, "p", "c"), "ستونِ نا‌عددی")
    assertNull(ReportCompare.column(emptyList(), listOf(head), 2, "p", "c"))
  }

  @Test
  fun numbersKeepTheSheetShape() {
    assertEquals("1200", ReportCompare.format(1200.0))
    assertEquals("-3", ReportCompare.format(-3.0))
    assertEquals("0.1", ReportCompare.format(0.1 + 0.0000001))
    assertEquals("12.35", ReportCompare.format(12.346))
  }
}
