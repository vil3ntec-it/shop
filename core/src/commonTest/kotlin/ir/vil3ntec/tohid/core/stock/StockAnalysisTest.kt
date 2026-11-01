package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.StockAnalysis.Abc
import ir.vil3ntec.tohid.core.stock.StockAnalysis.AbcInput
import ir.vil3ntec.tohid.core.stock.StockAnalysis.ValueLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-15 (ABC) و INV-16 (ارزش و سودِ بالقوه) — منطقِ خالصِ `StockAnalysis`. */
class StockAnalysisTest {

  // ───────────────────────────── ABC ─────────────────────────────

  @Test
  fun abc_paretoCutsAt80And95() {
    val r = StockAnalysis.abc(listOf(AbcInput("c", 15), AbcInput("a", 50), AbcInput("d", 5), AbcInput("b", 30)))
    assertEquals(listOf("a", "b", "c", "d"), r.rows.map { it.id })
    assertEquals(listOf(Abc.A, Abc.A, Abc.B, Abc.C), r.rows.map { it.cls })
    assertEquals(listOf(5000L, 3000L, 1500L, 500L), r.rows.map { it.shareBp })
    assertEquals(listOf(5000L, 8000L, 9500L, 10000L), r.rows.map { it.cumulativeBp })
    assertEquals(100L, r.totalMinor)
    assertEquals(mapOf(Abc.A to 2, Abc.B to 1, Abc.C to 1), r.count)
    assertEquals(mapOf(Abc.A to 80L, Abc.B to 15L, Abc.C to 5L), r.valueMinor)
    assertEquals(mapOf(Abc.A to 8000L, Abc.B to 1500L, Abc.C to 500L), r.shareBp)
  }

  @Test
  fun abc_itemThatCrossesTheBoundaryStaysInUpperClass() {
    //  پیش از «b» ۷۰٪ است (زیرِ ۸۰٪) ⇒ b هم A، هرچند پس از آن ۹۰٪ می‌شود
    val r = StockAnalysis.abc(listOf(AbcInput("a", 70), AbcInput("b", 20), AbcInput("c", 10)))
    assertEquals(listOf(Abc.A, Abc.A, Abc.B), r.rows.map { it.cls })
  }

  @Test
  fun abc_singleBigSellerIsAOthersFollowCutsNotCount() {
    val r = StockAnalysis.abc(listOf(AbcInput("big", 990), AbcInput("x", 5), AbcInput("y", 5)))
    assertEquals(listOf(Abc.A, Abc.C, Abc.C), r.rows.map { it.cls })
    assertEquals(9900L, r.rows[0].shareBp)
  }

  @Test
  fun abc_zeroAndNegativeAreAlwaysC_duplicatesMerge_tiesByIdForStableOrder() {
    val r = StockAnalysis.abc(
      listOf(AbcInput("z", 40), AbcInput("dead", 0), AbcInput("ret", -30), AbcInput("m", 40), AbcInput("m", 20)),
    )
    assertEquals(listOf("m", "z", "dead", "ret"), r.rows.map { it.id })
    assertEquals(60L, r.rows[0].valueMinor)
    assertEquals(listOf(Abc.A, Abc.A, Abc.C, Abc.C), r.rows.map { it.cls })
    assertEquals(0L, r.rows.first { it.id == "ret" }.valueMinor)
    assertEquals(0L, r.rows.first { it.id == "dead" }.shareBp)
    //  برابر ⇒ الفبایی، هر بار همان ترتیب
    val tie = StockAnalysis.abc(listOf(AbcInput("q", 10), AbcInput("p", 10)))
    assertEquals(listOf("p", "q"), tie.rows.map { it.id })
  }

  @Test
  fun abc_noSalesAtAll_everythingIsC() {
    val r = StockAnalysis.abc(listOf(AbcInput("a", 0), AbcInput("b", 0)))
    assertTrue(r.rows.all { it.cls == Abc.C && it.shareBp == 0L && it.cumulativeBp == 0L })
    assertEquals(0L, r.totalMinor)
    assertTrue(StockAnalysis.abc(emptyList()).rows.isEmpty())
  }

  @Test
  fun abc_customCutsAndInvalidCuts() {
    val r = StockAnalysis.abc(listOf(AbcInput("a", 50), AbcInput("b", 30), AbcInput("c", 20)), aCutBp = 5_000, bCutBp = 8_000)
    assertEquals(listOf(Abc.A, Abc.B, Abc.C), r.rows.map { it.cls })
    assertFailsWith<IllegalArgumentException> { StockAnalysis.abc(emptyList(), aCutBp = 9_000, bCutBp = 8_000) }
    assertFailsWith<IllegalArgumentException> { StockAnalysis.abc(emptyList(), aCutBp = 0) }
    assertFailsWith<IllegalArgumentException> { StockAnalysis.abc(emptyList(), bCutBp = 10_001) }
  }

