package ir.vil3ntec.tohid.core.money

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 *  ماندهٔ حساب از جمعِ چند ردیف (نه یک Double ِ تنها): تسویهٔ دقیق یعنی صفرِ دقیق و «حساب صاف».
 *  عددها از گزارشِ بازبینیِ #470 (966.77 + 4963.8 + 1920.32، تسویهٔ 7850.89) نوشته شده‌اند.
 */
class LedgerBalanceTest {

  private val settled = listOf(966.77, 4963.8, 1920.32, -7850.89)

  @Test
  fun exactSettlementOfSeveralRowsIsExactlyZero() {
    //  همان جمعِ Double ِ پیشین پسماند دارد — وگرنه این آزمون چیزی را نمی‌سنجید
    assertNotEquals(0.0, settled.sum())
    assertEquals(0L, LedgerBalance.minor(settled))
    assertEquals(0.0, LedgerBalance.sum(settled))
    assertEquals(0, LedgerBalance.sign(LedgerBalance.sum(settled)))
    //  پسماندِ خامِ همان جمع هم به پول صفر است
    assertEquals(0, LedgerBalance.sign(settled.sum()))
  }

  @Test
  fun oneCentLeftIsStillOwedOrCredit() {
    assertEquals(1L, LedgerBalance.minor(settled + 0.01))
    assertEquals(1, LedgerBalance.sign(LedgerBalance.sum(settled + 0.01)))
    assertEquals(-1L, LedgerBalance.minor(settled + (-0.01)))
    assertEquals(-1, LedgerBalance.sign(LedgerBalance.sum(settled + (-0.01))))
    assertEquals(0.01, LedgerBalance.sum(settled + 0.01))
  }

  @Test
  fun eachRowIsRoundedToTheMinorUnitLikeDebtBook() {
    assertEquals(Money.fromLegacy(0.105, Currency.AFN).minor, LedgerBalance.minorOf(0.105))
    assertEquals(Money.fromLegacy(-2.345, Currency.AFN).minor, LedgerBalance.minorOf(-2.345))
    assertEquals(30L, LedgerBalance.minor(listOf(0.1, 0.2)))
    assertEquals(0.3, LedgerBalance.sum(listOf(0.1, 0.2)))
  }

  @Test
  fun neverThrowsOnBrokenRows() {
    assertEquals(0L, LedgerBalance.minor(listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)))
    assertEquals(500L, LedgerBalance.minor(listOf(Double.NaN, 5.0)))
    assertEquals(Long.MAX_VALUE, LedgerBalance.minor(listOf(1e300, 1e300)))
    assertEquals(Long.MIN_VALUE, LedgerBalance.minor(listOf(-1e300, -1e300)))
    assertEquals(0, LedgerBalance.sign(Double.NaN))
    assertEquals(0.0, LedgerBalance.sum(emptyList()))
  }

  @Test
  fun randomDecimalSettlementsAlwaysCloseToZero() {
    //  چند برد با دو رقمِ اعشار، و یک رسید که دقیقاً همه را می‌بندد (به پول حساب‌شده) — هزار بار
    val rnd = Random(470)
    var residues = 0
    repeat(1000) {
      val gives = List(1 + rnd.nextInt(6)) { rnd.nextLong(1, 1_000_000) }
      val rows = gives.map { it / 100.0 } + (-gives.sum() / 100.0)
      if (rows.sum() != 0.0) residues++
      assertEquals(0L, LedgerBalance.minor(rows), rows.toString())
      assertEquals(0.0, LedgerBalance.sum(rows), rows.toString())
      assertEquals(0, LedgerBalance.sign(rows.sum()), rows.toString())
    }
    //  جمعِ Double در بسیاری از همین‌ها پسماند داشت (گزارش: ۴۵٫۸٪)
    assertTrue(residues > 100, "residues=$residues")
  }
}
