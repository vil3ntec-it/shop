package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.pay.DayClose.Event
import ir.vil3ntec.tohid.core.pay.DayClose.Shift
import ir.vil3ntec.tohid.core.pay.DayClose.Status
import ir.vil3ntec.tohid.core.pay.DayClose.Sums
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RPT-04 — پایانِ روزِ چندشیفتی: جمعِ X/Z ها به تفکیکِ روش، و تطبیق با دفترِ همان روز.
 *  روزِ کاری: لحظهٔ کمتر از ۱۰۰۰ مالِ «d1»، تا ۲۰۰۰ «d2»، بعد «d3».
 */
class DayCloseTest {
  private val dayOf: (Long) -> String = { ms -> if (ms < 1000) "d1" else if (ms < 2000) "d2" else "d3" }

  private fun sale(at: Long, vararg rows: Pair<String, Long>) = Event("sale", at, rows.toList())
  private fun refund(at: Long, vararg rows: Pair<String, Long>) = Event("refund", at, rows.toList())
  private fun receipt(at: Long, amount: Long) = Event("receipt", at, listOf("cash" to amount))

  /** صبح: نقد ۵۰۰ + کارت ۳۰۰؛ رسید ۱۰۰؛ برگشتِ نقد ۵۰. بسته، بی اختلاف. */
  private val morning = Shift(
    id = "m", openedAt = 1100, openedBy = "علی", closedAt = 1400, float = 1000,
    methods = linkedMapOf("cash" to Sums(500, 50, 100), "card" to Sums(300)),
    cashIn = 20, cashOut = 70,
    expected = mapOf("cash" to 1500, "card" to 300),
    counted = mapOf("cash" to 1500, "card" to 300),
    variance = mapOf("cash" to 0, "card" to 0),
  )

  /** عصر: نقد ۴۰۰ + به حساب ۲۰۰؛ کسریِ نقدِ ۱۰، کارت شمرده نشد. */
  private val evening = Shift(
    id = "e", openedAt = 1500, openedBy = "رضا", closedAt = 1900, float = 1500,
    methods = linkedMapOf("account" to Sums(200), "cash" to Sums(400)),
    expected = mapOf("cash" to 1900),
    counted = mapOf("cash" to 1890),
    variance = mapOf("cash" to -10, "card" to null),
    needsReview = true,
  )

  private val ledger = listOf(
    sale(1150, "cash" to 300, "card" to 300), sale(1200, "cash" to 200), receipt(1250, 100), refund(1300, "cash" to 50),
    sale(1600, "cash" to 400, "account" to 200),
  )

  @Test
  fun twoShiftsAddUpPerMethodAndMatchTheLedger() {
    val r = DayClose.of("d2", listOf(evening, morning), ledger, dayOf, now = 1950)
    assertEquals(listOf("m", "e"), r.shifts.map { it.id })
    assertEquals(listOf("cash", "card", "account"), r.methods.map { it.method })
    val cash = r.methods[0]
    assertEquals(Sums(900, 50, 100), cash.shifts)
    assertEquals(950, cash.shifts.net)
    assertEquals(3400L, cash.expected)
    assertEquals(3390L, cash.counted)
    assertEquals(-10L, cash.variance)
    assertEquals(cash.shifts, cash.ledger)
    assertEquals(0, cash.outside)
    val card = r.methods[1]
    assertEquals(300L, card.expected)
    assertEquals(300L, card.counted)
    assertEquals(0L, card.variance)
    val account = r.methods[2]
    assertEquals(Sums(200), account.shifts)
    assertNull(account.expected)
    assertNull(account.counted)
    assertNull(account.variance)
    assertEquals(Sums(1400, 50, 100), r.total.shifts)
    assertEquals(r.total.shifts, r.total.ledger)
    assertEquals(3700L, r.total.expected)
    assertEquals(-10L, r.total.variance)
    assertTrue(r.reconciled)
    assertEquals(5, r.ledgerEvents)
    assertEquals(0, r.openShifts)
    assertEquals(Status.REVIEW, r.status)
  }

  @Test
  fun shiftLinesCarryCashFiguresAndStatus() {
    val r = DayClose.of("d2", listOf(morning, evening), ledger, dayOf, now = 1950)
    val m = r.shifts[0]
    assertEquals("closed", m.status)
    assertEquals(850, m.net)
    assertEquals(1500L, m.expectedCash)
    assertEquals(1500L, m.countedCash)
    assertEquals(0L, m.varianceCash)
    assertEquals(20, m.cashIn)
    assertEquals(70, m.cashOut)
    assertEquals(-10L, r.shifts[1].varianceCash)
    assertTrue(r.shifts[1].needsReview)
  }

  @Test
  fun cleanDayIsClosed() {
    val r = DayClose.of("d2", listOf(morning), ledger.take(4), dayOf, now = 1950)
    assertTrue(r.reconciled)
    assertEquals(Status.CLOSED, r.status)
  }

