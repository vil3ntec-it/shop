package ir.vil3ntec.tohid.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  SEC-DATA-01 — برگشتِ تکه‌تکه بیش از پولِ گرفته‌شده پس نمی‌دهد.
 *  سطرهای نخست همان `license/test-vectors/return-share-v1.json` اند (رونویسِ دستی تا روی Native هم بدود)؛
 *  کلِ فایل را `ReturnShareVectorsTest` (jvmTest) و `server/test/return-share.test.js` می‌خوانند.
 */
class ReturnShareTest {

  /** برگشت‌های پشتِ هم روی یک قلم، همان‌طور که `SaleItem.returnedQty` بالا می‌رود */
  private fun run(lineTotal: Double, quantity: Double, paidRatio: Double, steps: List<Double>): List<Double> {
    var before = 0.0
    return steps.map { q ->
      val a = ReturnShare.amount(lineTotal, quantity, before, q, paidRatio)
      if (q > 0) before += q
      a
    }
  }

  @Test
  fun twoUnitsAtTwoAndAHalfReturnFiveNotSix() {
    assertEquals(listOf(3.0, 2.0), run(5.0, 2.0, 1.0, listOf(1.0, 1.0)))
  }

  @Test
  fun threeUnitsWorthTwoReturnTwoNotThree() {
    assertEquals(listOf(1.0, 0.0, 1.0), run(2.0, 3.0, 1.0, listOf(1.0, 1.0, 1.0)))
  }

  @Test
  fun invoiceDiscountSharesSumToTheOneShotReturn() {
    assertEquals(listOf(5.0, 4.0, 5.0), run(15.0, 3.0, 0.9, listOf(1.0, 1.0, 1.0)))
    assertEquals(listOf(450.0), run(500.0, 2.0, 0.9, listOf(2.0)))
    assertEquals(listOf(225.0), run(500.0, 2.0, 0.9, listOf(1.0)))
  }

  @Test
  fun weighedLineReturnsInTwoParts() {
    assertEquals(listOf(150.0, 225.0), run(375.0, 1.25, 1.0, listOf(0.5, 0.75)))
  }

  @Test
  fun nothingLeftAfterTheWholeLineIsBack() {
    assertEquals(listOf(500.0, 0.0), run(500.0, 2.0, 1.0, listOf(2.0, 1.0)))
  }

  @Test
  fun zeroNegativeOrNotANumberGivesNothing() {
    assertEquals(listOf(0.0, 0.0), run(500.0, 2.0, 1.0, listOf(0.0, -1.0)))
    assertEquals(listOf(250.0, 0.0), run(500.0, 2.0, 1.0, listOf(1.0, -1.0)))
    assertEquals(0.0, ReturnShare.amount(500.0, 0.0, 0.0, 1.0, 1.0))
    assertEquals(0.0, ReturnShare.amount(500.0, 2.0, 0.0, Double.NaN, 1.0))
    assertEquals(0.0, ReturnShare.amount(Double.POSITIVE_INFINITY, 2.0, 0.0, 1.0, 1.0))
    assertEquals(0.0, ReturnShare.amount(500.0, 2.0, 0.0, 1.0, Double.NaN))
    //  returnedBefore ِ خراب صفر شمرده می‌شود، نه بیشترین مبلغ
    assertEquals(250.0, ReturnShare.amount(500.0, 2.0, Double.NaN, 1.0, 1.0))
  }

