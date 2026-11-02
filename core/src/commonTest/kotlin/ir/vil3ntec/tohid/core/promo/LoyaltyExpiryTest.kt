package ir.vil3ntec.tohid.core.promo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PRM-13 — انقضای امتیاز (FIFO از روی دفتر) و اصلاحِ دستی. همین بردارها در `server/test/loyalty-balance.test.js`
 *  روی پیادهٔ JS ِ سرور (`lib/loyalty-balance.js`) با همان عددها سنجیده می‌شوند.
 */
class LoyaltyExpiryTest {

  private val day = 86_400_000L
  private val year = 365 * day
  private val rule = Loyalty.Rule(expiryMonths = 12)
  private val never = Loyalty.Rule()

  private fun ev(id: String, kind: String, points: Long, at: Long, sale: String = "") = Loyalty.Event(id, kind, "c", points, sale, at)

  @Test
  fun twelveMonthsIsExactlyTheTierYear() {
    assertEquals(year, 12 * Loyalty.MONTH_MS)
    assertTrue(Loyalty.Rule(expiryMonths = 120).valid)
    assertFalse(Loyalty.Rule(expiryMonths = 121).valid)
    assertFalse(Loyalty.Rule(expiryMonths = -1).valid)
  }

  @Test
  fun oldestLotIsUsedFirstAndBurnsExactlyAtItsBoundary() {
    val e = listOf(
      ev("e1", Loyalty.EARN, 100, 0, "s1"),
      ev("e2", Loyalty.EARN, 50, 100 * day, "s2"),
      ev("e3", Loyalty.SPEND, 30, 200 * day, "s3"),
    )
    //  یک میلی‌ثانیه پیش از مرز: دستهٔ نخست (۷۰ ِ مانده) هنوز زنده و «به‌زودی می‌سوزد»
    val before = Loyalty.state(e, "c", rule, year - 1)
    assertEquals(120, before.balance)
    assertEquals(0, before.expired)
    assertEquals(70, before.soon)
    assertEquals(year, before.soonAt)
    //  درست در مرز: سوخت
    val at = Loyalty.state(e, "c", rule, year)
    assertEquals(50, at.balance)
    assertEquals(70, at.expired)
    assertEquals(0, at.soon)
    assertNull(at.soonAt)
    assertEquals(50, Loyalty.balanceAt(e, "c", rule, year))
    //  بی انقضا همان جمعِ ساده
    assertEquals(120, Loyalty.balanceAt(e, "c", never, 10 * year))
    assertEquals(Loyalty.balance(e, "c"), Loyalty.state(e, "c", never, 10 * year).balance)
  }

  @Test
  fun earnBackTakesFromItsOwnSaleLotFirst() {
    val e = listOf(
      ev("e1", Loyalty.EARN, 100, 0, "s1"),
      ev("e2", Loyalty.EARN, 50, 100 * day, "s2"),
      ev("e3", Loyalty.EARN_BACK, 20, 150 * day, "s2"),
    )
    val s = Loyalty.state(e, "c", rule, year)
    assertEquals(30, s.balance)
    assertEquals(100, s.expired)
  }

  @Test
  fun deficitFromTwoDevicesIsPaidBeforeANewLotStarts() {
    val e = listOf(ev("e1", Loyalty.SPEND, 10, 0, "s1"), ev("e2", Loyalty.EARN, 25, 10 * day, "s2"))
    assertEquals(15, Loyalty.state(e, "c", rule, 10 * day + year - 1).balance)
    val s = Loyalty.state(e, "c", rule, 10 * day + year)
    assertEquals(0, s.balance)
    assertEquals(15, s.expired)
    //  کسریِ بی دسته منفی می‌ماند
    assertEquals(-10, Loyalty.state(listOf(ev("x", Loyalty.SPEND, 10, 0, "s1")), "c", rule, 5).balance)
  }

  @Test
  fun refundedSpendCountsFromTheSpendNotTheRefund() {
    val e = listOf(
      ev("e1", Loyalty.EARN, 100, 0, "s1"),
      ev("e2", Loyalty.SPEND, 40, 300 * day, "s3"),
      ev("e3", Loyalty.SPEND_BACK, 40, 400 * day, "s3"),
    )
    val a = Loyalty.state(e, "c", rule, year + 1)
    assertEquals(40, a.balance)
    assertEquals(60, a.expired)
    val b = Loyalty.state(e, "c", rule, 300 * day + year)
    assertEquals(0, b.balance)
    assertEquals(100, b.expired)
  }

  @Test
  fun manualAdjustIsSignedAndFifo() {
    val e = listOf(
      ev("e1", Loyalty.EARN, 10, 0, "s1"),
      ev("a1", Loyalty.ADJUST, 5, day),
      ev("a2", Loyalty.ADJUST, -12, 2 * day),
    )
    assertEquals(3, Loyalty.balance(e, "c"))
    assertEquals(3, Loyalty.state(e, "c", rule, year).balance)
    val s = Loyalty.state(e, "c", rule, day + year)
    assertEquals(0, s.balance)
    assertEquals(3, s.expired)
  }

  @Test
  fun lotsNeverBurnAfterNowEvenWithAFutureRow() {
    //  ردیفِ «آینده» از ساعتِ کجِ دستگاهِ دیگر: دستهٔ نخست تا خودِ now نمی‌سوزد
    val e = listOf(ev("e1", Loyalty.EARN, 100, 0, "s1"), ev("e2", Loyalty.EARN, 1, 2 * year, "s2"))
    assertEquals(101, Loyalty.state(e, "c", rule, year - 1).balance)
  }

  @Test
  fun adjustRejectsZeroOverflowAndGoingNegative() {
    assertEquals(Loyalty.Adjust.Bad(Loyalty.Code.BAD_POINTS), Loyalty.adjust(10, 0))
    assertEquals(Loyalty.Adjust.Bad(Loyalty.Code.BAD_POINTS), Loyalty.adjust(10, Loyalty.MAX_POINTS + 1))
    assertEquals(Loyalty.Adjust.Bad(Loyalty.Code.OVER_BALANCE), Loyalty.adjust(10, -11))
    assertEquals(Loyalty.Adjust.Bad(Loyalty.Code.OVER_BALANCE), Loyalty.adjust(-5, -1))
    assertEquals(Loyalty.Adjust.Ok(-10), Loyalty.adjust(10, -10))
    assertEquals(Loyalty.Adjust.Ok(7), Loyalty.adjust(-5, 7))
  }
}
