package ir.vil3ntec.tohid.core.money

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 *  ماندهٔ حساب از جمعِ چند ردیف (نه یک Double ِ تنها): تسویهٔ دقیق یعنی صفرِ دقیق و «حساب صاف»، و
 *  هر ردیف دقیقاً با قاعدهٔ `DebtBook` (اول گردِ مبلغِ ردیف، بعد نشانه). عددها از بازبینی‌های #470 و #610.
 */
class LedgerBalanceTest {

  private data class Row(val give: Boolean, val amount: Double)
  private fun give(a: Double) = Row(true, a)
  private fun receive(a: Double) = Row(false, a)

  private fun minor(rows: List<Row>) = LedgerBalance.minor(rows, plus = { it.give }, amount = { it.amount })
  private fun sum(rows: List<Row>) = LedgerBalance.sum(rows, plus = { it.give }, amount = { it.amount })

  /** فرمولِ خودِ `DebtBook` (`DebtBook.creditCheck` و جمعِ جاریِ `book`): `acc ± DebtBook.minor(amount)` */
  private fun debtBook(rows: List<Row>): Long = rows.fold(0L) { acc, r ->
    val a = if (r.amount.isNaN() || r.amount.isInfinite()) 0.0 else r.amount
    val m = Money.fromLegacy(a, Currency.AFN).minor
    if (r.give) acc + m else acc - m
  }

  /** همان جمعِ Double ِ پیشین (`ShopStore.debt` ِ main) */
  private fun double(rows: List<Row>) = rows.sumOf { if (it.give) it.amount else -it.amount }

  private val settled = listOf(give(966.77), give(4963.8), give(1920.32), receive(7850.89))

  @Test
  fun exactSettlementOfSeveralRowsIsExactlyZero() {
    //  همان جمعِ Double ِ پیشین پسماند دارد — وگرنه این آزمون چیزی را نمی‌سنجید
    assertNotEquals(0.0, double(settled))
    assertEquals(0L, minor(settled))
    assertEquals(0.0, sum(settled))
    assertEquals(0, LedgerBalance.sign(sum(settled)))
    //  پسماندِ خامِ همان جمع هم به پول صفر است
    assertEquals(0, LedgerBalance.sign(double(settled)))
  }

  @Test
  fun oneCentLeftIsStillOwedOrCredit() {
    assertEquals(1L, minor(settled + give(0.01)))
    assertEquals(1, LedgerBalance.sign(sum(settled + give(0.01))))
    assertEquals(-1L, minor(settled + receive(0.01)))
    assertEquals(-1, LedgerBalance.sign(sum(settled + receive(0.01))))
    assertEquals(0.01, sum(settled + give(0.01)))
  }

  @Test
  fun halfPulGiveAndSameReceiveIsSettled() {
    //  12.625 ⇒ 1262.5 پول: برد +1263 و رسید −1263، نه −1262 (گردِ عددِ نشانه‌دار به سمتِ +∞)
    val rows = listOf(give(12.625), receive(12.625))
    assertEquals(0.0, double(rows))
    assertEquals(0L, debtBook(rows))
    assertEquals(0L, minor(rows))
    assertEquals(0, LedgerBalance.sign(sum(rows)))
    //  گزارش: داده و گرفته هر دو 12.63، مانده 0
    assertEquals(1263L, minor(listOf(give(12.625))))
    assertEquals(-1263L, minor(listOf(receive(12.625))))
  }

  @Test
  fun receiveOnlyHalfPulIsTheSameAsDebtBook() {
    val rows = listOf(receive(0.005))
    assertEquals(debtBook(rows), minor(rows))
    assertEquals(-1L, minor(rows))
    assertEquals(-1, LedgerBalance.sign(sum(rows)))
  }

  @Test
  fun everyRowIsRoundedExactlyLikeDebtBook() {
    //  نیم‌پول‌ها و سه‌رقمی‌ها، هر دو جهت، و مبلغِ منفیِ خراب — همه با فرمولِ خودِ DebtBook
    val amounts = listOf(
      0.005, 0.015, 0.025, 0.105, 0.125, 2.345, 12.625, 1262.505, 99.995, 0.0049, 0.0051, 7850.885,
      -0.005, -0.015, -2.345, -12.625, -99.995, 0.0, -0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
    )
    for (a in amounts) for (g in listOf(true, false)) {
      val rows = listOf(Row(g, a))
      assertEquals(debtBook(rows), minor(rows), "row ${if (g) "give" else "receive"} $a")
    }
    val rnd = Random(610)
    repeat(2000) {
      val rows = List(1 + rnd.nextInt(8)) {
        val half = (rnd.nextLong(-200_000, 200_000) * 2 + 1) / 200.0          // x.xx5 — درست وسطِ دو پول
        val three = rnd.nextLong(-2_000_000, 2_000_000) / 1000.0
        Row(rnd.nextBoolean(), if (rnd.nextBoolean()) half else three)
      }
      assertEquals(debtBook(rows), minor(rows), rows.toString())
      assertEquals(LedgerBalance.major(debtBook(rows)), sum(rows), rows.toString())
    }
  }

  @Test
  fun signIsSymmetricForRawBalances() {
    assertEquals(1, LedgerBalance.sign(0.005))
    assertEquals(-1, LedgerBalance.sign(-0.005))
    assertEquals(0, LedgerBalance.sign(0.0049))
    assertEquals(0, LedgerBalance.sign(-0.0049))
    assertEquals(0, LedgerBalance.sign(-0.0))
    assertEquals(1, LedgerBalance.sign(12.63))
    assertEquals(-1, LedgerBalance.sign(-12.63))
  }

  @Test
  fun neverThrowsOnBrokenRows() {
    assertEquals(0L, minor(listOf(give(Double.NaN), give(Double.POSITIVE_INFINITY), receive(Double.NEGATIVE_INFINITY))))
    assertEquals(500L, minor(listOf(give(Double.NaN), give(5.0))))
    assertEquals(Long.MAX_VALUE, minor(listOf(give(1e300), give(1e300))))
    assertEquals(Long.MIN_VALUE, minor(listOf(receive(1e300), receive(1e300))))
    //  رسیدِ منفیِ بی‌اندازه: −Long.MIN_VALUE سرریز نمی‌کند
    assertEquals(Long.MAX_VALUE, minor(listOf(receive(-1e300))))
    assertEquals(0, LedgerBalance.sign(Double.NaN))
    assertEquals(0.0, sum(emptyList()))
  }

  @Test
  fun randomDecimalSettlementsAlwaysCloseToZero() {
    //  چند برد با دو رقمِ اعشار، و یک رسید که دقیقاً همه را می‌بندد (به پول حساب‌شده) — هزار بار
    val rnd = Random(470)
    var residues = 0
    repeat(1000) {
      val gives = List(1 + rnd.nextInt(6)) { rnd.nextLong(1, 1_000_000) }
      val rows = gives.map { give(it / 100.0) } + receive(gives.sum() / 100.0)
      if (double(rows) != 0.0) residues++
      assertEquals(0L, minor(rows), rows.toString())
      assertEquals(0.0, sum(rows), rows.toString())
      assertEquals(0, LedgerBalance.sign(double(rows)), rows.toString())
    }
    //  جمعِ Double در بسیاری از همین‌ها پسماند داشت (گزارش: ۴۵٫۸٪)
    assertTrue(residues > 100, "residues=$residues")
  }
}