  @Test
  fun openShiftKeepsTheDayOpenWithXFigures() {
    val x = evening.copy(closedAt = null, counted = null, variance = null, needsReview = false)
    val r = DayClose.of("d2", listOf(morning, x), ledger, dayOf, now = 1950)
    assertEquals(1, r.openShifts)
    assertEquals("open", r.shifts[1].status)
    assertNull(r.shifts[1].countedCash)
    assertEquals(1500L, r.methods[0].counted)
    assertEquals(Status.OPEN, r.status)
  }

  @Test
  fun saleOutsideEveryShiftIsReportedNotHidden() {
    val r = DayClose.of("d2", listOf(morning, evening), ledger + sale(1450, "cash" to 75), dayOf, now = 1950)
    assertFalse(r.reconciled)
    assertEquals(75, r.methods[0].outside)
    assertEquals(Sums(975, 50, 100), r.methods[0].ledger)
    assertEquals(Sums(900, 50, 100), r.methods[0].shifts)
    assertEquals(75, r.total.outside)
    assertEquals(Status.MISMATCH, r.status)
  }

  @Test
  fun aDayWithSalesButNoShiftIsAMismatch() {
    val r = DayClose.of("d2", emptyList(), listOf(sale(1150, "card" to 40)), dayOf, now = 1950)
    assertEquals(Status.MISMATCH, r.status)
    assertEquals(listOf("cash", "card"), r.methods.map { it.method })
    assertEquals(40, r.methods[1].outside)
  }

  @Test
  fun overlappingShiftsAreFlaggedBeforeAnythingElse() {
    val twin = morning.copy(id = "m2", openedAt = 1200, closedAt = 1300)
    val r = DayClose.of("d2", listOf(morning, twin), ledger, dayOf, now = 1950)
    assertEquals(listOf("m" to "m2"), r.overlapping)
    assertEquals(Status.OVERLAP, r.status)
  }

  @Test
  fun shiftOpenedAtTheCloseMomentOfThePreviousOneOverlaps() {
    val next = evening.copy(openedAt = 1400)
    assertEquals(listOf("m" to "e"), DayClose.of("d2", listOf(morning, next), ledger, dayOf, now = 1950).overlapping)
    val after = evening.copy(openedAt = 1401)
    assertEquals(emptyList(), DayClose.of("d2", listOf(morning, after), ledger, dayOf, now = 1950).overlapping)
  }

  @Test
  fun openShiftOverlapsUntilNow() {
    val open = morning.copy(closedAt = null)
    val r = DayClose.of("d2", listOf(open, evening), ledger, dayOf, now = 1950)
    assertEquals(listOf("m" to "e"), r.overlapping)
    val early = DayClose.of("d2", listOf(open, evening), ledger, dayOf, now = 1450)
    assertEquals(emptyList(), early.overlapping)
  }

  @Test
  fun shiftBelongsToTheBusinessDayItOpenedOn() {
    //  باز در d1 و بسته در d2: مالِ d1؛ رویدادِ d2 اش در تطبیقِ d2 «بیرون از شیفت» است
    val night = morning.copy(id = "n", openedAt = 900, closedAt = 1150)
    val d2 = DayClose.of("d2", listOf(night), listOf(sale(1100, "cash" to 30)), dayOf, now = 1950)
    assertTrue(d2.shifts.isEmpty())
    assertEquals(30, d2.methods[0].outside)
    val d1 = DayClose.of("d1", listOf(night), listOf(sale(1100, "cash" to 30)), dayOf, now = 1950)
    assertEquals(listOf("n"), d1.shifts.map { it.id })
    assertEquals(0, d1.ledgerEvents)
  }

  @Test
  fun legacyCloseAndSkippedEventsNeedReview() {
    val legacy = morning.copy(legacy = true)
    val r = DayClose.of("d2", listOf(legacy), ledger.take(4), dayOf, now = 1950)
    assertEquals("legacy", r.shifts[0].status)
    assertEquals(Status.CLOSED, r.status)
    val skipped = morning.copy(skipped = 1)
    assertEquals(Status.REVIEW, DayClose.of("d2", listOf(skipped), ledger.take(4), dayOf, now = 1950).status)
  }

  @Test
  fun emptyDayIsEmptyWithOnlyTheCashLine() {
    val r = DayClose.of("d3", listOf(morning), ledger, dayOf, now = 2500)
    assertEquals(Status.EMPTY, r.status)
    assertEquals(listOf("cash"), r.methods.map { it.method })
    assertEquals(Sums(), r.total.shifts)
    assertNull(r.total.expected)
  }

  @Test
  fun unknownEventTypesAreIgnored() {
    val r = DayClose.of("d2", listOf(morning), ledger.take(4) + Event("cash_out", 1300, listOf("cash" to 70)), dayOf, now = 1950)
    assertTrue(r.reconciled)
    assertEquals(4, r.ledgerEvents)
  }

  @Test
  fun sumBeyondTheExactRangeFails() {
    val big = Shift(id = "b", openedAt = 1100, closedAt = 1200, methods = mapOf("cash" to Sums(9_007_199_254_740_991L)))
    assertFailsWith<ArithmeticException> {
      DayClose.of("d2", listOf(big, big.copy(id = "c", openedAt = 1300, closedAt = 1400)), emptyList(), dayOf, now = 1950)
    }
  }
}
