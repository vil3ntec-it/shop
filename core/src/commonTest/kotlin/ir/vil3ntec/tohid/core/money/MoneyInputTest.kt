package ir.vil3ntec.tohid.core.money

import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  «تسویهٔ کامل» = همهٔ مانده تا پول، هرگز گردِ افغانی.
 *  گردِ افغانی (`Math.round(balance)` ِ صفحه‌های قرض‌دار و خرید) از 899.6 «900» و از 1200.4 «1200» می‌ساخت.
 */
class MoneyInputTest {

  @Test
  fun fractionalBalanceIsNotRoundedToAfghani() {
    assertEquals("899.6", MoneyInput.settleInput(899.6))     // گردِ افغانی: 900 ⇒ ۰٫۴ پیش‌پرداخت
    assertEquals("1200.4", MoneyInput.settleInput(1200.4))   // گردِ افغانی: 1200 ⇒ ۰٫۴ بدهیِ باز
    assertEquals("1200.5", MoneyInput.settleInput(1200.5))
    assertEquals("1200.05", MoneyInput.settleInput(1200.05))
    assertEquals("0.99", MoneyInput.settleInput(0.99))
  }

  @Test
  fun smallestSettlementIsOnePul() {
    assertEquals("0.01", MoneyInput.settleInput(0.01))
    assertEquals("0.1", MoneyInput.settleInput(0.1))
    //  نیمه به سمتِ +∞، همان `Money.fromLegacy`
    assertEquals("0.01", MoneyInput.settleInput(0.005))
    //  کمتر از نیم‌پول چیزی برای تسویه نیست
    assertNull(MoneyInput.settleInput(0.004))
  }

  @Test
  fun wholeBalanceHasNoDecimals() {
    assertEquals("1200", MoneyInput.settleInput(1200.0))
    assertEquals("1", MoneyInput.settleInput(1.0))
    assertEquals("10", MoneyInput.settleInput(10.0))
  }

  @Test
  fun doubleSumNoiseIsCutAtPul() {
    assertEquals("0.3", MoneyInput.settleInput(0.1 + 0.2))           // 0.30000000000000004
    assertEquals("30.3", MoneyInput.settleInput(10.1 + 20.2))        // 30.299999999999997
    assertEquals("899.6", MoneyInput.settleInput(899.5999999999999))
    assertEquals("899.6", MoneyInput.settleInput(899.6000000000001))
  }

  @Test
  fun largeBalancesAreExactWithoutExponent() {
    assertEquals("12345678.91", MoneyInput.settleInput(12_345_678.91))
    assertEquals("1000000000.05", MoneyInput.settleInput(1_000_000_000.05))
    assertEquals("10000000", MoneyInput.settleInput(1e7))
    assertEquals("90000000000000", MoneyInput.settleInput(9e13))   // ۹×۱۰^۱۵ پول، زیرِ ۲^۵۳
    //  بیرون از بازهٔ دقیقِ پول (۲^۵۳−۱): عددِ گردشده نوشته نمی‌شود
    assertNull(MoneyInput.settleInput(1e14))
    assertNull(MoneyInput.settleInput(Double.MAX_VALUE))
  }

  @Test
  fun zeroNegativeAndNonFiniteHaveNoShortcut() {
    assertNull(MoneyInput.settleInput(0.0))
    assertNull(MoneyInput.settleInput(-0.0))
    assertNull(MoneyInput.settleInput(-0.01))
    assertNull(MoneyInput.settleInput(-50.0))
    assertNull(MoneyInput.settleInput(-899.6))
    assertNull(MoneyInput.settleInput(Double.NaN))
    assertNull(MoneyInput.settleInput(Double.POSITIVE_INFINITY))
    assertNull(MoneyInput.settleInput(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun settlingLeavesExactlyZeroForEveryPulAmount() {
    //  همهٔ مبلغ‌ها از ۰٫۰۱ تا ۲۰۰۰ افغانی، و نمونه‌هایی تا ۱۰^۱۱ افغانی
    val minors = (1L..200_000L) + listOf(123_456_789L, 999_999_999_99L, 100_000_000_000_01L)
    for (m in minors) {
      val balance = m / 100.0
      val text = MoneyInput.settleInput(balance)!!
      assertEquals(m, (text.toDouble() * 100).roundToLong(), "پول برای $balance")
      assertEquals(0.0, balance - text.toDouble(), "مانده پس از تسویهٔ $balance")
    }
  }

  @Test
  fun minorToTextIsExactFromZeroUp() {
    assertEquals("0", MoneyInput.ofMinor(0))
    assertEquals("0.05", MoneyInput.ofMinor(5))
    assertEquals("0.5", MoneyInput.ofMinor(50))
    assertEquals("899.6", MoneyInput.ofMinor(89_960))
    assertEquals("1200", MoneyInput.ofMinor(120_000))
    assertEquals("90071992547409.91", MoneyInput.ofMinor(9_007_199_254_740_991))
    assertNull(MoneyInput.ofMinor(9_007_199_254_740_992))
    assertNull(MoneyInput.ofMinor(-1))
    for (m in 0L..20_000L) assertEquals(m, (MoneyInput.ofMinor(m)!!.toDouble() * 100).roundToLong(), "پولِ $m")
  }
}
