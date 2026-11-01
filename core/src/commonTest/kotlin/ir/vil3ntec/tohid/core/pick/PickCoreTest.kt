package ir.vil3ntec.tohid.core.pick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PCK: قاعده‌هایی از `PickCore` که بردار نمی‌پوشاند (مرز، سرریز، ناوردایی) */
class PickCoreTest {

  @Test
  fun roundDivMatchesJsMathRoundHalfUp() {
    //  Math.round ِ JS: نیمه به سمتِ +∞ (نه نیمه به زوج)
    assertEquals(3, PickCore.roundDiv(5, 2))
    assertEquals(2, PickCore.roundDiv(3, 2))
    assertEquals(-2, PickCore.roundDiv(-5, 2))
    assertEquals(6667, PickCore.roundDiv(20_000, 3))
    assertEquals(3333, PickCore.roundDiv(10_000, 3))
  }

  @Test
  fun lineTotalRefusesOverflow() {
    assertFailsWith<IllegalArgumentException> { PickCore.lineTotalMinor(10_000_000, Long.MAX_VALUE / 1000) }
    assertEquals(0, PickCore.lineTotalMinor(0, 123))
  }

  @Test
  fun naturalOrderIsNumericAndTotal() {
    assertTrue(PickCore.compareNatural("9", "10") < 0)
    assertTrue(PickCore.compareNatural("A2", "A10") < 0)
    assertEquals(0, PickCore.compareNatural("A02", "A2"))
    assertTrue(PickCore.compareNatural("A", "a") < 0)
    assertTrue(PickCore.compareNatural("A", "A1") < 0)
    for ((a, b) in listOf("1" to "2", "x9" to "x10", "۲" to "10")) {
      assertEquals(-PickCore.compareNatural(a, b), PickCore.compareNatural(b, a), "$a $b")
    }
  }

  @Test
  fun locPartIsBoundedAndLatin() {
    assertEquals("12 B", PickCore.locPart("  ۱۲  B  "))
    assertEquals(20, PickCore.locPart("x".repeat(50)).length)
  }

  @Test
  fun weighBoundsAreInclusive() {
    assertEquals(1100, PickCore.maxWeighMilli(1000))
    assertEquals(900, PickCore.minWeighMilli(1000))
    assertEquals(PickCore.Weigh.OK, PickCore.weighVerdict(1000, 1100))
    assertEquals(PickCore.Weigh.OVER, PickCore.weighVerdict(1000, 1101))
    //  حدِ بیش از ۵۰٪ پذیرفته نمی‌شود؛ منفی صفر است
    assertEquals(1500, PickCore.maxWeighMilli(1000, 99_999))
    assertEquals(PickCore.Weigh.OVER, PickCore.weighVerdict(1000, 1001, -5))
  }

  @Test
  fun settleNeverChargesMoreThanPickedAndNeverNegative() {
    val lines = listOf(
      PickCore.SettleLine(1, "picked", 1000, 1000, 50_000, 50_000),
      PickCore.SettleLine(2, "not_found", 2000, 0, 10_000, 20_000),
    )
    val s = PickCore.settle(lines, discountMinor = 999_999, deliveryFeeMinor = -5)
    assertEquals(50_000, s.subtotalMinor)
    assertEquals(50_000, s.discountMinor, "تخفیف از جمعِ تازه بیشتر نمی‌شود")
    assertEquals(0, s.deliveryFeeMinor)
    assertEquals(0, s.totalMinor)
    assertEquals(20_000, s.deductedMinor)
    assertEquals(listOf(2), s.removedLines)
    assertFalse(s.changedLines.contains(1))
  }

  @Test
  fun pendingLineCountsAndIsNotSold() {
    val s = PickCore.settle(listOf(PickCore.SettleLine(1, "pending", 1000, 600, 10_000, 10_000)))
    assertEquals(1, s.pending)
    assertEquals(0, s.lines.single().qtyMilli, "قلمِ باز فروخته نمی‌شود حتی با برداشتِ بخشی")
  }
}
