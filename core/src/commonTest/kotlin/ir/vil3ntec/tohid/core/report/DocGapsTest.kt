package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.report.DocGaps.Cause
import ir.vil3ntec.tohid.core.report.DocGaps.Doc
import ir.vil3ntec.tohid.core.report.DocGaps.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  RPT-17 — شماره‌های جاافتادهٔ اسناد. «پرشِ شمارنده» پس از همگام‌سازی جاافتاده نیست؛
 *  جای خالی‌ای که هیچ سندی در دفتر دلیلش نیست، جاافتاده است.
 */
class DocGapsTest {

  private fun sale(series: String, n: Long, id: String = "$series-$n", date: String = "2026-10-0${(n % 9) + 1}", cancelled: Boolean = false) =
    Doc(Kind.SALE, series, n, id, date, cancelled)

  private fun purchase(n: Long) = Doc(Kind.PURCHASE, "", n, "p$n", "2026-10-01")

  @Test
  fun contiguousSeriesIsClean() {
    val r = DocGaps.of((1L..5L).map { sale("A1", it) })
    assertTrue(r.clean)
    assertEquals(0L, r.missing)
    assertEquals(0L, r.jumped)
    assertEquals(1, r.runs.size)
    val run = r.runs.single()
    assertEquals(1L, run.first)
    assertEquals(5L, run.last)
    assertEquals(5, run.docs)
    assertTrue(run.gaps.isEmpty())
  }

  @Test
  fun counterJumpAfterSyncIsNotMissing() {
    //  A1 تا ۳ رفت؛ B1 در همان مدت ۴ تا ۱۰ را زد؛ A1 پس از همگام‌سازی از ۱۱ ادامه داد
    val docs = (1L..3L).map { sale("A1", it) } + (4L..10L).map { sale("B1", it) } + sale("A1", 11)
    val r = DocGaps.of(docs)
    assertEquals(0L, r.missing)
    assertEquals(7L, r.jumped)
    assertTrue(r.clean)
    val a1 = r.runs.first { it.series == "A1" }
    val g = a1.gaps.single()
    assertEquals(4L, g.from)
    assertEquals(10L, g.to)
    assertEquals(7L, g.count)
    assertEquals(Cause.JUMP, g.cause)
    assertEquals("B1", g.jumpSeries)
    assertEquals("A1-3", g.beforeId)
    assertEquals("A1-11", g.afterId)
    assertTrue(r.runs.first { it.series == "B1" }.gaps.isEmpty())
  }

  @Test
  fun deletedInvoiceIsMissingWithNeighbours() {
    val docs = listOf(sale("A1", 1), sale("A1", 2, date = "2026-10-05"), sale("A1", 4, date = "2026-10-06"), sale("A1", 5))
    val r = DocGaps.of(docs)
    assertFalse(r.clean)
    assertEquals(1L, r.missing)
    val g = r.runs.single().gaps.single()
    assertEquals(3L, g.from)
    assertEquals(3L, g.to)
    assertEquals(Cause.MISSING, g.cause)
    assertEquals("", g.jumpSeries)
    assertEquals("A1-2", g.beforeId)
    assertEquals("2026-10-05", g.beforeDate)
    assertEquals("A1-4", g.afterId)
    assertEquals("2026-10-06", g.afterDate)
  }

  @Test
  fun jumpNeedsTheNumberJustBeforeTheNextDocument() {
    //  A1 از ۱ به ۵ پرید چون B1-4 را دیده بود؛ B1-4 بعداً پاک شد ⇒ دیگر دلیلی در دفتر نیست
    val docs = listOf(sale("A1", 1), sale("A1", 5), sale("B1", 2), sale("B1", 3))
    val r = DocGaps.of(docs)
    val g = r.runs.first { it.series == "A1" }.gaps.single()
    assertEquals(Cause.MISSING, g.cause)
    assertEquals(3L, r.missing)
    //  B1 تا ۳ پشتِ هم است؛ پاک‌شدنِ آخرین سندش فقط از جای خالیِ A1 دیده می‌شود
    assertTrue(r.runs.first { it.series == "B1" }.gaps.isEmpty())
  }

