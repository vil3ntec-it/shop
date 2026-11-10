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
  /* ---------- برگهٔ فهرستیِ سند (فاکتور، خرید، مصرف): گروه با ستونِ دسته‌ای ---------- */

  private val list = listOf("تاریخ", "عنوان", "دسته", "مبلغ", "یادداشت")

  @Test
  fun aListSheetGroupsByItsKeyColumnWithCountAndExactSum() {
    val rows = listOf(
      list,
      listOf("2026-09-01", "برق", "قبض", "1200.5", ""),
      listOf("2026-09-02", "کرایه", "کرایه", "5000", ""),
      listOf("2026-09-03", "آب", "قبض", "0.1", ""),
      listOf("2026-09-04", "چای", "", "0.2", ""),
      listOf("2026-09-05", "نان", " ", "", ""),
    )
    val out = ReportCompare.group(rows, 2, 3, "تعداد", "—")!!
    assertEquals(listOf("دسته", "تعداد", "مبلغ"), out[0])
    //  جمعِ بزرگ‌تر اول؛ ۰٫۱ + ۱۲۰۰٫۵ به صدم، بی خطای اعشار
    assertEquals(listOf("کرایه", "1", "5000"), out[1])
    assertEquals(listOf("قبض", "2", "1200.6"), out[2])
    //  دستهٔ خالی یک گروهِ نام‌دار است؛ خانهٔ عددِ خالی صفر است و ردیف شمرده می‌شود
    assertEquals(listOf("—", "2", "0.2"), out[3])
    assertEquals(4, out.size)
    //  جمعِ گروه‌ها همان جمعِ ستون است
    assertEquals(620080L, out.drop(1).sumOf { kotlin.math.round(it[2].toDouble() * 100).toLong() })
  }

  @Test
  fun rowsTheCallerExcludesAreNotCounted() {
    val head = listOf("شماره فاکتور", "روش پرداخت", "مبلغ نهایی", "وضعیت")
    val rows = listOf(head, listOf("1", "نقدی", "300", "ok"), listOf("2", "نقدی", "900", "void"), listOf("3", "قرض", "100", "ok"))
    val out = ReportCompare.group(rows, 1, 2, "n", "—", include = { it[3] != "void" })!!
    assertEquals(listOf(listOf("روش پرداخت", "n", "مبلغ نهایی"), listOf("نقدی", "1", "300"), listOf("قرض", "1", "100")), out)
  }

  @Test
  fun equalSumsFollowTheKeySoTheOrderIsStable() {
    val head = listOf("k", "v")
    val out = ReportCompare.group(listOf(head, listOf("ب", "5"), listOf("الف", "5")), 0, 1, "n", "—")!!
    assertEquals(listOf("الف", "ب"), out.drop(1).map { it[0] })
  }

  @Test
  fun aSheetThatIsNotTheListDoesNotGroup() {
    assertNull(ReportCompare.group(emptyList(), 2, 3, "n", "—"), "بی سرخط")
    assertNull(ReportCompare.group(listOf(list), 2, 2, "n", "—"), "کلید و عدد یک ستون")
    assertNull(ReportCompare.group(listOf(list), 2, 5, "n", "—"), "ستونِ بیرون از سرخط")
    assertNull(ReportCompare.group(listOf(list), -1, 3, "n", "—"))
    assertNull(ReportCompare.group(listOf(list), 2, 3, " ", "—"), "سرخطِ شمارِ خالی")
    assertNull(ReportCompare.group(listOf(list), 2, 3, "n", ""), "نامِ گروهِ بی‌دسته خالی")
    assertNull(ReportCompare.group(listOf(list, listOf("x", "y", "z", "متن", "")), 2, 3, "n", "—"), "خانهٔ نا‌عددی")
  }

  @Test
  fun groupedListsGetThePreviousPeriodAndChangePerGroup() {
    val cur = listOf(list, listOf("2026-09-01", "برق", "قبض", "1200", ""), listOf("2026-09-02", "کرایه", "کرایه", "5000", ""))
    val prev = listOf(
      list,
      listOf("2026-08-01", "برق", "قبض", "900", ""),
      listOf("2026-08-03", "آب", "قبض", "100", ""),
      listOf("2026-08-09", "تعمیر", "تعمیر", "2500.5", ""),
    )
    val out = ReportCompare.grouped(cur, prev, 2, 3, "تعداد", "—", "دورهٔ قبل", "تغییر")!!
    assertEquals(listOf("دسته", "تعداد", "مبلغ", "دورهٔ قبل", "تغییر"), out[0])
    assertEquals(listOf("کرایه", "1", "5000", "", "5000"), out[1])
    assertEquals(listOf("قبض", "1", "1200", "1000", "200"), out[2])
    //  گروهی که این دوره هیچ سندی نداشت گم نمی‌شود
    assertEquals(listOf("تعمیر", "", "", "2500.5", "-2500.5"), out[3])
    assertEquals(4, out.size)
    //  دورهٔ قبلِ خالی: همهٔ گروه‌ها نو
    val fresh = ReportCompare.grouped(cur, listOf(list), 2, 3, "n", "—", "p", "c")!!
    assertEquals(listOf("قبض", "1", "1200", "", "1200"), fresh[2])
  }

  @Test
  fun groupedNeedsTheSameSheetInBothPeriods() {
    val cur = listOf(list, listOf("2026-09-01", "برق", "قبض", "1200", ""))
    assertNull(ReportCompare.grouped(cur, listOf(listOf("x", "y", "z", "w", "v")), 2, 3, "n", "—", "p", "c"), "سرخطِ دیگر")
    assertNull(ReportCompare.grouped(emptyList(), cur, 2, 3, "n", "—", "p", "c"))
    assertNull(ReportCompare.grouped(cur, cur + listOf(listOf("x", "y", "z", "متن", "")), 2, 3, "n", "—", "p", "c"), "دورهٔ قبلِ نا‌عددی")
  }

  @Test
  fun bigAmountsWithCentsStayPlainDigits() {
    //  پیش‌تر `Double.toString` ⇒ «1.23456785E7»
    assertEquals("12345678.5", ReportCompare.format(12345678.5))
    assertEquals("-98765432.05", ReportCompare.format(-98765432.05))
    assertEquals("0.05", ReportCompare.format(0.05))
    val head = listOf("k", "v")
    assertEquals("123456789.75", ReportCompare.group(listOf(head, listOf("a", "123456789.5"), listOf("a", "0.25")), 0, 1, "n", "—")!![1][2])
  }
}
