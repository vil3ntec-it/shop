package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.PriceCheck.Lot
import ir.vil3ntec.tohid.core.pos.PriceCheck.Purchase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  قیمت‌پرسی بی فروش — POS-15.
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class PriceCheckTest {

  private val today = "2026-10-31"

  @Test
  fun lastPurchaseIsLatestDateThenLatestEntry() {
    val a = Purchase("2026-10-01", 5, 9_000, 10_000, "الف")
    val b = Purchase("2026-10-20", 1, 9_500, 5_000, "ب")
    val c = Purchase("2026-10-20", 9, 9_700, 2_000, "ج")
    val bad = Purchase("", 99, 1, 1)
    assertEquals(c, PriceCheck.lastPurchase(listOf(a, b, c, bad)))
    assertEquals(a, PriceCheck.lastPurchase(listOf(a)))
    assertNull(PriceCheck.lastPurchase(listOf(bad)))
    assertNull(PriceCheck.lastPurchase(emptyList()))
  }

  @Test
  fun nearestUnexpiredLotWithStockAndExpiredSum() {
    val lots = listOf(
      Lot("2026-12-01", 3_000),
      Lot("2026-11-05", 2_000),
      Lot("2026-11-01", 0),          // تمام‌شده: به حساب نمی‌آید
      Lot("2026-10-30", 1_500),      // گذشته
      Lot("2026-10-31", 500),        // امروز: هنوز نگذشته، ۰ روز
      Lot("bad", 9_000),
    )
    val c = PriceCheck.card(10_000, 8_000, emptyList(), lots, today)
    assertEquals(Lot("2026-10-31", 500), c.nearest)
    assertEquals(0, c.nearestDays)
    assertEquals(1_500, c.expiredMilli)
    assertTrue(c.near)
  }

  @Test
  fun farLotIsNotNear() {
    val c = PriceCheck.card(10_000, 0, emptyList(), listOf(Lot("2027-01-15", 1_000)), today)
    assertEquals(76, c.nearestDays)
    assertFalse(c.near)
    assertEquals(0, c.expiredMilli)
  }

  @Test
  fun marginOnSalePriceRoundsDownAndLossIsNegative() {
    assertEquals(2_000, PriceCheck.marginBp(10_000, 8_000))
    assertEquals(3_333, PriceCheck.marginBp(15_000, 10_000))
    assertEquals(-2_500, PriceCheck.marginBp(8_000, 10_000))
    assertNull(PriceCheck.marginBp(0, 8_000))
    assertNull(PriceCheck.marginBp(10_000, 0))
  }

  @Test
  fun emptyLedgerGivesEmptyCard() {
    assertEquals(PriceCheck.Card(), PriceCheck.card(0, 0, emptyList(), emptyList(), today))
    //  تاریخِ امروزِ خراب ⇒ انقضا سنجیده نمی‌شود
    assertNull(PriceCheck.card(1, 1, emptyList(), listOf(Lot("2026-11-01", 1)), "x").nearest)
  }
}
