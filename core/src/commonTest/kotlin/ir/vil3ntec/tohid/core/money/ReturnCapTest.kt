package ir.vil3ntec.tohid.core.money

import ir.vil3ntec.tohid.core.money.ReturnCap.Row
import ir.vil3ntec.tohid.core.money.ReturnCap.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  ماندهٔ قابلِ برگشتِ یک قلم (سفرِ JB5). سطرهای نخست همان `license/test-vectors/return-cap-v1.json` اند
 *  (رونویسِ دستی تا روی Native هم بدود)؛ کلِ فایل را `ReturnCapVectorsTest` (jvmTest) و
 *  `server/test/return-line-cap.test.js` می‌خوانند.
 */
class ReturnCapTest {

  @Test
  fun secondFullReturnFromAnotherDeviceIsRefused() {
    assertEquals(Verdict.OK, ReturnCap.check(2000, 100_000, emptyList(), Row(2000, 100_000)))
    assertEquals(Verdict.QTY, ReturnCap.check(2000, 100_000, listOf(Row(2000, 100_000)), Row(2000, 100_000)))
  }

  @Test
  fun partialReturnsCannotAddUpToMoreThanSold() {
    val one = listOf(Row(1000, 50_000))
    assertEquals(Verdict.OK, ReturnCap.check(2000, 100_000, one, Row(1000, 50_000)))
    assertEquals(Verdict.QTY, ReturnCap.check(2000, 100_000, one, Row(1001, 50_000)))
    assertEquals(Verdict.QTY, ReturnCap.check(3000, 30_000, listOf(Row(1000, 10_000), Row(1000, 10_000)), Row(1001, 10_000)))
  }

  @Test
  fun stateShowsWhatIsAlreadyReturnedAndWhatIsLeft() {
    val s = ReturnCap.state(2000, 100_000, listOf(Row(500, 25_000), Row(500, 25_000)))
    assertEquals(ReturnCap.State(2000, 100_000, 1000, 50_000, 1000, 50_000), s)
  }

  @Test
  fun counterAheadOfRowsCountsAsReturned() {
    //  ردیفِ کهنهٔ بی `saleItemId`: شمارندهٔ قلم بیشتر از جمعِ ردیف‌هاست
    assertEquals(1000L, ReturnCap.state(2000, 100_000, emptyList(), counterMilli = 1000).leftMilli)
    assertEquals(Verdict.QTY, ReturnCap.check(2000, 100_000, emptyList(), Row(1500, 0), counterMilli = 1000))
  }

  @Test
  fun leftNeverGoesBelowZero() {
    val s = ReturnCap.state(2000, 100_000, listOf(Row(2000, 100_000), Row(1000, 50_000)))
    assertEquals(0L, s.leftMilli)
    assertEquals(0L, s.leftMinor)
    assertEquals(Verdict.OK, ReturnCap.check(2000, 100_000, listOf(Row(2000, 100_000)), Row(0, 0)))
  }

  @Test
  fun amountAboveTheLineIsRefusedEvenWhenQuantityFits() {
    assertEquals(Verdict.AMOUNT, ReturnCap.check(2000, 100_000, emptyList(), Row(1000, 100_001)))
  }

  @Test
  fun oldPerReturnRoundingGetsHalfAnAfghaniPerRow() {
    //  دو عدد به ۲٫۵ که برنامهٔ پیش از #586 جدا گرد کرد: ۳ + ۳ از قلمِ ۵
    assertEquals(Verdict.OK, ReturnCap.check(2000, 500, listOf(Row(1000, 300)), Row(1000, 300), tolMinorPerRow = 50))
    assertEquals(Verdict.AMOUNT, ReturnCap.check(2000, 500, listOf(Row(1000, 300)), Row(1000, 300)))
  }

  @Test
  fun cashRoundedInvoiceFullReturnTakesTheRounding() {
    assertEquals(Verdict.OK, ReturnCap.check(2000, 99_500, emptyList(), Row(2000, 100_000), extraMinor = 500))
    assertEquals(Verdict.AMOUNT, ReturnCap.check(2000, 99_500, emptyList(), Row(2000, 100_000)))
  }

  @Test
  fun weighedLineInMilli() {
    assertEquals(1250L, ReturnCap.milli(1.25))
    assertEquals(Verdict.OK, ReturnCap.check(1250, 37_500, listOf(Row(500, 15_000)), Row(750, 22_500)))
    assertEquals(Verdict.QTY, ReturnCap.check(1250, 37_500, listOf(Row(500, 15_000)), Row(751, 22_500)))
  }

  @Test
  fun capIsTheWholeLineAfterInvoiceDiscountExactToThePul() {
    //  O-22 ب: دقیق تا یک‌صدم (الف: افغانیِ کامل، ۱۴۰۰ و ۱۰۰)
    assertEquals(500L, ReturnCap.capMinor(5.0, 1.0))
    assertEquals(1350L, ReturnCap.capMinor(15.0, 0.9))
    assertEquals(50L, ReturnCap.capMinor(0.5, 1.0))
    assertEquals(67L, ReturnCap.capMinor(1.0, 2.0 / 3.0))
    assertEquals(0L, ReturnCap.capMinor(500.0, -1.0))
    assertEquals(0L, ReturnCap.capMinor(Double.NaN, 1.0))
  }

  @Test
  fun capMatchesAFullReturnShare() {
    //  برگشتِ همهٔ قلم با `ReturnShare` دقیقاً همان سقف است، پس صفحهٔ برگشت خودش هرگز بیش از آن نمی‌سازد
    for ((total, qty, ratio) in listOf(Triple(5.0, 2.0, 1.0), Triple(15.0, 3.0, 0.9), Triple(1.0, 1.0, 2.0 / 3.0), Triple(375.0, 1.25, 1.0), Triple(375.0, 1.25, 0.9))) {
      assertEquals(ReturnCap.capMinor(total, ratio), ReturnCap.minor(ReturnShare.amount(total, qty, 0.0, qty, ratio)))
    }
  }
}
