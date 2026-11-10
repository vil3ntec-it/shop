package ir.vil3ntec.tohid.core.pay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RPT-04 — شمارهٔ Z در سریِ دستگاهِ بندنده و پیوستگیِ آن (`ZSeq`). */
class ZSeqTest {

  private fun z(id: String, series: String, no: Long) = ZSeq.Z(id, series, no)

  @Test
  fun eachDeviceSeriesCountsItsOwnZFromOne() {
    assertEquals(1L, ZSeq.next(emptyList(), "A1"))
    val zs = listOf(z("a1", "A1", 1), z("a2", "A1", 2), z("b7", "B1", 7), z("old", "", 0), z("oldA", "A1", 0))
    assertEquals(3L, ZSeq.next(zs, "A1"))
    assertEquals(8L, ZSeq.next(zs, " B1 "), "سری با فاصلهٔ دور همان سری است")
    assertEquals(1L, ZSeq.next(zs, ""), "Z ِ بی شماره شمارنده را جلو نمی‌برد")
    assertEquals(1L, ZSeq.next(zs, "C1"), "سریِ دستگاهِ دیگر شمارهٔ این دستگاه را بالا نمی‌برد")
  }

  @Test
  fun labelIsTheInvoiceNumberLabel() {
    assertEquals("A1-7", ZSeq.label("A1", 7))
    assertEquals("3", ZSeq.label(" ", 3))
  }

  @Test
  fun missingAndDuplicateNumbersAreReportedPerSeries() {
    val r = ZSeq.check(
      listOf(
        z("a1", "A1", 1), z("a2", "A1", 2), z("a5", "A1", 5), z("a6", "A1", 6), z("a6b", "A1", 6),
        z("b3", "B1", 3), z("b4", "B1", 4), z("old", "", 0),
      ),
    )
    assertEquals(listOf(ZSeq.Gap("A1", 3, 4)), r.gaps)
    assertEquals(listOf(ZSeq.Dup("A1", 6, listOf("a6", "a6b"))), r.duplicates)
    assertEquals(1, r.unnumbered)
    assertTrue(!r.clean)
  }

  @Test
  fun nothingBeforeTheFirstOrAfterTheLastNumberAndTheSameRowTwiceIsNotADuplicate() {
    val r = ZSeq.check(listOf(z("b3", "B1", 3), z("b4", "B1", 4), z("b4", "B1", 4)))
    assertTrue(r.clean, r.toString())
    assertEquals(0, r.unnumbered)
    assertTrue(ZSeq.check(emptyList()).clean)
  }
}