  @Test
  fun cancelledKeepsItsNumber() {
    val docs = listOf(sale("A1", 1), sale("A1", 2, cancelled = true), sale("A1", 3))
    val r = DocGaps.of(docs)
    assertTrue(r.clean)
    assertEquals(1, r.cancelled)
    assertEquals(1, r.runs.single().cancelled)
    assertTrue(r.runs.single().gaps.isEmpty())
  }

  @Test
  fun sameNumberTwiceInOneSeriesIsDuplicate() {
    val docs = listOf(sale("A1", 1), sale("A1", 2, id = "x"), sale("A1", 2, id = "a"), sale("A1", 3), sale("A1", 3, id = "A1-3"))
    val r = DocGaps.of(docs)
    assertEquals(1, r.duplicates)
    assertFalse(r.clean)
    val dup = r.runs.single().duplicates.single()
    assertEquals(2L, dup.number)
    assertEquals(listOf("a", "x"), dup.ids)
    //  همان شناسه دو بار ⇒ یک سند
    assertEquals(4, r.runs.single().docs)
  }

  @Test
  fun purchasesNeverJumpAndKindsDoNotMix() {
    //  فروشِ شمارهٔ ۲ دلیلِ جای خالیِ خرید نیست
    val docs = listOf(purchase(1), purchase(3), sale("A1", 2), sale("A1", 1))
    val r = DocGaps.of(docs)
    val p = r.runs.first { it.kind == Kind.PURCHASE }
    assertEquals(Cause.MISSING, p.gaps.single().cause)
    assertEquals(1L, p.missing)
    assertEquals(listOf(Kind.SALE, Kind.PURCHASE), r.runs.map { it.kind })
  }

  @Test
  fun untrackedSeriesExplainsJumpsButIsNotReported() {
    //  فروشِ سفارشِ آنلاین «O-40» شمارهٔ سفارشِ سرور را دارد؛ دستگاه پس از دیدنش از ۴۱ ادامه داد
    val docs = listOf(sale("A1", 1), sale("A1", 2), sale("O", 40), sale("O", 7), sale("A1", 41))
    val r = DocGaps.of(docs, untracked = setOf("O"))
    assertEquals(listOf("A1"), r.runs.map { it.series })
    assertEquals(2, r.external)
    val g = r.runs.single().gaps.single()
    assertEquals(Cause.JUMP, g.cause)
    assertEquals("O", g.jumpSeries)
    assertEquals(0L, r.missing)
    //  بی `untracked` همان سری خودش گزارش می‌شود و جای خالیِ ۸ تا ۳۹ «جاافتاده» است
    val plain = DocGaps.of(docs)
    assertEquals(32L, plain.runs.first { it.series == "O" }.missing)
  }

  @Test
  fun unnumberedAndBlankSeries() {
    val docs = listOf(sale("", 1), sale("", 0, id = "old"), sale(" ", 2), sale("A1 ", 9), sale("A1", 10))
    val r = DocGaps.of(docs)
    assertEquals(1, r.unnumbered)
    assertEquals(listOf("", "A1"), r.runs.map { it.series })
    assertEquals(2, r.runs.first().docs)
    assertTrue(r.clean)
  }

  @Test
  fun listIsCappedMissingFirstButCountsStayExact() {
    //  A1: پرشِ ۴..۱۰ (B1) و جاافتادهٔ ۱۳؛ سقفِ یک ⇒ فقط جاافتاده فهرست می‌شود
    val docs = (1L..3L).map { sale("A1", it) } + (4L..10L).map { sale("B1", it) } + listOf(sale("A1", 11), sale("A1", 12), sale("A1", 14))
    val r = DocGaps.of(docs, maxGaps = 1)
    assertEquals(2, r.gaps)
    assertEquals(1, r.listed)
    assertTrue(r.truncated)
    assertEquals(Cause.MISSING, r.runs.first { it.series == "A1" }.gaps.single().cause)
    assertEquals(1L, r.missing)
    assertEquals(7L, r.jumped)
    assertFalse(DocGaps.of(docs).truncated)
  }

  @Test
  fun labelUsesSeries() {
    assertEquals("A1-154", DocGaps.label("A1", 154))
    assertEquals("154", DocGaps.label(" ", 154))
  }
}