  /**
   *  ویژگی: برای هر قلم و هر شکستنِ مقدار، جمعِ برگشت‌ها = برگشتِ یک‌جای همان مقدار، و برگشتِ همه =
   *  round(جمعِ قلم × سهم). مولدِ قطعی (LCG) تا روی هر سکو همان نمونه‌ها بیاید.
   */
  @Test
  fun anySplitSumsToTheOneShotReturnAndNeverMore() {
    var seed = 20261030L
    fun next(n: Int): Int { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % n).toInt() }
    repeat(4000) {
      val units = 1 + next(40)
      val weighed = next(4) == 0
      val quantity = if (weighed) units * 0.25 else units.toDouble()
      val lineTotal = (1 + next(5000)).toDouble()
      val paidRatio = if (next(3) == 0) 1.0 else (1 + next(99)) / 100.0
      val parts = ArrayList<Double>()
      var left = units
      while (left > 0) { val p = 1 + next(left); parts += if (weighed) p * 0.25 else p.toDouble(); left -= p }
      val split = run(lineTotal, quantity, paidRatio, parts).sum()
      val oneShot = ReturnShare.amount(lineTotal, quantity, 0.0, quantity, paidRatio)
      assertEquals(oneShot, split, "قلمِ $lineTotal × $paidRatio، مقدارِ $quantity، تکه‌ها $parts")
      assertEquals(mathRound(lineTotal * paidRatio).toDouble(), oneShot)
      assertTrue(run(lineTotal, quantity, paidRatio, parts).all { it >= 0.0 })
    }
  }
  /**
   *  #586 — تاریخچهٔ قاطی: قلمِ ۲ افغانی برای ۳ عدد، دو برگشتِ پیش از به‌روزرسانی با فرمولِ جداجدا
   *  (۱ + ۱ = ۲، همهٔ پولِ قلم). برگشتِ سوم با تفاضلِ تنها `upTo(3) − upTo(2)` = ۱ می‌داد ⇒ جمع ۳ > ۲.
   */
  @Test
  fun mixedHistoryNeverRefundsMoreThanTheLine() {
    assertEquals(0.0, ReturnShare.amount(2.0, 3.0, 2.0, 1.0, 1.0, refundedBefore = 2.0, refundedQty = 2.0))
    //  پیش‌تر کمتر پس داده شده (۴ برای ۳ عدد: ۱ + ۱) ⇒ برگشتِ آخر کسری را می‌دهد و جمع درست ۴ می‌شود
    assertEquals(2.0, ReturnShare.amount(4.0, 3.0, 2.0, 1.0, 1.0, refundedBefore = 2.0, refundedQty = 2.0))
    //  پیش از پایانِ قلم هم: ۴ عدد به جمعِ ۶، دو برگشتِ کهنهٔ ۲ + ۲ = ۴ ⇒ برگشتِ سوم ۵ − ۴ = ۱ (تفاضلِ تنها ۵ − ۳ = ۲ می‌داد و جمعِ آخر ۷ > ۶)
    assertEquals(1.0, ReturnShare.amount(6.0, 4.0, 2.0, 1.0, 1.0, refundedBefore = 4.0, refundedQty = 2.0))
    //  ردیفِ برگشتِ کهنه بی saleItemId (ردیف‌ها کمتر از returnedQty) ⇒ بخشِ بی‌ردیف «تا پیش از این» فرض می‌شود
    assertEquals(250.0, ReturnShare.amount(500.0, 2.0, 1.0, 1.0, 1.0, refundedBefore = 0.0, refundedQty = 0.0))
    //  تاریخچهٔ یک‌دست همان پنج‌ورودی است
    assertEquals(
      ReturnShare.amount(15.0, 3.0, 1.0, 1.0, 0.9),
      ReturnShare.amount(15.0, 3.0, 1.0, 1.0, 0.9, refundedBefore = 5.0, refundedQty = 1.0),
    )
    assertEquals(0.0, ReturnShare.amount(2.0, 3.0, 2.0, -1.0, 1.0, refundedBefore = 0.0, refundedQty = 2.0))
  }

  /** ویژگی: هر تاریخچهٔ کهنه (فرمولِ جداجدا، یکی‌یکی) + بقیه با این تابع ⇒ جمع = max(کهنه، round(جمعِ قلم × سهم)) */
  @Test
  fun anyLegacyPrefixThenNewNeverExceedsTheLine() {
    var seed = 5860586L
    fun next(n: Int): Int { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % n).toInt() }
    repeat(3000) {
      val quantity = (1 + next(30)).toDouble()
      val lineTotal = (1 + next(3000)).toDouble()
      val paidRatio = if (next(3) == 0) 1.0 else (1 + next(99)) / 100.0
      val legacyUnits = next(quantity.toInt() + 1)
      var refunded = 0.0
      repeat(legacyUnits) { refunded += mathRound(lineTotal / quantity * 1.0 * paidRatio).toDouble() }
      var before = legacyUnits.toDouble()
      var rowsQty = before
      while (before < quantity) {
        val q = minOf((1 + next(4)).toDouble(), quantity - before)
        val a = ReturnShare.amount(lineTotal, quantity, before, q, paidRatio, refunded, rowsQty)
        assertTrue(a >= 0.0)
        refunded += a; before += q; rowsQty += q
      }
      val line = mathRound(lineTotal * paidRatio).toDouble()
      val legacyOnly = legacyUnits * mathRound(lineTotal / quantity * paidRatio).toDouble()
      val expected = if (legacyUnits.toDouble() == quantity) legacyOnly else maxOf(line, legacyOnly)
      assertEquals(expected, refunded, "قلمِ $lineTotal × $paidRatio / $quantity، کهنه $legacyUnits")
    }
  }
}
