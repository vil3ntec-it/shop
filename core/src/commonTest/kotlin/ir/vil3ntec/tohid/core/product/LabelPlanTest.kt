package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.product.LabelPlan.Item
import ir.vil3ntec.tohid.core.product.LabelPlan.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PRD-23: چند کالا، چند نسخه، سقفِ هر کالا و هر کار، و برگه‌های A4 ِ ۳×۸ برای برچسب‌های گوناگون. */
class LabelPlanTest {

  @Test fun copiesFollowTheSelectionOrderAndAreCapped() {
    val p = LabelPlan.plan(
      listOf(Item("a", 2, true), Item("b", 0, true), Item("c", 150, true), Item("d", -3, true)),
      Kind.BARCODE,
    )
    assertEquals(listOf("a", "a") + List(LabelPlan.MAX_COPIES) { "c" }, p.labels)
    assertEquals(0, p.dropped)
    assertTrue(p.missingCode.isEmpty())
  }

  @Test fun barcodeLabelsSkipProductsWithoutACodeButShelfLabelsDoNot() {
    val items = listOf(Item("a", 1, false), Item("b", 2, true))
    val barcode = LabelPlan.plan(items, Kind.BARCODE)
    assertEquals(listOf("b", "b"), barcode.labels)
    assertEquals(listOf("a"), barcode.missingCode)
    val shelf = LabelPlan.plan(items, Kind.SHELF)
    assertEquals(listOf("a", "b", "b"), shelf.labels)
    assertTrue(shelf.missingCode.isEmpty())
  }

  @Test fun weighedProductsGetOneLabel() {
    assertEquals(listOf("rice", "soap", "soap"), LabelPlan.plan(listOf(Item("rice", 7, true, weighed = true), Item("soap", 2, true)), Kind.BARCODE).labels)
  }

  @Test fun theJobIsCappedAndTheOverflowIsReported() {
    val p = LabelPlan.plan(listOf(Item("a", 99, true), Item("b", 99, true), Item("c", 99, true)), Kind.SHELF)
    assertEquals(LabelPlan.MAX_TOTAL, p.labels.size)
    assertEquals(3 * 99 - LabelPlan.MAX_TOTAL, p.dropped)
    assertEquals(42, p.labels.count { it == "c" })
    assertEquals(3, LabelPlan.plan(listOf(Item("a", 5, true)), Kind.SHELF, maxTotal = 2).dropped)
  }

  @Test fun runsGroupConsecutiveCopies() {
    assertEquals(listOf("a" to 2, "b" to 1, "a" to 1), LabelPlan.runs(listOf("a", "a", "b", "a")))
    assertEquals(emptyList(), LabelPlan.runs(emptyList()))
  }

  @Test fun a4SheetsHold24DifferentLabelsPerPageInTheSameCells() {
    fun label(dots: Int): MonoImage = MonoImage(Labels.SIZE_40x30.widthDots, Labels.SIZE_40x30.heightDots).also { img ->
      for (i in 0 until dots) img.set(i, 0, true)
    }
    //  ۲۵ برچسبِ گوناگون ⇒ دو برگه، دومی با یک برچسب
    val labels = (1..25).map { label(it) }
    val pages = Labels.A4.sheets(labels)
    assertEquals(2, pages.size)
    assertEquals((1..24).sum(), pages[0].blackCount())
    assertEquals(25, pages[1].blackCount())
    //  همان خانه‌بندیِ `sheet`: یک برچسب تنها همان‌جا می‌نشیند که `sheet(…, 1)` می‌گذارد
    val one = label(10)
    assertTrue(Labels.A4.sheets(listOf(one)).single().bits.contentEquals(Labels.A4.sheet(one, 1).bits))
    assertTrue(Labels.A4.sheets(List(24) { one }).single().bits.contentEquals(Labels.A4.sheet(one, 24).bits))
    assertEquals(emptyList(), Labels.A4.sheets(emptyList()))
  }
}
