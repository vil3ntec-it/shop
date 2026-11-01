package ir.vil3ntec.tohid.core.delivery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RPT-15 — هم‌خوانیِ ردیف‌ها با جمعِ کل و برچسب‌های هر پیک (همان عددهای `delivery-cash-report.test.js`) */
class CashReportTest {
  private val total = 1_240_000L
  private val one = CashReportRow(
    "c1", "پیکِ یک", deliveries = 2, expectedCashMinor = 2 * total, collectedCashMinor = 2 * total, unsettledMinor = total,
    settlements = 1, handoverDueMinor = total, receivedMinor = total, inHandMinor = total,
  )
  private val two = CashReportRow(
    "c2", "پیکِ دو", deliveries = 1, expectedCashMinor = total, collectedCashMinor = total, settlements = 1,
    handoverDueMinor = total, receivedMinor = total - 10_000, shortageMinor = 10_000,
  )
  private val totals = CashReportRow(
    deliveries = 3, expectedCashMinor = 3 * total, collectedCashMinor = 3 * total, unsettledMinor = total, settlements = 2,
    handoverDueMinor = 2 * total, receivedMinor = 2 * total - 10_000, shortageMinor = 10_000, inHandMinor = total,
  )

  @Test
  fun rowsAddUpToTheServerTotals() {
    assertTrue(CashReportMath.consistent(CashReport(0, 1, listOf(one, two), totals)))
    assertTrue(CashReportMath.consistent(CashReport(0, 1, emptyList(), CashReportRow())))
  }

  @Test
  fun anyColumnOffMakesTheReportInconsistent() {
    assertFalse(CashReportMath.consistent(CashReport(0, 1, listOf(one, two), totals.copy(inHandMinor = 0))))
    assertFalse(CashReportMath.consistent(CashReport(0, 1, listOf(one, two), totals.copy(settlements = 1))))
    assertFalse(CashReportMath.consistent(CashReport(0, 1, listOf(one), totals)))
    assertFalse(CashReportMath.consistent(CashReport(0, 1, emptyList(), CashReportRow(excessMinor = 1))))
  }

  @Test
  fun settledPartAndCustomerDiff() {
    assertEquals(total, CashReportMath.settledOfCollected(one))
    assertEquals(total, CashReportMath.settledOfCollected(two))
    assertEquals(0, CashReportMath.customerDiff(one))
    assertEquals(-40_000, CashReportMath.customerDiff(two.copy(collectedCashMinor = total - 40_000)))
  }

  @Test
  fun attentionForShortageUnsettledOrCustomerDiff() {
    assertTrue(CashReportMath.attention(one))
    assertTrue(CashReportMath.attention(two))
    assertFalse(CashReportMath.attention(two.copy(receivedMinor = total, shortageMinor = 0)))
    assertTrue(CashReportMath.attention(two.copy(receivedMinor = total, shortageMinor = 0, collectedCashMinor = total - 1)))
    assertTrue(CashReportMath.attention(CashReportRow(excessMinor = 5)))
  }
}
