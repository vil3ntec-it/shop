package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.StockLevelRules.Edit
import ir.vil3ntec.tohid.core.stock.StockLevelRules.Patch
import ir.vil3ntec.tohid.core.stock.StockLevelRules.Problem
import ir.vil3ntec.tohid.core.stock.StockLevelRules.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-12 — حدِ هشدار، نقطهٔ سفارش و بیشینه، و ویرایشِ گروهی (`StockLevelRules`). */
class StockLevelsTest {
  private fun u(n: Long) = n * 1000 // واحد ⇒ هزارم

  @Test
  fun problems_orderMustBeMinReorderMax() {
    assertEquals(emptyList(), StockLevelRules.problems(StockLevels(u(5), u(10), u(40))))
    assertEquals(emptyList(), StockLevelRules.problems(StockLevels()))           // هیچ حدی: درست
    assertEquals(emptyList(), StockLevelRules.problems(StockLevels(u(5), u(5), null))) // نقطهٔ سفارش = کمینه: درست
    assertEquals(listOf(Problem.REORDER_BELOW_MIN), StockLevelRules.problems(StockLevels(u(10), u(5), null)))
    assertEquals(listOf(Problem.MAX_NOT_ABOVE_REORDER), StockLevelRules.problems(StockLevels(null, u(10), u(10))))
    assertEquals(listOf(Problem.MAX_BELOW_MIN), StockLevelRules.problems(StockLevels(u(10), null, u(8))))
    assertEquals(listOf(Problem.MAX_ZERO), StockLevelRules.problems(StockLevels(null, null, 0)))
    assertEquals(listOf(Problem.NEGATIVE), StockLevelRules.problems(StockLevels(-1, u(3), u(9))))
    assertEquals(listOf(Problem.TOO_LARGE), StockLevelRules.problems(StockLevels(null, null, StockLevelRules.MAX_MILLI + 1)))
  }

  @Test
  fun state_matchesTodaysLowRuleAndAddsReorderAndOverMax() {
    val l = StockLevels(minMilli = u(5), reorderMilli = u(12), maxMilli = u(40))
    assertEquals(State.OUT, StockLevelRules.state(0, l))
    assertEquals(State.OUT, StockLevelRules.state(-u(2), l))
    assertEquals(State.BELOW_MIN, StockLevelRules.state(u(5), l))         // ≤ کمینه، مثلِ «موجودی کم» ِ امروز
    assertEquals(State.REORDER, StockLevelRules.state(u(6), l))
    assertEquals(State.REORDER, StockLevelRules.state(u(12), l))          // روی خودِ نقطهٔ سفارش
    assertEquals(State.OK, StockLevelRules.state(u(13), l))
    assertEquals(State.OK, StockLevelRules.state(u(40), l))
    assertEquals(State.OVER_MAX, StockLevelRules.state(u(40) + 1, l))
    //  کمینهٔ صفر یعنی «هشدار ندارد» (همان `minStock = 0` ِ امروز)
    assertEquals(State.OK, StockLevelRules.state(u(1), StockLevels(minMilli = 0)))
    assertEquals(State.OK, StockLevelRules.state(u(1), StockLevels()))
  }

  @Test
  fun suggestOrder_fillsUpToMaxOnlyWhenNeededAndOnlyIfMaxKnown() {
    val l = StockLevels(minMilli = u(5), reorderMilli = u(12), maxMilli = u(40))
    assertEquals(u(30), StockLevelRules.suggestOrderMilli(u(10), l))      // REORDER ⇒ ۴۰ − ۱۰
    assertEquals(u(37), StockLevelRules.suggestOrderMilli(u(3), l))       // BELOW_MIN
    assertEquals(u(40), StockLevelRules.suggestOrderMilli(0, l))          // OUT
    assertEquals(u(40), StockLevelRules.suggestOrderMilli(-u(4), l))      // منفی ⇒ صفر، نه ۴۴
    assertNull(StockLevelRules.suggestOrderMilli(u(20), l))               // OK ⇒ بی پیشنهاد
    assertNull(StockLevelRules.suggestOrderMilli(u(50), l))               // OVER_MAX
    assertNull(StockLevelRules.suggestOrderMilli(u(3), StockLevels(minMilli = u(5), reorderMilli = u(12))))  // بی بیشینه
  }

  @Test
  fun plan_appliesValidRowsReportsInvalidFirstAndKeepsUntouchedFields() {
    val rows = listOf(
      "a" to StockLevels(minMilli = u(2)),
      "b" to StockLevels(minMilli = u(30)),                    // نقطهٔ سفارشِ ۱۰ زیرِ کمینهٔ ۳۰ ⇒ رد
      "c" to StockLevels(minMilli = u(2), reorderMilli = u(10), maxMilli = u(50)),
    )
    val plan = StockLevelRules.plan(rows, Patch(reorder = Edit.To(u(10)), max = Edit.To(u(50))))
    assertEquals(listOf("b", "a", "c"), plan.map { it.id })   // ناموفق اول
    assertEquals(listOf(Problem.REORDER_BELOW_MIN), plan[0].problems)
    assertFalse(plan[0].changes)
    assertEquals(StockLevels(u(2), u(10), u(50)), plan[1].after) // کمینه دست نخورد
    assertTrue(plan[1].changes)
    assertTrue(plan[2].ok)
    assertFalse(plan[2].changes)                               // از قبل همین بود
  }

  @Test
  fun plan_clearAndKeep() {
    val before = StockLevels(minMilli = u(3), reorderMilli = u(8), maxMilli = u(20))
    val p = StockLevelRules.plan(listOf("x" to before), Patch(reorder = Edit.Clear, max = Edit.Clear)).single()
    assertEquals(StockLevels(minMilli = u(3)), p.after)
    assertTrue(p.changes)
    assertTrue(Patch().isNoop)
    assertFalse(Patch(min = Edit.Clear).isNoop)
  }
}