  @Test
  fun abc_hugeValuesDoNotOverflow() {
    val big = StockAnalysis.MAX_EXACT
    val r = StockAnalysis.abc(listOf(AbcInput("a", big), AbcInput("b", big / 4)))
    assertEquals(StockAnalysis.MAX_EXACT, r.totalMinor) // جمعِ سقف‌دار، نه دور زدن
    assertEquals(Abc.A, r.rows[0].cls)
    assertTrue(r.rows.all { it.shareBp in 0..10_000 })
  }

  // ─────────────────────── ارزش و سودِ بالقوه ───────────────────────

  @Test
  fun valuation_costRetailAndPotentialProfit() {
    val v = StockAnalysis.valuation(
      listOf(
        ValueLine("rice", onHandMilli = 2_500, unitCostMinor = 10_000, unitPriceMinor = 15_000), // ۲٫۵ × ۱۰۰ و ۱۵۰
        ValueLine("oil", onHandMilli = 4_000, unitCostMinor = 5_000, unitPriceMinor = 6_000),
      ),
    )
    assertEquals(25_000L + 20_000L, v.costMinor)
    assertEquals(37_500L + 24_000L, v.retailMinor)
    assertEquals(12_500L + 4_000L, v.potentialProfitMinor)
    assertEquals(2_683L, v.marginBp) // ۱۶۵۰۰ ÷ ۶۱۵۰۰ = ۲۶٫۸۲۹٪
    assertEquals(2, v.inStock)
    assertTrue(v.complete)
    //  ارزشِ هر ردیف برای جدول؛ جمعش همان جمعِ بالا
    assertEquals(listOf(StockAnalysis.LineValue("rice", 25_000, 37_500), StockAnalysis.LineValue("oil", 20_000, 24_000)), v.lines)
  }

  @Test
  fun valuation_missingCostIsReportedAndKeptOutOfProfit() {
    val v = StockAnalysis.valuation(
      listOf(
        ValueLine("known", 1_000, 10_000, 12_000),
        ValueLine("noCost", 3_000, 0, 5_000), //  بی بها: سود نمی‌سازد، ولی ارزشِ فروش دارد
        ValueLine("noPrice", 2_000, 4_000, 0),
      ),
    )
    assertEquals(10_000L + 8_000L, v.costMinor)
    assertEquals(12_000L + 15_000L, v.retailMinor)
    assertEquals(2_000L, v.potentialProfitMinor) // فقط «known»
    assertEquals(1_667L, v.marginBp)
    assertEquals(listOf("noCost"), v.missingCost)
    assertEquals(listOf("noPrice"), v.missingPrice)
    assertFalse(v.complete)
  }

  @Test
  fun valuation_negativeAndZeroStockStayOutOfEverySum() {
    val v = StockAnalysis.valuation(
      listOf(
        ValueLine("neg", -2_000, 10_000, 20_000),
        ValueLine("zero", 0, 10_000, 20_000),
        ValueLine("ok", 1_000, 100, 300),
      ),
    )
    assertEquals(100L, v.costMinor)
    assertEquals(300L, v.retailMinor)
    assertEquals(200L, v.potentialProfitMinor)
    assertEquals(listOf("neg"), v.negative)
    assertEquals(1, v.inStock)
    assertEquals(listOf("ok"), v.lines.map { it.id })
  }

  @Test
  fun valuation_priceBelowCostGivesNegativeProfitAndIsFlagged() {
    val v = StockAnalysis.valuation(listOf(ValueLine("loss", 1_000, 10_000, 8_000)))
    assertEquals(-2_000L, v.potentialProfitMinor)
    assertEquals(-2_500L, v.marginBp)
    assertEquals(listOf("loss"), v.belowCost)
  }

  @Test
  fun valuation_lineRoundingHalfUpPerLine() {
    //  ۰٫۵ پول ⇒ ۱؛ ۰٫۰۰۱ پول ⇒ ۰ — هر ردیف جدا گرد می‌شود
    val v = StockAnalysis.valuation(listOf(ValueLine("half", 500, 1, 3), ValueLine("tiny", 1, 1, 1)))
    assertEquals(1L, v.costMinor)
    assertEquals(2L, v.retailMinor) // ۱٫۵ ⇒ ۲ ؛ ۰٫۰۰۱ ⇒ ۰
    assertEquals(1L, v.potentialProfitMinor)
  }

  @Test
  fun valuation_outOfExactRangeIsNotSilentlyWrong() {
    val v = StockAnalysis.valuation(
      listOf(ValueLine("huge", StockAnalysis.MAX_EXACT, 2, 3), ValueLine("ok", 1_000, 10, 20)),
    )
    assertEquals(listOf("huge"), v.outOfRange)
    assertEquals(10L, v.costMinor)
    assertEquals(20L, v.retailMinor)
    assertFalse(v.complete)
  }

  @Test
  fun valuation_emptyHasNoMargin() {
    val v = StockAnalysis.valuation(emptyList())
    assertEquals(0L, v.costMinor)
    assertNull(v.marginBp)
    assertTrue(v.complete)
  }
}
